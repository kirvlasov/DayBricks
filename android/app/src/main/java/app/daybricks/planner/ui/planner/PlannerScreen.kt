package app.daybricks.planner.ui.planner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import app.daybricks.planner.R
import app.daybricks.planner.domain.*
import app.daybricks.planner.ui.placement.DurationRuler
import app.daybricks.planner.ui.settings.SettingsDialog
import app.daybricks.planner.ui.templates.*
import app.daybricks.planner.ui.timeline.DayTimeline
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun PlannerScreen(state: PlannerUiState, dispatch: (PlannerAction) -> Unit,
    requestPermission: () -> Unit = {}, openAppSettings: () -> Unit = {}, currentLanguageTag: String? = null,
    setLanguage: (String?) -> Unit = {}, openEvent: (CalendarEvent) -> Unit = {},
    onboarding: Boolean = false, finishOnboarding: () -> Unit = {},
    exportJson: () -> Unit = {}, importData: () -> Unit = {}) {
    val snackbar = remember { SnackbarHostState() }
    var durationDragging by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    val showEvent: (CalendarEvent) -> Unit = { event ->
        if (state.device.openEventsDirectlyInCalendar) openEvent(event) else selectedEvent = event
    }
    BackHandler(state.draft != null && !state.settingsOpen && !state.editorOpen) { dispatch(PlannerAction.CancelDraft) }
    BackHandler(state.draft == null && state.saved.paneOpen && !state.settingsOpen && !state.editorOpen) { dispatch(PlannerAction.ToggleTemplatePane) }
    val messageText = state.message?.let { stringResource(it.resourceId()) }
    LaunchedEffect(messageText) { messageText?.let { snackbar.showSnackbar(it); dispatch(PlannerAction.DismissMessage) } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      HingeSafeFrame {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val windowWidth = maxWidth
            val layout = AdaptiveLayoutPolicy.chooseLayout(maxWidth.value, maxHeight.value, state.device.pane)
            val persistent = layout == PlannerLayout.PERSISTENT_TWO_PANE
            val transientPaneOpen = state.saved.paneOpen && state.draft == null
            val bottom = layout == PlannerLayout.TRANSIENT_BOTTOM
            val compact = maxHeight < 440.dp
            val aspectRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
            val paneWidth = (maxWidth * .32f).coerceIn(280.dp, 360.dp)
            val transientSideWidth = AdaptiveLayoutPolicy.sidePaneWidth(maxWidth.value, maxHeight.value).dp
            var bottomPaneFraction by rememberSaveable { mutableFloatStateOf(if (aspectRatio < .5f) .38f else .45f) }
            val density = LocalDensity.current
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val paneWidthPx = with(density) { transientSideWidth.toPx() }
            var sideDragging by remember { mutableStateOf(false) }
            var sideDragProgress by remember { mutableFloatStateOf(if (transientPaneOpen && !bottom) 1f else 0f) }
            var bottomDragging by remember { mutableStateOf(false) }
            var bottomDragProgress by remember { mutableFloatStateOf(if (transientPaneOpen && bottom) 1f else 0f) }
            val sideProgress by animateFloatAsState(
                targetValue = if (sideDragging) sideDragProgress else if (!bottom && transientPaneOpen) 1f else 0f,
                animationSpec = if (sideDragging) snap() else tween(260), label = "side panel",
            )
            val bottomProgress by animateFloatAsState(
                targetValue = if (bottomDragging) bottomDragProgress else if (bottom && transientPaneOpen) 1f else 0f,
                animationSpec = if (bottomDragging) snap() else tween(260), label = "bottom panel",
            )
            val currentSideProgress = rememberUpdatedState(sideProgress)
            val currentBottomProgress = rememberUpdatedState(bottomProgress)
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, shadowElevation = 2.dp) {
                    PlannerHeader(state, dispatch, compact)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (bottom) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val bottomAvailableHeight = maxHeight.coerceAtLeast(1.dp)
                        val paneHeight = bottomAvailableHeight * bottomPaneFraction
                        val paneHeightPx = with(density) { paneHeight.toPx() }
                        val bottomAvailableHeightPx = with(density) { bottomAvailableHeight.toPx() }
                        CalendarPane(
                            state, dispatch, requestPermission, showEvent, persistent, compact, 0f,
                            durationDragging = durationDragging,
                            onDurationDragChange = { durationDragging = it },
                            bottomSwipeEnabled = state.draft == null,
                            onBottomDragStart = {
                                bottomDragging = true
                                bottomDragProgress = currentBottomProgress.value
                            },
                            onBottomDrag = { delta ->
                                bottomDragProgress = (bottomDragProgress - delta / paneHeightPx).coerceIn(0f, 1f)
                            },
                            onBottomDragEnd = { total ->
                                val open = when {
                                    total < -20.dp.value * density.density -> true
                                    total > 20.dp.value * density.density -> false
                                    else -> bottomDragProgress >= .5f
                                }
                                bottomDragging = false
                                if (open != transientPaneOpen) dispatch(PlannerAction.ToggleTemplatePane)
                            },
                            onBottomDragCancel = { bottomDragging = false },
                            browseDockVisible = bottomDragging || bottomProgress < .999f,
                        )
                        if (bottomProgress > .001f || transientPaneOpen) {
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .height(paneHeight * bottomProgress).clipToBounds()) {
                            TemplatePane(state, dispatch, true, false, Modifier.fillMaxWidth().requiredHeight(paneHeight),
                                bottomFraction = bottomPaneFraction,
                                onBottomFractionChange = { bottomPaneFraction = it },
                                bottomAvailableHeightPx = bottomAvailableHeightPx,
                                onSwipeDown = { dispatch(PlannerAction.ToggleTemplatePane) },
                            )
                        }
                        }
                    }
                } else if (persistent) Row(Modifier.weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CalendarPane(state, dispatch, requestPermission, showEvent, persistent, compact,
                            AdaptiveLayoutPolicy.textCompression((windowWidth - paneWidth).value),
                            durationDragging = durationDragging,
                            onDurationDragChange = { durationDragging = it })
                    }
                    TemplatePane(state, dispatch, false, true, Modifier.width(paneWidth).fillMaxHeight())
                } else Row(Modifier.weight(1f).sidePaneGesture(
                    enabled = state.draft == null,
                    paneOpen = transientPaneOpen,
                    paneWidthPx = paneWidthPx,
                    currentProgress = { currentSideProgress.value },
                    rtl = rtl,
                    onDragStart = { progress -> sideDragging = true; sideDragProgress = progress },
                    onProgress = { sideDragProgress = it },
                    onSettled = { open ->
                        sideDragging = false
                        if (open != transientPaneOpen) dispatch(PlannerAction.ToggleTemplatePane)
                    },
                )) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CalendarPane(state, dispatch, requestPermission, showEvent, false, compact,
                            AdaptiveLayoutPolicy.textCompression((windowWidth - transientSideWidth * sideProgress).value),
                            durationDragging = durationDragging,
                            onDurationDragChange = { durationDragging = it })
                    }
                    if (sideProgress > .001f || transientPaneOpen) Box(
                        Modifier.width(transientSideWidth * sideProgress).fillMaxHeight().clipToBounds(),
                    ) {
                        TemplatePane(state, dispatch, false, false, Modifier.requiredWidth(transientSideWidth).fillMaxHeight())
                    }
                }
            }
            AnimatedVisibility(
                visible = durationDragging && state.draft != null,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .48f)).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    state.draft?.let { draft ->
                        val start = Instant.ofEpochMilli(draft.startMillis).atZone(state.zone)
                        val end = start.plusMinutes(draft.durationMinutes.toLong())
                        val locale = LocalConfiguration.current.locales[0]
                        val formatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
                        Surface(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .92f),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 18.dp,
                        ) {
                            Column(
                                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(stringResource(R.string.duration_minutes_short, draft.durationMinutes), style = MaterialTheme.typography.displaySmall)
                                val timeRange = stringResource(R.string.time_range, start.format(formatter), end.format(formatter))
                                Text(if (rtl) timeRange.replace("→", "←") else timeRange, style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                DurationRuler(draft.durationMinutes, {}, enabled = false, modifier = Modifier.fillMaxWidth(),
                                    showSelectedUnit = false)
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
      }
    }
    state.presetTemplate?.let { template ->
        AlertDialog(onDismissRequest = { dispatch(PlannerAction.DismissPresets) }, title = { Text(template.title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (template.description.isNotBlank()) Text(template.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                template.presets.forEach { preset ->
                    TextButton(onClick = { dispatch(PlannerAction.SelectPreset(template, preset)) }, modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                        Text(preset.icon ?: template.icon ?: "▰", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preset.title, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.template_duration_summary, preset.durationMinutes ?: template.defaultDurationMinutes), style = MaterialTheme.typography.bodySmall)
                            if (preset.description.isNotBlank()) Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                TextButton(onClick = { dispatch(PlannerAction.SelectPreset(template, null)) }) { Text(stringResource(R.string.action_custom)) }
            }
        }, confirmButton = { TextButton(onClick = { dispatch(PlannerAction.DismissPresets) }) { Text(stringResource(R.string.action_cancel)) } })
    }
    selectedEvent?.let { event ->
        EventDetailsDialog(event,
            onDismiss = { selectedEvent = null },
            onOpenInCalendar = { selectedEvent = null; openEvent(event) },
            onDelete = { selectedEvent = null; dispatch(PlannerAction.DeleteEvent(event)) })
    }
    if (state.editorOpen) TemplateEditor(state.editingTemplate, (state.templates.maxOfOrNull { it.sortOrder } ?: 0) + 100,
        state.device.defaultReminderMinutes,
        { dispatch(PlannerAction.SaveTemplate(it)) }, { dispatch(PlannerAction.CloseEditor) })
    if (state.settingsOpen) SettingsDialog(
        state, dispatch, requestPermission, openAppSettings, currentLanguageTag, setLanguage,
        onboarding = onboarding,
        finishOnboarding = finishOnboarding,
        exportJson = exportJson,
        importData = importData,
    )
}

private fun UiMessage.resourceId() = when (this) {
    UiMessage.CALENDAR_PERMISSION_REQUIRED -> R.string.error_calendar_permission_required
    UiMessage.COULD_NOT_SAVE -> R.string.error_could_not_save
    UiMessage.SYNC_SETTINGS_SAVED -> R.string.message_sync_settings_saved
    UiMessage.ALLOW_CALENDAR_BEFORE_ADDING -> R.string.error_allow_calendar_before_adding
    UiMessage.CHOOSE_CALENDAR_FOR_BLOCKS -> R.string.error_choose_calendar_for_blocks
    UiMessage.BLOCK_NAME_REQUIRED -> R.string.error_block_name_required
    UiMessage.BLOCK_ADDED -> R.string.message_block_added
    UiMessage.IMPORT_COMPLETE -> R.string.message_import_complete
}

private fun Modifier.sidePaneGesture(
    enabled: Boolean,
    paneOpen: Boolean,
    paneWidthPx: Float,
    currentProgress: () -> Float,
    rtl: Boolean,
    onDragStart: (Float) -> Unit,
    onProgress: (Float) -> Unit,
    onSettled: (Boolean) -> Unit,
): Modifier = if (!enabled) this else pointerInput(paneOpen, paneWidthPx) {
        var eligible = false
        var total = 0f
        var progress = currentProgress()
        detectHorizontalDragGestures(onDragStart = { start ->
            eligible = start.x > 32.dp.toPx() && start.x < size.width - 32.dp.toPx()
            total = 0f
            progress = currentProgress()
            if (eligible) onDragStart(progress)
        },
            onDragEnd = {
                val logicalTotal = total * if (rtl) -1f else 1f
                if (eligible) onSettled(when {
                    logicalTotal < -64.dp.toPx() -> true
                    logicalTotal > 64.dp.toPx() -> false
                    else -> progress >= .5f
                })
            },
            onDragCancel = { if (eligible) onSettled(paneOpen) },
        ) { change, delta ->
            if (eligible) {
                change.consume()
                total += delta
                progress = (progress - delta * (if (rtl) -1f else 1f) / paneWidthPx).coerceIn(0f, 1f)
                onProgress(progress)
            }
        }
    }

@Composable
private fun PlannerHeader(state: PlannerUiState, dispatch: (PlannerAction) -> Unit, compact: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val datePattern = stringResource(R.string.planner_date_header_pattern)
    val dateFormatter = remember(locale, datePattern) { DateTimeFormatter.ofPattern(datePattern, locale) }
    val formattedDate = remember(state.date, dateFormatter, locale) { formatPlannerDate(state.date, dateFormatter, locale) }
    val zoomOutDescription = stringResource(R.string.action_zoom_out)
    val zoomInDescription = stringResource(R.string.action_zoom_in)
    val settingsDescription = stringResource(R.string.action_open_settings)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 2.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formattedDate, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("selected-date"))
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 2.dp,
        ) {
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dispatch(PlannerAction.ChangeZoom(state.saved.zoom / 1.25f)) },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = zoomOutDescription }) {
                    Text("−", fontSize = 30.sp, lineHeight = 30.sp)
                }
                VerticalDivider(Modifier.height(26.dp), color = MaterialTheme.colorScheme.outlineVariant)
                IconButton(onClick = { dispatch(PlannerAction.ChangeZoom(state.saved.zoom * 1.25f)) },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = zoomInDescription }) {
                    Text("+", fontSize = 30.sp, lineHeight = 30.sp)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 2.dp,
        ) {
            IconButton(onClick = { dispatch(PlannerAction.ShowSettings(true)) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = settingsDescription }) {
                Text("⚙", fontSize = 25.sp, lineHeight = 25.sp)
            }
        }
    }
}

