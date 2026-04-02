package dev.heyduk.relay.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)
