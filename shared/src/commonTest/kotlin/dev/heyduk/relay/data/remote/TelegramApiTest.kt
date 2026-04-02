package dev.heyduk.relay.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelegramApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun createMockClient(responseBody: String): HttpClient {
        val engine = MockEngine { _ ->
            respond(content = responseBody, headers = jsonHeaders)
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    @Test
    fun getUpdatesWithEmptyResultReturnsEmptyList() = runTest {
        val client = createMockClient("""{"ok":true,"result":[]}""")
        val api = TelegramApiImpl(client, "test-token", "123")
        val result = api.getUpdates()
        assertTrue(result.isEmpty())
    }

    @Test
    fun getUpdatesWithUpdatesReturnsParsedList() = runTest {
        val responseJson = """
            {
                "ok": true,
                "result": [
                    {
                        "update_id": 100,
                        "message": {
                            "message_id": 1,
                            "text": "hello",
                            "date": 1700000000,
                            "chat": {"id": 123, "type": "private"}
                        }
                    }
                ]
            }
        """.trimIndent()
        val client = createMockClient(responseJson)
        val api = TelegramApiImpl(client, "test-token", "123")
        val result = api.getUpdates()
        assertEquals(1, result.size)
        assertEquals(100L, result[0].updateId)
        assertEquals("hello", result[0].message?.text)
    }

    @Test
    fun getUpdatesSetsCorrectQueryParams() = runTest {
        var capturedUrl = ""
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(content = """{"ok":true,"result":[]}""", headers = jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val api = TelegramApiImpl(client, "test-token", "123")
        api.getUpdates(offset = 42, timeout = 30)
        assertTrue(capturedUrl.contains("offset=42"), "URL should contain offset=42, was: $capturedUrl")
        assertTrue(capturedUrl.contains("timeout=30"), "URL should contain timeout=30, was: $capturedUrl")
    }

    @Test
    fun sendMessagePostsJsonBodyAndReturnsMessage() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedMethod = request.method
            capturedBody = String(request.body.toByteArray(), Charsets.UTF_8)
            respond(
                content = """
                    {
                        "ok": true,
                        "result": {
                            "message_id": 42,
                            "text": "test message",
                            "date": 1700000000,
                            "chat": {"id": 123, "type": "private"}
                        }
                    }
                """.trimIndent(),
                headers = jsonHeaders
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val api = TelegramApiImpl(client, "test-token", "123")
        val result = api.sendMessage("test message")
        assertEquals(HttpMethod.Post, capturedMethod)
        assertTrue(capturedBody.contains("123")) // chat_id
        assertTrue(capturedBody.contains("test message")) // text
        assertEquals(42L, result.messageId)
    }

    @Test
    fun getUpdatesWithOkFalseThrowsTelegramApiException() = runTest {
        val client = createMockClient("""{"ok":false,"error_code":401,"description":"Unauthorized"}""")
        val api = TelegramApiImpl(client, "test-token", "123")
        val exception = assertFailsWith<TelegramApiException> {
            api.getUpdates()
        }
        assertEquals(401, exception.errorCode)
        assertEquals("Unauthorized", exception.message)
    }
}
