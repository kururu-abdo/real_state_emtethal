package sa.emtethal.realestate

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException

class ContractApiTest {
    private val draft = Draft(Facts.demo(), listOf(Clause("parties", "الأطراف", "Parties", "Counsel reference")))
    @Test fun `review request preserves bilingual edits and sends authentication`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val contract = Contract("run-1", "finalized_demo", 1, draft, demo = true)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(ApiFactory.json.encodeToString(contract)))
            val api = ApiFactory.create(server.url("/").toString(), "reviewer-secret", true)
            val result = api.review("run-1", Approval(1, draft, "approve", "Reviewed", true, true, true, "BANK-001"))
            assertTrue(result.finalized)
            val request = server.takeRequest()
            assertEquals("/api/contracts/run-1/review", request.path)
            assertEquals("Bearer reviewer-secret", request.getHeader("Authorization"))
            val json = ApiFactory.json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("1", json.getValue("expected_revision").jsonPrimitive.content)
            assertEquals("true", json.getValue("bilingual_equivalence_verified").jsonPrimitive.content)
            val returnedDraft = ApiFactory.json.decodeFromJsonElement(Draft.serializer(), json.getValue("draft"))
            assertEquals(draft, returnedDraft)
        } finally { server.shutdown() }
    }
    @Test fun `unauthorized responses remain failures`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{\"detail\":\"Invalid token\"}"))
            val api = ApiFactory.create(server.url("/").toString(), "wrong-token", true)
            try { api.contracts(); fail("Expected authentication failure") }
            catch (error: HttpException) { assertEquals(401, error.code()) }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun `processing and finalized payloads tolerate null draft and extra fields`() {
        val item = ApiFactory.json.decodeFromString<Contract>("""{"id":"x","status":"processing","draft":null,"demo":null,"waiting":false,"future_field":true}""")
        assertNull(item.draft)
        assertFalse(item.finalized)
    }
    @Test fun `release endpoint requires HTTPS and cannot contain credentials`() {
        assertEquals("https://example.com/service/", ApiFactory.normalizeUrl("https://example.com/service", false))
        assertThrows(IllegalArgumentException::class.java) { ApiFactory.normalizeUrl("http://example.com", false) }
        assertThrows(IllegalArgumentException::class.java) { ApiFactory.normalizeUrl("https://user:pass@example.com", false) }
    }
    @Test fun `project validation rejects invalid dates prices and blank required fields`() {
        assertNull(Facts.demo().error())
        assertNotNull(Facts.demo().copy(handoverDate = "2028-02-30").error())
        assertNotNull(Facts.demo().copy(priceSar = "-5").error())
        assertNotNull(Facts.demo().copy(priceSar = "5.123").error())
        assertNotNull(Facts.demo().copy(buyer = " ").error())
    }
}
