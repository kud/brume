package app.beansontoast.brume.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.learn.Decibels
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.learn.Measure
import app.beansontoast.brume.core.learn.SafeTime
import app.beansontoast.brume.core.learn.Strain
import app.beansontoast.design.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The decibel dial (Iris, 2026-10-31, from Erwann: « tu règles avec une molette… tu vois une oreille
 * souffrir ou non… et combien de temps tu peux tenir »). The ear leads, the time before harm is the
 * readout, the everyday sound sits under them. Every number comes from `:core` Decibels. Nothing
 * rests on colour: the ear's state is a word, the hourglass is an amount, the rocket's measure a
 * shape and a word. Reduced motion keeps every state and drops only the pulse.
 */

/**
 * The art slot for an everyday sound: `res/drawable-nodpi/db_<id>_night.png` on dark themes (pale
 * line with its glow, transparent) and `db_<id>_day.png` on light ones (dark line, transparent),
 * 384x384. Sacha's final files replace these by name; nothing else changes.
 */
@SuppressLint("DiscouragedApi")
@Composable
fun ScaleArt(id: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val res = context.resources.getIdentifier("db_${id}_${if (dark) "night" else "day"}", "drawable", context.packageName)
    if (res != 0) Image(painterResource(res), contentDescription = null, modifier = modifier)
}

internal fun Strain.word() = when (this) {
    Strain.REST -> R.string.db_strain_rest
    Strain.EASY -> R.string.db_strain_easy
    Strain.TIRING -> R.string.db_strain_tiring
    Strain.FAST -> R.string.db_strain_fast
    Strain.OVERWHELMED -> R.string.db_strain_overwhelmed
    Strain.TOO_LOUD -> R.string.db_strain_too_loud
}

@Composable
internal fun safeTimeText(db: Int): String = when (val t = Decibels.safeTime(db)) {
    SafeTime.NoLimit -> stringResource(R.string.db_time_none)
    SafeTime.MoreThanEightHours -> stringResource(R.string.db_time_more8)
    SafeTime.Never -> stringResource(R.string.db_time_never)
    is SafeTime.Daily -> {
        val r = Decibels.rounded(t.seconds)
        when {
            r.underASecond -> stringResource(R.string.db_time_instant)
            r.seconds > 0 -> stringResource(R.string.db_time_s, r.seconds)
            r.hours > 0 && r.minutes > 0 -> stringResource(R.string.db_time_hm, r.hours, r.minutes)
            r.hours > 0 -> stringResource(R.string.db_time_h, r.hours)
            else -> stringResource(R.string.db_time_m, r.minutes)
        }
    }
}

/** Two significant figures, whole numbers: 8, 45, 180. */
private fun factor(x: Double): String {
    if (x < 10) return x.roundToInt().toString()
    val p = 10.0.pow(log10(x).toInt() - 1)
    return ((x / p).roundToInt() * p).roundToInt().toString()
}

@Composable
private fun loudnessText(db: Int): String {
    val x = Decibels.loudness(db)
    return when {
        x >= 1.5 -> stringResource(R.string.db_louder, factor(x))
        x <= 1 / 1.5 -> stringResource(R.string.db_quieter, factor(1 / x))
        else -> stringResource(R.string.db_same)
    }
}

/**
 * The explorer: the ear and its word, the time as an hourglass and in words, the everyday sound,
 * then the dial. [initial] and [still] let the renders pin a level and the motion.
 */
