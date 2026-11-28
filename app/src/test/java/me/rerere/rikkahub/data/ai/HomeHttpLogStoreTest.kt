package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.android.LogEntry
import me.rerere.rikkahub.data.datastore.DisplaySetting
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HomeHttpLogStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private val sanitizer = HomeHttpLogSanitizer

    @Test
    fun settingsDefaultToOnAndRememberExplicitOff() {
        assertTrue(Json.decodeFromString<DisplaySetting>("{}").httpRequestLoggingEnabled)
        val off = DisplaySetting(httpRequestLoggingEnabled = false)
        assertFalse(Json.decodeFromString<DisplaySetting>(Json.encodeToString(off)).httpRequestLoggingEnabled)
    }

    @Test
    fun redactsHeadersQueriesExceptionUrlsAndLogcat() {
        val fixture = "synthetic-value-for-test"
        val sensitive = listOf("aUtHoRiZaTiOn", "Proxy-Authorization", "x-api-key", "api-key",
            "x-goog-api-key", "Cookie", "Set-Cookie", "X-Client-Secret", "X-Auth-Token")
        val headerPairs = sensitive.flatMap { listOf(it, fixture) } +
            listOf("Host", "example.invalid", "User-Agent", "test-agent", "X-Session-ID", "session-fixture")
        val headers = sanitizer.headers(headersOf(*headerPairs.toTypedArray()))
        assertTrue(sensitive.all { key -> headers.entries.any { it.key.equals(key, true) && it.value == "[REDACTED]" } })
        assertEquals("session-fixture", headers["X-Session-ID"])
        assertEquals("example.invalid", headers["Host"])
        assertEquals("test-agent", headers["User-Agent"])
        val endpoint = "https://user:$fixture@example.invalid/v1?%6bey=$fixture&signature=$fixture&alt=sse"
        val url = sanitizer.url(endpoint.toHttpUrl()).toHttpUrl()
        assertEquals("", url.username)
        assertEquals("", url.password)
        assertEquals("[REDACTED]", url.queryParameter("key"))
        assertEquals("[REDACTED]", url.queryParameter("signature"))
        assertEquals("sse", url.queryParameter("alt"))
        val error = sanitizer.error("GET $endpoint Authorization: Bearer $fixture")
        assertFalse(fixture in error)
        assertTrue("example.invalid" in error)
        assertFalse(fixture in sanitizer.logcatLine("--> GET $endpoint").orEmpty())
        assertEquals(null, sanitizer.logcatLine("Authorization: $fixture"))
        assertEquals("<-- 401", sanitizer.logcatLine("<-- 401 $fixture $endpoint"))
    }

    @Test
    fun slimsBase64AndRedactsCredentialFieldsWithoutBreakingJson() {
        val base64 = "QUFB".repeat(4096)
        val input = """{"messages":[{"content":[{"image":"data:image/png;base64,$base64"}]}],"binary":"$base64","api_key":"fixture","max_tokens":42}"""
        val output = sanitizer.bodyText(input, isJson = true)
        val decoded = Json.parseToJsonElement(output).jsonObject
        assertTrue("messages" in decoded)
        assertEquals("42", decoded.getValue("max_tokens").jsonPrimitive.content)
        assertEquals("[REDACTED]", decoded.getValue("api_key").jsonPrimitive.content)
        assertTrue(decoded.getValue("binary").jsonPrimitive.content.startsWith("[omitted base64:"))
        assertTrue("omitted base64" in decoded.getValue("messages").toString())
        assertFalse(base64 in output)
    }

    @Test
    fun truncatesOnAUnicodeBoundaryWithinTheByteLimit() {
        val output = sanitizer.bodyText("🌕".repeat(180_000), isJson = false)
        assertTrue(output.encodeToByteArray().size <= HomeHttpLogSanitizer.MAX_BODY_BYTES)
        assertTrue(output.endsWith("[truncated: 512 KiB body limit]"))
        assertFalse('\uFFFD' in output)
    }

    @Test
    fun skipsUnsafeBodiesBeforeWritingAndBoundsInaccurateLength() {
        var attempts = 0
        fun body(type: String, length: Long, oneShot: Boolean = false, duplex: Boolean = false) =
            object : RequestBody() {
                override fun contentType(): MediaType = type.toMediaType()
                override fun contentLength() = length
                override fun isOneShot() = oneShot
                override fun isDuplex() = duplex
                override fun writeTo(sink: BufferedSink) {
                    attempts++
                    val chunk = ByteArray(8 * 1024)
                    repeat(600) { sink.write(chunk) }
                }
            }
        val unsafe = listOf(
            body("application/json", 10, oneShot = true),
            body("application/json", 10, duplex = true),
            body("multipart/form-data", 10),
            body("application/octet-stream", 10),
            body("text/plain", -1),
            body("application/json", HomeHttpLogSanitizer.MAX_CAPTURE_BYTES + 1),
        )
        unsafe.forEach { assertTrue(sanitizer.captureBody(it).orEmpty().startsWith("[request body omitted:")) }
        assertEquals(0, attempts)
        assertTrue("4 MiB" in sanitizer.captureBody(body("application/json", 1)).orEmpty())
        assertEquals(1, attempts)
    }

    @Test
    fun rollsTo50AndMergesStartupRecordsById() = runTest {
        val path = temporary.root.resolve("history.jsonl")
        val dispatcher = StandardTestDispatcher(testScheduler)
        var visible = emptyList<LogEntry.RequestLog>()
        val store = HomeHttpLogStore(path, backgroundScope, { visible = it }, {}, dispatcher = dispatcher)
        repeat(55) { store.record(entry(it), store.currentEpoch()) }
        advanceUntilIdle()
        testScheduler.runCurrent()
        assertEquals((54 downTo 5).map(Int::toLong), visible.map { it.timestamp })
        assertEquals(50, path.readLines().size)
        val duplicate = visible.first()
        var restored = emptyList<LogEntry.RequestLog>()
        val reopened = HomeHttpLogStore(path, backgroundScope, { restored = it }, {}, dispatcher = dispatcher)
        reopened.record(duplicate, reopened.currentEpoch())
        reopened.record(entry(100), reopened.currentEpoch())
        testScheduler.runCurrent()
        assertEquals(50, restored.size)
        assertEquals(100L, restored.first().timestamp)
        assertEquals(1, restored.count { it.id == duplicate.id })
        assertEquals(visible.take(49), restored.drop(1))
    }

    @Test
    fun byteLimitEvictsOldestFirst() = runTest {
        val path = temporary.root.resolve("history.jsonl")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = HomeHttpLogStore(path, backgroundScope, {}, {}, dispatcher = dispatcher, maxFileBytes = 600)
        repeat(6) {
            store.record(entry(it).copy(requestBody = "synthetic body ".repeat(16)), store.currentEpoch())
        }
        testScheduler.runCurrent()
        assertTrue(path.length() in 1..600)
        val kept = path.readLines().map { Json.decodeFromString<LogEntry.RequestLog>(it).timestamp }
        assertTrue(kept.size < 6)
        assertEquals((5 downTo 6 - kept.size).map(Int::toLong), kept)
    }

    @Test
    fun clearWinsAgainstPendingWritesLateRestoreAndOldRequests() = runTest {
        val path = temporary.root.resolve("history.jsonl")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = entry(1)
        path.writeText(Json.encodeToString(old) + "\n")
        var visible = listOf(old)
        val store = HomeHttpLogStore(path, backgroundScope, { visible = it }, { visible = emptyList() }, dispatcher = dispatcher)
        val beforeClear = store.currentEpoch()
        store.record(entry(2), beforeClear)
        store.clear()
        assertTrue(visible.isEmpty())
        store.record(entry(3), beforeClear)
        testScheduler.runCurrent()
        assertTrue(visible.isEmpty())
        assertFalse(path.exists())
        val fresh = entry(4)
        store.record(fresh, store.currentEpoch())
        testScheduler.runCurrent()
        assertEquals(listOf(fresh), visible)
        assertEquals(fresh, Json.decodeFromString<LogEntry.RequestLog>(path.readText()))
        store.clear()
        testScheduler.runCurrent()
        assertFalse(path.exists())
    }

    private fun entry(index: Int) = LogEntry.RequestLog(
        timestamp = index.toLong(),
        tag = "HTTP",
        url = "https://example.invalid/v1",
        method = "POST",
    )
}
