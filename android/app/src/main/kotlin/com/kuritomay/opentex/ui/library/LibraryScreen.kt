package com.kuritomay.opentex.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kuritomay.opentex.data.DocumentEntity
import com.kuritomay.opentex.ui.theme.OpenTexColor
import com.kuritomay.opentex.ui.theme.OpenTexSpacing
import com.kuritomay.opentex.ui.theme.OpenTexTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class LibraryFilter(val label: String) {
    All("Todos"), Books("Libros"), Notes("Apuntes"), Articles("Artículos"), Other("Otros")
}

@Composable
fun LibraryScreen(
    documents: List<DocumentEntity>,
    tabs: List<DocumentEntity>,
    modifier: Modifier = Modifier,
    open: (DocumentEntity) -> Unit,
    closeTab: (Long) -> Unit,
    onDocumentMenu: (DocumentEntity) -> Unit,
    onImport: () -> Unit,
    query: String = "",
) {
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    val filteredDocuments = remember(documents, selectedFilter, query) {
        documents.filter {
            (selectedFilter == LibraryFilter.All || documentKind(it) == selectedFilter) &&
                it.name.contains(query, ignoreCase = true)
        }
    }

    Column(modifier.fillMaxSize()) {
        if (tabs.isNotEmpty()) OpenTabs(tabs, open, closeTab)
        if (documents.isEmpty()) {
            LibraryEmptyState(Modifier.weight(1f), onImport)
        } else {
            LibraryFilterRow(selectedFilter, { selectedFilter = it })
            SectionTitle(
                title = if (selectedFilter == LibraryFilter.All) "Tu biblioteca" else selectedFilter.label,
                supporting = "${filteredDocuments.size} documentos",
                modifier = Modifier.padding(
                    start = OpenTexSpacing.Lg,
                    end = OpenTexSpacing.Lg,
                    top = OpenTexSpacing.Xl,
                ),
            )
            if (filteredDocuments.isEmpty()) {
                FilterEmptyState(selectedFilter.label, Modifier.weight(1f))
            } else {
                DocumentGrid(filteredDocuments, Modifier.weight(1f), open, onDocumentMenu)
            }
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier.fillMaxWidth().padding(horizontal = OpenTexSpacing.Lg, vertical = OpenTexSpacing.Sm),
            placeholder = { Text("Buscar en tu biblioteca") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cerrar búsqueda") } },
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun SectionTitle(title: String, supporting: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MetadataText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LibraryFilterRow(selected: LibraryFilter, select: (LibraryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = OpenTexSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Sm),
    ) {
        items(LibraryFilter.entries.size, key = { LibraryFilter.entries[it].name }) { index ->
            val filter = LibraryFilter.entries[index]
            FilterChip(
                selected = selected == filter,
                onClick = { select(filter) },
                label = { Text(filter.label) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

@Composable
private fun OpenTabs(tabs: List<DocumentEntity>, open: (DocumentEntity) -> Unit, close: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = OpenTexSpacing.Lg, vertical = OpenTexSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Sm),
    ) {
        items(tabs.take(3), key = { it.id }) { tab ->
            androidx.compose.material3.AssistChip(
                onClick = { open(tab) },
                label = { Text(tab.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    IconButton(onClick = { close(tab.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, "Cerrar ${tab.name}", modifier = Modifier.size(16.dp))
                    }
                },
            )
        }
    }
}

@Composable
fun DocumentGrid(
    documents: List<DocumentEntity>,
    modifier: Modifier,
    open: (DocumentEntity) -> Unit,
    addToAlbum: (DocumentEntity) -> Unit = {},
) = BoxWithConstraints(modifier) {
    val columns = when {
        maxWidth >= 840.dp -> 6
        maxWidth >= 600.dp -> 4
        maxWidth >= 420.dp -> 3
        else -> 2
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(OpenTexSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(OpenTexSpacing.Xxl),
    ) {
        val pinned = documents.filter { it.isPinned }
        val remaining = documents.filterNot { it.isPinned }
        if (pinned.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Fijados", style = MaterialTheme.typography.titleMedium) }
        items(pinned, key = { it.id }, contentType = { "document" }) { document ->
            DocumentGridItem(document, open, addToAlbum)
        }
        if (pinned.isNotEmpty() && remaining.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Todos los documentos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = OpenTexSpacing.Sm)) }
        items(remaining, key = { it.id }, contentType = { "document" }) { document -> DocumentGridItem(document, open, addToAlbum) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridItem(document: DocumentEntity, open: (DocumentEntity) -> Unit, addToAlbum: (DocumentEntity) -> Unit) {
    Column(
        Modifier.combinedClickable(onClick = { open(document) }, onLongClick = { addToAlbum(document) })
            .semantics { contentDescription = "Abrir ${document.name}. Mantén pulsado para añadir a un álbum." },
    ) {
        DocumentCover(document, Modifier.fillMaxWidth())
        Spacer(Modifier.height(OpenTexSpacing.Sm))
        Text((document.alias ?: document.name).substringBeforeLast("."), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(OpenTexSpacing.Xs))
        LinearProgressIndicator(
            progress = { document.progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        MetadataText("${(document.progress * 100).roundToInt()}%  ·  ${documentKind(document).label}", Modifier.padding(top = OpenTexSpacing.Xs))
    }
}

@Composable
fun DocumentCover(document: DocumentEntity, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, document.thumbnailPath) {
        value = withContext(Dispatchers.IO) { document.thumbnailPath?.let(BitmapFactory::decodeFile) }
    }
    Box(
        modifier.aspectRatio(2f / 3f).shadow(4.dp, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)
            .background(coverColor(document.id)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(OpenTexSpacing.Md)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(OpenTexSpacing.Sm))
                Text((document.alias ?: document.name).substringBeforeLast(".").take(28), style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(OpenTexSpacing.Sm))
                Text(document.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(modifier: Modifier, onImport: () -> Unit) {
    Box(modifier.fillMaxSize().padding(OpenTexSpacing.Xxxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(OpenTexSpacing.Lg))
            Text("Tu biblioteca está vacía", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(OpenTexSpacing.Sm))
            Text("Añade un documento para comenzar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(OpenTexSpacing.Xl))
            androidx.compose.material3.FilledTonalButton(onClick = onImport) { Text("Abrir documento") }
        }
    }
}

@Composable
private fun FilterEmptyState(filter: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(OpenTexSpacing.Xxxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(OpenTexSpacing.Md))
            Text("No hay $filter", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(OpenTexSpacing.Xs))
            Text("Prueba con otro filtro.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun documentKind(document: DocumentEntity): LibraryFilter {
    val name = document.name.lowercase()
    return when {
        name.contains("apunte") || name.contains("nota") || name.contains("clase") -> LibraryFilter.Notes
        name.contains("artículo") || name.contains("articulo") || name.contains("article") || name.contains("paper") -> LibraryFilter.Articles
        document.type in setOf("PDF", "EPUB", "DOC", "DOCX") -> LibraryFilter.Books
        else -> LibraryFilter.Other
    }
}

private fun coverColor(id: Long): Color = when ((id % 4).toInt()) {
    0 -> OpenTexColor.SurfaceElevated
    1 -> Color(0xFF1D2A3A)
    2 -> Color(0xFF243038)
    else -> Color(0xFF29243A)
}

private val sampleDocuments = listOf(
    DocumentEntity(1, "", "Física Universitaria.pdf", "PDF", 472, progress = .43f),
    DocumentEntity(2, "", "Apuntes de Cálculo.pdf", "PDF", 320, progress = .16f),
    DocumentEntity(3, "", "Biología Celular.pdf", "PDF", 284, progress = .71f),
    DocumentEntity(4, "", "Química General.pdf", "PDF", 384, progress = .08f),
)

@Preview(showBackground = true, backgroundColor = 0xFF0E1116, widthDp = 390, heightDp = 840)
@Composable
private fun LibraryDarkPreview() = OpenTexTheme { LibraryScreen(sampleDocuments, emptyList(), open = {}, closeTab = {}, onDocumentMenu = {}, onImport = {}) }

@Preview(showBackground = true, backgroundColor = 0xFF0E1116, widthDp = 840, heightDp = 600)
@Composable
private fun TabletLibraryPreview() = OpenTexTheme { LibraryScreen(sampleDocuments, emptyList(), open = {}, closeTab = {}, onDocumentMenu = {}, onImport = {}) }
