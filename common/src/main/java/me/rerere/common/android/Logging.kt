package me.rerere.common.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import me.rerere.common.http.HttpRequestLogCategory
import kotlin.uuid.Uuid

private const val MAX_RECENT_LOGS = 100

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val message: String
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val durationMs: Long? = null,
        val error: String? = null,
        val category: HttpRequestLogCategory? = null,
        val groupId: String? = null,
        val stepIndex: Int? = null,
        val modelName: String? = null,
        val toolNames: List<String> = emptyList(),
        val skillNames: List<String> = emptyList(),
        val requestBodyBytes: Long? = null,
    ) : LogEntry()
}

object Logging {
    private val mutableLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs = mutableLogs.asStateFlow()

    @Volatile
    private var requestLoggingEnabled = false

    fun log(tag: String, message: String) {
        val entry = LogEntry.TextLog(tag = tag, message = message)
        mutableLogs.update { current ->
            val recentText = (listOf(entry) + current.filterIsInstance<LogEntry.TextLog>()).take(MAX_RECENT_LOGS)
            recentText + current.filterIsInstance<LogEntry.RequestLog>()
        }
    }

    fun replaceRequestLogs(requests: List<LogEntry.RequestLog>) {
        mutableLogs.update { current -> current.filterIsInstance<LogEntry.TextLog>() + requests }
    }

    fun isRequestLoggingEnabled(): Boolean = requestLoggingEnabled

    fun setRequestLoggingEnabled(enabled: Boolean) {
        requestLoggingEnabled = enabled
    }

    fun getRecentLogs(): List<LogEntry> = recentLogs.value.sortedByDescending { it.timestamp }

    fun getTextLogs(): List<LogEntry.TextLog> = recentLogs.value.filterIsInstance<LogEntry.TextLog>()

    fun getRequestLogs(): List<LogEntry.RequestLog> = recentLogs.value.filterIsInstance<LogEntry.RequestLog>()

    fun clear() {
        mutableLogs.value = emptyList()
    }
}
