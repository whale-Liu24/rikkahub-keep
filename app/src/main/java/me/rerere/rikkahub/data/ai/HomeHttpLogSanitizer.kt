package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody
import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.buffer
import java.io.IOException

internal object HomeHttpLogSanitizer {
    const val MAX_CAPTURE_BYTES = 4 * 1024 * 1024L
    const val MAX_BODY_BYTES = 512 * 1024
    private const val MAX_METADATA_CHARS = 4096
    private const val MAX_JSON_DEPTH = 64
    private const val MAX_JSON_MARKS = 100_000
    private const val MIN_BASE64_CHARS = 4096
    private const val REDACTED = "[REDACTED]"
    private const val TRUNCATED = "\n[truncated: 512 KiB body limit]"

    val sensitiveHeaderNames = setOf(
        "Authorization", "Proxy-Authorization", "x-api-key", "api-key",
        "x-goog-api-key", "Cookie", "Set-Cookie",
    )
    private val safeHeaders = setOf(
        "host", "user-agent", "content-type", "content-length", "accept",
        "x-session-id", "accept-encoding", "content-encoding", "accept-language",
        "cache-control", "date", "retry-after", "transfer-encoding",
    )
    private val safeQueryNames = setOf("alt", "api-version", "stream", "prettyprint", "version")
    private val credentialFields = setOf(
        "key", "apikey", "xapikey", "xgoogapikey", "token", "accesstoken",
        "refreshtoken", "idtoken", "authtoken", "sessiontoken", "sessionid",
        "secret", "clientsecret", "auth", "authorization", "proxyauthorization", "authentication",
        "cookie", "setcookie", "password", "passwd", "credential", "credentials",
        "sig", "signature", "privatekey", "accesskey", "accesskeyid",
    )
    private val urlPattern = Regex("""(?i)https?://[^\s<>"'\\]+""")
    private val inlineCredentials = Regex(
        """(?i)\b(authorization|proxy[-_ ]authorization|(?:x[-_])?(?:goog[-_])?api[-_ ]?key|access[-_]?token|refresh[-_]?token|client[-_]?secret|key|token|secret|auth|password|cookie|signature|sig)(\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|(?:(?:bearer|basic)\s+)?[^\s&,;"']+)"""
    )
    private val tokenPattern = Regex(
        """(?i)(\b(?:bearer|basic)\s+)[a-z0-9+/_.=-]+|\b(?:sk-[\w-]{12,}|eyJ[\w-]+\.[\w-]+\.[\w-]+)"""
    )
    private val dataUri = Regex("""(?i)data:[^\s,;]{0,128}(?:;[^\s,;]{1,64}){0,4};base64,[a-z0-9+/_=\r\n-]+""")
    private val base64Run = Regex("""(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{$MIN_BASE64_CHARS,}={0,2}""")
    private val methodLine = Regex("""^--> ([A-Z]{1,20}) (https?://\S+)""")
    private val statusLine = Regex("""^<-- (\d{3})(?:\s|$)""")

    private fun isCredential(name: String): Boolean {
        val normalized = name.lowercase().filter(Char::isLetterOrDigit)
        return normalized in credentialFields ||
            normalized.endsWith("apikey") || normalized.endsWith("credential") ||
            normalized.endsWith("signature") || normalized.endsWith("token") ||
            normalized.endsWith("secret") || normalized.endsWith("password")
    }

    fun headers(headers: Headers): Map<String, String> =
        headers.names().take(64).associateWith { name -> headerValue(name, headers[name].orEmpty()) }

    private fun headerValue(name: String, value: String): String =
        if (name.lowercase() !in safeHeaders) REDACTED
        else if (value.length > MAX_METADATA_CHARS) "[header omitted: size limit]"
        else sanitizeText(value)

    fun url(url: HttpUrl): String {
        val builder = url.newBuilder().username("").password("").fragment(null).query(null)
        for (index in 0 until url.querySize) {
            val name = url.queryParameterName(index)
            val value = url.queryParameterValue(index)
            val safeValue = if (value == null || name.lowercase() in safeQueryNames) {
                value?.let(::sanitizeInline)
            } else {
                REDACTED
            }
            builder.addQueryParameter(name, safeValue)
        }
        val sanitized = builder.build().toString()
        return if (sanitized.length <= MAX_METADATA_CHARS) sanitized else "[URL omitted: size limit]"
    }

    fun error(message: String?): String {
        val urls = urlPattern.findAll(message.orEmpty().take(MAX_METADATA_CHARS))
            .mapNotNull { it.value.toHttpUrlOrNull() }
            .take(2)
            .map(::url)
            .toList()
        return (listOf("[exception details omitted]") + urls).joinToString(" ")
    }

