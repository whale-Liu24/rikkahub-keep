package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePreviewSearchTest {
    @Test
    fun substringModeIncludesEmbeddedOccurrencesAndHighlightsThem() {
        val search = HomePreviewSearch("  arm  ", wholeWord = false)
        val text = "arm's length, warmth, harmful, warm, ARM"
        val expected = listOf(0..2, 15..17, 23..25, 32..34, 37..39)

        assertTrue(search.matches("warmth"))
        assertEquals(expected, search.ranges(text).toList())
        assertEquals(expected, search.snippet(text).highlights)
    }

    @Test
    fun wholeWordModeFiltersAsciiNeighborsAndSharesHighlightRanges() {
        val search = HomePreviewSearch("  arm  ", wholeWord = true)
        val text = "arm's length, warmth, harmful, warm, ARM"
        val expected = listOf(0..2, 37..39)

        for (word in listOf("warmth", "harmful", "warm", "arm_1", "1arm")) {
            assertFalse(search.matches(word))
            assertTrue(search.snippet(word).highlights.isEmpty())
        }
        assertTrue(search.matches("arm's length"))
        assertTrue(search.matches("ARM"))
        assertEquals(expected, search.ranges(text).toList())
        assertEquals(expected, search.snippet(text).highlights)
        assertEquals(listOf(1..3), search.ranges("Karm测").toList())
    }

    @Test
    fun chineseAndLiteralPunctuationNeedNoUnrelatedWordBoundaries() {
        val search = HomePreviewSearch("测试", wholeWord = true)
        assertTrue(search.matches("测试短句"))
        assertEquals(listOf(1..2), search.snippet("a测试b").highlights)
        assertEquals(listOf(5..8), HomePreviewSearch("arm+", true).ranges("warm arm+1").toList())
        assertEquals(listOf(1..4), HomePreviewSearch("+arm", true).ranges("x+ARM x+arms").toList())
        assertTrue(HomePreviewSearch(" ", true).matches("测试短句"))
        assertTrue(HomePreviewSearch(" ", true).snippet("测试短句").highlights.isEmpty())
    }

    @Test
    fun snippetOffsetsRealMatchesWithoutInventingBoundariesAtTruncation() {
        val search = HomePreviewSearch("arm", wholeWord = true)
        val laterMatch = search.snippet("测试 ".repeat(20) + "warm, arm's length")
        assertTrue(laterMatch.text.startsWith("…"))
        assertEquals(listOf("arm"), laterMatch.highlights.map { laterMatch.text.substring(it) })

        val clippedWord = search.snippet("arm " + "x".repeat(293) + "armful")
        assertTrue(clippedWord.text.endsWith("arm"))
        assertEquals(listOf(0..2), clippedWord.highlights)
    }
}
