package app.beansontoast.brume.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import app.beansontoast.design.Motion
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.learn.Card as LearnCard
import app.beansontoast.brume.core.learn.Evidence
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.region.Regions

/*
 * Learn (Iris, 2026-10-31; Erwann chose direction B the same day): short cards, each with its
 * evidence as a shape plus a word. The library opens the Learn tab, one topic per row (Erwann,
 * 2026-10-31: « Comprendre à la place d'À propos »); a Journal tag or Tones' « How it works » leads in. Words live in string resources, keyed by card id.
 */

/** A card's words by id. getIdentifier, so `:core` can hold the catalogue; LearnStringsTest pins every key. */
@SuppressLint("DiscouragedApi")
internal fun learnText(context: Context, card: LearnCard, part: String): String? {
    val id = context.resources.getIdentifier("learn_${card.id}_$part", "string", context.packageName)
    return if (id == 0) null else context.getString(id)
}

@SuppressLint("DiscouragedApi")
internal fun topicName(context: Context, topic: Int): String =
    context.getString(context.resources.getIdentifier("learn_topic_$topic", "string", context.packageName))

@Composable internal fun cardTitle(card: LearnCard) = learnText(LocalContext.current, card, "title").orEmpty()

internal fun Evidence.word() = when (this) {
    Evidence.STRONG -> R.string.evidence_strong
    Evidence.MIXED -> R.string.evidence_mixed
    Evidence.LITTLE -> R.string.evidence_little
    Evidence.MYTH -> R.string.evidence_myth
}

/** Filled, half, hollow, struck through: the level reads in grey scale and in words, never by hue. */
@Composable
fun EvidenceMark(evidence: Evidence, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val word = stringResource(evidence.word()).lowercase()
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            val r = size.minDimension / 2 - 1.dp.toPx(); val c = center; val w = 1.5.dp.toPx()
            when (evidence) {
                Evidence.STRONG -> drawCircle(ink, r, c)
                Evidence.MIXED -> { drawCircle(ink, r, c, style = Stroke(w)); drawArc(ink, 90f, 180f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r)) }
                Evidence.LITTLE -> drawCircle(ink, r, c, style = Stroke(w))
                Evidence.MYTH -> { drawCircle(ink, r, c, style = Stroke(w)); drawLine(ink, Offset(c.x - r * 0.7f, c.y + r * 0.7f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), w) }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.learn_evidence, word), style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

/** A row that opens something: the words and a chevron carry it, 56 dp, announced as a button. */
@Composable
internal fun NavRow(title: String, summary: String? = null, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  ›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The library as tiles (Iris, 2026-11-01, garden second pass): the seven topics and « Mon
 * acouphène », two to a row, each a framed tile with its name and its size in words.
 * The whole set fits one frame, so nothing scrolls.
 */
@Composable
fun LibraryTiles(onTopic: (Int) -> Unit, onFinder: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tiles: List<Triple<String, String, () -> Unit>> = (1..Learn.TOPICS).map { topic ->
        Triple(topicName(context, topic), stringResource(R.string.learn_count, Learn.topic(topic).size)) { onTopic(topic) }
    } + Triple(stringResource(R.string.finder_title), stringResource(R.string.learn_finder_tile), onFinder)
    // At large text, four tiles a page with ‹ 1 / 2 ›, so every name keeps all its words (Iris, 2026-11-01).
    val pages = tiles.chunked(if (largeText()) 4 else tiles.size)
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(modifier) {
        HorizontalPager(pager, pageSpacing = 12.dp, verticalAlignment = Alignment.Top,
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = Motion.tween(Motion.CALM)),
            modifier = Modifier.fillMaxWidth().weight(1f)) { p ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pages[p].chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (name, size, open) -> Tile(name, size, open, Modifier.weight(1f).fillMaxSize()) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (pages.size > 1) PageStepper(pager.currentPage, pages.size, { scope.launch { pager.animateScrollToPage(it, animationSpec = Motion.tween(Motion.CALM)) } },
            Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun Tile(name: String, size: String, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium,
            maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(size, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * One topic's cards as a deck: framed tiles that settle one at a time on the calm curve, the
 * card's words in full, and « Read › » for what to try and the sources. ‹ 2 / 6 › says where you are.
 */
@Composable
fun LearnTopicScreen(topic: Int, onBack: () -> Unit, onCard: (String) -> Unit) {
    val context = LocalContext.current
    val cards = Learn.topic(topic)
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    SettleTick(pager)
    Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = 28.dp),
            pageSpacing = 12.dp,
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = Motion.tween(Motion.CALM)),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
        ) { page -> TopicCard(cards[page], onCard) }
        PageStepper(pager.currentPage, cards.size, { scope.launch { pager.animateScrollToPage(it, animationSpec = Motion.tween(Motion.CALM)) } },
            Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
    }
}

@Composable
private fun TopicCard(card: LearnCard, onCard: (String) -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape).padding(20.dp),
    ) {
        Text(cardTitle(card), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() })
        card.evidence?.let { EvidenceMark(it, Modifier.padding(top = 8.dp)) }
        Text(learnText(context, card, "body").orEmpty(), style = MaterialTheme.typography.bodyLarge, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp).weight(1f))
        androidx.compose.material3.TextButton(onClick = { onCard(card.id) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.advice_read) + "  ›")
        }
    }
}

/**
 * One card in one frame: topic, title, evidence, the text, then what to try and where to turn as
 * rows that open quiet sheets, the sources, and the next card. [onNext] walks the reading order.
 */
@Composable
fun LearnCardScreen(card: LearnCard, backLabel: String, onBack: () -> Unit, onNext: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<String?>(null) }
    val tip = learnText(context, card, "try")
    val needs = Regions.forCard(card.id)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
            Text(cardTitle(card), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
            card.evidence?.let { EvidenceMark(it, Modifier.padding(top = 10.dp)) }
            Text(learnText(context, card, "body").orEmpty(), style = MaterialTheme.typography.bodyLarge, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp).weight(1f, fill = false))
            Spacer(Modifier.weight(0.001f))
            Text(stringResource(R.string.learn_sources, card.sources), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            if (tip != null) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); NavRow(stringResource(R.string.learn_try)) { sheet = "try" } }
            if (needs.isNotEmpty()) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); NavRow(stringResource(R.string.learn_where)) { sheet = "where" } }
            val next = if (onNext != null) Learn.cards.getOrNull(Learn.cards.indexOf(card) + 1) else null
            if (next != null && onNext != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(stringResource(R.string.learn_next, cardTitle(next))) { onNext(next.id) }
            }
        }
        QuietSheet(sheet == "try", { sheet = null }, stringResource(R.string.learn_try)) {
            Text(tip.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
        QuietSheet(sheet == "where", { sheet = null }, stringResource(R.string.learn_where)) {
            RegionLines(needs)
            RegionFor(onChange = null, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/**
 * Tones' own card, opened by its quiet « How it works »: what the practice is, how a session goes,
 * and that the evidence is thin, as a shape and a word like every card.
 */
@Composable
fun TonesHowScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
        EvidenceMark(Evidence.LITTLE, Modifier.padding(top = 4.dp))
        Text(stringResource(R.string.tones_note), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.ux_tones_session), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        Text(stringResource(R.string.tones_evidence_note), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(24.dp))
    }
}
