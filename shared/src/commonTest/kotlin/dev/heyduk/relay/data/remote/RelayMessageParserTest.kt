package dev.heyduk.relay.data.remote

import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.domain.model.SessionStatus
import dev.heyduk.relay.domain.model.DirectoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun directoryListParsesToRelayUpdateWithDirectoryEntries() {
        val json = """
            {
                "type": "directory_list",
                "session": "_system",
                "directories": [{"path": "/Users/jheyduk/prj/relay", "name": "relay"}],
                "defaultFlags": "--dangerously-skip-permissions"
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 10L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.DIRECTORY_LIST, result.type)
        assertEquals("_system", result.session)
        assertEquals(1, result.directoryList?.size)
        assertEquals(DirectoryEntry("/Users/jheyduk/prj/relay", "relay"), result.directoryList?.first())
        assertEquals("--dangerously-skip-permissions", result.defaultFlags)
    }

    @Test
    fun sessionCreatedSuccessParsesToRelayUpdate() {
        val json = """
            {
                "type": "session_created",
                "session": "_system",
                "kuerzel": "relay",
                "path": "/Users/jheyduk/prj/relay",
                "success": true
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 11L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.SESSION_CREATED, result.type)
        assertEquals("relay", result.sessionCreatedKuerzel)
        assertEquals(true, result.sessionCreatedSuccess)
        assertNull(result.sessionCreatedError)
    }

    @Test
    fun sessionCreatedFailureParsesErrorMessage() {
        val json = """
            {
                "type": "session_created",
                "session": "_system",
                "success": false,
                "error": "Directory does not exist"
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 12L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.SESSION_CREATED, result.type)
        assertEquals(false, result.sessionCreatedSuccess)
        assertEquals("Directory does not exist", result.sessionCreatedError)
    }

    @Test
    fun emptyDirectoryListParsesToEmptyList() {
        val json = """
            {
                "type": "directory_list",
                "session": "_system",
                "directories": []
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 13L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.DIRECTORY_LIST, result.type)
        val dirs = result.directoryList
        assertNotNull(dirs)
        assertTrue(dirs.isEmpty())
    }

    @Test
    fun lastResponseWithNoChangeFlagParsesCorrectly() {
        val json = """
            {
                "type": "last_response",
                "session": "test",
                "message": "No updates",
                "success": true,
                "no_change": true
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 20L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.LAST_RESPONSE, result.type)
        assertTrue(result.noChange)
    }

    @Test
    fun lastResponseWithoutNoChangeFlagDefaultsToFalse() {
        val json = """
            {
                "type": "last_response",
                "session": "test",
                "message": "Some content here",
                "success": true
            }
        """.trimIndent()
        val result = RelayMessageParser.parse(updateId = 21L, messageText = json, timestamp = 9999L)
        assertNotNull(result)
        assertEquals(RelayMessageType.LAST_RESPONSE, result.type)
        assertFalse(result.noChange)
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