@Composable
fun DecibelExplorer(initial: Int = 60, still: Boolean? = null, onCard: (String) -> Unit = {}) {
    var db by rememberSaveable { mutableIntStateOf(Decibels.snap(initial.toFloat())) }
    val reduced = still ?: rememberReducedMotion()
    val shown by animateFloatAsState(db.toFloat(), if (reduced) snap() else tween(320), label = "db")
    val phase = if (reduced) 0.35f else {
        val pulse = rememberInfiniteTransition(label = "pulse")
        pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val glow = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strain = Decibels.strain(db)
    val sound = Decibels.nearest(db)

    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.db_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.db_intro), style = MaterialTheme.typography.bodyMedium, color = quiet, modifier = Modifier.padding(top = 4.dp))

        // The ear, with the sound coming in on its left and, past 85, a ringing at its back.
        Ear(shown, phase, ink, glow, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).size(width = 240.dp, height = 156.dp))
        Text(
            stringResource(strain.word()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )

        // Time before harm: the key readout.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Hourglass(Decibels.sand(db), shown, phase, reduced, ink, Modifier.size(width = 36.dp, height = 56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.db_at, db), style = MaterialTheme.typography.labelLarge, color = quiet)
                Text(safeTimeText(db), style = MaterialTheme.typography.headlineSmall)
            }
        }

        // The everyday sound at about this level, second to the ear.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp)) { if (sound != null) ScaleArt(sound.id, Modifier.size(56.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (sound == null) Text(stringResource(R.string.db_silence), style = MaterialTheme.typography.bodyLarge)
                else {
                    Text(everydayLabel(sound.id), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.db_typical, sound.db), style = MaterialTheme.typography.bodyMedium, color = quiet)
                        if (sound.measure == Measure.UNWEIGHTED) UnweightedMark(Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            StepButton("−", stringResource(R.string.db_decrease)) { db = (db - Decibels.STEP).coerceAtLeast(Decibels.MIN) }
            Dial(db, onChange = { db = it }, modifier = Modifier.padding(horizontal = 12.dp).size(184.dp), strainWord = stringResource(strain.word()))
            StepButton("+", stringResource(R.string.db_increase)) { db = (db + Decibels.STEP).coerceAtMost(Decibels.MAX) }
        }

        Text(loudnessText(db), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        if (sound?.measure == Measure.UNWEIGHTED) {
            Text(stringResource(R.string.db_unweighted_note), style = MaterialTheme.typography.bodySmall, color = quiet, modifier = Modifier.padding(top = 8.dp))
        }
        Text(stringResource(R.string.db_rule), style = MaterialTheme.typography.bodySmall, color = quiet, modifier = Modifier.padding(top = 10.dp))
        Learn.card("c5_1")?.let { card ->
            HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(cardTitle(card)) { onCard(card.id) }
        }
    }
}

@SuppressLint("DiscouragedApi")
@Composable
private fun everydayLabel(id: String): String {
    val context = LocalContext.current
    val res = context.resources.getIdentifier("db_$id", "string", context.packageName)
    return if (res == 0) id else stringResource(res)
}

