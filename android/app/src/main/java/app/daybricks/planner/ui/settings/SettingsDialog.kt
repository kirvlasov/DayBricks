package app.daybricks.planner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.daybricks.planner.R
import app.daybricks.planner.app.AppLanguage
import app.daybricks.planner.domain.*
import app.daybricks.planner.ui.planner.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsDialog(
    state: PlannerUiState,
    dispatch: (PlannerAction) -> Unit,
    requestPermission: () -> Unit,
    openAppSettings: () -> Unit,
    currentLanguageTag: String?,
    setLanguage: (String?) -> Unit,
    onboarding: Boolean = false,
    finishOnboarding: () -> Unit = {},
    exportJson: () -> Unit = {},
    importData: () -> Unit = {},
) {
    var url by rememberSaveable { mutableStateOf(state.device.serverUrl) }
    var targetCalendarExpanded by rememberSaveable { mutableStateOf(onboarding) }
    var calendarListExpanded by rememberSaveable { mutableStateOf(false) }
    var languageExpanded by rememberSaveable { mutableStateOf(false) }
    val interfaceLocale = LocalConfiguration.current.locales[0]
    val sortedLanguageTags = remember(interfaceLocale) { AppLanguage.sortedTags(interfaceLocale) }
    // Deliberately not rememberSaveable: never put API credentials in activity saved state.
    var token by remember { mutableStateOf("") }
    fun close() {
        dispatch(PlannerAction.ShowSettings(false))
        if (onboarding) finishOnboarding()
    }
    AlertDialog(onDismissRequest = ::close,
        title = { Text(stringResource(if (onboarding) R.string.onboarding_setup_title else R.string.settings_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val targetCalendar = state.calendars.firstOrNull { it.id == state.device.targetCalendarId }
                Row(
                    Modifier.fillMaxWidth().clickable { targetCalendarExpanded = !targetCalendarExpanded }
                        .testTag("target-calendar-toggle").padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_create_activities_in), style = MaterialTheme.typography.titleSmall)
                        Text(targetCalendar?.let { stringResource(R.string.calendar_name_and_account, it.name, it.account) }
                            ?: stringResource(R.string.settings_choose_writable_calendar), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (targetCalendarExpanded) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
                }
                if (targetCalendarExpanded) {
                    if (!state.hasPermission) {
                        Text(stringResource(R.string.calendar_permission_explanation))
                        Button(onClick = requestPermission) { Text(stringResource(R.string.action_allow_calendar_access)) }
                        TextButton(onClick = openAppSettings) { Text(stringResource(R.string.action_open_android_app_settings)) }
                    } else if (state.calendars.isEmpty()) Text(stringResource(R.string.settings_no_writable_calendars))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.calendars.forEach { calendar ->
                            CalendarOptionRow(
                                calendar = calendar,
                                selected = state.device.targetCalendarId == calendar.id,
                                multiple = false,
                                modifier = Modifier.testTag("target-calendar-${calendar.id}"),
                            ) { dispatch(PlannerAction.SelectCalendar(calendar.id)); targetCalendarExpanded = false }
                        }
                    }
                }
                HorizontalDivider()
                val visibleCalendarCount = state.displayCalendars.count { it.id !in state.device.hiddenCalendarIds }
                Row(
                    Modifier.fillMaxWidth().clickable { calendarListExpanded = !calendarListExpanded }
                        .testTag("display-calendars-toggle").padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_display_calendars_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.displayCalendars.isEmpty()) stringResource(R.string.settings_no_readable_calendars)
                            else stringResource(R.string.settings_calendars_shown, visibleCalendarCount, state.displayCalendars.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(if (calendarListExpanded) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
                }
                if (calendarListExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.displayCalendars.forEach { calendar ->
                            val visible = calendar.id !in state.device.hiddenCalendarIds
                            CalendarOptionRow(
                                calendar = calendar,
                                selected = visible,
                                multiple = true,
                                modifier = Modifier.testTag("display-calendar-${calendar.id}"),
                            ) { dispatch(PlannerAction.SetCalendarVisible(calendar.id, !visible)) }
                        }
                    }
                    Text(stringResource(R.string.settings_calendar_filter_help), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { languageExpanded = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(currentLanguageTag?.let(AppLanguage::displayName) ?: stringResource(R.string.choice_language_system), Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choice_language_system)) },
                            onClick = { languageExpanded = false; setLanguage(null) },
                            leadingIcon = { Text(if (currentLanguageTag == null) "✓" else "  ") },
                        )
                        sortedLanguageTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(AppLanguage.displayName(tag)) },
                                onClick = { languageExpanded = false; setLanguage(tag) },
                                leadingIcon = { Text(if (currentLanguageTag == tag) "✓" else "  ") },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(ThemePreference.entries, state.device.theme, { when (it) { ThemePreference.SYSTEM -> stringResource(R.string.choice_theme_system); ThemePreference.LIGHT -> stringResource(R.string.choice_theme_light); ThemePreference.DARK -> stringResource(R.string.choice_theme_dark) } }) { dispatch(PlannerAction.SetTheme(it)) }
                Text(stringResource(R.string.settings_template_panel), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(PanePreference.entries, state.device.pane, { when (it) { PanePreference.AUTO -> stringResource(R.string.choice_template_panel_auto); PanePreference.SIDE -> stringResource(R.string.choice_template_panel_right); PanePreference.BOTTOM -> stringResource(R.string.choice_template_panel_bottom) } }) { dispatch(PlannerAction.SetPane(it)) }
                ReminderSelector(state.device.defaultReminderMinutes, { dispatch(PlannerAction.SetDefaultReminder(it)) },
                    stringResource(R.string.settings_default_reminder))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().testTag("open-events-directly")
                    .toggleable(state.device.openEventsDirectlyInCalendar, role = Role.Switch) {
                        dispatch(PlannerAction.SetOpenEventsDirectly(it))
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_open_events_directly), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_open_events_directly_help), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.device.openEventsDirectlyInCalendar, onCheckedChange = null)
                }
                HorizontalDivider()
                Text(stringResource(R.string.settings_backup_restore), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = exportJson, modifier = Modifier.weight(1f).testTag("export-json")) {
                        Text(stringResource(R.string.action_export_data))
                    }
                    OutlinedButton(onClick = importData, modifier = Modifier.weight(1f).testTag("import-data")) {
                        Text(stringResource(R.string.action_import_short))
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.settings_sync_server_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_sync_server_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.field_server_url)) }, placeholder = { Text(stringResource(R.string.server_url_example)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.field_api_token)) }, supportingText = { Text(stringResource(R.string.api_token_keep_saved_help)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                OutlinedButton(onClick = { dispatch(PlannerAction.SaveSync(url, token)); token = "" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save_sync_settings)) }
                Button(onClick = { dispatch(PlannerAction.SyncNow) }, enabled = !state.sync.running && state.device.serverUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (state.sync.running) R.string.sync_in_progress else R.string.action_sync_now)) }
                val locale = LocalConfiguration.current.locales[0]
                val timeFormatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
                Text(if (state.sync.error != null) stringResource(R.string.error_sync_failed)
                    else state.sync.lastSuccess?.let { stringResource(R.string.sync_last_success, it.atZone(state.zone).format(timeFormatter)) }
                        ?: stringResource(R.string.sync_offline_status), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.action_done)) } })
}

@Composable
private fun CalendarOptionRow(
    calendar: CalendarInfo,
    selected: Boolean,
    multiple: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val selection = if (multiple) {
        Modifier.toggleable(selected, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.selectable(selected, role = Role.RadioButton) { onClick() }
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).then(selection)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiple) Checkbox(checked = selected, onCheckedChange = null)
        else RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
            Text(calendar.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) }) }
    }
}
