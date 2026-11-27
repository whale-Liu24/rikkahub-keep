package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Recognizes a swipe on release. The current page stays still until the swipe is accepted.
 * Children get the first chance to claim the gesture on the Main pass.
 */
internal fun Modifier.previewSwipeGesture(
    layoutDirection: LayoutDirection,
    canEnterPreview: () -> Boolean,
    isPreviewMode: () -> Boolean,
    onPreviewModeChange: (Boolean) -> Unit,
): Modifier = pointerInput(layoutDirection) {
    val sign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val edgeInset = 32.dp.toPx()
    val flingThreshold = 400.dp.toPx()
    val slop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        if (down.position.x < edgeInset || down.position.x > width - edgeInset) return@awaitEachGesture
        val startInPreview = isPreviewMode()
        if (!startInPreview && !canEnterPreview()) return@awaitEachGesture

        val direction = if (startInPreview) sign else -sign
        val distanceThreshold = minOf(96.dp.toPx(), width * 0.25f)
        val tracker = VelocityTracker()
        tracker.addPointerInputChange(down)
        var claimed = false

        while (true) {
            val event = awaitPointerEvent(if (claimed) PointerEventPass.Initial else PointerEventPass.Main)
            if (event.changes.any { it.id != down.id && it.pressed }) return@awaitEachGesture
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (change.isConsumed) return@awaitEachGesture

            tracker.addPointerInputChange(change)
            val displacement = change.position - down.position
            val distance = displacement.x * direction
            if (change.changedToUpIgnoreConsumed()) {
                if (claimed) {
                    change.consume()
                    val velocity = tracker.calculateVelocity().x * direction
                    val target = !startInPreview
                    if (startInPreview == isPreviewMode() && (!target || canEnterPreview()) &&
                        (distance >= distanceThreshold || (distance > slop && velocity >= flingThreshold))
                    ) {
                        onPreviewModeChange(target)
                    }
                }
                return@awaitEachGesture
            }

            if (!claimed) {
                if (abs(displacement.y) >= slop && abs(displacement.y) >= abs(displacement.x)) {
                    return@awaitEachGesture
                }
                if (abs(displacement.x) < slop) continue
                if (distance <= 0f) return@awaitEachGesture
                claimed = true
            }
            change.consume()
        }
    }
}