internal fun formatPlannerDate(date: java.time.LocalDate, formatter: DateTimeFormatter, locale: Locale): String {
    val capitalized = date.format(formatter).replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(locale) else first.toString()
    }
    val digitIndex = capitalized.indexOfFirst(Char::isDigit)
    if (digitIndex < 0) return capitalized
    val commaIndex = capitalized.lastIndexOf(',', digitIndex)
    val protectedStart = if (commaIndex >= 0) {
        (commaIndex + 1 until capitalized.length).firstOrNull { !capitalized[it].isWhitespace() } ?: digitIndex
    } else digitIndex
    return capitalized.substring(0, protectedStart) +
        capitalized.substring(protectedStart).replace(' ', '\u00A0')
}

@Composable
private fun CalendarPane(state: PlannerUiState, dispatch: (PlannerAction) -> Unit, requestPermission: () -> Unit,
    openEvent: (CalendarEvent) -> Unit, persistent: Boolean, compact: Boolean, compressionProgress: Float,
    durationDragging: Boolean,
    onDurationDragChange: (Boolean) -> Unit,
    bottomSwipeEnabled: Boolean = false,
    onBottomDragStart: () -> Unit = {},
    onBottomDrag: (Float) -> Unit = {},
    onBottomDragEnd: (Float) -> Unit = {},
    onBottomDragCancel: () -> Unit = {},
    browseDockVisible: Boolean = true,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val allDay = state.events.filter { it.allDay }
        if (allDay.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().heightIn(max = 84.dp).testTag("all-day-events"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allDay, key = { "${it.id}-${it.start}" }) { event ->
                val statusMark = when (event.attendanceStatus) {
                    AttendanceStatus.ACCEPTED -> "✓ "
                    AttendanceStatus.TENTATIVE -> "~ "
                    AttendanceStatus.NEEDS_ACTION -> "? "
                    AttendanceStatus.DECLINED -> "× "
                    AttendanceStatus.UNKNOWN -> ""
                }
                AssistChip(onClick = { openEvent(event) },
                modifier = Modifier.graphicsLayer { alpha = if (event.attendanceStatus == AttendanceStatus.DECLINED) .4f else 1f },
                label = { Text(stringResource(R.string.event_all_day, "${if (event.owned) "" else statusMark}${event.title}"), maxLines = 1,
                    modifier = Modifier.graphicsLayer { alpha = (1f - compressionProgress).coerceIn(0f, 1f) }) }) }
        }
        if (!state.hasPermission) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calendar_permission_banner), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = requestPermission) { Text(stringResource(R.string.action_allow_access)) }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            DayTimeline(state, dispatch, openEvent, compressionProgress = compressionProgress)
            if (state.draft == null && browseDockVisible) Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(top = 8.dp)
                    .graphicsLayer {
                        alpha = (1f - compressionProgress * 1.35f).coerceIn(0f, 1f)
                        translationY = compressionProgress * 32.dp.toPx()
                    },
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .96f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) {
                BrowseDock(state, dispatch, persistent,
                    modifier = Modifier.bottomPanelSwipe(bottomSwipeEnabled, onBottomDragStart, onBottomDrag,
                        onBottomDragEnd, onBottomDragCancel))
            }
        }
        if (state.draft != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) { PlacementDock(state, dispatch, compact, durationDragging, onDurationDragChange) }
        }
    }
}

