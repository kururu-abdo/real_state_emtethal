package sa.emtethal.realestate

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val Green = Color(0xFF145B50)
private val Cream = Color(0xFFF5F6F0)
private val Ink = Color(0xFF193C36)
private val Gold = Color(0xFFC9A663)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White,
                background = Cream, surface = Color.White, onSurface = Ink, secondary = Gold,
                primaryContainer = Color(0xFFE0EEE5), outline = Color(0xFF84968A))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    EmtethalApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmtethalApp(vm: ContractViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDecision by remember { mutableStateOf<Boolean?>(null) }
    var discardTarget by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.upload(uris)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.savePdf(uri)
    }
    val navigate: (String) -> Unit = { target ->
        if (!state.busy) {
            // Settings preserves the in-memory editor so an officer can change credentials.
            if (state.screen == "review" && state.dirty && target != "settings") discardTarget = target
            else vm.navigate(target)
        }
    }
    BackHandler(enabled = state.busy || (state.connected && state.screen != "home")) {
        if (!state.busy) navigate("home")
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Column {
                Text("امتثال العقاري", fontWeight = FontWeight.Bold)
                Text("العقود والامتثال • Emtethal", style = MaterialTheme.typography.labelSmall, color = Green)
            } }, actions = {
                if (state.connected) IconButton(onClick = { navigate("settings") }, enabled = !state.busy) {
                    Icon(Icons.Outlined.Settings, contentDescription = "إعدادات الاتصال")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream))
        },
        bottomBar = {
            if (state.connected) NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = state.screen == "home" || state.screen == "review", onClick = { navigate("home") },
                    enabled = !state.busy, icon = { Icon(Icons.Outlined.Description, null) }, label = { Text("العقود") })
                NavigationBarItem(selected = state.screen == "new", onClick = { navigate("new") }, enabled = !state.busy,
                    icon = { Icon(Icons.Outlined.AddCircleOutline, null) }, label = { Text("عقد جديد") })
                NavigationBarItem(selected = state.screen == "settings", onClick = { navigate("settings") }, enabled = !state.busy,
                    icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("الاتصال") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.busyText, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
            state.error?.let { text -> Message(text, true, vm::clearMessage) }
            state.info?.let { text -> Message(text, false, vm::clearMessage) }
            when (state.screen) {
                "settings" -> SettingsScreen(state, vm)
                "new" -> NewContractScreen(state, vm) { filePicker.launch(arrayOf("application/pdf", "text/plain", "image/png", "image/jpeg")) }
                "review" -> ReviewScreen(state, vm, { confirmDecision = it }, {
                    pdfPicker.launch("emtethal-${state.selected?.id}.pdf")
                })
                else -> HomeScreen(state, vm) { navigate("new") }
            }
        }
    }
    confirmDecision?.let { approve ->
        AlertDialog(onDismissRequest = { confirmDecision = null }, title = { Text(if (approve) "اعتماد النسخة الحالية؟" else "رفض العقد؟") },
            text = { Text(if (approve) "سيُرسل النص المعدّل والإقرارات للسيرفر للتحقق من حساب الضمان وإتمام العقد."
                else "سيُغلق مسار هذا العقد بالرفض. لإنشاء نسخة بديلة ابدأ عقداً جديداً.") },
            confirmButton = { TextButton(onClick = { confirmDecision = null; vm.decide(approve) }) { Text("تأكيد") } },
            dismissButton = { TextButton(onClick = { confirmDecision = null }) { Text("رجوع") } })
    }
    discardTarget?.let { target ->
        AlertDialog(onDismissRequest = { discardTarget = null }, title = { Text("مغادرة المراجعة؟") },
            text = { Text("تعديلاتك لم تُرسل للسيرفر بعد. إعادة فتح العقد ستستبدلها بالنسخة المحفوظة.") },
            confirmButton = { TextButton(onClick = { discardTarget = null; vm.navigate(target) }) { Text("مغادرة") } },
            dismissButton = { TextButton(onClick = { discardTarget = null }) { Text("متابعة المراجعة") } })
    }
}

