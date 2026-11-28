package me.rerere.rikkahub.ui.pages.log

import android.content.ClipData
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.common.http.HttpRequestLogCategory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.HomeHttpLogStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogPage() {
    val store = koinInject<SettingsStore>()
    val httpLogStore = koinInject<HomeHttpLogStore>()
    val scope = rememberCoroutineScope()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val logs by Logging.recentLogs.collectAsStateWithLifecycle()
    val requestLoggingEnabled = !settings.init && settings.displaySetting.httpRequestLoggingEnabled
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Logs") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = httpLogStore::clear
                    ) {
                        Icon(HugeIcons.Delete01, null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        UnifiedLogList(
            logs = logs,
            requestLoggingEnabled = requestLoggingEnabled,
            onRequestLoggingChange = {
                scope.launch {
                    store.update { current ->
                        current.copy(displaySetting = current.displaySetting.copy(httpRequestLoggingEnabled = it))
                    }
                    Logging.setRequestLoggingEnabled(store.settingsFlow.value.displaySetting.httpRequestLoggingEnabled)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    }
}

@Composable
private fun UnifiedLogList(
    logs: List<LogEntry>,
    requestLoggingEnabled: Boolean,
    onRequestLoggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLog by remember { mutableStateOf<LogEntry.RequestLog?>(null) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val sortedLogs = remember(logs, filter) {
        logs.filter(filter::includes).sortedByDescending { it.timestamp }
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            RequestLoggingSwitchCard(
                enabled = requestLoggingEnabled,
                onEnabledChange = onRequestLoggingChange
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.title)) },
                    )
                }
            }
        }

        items(sortedLogs, key = { it.id }, contentType = { it.javaClass.simpleName }) { log ->
            when (log) {
                is LogEntry.RequestLog -> RequestLogCard(
                    log = log,
                    onClick = {
                        selectedLog = log
                        scope.launch { sheetState.show() }
                    }
                )

                is LogEntry.TextLog -> TextLogCard(log = log)
            }
        }
    }

    selectedLog?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { selectedLog = null },
            sheetState = sheetState
        ) {
            RequestLogDetail(log)
        }
    }
}

@Composable
private fun RequestLoggingSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.log_page_record_requests),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.log_page_record_requests_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

private enum class LogFilter(val title: Int) {
    ALL(R.string.log_page_filter_all),
    CHAT(R.string.log_page_filter_chat),
    MEMORY(R.string.log_page_filter_memory),
    OTHER(R.string.log_page_filter_other);

    fun includes(log: LogEntry): Boolean = when (this) {
        ALL -> true
        CHAT -> log is LogEntry.RequestLog && log.source in chatSources
        MEMORY -> log is LogEntry.RequestLog && log.source == HttpRequestLogCategory.MEMORY
        OTHER -> log is LogEntry.TextLog || (log is LogEntry.RequestLog &&
            log.source !in chatSources && log.source != HttpRequestLogCategory.MEMORY)
    }
}

private val chatSources = setOf(HttpRequestLogCategory.CHAT, HttpRequestLogCategory.TOOL_CONTINUATION)

private val LogEntry.RequestLog.source: HttpRequestLogCategory
    get() = category ?: HttpRequestLogCategory.SYSTEM

private val HttpRequestLogCategory.title: Int
    get() = when (this) {
        HttpRequestLogCategory.CHAT -> R.string.log_page_source_chat
        HttpRequestLogCategory.TOOL_CONTINUATION -> R.string.log_page_source_tool
        HttpRequestLogCategory.MEMORY -> R.string.log_page_source_memory
        HttpRequestLogCategory.COMPRESS -> R.string.log_page_source_compress
        HttpRequestLogCategory.TITLE -> R.string.log_page_source_title
        HttpRequestLogCategory.SUGGESTION -> R.string.log_page_source_suggestion
        HttpRequestLogCategory.SYSTEM -> R.string.log_page_source_system
    }

private val LogEntry.RequestLog.isFollowUp: Boolean
    get() = groupId != null && (stepIndex ?: 0) > 1

private fun String.logGroupCode(): String = take(6).uppercase(Locale.ROOT)

private fun Long.formatBodySize(): String = when {
    this < 1_024 -> "$this B"
    this < 1_048_576 -> String.format(Locale.getDefault(), "%.0f KiB", this / 1_024.0)
    else -> String.format(Locale.getDefault(), "%.1f MiB", this / 1_048_576.0)
}