    // Do not forward the library's raw status text, URL suffixes or exception messages.
    fun logcatLine(line: String): String? {
        methodLine.find(line)?.let { match ->
            val target = match.groupValues[2].toHttpUrlOrNull() ?: return null
            return "--> ${match.groupValues[1]} ${url(target)}"
        }
        statusLine.find(line)?.let { return "<-- ${it.groupValues[1]}" }
        val name = line.substringBefore(':')
        if (name == line || name.lowercase() !in safeHeaders) return null
        return "$name: ${headerValue(name, line.substringAfter(':').trimStart())}"
    }

    fun captureBody(body: RequestBody?): String? {
        body ?: return null
        return try {
            captureRepeatableText(body)
        } catch (_: IOException) {
            "[request body omitted: unreadable or exceeds 4 MiB]"
        } catch (_: RuntimeException) {
            "[request body omitted: capture failed]"
        }
    }

    private fun captureRepeatableText(body: RequestBody): String {
        if (body.isOneShot() || body.isDuplex()) return "[request body omitted: streaming or one-shot]"
        val type = body.contentType()
        val subtype = type?.subtype.orEmpty()
        val isJson = subtype == "json" || subtype.endsWith("+json")
        if (type == null || type.type == "multipart" || (!isJson && type.type != "text")) {
            return "[request body omitted: non-text content]"
        }
        val length = body.contentLength()
        if (length < 0) return "[request body omitted: unknown content length]"
        if (length > MAX_CAPTURE_BYTES) return "[request body omitted: exceeds 4 MiB ($length bytes)]"

        val captured = Buffer()
        val limited = object : Sink {
            override fun write(source: Buffer, byteCount: Long) {
                if (byteCount > MAX_CAPTURE_BYTES - captured.size) throw IOException()
                captured.write(source, byteCount)
            }

            override fun flush() = Unit
            override fun close() = Unit
            override fun timeout() = Timeout.NONE
        }
        limited.buffer().use(body::writeTo)
        return bodyText(captured.readString(type.charset(Charsets.UTF_8) ?: Charsets.UTF_8), isJson)
    }

    fun bodyText(input: String, isJson: Boolean): String {
        val trimmed = input.trimStart()
        val cleaned = if (isJson || trimmed.startsWith('{') || trimmed.startsWith('[')) {
            if (!isBoundedJson(trimmed)) return "[request body omitted: malformed or complex JSON]"
            try {
                sanitizeJson(Json.parseToJsonElement(trimmed)).toString()
            } catch (_: IllegalArgumentException) {
                return "[request body omitted: invalid JSON]"
            }
        } else {
            sanitizeText(input)
        }
        return truncateUtf8(cleaned)
    }

    private fun sanitizeJson(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (name, child) ->
            if (isCredential(name)) JsonPrimitive(REDACTED) else sanitizeJson(child)
        })
        is JsonArray -> JsonArray(value.map(::sanitizeJson))
        is JsonPrimitive -> if (value.isString) JsonPrimitive(sanitizeText(value.content)) else value
    }

    private fun sanitizeText(value: String): String {
        val compact = dataUri.replace(value) { omittedBase64(it.value.substringAfter(',').length) }
        val withoutBase64 = base64Run.replace(compact) { omittedBase64(it.value.length) }
        return buildString {
            var offset = 0
            for (match in urlPattern.findAll(withoutBase64)) {
                append(sanitizeInline(withoutBase64.substring(offset, match.range.first)))
                append(match.value.toHttpUrlOrNull()?.let(::url) ?: "[URL omitted]")
                offset = match.range.last + 1
            }
            append(sanitizeInline(withoutBase64.substring(offset)))
        }
    }

    private fun sanitizeInline(value: String): String =
        tokenPattern.replace(inlineCredentials.replace(value) { "${it.groupValues[1]}${it.groupValues[2]}$REDACTED" }) {
            "${it.groupValues[1]}$REDACTED"
        }

    private fun omittedBase64(length: Int): String = "[omitted base64: ~${length * 3L / 4 / 1024} KiB]"

    private fun truncateUtf8(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_BODY_BYTES) return text
        var end = MAX_BODY_BYTES - TRUNCATED.length
        while ((bytes[end].toInt() and 0xC0) == 0x80) end--
        return bytes.decodeToString(endIndex = end) + TRUNCATED
    }

    private fun isBoundedJson(text: String): Boolean {
        var depth = 0
        var marks = 0
        var inString = false
        var escaped = false
        for (char in text) {
            if (inString) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
            } else {
                if ((char == '{' || char == '[' || char == ',' || char == ':') && ++marks > MAX_JSON_MARKS) {
                    return false
                }
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> if (++depth > MAX_JSON_DEPTH) return false
                    '}', ']' -> if (--depth < 0) return false
                }
            }
        }
        return !inString && depth == 0
    }
}
