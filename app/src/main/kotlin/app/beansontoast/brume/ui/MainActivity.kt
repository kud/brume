package app.beansontoast.brume.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.beansontoast.brume.AppGraph
import app.beansontoast.brume.BrumeApp
import app.beansontoast.brume.BuildConfig
import app.beansontoast.brume.R
import app.beansontoast.brume.core.NightHours
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.design.GammeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as BrumeApp).graph
        setContent { BrumeRoot(graph) }
    }
}

@Composable
private fun BrumeRoot(graph: AppGraph) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nightHours by graph.settings.nightHours.collectAsStateWithLifecycle()
    val night = nightHours && NightHours.contains(LocalTime.now().hour)
    // At night Brume always opens on Listen: Listen is the first tab, and nothing else asks for effort.
    var tab by rememberSaveable { mutableStateOf(Tab.LISTEN) }
    val player by graph.player.ui.collectAsStateWithLifecycle()
    val checkIns by graph.journal.checkIns.collectAsStateWithLifecycle(emptyList())
    val weekSeconds by remember { graph.journal.weekSeconds() }.collectAsStateWithLifecycle(0L)
    var journalMessage by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = graph.journal.exportJson(BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            journalMessage = context.getString(if (ok) R.string.journal_exported else R.string.journal_export_failed)
        }
    }

    GammeTheme(darkTheme = night || isSystemInDarkTheme(), trueBlack = true, dim = night) {
        BrumeScaffold(tab = tab, onTab = { tab = it }) {
            when (tab) {
                Tab.LISTEN -> ListenScreen(player, ListenActions(
                    noise = graph.player::selectNoise,
                    ambience = graph.player::selectAmbience,
                    noiseLevel = graph.player::setNoiseLevel,
                    ambienceLevel = graph.player::setAmbienceLevel,
                    timer = graph.player::setTimer,
                    toggle = graph.player::toggle,
                    tooLoud = graph.player::tooLoud,
                    mode = graph.player::selectMode,
                    tones = ToneActions(
                        pitch = graph.player::setPitch,
                        level = graph.player::setToneLevel,
                        trim = graph.player::setTrim,
                        match = graph.player::setMatch,
                    ),
                ))
                Tab.JOURNAL -> JournalScreen(
                    checkIns = checkIns,
                    weekSeconds = weekSeconds,
                    today = LocalDate.now(),
                    message = journalMessage,
                    onSave = { loudness, bother, note ->
                        scope.launch {
                            graph.journal.save(CheckIn.of(LocalDate.now(), loudness, bother, note))
                            journalMessage = context.getString(R.string.journal_saved)
                        }
                    },
                    onExport = { export.launch("brume-export-${LocalDate.now()}.json") },
                )
                Tab.ABOUT -> AboutScreen(nightHours = nightHours, onNightHours = graph.settings::setNightHours, version = BuildConfig.VERSION_NAME)
            }
        }
    }
}
