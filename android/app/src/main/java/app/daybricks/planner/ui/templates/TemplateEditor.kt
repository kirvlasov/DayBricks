package app.daybricks.planner.ui.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daybricks.planner.R
import app.daybricks.planner.domain.*
import app.daybricks.planner.ui.planner.ReminderSelector
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val editorJson = Json { ignoreUnknownKeys = false }
private val presetListSerializer = ListSerializer(BlockPreset.serializer())
private const val NO_ACTIVITY_EDITOR = Int.MIN_VALUE
private const val NEW_ACTIVITY = -1

@Composable
fun TemplateEditor(
    template: BlockTemplate?, sortOrder: Long, defaultReminderMinutes: Int?,
    onSave: (BlockTemplate) -> Unit, onDismiss: () -> Unit,
) {
    val id = rememberSaveable { template?.id ?: UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf(template?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(template?.description.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(template?.icon.orEmpty()) }
    var templateEmojiPickerOpen by rememberSaveable { mutableStateOf(false) }
    var activityEditorIndex by rememberSaveable { mutableIntStateOf(NO_ACTIVITY_EDITOR) }
    var duration by rememberSaveable { mutableStateOf((template?.defaultDurationMinutes ?: 15).toString()) }
    var reminderMinutes by rememberSaveable { mutableStateOf(if (template == null) defaultReminderMinutes else template.reminderMinutes) }
    var presetsJson by rememberSaveable {
        mutableStateOf(editorJson.encodeToString(presetListSerializer, template?.presets.orEmpty()))
    }
    val presets = remember(presetsJson) { editorJson.decodeFromString(presetListSerializer, presetsJson) }
    val valid = title.isNotBlank() && title.length <= 200 && description.length <= MAX_DESCRIPTION_LENGTH &&
        icon.length <= 32 && duration.toIntOrNull() in MIN_DURATION..MAX_DURATION && presets.size <= 100 &&
        presets.all { it.title.isNotBlank() && it.title.length <= 200 && it.description.length <= MAX_DESCRIPTION_LENGTH &&
            it.icon.orEmpty().length <= 32 && (it.durationMinutes == null || it.durationMinutes in MIN_DURATION..MAX_DURATION) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (template == null) R.string.template_editor_new_title else R.string.template_editor_edit_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title, { title = it.take(200) }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("template-name"),
                )
                OutlinedTextField(
                    description, { description = it.take(MAX_DESCRIPTION_LENGTH) }, label = { Text(stringResource(R.string.field_description)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                EmojiButton(icon) { templateEmojiPickerOpen = true }
                OutlinedTextField(
                    duration, { duration = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.template_default_duration_label)) },
                    supportingText = { Text(stringResource(R.string.template_duration_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ReminderSelector(reminderMinutes, { reminderMinutes = it }, stringResource(R.string.reminder_default_label))
                HorizontalDivider()
                Text(stringResource(R.string.template_presets_optional), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.template_presets_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                presets.forEachIndexed { index, preset ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(preset.icon?.ifBlank { null } ?: "•", fontSize = 26.sp, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(preset.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.template_duration_summary, preset.durationMinutes ?: duration.toIntOrNull() ?: 15),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (preset.description.isNotBlank()) Text(
                                    preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val editDescription = stringResource(R.string.action_edit_activity, preset.title)
                            TextButton(
                                onClick = { activityEditorIndex = index },
                                modifier = Modifier.testTag("edit-activity-option-$index").semantics { contentDescription = editDescription },
                            ) { Text(stringResource(R.string.action_edit)) }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { activityEditorIndex = NEW_ACTIVITY }, enabled = presets.size < 100,
                    modifier = Modifier.fillMaxWidth().testTag("add-activity-option"),
                ) { Text(stringResource(R.string.action_add_activity_option)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(BlockTemplate(
                        id = id, title = title.trim(), icon = icon.ifBlank { null }, description = description.trim(),
                        defaultDurationMinutes = duration.toInt(), reminderMinutes = reminderMinutes,
                        presets = presets.map { it.copy(title = it.title.trim(), description = it.description.trim(), icon = it.icon?.ifBlank { null }) },
                        sortOrder = template?.sortOrder ?: sortOrder,
                    ))
                }, enabled = valid, modifier = Modifier.testTag("save-template"),
            ) { Text(stringResource(R.string.action_save_template)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (templateEmojiPickerOpen) EmojiPickerDialog(
        onSelect = { selected -> icon = selected; templateEmojiPickerOpen = false },
        onDismiss = { templateEmojiPickerOpen = false },
    )

    if (activityEditorIndex != NO_ACTIVITY_EDITOR) {
        val editingIndex = activityEditorIndex
        val initial = if (editingIndex == NEW_ACTIVITY) BlockPreset(title = "") else presets[editingIndex]
        key(editingIndex) {
            ActivityEditorDialog(
                initial = initial,
                inheritedDurationMinutes = duration.toIntOrNull() ?: 15,
                isNew = editingIndex == NEW_ACTIVITY,
                onSave = { updated ->
                    val changed = if (editingIndex == NEW_ACTIVITY) presets + updated
                        else presets.toMutableList().also { it[editingIndex] = updated }
                    presetsJson = editorJson.encodeToString(presetListSerializer, changed)
                    activityEditorIndex = NO_ACTIVITY_EDITOR
                },
                onDelete = if (editingIndex == NEW_ACTIVITY) null else {{
                    presetsJson = editorJson.encodeToString(
                        presetListSerializer,
                        presets.toMutableList().also { it.removeAt(editingIndex) },
                    )
                    activityEditorIndex = NO_ACTIVITY_EDITOR
                }},
                onDismiss = { activityEditorIndex = NO_ACTIVITY_EDITOR },
            )
        }
    }
}

@Composable
private fun ActivityEditorDialog(
    initial: BlockPreset,
    inheritedDurationMinutes: Int,
    isNew: Boolean,
    onSave: (BlockPreset) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var icon by rememberSaveable { mutableStateOf(initial.icon.orEmpty()) }
    var duration by rememberSaveable { mutableStateOf((initial.durationMinutes ?: inheritedDurationMinutes).toString()) }
    var durationOverridden by rememberSaveable { mutableStateOf(initial.durationMinutes != null) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var emojiPickerOpen by rememberSaveable { mutableStateOf(false) }
    val valid = title.isNotBlank() && title.length <= 200 && icon.length <= 32 &&
        description.length <= MAX_DESCRIPTION_LENGTH && (duration.isBlank() || duration.toIntOrNull() in MIN_DURATION..MAX_DURATION)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("activity-editor"),
        title = { Text(if (isNew) stringResource(R.string.action_add_activity_option) else title) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    title, { title = it.take(200) }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("activity-name"),
                )
                EmojiButton(icon) { emojiPickerOpen = true }
                OutlinedTextField(
                    duration, {
                        duration = it.filter(Char::isDigit).take(3)
                        durationOverridden = true
                    },
                    label = { Text(stringResource(R.string.activity_option_duration_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("activity-duration"),
                )
                OutlinedTextField(
                    description, { description = it.take(MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(R.string.field_description)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).testTag("activity-description"),
                )
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) ActivityDialogAction(
                    symbol = "🗑",
                    description = stringResource(R.string.action_remove_activity_option),
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete-activity"),
                    destructive = true,
                )
                Spacer(Modifier.weight(1f))
                ActivityDialogAction(
                    symbol = "×",
                    description = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel-activity"),
                )
                ActivityDialogAction(
                    symbol = "✓",
                    description = stringResource(R.string.action_done),
                    onClick = {
                        onSave(BlockPreset(
                            title = title.trim(),
                            durationMinutes = if (durationOverridden) duration.toIntOrNull() else null,
                            icon = icon.ifBlank { null },
                            description = description.trim(),
                        ))
                    },
                    enabled = valid,
                    modifier = Modifier.testTag("save-activity"),
                )
            }
        },
        dismissButton = {},
    )

    if (emojiPickerOpen) EmojiPickerDialog(
        onSelect = { selected -> icon = selected; emojiPickerOpen = false },
        onDismiss = { emojiPickerOpen = false },
    )
}

@Composable
private fun ActivityDialogAction(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp).semantics { contentDescription = description },
        colors = if (destructive) IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else IconButtonDefaults.filledTonalIconButtonColors(),
    ) { Text(symbol, fontSize = 24.sp, lineHeight = 24.sp) }
}

@Composable
private fun EmojiButton(icon: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(icon.ifBlank { "+" }, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.emoji_choose_optional), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmojiPickerDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { ActivityEmojiCatalog.search(query) }
    val manualEmoji = remember(query) { ActivityEmojiCatalog.singleEmojiOrNull(query) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.emoji_picker_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("emoji-picker"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    query, { query = it.take(64) }, label = { Text(stringResource(R.string.emoji_search_short)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emoji-search"),
                )
                if (manualEmoji != null && results.none { it.value == manualEmoji }) {
                    FilledTonalButton(onClick = { onSelect(manualEmoji) }, modifier = Modifier.fillMaxWidth()) { Text(manualEmoji, fontSize = 28.sp) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(results, key = ActivityEmoji::value) { item ->
                        TextButton(onClick = { onSelect(item.value) }, contentPadding = PaddingValues(4.dp)) { Text(item.value, fontSize = 28.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = { onSelect("") }) { Text(stringResource(R.string.emoji_no_icon)) } },
    )
}
