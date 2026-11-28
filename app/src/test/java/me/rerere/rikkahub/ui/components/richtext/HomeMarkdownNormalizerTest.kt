package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.ui.text.font.FontWeight
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownDocument
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.bettertext.markdown.MarkdownStreamParser
import me.rerere.bettertext.markdown.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import me.rerere.rikkahub.data.ai.HomeHttpLogStoreTest
import org.junit.Test
import org.junit.runner.JUnitCore

class HomeMarkdownNormalizerTest {
    // The release workflow filters this class by name.
    @Test
    fun runHttpLoggingRegressionTests() {
        val result = JUnitCore.runClasses(HomeHttpLogStoreTest::class.java)
        assertTrue(result.failures.joinToString("\n") { it.trace }, result.wasSuccessful())
    }

    @Test
    fun repairsSymbolBoundariesAndKeepsValidCjkClosingDelimiter() {
        assertRendered("**——（（……测试甲……测试乙……））**🌕", "——（（……测试甲……测试乙……））" to true, "🌕" to false)
        assertRendered("🌕**（（测试内容））**", "🌕" to false, "（（测试内容））" to true)
        assertRendered("**测试内容：**🥺", "测试内容：" to true, "🥺" to false)
        assertRendered("**测试内容：**©", "测试内容：" to true, "©" to false)
        val cjk = "**测试内容：**紧接文字"
        assertEquals(cjk, normalizeHomeMarkdown(cjk))
        assertRendered(cjk, "测试内容：" to true, "紧接文字" to false)
    }

    @Test
    fun flattensBracketedNestedStrongWithoutLeavingStars() {
        assertRendered(
            "**——（（**关键词**……测试内容……））**🌕",
            "——（（关键词……测试内容……））" to true,
            "🌕" to false,
        )
        assertRendered("**前文（**关键词**）后文**", "前文（关键词）后文" to true)
    }

    @Test
    fun keepsAdjacentStrongSpansAndTheirPlainSeparators() {
        val fixtures = listOf(
            "**第一项**和**第二项**" to listOf("第一项" to true, "和" to false, "第二项" to true),
            "**重点一**：说明，**重点二**：说明" to
                listOf("重点一" to true, "：说明，" to false, "重点二" to true, "：说明" to false),
            "**A**（补充）**B**" to listOf("A" to true, "（补充）" to false, "B" to true),
        )
        for ((source, expected) in fixtures) {
            assertEquals(source, normalizeHomeMarkdown(source))
            assertRendered(source, *expected.toTypedArray())
        }
    }

    @Test
    fun keepsCodeMathAndOrdinaryStarsByteForByte() {
        val protectedText = listOf(
            "```text\n**——（（测试））**🌕\n```",
            "~~~~text\n~~~\n**——（（测试））**🌕\n~~~~",
            "> ```text\n> **——（（测试））**🌕\n> ```",
            "~~~text\n> ~~~\n**——（（测试））**🌕\n~~~",
            "`**测试））**🌕`",
            "`` ` **测试））**🌕 ``",
            "\$a ** b\$",
            "\$\$x ** y\$\$",
            "\$\$**测试））**🌕\$\$",
            "\$**测试））**🌕\$",
            "\\[**测试））**🌕\\]",
            "\\(**测试））**🌕\\)",
            "*斜体*、2 * 3 * 4、\\**测试））**🌕",
        )
        for (source in protectedText) {
            assertEquals(source, normalizeHomeMarkdown(source))
        }

        val code = parseMarkdown(normalizeHomeMarkdown(protectedText.first())).blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("**——（（测试））**🌕", code.code.trimEnd('\n'))
        val inline = (parseMarkdown(normalizeHomeMarkdown(protectedText[4])).blocks.single() as MarkdownBlock.Paragraph)
            .content.single() as MarkdownInline.Code
        assertEquals("**测试））**🌕", inline.code)

        val mixed = "`**测试））**🌕`\n\n**（测试）**🌕"
        assertEquals("`**测试））**🌕`\n\n**（测试）**\u200A🌕", normalizeHomeMarkdown(mixed))
    }

    @Test
    fun streamingUsesRealIncrementalParserAndFinalOutputIsClean() {
        for (source in listOf("**——（（**关键词", "**测试内容））*", "```\n**测试））**🌕", "\\[**测试））**🌕")) {
            assertEquals(source, normalizeHomeMarkdown(source))
        }

        val fixtures = listOf(
            "**——（（……测试甲……测试乙……））**🌕" to listOf("——（（……测试甲……测试乙……））" to true, "🌕" to false),
            "🌕**（（测试内容））**" to listOf("🌕" to false, "（（测试内容））" to true),
            "**测试内容：**紧接文字" to listOf("测试内容：" to true, "紧接文字" to false),
            "**——（（**关键词**……测试内容……））**🌕" to listOf("——（（关键词……测试内容……））" to true, "🌕" to false),
            "**前文（**关键词**）后文**" to listOf("前文（关键词）后文" to true),
        )
        for ((source, expected) in fixtures) {
            val parser = MarkdownStreamParser()
            for (end in 0..source.length) {
                val normalized = normalizeHomeMarkdown(source.substring(0, end))
                assertEquals(normalized, normalizeHomeMarkdown(normalized))
                val document = parser.parse(normalized, streaming = true)
                if (end == source.length) assertDocument(document, expected)
            }
            assertDocument(parser.parse(normalizeHomeMarkdown(source), streaming = false), expected)
        }
    }

    private fun assertRendered(source: String, vararg expected: Pair<String, Boolean>) {
        val normalized = normalizeHomeMarkdown(source)
        assertEquals(normalized, normalizeHomeMarkdown(normalized))
        assertDocument(parseMarkdown(normalized), expected.toList())
    }

    private fun assertDocument(document: MarkdownDocument, expected: List<Pair<String, Boolean>>) {
        val paragraph = document.blocks.single() as MarkdownBlock.Paragraph
        val runs = textRuns(paragraph.content).filter { it.first.isNotEmpty() }
        assertTrue(runs.any { it.second })
        assertFalse(runs.any { "**" in it.first })
        assertEquals(
            expected.flatMap { (text, bold) -> text.map { it to bold } },
            runs.flatMap { (text, bold) -> text.map { it to bold } },
        )
    }

    private fun textRuns(nodes: List<MarkdownInline>, bold: Boolean = false): List<Pair<String, Boolean>> =
        nodes.flatMap { node ->
            when (node) {
                is MarkdownInline.Text -> listOf(node.text.replace("\u200A", "") to bold)
                is MarkdownInline.Styled -> textRuns(node.children, bold || node.style.fontWeight == FontWeight.Bold)
                is MarkdownInline.Link -> textRuns(node.children, bold)
                else -> error("Unexpected inline node in emphasis fixture")
            }
        }
}
