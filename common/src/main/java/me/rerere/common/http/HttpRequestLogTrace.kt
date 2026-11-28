package me.rerere.common.http

import kotlinx.serialization.Serializable

@Serializable
enum class HttpRequestLogCategory {
    CHAT,
    TOOL_CONTINUATION,
    MEMORY,
    COMPRESS,
    TITLE,
    SUGGESTION,
    SYSTEM,
}

/** Client-side request metadata carried by an OkHttp tag. */
data class HttpRequestLogTrace(
    val category: HttpRequestLogCategory = HttpRequestLogCategory.SYSTEM,
    val groupId: String? = null,
    val stepIndex: Int? = null,
    val modelName: String? = null,
    val toolNames: List<String> = emptyList(),
    val skillNames: List<String> = emptyList(),
)