@Composable
private fun Message(text: String, error: Boolean, dismiss: () -> Unit) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else Color(0xFFE2EEE3)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 8)
            IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "إغلاق الرسالة") }
        }
    }
}
@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF65776B)) }
            content()
        }
    }
}
@Composable
private fun SettingsScreen(s: UiState, vm: ContractViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Hero("مساحة عملك العقارية", "اربط التطبيق بالسيرفر وابدأ مراجعة عقودك من الجوال.") }
        item { Section("الاتصال بالسيرفر", "مفتاح الدخول يحدد صلاحيتك. استخدم مفتاح مسؤول الامتثال للاعتماد والرفض.") {
            LtrField("عنوان السيرفر", s.endpoint, vm::endpoint, !s.busy)
            OutlinedTextField(s.token, vm::token, Modifier.fillMaxWidth(), enabled = !s.busy, label = { Text("مفتاح الدخول API Key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(onClick = vm::connect, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("اتصال وتحميل العقود") }
            if (s.connected) {
                if (s.selected != null) OutlinedButton(onClick = { vm.navigate("review") }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("العودة للمراجعة الحالية") }
                TextButton(onClick = vm::disconnect, enabled = !s.busy) { Text("إنهاء الجلسة ومسح المفتاح") }
            }
        } }
        item { Section("التشغيل المحلي") {
            Text("المحاكي: http://10.0.2.2:8000\nالجوال عبر USB: استخدم adb reverse ثم http://127.0.0.1:8000", style = MaterialTheme.typography.bodySmall)
            Text("المفتاح يبقى في ذاكرة التطبيق فقط. عند إغلاق العملية تحتاج إدخاله مجدداً. HTTP متاح في نسخة Debug فقط.", style = MaterialTheme.typography.bodySmall)
        } }
    }
}
@Composable
private fun Hero(title: String, description: String) {
    Surface(color = Green, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = Gold, modifier = Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD5E6DB))
        }
    }
}
@Composable
private fun HomeScreen(s: UiState, vm: ContractViewModel, create: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = s.contracts.filter { c -> query.isBlank() || listOf(c.id, c.draft?.facts?.projectId.orEmpty(), c.draft?.facts?.buyer.orEmpty()).any { it.contains(query, true) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Hero("عقودك تحت المراجعة", "من مستندات المشروع إلى عقد ثنائي اللغة، مع اعتماد بشري قبل إصدار PDF.") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("العقود", s.contracts.size, Modifier.weight(1f))
            Stat("بانتظارك", s.contracts.count { it.waiting }, Modifier.weight(1f))
            Stat("مكتملة", s.contracts.count { it.finalized }, Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = create, enabled = !s.busy, modifier = Modifier.weight(1f)) { Text("إنشاء عقد جديد") }
            OutlinedButton(onClick = vm::refresh, enabled = !s.busy) { Icon(Icons.Outlined.Refresh, "تحديث العقود") }
        } }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("بحث بالمشروع أو المشتري أو رقم العقد") }, singleLine = true) }
        if (filtered.isEmpty()) item { Section("لا توجد عقود مطابقة") { Text("أنشئ عقداً جديداً أو حدّث القائمة لاسترجاع العقود من السيرفر.") } }
        items(filtered, key = { it.id }) { contract ->
            Card(onClick = { vm.open(contract.id) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(contract.draft?.facts?.projectId ?: "مشروع قيد المعالجة", fontWeight = FontWeight.Bold)
                    Text(contract.draft?.facts?.buyer ?: contract.id.take(8), style = MaterialTheme.typography.bodyMedium)
                    Text(statusLabel(contract.status), color = Green, style = MaterialTheme.typography.labelLarge)
                    if (contract.demo == true) Text("تجريبي • غير صالح للتوقيع", color = Color(0xFF8C651E), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { Text("تعرض القائمة آخر 100 عقد. حدّثها بعد طلب انقطع اتصاله قبل تكراره.", style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable
private fun Stat(label: String, count: Int, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = Green, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
private val labels = mapOf("project_id" to "رقم المشروع", "developer" to "المطور", "buyer" to "المشتري", "unit" to "الوحدة",
    "deed_number" to "رقم الصك", "license_number" to "رقم الترخيص", "price_sar" to "الثمن بالريال السعودي",
    "handover_date" to "تاريخ التسليم YYYY-MM-DD", "escrow_iban" to "آيبان حساب الضمان")
@Composable
private fun FactsEditor(facts: Facts, enabled: Boolean, review: Boolean, update: (String, String) -> Unit) {
    facts.fields().forEach { (key, value) ->
        val editable = enabled && !(review && key == "project_id")
        if (key in setOf("price_sar", "handover_date", "escrow_iban")) LtrField(labels.getValue(key), value, { update(key, it) }, editable)
        else OutlinedTextField(value, { update(key, it) }, Modifier.fillMaxWidth(), label = { Text(labels.getValue(key)) }, enabled = editable, singleLine = true)
    }
}
@Composable
private fun LtrField(label: String, value: String, update: (String) -> Unit, enabled: Boolean = true) {
    OutlinedTextField(value, update, Modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled,
        singleLine = true, textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
}
@Composable
private fun NewContractScreen(s: UiState, vm: ContractViewModel, pickFiles: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Section("١ • مستندات المشروع", "PDF نصي أو TXT. الصور تتطلب وضع Live في السيرفر. حتى 12 مستنداً، 20 ميجابايت للمستند.") {
            OutlinedButton(onClick = pickFiles, enabled = !s.busy && s.documents.size < 12, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("اختيار المستندات") }
            s.documents.forEach { doc -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(doc.name); Text("${doc.pages} صفحات • تم الرفع", style = MaterialTheme.typography.labelSmall, color = Green) }
                IconButton(onClick = { vm.removeDocument(doc.id) }, enabled = !s.busy) { Icon(Icons.Outlined.Close, "إزالة ${doc.name}") }
            } }
            TextButton(onClick = vm::demo, enabled = !s.busy) { Text("تعبئة مثال تجريبي وإرفاق مستنده") }
        } }
        item { Section("٢ • بيانات العقد", "راجع البيانات مع المستندات الأصلية؛ لن يتم تخمينها من المخططات.") {
            FactsEditor(s.newFacts, !s.busy, false) { k, v -> vm.fact(k, v, false) }
        } }
        item { Button(onClick = vm::create, enabled = !s.busy && s.documents.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("استخراج وصياغة العقد")
        } }
    }
}
@Composable
private fun ReviewScreen(s: UiState, vm: ContractViewModel, decide: (Boolean) -> Unit, savePdf: () -> Unit) {
    val contract = s.selected ?: return
    val draft = s.edited
    val editable = contract.waiting && !s.busy
    var showEnglishSummary by rememberSaveable(contract.id) { mutableStateOf(false) }
    var reloadConfirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Section(draft?.facts?.projectId ?: "العقد", "${statusLabel(contract.status)} • النسخة ${contract.revision}") {
            Text(contract.id, style = MaterialTheme.typography.labelSmall)
            if (contract.demo == true) Text("وضع تجريبي: هذا العقد غير صالح للتوقيع.", color = Color(0xFF8C651E))
            if (s.dirty) Text("تعديلات محلية لم تُرسل بعد", style = MaterialTheme.typography.labelMedium, color = Green)
            OutlinedButton(onClick = { if (s.dirty) reloadConfirm = true else vm.open(contract.id) }, enabled = !s.busy) { Text("تحديث من السيرفر") }
        } }
        contract.extraction?.let { extraction ->
            item { Section("نتائج الاستخراج") {
                Text(if (showEnglishSummary) extraction.summaryEn else extraction.summaryAr)
                TextButton(onClick = { showEnglishSummary = !showEnglishSummary }) { Text(if (showEnglishSummary) "العربية" else "English") }
                extraction.unresolved.forEach { Text("• $it", color = Color(0xFF955022)) }
            } }
            items(extraction.evidence) { evidence -> Section("${evidence.document} • صفحة ${evidence.page}", evidence.field) {
                Text(evidence.quote, style = MaterialTheme.typography.bodyMedium)
            } }
        }
        if (draft == null) item { Section("لا توجد مسودة جاهزة") { Text("قد تكون المعالجة جارية أو فشلت. حدّث الحالة وافحص سجل السيرفر قبل إنشاء عقد بديل.") } }
        if (draft != null) {
            item { Section("متغيرات العقد") { FactsEditor(draft.facts, editable, true) { k, v -> vm.fact(k, v, true) } } }
            items(draft.clauses.indices.toList(), key = { "clause-${draft.clauses[it].id}" }) { index ->
                ClauseEditor(draft.clauses[index], editable) { lang, value -> vm.clause(index, lang, value) }
            }
            if (contract.waiting) item { Section("قرار مسؤول الامتثال", "يتحقق السيرفر من صلاحية المفتاح وحساب الضمان. تغيير النص يتطلب مراجعة اللغتين.") {
                CheckLine("راجعت البنود والمتطلبات القانونية كاملة", s.legal, !s.busy) { vm.reviewFields(legal = it) }
                CheckLine("تحققت من تطابق المعنى العربي والإنجليزي", s.bilingual, !s.busy) { vm.reviewFields(bilingual = it) }
                CheckLine("راجعت المصادر وعالجت النقاط غير المحسومة", s.sources, !s.busy) { vm.reviewFields(sources = it) }
                OutlinedTextField(s.escrowReference, { vm.reviewFields(reference = it) }, Modifier.fillMaxWidth(), enabled = !s.busy, label = { Text("مرجع تحقق حساب الضمان") })
                OutlinedTextField(s.note, { vm.reviewFields(note = it) }, Modifier.fillMaxWidth(), enabled = !s.busy, minLines = 3, label = { Text("ملاحظة المراجعة / سبب الرفض") })
                Button(onClick = { decide(true) }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("اعتماد وإتمام العقد") }
                OutlinedButton(onClick = { decide(false) }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("رفض العقد", color = MaterialTheme.colorScheme.error) }
            } }
            if (contract.finalized) item { Section("النسخة المعتمدة") {
                Text("الـPDF صادر من النص الذي اعتمده المراجع.")
                contract.approvedHash?.let { Text("SHA-256: $it", style = MaterialTheme.typography.labelSmall) }
                Button(onClick = savePdf, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("حفظ PDF") }
            } }
        }
    }
    if (reloadConfirm) AlertDialog(onDismissRequest = { reloadConfirm = false }, title = { Text("استبدال تعديلاتك؟") },
        text = { Text("سيتم تحميل نسخة السيرفر وفقد التعديلات المحلية غير المرسلة.") },
        confirmButton = { TextButton(onClick = { reloadConfirm = false; vm.open(contract.id) }) { Text("تحديث") } },
        dismissButton = { TextButton(onClick = { reloadConfirm = false }) { Text("إلغاء") } })
}
@Composable
private fun ClauseEditor(clause: Clause, enabled: Boolean, update: (String, String) -> Unit) {
    Section(clause.id.replace('_', ' ')) {
        BoxWithConstraints {
            if (maxWidth >= 640.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguageEditor("العربية", clause.ar, true, enabled, Modifier.weight(1f)) { update("ar", it) }
                LanguageEditor("English", clause.en, false, enabled, Modifier.weight(1f)) { update("en", it) }
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguageEditor("العربية", clause.ar, true, enabled, Modifier.fillMaxWidth()) { update("ar", it) }
                LanguageEditor("English", clause.en, false, enabled, Modifier.fillMaxWidth()) { update("en", it) }
            }
        }
        OutlinedTextField(clause.sourceReference, { update("source", it) }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("المرجع القانوني / مصدر البند") }, minLines = 2)
    }
}
@Composable
private fun LanguageEditor(label: String, value: String, arabic: Boolean, enabled: Boolean, modifier: Modifier, update: (String) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        OutlinedTextField(value, update, modifier, enabled = enabled, label = { Text(label) }, minLines = 4,
            textStyle = LocalTextStyle.current.copy(textDirection = if (arabic) TextDirection.Rtl else TextDirection.Ltr))
    }
}
@Composable
private fun CheckLine(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, change, enabled = enabled); Text(label, style = MaterialTheme.typography.bodyMedium) }
}
