package sa.emtethal.realestate

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

// Tokens and edited contract content remain in memory, never SharedPreferences or saved instance state.
data class UiState(
    val screen: String = "settings", val connected: Boolean = false,
    val endpoint: String = "http://10.0.2.2:8000/", val token: String = "",
    val busy: Boolean = false, val busyText: String = "", val error: String? = null,
    val info: String? = null, val contracts: List<Contract> = emptyList(),
    val documents: List<UploadedDocument> = emptyList(), val newFacts: Facts = Facts(),
    val selected: Contract? = null, val edited: Draft? = null,
    val note: String = "", val escrowReference: String = "", val legal: Boolean = false,
    val bilingual: Boolean = false, val sources: Boolean = false, val dirty: Boolean = false
)
class ContractViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("connection", 0)
    private val mutable = MutableStateFlow(UiState(endpoint = preferences.getString("endpoint", null)
        ?: if (BuildConfig.DEBUG) "http://10.0.2.2:8000/" else "https://"))
    val state = mutable.asStateFlow()
    private var api: ContractApi? = null
    private fun service() = api ?: error("اتصل بالسيرفر أولاً.")
    fun clearMessage() = mutable.update { it.copy(error = null, info = null) }
    fun endpoint(value: String) = mutable.update { it.copy(endpoint = value) }
    fun token(value: String) = mutable.update { it.copy(token = value) }
    fun navigate(screen: String) { if (!mutable.value.busy) mutable.update { it.copy(screen = screen) } }
    fun fact(key: String, value: String, review: Boolean) = mutable.update {
        if (it.busy) it else if (review) it.copy(edited = it.edited?.copy(facts = it.edited.facts.update(key, value)), dirty = true)
        else it.copy(newFacts = it.newFacts.update(key, value))
    }
    fun clause(index: Int, lang: String, value: String) = mutable.update { s ->
        if (s.busy) s else s.copy(dirty = true, edited = s.edited?.copy(clauses = s.edited.clauses.mapIndexed { i, c ->
            if (index != i) c else when (lang) { "ar" -> c.copy(ar = value); "en" -> c.copy(en = value); else -> c.copy(sourceReference = value) }
        }))
    }
    fun reviewFields(note: String? = null, reference: String? = null, legal: Boolean? = null,
                     bilingual: Boolean? = null, sources: Boolean? = null) = mutable.update {
        it.copy(note = note ?: it.note, escrowReference = reference ?: it.escrowReference,
            legal = legal ?: it.legal, bilingual = bilingual ?: it.bilingual, sources = sources ?: it.sources, dirty = true)
    }
    private fun action(label: String, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, busyText = label, error = null, info = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = message(e)) } }
            finally { mutable.update { it.copy(busy = false, busyText = "") } }
        }
    }
    private fun message(e: Exception): String = when (e) {
        is HttpException -> {
            val detail = e.response()?.errorBody()?.use { it.string() }?.take(1600).orEmpty()
            when (e.code()) {
                401 -> "مفتاح الدخول غير صحيح. راجع إعدادات الاتصال."
                403 -> "الاعتماد والرفض يتطلبان مفتاح مسؤول الامتثال. افتح الإعدادات وغيّر المفتاح."
                409 -> "تغيّرت حالة العقد أو تمت مراجعته. حدّث العقد قبل المحاولة.\n$detail"
                422 -> "راجع البيانات والبنود المطلوبة:\n$detail"
                else -> "خطأ من السيرفر (${e.code()}): $detail"
            }
        }
        is IOException -> "تعذر الاتصال أو انتهت المهلة. حدّث قائمة العقود قبل تكرار إنشاء أو اعتماد عقد؛ قد يكون الطلب وصل للسيرفر."
        else -> e.message ?: "حدث خطأ غير متوقع."
    }
    fun connect() = action("جارٍ التحقق من الاتصال…") {
        val before = mutable.value
        val normalized = ApiFactory.normalizeUrl(before.endpoint, BuildConfig.DEBUG)
        val candidate = ApiFactory.create(normalized, before.token)
        val contracts = candidate.contracts()
        val sameServer = preferences.getString("endpoint", null) == normalized && before.connected
        api = candidate
        preferences.edit().putString("endpoint", normalized).apply()
        mutable.update { it.copy(connected = true, endpoint = normalized, contracts = contracts,
            screen = if (sameServer && it.selected != null) "review" else "home",
            selected = if (sameServer) it.selected else null, edited = if (sameServer) it.edited else null,
            documents = if (sameServer) it.documents else emptyList(), dirty = sameServer && it.dirty,
            info = "تم الاتصال بالسيرفر.") }
    }
    fun disconnect() {
        if (mutable.value.busy) return
        api = null
        mutable.value = UiState(endpoint = mutable.value.endpoint)
    }
    fun refresh() = action("جارٍ تحميل العقود…") {
        val contracts = service().contracts()
        mutable.update { it.copy(contracts = contracts) }
    }
    fun open(id: String) = action("جارٍ تحميل العقد…") { select(service().contract(id)) }
    private fun select(contract: Contract) = mutable.update {
        it.copy(selected = contract, edited = contract.draft, screen = "review", note = "", escrowReference = "",
            legal = false, bilingual = false, sources = false, dirty = false)
    }
    fun removeDocument(id: String) { if (!mutable.value.busy) mutable.update { it.copy(documents = it.documents.filterNot { d -> d.id == id }) } }
    fun upload(uris: List<Uri>) = action("جارٍ رفع المستندات…") {
        require(mutable.value.documents.size + uris.size <= 12) { "يمكن إرفاق 12 مستنداً كحد أقصى." }
        for (uri in uris) {
            val uploaded = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }?.replace(Regex("[\\r\\n\"/\\\\]"), "_")?.take(200) ?: "document.txt"
                val temp = File.createTempFile("upload-", ".tmp", getApplication<Application>().cacheDir)
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(8192); var size = 0L
                            while (true) {
                                val count = input.read(buffer); if (count < 0) break
                                size += count; require(size <= 20L * 1024 * 1024) { "حجم المستند يتجاوز 20 ميجابايت." }
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error("تعذر قراءة المستند.")
                    service().upload(MultipartBody.Part.createFormData("file", name,
                        temp.asRequestBody("application/octet-stream".toMediaType())))
                } finally { temp.delete() }
            }
            mutable.update { it.copy(documents = it.documents + uploaded) }
        }
    }
    fun demo() = action("جارٍ تجهيز مثال تجريبي…") {
        val doc = service().upload(MultipartBody.Part.createFormData("file", "android-demo.txt",
            "SYNTHETIC DEMO ONLY. Project DEMO-001. Unit A-101. Handover 2028-06-30. Price SAR 950000.00.\nمشروع تجريبي للاختبار فقط."
                .toRequestBody("text/plain; charset=utf-8".toMediaType())))
        mutable.update { it.copy(newFacts = Facts.demo(), documents = listOf(doc), info = "بيانات تجريبية فقط؛ ليست مشروعاً حقيقياً.") }
    }
    fun create() = action("جارٍ استخراج البيانات وصياغة العقد…") {
        val current = mutable.value
        current.newFacts.error()?.let { error(it) }
        require(current.documents.isNotEmpty()) { "ارفع مستنداً واحداً على الأقل." }
        val contract = service().create(StartRequest(current.newFacts, current.documents.map { it.id }))
        mutable.update { it.copy(documents = emptyList(), newFacts = Facts(), contracts = listOf(contract) + it.contracts) }
        select(contract)
    }
    fun decide(approve: Boolean) = action("جارٍ إرسال قرار المراجعة…") {
        val s = mutable.value; val contract = s.selected ?: error("اختر عقداً.")
        val draft = s.edited ?: error("المسودة غير متاحة.")
        require(contract.waiting) { "العقد لا ينتظر المراجعة." }
        draft.facts.error()?.let { error(it) }
        require(s.note.isNotBlank() && s.escrowReference.isNotBlank()) { "أدخل ملاحظة المراجعة ومرجع حساب الضمان." }
        require(draft.clauses.all { it.ar.isNotBlank() && it.en.isNotBlank() && it.sourceReference.isNotBlank() }) { "أكمل نصوص البنود ومراجعها." }
        if (approve) {
            require(s.legal && s.bilingual && s.sources) { "أكمل إقرارات المراجعة الثلاثة." }
            require(draft.clauses.none { "REVIEW_REQUIRED" in it.ar || "REVIEW_REQUIRED" in it.en || "REVIEW_REQUIRED" in it.sourceReference }) {
                "استبدل كل REVIEW_REQUIRED بالنص والمراجع التي راجعتها قبل الاعتماد."
            }
        }
        val result = service().review(contract.id, Approval(contract.revision, draft, if (approve) "approve" else "reject",
            s.note, s.legal, s.bilingual, s.sources, s.escrowReference))
        select(result)
        mutable.update { it.copy(contracts = it.contracts.map { c -> if (c.id == result.id) result else c }, info = "تم حفظ قرار المراجعة.") }
    }
    fun savePdf(uri: Uri) = action("جارٍ تنزيل PDF…") {
        val selected = mutable.value.selected ?: error("اختر عقداً.")
        require(selected.finalized) { "العقد غير معتمد." }
        withContext(Dispatchers.IO) {
            service().pdf(selected.id).use { body ->
                val output = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف الحفظ.")
                output.use { destination -> body.byteStream().use { it.copyTo(destination) } }
            }
        }
        mutable.update { it.copy(info = "تم حفظ PDF في المكان الذي اخترته.") }
    }
}
