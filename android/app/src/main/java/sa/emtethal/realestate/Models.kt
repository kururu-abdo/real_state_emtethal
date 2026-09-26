package sa.emtethal.realestate

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Facts(
    @SerialName("project_id") val projectId: String = "",
    val developer: String = "", val buyer: String = "", val unit: String = "",
    @SerialName("deed_number") val deedNumber: String = "",
    @SerialName("license_number") val licenseNumber: String = "",
    @SerialName("price_sar") val priceSar: String = "",
    @SerialName("handover_date") val handoverDate: String = "",
    @SerialName("escrow_iban") val escrowIban: String = ""
) {
    fun fields() = linkedMapOf("project_id" to projectId, "developer" to developer, "buyer" to buyer,
        "unit" to unit, "deed_number" to deedNumber, "license_number" to licenseNumber,
        "price_sar" to priceSar, "handover_date" to handoverDate, "escrow_iban" to escrowIban)
    fun update(key: String, value: String): Facts = when (key) {
        "project_id" -> copy(projectId = value); "developer" -> copy(developer = value)
        "buyer" -> copy(buyer = value); "unit" -> copy(unit = value)
        "deed_number" -> copy(deedNumber = value); "license_number" -> copy(licenseNumber = value)
        "price_sar" -> copy(priceSar = value); "handover_date" -> copy(handoverDate = value)
        "escrow_iban" -> copy(escrowIban = value); else -> this
    }
    fun error(): String? {
        if (fields().values.any { it.isBlank() || it.length > 250 }) return "أكمل بيانات المشروع؛ الحد الأقصى للحقل 250 حرفاً."
        val price = priceSar.toBigDecimalOrNull()
        if (price == null || price <= BigDecimal.ZERO || price.scale() > 2 || price.precision() > 15)
            return "أدخل سعراً موجباً بالأرقام الإنجليزية ومنزلتين عشريتين كحد أقصى."
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(handoverDate) || runCatching { LocalDate.parse(handoverDate) }.isFailure)
            return "أدخل تاريخ التسليم بالشكل YYYY-MM-DD."
        return null
    }
    companion object {
        fun demo() = Facts("DEMO-001", "المطور التجريبي / Demo Developer", "المشتري التجريبي / Demo Buyer",
            "A-101", "DEMO-DEED", "DEMO-LICENSE", "950000.00", "2028-06-30", "SA0380000000608010167519")
    }
}
@Serializable data class Clause(val id: String, val ar: String, val en: String,
    @SerialName("source_reference") val sourceReference: String)
@Serializable data class Draft(val facts: Facts, val clauses: List<Clause>)
@Serializable data class Evidence(val document: String, val page: Int, val quote: String, val field: String)
@Serializable data class Extraction(@SerialName("summary_ar") val summaryAr: String,
    @SerialName("summary_en") val summaryEn: String, val evidence: List<Evidence> = emptyList(),
    val unresolved: List<String> = emptyList())
@Serializable data class Contract(val id: String, val status: String, val revision: Int = 0,
    val draft: Draft? = null, val extraction: Extraction? = null, val demo: Boolean? = null,
    val waiting: Boolean = false, @SerialName("approved_hash") val approvedHash: String? = null) {
    val finalized get() = status == "finalized" || status == "finalized_demo"
}
@Serializable data class UploadedDocument(val id: String, val name: String, val pages: Int, val sha256: String)
@Serializable data class StartRequest(val facts: Facts,
    @SerialName("document_ids") val documentIds: List<String>, val framework: String = "wafi_sale")
@Serializable data class Approval(
    @SerialName("expected_revision") val expectedRevision: Int,
    val draft: Draft, val decision: String, val note: String,
    @SerialName("legal_reviewed") val legalReviewed: Boolean,
    @SerialName("bilingual_equivalence_verified") val bilingualVerified: Boolean,
    @SerialName("source_documents_verified") val sourcesVerified: Boolean,
    @SerialName("escrow_evidence_reference") val escrowReference: String
)
fun statusLabel(status: String) = when (status) {
    "awaiting_review" -> "بانتظار المراجعة"; "finalized" -> "معتمد"
    "finalized_demo" -> "معتمد • تجريبي"; "rejected" -> "مرفوض"
    "processing" -> "قيد المعالجة"; else -> status
}
