package app.daybricks.planner.ui.planner

import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import app.daybricks.planner.R
import app.daybricks.planner.domain.CalendarEvent
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun EventDetailsDialog(event: CalendarEvent, onDismiss: () -> Unit,
    onOpenInCalendar: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(event.id) { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val eventZone = runCatching { ZoneId.of(event.timeZone.orEmpty()) }.getOrDefault(ZoneId.systemDefault())
    val endZone = runCatching { ZoneId.of(event.endTimeZone.orEmpty()) }.getOrDefault(eventZone)
    val timeText = if (event.allDay) {
        val first = dateFormat.format(event.start.atZone(ZoneOffset.UTC))
        val last = dateFormat.format(event.end.minusMillis(1).atZone(ZoneOffset.UTC))
        (if (first == last) first else "$first – $last") + " · " + stringResource(R.string.event_details_all_day)
    } else {
        val start = event.start.atZone(eventZone)
        val end = event.end.atZone(endZone)
        val startDate = dateFormat.format(start)
        val endDate = dateFormat.format(end)
        val range = if (start.toLocalDate() == end.toLocalDate()) {
            "$startDate · ${timeFormat.format(start)}–${timeFormat.format(end)}"
        } else {
            "$startDate ${timeFormat.format(start)} – $endDate ${timeFormat.format(end)}"
        }
        "$range (${if (eventZone == endZone) eventZone.id else "${eventZone.id} → ${endZone.id}"})"
    }
    val deleteLabel = stringResource(R.string.action_delete)
    val openLabel = stringResource(R.string.action_open_in_calendar)
    val deleteButton: @Composable () -> Unit = {
        FilledTonalIconButton(onClick = { confirmDelete = true },
            modifier = Modifier.semantics { contentDescription = deleteLabel },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )) { Text("🗑", fontSize = 24.sp, lineHeight = 24.sp) }
    }
    val closeButton: @Composable () -> Unit = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close_event)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                event.displayColor?.let { color ->
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color(color)))
                }
                Text(timeText, style = MaterialTheme.typography.bodyMedium)
                if (event.location.isNotBlank()) DetailField(stringResource(R.string.event_details_location), event.location)
                if (event.description.isNotBlank()) {
                    Text(stringResource(R.string.event_details_description), style = MaterialTheme.typography.labelMedium)
                    RichDescription(event.description)
                }
            }
        },
        confirmButton = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    deleteButton()
                    TextButton(onClick = onOpenInCalendar, modifier = Modifier.semantics {
                        contentDescription = openLabel
                    }) {
                        Text(stringResource(R.string.action_open_calendar_short), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    closeButton()
                }
            }
        },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.action_delete)) },
        text = { Text(stringResource(if (event.recurring) R.string.delete_event_series_confirmation
            else R.string.delete_event_confirmation, event.title)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() },
            modifier = Modifier.testTag("confirm-delete-event")) {
            Text(stringResource(R.string.action_delete))
        } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) {
            Text(stringResource(R.string.action_cancel))
        } },
    )
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RichDescription(description: String) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(factory = { context -> TextView(context).apply { movementMethod = LinkMovementMethod.getInstance() } },
        modifier = Modifier.fillMaxWidth(), update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.text = if (HTML_TAG.containsMatchIn(description))
                Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT) else description
            Linkify.addLinks(view, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS)
        })
}

private val HTML_TAG = Regex("</?(?:a|br|p|b|strong|i|em|u|ul|ol|li|div|span)(?:\\s|/?>)", RegexOption.IGNORE_CASE)
