package me.rerere.rikkahub.ui.components.richtext

// JetBrains Markdown's JVM whitespace check includes Character.isSpaceChar.
private const val HairSpace = '\u200A'
private val fenceLine = Regex("""(?m)^([ \t]*(?:(?:>[ \t]*)|(?:[-+*]|[0-9]+[.)])[ \t]+)*)(`{3,}|~{3,})([^\r\n]*)""")

/** Repairs strong delimiters in prose before BetterText parses it. Stored message text is untouched. */
internal fun normalizeHomeMarkdown(content: String): String {
    if ("**" !in content) return content

    val edits = mutableListOf<MarkdownEdit>()
    val delimiters = mutableListOf<StrongDelimiter>()
    var fence = fenceLine.find(content)
    var index = 0

    fun finishProse() {
        repairStrongDelimiters(content, delimiters, edits)
        delimiters.clear()
    }

    while (index < content.length) {
        while (fence != null && fence.range.first < index) fence = fence.next()
        val currentFence = fence
        if (currentFence != null && currentFence.range.first == index) {
            finishProse()
            val opener = currentFence.groupValues[2]
            val quoteDepth = currentFence.groupValues[1].count { it == '>' }
            fence = generateSequence(currentFence.next()) { it.next() }.firstOrNull {
                val prefix = it.groupValues[1]
                val closer = it.groupValues[2]
                prefix.count { char -> char == '>' } == quoteDepth &&
                    prefix.all { char -> char == '>' || char.isWhitespace() } &&
                    closer.first() == opener.first() && closer.length >= opener.length &&
                    it.groupValues[3].isBlank()
            }
            index = fence?.range?.last?.plus(1) ?: content.length
            fence = fence?.next()
            continue
        }

        val char = content[index]
        val next = content.getOrNull(index + 1)
        when {
            char == '\r' || char == '\n' -> {
                finishProse()
                index++
            }
            char == '`' || char == '$' -> {
                finishProse()
                val end = content.runEnd(index, char)
                index = skipDelimitedRun(content, end, char, end - index)
            }
            char == '\\' && (next == '(' || next == '[') -> {
                finishProse()
                val closer = if (content[index + 1] == '(') ')' else ']'
                index = skipLatex(content, index + 2, closer)
            }
            char == '\\' && next != null && next != '\r' && next != '\n' -> index += 2
            char == '*' -> {
                val end = content.runEnd(index, char)
                if (end - index == 2 && content.getOrNull(index - 1) != '*') {
                    delimiters += StrongDelimiter(index, content.boundary(index - 1), content.boundary(end))
                }
                index = end
            }
            else -> index++
        }
    }
    finishProse()
    if (edits.isEmpty()) return content
    return buildString(content.length) {
        var cursor = 0
        for ((offset, length, replacement) in edits) {
            append(content, cursor, offset)
            append(replacement)
            cursor = offset + length
        }
        append(content, cursor, content.length)
    }
}

private data class MarkdownEdit(val offset: Int, val length: Int, val replacement: String)

private data class StrongDelimiter(val index: Int, val left: Boundary, val right: Boundary) {
    val canOpen: Boolean get() = !right.space && (!right.punctuation || left.separates)
    val canClose: Boolean get() = !left.space && (!left.punctuation || right.separates)
    val repairOpen: Boolean get() = !canOpen && right.punctuation && left.symbol
    val repairClose: Boolean get() = !canClose && left.punctuation && right.symbol
}

private data class Boundary(val space: Boolean, val punctuation: Boolean, val cjk: Boolean, val symbol: Boolean) {
    val separates: Boolean get() = space || punctuation || cjk
}

private fun String.boundary(index: Int): Boundary {
    val char = getOrNull(index) ?: return Boundary(true, false, false, false)
    val type = Character.getType(char)
    val punctuation = char in "$^`" || type in punctuationTypes
    val cjk = char in '\u2E80'..'\u9FFF' || char in '\uAC00'..'\uD7AF' || char in '\uF900'..'\uFAFF'
    val symbol = Character.isSurrogate(char) || type in symbolTypes
    return Boundary(char == '\u0000' || Character.isSpaceChar(char) || char.isWhitespace(), punctuation, cjk, symbol)
}

private val punctuationTypes = setOf(
    Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(),
    Character.END_PUNCTUATION.toInt(), Character.CONNECTOR_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
    Character.FINAL_QUOTE_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
)

private val symbolTypes = setOf(
    Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(), Character.OTHER_SYMBOL.toInt(),
)

private fun repairStrongDelimiters(text: String, runs: List<StrongDelimiter>, edits: MutableList<MarkdownEdit>) {
    var index = 0
    while (index + 1 < runs.size) {
        val open = runs[index]
        if (!open.canOpen && !open.repairOpen) {
            index++
            continue
        }
        val next = runs[index + 1]
        val innerClose = runs.getOrNull(index + 2)
        val outerClose = runs.getOrNull(index + 3)
        if (innerClose != null && outerClose != null && next.canOpen && innerClose.canClose && innerClose.right.punctuation &&
            (outerClose.canClose || outerClose.repairClose) &&
            hasWrappingBrackets(text, open, next, innerClose, outerClose)
        ) {
            if (open.repairOpen) edits += MarkdownEdit(open.index, 0, HairSpace.toString())
            edits += MarkdownEdit(next.index, 2, "")
            edits += MarkdownEdit(innerClose.index, 2, "")
            if (outerClose.repairClose) edits += MarkdownEdit(outerClose.index + 2, 0, HairSpace.toString())
            index += 4
        } else if (next.canClose || next.repairClose) {
            if (open.repairOpen) edits += MarkdownEdit(open.index, 0, HairSpace.toString())
            if (next.repairClose) edits += MarkdownEdit(next.index + 2, 0, HairSpace.toString())
            index += 2
        } else {
            index++
        }
    }
}

private fun hasWrappingBrackets(
    text: String,
    open: StrongDelimiter,
    innerOpen: StrongDelimiter,
    innerClose: StrongDelimiter,
    outerClose: StrongDelimiter,
): Boolean {
    val opening = text[innerOpen.index - 1]
    val closing = when (opening) {
        '(' -> ')'
        '（' -> '）'
        '[' -> ']'
        '【' -> '】'
        else -> return false
    }
    // The bracket must surround the inner emphasis within the outer span.
    // In parallel strong spans the first closer belongs to the first phrase.
    if (innerOpen.index - 1 < open.index + 2) return false
    for (offset in innerClose.index + 2 until outerClose.index) {
        if (text[offset] == closing) return true
    }
    return false
}

private fun String.runEnd(start: Int, marker: Char): Int {
    var end = start
    while (getOrNull(end) == marker) end++
    return end
}

private fun skipDelimitedRun(text: String, start: Int, marker: Char, length: Int): Int {
    var index = start
    while (index < text.length) {
        if (marker == '$' && text[index] == '\\') {
            index += 2
        } else if (text[index] == marker) {
            val end = text.runEnd(index, marker)
            if (end - index == length) return end
            index = end
        } else {
            index++
        }
    }
    // The closing delimiter may arrive in the next streaming update.
    return text.length
}

private fun skipLatex(text: String, start: Int, closer: Char): Int {
    var index = start
    while (index < text.length) {
        if (text[index] == '\\') {
            if (text.getOrNull(index + 1) == closer) return index + 2
            index += 2
        } else {
            index++
        }
    }
    return text.length
}
