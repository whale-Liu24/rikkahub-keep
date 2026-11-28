package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.expandNotices
import me.rerere.ai.agent.runAgent
import me.rerere.ai.provider.HistoryReasoningMode
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.ui.OpenRouterReasoningMetadata
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHistoryReasoningTest {
    private val model = Model(modelId = "fixture-model")
    private val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())

    @Test
    fun legacyProviderJsonRetainsItsBehaviorAndExplicitModeWins() {
        fun decode(fields: String) =
            Json.decodeFromString<ProviderSetting>("""{"type":"openai"$fields}""") as ProviderSetting.OpenAI

        val legacyOn = decode(""","includeHistoryReasoning":true""")
        val legacyOff = decode(""","includeHistoryReasoning":false""")
        assertNull(legacyOn.historyReasoningMode)
        assertNull(legacyOff.historyReasoningMode)
        assertEquals(HistoryReasoningMode.ALL, legacyOn.resolvedHistoryReasoningMode())
        assertEquals(HistoryReasoningMode.NONE, legacyOff.resolvedHistoryReasoningMode())
        assertEquals(HistoryReasoningMode.ALL, decode("").resolvedHistoryReasoningMode())
        assertEquals(
            HistoryReasoningMode.CURRENT_TURN_ONLY,
            decode(""","includeHistoryReasoning":false,"historyReasoningMode":"CURRENT_TURN_ONLY"""")
                .resolvedHistoryReasoningMode(),
        )
    }

    @Test
    fun ordinaryChatReplaysOldReasoningOnlyInAllMode() {
        val messages = listOf(
            UIMessage.user("Prompt A"),
            assistant("Reply A", "reason-a"),
            UIMessage.user("Prompt B"),
            assistant("Reply B", "reason-b"),
            UIMessage.user("Prompt C"),
        )
        val all = requestMessages(messages, HistoryReasoningMode.ALL)
        val current = requestMessages(messages, HistoryReasoningMode.CURRENT_TURN_ONLY)
        val none = requestMessages(messages, HistoryReasoningMode.NONE)

        assertEquals(
            listOf(JsonPrimitive("reason-a"), JsonPrimitive("reason-b")),
            all.mapNotNull { it["reasoning_content"] },
        )
        assertEquals(all.map { JsonObject(it - "reasoning_content") }, current)
        assertEquals(current, none)
    }

    @Test
    fun resumedToolLoopRetainsEveryActiveSegmentWithSyntheticNotices() = runBlocking {
        val earlier = assistant("Earlier reply", "earlier-reason", tool("old-call"))
        val active = assistant("Before notice", "first-reason", tool("first-call"))
        val resumed = active.copy(
            parts = active.parts + UIMessagePart.Notice(title = "Fixture", text = "Fixture notice") +
                listOf(
                    UIMessagePart.Reasoning(reasoning = "second-reason"),
                    UIMessagePart.Text("After notice"),
                    tool("second-call"),
                ),
        )
        val history = listOf(UIMessage.user("Prompt A"), earlier, UIMessage.user("Prompt B"))
        val provider = object : Provider<ProviderSetting> {
            override suspend fun listModels(providerSetting: ProviderSetting) = emptyList<Model>()

            override suspend fun streamText(
                providerSetting: ProviderSetting,
                messages: List<UIMessage>,
                params: TextGenerationParams,
            ) = error("Fixture uses non-streaming generation")

            override suspend fun generateText(
                providerSetting: ProviderSetting,
                messages: List<UIMessage>,
                params: TextGenerationParams,
            ): TextGenerationResult {
                assertEquals(resumed.id, params.activeAssistantMessageId)
                assertEquals(2, messages.count { it.id == resumed.id })
                assertTrue(messages.filter { it.isSynthetic }.all { it.id != resumed.id })
                assertFalse("activeAssistantMessageId" in Json.encodeToString(params))
                val body = requestBody(messages, providerSetting as ProviderSetting.OpenAI, params)
                assertFalse(body.toString().contains(resumed.id.toString()))
                val wire = body.getValue("messages").jsonArray.map { it.jsonObject }
                val roles = listOf("user", "assistant", "tool", "user", "assistant", "tool", "user", "assistant", "tool")
                val partialReply = messages[messages.lastIndex - 1].parts.lastOrNull() as? UIMessagePart.Text
                val tailRoles = if (partialReply == null) listOf("user") else listOf("assistant", "user")
                assertEquals((roles + tailRoles).map(::JsonPrimitive), wire.map { it.getValue("role") })
                if (partialReply != null) {
                    assertEquals(JsonPrimitive(partialReply.text), wire[9]["content"])
                }
                assertFalse("reasoning_content" in wire[1])
                assertEquals(JsonPrimitive("first-reason"), wire[4]["reasoning_content"])
                assertEquals(JsonPrimitive("second-reason"), wire[7]["reasoning_content"])
                assertEquals(JsonPrimitive("Fixture notice"), wire[6]["content"])
                assertEquals(JsonPrimitive("<time_reminder>fixture</time_reminder>"), wire.last()["content"])
                assertEquals(
                    requestMessages(messages, HistoryReasoningMode.ALL).map { JsonObject(it - "reasoning_content") },
                    wire.map { JsonObject(it - "reasoning_content") },
                )
                for ((index, id) in listOf(1 to "old-call", 4 to "first-call", 7 to "second-call")) {
                    assertEquals(JsonPrimitive(id), wire[index].getValue("tool_calls").jsonArray.single().jsonObject["id"])
                    assertEquals(JsonPrimitive(id), wire[index + 1]["tool_call_id"])
                    assertEquals(JsonPrimitive("Fixture result"), wire[index + 1]["content"])
                }
                return TextGenerationResult("fixture", model.modelId, UIMessage.assistant("Final reply"))
            }
        }
        val settings = ProviderSetting.OpenAI(historyReasoningMode = HistoryReasoningMode.CURRENT_TURN_ONLY)
        val hooks = object : AgentHooks {
            override suspend fun prepareStep(messages: List<UIMessage>) = StepRequest(
                messages.expandNotices() +
                    UIMessage.user("<time_reminder>fixture</time_reminder>").copy(isSynthetic = true),
                TextGenerationParams(model),
            )
        }
        val partial = resumed.copy(parts = resumed.parts + UIMessagePart.Text("Partial reply"))
        for (reply in listOf(resumed, partial)) {
            runAgent(AgentRequest(provider, settings, history + reply, stream = false), hooks).collect()
        }
    }

    @Test
    fun replayFilterAlsoAppliesToOpenRouterDetailsAndMistralThinking() {
        val details = buildJsonArray {
            add(buildJsonObject {
                put("type", "reasoning.encrypted")
                put("data", "fixture")
            })
        }
        val previous = assistant("Earlier reply", "earlier-reason")
        val active = UIMessage.assistant("Current reply").let { message ->
            val reasoning = UIMessagePart.Reasoning(
                reasoning = "current-reason",
                metadata = OpenRouterReasoningMetadata(details).toMetadata(),
            )
            message.copy(parts = listOf(reasoning) + message.parts)
        }
        val messages = listOf(previous, active)
        val params = TextGenerationParams(model, activeAssistantMessageId = active.id)
        for (baseUrl in listOf("https://openrouter.ai/api/v1", "https://api.mistral.ai/v1")) {
            fun serialize(mode: HistoryReasoningMode) = requestBody(
                messages,
                ProviderSetting.OpenAI(baseUrl = baseUrl, historyReasoningMode = mode),
                params,
            ).getValue("messages").jsonArray.map { it.jsonObject }

            val all = serialize(HistoryReasoningMode.ALL)
            val current = serialize(HistoryReasoningMode.CURRENT_TURN_ONLY)
            val none = serialize(HistoryReasoningMode.NONE)
            assertEquals(JsonPrimitive("Earlier reply"), current.first()["content"])
            assertEquals(none.first(), current.first())
            assertNotEquals(all.first(), current.first())
            assertEquals(all.last(), current.last())
            assertNotEquals(all.last(), none.last())
            assertEquals(JsonPrimitive("Current reply"), none.last()["content"])
            if ("openrouter" in baseUrl) {
                assertEquals(details, current.last()["reasoning_details"])
                assertFalse("reasoning_details" in none.last())
            } else {
                val chunks = current.last().getValue("content").jsonArray.map { it.jsonObject }
                assertEquals(listOf(JsonPrimitive("thinking"), JsonPrimitive("text")), chunks.map { it["type"] })
                assertEquals(JsonPrimitive("Current reply"), chunks.last()["text"])
            }
        }
    }

    private fun assistant(text: String, reasoning: String, vararg tools: UIMessagePart.Tool) =
        UIMessage.assistant(text).copy(
            parts = listOf(UIMessagePart.Reasoning(reasoning = reasoning), UIMessagePart.Text(text)) + tools,
        )

    private fun tool(id: String) = UIMessagePart.Tool(
        toolCallId = id,
        toolName = "fixture_tool",
        input = "{}",
        output = listOf(UIMessagePart.Text("Fixture result")),
    )

    private fun requestMessages(messages: List<UIMessage>, mode: HistoryReasoningMode) = requestBody(
        messages,
        ProviderSetting.OpenAI(historyReasoningMode = mode),
        TextGenerationParams(model),
    ).getValue("messages").jsonArray.map { it.jsonObject }

    // Exercise the same request body builder used by both HTTP generation paths.
    private fun requestBody(
        messages: List<UIMessage>,
        settings: ProviderSetting.OpenAI,
        params: TextGenerationParams,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(api, messages, params, settings, false) as JsonObject
    }
}
