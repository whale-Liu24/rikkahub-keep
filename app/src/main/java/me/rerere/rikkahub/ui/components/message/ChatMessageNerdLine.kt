package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown02
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toFixed
import java.time.Duration

/**
 * 显示消息的累计 token 用量和生成速度。
 */
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val usage = message.usage
    if (!LocalSettings.current.displaySetting.showTokenUsage || usage == null) return

    val durationMillis = remember(message) {
        message.finishedAt?.let { finishedAt ->
            // 旧消息未记录生成耗时时，用创建至完成的时长估算。
            message.generationTimeMs ?: Duration.between(
                message.createdAt.toJavaLocalDateTime(),
                finishedAt.toJavaLocalDateTime(),
            ).toMillis()
        }?.takeIf { it > 0 }
    }

    ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    StatsItem(
                        icon = { StatsIcon(HugeIcons.ArrowUp02, "Input") },
                        content = { Text("${usage.promptTokens.formatNumber()} tokens") },
                    )
                    if (usage.cachedTokens > 0) {
                        Text("(${usage.cachedTokens.formatNumber()} cached)")
                    }
                    StatsItem(
                        icon = { StatsIcon(HugeIcons.ArrowDown02, "Output") },
                        content = { Text("${usage.completionTokens.formatNumber()} tokens") },
                    )
                }

                if (durationMillis != null) {
                    val tps = usage.completionTokens.toFloat() / durationMillis * 1000
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatsItem(
                            icon = { StatsIcon(HugeIcons.Zap, "Speed") },
                            content = { Text("${tps.toFixed(1)} tok/s") },
                        )
                        StatsItem(
                            icon = { StatsIcon(HugeIcons.Clock01, "Duration") },
                            content = { Text("${(durationMillis / 1000f).toFixed(1)}s") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsIcon(imageVector: ImageVector, contentDescription: String) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(12.dp)
    )
}

@Composable
fun StatsItem(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        icon()
        content()
    }
}
