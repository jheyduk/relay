package dev.heyduk.relay.domain.parser

import dev.heyduk.relay.domain.model.Session
import dev.heyduk.relay.domain.model.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionListParserTest {

    @Test
    fun parseMultipleSessionsWithMixedStatuses() {
        val input = "@abc  working  (active)\n@xyz  ready"
        val result = SessionListParser.parse(input)
        assertEquals(2, result.size)
        assertEquals(Session("abc", SessionStatus.WORKING, "active", true), result[0])
        assertEquals(Session("xyz", SessionStatus.READY, null, false), result[1])
    }

    @Test
    fun parseShellStatus() {
        val input = "@test  shell"
        val result = SessionListParser.parse(input)
        assertEquals(1, result.size)
        assertEquals(Session("test", SessionStatus.SHELL, null, false), result[0])
    }

    @Test
    fun parseSkipsGarbageLines() {
        val input = "@foo  waiting  (active)\nsome garbage line\n@bar  ready"
        val result = SessionListParser.parse(input)
        assertEquals(2, result.size)
        assertEquals("foo", result[0].kuerzel)
        assertEquals("bar", result[1].kuerzel)
    }

    @Test
    fun parseEmptyStringReturnsEmptyList() {
        val result = SessionListParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseNoMatchesReturnsEmptyList() {
        val result = SessionListParser.parse("no matches here")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseSkipsUnknownStatus() {
        val input = "@abc  working  (active)\n@xyz  unknownstatus\n@def  ready"
        val result = SessionListParser.parse(input)
        assertEquals(2, result.size)
        assertEquals("abc", result[0].kuerzel)
        assertEquals("def", result[1].kuerzel)
    }
}
