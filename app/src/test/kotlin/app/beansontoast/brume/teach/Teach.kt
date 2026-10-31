package app.beansontoast.brume.teach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.ui.Mist

/*
 * The teach exploration (Iris, 2026-10-31): mock screens on invented data, rendered through the
 * Renders harness. Not wired into the app. Shared pieces for directions A and B.
 */

/** How well a Learn card's point is supported, as a shape and a word, never a hue. */
enum class Evidence(val word: String) { STRONG("Bien étayé"), MIXED("Mitigé"), WEAK("Peu étayé"), MYTH("Idée reçue") }

@Composable
fun EvidenceMark(evidence: Evidence, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            val r = size.minDimension / 2 - 1.dp.toPx(); val c = center; val w = 1.5.dp.toPx()
            when (evidence) {
                Evidence.STRONG -> drawCircle(ink, r, c)
                Evidence.MIXED -> { drawCircle(ink, r, c, style = Stroke(w)); drawArc(ink, 90f, 180f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r)) }
                Evidence.WEAK -> drawCircle(ink, r, c, style = Stroke(w))
                Evidence.MYTH -> { drawCircle(ink, r, c, style = Stroke(w)); drawLine(ink, Offset(c.x - r * 0.7f, c.y + r * 0.7f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), w) }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("Études : ${evidence.word.lowercase()}", style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

/** The screen: a scrolling body over an optional dock, so the cut at the fold reads as "more below". */
@Composable
fun Screen(dock: (@Composable () -> Unit)? = null, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), content = body)
        if (dock != null) dock()
    }
}

@Composable
fun Title(text: String, sub: String? = null) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
    if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
fun Panel(modifier: Modifier = Modifier, low: Boolean = false, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(if (low) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer)
        .padding(16.dp),
    content = content,
)

/** A quiet link into a Learn card: the arrow and the word "Lire" carry it, not a colour. */
@Composable
fun ReadLink(text: String, minutes: Int? = null, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    if (minutes != null) Text("$minutes min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("  ›", style = MaterialTheme.typography.titleMedium)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, quiet: Boolean = false) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = modifier)

/** The ink Play disc, as on Listen today. */
@Composable
fun Disc(size: Dp = 76.dp) {
    val s = MaterialTheme.colorScheme
    Box(Modifier.size(size).clip(CircleShape).background(s.inverseSurface), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_play), null, tint = s.inverseOnSurface, modifier = Modifier.size(size * 0.48f))
    }
}

/** A pill-ish secondary action with its value written out. */
@Composable
fun Chip(label: String, value: String? = null, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val s = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).then(if (outlined) Modifier.border(1.dp, s.outlineVariant, shape) else Modifier.background(s.surfaceContainerHigh))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (value == null) s.onSurface else s.onSurfaceVariant)
        if (value != null) Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** The dock: mist behind, a written status, the disc and a timer chip. */
@Composable
fun Dock(status: String, chip: String, chipValue: String) {
    Box(Modifier.fillMaxWidth().height(150.dp)) {
        Mist(Modifier.fillMaxSize(), strength = 0.35f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
                Chip(chip, chipValue, Modifier.align(Alignment.CenterStart))
                Box(Modifier.align(Alignment.Center)) { Disc() }
            }
        }
    }
}

