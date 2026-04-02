package dev.heyduk.relay.data.remote

import dev.heyduk.relay.data.remote.dto.TelegramChat
import dev.heyduk.relay.data.remote.dto.TelegramMessage
import dev.heyduk.relay.data.remote.dto.TelegramUpdate
import dev.heyduk.relay.domain.model.RelayUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramPollerTest {

    private class FakeOffsetProvider : OffsetProvider {
        var currentOffset = 0L
        override suspend fun getOffset(): Long = currentOffset
        override suspend fun setOffset(offset: Long) {
            currentOffset = offset
        }
    }

    /**
     * Fake API that returns queued responses, then suspends indefinitely.
     * This prevents the poller from running away after all responses are consumed.
     */
    private class FakeApi : TelegramApi {
        val responses: MutableList<Result<List<TelegramUpdate>>> = mutableListOf()
        var callCount = 0
        var lastRequestedOffset = 0L

        override suspend fun getUpdates(offset: Long, timeout: Int, allowedUpdates: List<String>): List<TelegramUpdate> {
            val index = callCount++
            lastRequestedOffset = offset
            if (index >= responses.size) {
                // Suspend indefinitely to prevent runaway polling
                delay(Long.MAX_VALUE)
            }
            return responses[index].getOrThrow()
        }

        override suspend fun sendMessage(text: String): TelegramMessage {
            error("Not expected in poller tests")
        }
    }

    private fun makeUpdate(id: Long, text: String, date: Long = 1700000000L): TelegramUpdate {
        return TelegramUpdate(
            updateId = id,
            message = TelegramMessage(
                messageId = id,
                text = text,
                date = date,
                chat = TelegramChat(id = 123, type = "private")
            )
        )
    }

    private val relayJson = """{"type":"status","session":"infra","status":"working","message":"Running","timestamp":1700000000}"""

    @Test
    fun onSuccessfulGetUpdatesEmitsParsedRelayUpdates() = runTest {
        val fakeApi = FakeApi()
        fakeApi.responses.add(Result.success(listOf(makeUpdate(100, relayJson))))

        val offsetProvider = FakeOffsetProvider()
        val poller = TelegramPoller(fakeApi, RelayMessageParser, offsetProvider)

        val job = launch { poller.pollLoop() }
        val update = poller.updates.first()

        assertEquals("infra", update.session)
        assertEquals(100L, update.updateId)
        job.cancelAndJoin()
    }

    @Test
    fun onSuccessfulGetUpdatesPersistsNewOffset() = runTest {
        val fakeApi = FakeApi()
        fakeApi.responses.add(Result.success(listOf(makeUpdate(100, relayJson))))

        val offsetProvider = FakeOffsetProvider()
        val poller = TelegramPoller(fakeApi, RelayMessageParser, offsetProvider)

        val job = launch { poller.pollLoop() }
        poller.updates.first() // wait for first update to be processed
        job.cancelAndJoin()

        assertEquals(101L, offsetProvider.currentOffset) // maxUpdateId + 1
    }

    @Test
    fun onApiErrorBackoffIncreases() = runTest {
        val fakeApi = FakeApi()
        // Fail 3 times, then succeed with an update
        fakeApi.responses.add(Result.failure(TelegramApiException(500, "Server error")))
        fakeApi.responses.add(Result.failure(TelegramApiException(500, "Server error")))
        fakeApi.responses.add(Result.failure(TelegramApiException(500, "Server error")))
        fakeApi.responses.add(Result.success(listOf(makeUpdate(100, relayJson))))

        val offsetProvider = FakeOffsetProvider()
        val poller = TelegramPoller(fakeApi, RelayMessageParser, offsetProvider)

        // Start collecting before polling so we don't miss the emission
        var receivedUpdate: RelayUpdate? = null
        val collectJob = launch {
            receivedUpdate = poller.updates.first()
        }
        val pollJob = launch { poller.pollLoop() }

        // First call happens immediately -> fails -> backoff 1000ms
        // Advance 1000ms to trigger second call -> fails -> backoff 2000ms
        advanceTimeBy(1001)
        assertEquals(2, fakeApi.callCount, "After 1s backoff, should have 2 calls")

        // Advance 2000ms to trigger third call -> fails -> backoff 4000ms
        advanceTimeBy(2001)
        assertEquals(3, fakeApi.callCount, "After 2s backoff, should have 3 calls")

        // Advance 4000ms to trigger fourth call -> succeeds
        advanceTimeBy(4001)
        assertTrue(fakeApi.callCount >= 4, "After 4s backoff, should have >= 4 calls, got ${fakeApi.callCount}")

        assertEquals("infra", receivedUpdate?.session)
        collectJob.cancelAndJoin()
        pollJob.cancelAndJoin()
    }

    @Test
    fun onSuccessAfterErrorBackoffResets() = runTest {
        val fakeApi = FakeApi()
        // Fail once, then succeed twice
        fakeApi.responses.add(Result.failure(TelegramApiException(500, "Server error")))
        fakeApi.responses.add(Result.success(listOf(makeUpdate(100, relayJson))))
        fakeApi.responses.add(Result.success(listOf(makeUpdate(101, relayJson.replace("infra", "hub")))))

        val offsetProvider = FakeOffsetProvider()
        val poller = TelegramPoller(fakeApi, RelayMessageParser, offsetProvider)

        val collected = mutableListOf<RelayUpdate>()
        val collectJob = launch {
            poller.updates.collect { collected.add(it) }
        }
        val pollJob = launch { poller.pollLoop() }

        // First call immediate -> fails -> backoff 1000ms
        advanceTimeBy(1001) // trigger second call -> succeeds -> backoff resets to 0
        // Third call should happen immediately (no backoff after success)
        advanceTimeBy(1) // tiny advance to let the loop iterate

        assertTrue(fakeApi.callCount >= 3, "After success, backoff should reset -- expected >= 3 calls, got ${fakeApi.callCount}")
        collectJob.cancelAndJoin()
        pollJob.cancelAndJoin()
    }

    @Test
    fun emptyGetUpdatesResultDoesNotChangeOffset() = runTest {
        val fakeApi = FakeApi()
        fakeApi.responses.add(Result.success(emptyList()))
        fakeApi.responses.add(Result.success(listOf(makeUpdate(100, relayJson))))

        val offsetProvider = FakeOffsetProvider()
        offsetProvider.currentOffset = 50L
        val poller = TelegramPoller(fakeApi, RelayMessageParser, offsetProvider)

        val job = launch { poller.pollLoop() }
        poller.updates.first() // wait for the update from second call
        job.cancelAndJoin()

        // After second call with update 100, offset should be 101
        assertEquals(101L, offsetProvider.currentOffset)
        // Verify at least 2 calls were made (empty + update), may have started a 3rd
        assertTrue(fakeApi.callCount >= 2, "Expected >= 2 calls, got ${fakeApi.callCount}")
    }
}
