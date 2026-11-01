package app.beansontoast.brume.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.learn.Advice
import app.beansontoast.brume.core.learn.AdviceItem
import app.beansontoast.design.Motion
import app.beansontoast.design.tabular

/*
 * Advice cards (Iris, 2026-11-01), in place of « Vrai ou idée reçue ? »: Erwann found the quiz
 * « prétentieux » and « anxiogène ». Each card is one gentle, practical piece of advice, the quiz's
 * verified facts said plainly; nothing to guess, nothing to turn over, no score. The deck is a pager
 * of fixed-height cards that settle one at a time on the calm curve, so nothing below ever moves.
 */

@SuppressLint("DiscouragedApi")
@Composable
private fun adviceText(item: AdviceItem, part: String): String {
    val context = LocalContext.current
    val id = context.resources.getIdentifier("advice_${item.id}_$part", "string", context.packageName)
    return if (id == 0) "" else stringResource(id)
}

@Composable
fun AdviceDeck(onCard: (String) -> Unit = {}, startPage: Int = 0, titled: Boolean = true, deck: Modifier = Modifier.height(344.dp)) {
    val cards = Advice.cards
    val pager = rememberPagerState(initialPage = startPage) { cards.size }
    Column(Modifier.fillMaxWidth()) {
        if (titled) {
            Text(stringResource(R.string.advice_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.advice_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = 28.dp),
            pageSpacing = 12.dp,
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = Motion.tween(Motion.CALM)),
            modifier = Modifier.fillMaxWidth().padding(top = if (titled) 12.dp else 0.dp).then(deck),
        ) { page -> AdviceCard(cards[page], page + 1, cards.size, onCard) }
    }
}

@Composable
private fun AdviceCard(item: AdviceItem, position: Int, of: Int, onCard: (String) -> Unit) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.advice_position, position, of), style = MaterialTheme.typography.labelMedium.tabular(), color = quiet)
        Text(adviceText(item, "title"), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp).semantics { heading() })
        Text(adviceText(item, "body"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).weight(1f))
        TextButton(onClick = { onCard(item.card) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.advice_read) + "  ›")
        }
    }
}
