package app.beansontoast.brume.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Every function Brume has, in the order Erwann named them (2026-11-01). Each opens its own
 * screen from home; [SETTINGS] is reached by the quiet icon, not a row.
 */
enum class Fn(@StringRes val title: Int, @StringRes val line: Int) {
    SLEEP(R.string.scene_sleep, R.string.fn_sleep_line),
    SETTLE(R.string.scene_settle, R.string.fn_settle_line),
    BREATHE(R.string.scene_breathe, R.string.fn_breathe_line),
    MEDITATE(R.string.fn_meditate, R.string.fn_meditate_line),
    TONES(R.string.scene_tones, R.string.fn_tones_line),
    EAR(R.string.finder_title, R.string.fn_ear_line),
    JOURNAL(R.string.tab_journal, R.string.fn_journal_line),
    LEARN(R.string.tab_learn, R.string.fn_learn_line),
    SETTINGS(R.string.settings_title, R.string.settings_title),
}

/** The rows home shows: every function the modules (Settings › Activities) leave on. */
fun Modules.fns(): List<Fn> = Fn.entries.filter {
    when (it) { Fn.BREATHE -> breath; Fn.MEDITATE -> meditation; Fn.TONES -> tones; Fn.SETTINGS -> false; else -> true }
}

/** Whether [fn] can be open at all with these modules: Settings always can. */
fun Modules.allows(fn: Fn) = fn == Fn.SETTINGS || fn in fns()

/**
 * Home (Iris, 2026-11-01): the name and a line over the sky, then one light list of every
 * function (a small symbol, its name, what it is for, ›), the sound playing under it. Fits
 * 360x780 without a scroll: rows are 60 dp, two lines each, in one surface with hairlines rather
 * than eight cards.
 */
@Composable
fun HomeScreen(fns: List<Fn>, onOpen: (Fn) -> Unit, mini: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Landscape(Land.HOME)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(start = 8.dp, top = 12.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.home_moment), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
                val settings = stringResource(R.string.settings_title)
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = settings) { onOpen(Fn.SETTINGS) }
                    .semantics { contentDescription = settings }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_tune), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            val shape = RoundedCornerShape(20.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(scheme.surfaceContainerLowest.copy(alpha = 0.8f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.8f), shape)) {
                fns.forEachIndexed { i, fn ->
                    if (i > 0) Box(Modifier.padding(start = 64.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.7f)))
                    FnRow(fn) { onOpen(fn) }
                }
            }
            if (mini != null) Box(Modifier.padding(top = 8.dp)) { mini() }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FnRow(fn: Fn, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FnSymbol(fn, Modifier.size(38.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(stringResource(fn.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(fn.line), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant)
    }
}

/**
 * Each function's small symbol: a soft round of its family's colour (blue for sound, lavender for
 * calm, peach for knowing) with a line drawing that differs by shape alone, so they read apart
 * in greyscale. Placeholders for Sacha's illustrated symbols, same 38 dp place.
 */
@Composable
fun FnSymbol(fn: Fn, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ground = when (fn) {
        Fn.SLEEP, Fn.SETTLE, Fn.TONES -> scheme.primaryContainer
        Fn.BREATHE, Fn.MEDITATE -> scheme.secondaryContainer
        else -> scheme.tertiaryContainer
    }
    val ink = scheme.onSurface.copy(alpha = 0.85f)
    Canvas(modifier) {
        drawCircle(ground)
        glyph(fn, ink)
    }
}

private fun DrawScope.glyph(fn: Fn, ink: Color) {
    val w = size.width; val c = center; val s = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
    fun wave(y: Float, from: Float, to: Float, amp: Float) {
        val p = Path(); var x = from; p.moveTo(x, y)
        while (x < to) { x += 1f; p.lineTo(x, y + amp * sin((x - from) / (to - from) * 2 * PI).toFloat()) }
        drawPath(p, ink, style = s)
    }
    when (fn) {
        Fn.SLEEP -> {
            val moon = Path().apply { addOval(Rect(c, w * 0.24f)) }
            val bite = Path().apply { addOval(Rect(Offset(c.x + w * 0.12f, c.y - w * 0.08f), w * 0.21f)) }
            drawPath(Path.combine(PathOperation.Difference, moon, bite), ink)
        }
        Fn.SETTLE -> { wave(c.y - w * 0.08f, w * 0.26f, w * 0.74f, w * 0.05f); wave(c.y + w * 0.1f, w * 0.3f, w * 0.7f, w * 0.04f) }
        Fn.BREATHE -> { drawCircle(ink, w * 0.24f, style = s); drawCircle(ink.copy(alpha = 0.45f), w * 0.12f) }
        Fn.MEDITATE -> {
            drawOval(ink, Offset(c.x - w * 0.2f, c.y + w * 0.06f), Size(w * 0.4f, w * 0.15f), style = s)
            drawOval(ink, Offset(c.x - w * 0.14f, c.y - w * 0.1f), Size(w * 0.28f, w * 0.13f), style = s)
            drawOval(ink, Offset(c.x - w * 0.08f, c.y - w * 0.23f), Size(w * 0.16f, w * 0.1f), style = s)
        }
        Fn.TONES -> listOf(-0.15f to 0.14f, 0f to 0.24f, 0.15f to 0.18f).forEach { (dx, h) ->
            drawLine(ink, Offset(c.x + w * dx, c.y - w * h), Offset(c.x + w * dx, c.y + w * h), s.width, StrokeCap.Round)
        }
        Fn.EAR -> {
            drawCircle(ink, w * 0.05f, Offset(c.x - w * 0.16f, c.y))
            for (r in listOf(0.16f, 0.29f)) drawArc(ink, -50f, 100f, false, Offset(c.x - w * 0.16f - w * r, c.y - w * r), Size(2 * w * r, 2 * w * r), style = s)
        }
        Fn.JOURNAL -> {
            drawRoundRect(ink, Offset(c.x - w * 0.18f, c.y - w * 0.23f), Size(w * 0.36f, w * 0.46f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f), style = s)
            for (i in 0..2) drawLine(ink, Offset(c.x - w * 0.1f, c.y - w * 0.1f + i * w * 0.1f), Offset(c.x + w * 0.1f, c.y - w * 0.1f + i * w * 0.1f), s.width, StrokeCap.Round)
        }
        Fn.LEARN, Fn.SETTINGS -> {
            val p = Path().apply {
                moveTo(c.x, c.y - w * 0.14f); lineTo(c.x - w * 0.24f, c.y - w * 0.2f); lineTo(c.x - w * 0.24f, c.y + w * 0.16f); lineTo(c.x, c.y + w * 0.22f)
                lineTo(c.x + w * 0.24f, c.y + w * 0.16f); lineTo(c.x + w * 0.24f, c.y - w * 0.2f); close()
                moveTo(c.x, c.y - w * 0.14f); lineTo(c.x, c.y + w * 0.22f)
            }
            drawPath(p, ink, style = s)
        }
    }
}