@Composable
private fun BrowseDock(state: PlannerUiState, dispatch: (PlannerAction) -> Unit, persistent: Boolean, modifier: Modifier = Modifier) {
    val previousDayDescription = stringResource(R.string.action_previous_day)
    val nextDayDescription = stringResource(R.string.action_next_day)
    val templateLibraryDescription = stringResource(if (state.saved.paneOpen) R.string.action_close_template_library else R.string.action_open_template_library)
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { dispatch(PlannerAction.PreviousDay) }, modifier = Modifier.size(48.dp).testTag("previous-day").semantics { contentDescription = previousDayDescription }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        Spacer(Modifier.width(24.dp))
        if (!persistent) FloatingActionButton(onClick = { dispatch(PlannerAction.ToggleTemplatePane) }, modifier = Modifier.testTag("toggle-templates").semantics { contentDescription = templateLibraryDescription }) { Text(if (state.saved.paneOpen) "×" else "+", style = MaterialTheme.typography.headlineMedium) }
        Spacer(Modifier.width(24.dp))
        FilledTonalIconButton(onClick = { dispatch(PlannerAction.NextDay) }, modifier = Modifier.size(48.dp).testTag("next-day").semantics { contentDescription = nextDayDescription }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

private fun Modifier.bottomPanelSwipe(
    enabled: Boolean,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (Float) -> Unit,
    onCancel: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var previousY = down.position.y
            var total = 0f
            var dragging = false
            var pressed: Boolean
            do {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position.y - previousY
                previousY = change.position.y
                total += delta
                if (!dragging && kotlin.math.abs(total) > viewConfiguration.touchSlop) {
                    dragging = true
                    onStart()
                    onDrag(total)
                } else if (dragging) {
                    onDrag(delta)
                }
                if (dragging) change.consume()
                pressed = change.pressed
            } while (pressed)
            if (dragging) onEnd(total)
        }
    } finally {
        onCancel()
    }
}