/** A hollow diamond and the word: the rocket's level is not on the dBA scale. */
@Composable
private fun UnweightedMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) {
            val c = center; val r = size.minDimension / 2 - 1.dp.toPx()
            drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, ink, style = Stroke(1.4.dp.toPx()))
        }
        Text(stringResource(R.string.db_unweighted), style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun StepButton(glyph: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(glyph, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val START = 135f
private const val SWEEP = 270f

/**
 * The molette: a knob turned by dragging round it (or tapping a point on its ring), 270 degrees for
 * 0 to 140 dB, a detent and a haptic tick every 5 dB. TalkBack reads the level and the ear's word
 * and gets Louder and Quieter.
 */
@Composable
private fun Dial(db: Int, onChange: (Int) -> Unit, strainWord: String, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(db)
    val change by rememberUpdatedState(onChange)
    val ink = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.outlineVariant
    val knob = MaterialTheme.colorScheme.surfaceContainerHigh
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = quiet)
    val dialLabel = stringResource(R.string.db_dial)
    val louder = stringResource(R.string.db_increase)
    val quieter = stringResource(R.string.db_decrease)
    val valueText = stringResource(R.string.db_value, db)

    fun set(next: Int) {
        if (next != current) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); change(next) }
    }

    fun at(p: Offset, w: Float, h: Float, jumpOk: Boolean) {
        val deg = (atan2(p.y - h / 2, p.x - w / 2) * 180 / PI).toFloat()
        var a = ((deg - START) % 360 + 360) % 360
        if (a > SWEEP) a = if (a > SWEEP + (360 - SWEEP) / 2) 0f else SWEEP
        val next = Decibels.snap(a / SWEEP * Decibels.MAX)
        // Dragging across the gap at the bottom would leap from 0 to 140; a drag never jumps that far.
        if (jumpOk || kotlin.math.abs(next - current) <= 30) set(next)
    }

    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { at(it, size.width.toFloat(), size.height.toFloat(), jumpOk = true) }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { at(it, size.width.toFloat(), size.height.toFloat(), jumpOk = true) }) { c, _ ->
                    c.consume(); at(c.position, size.width.toFloat(), size.height.toFloat(), jumpOk = false)
                }
            }
            .clearAndSetSemantics {
                contentDescription = dialLabel
                stateDescription = "$valueText, $strainWord"
                progressBarRangeInfo = ProgressBarRangeInfo(db.toFloat(), Decibels.MIN.toFloat()..Decibels.MAX.toFloat(), Decibels.MAX / Decibels.STEP - 1)
                setProgress { set(Decibels.snap(it)); true }
                customActions = listOf(
                    CustomAccessibilityAction(louder) { set((current + Decibels.STEP).coerceAtMost(Decibels.MAX)); true },
                    CustomAccessibilityAction(quieter) { set((current - Decibels.STEP).coerceAtLeast(Decibels.MIN)); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val outer = size.minDimension / 2
            val ring = outer - 22.dp.toPx()
            fun angle(v: Int) = ((START + SWEEP * v / Decibels.MAX) * PI / 180).toFloat()
            fun on(r: Float, a: Float) = Offset(c.x + r * cos(a), c.y + r * sin(a))
            // Detents: a short tick every 5 dB, longer every 20, and 85 (NIOSH's 8-hour mark) the longest.
            for (v in Decibels.MIN..Decibels.MAX step Decibels.STEP) {
                val a = angle(v)
                val long = v == Decibels.REL || v % 20 == 0
                val passed = v <= db
                drawLine(
                    if (passed) ink else track, on(ring + 6.dp.toPx(), a), on(ring + (if (v == Decibels.REL) 18 else if (long) 13 else 10).dp.toPx(), a),
                    (if (v == Decibels.REL) 2.4f else if (passed) 1.8f else 1.2f).dp.toPx(), StrokeCap.Round,
                )
            }
            listOf(0, Decibels.REL, Decibels.MAX).forEach { v ->
                val layout = measurer.measure(v.toString(), labelStyle)
                val p = on(outer - 2.dp.toPx() + if (v == Decibels.REL) 0f else -4.dp.toPx(), angle(v))
                val q = on(ring - 2.dp.toPx(), angle(v))
                val spot = if (v == Decibels.REL) Offset(p.x, p.y - 6.dp.toPx()) else Offset((p.x + q.x) / 2, q.y + 14.dp.toPx())
                drawText(layout, topLeft = Offset(spot.x - layout.size.width / 2, spot.y - layout.size.height / 2))
            }
            // The knob, its rim and the notch that points at the level.
            drawCircle(knob, ring - 2.dp.toPx(), c)
            drawCircle(track, ring - 2.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            drawArc(ink, START, SWEEP * db / Decibels.MAX, false, Offset(c.x - ring, c.y - ring), Size(2 * ring, 2 * ring), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            val a = angle(db)
            drawLine(ink, on(ring - 22.dp.toPx(), a), on(ring - 8.dp.toPx(), a), 3.dp.toPx(), StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(db.toString(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp), fontWeight = FontWeight.Light)
            Text("dB", style = MaterialTheme.typography.labelLarge, color = quiet)
        }
    }
}

/**
 * The hourglass: the top holds what is left of a day's allowance, emptied by the same amount every
 * 3 dB (each one halves the time), so the amount reads without colour. The stream runs from 70 dB
 * and quickens with the level; reduced motion draws it still.
 */
@Composable
private fun Hourglass(sand: Float, db: Float, phase: Float, reduced: Boolean, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val neck = h / 2; val m = 3.dp.toPx(); val stroke = 1.6.dp.toPx()
        drawLine(ink, Offset(0f, m / 2), Offset(w, m / 2), stroke * 1.4f, StrokeCap.Round)
        drawLine(ink, Offset(0f, h - m / 2), Offset(w, h - m / 2), stroke * 1.4f, StrokeCap.Round)
        val glass = Path().apply {
            moveTo(m, m); lineTo(w - m, m); lineTo(w / 2 + 2.dp.toPx(), neck); lineTo(w - m, h - m); lineTo(m, h - m); lineTo(w / 2 - 2.dp.toPx(), neck); close()
        }
        drawPath(glass, ink, style = Stroke(stroke))
        val sandInk = ink.copy(alpha = 0.55f)
        // Top: a triangle of sand down to the neck, its height by what remains.
        val top = (neck - m) * sand
        if (top > 0.5f) {
            val half = (w / 2 - m) * sand
            drawPath(Path().apply { moveTo(w / 2 - half, neck - top); lineTo(w / 2 + half, neck - top); lineTo(w / 2, neck); close() }, sandInk)
        }
        // Bottom: a mound for what has run.
        val bottom = (h - m - neck) * (1 - sand) * 0.9f
        if (bottom > 0.5f) {
            val half = (w / 2 - m) * (0.4f + 0.6f * (1 - sand))
            drawPath(Path().apply { moveTo(w / 2 - half, h - m); lineTo(w / 2 + half, h - m); lineTo(w / 2, h - m - bottom); close() }, sandInk)
        }
        if (db > Decibels.QUIET && sand > 0f) {
            val from = neck; val to = h - m - bottom
            if (reduced) drawLine(sandInk, Offset(w / 2, from), Offset(w / 2, to), 1.2.dp.toPx())
            else {
                val speed = 1f + (db - Decibels.QUIET) / 10f
                val gap = 5.dp.toPx()
                val shift = (phase * speed * 4 % 1f) * gap
                var y = from + shift
                while (y < to) { drawCircle(sandInk, 0.9.dp.toPx(), Offset(w / 2, y)); y += gap }
            }
        }
    }
}

/**
 * A stylised ear in the board's luminous line, drawn in a 100x130 box. Tension rises from 75 dB:
 * the inner lines draw in and thicken, the glow tightens and brightens. Sound arrives from the left
 * as arcs, one per 10 dB past 40 (each one about twice as loud). From 85 a faint ringing pulses at
 * its back; from 100 short strain marks sit above it. Never a wound, never a colour.
 */
@Composable
private fun Ear(db: Float, phase: Float, ink: Color, glow: Boolean, modifier: Modifier = Modifier) {
    val t = Decibels.tension(db)
    Canvas(modifier) {
        val unit = size.height / 130f
        val left = size.width / 2 - 55 * unit
        withTransform({ translate(left, 0f); scale(unit, unit, Offset.Zero) }) {
            // Incoming sound.
            val arcs = ((db - 40f) / 10f).coerceIn(0f, 9f)
            for (i in 0 until arcs.toInt().coerceAtLeast(if (db >= 30) 1 else 0)) {
                val r = 18f + i * 9f + phase * 9f
                val fade = (1f - (r - 18f) / 100f).coerceIn(0f, 1f) * (0.25f + 0.5f * t)
                drawArc(ink.copy(alpha = fade), 155f, 50f, false, Offset(40f - r, 70f - r), Size(2 * r, 2 * r), style = Stroke(1.4f, cap = StrokeCap.Round))
            }
            val outer = Path().apply {
                moveTo(40f, 44f)
                cubicTo(40f, 20f, 58f, 6f, 74f, 8f)
                cubicTo(92f, 10f, 100f, 28f, 98f, 46f)
                cubicTo(96f, 64f, 84f, 72f, 78f, 84f)
                cubicTo(72f, 96f, 74f, 110f, 62f, 120f)
                cubicTo(54f, 126f, 42f, 124f, 38f, 114f)
            }
            val pull = 1f - 0.1f * t
            val inner = Path().apply {
                moveTo(54f, 46f)
                cubicTo(54f, 32f, 64f, 24f, 74f, 26f)
                cubicTo(84f, 28f, 88f, 40f, 84f, 50f)
                cubicTo(80f, 58f, 72f, 60f, 70f, 72f)
            }
            val tragus = Path().apply { moveTo(60f, 84f); cubicTo(53f, 82f, 51f, 73f, 57f, 68f) }
            val core = 2.2f + 1.4f * t
            fun line(p: Path, s: Float = 1f) {
                withTransform({ scale(s, s, Offset(72f, 60f)) }) {
                    if (glow) {
                        drawPath(p, ink.copy(alpha = 0.05f + 0.06f * t), style = Stroke(core + 12f * (1f - 0.5f * t), cap = StrokeCap.Round))
                        drawPath(p, ink.copy(alpha = 0.10f + 0.12f * t), style = Stroke(core + 5f * (1f - 0.4f * t), cap = StrokeCap.Round))
                    }
                    drawPath(p, ink, style = Stroke(core, cap = StrokeCap.Round))
                }
            }
            line(outer)
            line(inner, pull)
            line(tragus, pull)
            // Ringing at the back of the ear, from 85.
            if (db >= Decibels.REL) {
                val n = if (db >= 100) 3 else 2
                for (i in 0 until n) {
                    val r = 8f + i * 7f + phase * 7f
                    val a = (1f - (r - 8f) / 30f).coerceIn(0f, 1f) * (0.3f + 0.5f * t)
                    drawArc(ink.copy(alpha = a), -35f, 70f, false, Offset(100f - r, 40f - r), Size(2 * r, 2 * r), style = Stroke(1.3f, cap = StrokeCap.Round))
                }
            }
            // Overwhelmed: three short strain marks over the top of the ear.
            if (db >= 100) {
                val lift = 3f + 2f * sin(phase * 2 * PI.toFloat())
                listOf(-30f, 0f, 30f).forEach { deg ->
                    val a = ((deg - 90f) * PI / 180).toFloat()
                    val cx = 72f; val cy = 30f
                    drawLine(
                        ink.copy(alpha = 0.4f + 0.5f * t),
                        Offset(cx + (30f + lift) * cos(a), cy + (30f + lift) * sin(a)), Offset(cx + (38f + lift) * cos(a), cy + (38f + lift) * sin(a)),
                        1.6f, StrokeCap.Round,
                    )
                }
            }
        }
    }
}
