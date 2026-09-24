package com.kuritomay.opentex.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuritomay.opentex.data.AlbumWithDocuments
import com.kuritomay.opentex.data.DocumentEntity
import com.kuritomay.opentex.ui.library.DocumentGrid
import com.kuritomay.opentex.ui.library.SectionTitle
import com.kuritomay.opentex.ui.theme.OpenTexSpacing
import com.kuritomay.opentex.ui.theme.OpenTexTheme
import kotlinx.coroutines.flow.Flow

@Composable
fun AlbumsScreen(albums: List<AlbumWithDocuments>, modifier: Modifier, open: (AlbumWithDocuments) -> Unit, create: () -> Unit) {
    if (albums.isEmpty()) {
        EmptyAlbums(modifier, create)
        return
    }
    Column(modifier.fillMaxSize()) {
        SectionTitle("Colecciones", "${albums.size} álbumes", Modifier.padding(horizontal = OpenTexSpacing.Lg, vertical = OpenTexSpacing.Xl))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(164.dp),
            contentPadding = PaddingValues(start = OpenTexSpacing.Lg, end = OpenTexSpacing.Lg, bottom = OpenTexSpacing.Xxl),
            horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OpenTexSpacing.Lg),
        ) {
            items(albums, key = { it.id }, contentType = { "album" }) { AlbumCard(it, open) }
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumWithDocuments, open: (AlbumWithDocuments) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { open(album) }) {
        Box(Modifier.fillMaxWidth().height(132.dp).clip(MaterialTheme.shapes.medium).background(albumTint(album.id))) {
            StackedCovers(album.id, Modifier.align(Alignment.Center))
            Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.align(Alignment.TopEnd).padding(OpenTexSpacing.Md).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(OpenTexSpacing.Md))
        Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text("${album.documentCount} documentos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StackedCovers(id: Long, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 112.dp, height = 88.dp)) {
        repeat(3) { index ->
            Box(
                Modifier.width(52.dp).height(76.dp).offset(x = (index * 18).dp, y = ((2 - index) * 4).dp)
                    .clip(RoundedCornerShape(6.dp)).background(coverTint(id + index)),
                contentAlignment = Alignment.Center,
            ) { if (index == 2) Icon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetail(album: AlbumWithDocuments, close: () -> Unit, documents: (Long) -> Flow<List<DocumentEntity>>, open: (DocumentEntity) -> Unit) {
    val list by documents(album.id).collectAsStateWithLifecycle(emptyList())
    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album.name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a álbumes") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (list.isEmpty()) EmptyAlbumDetail(Modifier.padding(padding)) else DocumentGrid(list, Modifier.padding(padding), open)
    }
}

@Composable
private fun EmptyAlbums(modifier: Modifier, create: () -> Unit) = Box(modifier.fillMaxSize().padding(OpenTexSpacing.Xxxl), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(OpenTexSpacing.Lg))
        Text("Aún no tienes álbumes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(OpenTexSpacing.Sm))
        Text("Reúne documentos sin duplicarlos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(OpenTexSpacing.Xl))
        androidx.compose.material3.FilledTonalButton(create) { Text("Crear álbum") }
    }
}

@Composable
private fun EmptyAlbumDetail(modifier: Modifier) = Box(modifier.fillMaxSize(), Alignment.Center) { Text("Añade documentos desde la biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant) }

private fun albumTint(id: Long) = listOf(Color(0xFF192839), Color(0xFF25302D), Color(0xFF2D293A), Color(0xFF312A22))[(id % 4).toInt()]
private fun coverTint(id: Long) = listOf(Color(0xFFB6CBE4), Color(0xFFB0D3C1), Color(0xFFD2C1E8), Color(0xFFE2C8A5))[(id % 4).toInt()]

@Preview(showBackground = true, backgroundColor = 0xFF0E1116, widthDp = 390, heightDp = 840)
@Composable
private fun AlbumsDarkPreview() = OpenTexTheme {
    AlbumsScreen(listOf(AlbumWithDocuments(1, "Física", null, 8), AlbumWithDocuments(2, "Admisión UNSCH", null, 12), AlbumWithDocuments(3, "Repasar esta semana", null, 5)), Modifier, {}, {})
}
