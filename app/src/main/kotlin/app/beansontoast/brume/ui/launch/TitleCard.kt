package app.beansontoast.brume.ui.launch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.ui.Land
import app.beansontoast.brume.ui.Landscape

/*
 * The title card (Iris, 2026-11-01, phase 2, the board's first screen): Brume and three words over
 * the whole landscape, held a moment after the splash, then faded into home by opacity alone on
 * the slow curve (docs/motion.md). Nothing lands on the haze; the landscape it shows is home's own,
 * so the fade only takes the words away.
 */
@Composable
fun TitleCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(scheme.background)) {
        Landscape(Land.HOME)
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 150.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // The 108 canvas holds a 60 disc: 168 dp shows it at about 93 dp. Decoration; the name follows.
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(168.dp).offset(y = 24.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.intro_card_words), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
