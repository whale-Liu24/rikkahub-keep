package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.RequestLogGroup
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.applyLogTrace
import me.rerere.common.android.LogEntry
import me.rerere.common.http.HttpRequestLogCategory
import me.rerere.common.http.HttpRequestLogTrace
import me.rerere.rikkahub.data.datastore.DisplaySetting
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    @Test
    fun persistsTraceFieldsAndRestoresStage19Entries() = runTest {
        val path = temporary.root.resolve("history.jsonl")
        val oldLine = """{"id":"12345678-1234-1234-1234-123456789abc","timestamp":1,"tag":"HTTP","url":"https://example.invalid/v1","method":"POST","responseCode":200}"""
        path.writeText(oldLine + "\n")
        val dispatcher = StandardTestDispatcher(testScheduler)
        var visible = emptyList<LogEntry.RequestLog>()
        val store = HomeHttpLogStore(path, backgroundScope, { visible = it }, {}, dispatcher = dispatcher)
        testScheduler.runCurrent()
        val old = visible.single()
        assertEquals(200, old.responseCode)
        assertEquals(null, old.category)
        assertEquals(null, old.groupId)
        assertEquals(null, old.stepIndex)
        assertEquals(null, old.modelName)
        assertEquals(emptyList<String>(), old.toolNames)
        assertEquals(emptyList<String>(), old.skillNames)
        assertEquals(null, old.requestBodyBytes)

        val new = entry(2).copy(
            category = HttpRequestLogCategory.TOOL_CONTINUATION,
            groupId = "fixture",
            stepIndex = 2,
            modelName = "fixture-model",
            toolNames = listOf("use_skill", "fixture_tool"),
            skillNames = listOf("fixture-skill"),
            requestBodyBytes = 1024,
        )
        store.record(new, store.currentEpoch())
        testScheduler.runCurrent()
        var restored = emptyList<LogEntry.RequestLog>()
        HomeHttpLogStore(path, backgroundScope, { restored = it }, {}, dispatcher = dispatcher)
        testScheduler.runCurrent()
        assertEquals(listOf(new, old), restored)
    }

    @Test
    fun appliesTraceOnlyToOkHttpTag() {
        val trace = HttpRequestLogTrace(
            category = HttpRequestLogCategory.TOOL_CONTINUATION,
            groupId = "fixture",
            stepIndex = 2,
            modelName = "fixture-model",
            toolNames = listOf("use_skill"),
            skillNames = listOf("fixture-skill"),
        )
        val plain = Request.Builder()
            .url("https://example.invalid/v1")
            .header("X-Session-ID", "original-session")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val params = TextGenerationParams(model = Model(modelId = "fixture-model"), logTrace = trace)
        val tagged = plain.newBuilder().applyLogTrace(params).build()

        assertEquals(trace, tagged.tag(HttpRequestLogTrace::class.java))
        assertEquals(trace, tagged.newBuilder().build().tag(HttpRequestLogTrace::class.java))
        assertEquals(plain.headers, tagged.headers)
        assertEquals(plain.url, tagged.url)
        assertTrue(plain.body === tagged.body)
        assertEquals(Json.encodeToString(params.copy(logTrace = null)), Json.encodeToString(params))
        assertEquals(null, sanitizer.traceName("not a fixture identifier"))
        assertEquals("fixture/model-1", sanitizer.traceName("fixture/model-1"))
    }

    @Test
    fun chatTraceSurvivesApprovalAndInTurnCompression() = runTest {
        val group = RequestLogGroup()
        val traces = mutableListOf<HttpRequestLogTrace>()
        val call = UIMessagePart.Tool(toolCallId = "fixture-call", toolName = "use_skill", input = "{}")
        val model = Model(modelId = "fixture-model")
        val adapter = recordingProvider(traces) { step ->
            if (step == 1) listOf(call) else listOf(UIMessagePart.Text("fixture"))
        }
        val tool = Tool(
            name = "use_skill",
            description = "Fixture",
            needsApproval = { true },
            loadedSkillName = { "fixture-skill" },
            execute = { listOf(UIMessagePart.Text("synthetic output")) },
        )
        val request = AgentRequest(
            provider = adapter,
            providerSetting = ProviderSetting.OpenAI(),
            messages = listOf(UIMessage.user("synthetic request")),
            tools = listOf(tool),
            stream = false,
            logGroup = group,
            logCategory = HttpRequestLogCategory.CHAT,
        )
        val hooks = fixtureHooks(model)
        val waiting = runAgent(request, hooks).toList().last() as AgentEvent.Finished
        assertTrue(waiting.reason is AgentFinishReason.AwaitingApproval)
        assertEquals(HttpRequestLogCategory.CHAT, traces.single().category)
        assertEquals(1, traces.single().stepIndex)
        assertEquals(emptyList<String>(), traces.single().toolNames)

        val saved = waiting.message.copy(requestLogPosition = group.position())
        val reloaded = Json.decodeFromString<UIMessage>(Json.encodeToString(saved))
        val resumed = RequestLogGroup(checkNotNull(reloaded.requestLogPosition))
        val approved = reloaded.copy(parts = listOf(call.copy(approvalState = ToolApprovalState.Approved)))
        runAgent(request.copy(messages = request.messages + approved, logGroup = resumed), hooks).toList()
        val followUp = traces[1]
        assertEquals(traces[0].groupId, followUp.groupId)
        assertEquals(2, followUp.stepIndex)
        assertEquals(HttpRequestLogCategory.TOOL_CONTINUATION, followUp.category)
        assertEquals(listOf("use_skill"), followUp.toolNames)
        assertEquals(listOf("fixture-skill"), followUp.skillNames)

        val compression = resumed.next(HttpRequestLogCategory.COMPRESS, model.modelId)
        assertEquals(3, compression.stepIndex)
        assertEquals(followUp.groupId, compression.groupId)
        runAgent(request.copy(messages = request.messages, logGroup = resumed), hooks).toList()
        assertEquals(HttpRequestLogCategory.TOOL_CONTINUATION, traces.last().category)
        assertEquals(compression.groupId, traces.last().groupId)
        assertEquals(4, traces.last().stepIndex)
        assertEquals(emptyList<String>(), traces.last().toolNames)
    }

    @Test
    fun memoryToolStepsShareOneGroupAndKeepTheMemoryCategory() = runTest {
        val traces = mutableListOf<HttpRequestLogTrace>()
        val provider = recordingProvider(traces) { step ->
            if (step == 1) {
                listOf(
                    UIMessagePart.Tool("fixture-1", "memory_list", "{}"),
                    UIMessagePart.Tool("fixture-2", "memory_list", "{}"),
                )
            } else {
                listOf(UIMessagePart.Text("fixture"))
            }
        }
        val request = AgentRequest(
            provider = provider,
            providerSetting = ProviderSetting.OpenAI(),
            messages = listOf(UIMessage.user("synthetic task")),
            tools = listOf(Tool(name = "memory_list", description = "Fixture", execute = {
                listOf(UIMessagePart.Text("synthetic output"))
            })),
            stream = false,
            logGroup = RequestLogGroup(),
            logCategory = HttpRequestLogCategory.MEMORY,
        )
        runAgent(request, fixtureHooks(Model(modelId = "fixture-model"))).toList()

        assertEquals(listOf(1, 2), traces.map { it.stepIndex })
        assertEquals(1, traces.map { it.groupId }.distinct().size)
        assertTrue(traces.all { it.category == HttpRequestLogCategory.MEMORY })
        assertEquals(listOf("memory_list"), traces.last().toolNames)
        assertEquals(emptyList<String>(), traces.last().skillNames)
    }

    @Test
    fun pausedToolBatchIsRetainedWhenCompressionCannotReplaceTheReply() = runTest {
        val traces = mutableListOf<HttpRequestLogTrace>()
        val model = Model(modelId = "fixture-model")
        val group = RequestLogGroup()
        val provider = recordingProvider(traces) { step ->
            if (step == 1) listOf(
                UIMessagePart.Tool("fixture-call", "fixture_tool", "{}"),
                UIMessagePart.Text("synthetic trailing text"),
            ) else listOf(UIMessagePart.Text("fixture"))
        }
        val request = AgentRequest(
            provider = provider,
            providerSetting = ProviderSetting.OpenAI(),
            messages = listOf(UIMessage.user("synthetic request")),
            tools = listOf(Tool(name = "fixture_tool", description = "Fixture", execute = {
                listOf(UIMessagePart.Text("synthetic output"))
            })),
            stream = false,
            logGroup = group,
            logCategory = HttpRequestLogCategory.CHAT,
        )
        val pausingHooks = object : AgentHooks by fixtureHooks(model) {
            override suspend fun shouldPause(message: UIMessage) = true
        }
        val paused = runAgent(request, pausingHooks).toList().last() as AgentEvent.Finished
        assertEquals(AgentFinishReason.Paused, paused.reason)
        group.next(HttpRequestLogCategory.COMPRESS, model.modelId)

        runAgent(request.copy(messages = request.messages + paused.message), fixtureHooks(model)).toList()
        assertEquals(traces.first().groupId, traces.last().groupId)
        assertEquals(3, traces.last().stepIndex)
        assertEquals(listOf("fixture_tool"), traces.last().toolNames)
    }

    private fun fixtureHooks(model: Model) = object : AgentHooks {
        override suspend fun prepareStep(messages: List<UIMessage>) =
            StepRequest(messages, TextGenerationParams(model = model))
    }

    private fun recordingProvider(
        traces: MutableList<HttpRequestLogTrace>,
        reply: (Int) -> List<UIMessagePart>,
    ): Provider<ProviderSetting> = object : Provider<ProviderSetting> {
        override suspend fun listModels(providerSetting: ProviderSetting): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): TextGenerationResult {
            val trace = checkNotNull(params.logTrace)
            traces.add(trace)
            return TextGenerationResult(
                id = "fixture",
                model = params.model.modelId,
                message = UIMessage(role = MessageRole.ASSISTANT, parts = reply(checkNotNull(trace.stepIndex))),
            )
        }

        override suspend fun streamText(
            providerSetting: ProviderSetting,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ) = error("Fixture uses non-streaming generation")
    }

    private fun entry(index: Int) = LogEntry.RequestLog(
        timestamp = index.toLong(),
        tag = "HTTP",
        url = "https://example.invalid/v1",
        method = "POST",
    )
}
