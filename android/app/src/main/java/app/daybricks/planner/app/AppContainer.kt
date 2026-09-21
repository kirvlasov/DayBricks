package app.daybricks.planner.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.daybricks.planner.BuildConfig
import app.daybricks.planner.calendar.AndroidCalendarRepository
import app.daybricks.planner.settings.KeystoreCredentialStore
import app.daybricks.planner.storage.DayBricksDatabase
import app.daybricks.planner.storage.LocalStateRepository
import app.daybricks.planner.sync.StateSyncRepository
import app.daybricks.planner.sync.StateTransport

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, DayBricksDatabase::class.java, "daybricks.db").build()
    val local = LocalStateRepository(database)
    val calendar = AndroidCalendarRepository(context.contentResolver)
    val credentials = KeystoreCredentialStore(context)
    val sync = StateSyncRepository(local, local, credentials, StateTransport(allowLocalHttp = BuildConfig.DEBUG))
}
class DayBricksApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguage.initialize(this)
    }
}
