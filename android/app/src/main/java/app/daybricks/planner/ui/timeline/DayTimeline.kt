package app.daybricks.planner.ui.timeline

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.LayoutDirection
import app.daybricks.planner.R
import app.daybricks.planner.domain.*
import app.daybricks.planner.ui.planner.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun DayTimeline(state: PlannerUiState, dispatch: (PlannerAction) -> Unit, openEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier, compressionProgress: Float = 0f) {
    val axis = remember(state.date, state.zone) { TimeAxisMapper(state.date, state.zone) }
    val positions = remember(state.events, axis) { EventLayoutEngine.layout(state.events, axis) }
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var pinchZoom by remember { mutableStateOf<Float?>(null) }
    var pinchViewport by remember { mutableStateOf<Double?>(null) }
    val animatedZoom by animateFloatAsState(
        targetValue = state.saved.zoom,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "timeline zoom",
    )
    val zoom = pinchZoom ?: animatedZoom
    val viewportMinute = pinchViewport ?: state.saved.viewportMinute
    val latestZoom by rememberUpdatedState(zoom)
    val latestViewport by rememberUpdatedState(viewportMinute)
    val latestDispatch by rememberUpdatedState(dispatch)
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val timeFormat = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    val gridTimeFormat = timeFormat
    val zoomInDescription = stringResource(R.string.action_zoom_in)
    val zoomOutDescription = stringResource(R.string.action_zoom_out)
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(30_000) } }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    BoxWithConstraints(modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).testTag("timeline")) {
        val viewportHeight = maxHeight
        val centerPx = with(density) { viewportHeight.toPx() / 2 }
        val dayHeight = (axis.totalMinutes * zoom).toFloat().dp
        val fullLabelWidth = if (maxWidth < 200.dp) 48.dp else 64.dp
        val labelWidth = lerp(fullLabelWidth, 36.dp, compressionProgress.coerceIn(0f, 1f))
        val eventWidth = (maxWidth - labelWidth - 8.dp).coerceAtLeast(16.dp)
        LaunchedEffect(scroll, axis) {
            snapshotFlow { scroll.value to scroll.isScrollInProgress }.distinctUntilChanged().collect { (offset, userScrolling) ->
                if (userScrolling) latestDispatch(PlannerAction.ChangeViewport(offset / (latestZoom * density.density).toDouble()))
            }
        }
        LaunchedEffect(axis, viewportHeight, state.draft?.templateId) {
            if (!scroll.isScrollInProgress) {
                scroll.scrollTo((latestViewport * latestZoom * density.density).roundToInt())
            }
        }
        SideEffect {
            if (!scroll.isScrollInProgress) {
                val target = (viewportMinute * zoom * density.density).roundToInt()
                scroll.dispatchRawDelta((target - scroll.value).toFloat())
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(axis, viewportHeight) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var transforming = false
                var gestureZoom = latestZoom
                var gestureViewport = scroll.value / (latestZoom * density.density).toDouble()
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        val factor = event.calculateZoom()
                        val currentCentroid = event.calculateCentroid(useCurrent = true)
                        val previousCentroid = event.calculateCentroid(useCurrent = false)
                        if (!transforming) {
                            transforming = true
                            gestureZoom = latestZoom
                            gestureViewport = scroll.value / (gestureZoom * density.density).toDouble()
                            pinchZoom = gestureZoom
                            pinchViewport = gestureViewport
                        }
                        if (factor.isFinite() && currentCentroid != Offset.Unspecified && previousCentroid != Offset.Unspecified) {
                            val old = gestureZoom
                            val next = (old * factor).coerceIn(.6f, 5f)
                            val anchorMinute = gestureViewport + (previousCentroid.y - centerPx) / (old * density.density)
                            val nextViewport = (anchorMinute - (currentCentroid.y - centerPx) / (next * density.density))
                                .coerceIn(0.0, axis.totalMinutes - 1)
                            gestureZoom = next
                            gestureViewport = nextViewport
                            pinchZoom = next
                            pinchViewport = nextViewport
                            scroll.dispatchRawDelta(((nextViewport * next * density.density) - scroll.value).toFloat())
                            latestDispatch(PlannerAction.ChangeZoom(next, nextViewport))
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
                pinchZoom = null
                pinchViewport = null
            }
        }.verticalScroll(scroll, enabled = !state.saving).semantics {
            customActions = listOf(
                CustomAccessibilityAction(zoomInDescription) { dispatch(PlannerAction.ChangeZoom(zoom * 1.2f)); true },
                CustomAccessibilityAction(zoomOutDescription) { dispatch(PlannerAction.ChangeZoom(zoom / 1.2f)); true })
        }) {
            Box(Modifier.fillMaxWidth().height(dayHeight + viewportHeight)) {
                Canvas(Modifier.fillMaxWidth().height(dayHeight).offset(y = viewportHeight / 2)) {
                    val gridStartX = if (rtl) size.width - labelWidth.toPx() else labelWidth.toPx()
                    val gridEndX = if (rtl) 0f else size.width
                    val labelX = if (rtl) size.width - 6.dp.toPx() else 6.dp.toPx()
                    paint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
                    val step = axis.gridStep(zoom)
                    for (minute in 0..axis.totalMinutes.toInt() step step) {
                        val y = minute * zoom * density.density
                        val time = axis.localTimeAt(minute.toDouble())
                        val major = time.minute == 0
                        drawLine(colors.outlineVariant.copy(alpha = if (major) .8f else .35f), Offset(gridStartX, y), Offset(gridEndX, y), if (major) 1.dp.toPx() else .5.dp.toPx())
                        paint.color = colors.onSurfaceVariant.toArgb()
                        paint.textSize = 10 * density.density * density.fontScale.coerceAtMost(1.5f)
                        val label = time.format(gridTimeFormat)
                        drawContext.canvas.nativeCanvas.drawText(label, labelX, y + 4.dp.toPx(), paint)
                        if (axis.isRepeatedHour(minute.toDouble())) {
                            paint.textSize = 8 * density.density
                            drawContext.canvas.nativeCanvas.drawText(time.offset.id, labelX, y + 14.dp.toPx(), paint)
                        }
                    }
                    if (now >= axis.start && now < axis.end) {
                        val y = (axis.minuteAt(now) * zoom * density.density).toFloat()
                        drawLine(colors.error.copy(alpha = .75f), Offset(gridStartX, y), Offset(gridEndX, y), 1.5.dp.toPx())
                        drawCircle(colors.error, 3.dp.toPx(), Offset(gridStartX, y))
                    }
                }
                positions.forEach { position ->
                    val event = position.event
                    val width = eventWidth / position.columns
                    val height = ((position.endMinute - position.startMinute) * zoom).toFloat().dp.coerceAtLeast(2.dp)
                    val statusLabel = when (event.attendanceStatus) {
                        AttendanceStatus.ACCEPTED -> stringResource(R.string.attendance_accepted)
                        AttendanceStatus.TENTATIVE -> stringResource(R.string.attendance_tentative)
                        AttendanceStatus.NEEDS_ACTION -> stringResource(R.string.attendance_needs_action)
                        AttendanceStatus.DECLINED -> stringResource(R.string.attendance_declined)
                        AttendanceStatus.UNKNOWN -> stringResource(R.string.attendance_unknown)
                    }
                    val statusMark = when (event.attendanceStatus) {
                        AttendanceStatus.ACCEPTED -> "✓ "
                        AttendanceStatus.TENTATIVE -> "~ "
                        AttendanceStatus.NEEDS_ACTION -> "? "
                        AttendanceStatus.DECLINED -> "× "
                        AttendanceStatus.UNKNOWN -> ""
                    }
                    val spokenTitle = if (event.owned) stringResource(R.string.event_owned_prefix, event.title) else event.title
                    val description = stringResource(
                        R.string.event_accessibility_description,
                        spokenTitle,
                        statusLabel,
                        event.start.atZone(state.zone).format(timeFormat),
                        event.end.atZone(state.zone).format(timeFormat),
                    )
                    val eventColor = when {
                        event.owned -> colors.primaryContainer
                        event.attendanceStatus == AttendanceStatus.ACCEPTED -> colors.secondaryContainer
                        event.attendanceStatus == AttendanceStatus.TENTATIVE -> colors.tertiaryContainer
                        event.attendanceStatus == AttendanceStatus.NEEDS_ACTION -> colors.surfaceContainerHighest
                        else -> colors.surfaceContainerHigh
                    }
                    val eventBorder = when {
                        event.owned -> BorderStroke(1.dp, colors.primary.copy(alpha = .55f))
                        event.attendanceStatus == AttendanceStatus.NEEDS_ACTION -> BorderStroke(1.dp, colors.tertiary)
                        event.attendanceStatus == AttendanceStatus.DECLINED -> BorderStroke(1.dp, colors.outline)
                        else -> null
                    }
                    Surface(onClick = { openEvent(event) }, shape = RoundedCornerShape(7.dp),
                        color = eventColor,
                        border = eventBorder,
                        modifier = Modifier.offset(x = labelWidth + width * position.column, y = viewportHeight / 2 + (position.startMinute * zoom).toFloat().dp)
                            .width((width - 3.dp).coerceAtLeast(1.dp)).height(height)
                            .graphicsLayer { alpha = if (event.attendanceStatus == AttendanceStatus.DECLINED) .32f else 1f }
                            .semantics { contentDescription = description }) {
                        if (compressionProgress < .8f && width >= 64.dp && height >= 25.dp) Column(Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            .graphicsLayer { alpha = (1f - compressionProgress * 1.25f).coerceIn(0f, 1f) }) {
                            Text((if (event.owned) "" else statusMark) + event.title, style = MaterialTheme.typography.labelMedium,
                                textDecoration = if (event.attendanceStatus == AttendanceStatus.DECLINED) TextDecoration.LineThrough else null,
                                maxLines = if (height > 60.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
                            if (height >= 55.dp && width >= 115.dp) Text(event.start.atZone(state.zone).format(timeFormat), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
        state.draft?.let { draft ->
            val start = Instant.ofEpochMilli(draft.startMillis).atZone(state.zone)
            val blockHeight = (draft.durationMinutes * zoom).dp.coerceAtLeast(2.dp)
            Surface(Modifier.align(Alignment.TopStart).offset(y = viewportHeight / 2)
                .padding(start = labelWidth, end = 8.dp).fillMaxWidth().height(blockHeight).testTag("draft"),
                shape = RoundedCornerShape(12.dp), color = colors.primaryContainer.copy(alpha = .96f),
                border = BorderStroke(2.dp, colors.primary), shadowElevation = 4.dp) {
                if (blockHeight >= 58.dp) Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    DraftDescription(draft, start, timeFormat, axis)
                } else Box(Modifier.fillMaxSize())
            }
            if (blockHeight < 58.dp) Surface(
                modifier = Modifier.align(Alignment.TopStart).offset(y = viewportHeight / 2 + blockHeight + 6.dp)
                    .padding(start = labelWidth, end = 8.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceContainerHigh.copy(alpha = .96f),
                border = BorderStroke(1.dp, colors.outlineVariant),
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    DraftDescription(draft, start, timeFormat, axis)
                }
            }
        }
    }
}

@Composable
private fun DraftDescription(
    draft: EventDraft,
    start: java.time.ZonedDateTime,
    timeFormat: DateTimeFormatter,
    axis: TimeAxisMapper,
) {
    val fallbackTitle = stringResource(R.string.event_new_block_fallback)
    Text(listOfNotNull(draft.icon, draft.title.ifBlank { fallbackTitle }).joinToString(" "),
        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    val summary = stringResource(R.string.draft_time_summary, start.format(timeFormat), start.plusMinutes(draft.durationMinutes.toLong()).format(timeFormat), draft.durationMinutes)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Text(if (rtl) summary.replace("→", "←") else summary,
        style = MaterialTheme.typography.labelMedium)
    if (axis.isRepeatedHour(axis.minuteAt(start.toInstant()))) Text(stringResource(R.string.utc_offset_label, start.offset.toString()), style = MaterialTheme.typography.labelSmall)
}
