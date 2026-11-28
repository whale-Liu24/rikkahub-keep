package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.rerere.common.android.LogEntry
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class HomeHttpLogStore(
    private val file: File,
    scope: CoroutineScope,
    private val publish: (List<LogEntry.RequestLog>) -> Unit,
    private val clearVisibleLogs: () -> Unit,
    private val onIoFailure: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxFileBytes: Long = 8 * 1024 * 1024L,
) {
    private val lock = Any()
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private val temporaryFile = File(file.parentFile, "${file.name}.tmp")
    private var epoch = 0L
    private var records = emptyList<LogEntry.RequestLog>()

    init {
        scope.launch(dispatcher) {
            restore()
            var diskEpoch = 0L
            for (signal in updates) {
                val snapshot = synchronized(lock) { Snapshot(epoch, records) }
                try {
                    if (snapshot.epoch != diskEpoch) {
                        deleteFiles()
                        diskEpoch = snapshot.epoch
                    }
                    if (snapshot.records.isNotEmpty()) writeSnapshot(snapshot)
                } catch (_: IOException) {
                    onIoFailure()
                    delay(1000)
                    updates.trySend(Unit)
                }
            }
        }
    }

    fun currentEpoch(): Long = synchronized(lock) { epoch }

    // Only already-sanitized entries reach this queue. The signal coalesces pending disk writes.
    fun record(entry: LogEntry.RequestLog, requestEpoch: Long) {
        synchronized(lock) {
            if (epoch != requestEpoch) return
            records = (listOf(entry) + records).latest()
            publish(records)
            updates.trySend(Unit)
        }
    }

    fun clear() {
        synchronized(lock) {
            epoch++
            records = emptyList()
            clearVisibleLogs()
            updates.trySend(Unit)
        }
    }

    private fun restore() {
        val saved = try {
            if (!file.isFile || file.length() > maxFileBytes) return
            file.bufferedReader().useLines { lines ->
                lines.take(MAX_ENTRIES).map { Json.decodeFromString<LogEntry.RequestLog>(it) }.toList()
            }
        } catch (_: IOException) {
            onIoFailure()
            return
        } catch (_: SerializationException) {
            onIoFailure()
            return
        }
        synchronized(lock) {
            // A clear before or during startup wins over records from the previous process.
            if (epoch != 0L) return
            records = (records + saved).latest()
            publish(records)
        }
    }

    private fun writeSnapshot(snapshot: Snapshot) {
        try {
            FileOutputStream(temporaryFile).use { stream ->
                val writer = stream.buffered()
                var bytesWritten = 0L
                for (entry in snapshot.records) {
                    val bytes = Json.encodeToString(entry).encodeToByteArray()
                    if (bytesWritten + bytes.size + 1 > maxFileBytes) break
                    writer.write(bytes)
                    writer.write('\n'.code)
                    bytesWritten += bytes.size + 1
                }
                writer.flush()
                stream.fd.sync()
            }
            if (snapshot.epoch != currentEpoch()) return
            Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // A clear racing the rename is handled on this same writer before its next write.
            if (snapshot.epoch != currentEpoch()) deleteFiles()
        } finally {
            Files.deleteIfExists(temporaryFile.toPath())
        }
    }

    private fun deleteFiles() {
        Files.deleteIfExists(file.toPath())
        Files.deleteIfExists(temporaryFile.toPath())
    }

    private fun List<LogEntry.RequestLog>.latest(): List<LogEntry.RequestLog> =
        distinctBy { it.id }.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)

    private data class Snapshot(val epoch: Long, val records: List<LogEntry.RequestLog>)

    private companion object {
        const val MAX_ENTRIES = 50
    }
}
