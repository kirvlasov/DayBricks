package app.daybricks.planner.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.daybricks.planner.domain.CalendarEvent
import app.daybricks.planner.domain.SyncedPreferences
import app.daybricks.planner.ui.DayBricksTheme
import app.daybricks.planner.ui.planner.*
import app.daybricks.planner.ui.settings.LanguageOnboarding
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DayBricksApplication).container
        setContent {
            val planner: PlannerViewModel = viewModel(factory = viewModelFactory {
                initializer { PlannerViewModel(container.calendar, container.local, container.local, container.sync, container.credentials, createSavedStateHandle()) }
            })
            val state by planner.state.collectAsStateWithLifecycle()
            var languageChosen by rememberSaveable { mutableStateOf(AppLanguage.isLanguageChosen(this)) }
            var onboardingComplete by rememberSaveable { mutableStateOf(AppLanguage.isOnboardingComplete(this)) }
            var pendingImport by remember { mutableStateOf<AppDataExport?>(null) }
            val latestState by rememberUpdatedState(state)
            val exportScope = rememberCoroutineScope()
            fun writeExport(uri: Uri) {
                val snapshot = latestState
                exportScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        runCatching {
                            val export = AppDataExporter.snapshot(snapshot.templates, SyncedPreferences(), snapshot.device)
                            requireNotNull(contentResolver.openOutputStream(uri, "w")).use { output ->
                                AppDataExporter.writeJson(output, export)
                            }
                        }.isSuccess
                    }
                    Toast.makeText(
                        this@MainActivity,
                        getString(if (success) app.daybricks.planner.R.string.message_export_complete else app.daybricks.planner.R.string.error_export_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                uri?.let { writeExport(it) }
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let {
                    exportScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { requireNotNull(contentResolver.openInputStream(it)).use(AppDataImporter::read) }
                        }
                        result.onSuccess { data -> pendingImport = data }
                            .onFailure { Toast.makeText(this@MainActivity, getString(app.daybricks.planner.R.string.error_import_failed), Toast.LENGTH_LONG).show() }
                    }
                }
            }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                planner.dispatch(PlannerAction.PermissionsChanged(hasCalendarPermissions()))
            }
            val openExternalCalendarEvent: (CalendarEvent) -> Unit = { event ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli())
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli()))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, getString(app.daybricks.planner.R.string.error_install_calendar_app), Toast.LENGTH_LONG).show()
                }
            }
            LaunchedEffect(languageChosen, onboardingComplete) {
                if (languageChosen && !onboardingComplete) {
                    val starterLanguage = AppLanguage.currentTag(this@MainActivity)
                        ?: resources.configuration.locales[0].toLanguageTag()
                    planner.dispatch(PlannerAction.InstallStarterTemplates(starterLanguage))
                    planner.dispatch(PlannerAction.ShowSettings(true))
                }
            }
            DisposableEffect(planner) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
                    planner.dispatch(PlannerAction.PermissionsChanged(hasCalendarPermissions()))
                    planner.dispatch(PlannerAction.SyncNow)
                } }
                lifecycle.addObserver(observer)
                planner.dispatch(PlannerAction.PermissionsChanged(hasCalendarPermissions()))
                onDispose { lifecycle.removeObserver(observer) }
            }
            DayBricksTheme(state.device.theme) {
                if (!languageChosen) LanguageOnboarding { tag ->
                    languageChosen = true
                    AppLanguage.set(this, tag)
                } else PlannerScreen(state, planner::dispatch,
                    requestPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
                    openAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    currentLanguageTag = AppLanguage.currentTag(this),
                    setLanguage = { AppLanguage.set(this, it) },
                    openEvent = openExternalCalendarEvent,
                    onboarding = !onboardingComplete,
                    finishOnboarding = {
                        AppLanguage.completeOnboarding(this)
                        onboardingComplete = true
                    },
                    exportJson = { jsonExportLauncher.launch("DayBricks-${LocalDate.now()}.json") },
                    importData = { importLauncher.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) },
                )
                pendingImport?.let { data ->
                    AlertDialog(
                        onDismissRequest = { pendingImport = null },
                        title = { Text(getString(app.daybricks.planner.R.string.import_dialog_title)) },
                        text = { Text(getString(app.daybricks.planner.R.string.import_dialog_help)) },
                        confirmButton = { TextButton(onClick = {
                            pendingImport = null
                            planner.dispatch(PlannerAction.ImportAppData(data, replace = false))
                        }) { Text(getString(app.daybricks.planner.R.string.action_merge)) } },
                        dismissButton = {
                            TextButton(onClick = { pendingImport = null }) { Text(getString(app.daybricks.planner.R.string.action_cancel)) }
                            TextButton(onClick = {
                                pendingImport = null
                                planner.dispatch(PlannerAction.ImportAppData(data, replace = true))
                            }) { Text(getString(app.daybricks.planner.R.string.action_replace_all)) }
                        },
                    )
                }
            }
        }
    }
    private fun hasCalendarPermissions() = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
