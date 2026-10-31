package app.beansontoast.brume.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.learn.Quiz
import app.beansontoast.brume.core.learn.QuizItem
import app.beansontoast.brume.core.learn.Verdict
import app.beansontoast.design.rememberReducedMotion
import kotlinx.coroutines.launch

/*
 * « Vrai ou idée reçue ? » (Iris, 2026-10-31, from Erwann: « comme un quiz, mais différentes
 * cartes »). Five cards to swipe; each takes a guess (or « Voir la réponse ») and turns over to the
 * verdict as a glyph and a word, the card's evidence mark and a way to the full card. Never graded:
 * a guess that missed reads « Surprenant ? Beaucoup pensent l'inverse. », nothing is stored, and
 * the last page only counts what matched, softly. Reduced motion swaps the sides without turning.
 */

@SuppressLint("DiscouragedApi")
@Composable
private fun quizText(item: QuizItem, part: String): String {
    val context = LocalContext.current
    val id = context.resources.getIdentifier("quiz_${item.id}_$part", "string", context.packageName)
    return if (id == 0) "" else stringResource(id)
}

@Composable
fun QuizDeck(
    onCard: (String) -> Unit = {},
    still: Boolean? = null,
    startPage: Int = 0,
    seededGuesses: Map<Int, Verdict> = emptyMap(),
    seededFlipped: Set<Int> = emptySet(),
) {
    var round by rememberSaveable { mutableIntStateOf(0) }
    val deck = remember(round) { Quiz.deck(round) }
    val guesses = remember(round) { mutableStateMapOf<Int, Verdict>().apply { if (round == 0) putAll(seededGuesses) } }
    val flipped = remember(round) { mutableStateMapOf<Int, Boolean>().apply { if (round == 0) seededFlipped.forEach { put(it, true) } } }
    val reduced = still ?: rememberReducedMotion()
    val pager = rememberPagerState(initialPage = startPage) { deck.size + 1 }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.quiz_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.quiz_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = 28.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(284.dp),
        ) { page ->
            if (page < deck.size) {
                QuizCard(
                    item = deck[page], position = page + 1, of = deck.size, reduced = reduced,
                    guess = guesses[page], flipped = flipped[page] == true,
                    onGuess = { guesses[page] = it; flipped[page] = true },
                    onShow = { flipped[page] = true },
                    onCard = onCard,
                )
            } else {
                val asThought = deck.indices.count { guesses[it] != null && guesses[it] == deck[it].verdict }
                Frame {
                    Text(stringResource(R.string.quiz_done_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    if (guesses.isNotEmpty()) {
                        Text(stringResource(R.string.quiz_done, asThought, guesses.size), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { round++; scope.launch { pager.scrollToPage(0) } }) { Text(stringResource(R.string.quiz_again)) }
                }
            }
        }
    }
}

/** A card's paper: outlined, quiet, the same on both sides. */
@Composable
private fun Frame(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(16.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun QuizCard(
    item: QuizItem, position: Int, of: Int, reduced: Boolean,
    guess: Verdict?, flipped: Boolean,
    onGuess: (Verdict) -> Unit, onShow: () -> Unit, onCard: (String) -> Unit,
) {
    val turn by animateFloatAsState(if (flipped) 180f else 0f, if (reduced) snap() else tween(480), label = "flip")
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxSize().graphicsLayer { rotationY = turn; cameraDistance = 14f * density }) {
        if (turn <= 90f) Frame {
            Text(stringResource(R.string.quiz_position, position, of), style = MaterialTheme.typography.labelMedium, color = quiet)
            Text(quizText(item, "q"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onGuess(Verdict.TRUE) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.quiz_true)) }
                OutlinedButton(onClick = { onGuess(Verdict.MYTH) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.quiz_myth)) }
            }
            TextButton(onClick = onShow, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.quiz_flip), color = quiet)
            }
        } else Frame(Modifier.graphicsLayer { rotationY = 180f }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                VerdictGlyph(item.verdict)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if (item.verdict == Verdict.TRUE) R.string.quiz_true else R.string.quiz_myth),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            }
            if (guess != null) {
                Text(stringResource(if (guess == item.verdict) R.string.quiz_as_thought else R.string.quiz_surprise),
                    style = MaterialTheme.typography.labelLarge, color = quiet, modifier = Modifier.padding(top = 6.dp))
            }
            Text(quizText(item, "a"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
            Learn.card(item.card)?.evidence?.let { EvidenceMark(it, Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onCard(item.card) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.quiz_read) + "  ›")
            }
        }
    }
}

/** A tick for true, a struck circle for a myth (the evidence mark's own « idée reçue » shape). */
@Composable
private fun VerdictGlyph(verdict: Verdict) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(22.dp)) {
        val w = 2.dp.toPx(); val r = size.minDimension / 2 - w; val c = center
        when (verdict) {
            Verdict.TRUE -> {
                drawLine(ink, Offset(size.width * 0.15f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.8f), w, StrokeCap.Round)
                drawLine(ink, Offset(size.width * 0.4f, size.height * 0.8f), Offset(size.width * 0.88f, size.height * 0.2f), w, StrokeCap.Round)
            }
            Verdict.MYTH -> {
                drawCircle(ink, r, c, style = Stroke(w))
                drawLine(ink, Offset(c.x - r * 0.7f, c.y + r * 0.7f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), w, StrokeCap.Round)
            }
        }
    }
}
