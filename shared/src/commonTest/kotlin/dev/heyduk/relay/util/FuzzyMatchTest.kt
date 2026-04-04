package dev.heyduk.relay.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzyMatchTest {

    @Test
    fun exactMatchScoresHigh() {
        val score = fuzzyMatch("relay", "relay")
        assertTrue(score > 0, "Exact match should score > 0, got $score")
    }

    @Test
    fun prefixMatchScoresPositive() {
        val score = fuzzyMatch("rel", "relay")
        assertTrue(score > 0, "Prefix 'rel' should match 'relay', got $score")
    }

    @Test
    fun nonConsecutiveMatchScoresPositive() {
        val score = fuzzyMatch("rl", "relay")
        assertTrue(score > 0, "Non-consecutive 'rl' should match 'relay', got $score")
    }

    @Test
    fun noMatchReturnsZero() {
        val score = fuzzyMatch("xyz", "relay")
        assertEquals(0, score, "No match should return 0")
    }

    @Test
    fun caseInsensitiveMatch() {
        val score = fuzzyMatch("REL", "relay")
        assertTrue(score > 0, "Case-insensitive 'REL' should match 'relay', got $score")
    }

    @Test
    fun startOfWordBonusScoresHigher() {
        val foFischer = fuzzyMatch("fo", "fischer-operations")
        val foInfolog = fuzzyMatch("fo", "infolog")
        assertTrue(
            foFischer > foInfolog,
            "Start-of-word 'fo' should score higher on 'fischer-operations' ($foFischer) than 'infolog' ($foInfolog)"
        )
    }

    @Test
    fun emptyQueryMatchesEverything() {
        val score = fuzzyMatch("", "anything")
        assertTrue(score > 0, "Empty query should match everything, got $score")
    }

    @Test
    fun emptyTargetReturnsZero() {
        val score = fuzzyMatch("abc", "")
        assertEquals(0, score, "Empty target should return 0 for non-empty query")
    }

    @Test
    fun emptyQueryEmptyTargetMatches() {
        val score = fuzzyMatch("", "")
        assertTrue(score > 0, "Empty query on empty target should match")
    }
}
