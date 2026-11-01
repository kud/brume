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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Back to where the page came from: the arrow and a word, 48 dp. */
@Composable
internal fun BackRow(label: String, onBack: () -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_back), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The topics of the library, as rows: the head of the Learn tab. */
@Composable
fun LearnTopics(onTopic: (Int) -> Unit) {
    val context = LocalContext.current
    Column {
        (1..Learn.TOPICS).forEachIndexed { i, topic ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(topicName(context, topic), trailing = stringResource(R.string.learn_count, Learn.topic(topic).size)) { onTopic(topic) }
        }
    }
}

/** One topic's cards, each a row with its evidence under the title. */
@Composable
fun LearnTopicScreen(topic: Int, onBack: () -> Unit, onCard: (String) -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        BackRow(stringResource(R.string.tab_learn), onBack)
        Text(topicName(context, topic), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Learn.topic(topic).forEachIndexed { i, card ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onCard(card.id) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cardTitle(card), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    card.evidence?.let { EvidenceMark(it, Modifier.padding(top = 4.dp)) }
                }
                Text("  ›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One card: topic, title, the text, what to try, the evidence and the sources. [onNext] walks the
 * reading order.
 */
@Composable
fun LearnCardScreen(card: LearnCard, backLabel: String, onBack: () -> Unit, onNext: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        BackRow(backLabel, onBack)
        Text(
            topicName(context, card.topic),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )
        Text(cardTitle(card), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
        card.evidence?.let { EvidenceMark(it, Modifier.padding(top = 10.dp)) }
        Text(learnText(context, card, "body").orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
        // The cards that send you somewhere name the places of the country you live in.
        val needs = Regions.forCard(card.id)
        if (needs.isNotEmpty()) Card(Modifier.padding(top = 16.dp)) {
            RegionLines(needs)
            RegionFor(onChange = null, modifier = Modifier.padding(top = 10.dp))
        }
        learnText(context, card, "try")?.let { tip ->
            Card(Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.learn_try), style = MaterialTheme.typography.titleSmall)
                Text(tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text(stringResource(R.string.learn_sources, card.sources), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        if (onNext != null) {
            val next = Learn.cards.getOrNull(Learn.cards.indexOf(card) + 1)
            if (next != null) {
                HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(stringResource(R.string.learn_next, cardTitle(next))) { onNext(next.id) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Tones' own card, opened by its quiet « How it works »: what the practice is, how a session goes,
 * and that the evidence is thin, as a shape and a word like every card.
 */
@Composable
fun TonesHowScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        BackRow(stringResource(R.string.tab_tones), onBack)
        Text(stringResource(R.string.tab_tones), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.tones_how), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
        EvidenceMark(Evidence.LITTLE, Modifier.padding(top = 10.dp))
        Text(stringResource(R.string.tones_note), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.ux_tones_session), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        Text(stringResource(R.string.tones_evidence_note), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(24.dp))
    }
}
