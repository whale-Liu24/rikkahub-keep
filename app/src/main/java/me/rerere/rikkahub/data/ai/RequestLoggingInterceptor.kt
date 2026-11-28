package me.rerere.rikkahub.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.common.http.HttpRequestLogCategory
import me.rerere.common.http.HttpRequestLogTrace
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal class RequestLoggingInterceptor(private val store: HomeHttpLogStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!Logging.isRequestLoggingEnabled()) return chain.proceed(request)

        val epoch = store.currentEpoch()
        val started = System.nanoTime()
        val sanitizer = HomeHttpLogSanitizer
        val trace = request.tag(HttpRequestLogTrace::class.java)
        val entry = LogEntry.RequestLog(
            tag = "HTTP",
            url = sanitizer.url(request.url),
            method = request.method,
            requestHeaders = sanitizer.headers(request.headers),
            requestBody = sanitizer.captureBody(request.body),
            category = trace?.category ?: HttpRequestLogCategory.SYSTEM,
            groupId = trace?.groupId,
            stepIndex = trace?.stepIndex,
            modelName = trace?.modelName?.let(sanitizer::traceName),
            toolNames = trace?.toolNames.orEmpty().mapNotNull(sanitizer::traceName),
            skillNames = trace?.skillNames.orEmpty().mapNotNull(sanitizer::traceName),
            requestBodyBytes = request.header("Content-Length")?.toLongOrNull()?.takeIf { request.body != null && it >= 0 },
        )

        val response = try {
            chain.proceed(request)
        } catch (error: IOException) {
            if (Logging.isRequestLoggingEnabled()) {
                store.record(
                    entry.copy(
                        timestamp = System.currentTimeMillis(),
                        durationMs = elapsedMillis(started),
                        error = "${error.javaClass.simpleName}: ${sanitizer.error(error.message)}",
                    ),
                    epoch,
                )
            }
            throw error
        }

        if (Logging.isRequestLoggingEnabled()) {
            store.record(
                entry.copy(
                    timestamp = System.currentTimeMillis(),
                    responseCode = response.code,
                    responseHeaders = sanitizer.headers(response.headers),
                    durationMs = elapsedMillis(started),
                ),
                epoch,
            )
        }
        return response
    }

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000
}
