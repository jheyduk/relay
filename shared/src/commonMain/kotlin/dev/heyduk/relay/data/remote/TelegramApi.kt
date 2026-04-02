package dev.heyduk.relay.data.remote

import dev.heyduk.relay.data.remote.dto.TelegramMessage
import dev.heyduk.relay.data.remote.dto.TelegramResponse
import dev.heyduk.relay.data.remote.dto.TelegramUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Telegram Bot API client contract for reading updates and sending messages.
 */
interface TelegramApi {
    suspend fun getUpdates(
        offset: Long = 0,
        timeout: Int = 30,
        allowedUpdates: List<String> = listOf("message", "callback_query")
    ): List<TelegramUpdate>

    suspend fun sendMessage(text: String): TelegramMessage
}

/**
 * Ktor-based implementation of [TelegramApi].
 * Supports getUpdates (long-polling) and sendMessage.
 */
class TelegramApiImpl(
    private val httpClient: HttpClient,
    private val botToken: String,
    private val chatId: String
) : TelegramApi {
    private val baseUrl = "https://api.telegram.org/bot$botToken"

    /**
     * Long-poll for updates from the Telegram Bot API.
     * Per-request timeout overrides ensure the connection stays open for long polling.
     */
    override suspend fun getUpdates(
        offset: Long,
        timeout: Int,
        allowedUpdates: List<String>
    ): List<TelegramUpdate> {
        val response: TelegramResponse<List<TelegramUpdate>> = httpClient.get("$baseUrl/getUpdates") {
            parameter("offset", offset)
            parameter("timeout", timeout)
            parameter("allowed_updates", Json.encodeToString(allowedUpdates))
            timeout {
                socketTimeoutMillis = (timeout + 5) * 1000L
                requestTimeoutMillis = (timeout + 10) * 1000L
            }
        }.body()

        if (!response.ok) {
            throw TelegramApiException(
                errorCode = response.errorCode ?: 0,
                message = response.description ?: "Unknown error"
            )
        }
        return response.result ?: emptyList()
    }

    /**
     * Send a message to the configured chat via Telegram Bot API.
     */
    override suspend fun sendMessage(text: String): TelegramMessage {
        val response: TelegramResponse<TelegramMessage> = httpClient.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(chatId = chatId, text = text))
        }.body()

        if (!response.ok) {
            throw TelegramApiException(
                errorCode = response.errorCode ?: 0,
                message = response.description ?: "Unknown error"
            )
        }
        return response.result!!
    }
}

/**
 * Exception thrown when the Telegram Bot API returns ok=false.
 */
class TelegramApiException(val errorCode: Int, message: String) : Exception(message)

@kotlinx.serialization.Serializable
private data class SendMessageRequest(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String,
    val text: String
)
