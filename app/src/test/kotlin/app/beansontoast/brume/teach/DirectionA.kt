package app.beansontoast.brume.teach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.ui.texture

/*
 * Direction A, "Expliquer sur place": the five tabs stay. Each screen leads with why it is there,
 * in one sentence and one drawing, with a way into the matching Learn card. The controls fold
 * into one summary line ("Régler") until they're wanted. Learn stays in About, but About now
 * opens on it, and the switches move behind a single "Réglages" row.
 */

/** The lead every A screen opens with: a drawing, the why, and a Learn link with its evidence. */
@Composable
private fun Why(text: String, link: String, evidence: Evidence?, picture: @Composable () -> Unit) = Panel(low = true) {
    picture()
    Spacer(Modifier.height(12.dp))
    Body(text)
    Spacer(Modifier.height(8.dp))
    Divider()
    ReadLink(link, 1)
    if (evidence != null) EvidenceMark(evidence)
}

@Composable
fun AListen() = Screen(dock = { Dock("Prêt. Ça démarre doucement.", "Minuterie", "45 min") }) {
    Title("Écouter")
    Spacer(Modifier.height(14.dp))
    Why(
        "Un son doux, réglé juste en dessous de l'acouphène, pour que les deux se mêlent. Le but n'est pas de le couvrir.",
        "Les sons de fond", Evidence.MIXED,
    ) { BlendPicture() }
    Spacer(Modifier.height(14.dp))
    Panel {
        Eyebrow("Votre mélange")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ink = MaterialTheme.colorScheme.onSurfaceVariant
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) { Canvas(Modifier.size(52.dp)) { texture(NoiseColour.PINK, null, ink) } }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) { Canvas(Modifier.size(52.dp)) { texture(null, Ambience.RAIN, ink) } }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Bruit rose + Pluie", style = MaterialTheme.typography.titleMedium)
                Text("Doux · centré · haut-parleur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Chip("Régler", outlined = true)
        }
    }
}

@Composable
fun ATones() = Screen(dock = { Dock("Prêt. Ça démarre doucement.", "Séance", "20 min") }) {
    Title("Tons")
    Spacer(Modifier.height(14.dp))
    Why(
        "Quatre tons doux autour de la hauteur de votre acouphène, dans un ordre qui change. Une pratique à faire en séances, seule, à peine audible.",
        "Ce qu'on en sait", Evidence.WEAK,
    ) { TonesPicture() }
    Spacer(Modifier.height(14.dp))
    Panel {
        Eyebrow("Avant de commencer")
        StepRow(1, "Votre hauteur", "Environ 6,2 kHz · trouvée le 28 oct.", Step.DONE)
        StepRow(2, "Le volume", "Juste audible, jamais plus fort", Step.NOW)
        StepRow(3, "La séance", "20 minutes, sans autre son", Step.LATER)
        Divider()
        ReadLink("Régler à la main")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AJournal() = Screen {
    Title("Journal")
    Spacer(Modifier.height(14.dp))
    Why(
        "Une note chaque soir, en dix secondes. Au fil des semaines, vous verrez ce qui pèse pour vous, et ce qui ne change rien.",
        "Noter pour remarquer", null,
    ) {
        Column {
            Marks(3); Spacer(Modifier.height(6.dp)); Marks(6); Spacer(Modifier.height(6.dp)); Marks(4)
            Text("trois soirs, côte à côte", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    Panel {
        Eyebrow("Ce soir")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Gêne", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("5 sur 10", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        Marks(5, big = true)
        Spacer(Modifier.height(14.dp))
        Text("Aujourd'hui, il y a eu…", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("café", on = true); Tag("bruit"); Tag("stress", on = true); Tag("nuit courte")
        }
        Text("Un tag n'est pas une cause : il aide à remarquer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
fun AAbout() = Screen {
    Title("À propos")
    Spacer(Modifier.height(14.dp))
    Eyebrow("Comprendre")
    Spacer(Modifier.height(8.dp))
    Panel {
        val chapters = listOf(
            "Ce qu'est l'acouphène" to "2 fiches", "Ce qui aide le plus" to "3 fiches", "Idées reçues et faits" to "10 fiches",
            "Le sommeil" to "2 fiches", "Protéger ses oreilles" to "10 fiches", "Quand consulter" to "3 fiches", "En parler" to "2 fiches",
        )
        chapters.forEachIndexed { i, (name, count) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  ›", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Panel(low = true) {
        ReadLink("Réglages")
        Body("Modules, langue, synchronisation, sauvegarde", quiet = true)
    }
    Spacer(Modifier.height(12.dp))
    Body("Sur ce téléphone uniquement. Rien ne part ailleurs.", quiet = true)
}

/** A Learn card open, the way A reaches it from "Lire" on any screen. */
@Composable
fun ALearnCard() = Screen {
    Text("‹ Protéger ses oreilles", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
    Title("Le temps compte autant que le volume")
    Spacer(Modifier.height(14.dp))
    Panel(low = true) { DecibelPicture() }
    Spacer(Modifier.height(14.dp))
    Body("L'OMS fixe un repère par semaine : 80 dB pendant 40 heures. Chaque +3 dB divise ce temps par deux. Au niveau d'un concert, la dose de la semaine part en quelques minutes.")
    Spacer(Modifier.height(14.dp))
    Panel {
        Eyebrow("À essayer")
        Spacer(Modifier.height(6.dp))
        Body("Votre temps d'écoute de la semaine est dans Journal.")
    }
    Spacer(Modifier.height(12.dp))
    EvidenceMark(Evidence.STRONG)
    Text("Source : OMS–UIT H.870", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}