@Composable
private fun PlacementDock(state: PlannerUiState, dispatch: (PlannerAction) -> Unit, compact: Boolean,
    durationDragging: Boolean,
    onDurationDragChange: (Boolean) -> Unit) {
    val draft = state.draft ?: return
    var editTitle by remember { mutableStateOf(false) }
    if (editTitle) AlertDialog(onDismissRequest = { editTitle = false }, title = { Text(stringResource(R.string.event_title_prompt)) },
        text = { OutlinedTextField(draft.title, { dispatch(PlannerAction.ChangeDraftTitle(it)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), shape = RoundedCornerShape(16.dp)) },
        confirmButton = { TextButton(onClick = { editTitle = false }) { Text(stringResource(R.string.action_done)) } })
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!compact) OutlinedTextField(draft.title, { dispatch(PlannerAction.ChangeDraftTitle(it)) }, label = { Text(stringResource(R.string.event_title_prompt)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), enabled = !state.saving,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("draft-title"))
        else TextButton(onClick = { editTitle = true }, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(draft.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        ReminderSelector(draft.reminderMinutes, { dispatch(PlannerAction.ChangeDraftReminder(it)) }, stringResource(R.string.reminder_label))
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Box(Modifier.fillMaxWidth().height(76.dp)) {
            DurationRuler(
                draft.durationMinutes,
                { dispatch(PlannerAction.ChangeDraftDuration(it)) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 72.dp)
                    .graphicsLayer { alpha = if (durationDragging) 0f else 1f },
                onDragStateChange = onDurationDragChange,
            )
            Box(Modifier.align(if (rtl) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)) {
                ConfirmPlacementButton(state.saving, "confirm-${if (rtl) "left" else "right"}") {
                    dispatch(PlannerAction.ConfirmDraft)
                }
            }
            Box(Modifier.align(if (rtl) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)) {
                CancelPlacementButton(state.saving) { dispatch(PlannerAction.CancelDraft) }
            }
        }
    }
}

@Composable
private fun ConfirmPlacementButton(saving: Boolean, tag: String, onClick: () -> Unit) {
    val confirmDescription = stringResource(R.string.action_confirm_block)
    Surface(
        onClick = onClick,
        enabled = !saving,
        modifier = Modifier.size(56.dp).testTag(tag).semantics { contentDescription = confirmDescription },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFD4EED9),
        contentColor = Color(0xFF174D28),
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (saving) CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.dp, color = Color(0xFF174D28))
            else Canvas(Modifier.size(24.dp)) {
                drawLine(color = Color(0xFF174D28), start = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .54f),
                    end = androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .76f), strokeWidth = 2.8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = Color(0xFF174D28), start = androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .76f),
                    end = androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .25f), strokeWidth = 2.8.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun CancelPlacementButton(saving: Boolean, onClick: () -> Unit) {
    val cancelDescription = stringResource(R.string.action_cancel_placement)
    Surface(
        onClick = onClick,
        enabled = !saving,
        modifier = Modifier.size(56.dp).testTag("cancel-draft").semantics { contentDescription = cancelDescription },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF1D4D2),
        contentColor = Color(0xFF682622),
        shadowElevation = 2.dp,
    ) { Box(contentAlignment = Alignment.Center) { Text("×", fontSize = 30.sp, lineHeight = 30.sp) } }
}
