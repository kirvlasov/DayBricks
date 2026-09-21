package app.daybricks.planner.ui.templates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import app.daybricks.planner.R
import app.daybricks.planner.domain.*
import app.daybricks.planner.ui.planner.*

@Composable
fun TemplatePane(state: PlannerUiState, dispatch: (PlannerAction) -> Unit, bottom: Boolean, persistent: Boolean,
    modifier: Modifier = Modifier, bottomFraction: Float = .45f,
    onBottomFractionChange: (Float) -> Unit = {}, bottomAvailableHeightPx: Float = 1f,
    onSwipeDown: () -> Unit = {}) {
    Surface(modifier.testTag(if (persistent) "persistent-pane" else if (bottom) "bottom-pane" else "side-pane"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (!bottom && !persistent) 6.dp else 0.dp,
        shadowElevation = if (!bottom && !persistent) 8.dp else 0.dp) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            val resizePanelDescription = stringResource(R.string.action_resize_template_panel)
            val latestBottomFraction = rememberUpdatedState(bottomFraction)
            val latestSwipeDown = rememberUpdatedState(onSwipeDown)
            val dismissThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
            val listDismissConnection = remember(bottom, dismissThresholdPx) {
                object : NestedScrollConnection {
                    var downwardDistance = 0f
                    var dismissed = false

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (!bottom || source != NestedScrollSource.UserInput) return Offset.Zero
                        if (available.y > 0f) downwardDistance += available.y else if (available.y < 0f) downwardDistance = 0f
                        if (!dismissed && downwardDistance >= dismissThresholdPx) {
                            dismissed = true
                            latestSwipeDown.value()
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        downwardDistance = 0f
                        dismissed = false
                        return Velocity.Zero
                    }
                }
            }
            var resizeHandleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            if (bottom) Box(Modifier.fillMaxWidth().height(32.dp)
                .onGloballyPositioned { resizeHandleCoordinates = it }
                .semantics {
                    contentDescription = resizePanelDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(bottomFraction, BottomPaneSizing.MIN_FRACTION..BottomPaneSizing.MAX_FRACTION)
                    setProgress { requested -> onBottomFractionChange(requested.coerceIn(BottomPaneSizing.MIN_FRACTION, BottomPaneSizing.MAX_FRACTION)); true }
                }
                .pointerInput(bottomAvailableHeightPx) {
                    var startFraction = latestBottomFraction.value
                    var startWindowY = 0f
                    var accumulatedDelta = 0f
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startFraction = latestBottomFraction.value
                            startWindowY = resizeHandleCoordinates?.localToWindow(offset)?.y ?: offset.y
                            accumulatedDelta = 0f
                        },
                    ) { change, amount ->
                        change.consume()
                        accumulatedDelta += amount
                        val windowY = resizeHandleCoordinates?.localToWindow(change.position)?.y
                        val dragDelta = windowY?.minus(startWindowY) ?: accumulatedDelta
                        onBottomFractionChange(BottomPaneSizing.resize(startFraction, dragDelta, bottomAvailableHeightPx))
                    }
                }, contentAlignment = Alignment.Center) {
                Surface(Modifier.width(40.dp).height(4.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.outline) {}
            }
            Row(
                modifier = if (bottom) Modifier.swipeDownToDismiss(onSwipeDown) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text(stringResource(R.string.template_library_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.template_library_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val closeTemplatesDescription = stringResource(R.string.action_close_templates)
                if (!persistent) IconButton(onClick = { dispatch(PlannerAction.ToggleTemplatePane) }, modifier = Modifier.semantics { contentDescription = closeTemplatesDescription }) { Text("×") }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("template-list")
                    .then(if (bottom) Modifier.nestedScroll(listDismissConnection) else Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    OutlinedButton(onClick = { dispatch(PlannerAction.EditTemplate(null)) }, modifier = Modifier.fillMaxWidth().testTag("add-template")) { Text(stringResource(R.string.action_add_template)) }
                }
                if (state.templates.isEmpty()) item {
                    Text(stringResource(R.string.template_empty_state), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                itemsIndexed(state.templates, key = { _, template -> template.id }) { index, template ->
                    var menu by remember { mutableStateOf(false) }
                    Card(onClick = { dispatch(PlannerAction.SelectTemplate(template)) }, enabled = state.draft == null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().testTag("template-${template.id}")) {
                        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(template.icon?.ifBlank { null } ?: "▰", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(template.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val duration = stringResource(R.string.template_duration_summary, template.defaultDurationMinutes)
                                val presets = if (template.presets.isNotEmpty()) pluralStringResource(
                                    R.plurals.template_presets_count,
                                    template.presets.size,
                                    template.presets.size,
                                ) else null
                                Text(listOfNotNull(duration, presets).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (template.description.isNotBlank()) Text(
                                    template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box {
                                val optionsDescription = stringResource(R.string.template_options_for, template.title)
                                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = optionsDescription }) { Text("⋮") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; dispatch(PlannerAction.EditTemplate(template)) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, enabled = index > 0, onClick = { menu = false; dispatch(PlannerAction.MoveTemplate(template.id, -1)) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, enabled = index < state.templates.lastIndex, onClick = { menu = false; dispatch(PlannerAction.MoveTemplate(template.id, 1)) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; dispatch(PlannerAction.DeleteTemplate(template.id)) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.swipeDownToDismiss(onDismiss: () -> Unit): Modifier = pointerInput(onDismiss) {
    var downwardDistance = 0f
    detectVerticalDragGestures(
        onDragStart = { downwardDistance = 0f },
        onDragEnd = { if (downwardDistance >= 56.dp.toPx()) onDismiss() },
        onDragCancel = { downwardDistance = 0f },
    ) { change, amount ->
        if (amount > 0f) downwardDistance += amount else downwardDistance = 0f
        if (downwardDistance > 0f) change.consume()
    }
}
