package dev.heyduk.relay.di

import dev.heyduk.relay.data.remote.RelayMessageParser
import dev.heyduk.relay.data.remote.TelegramApi
import dev.heyduk.relay.data.remote.TelegramApiImpl
import dev.heyduk.relay.data.remote.TelegramPoller
import dev.heyduk.relay.data.repository.ChatRepository
import dev.heyduk.relay.data.repository.ChatRepositoryImpl
import dev.heyduk.relay.data.repository.SessionRepository
import dev.heyduk.relay.data.repository.SessionRepositoryImpl
import dev.heyduk.relay.data.repository.TelegramRepository
import dev.heyduk.relay.data.repository.TelegramRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Shared Koin module for the transport layer.
 *
 * Platform-specific modules must provide:
 * - named("relayBotToken"): String
 * - named("commandBotToken"): String
 * - named("chatId"): String
 * - OffsetProvider
 * - RelayDatabase
 */
val sharedModule = module {
    // Ktor HttpClient with sensible defaults
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
                requestTimeoutMillis = 15_000
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    // Two TelegramApi instances: one for relay bot (reading), one for command bot (writing)
    single<TelegramApi>(named("relayApi")) {
        TelegramApiImpl(
            httpClient = get(),
            botToken = get(named("relayBotToken")),
            chatId = get(named("chatId"))
        )
    }
    single<TelegramApi>(named("commandApi")) {
        TelegramApiImpl(
            httpClient = get(),
            botToken = get(named("commandBotToken")),
            chatId = get(named("chatId"))
        )
    }

    single { RelayMessageParser }

    single {
        TelegramPoller(
            api = get(named("relayApi")),
            parser = get(),
            offsetProvider = get()
        )
    }

    single<TelegramRepository> {
        TelegramRepositoryImpl(
            poller = get(),
            commandApi = get(named("commandApi")),
            database = get()
        )
    }

    // Session repository: discovers sessions via /ls and parses update stream
    single<SessionRepository> { SessionRepositoryImpl(get()) }

    // Chat repository: per-session message history and send-with-persist
    single<ChatRepository> { ChatRepositoryImpl(get(), get()) }
}