/** Ten marks, filled up to the value and hollow after: the Journal's existing scale. */
@Composable
fun Marks(value: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    val ink = MaterialTheme.colorScheme.onSurface; val dim = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(if (big) 40.dp else 14.dp)) {
        val gap = size.width / 10f; val w = gap * 0.62f; val h = size.height
        for (i in 0 until 10) {
            val x = i * gap + (gap - w) / 2
            if (i < value) drawRoundRect(ink, Offset(x, 0f), Size(w, h), CornerRadius(4.dp.toPx()))
            else drawRoundRect(dim, Offset(x, 0f), Size(w, h), CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** A row in a step list: done, now or later, each told by a glyph and a word. */
enum class Step { DONE, NOW, LATER }

@Composable
fun StepRow(n: Int, title: String, detail: String, step: Step, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).then(
                when (step) {
                    Step.DONE -> Modifier.background(s.surfaceContainerHighest)
                    Step.NOW -> Modifier.background(s.inverseSurface)
                    Step.LATER -> Modifier.border(1.5.dp, s.outline, CircleShape)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (step == Step.DONE) "✓" else "$n", style = MaterialTheme.typography.labelLarge,
                color = if (step == Step.NOW) s.inverseOnSurface else s.onSurface, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = if (step == Step.NOW) FontWeight.SemiBold else FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant)
        }
        Text(when (step) { Step.DONE -> "fait"; Step.NOW -> "maintenant"; Step.LATER -> "" },
            style = MaterialTheme.typography.labelMedium, color = s.onSurfaceVariant)
    }
}

// ---- Illustrations: drawn lines, the mist motif, each labelled in words ----

/** The blend: a thin high line (your tinnitus) and a soft band rising to just under it. */
@Composable
fun BlendPicture(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface; val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text("votre acouphène", style = MaterialTheme.typography.labelSmall, color = ink)
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val y = size.height * 0.18f
            drawLine(ink, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
            for (i in 0 until 7) {
                val top = size.height * (0.34f + i * 0.09f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, top)
                    var x = 0f
                    while (x < size.width) { x += 6f; lineTo(x, top + 3.dp.toPx() * kotlin.math.sin((x / size.width) * 9f + i).toFloat()) }
                }
                drawPath(path, soft.copy(alpha = 0.9f - i * 0.1f), style = Stroke(1.3.dp.toPx()))
            }
        }
        Text("le son, juste en dessous", style = MaterialTheme.typography.labelSmall, color = soft)
    }
}

/** Four tones around a pitch, and the rhythm: three on, two off. */
@Composable
fun TonesPicture(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface; val soft = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val mid = size.width / 2; val base = size.height * 0.8f
            drawLine(soft, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            drawLine(ink, Offset(mid, 4f), Offset(mid, base), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val xs = listOf(-0.30f, -0.12f, 0.12f, 0.30f)
            xs.forEachIndexed { i, f -> drawLine(ink, Offset(mid + f * size.width, base), Offset(mid + f * size.width, base - size.height * (0.35f + 0.08f * i)), 3.dp.toPx(), StrokeCap.Round) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("plus grave", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("votre hauteur", style = MaterialTheme.typography.labelSmall)
            Text("plus aigu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(92.dp).height(14.dp)) {
                for (i in 0 until 5) {
                    val c = Offset(7.dp.toPx() + i * 18.dp.toPx(), size.height / 2)
                    if (i < 3) drawCircle(ink, 5.dp.toPx(), c) else drawCircle(soft, 5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                }
            }
            Text("3 temps de sons, 2 de pause", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The decibel ladder with WHO's weekly time beside each step: +3 dB halves the time. */
@Composable
fun DecibelPicture(modifier: Modifier = Modifier) {
    val rows = listOf("80 dB" to "40 h", "83 dB" to "20 h", "86 dB" to "10 h", "89 dB" to "5 h", "concert" to "minutes")
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        rows.forEachIndexed { i, (db, time) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(db, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                Canvas(Modifier.weight(1f).height(10.dp)) {
                    val f = if (i == 4) 0.012f else 1f / (1 shl i)
                    drawRoundRect(ink, Offset.Zero, Size(size.width * f, size.height), CornerRadius(3.dp.toPx()))
                }
                Text(time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(92.dp).padding(start = 10.dp))
            }
        }
        Text("Temps d'écoute sûr par semaine, d'après l'OMS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A small cup, for the coffee myth card. */
@Composable
fun CupPicture(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(44.dp)) {
        val w = 1.6.dp.toPx(); val s = size.width
        drawRoundRect(ink, Offset(s * 0.15f, s * 0.38f), Size(s * 0.5f, s * 0.45f), CornerRadius(6.dp.toPx()), style = Stroke(w))
        drawArc(ink, -90f, 180f, false, Offset(s * 0.55f, s * 0.45f), Size(s * 0.24f, s * 0.24f), style = Stroke(w))
        for (k in 0 until 2) drawLine(ink, Offset(s * (0.3f + k * 0.18f), s * 0.28f), Offset(s * (0.34f + k * 0.18f), s * 0.1f), w, StrokeCap.Round)
    }
}

@Composable
fun Divider() = HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun Tag(text: String, on: Boolean = false) {
    val s = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Text(
        if (on) "✓ $text" else text,
        style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        color = if (on) s.onSecondaryContainer else s.onSurfaceVariant,
        modifier = Modifier.clip(shape).then(if (on) Modifier.background(s.secondaryContainer) else Modifier.border(1.dp, s.outlineVariant, shape))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

