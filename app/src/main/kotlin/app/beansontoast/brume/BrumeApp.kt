package app.beansontoast.brume

import app.beansontoast.brume.playback.watchOutputs
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
    val player = Player(app, journal, scope).also { p -> watchOutputs(app, p::onOutput) }
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

    private val _modules = MutableStateFlow(Modules(
        tones = prefs.getBoolean(MODULE_TONES, true),
        breath = prefs.getBoolean(MODULE_BREATH, true),
        // New with Calm: on for everyone, including those who had turned Breathe off (they keep that).
        meditation = prefs.getBoolean(MODULE_MEDITATION, true),
    ))
    /** Which modules are on. Listen is always on; it is not a module. */
    val modules: StateFlow<Modules> = _modules

    /** When a module switch last changed, epoch ms; 0 if never. The later change wins in sync. */
    val modulesAt: Long get() = prefs.getLong(MODULES_AT, 0L)

    fun setModules(modules: Modules) = applyModules(modules, System.currentTimeMillis())

    /** Sets the switches with the time they were chosen, here or on another phone. */
    fun applyModules(modules: Modules, at: Long) {
        prefs.edit().putBoolean(MODULE_TONES, modules.tones).putBoolean(MODULE_BREATH, modules.breath).putBoolean(MODULE_MEDITATION, modules.meditation).putLong(MODULES_AT, at).apply()
        _modules.value = modules
    }

    /** The last breathing choices, this phone only: how to breathe is not worth a sync conflict. */
    var breathPattern: String?
        get() = prefs.getString(BREATH_PATTERN, null)
        set(v) = prefs.edit().putString(BREATH_PATTERN, v).apply()
    var breathMinutes: Int
        get() = prefs.getInt(BREATH_MINUTES, 5)
        set(v) = prefs.edit().putInt(BREATH_MINUTES, v).apply()
    var breathHaptics: Boolean
        get() = prefs.getBoolean(BREATH_HAPTICS, true)
        set(v) = prefs.edit().putBoolean(BREATH_HAPTICS, v).apply()
    var breathSound: Boolean
        get() = prefs.getBoolean(BREATH_SOUND, false)
        set(v) = prefs.edit().putBoolean(BREATH_SOUND, v).apply()

    /** Which practice Calm opens on, and the last meditation choices: this phone only, like Breathe's. */
    var calmPractice: String?
        get() = prefs.getString(CALM_PRACTICE, null)
        set(v) = prefs.edit().putString(CALM_PRACTICE, v).apply()
    var meditationChoice: String?
        get() = prefs.getString(MEDITATION_CHOICE, null)
        set(v) = prefs.edit().putString(MEDITATION_CHOICE, v).apply()
    var meditationMinutes: Int
        get() = prefs.getInt(MEDITATION_MINUTES, 10)
        set(v) = prefs.edit().putInt(MEDITATION_MINUTES, v).apply()
    var meditationHaptics: Boolean
        get() = prefs.getBoolean(MEDITATION_HAPTICS, true)
        set(v) = prefs.edit().putBoolean(MEDITATION_HAPTICS, v).apply()
    var meditationSound: Boolean
        get() = prefs.getBoolean(MEDITATION_SOUND, false)
        set(v) = prefs.edit().putBoolean(MEDITATION_SOUND, v).apply()

    internal companion object {
        const val MODULE_TONES = "module_tones"
        const val MODULE_BREATH = "module_breath"
        const val MODULE_MEDITATION = "module_meditation"
        const val CALM_PRACTICE = "calm_practice"
        const val MEDITATION_CHOICE = "meditation_choice"
        const val MEDITATION_MINUTES = "meditation_minutes"
        const val MEDITATION_HAPTICS = "meditation_haptics"
        const val MEDITATION_SOUND = "meditation_sound"
        const val MODULES_AT = "modules_at"
        const val BREATH_PATTERN = "breath_pattern"
        const val BREATH_MINUTES = "breath_minutes"
        const val BREATH_HAPTICS = "breath_haptics"
        const val BREATH_SOUND = "breath_sound"
        const val NIGHT_HOURS = "night_hours"
        const val NIGHT_HOURS_AT = "night_hours_at"
        const val INTRO_SEEN = "intro_seen"
    }
}

/**
 * The switchable modules (ADR 0003). Each is a pillar the person can turn off; the home and the
 * tabs show only what is on. Defaults are on: existing users already have Tones.
 */
data class Modules(val tones: Boolean = true, val breath: Boolean = true, val meditation: Boolean = true) {
    /** Calm holds Breathe and Meditation: its tab shows while either is on. */
    val calm: Boolean get() = breath || meditation

    fun toWire(): Map<String, Boolean> = mapOf(TONES to tones, BREATH to breath, MEDITATION to meditation)

    companion object {
        const val TONES = "TONES"
        const val BREATH = "BREATH"
        const val MEDITATION = "MEDITATION"
        /** A name missing from the file (an older version wrote it) stays on: an older file never switches anything off. */
        fun fromWire(map: Map<String, Boolean>) =
            Modules(tones = map[TONES] ?: true, breath = map[BREATH] ?: true, meditation = map[MEDITATION] ?: true)
    }
}
