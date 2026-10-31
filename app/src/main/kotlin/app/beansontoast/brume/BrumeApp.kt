package app.beansontoast.brume

import android.app.Application
import android.content.Context
import app.beansontoast.brume.data.BrumeDatabase
import app.beansontoast.brume.data.Journal
import app.beansontoast.brume.playback.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BrumeApp : Application() {
    val graph by lazy { AppGraph(this) }
}

/** The whole object graph, by hand (pastille's way): small enough that DI would cost more than it saves. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database = BrumeDatabase.open(app)
    val journal = Journal(database.journal())
    val settings = Settings(app)
    val player = Player(app, journal, scope)
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _nightHours = MutableStateFlow(prefs.getBoolean(NIGHT_HOURS, true))
    val nightHours: StateFlow<Boolean> = _nightHours

    fun setNightHours(on: Boolean) {
        prefs.edit().putBoolean(NIGHT_HOURS, on).apply()
        _nightHours.value = on
    }

    private companion object { const val NIGHT_HOURS = "night_hours" }
}
