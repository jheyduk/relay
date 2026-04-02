package dev.heyduk.relay.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null,
    @SerialName("callback_query") val callbackQuery: CallbackQuery? = null
)

@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val text: String? = null,
    val date: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat
)

@Serializable
data class TelegramUser(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean,
    @SerialName("first_name") val firstName: String
)

@Serializable
data class TelegramChat(
    val id: Long,
    val type: String
)

@Serializable
data class CallbackQuery(
    val id: String,
    val data: String? = null,
    val message: TelegramMessage? = null
)
