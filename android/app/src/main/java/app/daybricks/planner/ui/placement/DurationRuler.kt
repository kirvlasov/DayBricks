package app.daybricks.planner.ui.placement

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import app.daybricks.planner.R
import app.daybricks.planner.domain.*
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun DurationRuler(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    onDragStateChange: (Boolean) -> Unit = {}, showSelectedUnit: Boolean = true) {
    var fractional by remember { mutableFloatStateOf(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val latestChange by rememberUpdatedState(onChange)
    val latestDragStateChange by rememberUpdatedState(onDragStateChange)
    val latestValue by rememberUpdatedState(value)
    val durationDescription = pluralStringResource(R.plurals.duration_minutes_accessibility, value, value)
    val durationControl = stringResource(R.string.duration_control)
    val increaseAction = stringResource(R.string.duration_increase)
    val decreaseAction = stringResource(R.string.duration_decrease)
    val resources = LocalResources.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(value) { if (!dragging) fractional = value.toFloat() }
    val color = MaterialTheme.colorScheme.primary
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    Canvas(modifier.fillMaxWidth().height(70.dp).testTag("duration-ruler").semantics {
        contentDescription = durationControl
        stateDescription = durationDescription
        progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), MIN_DURATION.toFloat()..MAX_DURATION.toFloat(), MAX_DURATION - 2)
        setProgress { if (enabled) { latestChange(DurationRulerMath.snap(it)); true } else false }
        customActions = listOf(
            CustomAccessibilityAction(increaseAction) { if (enabled && latestValue < MAX_DURATION) { latestChange(latestValue + 1); true } else false },
            CustomAccessibilityAction(decreaseAction) { if (enabled && latestValue > MIN_DURATION) { latestChange(latestValue - 1); true } else false })
    }.pointerInput(enabled, rtl) {
        if (enabled) kotlinx.coroutines.coroutineScope {
            var displacement = 0f
            var ticker: Job? = null
            var emitted = latestValue
            fun finish() {
                ticker?.cancel(); ticker = null
                dragging = false
                displacement = 0f
                val finalValue = DurationRulerMath.snap(fractional)
                if (finalValue != emitted) latestChange(finalValue)
                fractional = finalValue.toFloat()
                latestDragStateChange(false)
            }
            detectHorizontalDragGestures(
                onDragStart = {
                    dragging = true
                    displacement = 0f
                    fractional = latestValue.toFloat()
                    emitted = latestValue
                    latestDragStateChange(true)
                    ticker = launch {
                        var previous = 0L
                        while (isActive) withFrameNanos { frame ->
                            if (previous != 0L) {
                                val elapsed = ((frame - previous) / 1_000_000_000f).coerceAtMost(.05f)
                                fractional = DurationRulerMath.advance(fractional, displacement, elapsed)
                                val snapped = DurationRulerMath.snap(fractional)
                                if (snapped != emitted) { emitted = snapped; latestChange(snapped) }
                            }
                            previous = frame
                        }
                    }
                },
                onDragEnd = ::finish,
                onDragCancel = ::finish,
            ) { change, delta ->
                change.consume()
                val directionalDelta = if (rtl) -delta else delta
                displacement = (displacement + directionalDelta / 96.dp.toPx()).coerceIn(-1f, 1f)
            }
        }
    }) {
        val widthDp = size.width / density.density
        val radius = ceil(widthDp / DurationRulerMath.DP_PER_MINUTE / 2).toInt() + 1
        val selected = DurationRulerMath.snap(fractional)
        for (minute in (selected - radius).coerceAtLeast(MIN_DURATION)..(selected + radius).coerceAtMost(MAX_DURATION)) {
            val tick = DurationRulerMath.tick(minute, fractional, widthDp)
            val naturalX = tick.x * density.density
            val x = if (rtl) size.width - naturalX else naturalX
            val length = (if (tick.strong) 15 else if (tick.major) 11 else 7).dp.toPx() * tick.scale
            drawLine(color.copy(alpha = tick.alpha), Offset(x, 20.dp.toPx() - length / 2), Offset(x, 20.dp.toPx() + length / 2), 1.5.dp.toPx())
            if (minute == selected || minute % 5 == 0) {
                paint.color = color.copy(alpha = tick.alpha).toArgb()
                paint.textSize = 12 * density.density * density.fontScale.coerceAtMost(1.5f) * tick.scale
                val label = if (minute == selected && showSelectedUnit) resources.getString(R.string.duration_minutes_short, minute) else minute.toString()
                drawContext.canvas.nativeCanvas.drawText(label, x, 54.dp.toPx(), paint)
            }
        }
        drawCircle(color, 3.dp.toPx(), Offset(size.width / 2, 3.dp.toPx()))
    }
}