@Composable
private fun RequestLogCard(log: LogEntry.RequestLog, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val colors = MaterialTheme.colorScheme
    val badgeColors = when (log.source) {
        HttpRequestLogCategory.CHAT -> colors.primaryContainer to colors.onPrimaryContainer
        HttpRequestLogCategory.TOOL_CONTINUATION -> colors.secondaryContainer to colors.onSecondaryContainer
        HttpRequestLogCategory.MEMORY -> colors.tertiaryContainer to colors.onTertiaryContainer
        HttpRequestLogCategory.COMPRESS -> colors.secondaryContainer to colors.onSecondaryContainer
        HttpRequestLogCategory.TITLE, HttpRequestLogCategory.SUGGESTION ->
            colors.surfaceContainerHighest to colors.primary
        HttpRequestLogCategory.SYSTEM -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    val groupStep = buildList {
        log.groupId?.let { add("#${it.logGroupCode()}") }
        log.stepIndex?.let { add(stringResource(R.string.log_page_step, it)) }
    }.joinToString(" · ")
    val location = remember(log.url) {
        log.url.toHttpUrlOrNull()?.let { it.host + it.encodedPath } ?: log.url
    }
    val metrics = listOfNotNull(
        log.requestBodyBytes?.formatBodySize(),
        log.responseCode?.toString(),
        log.durationMs?.let { "${it}ms" },
    ).joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (log.isFollowUp) {
                        val x = 2.dp.toPx()
                        drawLine(
                            color = badgeColors.second.copy(alpha = 0.40f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = badgeColors.first, contentColor = badgeColors.second) {
                    Text(
                        text = stringResource(log.source.title),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                Text(
                    text = if (log.isFollowUp) "↳ $groupStep" else groupStep,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(log.modelName, "${log.method} $location").joinToString(" · "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metrics.isNotEmpty()) {
                    Text(
                        text = metrics,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (log.responseCode?.let { it >= 400 } == true) colors.error else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            val triggers = buildList {
                if (log.toolNames.isNotEmpty()) {
                    add(stringResource(R.string.log_page_trigger_tools, log.toolNames.joinToString()))
                }
                if (log.skillNames.isNotEmpty()) {
                    add(stringResource(R.string.log_page_trigger_skills, log.skillNames.joinToString()))
                }
            }
            if (triggers.isNotEmpty()) {
                Text(
                    text = triggers.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            log.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RequestLogDetail(log: LogEntry.RequestLog) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Request Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                DetailSection(stringResource(R.string.log_page_detail_source), stringResource(log.source.title))
            }
            log.groupId?.let { groupId ->
                item { DetailSection(stringResource(R.string.log_page_detail_group), "#${groupId.logGroupCode()} · $groupId") }
            }
            log.stepIndex?.let { step ->
                item { DetailSection(stringResource(R.string.log_page_detail_step), step.toString()) }
            }
            log.modelName?.let { model ->
                item { DetailSection(stringResource(R.string.log_page_detail_model), model) }
            }
            if (log.toolNames.isNotEmpty()) {
                item { DetailSection(stringResource(R.string.log_page_detail_tools), log.toolNames.joinToString()) }
            }
            if (log.skillNames.isNotEmpty()) {
                item { DetailSection(stringResource(R.string.log_page_detail_skills), log.skillNames.joinToString()) }
            }
            log.requestBodyBytes?.let { bytes ->
                item { DetailSection(stringResource(R.string.log_page_detail_size), bytes.formatBodySize()) }
            }

            item {
                DetailSection("Time", dateFormat.format(Date(log.timestamp)))
            }

            item {
                DetailSection("URL", log.url)
            }

            item {
                DetailSection("Method", log.method)
            }

            log.responseCode?.let { code ->
                item {
                    DetailSection("Status Code", code.toString())
                }
            }

            log.durationMs?.let { duration ->
                item {
                    DetailSection("Duration", "${duration}ms")
                }
            }

            log.error?.let { error ->
                item {
                    DetailSection("Error", error)
                }
            }

            if (log.requestHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "Request Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.requestHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }

            log.requestBody?.let { body ->
                item {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Request Body",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("Request Body", body))
                                        )
                                    } catch (e: RuntimeException) {
                                        // 内容过大时 Binder 会抛出 TransactionTooLargeException (被包装为 RuntimeException)
                                        Toast.makeText(
                                            context,
                                            "Copy failed: content too large for clipboard",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Copy01,
                                contentDescription = stringResource(R.string.copy)
                            )
                        }
                    }
                    val jsonElement by produceState<JsonElement?>(null, body) {
                        value = withContext(Dispatchers.Default) {
                            runCatching { JsonInstantPretty.parseToJsonElement(body) }.getOrNull()
                        }
                    }
                    val parsedBody = jsonElement
                    if (parsedBody != null) {
                        JsonTree(
                            json = parsedBody,
                            modifier = Modifier.padding(top = 4.dp),
                            initialExpandLevel = if (body.length > 16_384) 0 else 2
                        )
                    } else {
                        Text(
                            text = if (body.length <= 16_384) body
                            else body.take(16_384) + "\n[preview limited; copy for the stored body]",
                            fontFamily = JetbrainsMono,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (log.responseHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "Response Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.responseHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun HeaderItem(key: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun TextLogCard(log: LogEntry.TextLog) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetbrainsMono
                )
            }
        }
    }
}
