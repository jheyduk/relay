package dev.heyduk.relay.data.remote

import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.domain.model.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayMessageParserTest {

    @Test
    fun validJsonWithStatusTypeParsesToRelayUpdate() {
        val json = """
            {
                "type": "status",
                "session": "infra",
                "status": "working",
                "message": "Running tests...",
                "timestamp": 1700000000
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 1L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.STATUS, result.type)
        assertEquals("infra", result.session)
        assertEquals(SessionStatus.WORKING, result.status)
        assertEquals("Running tests...", result.message)
        assertEquals(1700000000L, result.timestamp) // uses relay timestamp, not fallback
    }

    @Test
    fun validJsonWithPermissionAndToolDetailsParsesFields() {
        val json = """
            {
                "type": "permission",
                "session": "hub",
                "message": "Allow file write?",
                "tool_details": {
                    "tool_name": "Write",
                    "command": "write_file",
                    "file_path": "/tmp/test.kt"
                },
                "timestamp": 1700000001
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 2L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.PERMISSION, result.type)
        assertEquals("Write", result.toolName)
        assertEquals("write_file", result.command)
        assertEquals("/tmp/test.kt", result.filePath)
    }

    @Test
    fun invalidJsonReturnsNull() {
        val result = RelayMessageParser.parse(updateId = 3L, messageText = "{broken json", timestamp = 9999L)
        assertNull(result)
    }

    @Test
    fun plainTextReturnsNull() {
        val result = RelayMessageParser.parse(updateId = 4L, messageText = "Hello, world!", timestamp = 9999L)
        assertNull(result)
    }

    @Test
    fun jsonWithRelayMarkerTrueIsAccepted() {
        val json = """
            {
                "type": "response",
                "session": "infra",
                "message": "Done",
                "__relay": true,
                "timestamp": 1700000002
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 5L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.RESPONSE, result.type)
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val json = """
            {
                "type": "completion",
                "session": "hub",
                "message": "All done"
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 6L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.COMPLETION, result.type)
        assertNull(result.status)
        assertNull(result.toolName)
        assertEquals(9999L, result.timestamp) // fallback timestamp since relay timestamp is 0
    }
}
