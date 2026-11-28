package me.rerere.ai.agent

import kotlinx.serialization.Serializable
import me.rerere.common.http.HttpRequestLogCategory
import me.rerere.common.http.HttpRequestLogTrace
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.Uuid

/** Saved on a reply so an approval can resume the same request sequence after reloading it. */
@Serializable
data class RequestLogPosition(val groupId: String, val stepIndex: Int)

class RequestLogGroup(
    position: RequestLogPosition = RequestLogPosition(Uuid.random().toString(), 0),
) {
    private val groupId = position.groupId
    private val step = AtomicInteger(position.stepIndex)
    private val pausedTools = AtomicReference<Pair<Uuid, Set<String>>?>()

    internal fun rememberPausedTools(messageId: Uuid, toolIds: Set<String>) {
        pausedTools.set(messageId to toolIds)
    }

    internal fun takePausedTools(messageId: Uuid?): Set<String>? =
        pausedTools.getAndSet(null)?.takeIf { it.first == messageId }?.second

    fun position(): RequestLogPosition = RequestLogPosition(groupId, step.get())

    fun next(
        category: HttpRequestLogCategory,
        modelName: String,
        toolNames: List<String> = emptyList(),
        skillNames: List<String> = emptyList(),
        followsToolResult: Boolean = toolNames.isNotEmpty(),
    ): HttpRequestLogTrace {
        val stepIndex = step.incrementAndGet()
        val source = if (category == HttpRequestLogCategory.CHAT && (stepIndex > 1 || followsToolResult)) {
            HttpRequestLogCategory.TOOL_CONTINUATION
        } else {
            category
        }
        return HttpRequestLogTrace(
            category = source,
            groupId = groupId,
            stepIndex = stepIndex,
            modelName = modelName,
            toolNames = toolNames.distinct(),
            skillNames = skillNames.distinct(),
        )
    }
}
