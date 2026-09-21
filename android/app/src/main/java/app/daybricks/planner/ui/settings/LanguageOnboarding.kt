package app.daybricks.planner.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.daybricks.planner.R
import app.daybricks.planner.app.AppLanguage

@Composable
fun LanguageOnboarding(onSelect: (String?) -> Unit) {
    val interfaceLocale = LocalConfiguration.current.locales[0]
    val languages = remember(interfaceLocale) { AppLanguage.sortedTags(interfaceLocale) }
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("language-onboarding"),
        title = { Text(stringResource(R.string.onboarding_language_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                item {
                    TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth().testTag("onboarding-language-system")) {
                        Text(stringResource(R.string.choice_language_system), modifier = Modifier.fillMaxWidth())
                    }
                }
                items(languages, key = { it }) { tag ->
                    TextButton(onClick = { onSelect(tag) }, modifier = Modifier.fillMaxWidth().testTag("onboarding-language-$tag")) {
                        Text(AppLanguage.displayName(tag), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
    )
}
