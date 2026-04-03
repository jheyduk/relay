package dev.heyduk.relay.di

import dev.heyduk.relay.data.remote.RelayMessageParser
import dev.heyduk.relay.data.remote.WebSocketClient
import dev.heyduk.relay.data.repository.ChatRepository
import dev.heyduk.relay.data.repository.ChatRepositoryImpl
import dev.heyduk.relay.data.repository.RelayRepository
import dev.heyduk.relay.data.repository.RelayRepositoryImpl
import dev.heyduk.relay.data.repository.SessionRepository
import dev.heyduk.relay.data.repository.SessionRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Shared Koin module for the WebSocket transport layer.
 *
 * Platform-specific modules must provide:
 * - RelayDatabase
 */
val sharedModule = module {
    // Ktor HttpClient with WebSocket support
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(WebSockets)
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    single { RelayMessageParser }

    single { WebSocketClient(httpClient = get(), parser = get()) }

    single<RelayRepository> { RelayRepositoryImpl(webSocketClient = get(), database = get()) }

    // Session repository: discovers sessions from database (populated by PollingService)
    single<SessionRepository> { SessionRepositoryImpl(get(), get()) }

    // Chat repository: per-session message history and send-with-persist
    single<ChatRepository> { ChatRepositoryImpl(get<RelayRepository>(), get()) }
}
