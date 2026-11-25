package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Attachment01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.Image01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.SearchRemove
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toPreviewTimeString
import kotlin.uuid.Uuid

private data class PreviewMessage(
    val id: Uuid,
    val index: Int,
    val message: UIMessage,
    val text: String,
    val favorite: Boolean,
)

private fun List<MessageNode>.toPreviewMessages(): List<PreviewMessage> = mapIndexed { index, node ->
    val message = node.currentMessage
    PreviewMessage(node.id, index, message, message.toPreviewText(), node.isFavorite)
}

/** 消息概览按原始消息节点逐条展示，搜索后的列表位置不作为跳转索引。 */
@Composable
internal fun ChatListPreview(
    innerPadding: PaddingValues,
    conversation: Conversation,
    chatListState: LazyListState,
    listState: LazyListState,
    settings: Settings,
    hazeState: HazeState,
    onJumpToMessage: (Int) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val query = searchQuery.trim()

    // 长对话里逐条做正则清洗很慢，放到后台算；没算完时为 null。
    // 生成中节点每个 chunk 都在变，conflate 掉中间状态，只算最新的
    val nodes by rememberUpdatedState(conversation.messageNodes)
    val loadedMessages by produceState<List<PreviewMessage>?>(initialValue = null, key1 = conversation.id) {
        value = null
        snapshotFlow { nodes }
            .conflate()
            .collect { currentNodes -> value = withContext(Dispatchers.Default) { currentNodes.toPreviewMessages() } }
    }
    val messages = loadedMessages.orEmpty()
    val filtered = remember(messages, query) {
        if (query.isEmpty()) messages else messages.filter { it.text.contains(query, ignoreCase = true) }
    }

    val assistant = remember(settings.assistants, conversation.assistantId) {
        settings.getAssistantById(conversation.assistantId)
    }
    val modelById = remember(settings.providers) {
        settings.providers.flatMap { it.models }.associateBy { it.id }
    }

    // 打开时的阅读位置；概览期间聊天列表不动，记一次就够
    val currentIndex = remember(conversation.id) { chatListState.firstVisibleItemIndex }
    val loaded = loadedMessages != null
    LaunchedEffect(loaded, conversation.id) {
        if (!loaded || filtered.isEmpty()) return@LaunchedEffect
        val currentPosition = filtered.indexOfFirst { it.index >= currentIndex }
        val position = if (currentPosition < 0) filtered.lastIndex else (currentPosition - 1).coerceAtLeast(0)
        listState.scrollToItem(position)
    }
    // 搜索框固定在列表外，滚到中间时改搜索词，结果要从头看
    LaunchedEffect(listState) {
        snapshotFlow { searchQuery.trim() }.drop(1).collect { listState.scrollToItem(0) }
    }

    // 搜索框固定在列表上方，列表内容滚到搜索框下方时渐隐。
    val searchBarTop = innerPadding.calculateTopPadding() + 8.dp
    val listTop = searchBarTop + PreviewSearchBarHeight + 12.dp
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val bubbleMaxWidth = maxWidth * 0.84f
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp) + PaddingValues(
                top = listTop,
                bottom = 32.dp + innerPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .topFadingEdge(listTop)
                .hazeSource(state = hazeState),
        ) {
            if (loaded && filtered.isEmpty()) {
                item(key = PreviewEmptyKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.SearchRemove,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.search_page_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(items = filtered, key = { it.id }) { entry ->
                val jumpToMessage: () -> Unit = {
                    val currentMessageIndex = nodes.indexOfFirst { it.id == entry.id }
                    if (currentMessageIndex >= 0) onJumpToMessage(currentMessageIndex)
                }
                if (entry.message.isContextCheckpoint) {
                    PreviewCheckpointCard(
                        entry = entry,
                        query = query,
                        isCurrent = currentIndex == entry.index,
                        onClick = jumpToMessage,
                        modifier = Modifier.animateItem(),
                    )
                } else {
                    val message = entry.message
                    PreviewMessageBubble(
                        entry = entry,
                        query = query,
                        assistant = assistant,
                        model = message.modelId?.let(modelById::get) ?: message.snapshotModel(),
                        maxWidth = bubbleMaxWidth,
                        isCurrent = currentIndex == entry.index,
                        onClick = jumpToMessage,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        PreviewSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            resultCount = filtered.size,
            totalCount = messages.size,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = searchBarTop),
        )
    }
}

private val PreviewSearchBarHeight = 52.dp
private const val PreviewEmptyKey = "PreviewEmpty"

@Composable
private fun PreviewSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(PreviewSearchBarHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Search01,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_page_search_messages),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = if (query.isBlank()) "$totalCount" else "$resultCount / $totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = if (query.isEmpty()) 12.dp else 0.dp),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.chat_page_cancel),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewMessageBubble(
    entry: PreviewMessage,
    query: String,
    assistant: Assistant?,
    model: Model?,
    maxWidth: Dp,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = entry.message
    val isUser = message.role == MessageRole.USER
    val colors = MaterialTheme.colorScheme
    val bubbleContentColor = if (isUser) colors.onPrimaryContainer else colors.onSurface
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.widthIn(max = maxWidth),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                onClick = onClick,
                shape = MaterialTheme.shapes.large,
                color = if (isUser) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = bubbleContentColor,
                border = if (isCurrent) BorderStroke(1.5.dp, colors.primary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (message.role == MessageRole.ASSISTANT) {
                        PreviewReplyAvatar(assistant = assistant, model = model)
                    }
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        when {
                            entry.text.isNotEmpty() -> HighlightedSnippet(
                                text = entry.text,
                                query = query,
                                maxLines = 3,
                                style = MaterialTheme.typography.bodyMedium,
                                color = bubbleContentColor,
                            )

                            message.parts.hasAttachmentSummary() -> PreviewAttachmentSummary(message.parts)

                            else -> Text(
                                text = stringResource(
                                    if (isUser) R.string.user_default_name else R.string.assistant_page_default_assistant
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (entry.favorite) {
                        Icon(
                            imageVector = HugeIcons.FavouriteCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.primary,
                        )
                    }
                }
            }
            PreviewTimestamp(message)
        }
    }
}

@Composable
private fun PreviewTimestamp(message: UIMessage, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val timestamp = remember(message.createdAt) {
        // 部分导入器用 Unix epoch 表示缺失时间。
        runCatching {
            message.createdAt.toJavaLocalDateTime().takeIf { it.year > 1970 }?.toPreviewTimeString()
        }.getOrNull()
    } ?: return
    Text(
        text = timestamp,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * 压缩检查点：用色调容器和图标和普通对话区分开，一眼能看出上下文从哪里开始被摘要替代。
 */
@Composable
private fun PreviewCheckpointCard(
    entry: PreviewMessage,
    query: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        border = if (isCurrent) BorderStroke(1.5.dp, colors.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Package01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.chat_page_compress_checkpoint_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PreviewTimestamp(entry.message, color = colors.onSecondaryContainer)
            }
            if (entry.text.isNotEmpty()) {
                HighlightedSnippet(
                    text = entry.text,
                    query = query,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun HighlightedSnippet(
    text: String,
    query: String,
    maxLines: Int,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer
    val annotated = remember(text, query, highlightColor, highlightTextColor) {
        buildHighlightedText(
            // Text 会把整段文字都排版一遍才截到 maxLines，长回复必须先截短
            text = extractMatchingSnippet(text, query).take(SnippetMaxLength),
            query = query,
            highlightColor = highlightColor,
            highlightTextColor = highlightTextColor,
        )
    }
    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PreviewReplyAvatar(
    assistant: Assistant?,
    model: Model?,
) {
    // 和两行正文的首行对齐
    val avatarModifier = Modifier
        .padding(top = 1.dp)
        .size(18.dp)
    when {
        assistant?.useAssistantAvatar == true -> UIAvatar(
            name = assistant.name,
            value = assistant.avatar,
            modifier = avatarModifier,
        )

        model != null -> AutoAIIcon(name = model.modelId, modifier = avatarModifier)
    }
}

/**
 * 图片、文件、工具调用没有文字，用图标 + 数量概括，避免整条只剩空白。
 */
@Composable
private fun PreviewAttachmentSummary(parts: List<UIMessagePart>) {
    val images = parts.count { it is UIMessagePart.Image }
    val files = parts.count { it is UIMessagePart.Document || it is UIMessagePart.Video || it is UIMessagePart.Audio }
    val tools = remember(parts) {
        parts.filterIsInstance<UIMessagePart.Tool>().map { it.toolName }.distinct()
    }
    if (images == 0 && files == 0 && tools.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (images > 0) AttachmentChip(icon = HugeIcons.Image01, label = images.toString())
        if (files > 0) AttachmentChip(icon = HugeIcons.Attachment01, label = files.toString())
        if (tools.isNotEmpty()) {
            AttachmentChip(
                icon = HugeIcons.Wrench01,
                label = tools.joinToString(", "),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private fun List<UIMessagePart>.hasAttachmentSummary(): Boolean = any {
    it is UIMessagePart.Image || it is UIMessagePart.Document || it is UIMessagePart.Video ||
        it is UIMessagePart.Audio || it is UIMessagePart.Tool
}

@Composable
private fun AttachmentChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val CodeFenceRegex = Regex("(?:`{3,}|~{3,})[\\w-]*")
private val HeadingRegex = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
private val QuoteRegex = Regex("(?m)^\\s{0,3}>\\s?")
private val EmphasisRegex = Regex("\\*\\*|__|~~")
private val WhitespaceRegex = Regex("\\s+")

private fun UIMessage.toPreviewText(): String = toText()
    .replace(CodeFenceRegex, " ")
    .replace(HeadingRegex, "")
    .replace(QuoteRegex, "")
    .replace(EmphasisRegex, "")
    .replace(WhitespaceRegex, " ")
    .trim()

/**
 * 截取包含搜索词的片段：匹配词前保留一小段上下文，保证三行内能看到匹配位置。
 */
private fun extractMatchingSnippet(text: String, query: String): String {
    if (query.isBlank()) return text
    val matchIndex = text.indexOf(query, ignoreCase = true)
    if (matchIndex <= SnippetContextLength) return text
    // 尽量从词边界开始截，免得开头是半个单词
    val start = text.indexOf(' ', matchIndex - SnippetContextLength).let { space ->
        if (space in 0 until matchIndex) space + 1 else matchIndex - SnippetContextLength
    }
    return "…" + text.substring(start)
}

private const val SnippetContextLength = 24

// 气泡最多显示三行，提前截断长消息以减少排版开销。
private const val SnippetMaxLength = 300

private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color,
    highlightTextColor: Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var startIndex = 0
        var index = text.indexOf(query, startIndex, ignoreCase = true)
        while (index >= 0) {
            append(text.substring(startIndex, index))
            withStyle(
                SpanStyle(
                    background = highlightColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                append(text.substring(index, index + query.length))
            }
            startIndex = index + query.length
            index = text.indexOf(query, startIndex, ignoreCase = true)
        }
        if (startIndex < text.length) append(text.substring(startIndex))
    }
}
