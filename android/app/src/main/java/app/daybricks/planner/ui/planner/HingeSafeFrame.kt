package app.daybricks.planner.ui.planner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.adaptive.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity

/** Keeps interactive controls in one continuous region when a physical hinge splits it. */
@Suppress("DEPRECATION")
@Composable
fun HingeSafeFrame(content: @Composable () -> Unit) {
    val posture = currentWindowAdaptiveInfo().windowPosture
    val hinges = (posture.separatingVerticalHingeBounds + posture.occludingVerticalHingeBounds +
        posture.separatingHorizontalHingeBounds + posture.occludingHorizontalHingeBounds).distinct()
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().onGloballyPositioned { origin = it.positionInWindow() }) {
        var region = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        hinges.forEach { windowHinge ->
            val hinge = windowHinge.translate(-origin)
            if (hinge.height > hinge.width && hinge.left > region.left && hinge.right < region.right) {
                region = if (hinge.left - region.left >= region.right - hinge.right) Rect(region.left, region.top, hinge.left, region.bottom)
                    else Rect(hinge.right, region.top, region.right, region.bottom)
            } else if (hinge.width >= hinge.height && hinge.top > region.top && hinge.bottom < region.bottom) {
                region = if (hinge.top - region.top >= region.bottom - hinge.bottom) Rect(region.left, region.top, region.right, hinge.top)
                    else Rect(region.left, hinge.bottom, region.right, region.bottom)
            }
        }
        with(density) {
            Box(Modifier.absoluteOffset(region.left.toDp(), region.top.toDp()).size(region.width.toDp(), region.height.toDp())) { content() }
        }
    }
}
