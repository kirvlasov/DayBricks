package app.daybricks.planner.ui.planner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.daybricks.planner.R

val ReminderChoices = listOf<Int?>(null, 0, 5, 10, 15, 30, 60)

@Composable
private fun reminderLabel(minutes: Int?): String = when (minutes) {
    null -> stringResource(R.string.reminder_none)
    0 -> stringResource(R.string.reminder_at_start)
    60 -> stringResource(R.string.reminder_one_hour_before)
    else -> pluralStringResource(R.plurals.reminder_minutes_before, minutes, minutes)
}

@Composable
fun ReminderSelector(
    value: Int?,
    onChange: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(reminderLabel(value), modifier = Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.wrapContentWidth()) {
                ReminderChoices.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(reminderLabel(option)) },
                        onClick = { onChange(option); expanded = false },
                        leadingIcon = { Text(if (option == value) "✓" else "  ") },
                    )
                }
            }
        }
    }
}
