package app.beansontoast.brume.teach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Direction B, "Un guide": the first tab becomes "Ce soir" (or "Aujourd'hui" by day). It tells
 * you one thing to try and why, carries one Learn card a day, and keeps effortful things for the
 * morning. Tones becomes a short path, the Journal asks one question at a time, and Learn reads
 * as a path of short steps reached from the home; the full library stays in About.
 */

@Composable
fun BTonight() = Screen {
    Title("Ce soir", "Mardi, 22 h 40")
    Spacer(Modifier.height(16.dp))
    Panel {
        Eyebrow("Pour la nuit")
        Spacer(Modifier.height(6.dp))
        Text("Pluie douce, au haut-parleur, 45 minutes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Body("Un son bas dans la chambre évite le silence complet, où l'acouphène prend toute la place. La nuit, le haut-parleur plutôt que des écouteurs.", quiet = true)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Disc(64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Lancer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Ça démarre doucement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Divider()
        ReadLink("Choisir un autre son")
    }
    Spacer(Modifier.height(14.dp))
    Panel(low = true) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Une chose à savoir")
                Spacer(Modifier.height(6.dp))
                Text("Le café : faut-il arrêter ?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Body("Un essai en double aveugle n'a vu aucune différence entre les jours avec et sans caféine.", quiet = true)
            }
            Spacer(Modifier.width(10.dp))
            CupPicture()
        }
        Spacer(Modifier.height(8.dp))
        EvidenceMark(Evidence.MYTH)
        ReadLink("Lire la fiche", 1)
    }
    Spacer(Modifier.height(14.dp))
    Panel(low = true) {
        Eyebrow("Demain matin")
        Spacer(Modifier.height(6.dp))
        Body("Trouver la hauteur de votre acouphène. Mieux au calme, de jour : on vous le proposera ici.", quiet = true)
    }
}

@Composable
fun BTones() = Screen(dock = { Dock("Étape 3 : le volume. Montez jusqu'à l'entendre à peine.", "Séance", "20 min") }) {
    Title("Tons", "Une pratique en séances, en quatre étapes")
    Spacer(Modifier.height(14.dp))
    Panel(low = true) { TonesPicture() }
    Spacer(Modifier.height(10.dp))
    Panel {
        StepRow(1, "Comprendre la pratique", "Quatre tons autour de votre hauteur · lu", Step.DONE)
        Divider()
        StepRow(2, "Trouver votre hauteur", "Environ 6,2 kHz, le 28 oct.", Step.DONE)
        Divider()
        StepRow(3, "Le bon volume", "Juste audible, jamais plus fort", Step.NOW)
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Plus bas", outlined = true); Chip("Je l'entends à peine", outlined = false);
        }
        Divider()
        StepRow(4, "Une séance", "20 minutes, sans autre son", Step.LATER)
    }
    Spacer(Modifier.height(10.dp))
    EvidenceMark(Evidence.WEAK)
    ReadLink("Ce qu'on en sait", 2)
}

@Composable
fun BJournal() = Screen {
    Title("Journal", "Ce soir · question 1 sur 3")
    Spacer(Modifier.height(28.dp))
    Text("Quelle place a pris le son aujourd'hui ?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(20.dp))
    Text("5 sur 10", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(12.dp))
    Marks(5, big = true)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("à peine là", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("toute la place", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(20.dp))
    Box(Modifier.fillMaxWidth()) {
        Text("Passer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp))
        Box(Modifier.align(Alignment.CenterEnd)) { Chip("Suivant  ›") }
    }
    Spacer(Modifier.height(24.dp))
    Panel(low = true) {
        Eyebrow("Hier soir, vous avez noté « café »")
        Spacer(Modifier.height(6.dp))
        Body("Envie de savoir ce que disent les études ? Le tag reste là pour remarquer, à votre rythme.", quiet = true)
        ReadLink("Le café : faut-il arrêter ?", 1)
    }
}

/** Learn as a path of short steps, reached from the home's daily card. */
@Composable
fun BLearn() = Screen {
    Text("‹ Ce soir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
    Title("Protéger ses oreilles", "Étape 3 sur 10 · 1 min")
    Spacer(Modifier.height(8.dp))
    Marks(3)
    Spacer(Modifier.height(16.dp))
    Text("Le temps compte autant que le volume", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Panel(low = true) { DecibelPicture() }
    Spacer(Modifier.height(12.dp))
    Body("Chaque +3 dB divise par deux le temps sûr. 89 dB, ce n'est pas « un peu plus » que 80 : la semaine tient en 5 heures.")
    Spacer(Modifier.height(10.dp))
    EvidenceMark(Evidence.STRONG)
    Spacer(Modifier.height(14.dp))
    Panel {
        Eyebrow("Demain")
        Spacer(Modifier.height(4.dp))
        Text("Les bouchons d'oreilles : mousse, silicone, moulés", style = MaterialTheme.typography.titleSmall)
    }
    Spacer(Modifier.height(10.dp))
    ReadLink("Toutes les fiches, dans À propos")
}
