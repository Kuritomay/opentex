package com.kuritomay.opentex.ui.study

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kuritomay.opentex.data.FlashcardEntity
import com.kuritomay.opentex.data.FlashcardPort
import com.kuritomay.opentex.data.FlashcardWithDeck
import com.kuritomay.opentex.data.ReviewScheduler
import com.kuritomay.opentex.ui.library.SectionTitle
import com.kuritomay.opentex.ui.theme.OpenTexColor
import com.kuritomay.opentex.ui.theme.OpenTexSpacing
import com.kuritomay.opentex.ui.theme.OpenTexTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FlashcardsScreen(
    cards: List<FlashcardWithDeck>,
    modifier: Modifier = Modifier,
    rate: (Long, String) -> Unit,
    openSource: (Long, Int) -> Unit,
    editCard: (FlashcardWithDeck) -> Unit = {},
) {
    if (cards.isEmpty()) {
        FlashcardsEmpty(modifier)
        return
    }
    val now = remember(cards) { System.currentTimeMillis() }
    val dueCards = remember(cards, now) { cards.filter { ReviewScheduler.isDue(it.card, now) }.sortedBy { it.card.dueAt } }
    val newCount = remember(cards) { cards.count { it.card.reviewCount == 0 } }
    val byId = remember(cards) { cards.associateBy { it.card.id } }
    val decks = remember(cards) { cards.groupBy { it.deck.deckName }.map { (name, group) -> name to group.size }.sortedByDescending { it.second } }
    val nextDueAt = remember(cards, now) { cards.filter { it.card.dueAt > now }.minOfOrNull { it.card.dueAt } }

    var phase by rememberSaveable { mutableStateOf("pick") }
    var sessionIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableIntStateOf(-1) }
    var reviewed by rememberSaveable { mutableIntStateOf(0) }

    val start: (Boolean) -> Unit = { onlyDue ->
        sessionIds = (if (onlyDue) dueCards else cards.sortedBy { it.card.dueAt }).map { it.card.id }
        index = 0
        revealed = false
        chosen = -1
        reviewed = 0
        phase = "review"
    }

    when (phase) {
        "review" -> {
            val entry = sessionIds.getOrNull(index)?.let { byId[it] }
            if (entry == null) {
                LaunchedEffect(index, sessionIds.size) {
                    if (index >= sessionIds.size) phase = "done" else index += 1
                }
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val card = entry.card
                val isReversed = card.type == "reversed" && index % 2 == 1
                val question = if (isReversed) card.backText else card.frontText
                val answer = if (isReversed) card.frontText else card.backText
                val questionLatex = if (isReversed) card.backLatex else card.frontLatex
                val answerLatex = if (isReversed) card.frontLatex else card.backLatex
                val options = remember(card.id) { FlashcardPort.options(card) }
                val correct = remember(card.id) { FlashcardPort.correct(card) }
                val isChoice = card.type == "multiple_choice" && options.isNotEmpty()
                Column(modifier.fillMaxSize().padding(horizontal = OpenTexSpacing.Lg)) {
                    Row(Modifier.fillMaxWidth().padding(top = OpenTexSpacing.Xl), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.deck.deckName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${ReviewScheduler.dueLabel(card, now)} · ${card.reviewCount} repasos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${index + 1} / ${sessionIds.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { editCard(entry) }) { Icon(Icons.Outlined.Edit, "Editar flashcard") }
                    }
                    Spacer(Modifier.height(OpenTexSpacing.Md))
                    LinearProgressIndicator(
                        progress = { (index + 1f) / sessionIds.size },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.height(OpenTexSpacing.Xxl))
                    Text("Pregunta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(OpenTexSpacing.Md))
                    if (card.type != "latex" && question.isNotBlank()) Text(question, style = MaterialTheme.typography.headlineSmall)
                    if (!questionLatex.isNullOrBlank()) {
                        Spacer(Modifier.height(OpenTexSpacing.Md))
                        LatexBlock(questionLatex)
                    }
                    if (card.frontImage != null) {
                        Spacer(Modifier.height(OpenTexSpacing.Md))
                        CardImage(card.frontImage, Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.small))
                    }
                    Spacer(Modifier.height(OpenTexSpacing.Xl))
                    if (!revealed) {
                        if (isChoice) {
                            Text("Elige la respuesta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(OpenTexSpacing.Sm))
                            options.forEachIndexed { optionIndex, option ->
                                OptionRow(option, null, Modifier.fillMaxWidth().padding(bottom = OpenTexSpacing.Sm)) { chosen = optionIndex; revealed = true }
                            }
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                            TextButton({ revealed = true }, Modifier.fillMaxWidth().padding(bottom = OpenTexSpacing.Xl)) { Icon(Icons.Outlined.Visibility, null); Spacer(Modifier.width(OpenTexSpacing.Sm)); Text("Ver respuesta") }
                        }
                    } else {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        Spacer(Modifier.height(OpenTexSpacing.Md))
                        Column(Modifier.weight(1f)) {
                            Text("Respuesta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(OpenTexSpacing.Sm))
                            if (card.type != "latex" && answer.isNotBlank()) Text(answer, style = MaterialTheme.typography.bodyLarge)
                            if (!answerLatex.isNullOrBlank()) {
                                Spacer(Modifier.height(OpenTexSpacing.Md))
                                LatexBlock(answerLatex)
                            }
                            if (isChoice) {
                                Spacer(Modifier.height(OpenTexSpacing.Md))
                                options.forEachIndexed { optionIndex, option ->
                                    val state = when {
                                        optionIndex in correct -> OpenTexColor.Success
                                        optionIndex == chosen -> OpenTexColor.Error
                                        else -> null
                                    }
                                    OptionRow(option, state, Modifier.fillMaxWidth().padding(bottom = OpenTexSpacing.Sm)) {}
                                }
                            }
                            if (!card.backExplanation.isNullOrBlank()) {
                                Spacer(Modifier.height(OpenTexSpacing.Md))
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                    Text(card.backExplanation, Modifier.padding(OpenTexSpacing.Lg), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (card.documentId != null && card.pageNumber != null) {
                                val documentId = card.documentId
                                val pageNumber = card.pageNumber
                                Spacer(Modifier.height(OpenTexSpacing.Md))
                                TextButton(onClick = { openSource(documentId, pageNumber) }) { Icon(Icons.Outlined.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(OpenTexSpacing.Sm)); Text("Ver en fuente · pág. ${pageNumber + 1}", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        Spacer(Modifier.height(OpenTexSpacing.Md))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Sm)) {
                            val advance = {
                                revealed = false
                                chosen = -1
                                reviewed += 1
                                index += 1
                                if (index >= sessionIds.size) phase = "done"
                            }
                            ReviewButton("Otra vez", OpenTexColor.Error.copy(alpha = .2f), OpenTexColor.Error, Modifier.weight(1f)) { rate(card.id, "again"); advance() }
                            ReviewButton("Difícil", OpenTexColor.Warning.copy(alpha = .2f), OpenTexColor.Warning, Modifier.weight(1f)) { rate(card.id, "hard"); advance() }
                            ReviewButton("Bien", OpenTexColor.Success.copy(alpha = .2f), OpenTexColor.Success, Modifier.weight(1f)) { rate(card.id, "good"); advance() }
                            ReviewButton("Fácil", OpenTexColor.Primary.copy(alpha = .2f), OpenTexColor.Primary, Modifier.weight(1f)) { rate(card.id, "easy"); advance() }
                        }
                        Spacer(Modifier.height(OpenTexSpacing.Sm))
                        Text(scheduleHints(card, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(OpenTexSpacing.Xl))
                }
            }
        }
        "done" -> ReviewDone(reviewed, sessionIds.size, modifier) { phase = "pick" }
        else -> ReviewPicker(cards.size, dueCards.size, newCount, decks, nextDueLabel(nextDueAt, now), modifier, { start(true) }, { start(false) })
    }
}

@Composable
private fun ReviewPicker(total: Int, due: Int, fresh: Int, decks: List<Pair<String, Int>>, nextLabel: String, modifier: Modifier, reviewDue: () -> Unit, reviewAll: () -> Unit) {
    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = OpenTexSpacing.Lg)) {
        Spacer(Modifier.height(OpenTexSpacing.Xl))
        Text("Repaso", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(OpenTexSpacing.Xs))
        Text("$total tarjetas · $due vencidas · $fresh nuevas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(OpenTexSpacing.Xl))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(OpenTexSpacing.Xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$due", style = MaterialTheme.typography.displaySmall, color = if (due > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (due > 0) "vencidas ahora" else "todo al día", style = MaterialTheme.typography.titleMedium)
                if (nextLabel.isNotBlank()) {
                    Spacer(Modifier.height(OpenTexSpacing.Sm))
                    Text("Próxima revisión $nextLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(OpenTexSpacing.Lg))
        Button(onClick = reviewDue, enabled = due > 0, modifier = Modifier.fillMaxWidth()) { Text("Repasar vencidas ($due)") }
        Spacer(Modifier.height(OpenTexSpacing.Sm))
        OutlinedButton(onClick = reviewAll, modifier = Modifier.fillMaxWidth()) { Text("Repasar todo el mazo") }
        Spacer(Modifier.height(OpenTexSpacing.Xl))
        SectionTitle("Mazos", "${decks.size} en total", Modifier.padding(bottom = OpenTexSpacing.Sm))
        decks.forEach { (name, count) ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(bottom = OpenTexSpacing.Sm)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = OpenTexSpacing.Lg, vertical = OpenTexSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Style, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(OpenTexSpacing.Sm))
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(OpenTexSpacing.Xl))
    }
}

@Composable
private fun ReviewDone(reviewed: Int, total: Int, modifier: Modifier, back: () -> Unit) = Box(modifier.fillMaxSize().padding(OpenTexSpacing.Xxxl), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(44.dp), tint = OpenTexColor.Success)
        Spacer(Modifier.height(OpenTexSpacing.Md))
        Text("Sesión completada", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(OpenTexSpacing.Sm))
        Text("Repasaste $reviewed de $total tarjetas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(OpenTexSpacing.Xl))
        Button(onClick = back) { Text("Volver al repaso") }
    }
}

private fun scheduleHints(card: FlashcardEntity, now: Long): String = listOf("again" to "Otra vez", "hard" to "Difícil", "good" to "Bien", "easy" to "Fácil")
    .joinToString("  ·  ") { (rating, label) -> "$label ${ReviewScheduler.nextLabel(card, rating, now)}" }

private fun nextDueLabel(nextDueAt: Long?, now: Long): String {
    if (nextDueAt == null) return ""
    val day = 86_400_000L
    val days = ((nextDueAt - now + day - 1) / day).toInt().coerceAtLeast(1)
    return if (days == 1) "mañana" else "en $days días"
}

@Composable
private fun LatexBlock(text: String) = Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
    Text(text, Modifier.padding(OpenTexSpacing.Lg), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
}

@Composable
private fun OptionRow(label: String, state: Color?, modifier: Modifier, click: () -> Unit) {
    Surface(color = state?.copy(alpha = .16f) ?: MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(OpenTexSpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (state != null) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = state)
        }
    }
}

@Composable
private fun FlashcardsEmpty(modifier: Modifier) = Box(modifier.fillMaxSize().padding(OpenTexSpacing.Xxxl), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Style, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(OpenTexSpacing.Lg))
        Text("Sin flashcards", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(OpenTexSpacing.Sm))
        Text("Abre un documento, captura una región y elige «Crear flashcard desde esta captura».", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CardImage(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } }
    val loaded = bitmap
    if (loaded != null) Image(loaded.asImageBitmap(), contentDescription = null, modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ReviewButton(label: String, color: Color, contentColor: Color, modifier: Modifier, click: () -> Unit) {
    TextButton(onClick = click, modifier = modifier.height(44.dp), shape = MaterialTheme.shapes.small, colors = ButtonDefaults.textButtonColors(containerColor = color, contentColor = contentColor)) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1116, widthDp = 390, heightDp = 840)
@Composable
private fun FlashcardsDarkPreview() = OpenTexTheme {
    FlashcardsScreen(emptyList(), rate = { _, _ -> }, openSource = { _, _ -> })
}
