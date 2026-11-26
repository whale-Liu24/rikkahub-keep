package me.rerere.rikkahub.ui.pages.chat

private const val AsciiWordChar = "[A-Za-z0-9_]"
private const val SnippetContextLength = 24
private const val SnippetMaxLength = 300

internal class HomePreviewSearch(query: String, wholeWord: Boolean) {
    private val pattern = query.trim().takeIf { it.isNotEmpty() }?.let { trimmedQuery ->
        val literal = Regex.escape(trimmedQuery)
        // Keep the boundary ASCII even when the query itself uses Unicode case folding.
        val left = if (wholeWord && trimmedQuery.first().isAsciiWordChar()) "(?<!(?-i:$AsciiWordChar))" else ""
        val right = if (wholeWord && trimmedQuery.last().isAsciiWordChar()) "(?!(?-i:$AsciiWordChar))" else ""
        Regex(left + literal + right, RegexOption.IGNORE_CASE)
    }

    fun matches(text: String): Boolean = pattern?.containsMatchIn(text) ?: true

    fun ranges(text: String): Sequence<IntRange> =
        pattern?.findAll(text)?.map { it.range } ?: emptySequence()

    fun snippet(text: String): HomePreviewSnippet {
        val matches = ranges(text).iterator()
        var match = if (matches.hasNext()) matches.next() else null
        val matchIndex = match?.first ?: 0
        val start = if (matchIndex > SnippetContextLength) {
            val contextStart = matchIndex - SnippetContextLength
            val space = text.indexOf(' ', contextStart)
            if (space in contextStart until matchIndex) space + 1 else contextStart
        } else {
            0
        }
        val prefix = if (start > 0) "…" else ""
        val end = start + (text.length - start).coerceAtMost(SnippetMaxLength - prefix.length)
        val highlights = buildList {
            val offset = prefix.length - start
            while (true) {
                val range = match ?: break
                if (range.last >= end) break
                add((range.first + offset)..(range.last + offset))
                match = if (matches.hasNext()) matches.next() else null
            }
        }
        return HomePreviewSnippet(prefix + text.substring(start, end), highlights)
    }
}

internal data class HomePreviewSnippet(val text: String, val highlights: List<IntRange>)

private fun Char.isAsciiWordChar(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_'
