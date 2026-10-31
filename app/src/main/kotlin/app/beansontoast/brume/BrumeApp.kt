package app.beansontoast.brume

import android.app.Application
import android.content.Context
import app.beansontoast.brume.data.BrumeDatabase
import app.beansontoast.brume.data.Journal
import app.beansontoast.brume.playback.Player
import app.beansontoast.brume.sync.Sync
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
    val sync = Sync(app, database, journal, settings, player, scope).also { it.start() }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _nightHours = MutableStateFlow(prefs.getBoolean(NIGHT_HOURS, true))
    val nightHours: StateFlow<Boolean> = _nightHours

    fun setNightHours(on: Boolean) = applyNightHours(on, System.currentTimeMillis())

    /** When night hours last changed, epoch ms; 0 if never. The later change wins in sync. */
    val nightHoursAt: Long get() = prefs.getLong(NIGHT_HOURS_AT, 0L)

    /** Sets night hours with the time it was chosen, here or on another phone. */
    fun applyNightHours(on: Boolean, at: Long) {
        prefs.edit().putBoolean(NIGHT_HOURS, on).putLong(NIGHT_HOURS_AT, at).apply()
        _nightHours.value = on
    }

    /** Whether the introduction has been seen, finished or skipped. Read once at launch; About can replay it. */
    val introSeen: Boolean get() = prefs.getBoolean(INTRO_SEEN, false)

    fun setIntroSeen() = prefs.edit().putBoolean(INTRO_SEEN, true).apply()

    private companion object {
        const val NIGHT_HOURS = "night_hours"
        const val NIGHT_HOURS_AT = "night_hours_at"
        const val INTRO_SEEN = "intro_seen"
    }
}
