package com.kuritomay.opentex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuritomay.opentex.data.*
import com.kuritomay.opentex.ui.theme.OpenTexColor
import com.kuritomay.opentex.ui.theme.OpenTexSpacing
import com.kuritomay.opentex.ui.theme.OpenTexTheme
import com.kuritomay.opentex.ui.library.DocumentGrid
import com.kuritomay.opentex.ui.library.LibraryLoading
import com.kuritomay.opentex.ui.library.LibraryScreen
import com.kuritomay.opentex.ui.library.SearchField
import com.kuritomay.opentex.ui.library.SectionTitle
import com.kuritomay.opentex.ui.album.AlbumDetail
import com.kuritomay.opentex.ui.album.AlbumsScreen
import com.kuritomay.opentex.ui.study.FlashcardsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: OpenTexViewModel by viewModels()
    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) viewModel.importDocuments(uris) }
    private val exportCardsPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::exportFlashcards) }
    private val importCardsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFlashcards) }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::importTree) }
    override fun onCreate(savedInstanceState: android.os.Bundle?) { super.onCreate(savedInstanceState); setContent { OpenTexTheme { OpenTexApp(viewModel, { documentPicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "application/epub+zip", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }, { exportCardsPicker.launch("opentex-flashcards.zip") }, { importCardsPicker.launch(arrayOf("*/*")) }, { folderPicker.launch(null) }) } } }
}

class OpenTexViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application, (application as OpenTexApplication).database.dao())
    val documents = repository.documents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recent = repository.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val albums = repository.albums.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tabs = repository.tabs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var selectedDocument by mutableStateOf<DocumentEntity?>(null); private set
    var targetPage by mutableStateOf<Int?>(null); private set
    val flashcards = repository.flashcards.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var libraryLoaded by mutableStateOf(false); private set
    init { viewModelScope.launch { runCatching { repository.documents.first() }; libraryLoaded = true } }
    var activeAlbum by mutableStateOf<AlbumWithDocuments?>(null); private set
    var importError by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    fun clearNotice() { notice = null }
    fun importDocuments(uris: List<Uri>) = viewModelScope.launch { runCatching { repository.importAll(uris) }.onSuccess { imported -> imported.lastOrNull()?.let { selectedDocument = it } }.onFailure { importError = "No se pudieron importar los documentos seleccionados." } }
    fun importTree(tree: Uri) = viewModelScope.launch {
        runCatching { repository.importTree(tree) }
            .onSuccess { count -> if (count == 0) importError = "No se encontraron documentos compatibles en esa carpeta." else notice = "Se importaron $count documentos de la carpeta." }
            .onFailure { importError = "No se pudo importar la carpeta seleccionada." }
    }
    fun open(document: DocumentEntity) = viewModelScope.launch { repository.open(document); selectedDocument = document }
    fun openAt(documentId: Long, page: Int) = viewModelScope.launch {
        val document = repository.document(documentId)
        if (document == null) { importError = "El documento de esta fuente ya no está en la biblioteca."; return@launch }
        targetPage = page
        repository.open(document)
        selectedDocument = document
    }
    fun closeReader() { selectedDocument = null; targetPage = null }
    fun closeTab(id: Long) = viewModelScope.launch { repository.closeTab(id) }
    fun saveReadingState(document: DocumentEntity, page: Int, zoom: Float) = viewModelScope.launch { repository.saveState(document, page, zoom) }
    fun createAlbum(name: String) = viewModelScope.launch { repository.createAlbum(name) }
    fun selectAlbum(album: AlbumWithDocuments) { activeAlbum = album }
    fun closeAlbum() { activeAlbum = null }
    fun albumDocuments(id: Long) = repository.albumDocuments(id)
    fun addToAlbum(document: Long, album: Long) = viewModelScope.launch { repository.addToAlbum(document, album) }
    fun annotations(documentId: Long, pageNumber: Int) = repository.annotations(documentId, pageNumber)
    fun saveAnnotation(annotation: AnnotationEntity, complete: (AnnotationEntity) -> Unit) = viewModelScope.launch { complete(repository.saveAnnotation(annotation)) }
    fun deleteAnnotation(id: Long) = viewModelScope.launch { repository.deleteAnnotation(id) }
    fun restoreAnnotation(annotation: AnnotationEntity) = viewModelScope.launch { repository.saveAnnotation(annotation) }
    fun saveCapture(documentId: Long, pageNumber: Int, selection: CaptureSelection, bitmap: Bitmap, complete: (CaptureEntity) -> Unit) = viewModelScope.launch { cropCapture(bitmap, selection)?.let { complete(repository.saveCapture(documentId, pageNumber, selection.left, selection.top, selection.width, selection.height, it)) } ?: run { importError = "La región de captura no es válida." } }
    fun deleteCapture(capture: CaptureEntity) = viewModelScope.launch { repository.deleteCapture(capture) }
    fun notes(documentId: Long) = repository.notes(documentId)
    fun captures(documentId: Long) = repository.captures(documentId)
    fun saveNote(note: NoteEntity, complete: (NoteEntity) -> Unit) = viewModelScope.launch { complete(repository.saveNote(note)) }
    fun deleteNote(id: Long) = viewModelScope.launch { repository.deleteNote(id) }
    fun saveFlashcard(draft: FlashcardDraft, complete: (FlashcardEntity) -> Unit) = viewModelScope.launch {
        val deckId = repository.ensureDeck(draft.deck)
        val isLatex = draft.type == "latex"
        if (draft.id != 0L) {
            val current = repository.flashcard(draft.id)
            if (current == null) { importError = "Esta flashcard ya no existe."; return@launch }
            repository.updateFlashcard(current.copy(deckId = deckId, type = draft.type, frontText = draft.front.trim(), frontImage = draft.frontImage ?: current.frontImage, frontLatex = if (isLatex) draft.front.trim() else null, backText = draft.back.trim(), backLatex = if (isLatex) draft.back.trim() else null, backExplanation = draft.explanation.trim().ifBlank { null }, options = draft.options, correctOptionIds = draft.correct, updatedAt = System.currentTimeMillis()))
            complete(current.copy(updatedAt = System.currentTimeMillis()))
        } else {
            complete(repository.saveFlashcard(FlashcardEntity(deckId = deckId, type = draft.type, frontText = draft.front.trim(), frontImage = draft.frontImage, frontLatex = if (isLatex) draft.front.trim() else null, backText = draft.back.trim(), backLatex = if (isLatex) draft.back.trim() else null, backExplanation = draft.explanation.trim().ifBlank { null }, options = draft.options, correctOptionIds = draft.correct, documentId = draft.documentId, pageNumber = draft.pageNumber, captureId = draft.captureId)))
        }
    }
    fun deleteFlashcard(card: FlashcardEntity) = viewModelScope.launch { repository.deleteFlashcard(card) }
    fun rateFlashcard(id: Long, rating: String) = viewModelScope.launch { repository.rateFlashcard(id, rating) }
    fun exportFlashcards(uri: Uri) = viewModelScope.launch {
        if (flashcards.value.isEmpty()) { importError = "No hay flashcards para exportar."; return@launch }
        if (!repository.exportFlashcards(uri)) importError = "No se pudieron exportar las flashcards."
    }
    fun importFlashcards(uri: Uri) = viewModelScope.launch {
        runCatching { repository.importFlashcards(uri) }
            .onSuccess { notice = it }
            .onFailure { importError = it.message ?: "No se pudieron importar las flashcards." }
    }
    fun setPinned(document: DocumentEntity, pinned: Boolean) = viewModelScope.launch { repository.setPinned(document.id, pinned) }
    fun renameAlias(document: DocumentEntity, alias: String) = viewModelScope.launch { repository.setAlias(document.id, alias) }
    fun removeFromLibrary(document: DocumentEntity) = viewModelScope.launch { repository.removeFromLibrary(document); if (selectedDocument?.id == document.id) closeReader() }
    fun deleteFileAndRemove(document: DocumentEntity) = viewModelScope.launch { if (!repository.deleteFileAndRemove(document)) importError = "Android no concedió permiso para eliminar este archivo." }
    fun canDeleteFile(document: DocumentEntity) = repository.canDeleteFile(document)
    fun clearError() { importError = null }
}

@Composable private fun OpenTexApp(viewModel: OpenTexViewModel, onImport: () -> Unit, onExportCards: () -> Unit, onImportCards: () -> Unit, onImportFolder: () -> Unit) {
    val document = viewModel.selectedDocument
    val album = viewModel.activeAlbum
    BackHandler(enabled = document != null || album != null) {
        if (viewModel.selectedDocument != null) viewModel.closeReader() else viewModel.closeAlbum()
    }
    when {
        document != null -> PdfReader(document, viewModel, viewModel::closeReader, viewModel::saveReadingState)
        album != null -> AlbumDetail(album, viewModel::closeAlbum, viewModel::albumDocuments, viewModel::open)
        else -> Home(viewModel, onImport, onExportCards, onImportCards, onImportFolder)
    }
    CrashReportDialog()
}

@Composable private fun Home(viewModel: OpenTexViewModel, onImport: () -> Unit, onExportCards: () -> Unit, onImportCards: () -> Unit, onImportFolder: () -> Unit) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val flashcards by viewModel.flashcards.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableIntStateOf(0) }; var createAlbum by remember { mutableStateOf(false) }; var assigningDocument by remember { mutableStateOf<DocumentEntity?>(null) }; var managingDocument by remember { mutableStateOf<DocumentEntity?>(null) }; var renamingDocument by remember { mutableStateOf<DocumentEntity?>(null) }; var confirmingRemoval by remember { mutableStateOf<DocumentEntity?>(null) }
    var editingCard by remember { mutableStateOf<FlashcardWithDeck?>(null) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        Column {
            OpenTexTopBar(section, onImport, if (section == 1) { { createAlbum = true } } else null, onSearch = { searchVisible = !searchVisible }, onExportCards = onExportCards, onImportCards = onImportCards, onImportFolder = onImportFolder)
            if (section == 0) SearchField(libraryQuery, { libraryQuery = it }, { libraryQuery = ""; searchVisible = false }, searchVisible)
        }
    }, bottomBar = { OpenTexBottomNavigation(section) { section = it } }) { padding ->
        when (section) {
            0 -> if (!viewModel.libraryLoaded) LibraryLoading(Modifier.padding(padding)) else LibraryScreen(documents, tabs, Modifier.padding(padding), viewModel::open, viewModel::closeTab, { managingDocument = it }, onImport, libraryQuery)
            1 -> AlbumsScreen(albums, Modifier.padding(padding), viewModel::selectAlbum, { createAlbum = true })
            2 -> FlashcardsScreen(flashcards, Modifier.padding(padding), { id, rating -> viewModel.rateFlashcard(id, rating) }, { id, page -> viewModel.openAt(id, page) }, editCard = { editingCard = it })
            else -> RecentScreen(recent, Modifier.padding(padding), viewModel::open)
        }
    }
    if (createAlbum) AlbumDialog({ createAlbum = false }) { viewModel.createAlbum(it); createAlbum = false }
    assigningDocument?.let { document -> AddToAlbumDialog(document, albums, { assigningDocument = null }) { albumId -> viewModel.addToAlbum(document.id, albumId); assigningDocument = null } }
    managingDocument?.let { document -> DocumentActionsDialog(document, viewModel.canDeleteFile(document), { managingDocument = null }, viewModel::open, { viewModel.setPinned(document, !document.isPinned); managingDocument = null }, { renamingDocument = document; managingDocument = null }, { assigningDocument = document; managingDocument = null }, { confirmingRemoval = document; managingDocument = null }) }
    renamingDocument?.let { document -> AliasDialog(document, { renamingDocument = null }) { alias -> viewModel.renameAlias(document, alias); renamingDocument = null } }
    confirmingRemoval?.let { document -> RemoveDocumentDialog(document, viewModel.canDeleteFile(document), { confirmingRemoval = null }) { deleteFile -> if (deleteFile) viewModel.deleteFileAndRemove(document) else viewModel.removeFromLibrary(document); confirmingRemoval = null } }
    editingCard?.let { entry ->
        val card = entry.card
        FlashcardEditorDialog(
            draft = FlashcardDraft(id = card.id, documentId = card.documentId, pageNumber = card.pageNumber, captureId = card.captureId, frontImage = card.frontImage, deck = entry.deck.deckName, type = card.type, front = card.frontText, back = card.backText, explanation = card.backExplanation.orEmpty(), options = card.options, correct = card.correctOptionIds),
            dismiss = { editingCard = null },
            save = { updated -> viewModel.saveFlashcard(updated) { editingCard = null } },
            remove = { viewModel.deleteFlashcard(card); editingCard = null },
        )
    }
    viewModel.importError?.let { AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("No se pudo importar") }, text = { Text(it) }, confirmButton = { TextButton(viewModel::clearError) { Text("Entendido") } }) }
    viewModel.notice?.let { message -> AlertDialog(onDismissRequest = viewModel::clearNotice, title = { Text("Flashcards") }, text = { Text(message) }, confirmButton = { TextButton(viewModel::clearNotice) { Text("Entendido") } }) }
}

@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLogger.pendingCrash(context)) }
    report?.let { trace ->
        AlertDialog(
            onDismissRequest = { CrashLogger.clear(context); report = null },
            title = { Text("OpenTex se cerró inesperadamente") },
            text = {
                Column {
                    Text("Guardamos la traza del fallo. Compártela para poder diagnosticar el problema.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(OpenTexSpacing.Sm))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(trace.take(2000), Modifier.padding(OpenTexSpacing.Sm).heightIn(max = 240.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton({ runCatching { context.startActivity(CrashLogger.shareIntent(trace)) }; CrashLogger.clear(context); report = null }) { Text("Compartir") } },
            dismissButton = { TextButton({ CrashLogger.clear(context); report = null }) { Text("Cerrar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OpenTexTopBar(section: Int, onImport: () -> Unit, onAdd: (() -> Unit)?, onSearch: () -> Unit, onExportCards: () -> Unit = {}, onImportCards: () -> Unit = {}, onImportFolder: () -> Unit = {}) = TopAppBar(
    title = { Text(if (section == 0) "OpenTex" else listOf("Biblioteca", "Álbumes", "Flashcards", "Recientes")[section], style = MaterialTheme.typography.headlineSmall) },
    actions = {
        when {
            section == 0 -> { IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Buscar") }; IconButton(onClick = onImport) { Icon(Icons.Outlined.Add, "Abrir PDF") }; IconButton(onClick = onImportFolder) { Icon(Icons.Outlined.CreateNewFolder, "Importar carpeta") } }
            section == 1 -> onAdd?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Add, "Crear álbum") } }
            section == 2 -> { IconButton(onClick = onImportCards) { Icon(Icons.Outlined.FileDownload, "Importar flashcards") }; IconButton(onClick = onExportCards) { Icon(Icons.Outlined.FileUpload, "Exportar flashcards") } }
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
)

@Composable private fun OpenTexBottomNavigation(selected: Int, select: (Int) -> Unit) = NavigationBar(containerColor = OpenTexColor.Surface) {
    listOf("Biblioteca" to Icons.Outlined.MenuBook, "Álbumes" to Icons.Outlined.CollectionsBookmark, "Flashcards" to Icons.Outlined.Style, "Recientes" to Icons.Outlined.History).forEachIndexed { index, entry -> NavigationBarItem(selected = index == selected, onClick = { select(index) }, icon = { Icon(entry.second, null) }, label = { Text(entry.first) }) }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable private fun RecentScreen(documents: List<DocumentEntity>, modifier: Modifier, open: (DocumentEntity) -> Unit) { if (documents.isEmpty()) EmptyState("Sin lecturas recientes", "Los documentos que abras aparecerán aquí.", Icons.Outlined.History, modifier) else Column(modifier.fillMaxSize()) { SectionTitle("Continúa leyendo", "${documents.size} documentos", Modifier.padding(horizontal = OpenTexSpacing.Lg, vertical = OpenTexSpacing.Xl)); LazyColumn(contentPadding = PaddingValues(horizontal = OpenTexSpacing.Lg), verticalArrangement = Arrangement.spacedBy(OpenTexSpacing.Md)) { items(documents, key = { it.id }) { document -> Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).combinedClickable(onClick = { open(document) }).padding(vertical = OpenTexSpacing.Sm), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(40.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { Icon(Icons.Outlined.Description, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary) }; Spacer(Modifier.width(OpenTexSpacing.Md)); Column(Modifier.weight(1f)) { Text(document.name.substringBeforeLast("."), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Página ${document.currentPage + 1} · ${(document.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun EmptyState(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, action: (() -> Unit)? = null) = Box(modifier.fillMaxSize().padding(OpenTexSpacing.Xxxl), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(OpenTexSpacing.Lg)); Text(title, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(OpenTexSpacing.Sm)); Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); if (action != null) { Spacer(Modifier.height(OpenTexSpacing.Xl)); FilledTonalButton(action) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(OpenTexSpacing.Sm)); Text("Abrir PDF") } } } }
@Composable private fun AlbumDialog(dismiss: () -> Unit, confirm: (String) -> Unit) { var name by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = dismiss, title = { Text("Nuevo álbum") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true) }, confirmButton = { TextButton({ confirm(name) }, enabled = name.isNotBlank()) { Text("Crear") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } }) }
@Composable private fun AddToAlbumDialog(document: DocumentEntity, albums: List<AlbumWithDocuments>, dismiss: () -> Unit, add: (Long) -> Unit) = AlertDialog(
    onDismissRequest = dismiss,
    title = { Text("Añadir a álbum") },
    text = {
        if (albums.isEmpty()) Text("Crea primero un álbum desde la sección Álbumes.")
        else Column {
            Text(document.name, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(OpenTexSpacing.Md))
            albums.forEach { album ->
                TextButton({ add(album.id) }, Modifier.fillMaxWidth()) {
                    Text(album.name, Modifier.weight(1f))
                    Text("${album.documentCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    },
    confirmButton = { TextButton(dismiss) { Text("Cancelar") } },
)

@Composable private fun DocumentActionsDialog(document: DocumentEntity, canDeleteFile: Boolean, dismiss: () -> Unit, open: (DocumentEntity) -> Unit, pin: () -> Unit, rename: () -> Unit, addToAlbum: () -> Unit, remove: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss,
    title = { Text(document.alias ?: document.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    text = { Column { TextButton({ open(document); dismiss() }, Modifier.fillMaxWidth()) { Text("Abrir", Modifier.weight(1f)) }; TextButton(pin, Modifier.fillMaxWidth()) { Text(if (document.isPinned) "Desfijar" else "Fijar", Modifier.weight(1f)) }; TextButton(rename, Modifier.fillMaxWidth()) { Text("Renombrar alias", Modifier.weight(1f)) }; TextButton(addToAlbum, Modifier.fillMaxWidth()) { Text("Añadir a álbum", Modifier.weight(1f)) }; Text("${document.type} · ${document.pageCount} páginas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = OpenTexSpacing.Sm)); TextButton(remove, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (canDeleteFile) "Quitar de biblioteca o eliminar archivo" else "Quitar de biblioteca", Modifier.weight(1f)) } } },
    confirmButton = { TextButton(dismiss) { Text("Cerrar") } },
)
@Composable private fun AliasDialog(document: DocumentEntity, dismiss: () -> Unit, confirm: (String) -> Unit) { var alias by remember(document.id) { mutableStateOf(document.alias ?: "") }; AlertDialog(onDismissRequest = dismiss, title = { Text("Renombrar alias") }, text = { OutlinedTextField(alias, { alias = it }, label = { Text("Alias en OpenTex") }, singleLine = true) }, confirmButton = { TextButton({ confirm(alias) }) { Text("Guardar") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } }) }
@Composable private fun RemoveDocumentDialog(document: DocumentEntity, canDeleteFile: Boolean, dismiss: () -> Unit, confirm: (Boolean) -> Unit) { var deleteFile by remember { mutableStateOf(false) }; AlertDialog(onDismissRequest = dismiss, title = { Text("Quitar ${document.alias ?: document.name}") }, text = { Column { Text("Esta acción eliminará el documento de OpenTex."); if (canDeleteFile) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = OpenTexSpacing.Md)) { Checkbox(deleteFile, { deleteFile = it }); Text("Eliminar también el archivo del dispositivo") } } }, confirmButton = { TextButton({ confirm(deleteFile) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } }) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PdfReader(document: DocumentEntity, viewModel: OpenTexViewModel, close: () -> Unit, save: (DocumentEntity, Int, Float) -> Unit) {
    var page by rememberSaveable(document.id, viewModel.targetPage) { mutableIntStateOf((viewModel.targetPage ?: document.currentPage).coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))) }
    var zoom by rememberSaveable(document.id) { mutableFloatStateOf(document.zoom.coerceAtLeast(1f)) }
    var panX by rememberSaveable(document.id) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(document.id) { mutableFloatStateOf(0f) }
    var selectedTool by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var annotationColorValue by rememberSaveable { mutableLongStateOf(OpenTexColor.AnnotationYellow.toArgb().toLong()) }
    val annotationColor = Color(annotationColorValue)
    var strokeSize by rememberSaveable { mutableFloatStateOf(.009f) }
    val undoStack = remember { mutableStateListOf<EditorAction>() }
    val redoStack = remember { mutableStateListOf<EditorAction>() }
    var savedCapture by remember { mutableStateOf<CaptureEntity?>(null) }
    var noteDraft by remember { mutableStateOf<NoteDraft?>(null) }
    var flashcardDraft by remember { mutableStateOf<FlashcardDraft?>(null) }
    val isText = document.type == "TXT" || document.type == "MD"
    BackHandler(enabled = selectedTool != null || showNotes) { when { selectedTool != null -> selectedTool = null; showNotes -> showNotes = false } }
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, document.uri, page) { value = withContext(Dispatchers.IO) { renderPdfPage(context, document.uri, page) } }
    val annotations by viewModel.annotations(document.id, page).collectAsStateWithLifecycle(emptyList())
    val notes by viewModel.notes(document.id).collectAsStateWithLifecycle(emptyList())
    val captures by viewModel.captures(document.id).collectAsStateWithLifecycle(emptyList())
    val state = rememberTransformableState { scale, offset, _ ->
        val next = (zoom * scale).coerceIn(1f, 5f); zoom = next
        panX = if (next == 1f) 0f else panX + offset.x; panY = if (next == 1f) 0f else panY + offset.y
    }
    LaunchedEffect(page) { save(document, page, zoom) }
    LaunchedEffect(zoom) { delay(600); save(document, page, zoom) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(close) { Icon(Icons.Outlined.ArrowBack, "Biblioteca") } }, actions = { if (isText) Text(document.type, style = MaterialTheme.typography.labelSmall) else Text("${page + 1} / ${document.pageCount}", style = MaterialTheme.typography.labelSmall); IconButton({ showNotes = !showNotes }) { Icon(Icons.Outlined.NoteAlt, "Notas") }; IconButton({}) { Icon(Icons.Outlined.BookmarkBorder, "Marcador") }; IconButton({}) { Icon(Icons.Outlined.MoreHoriz, "Menú") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = OpenTexColor.Surface)) },
        bottomBar = { if (!showNotes && !isText) ReaderToolbar(selectedTool, undoStack.isNotEmpty(), redoStack.isNotEmpty(), { label -> if (label == "Nota") { noteDraft = NoteDraft(documentId = document.id, pageNumber = page); selectedTool = null } else selectedTool = if (selectedTool == label) null else label }, { val action = undoStack.removeLastOrNull() ?: return@ReaderToolbar; when (action) { is EditorAction.Add -> viewModel.deleteAnnotation(action.annotation.id); is EditorAction.Remove -> viewModel.restoreAnnotation(action.annotation) }; redoStack.add(action) }, { val action = redoStack.removeLastOrNull() ?: return@ReaderToolbar; when (action) { is EditorAction.Add -> viewModel.restoreAnnotation(action.annotation); is EditorAction.Remove -> viewModel.deleteAnnotation(action.annotation.id) }; undoStack.add(action) }) },
    ) { padding ->
        when {
            showNotes -> ReaderNotes(page, notes, captures, Modifier.padding(padding), createNote = { notePage -> noteDraft = NoteDraft(documentId = document.id, pageNumber = notePage) }, editNote = { note -> noteDraft = NoteDraft(id = note.id, documentId = note.documentId, pageNumber = note.pageNumber, captureId = note.captureId, type = note.type, title = note.title, body = note.body, tags = note.tags.orEmpty(), createdAt = note.createdAt) }, deleteNote = viewModel::deleteNote, goToPage = { target -> page = target.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)); showNotes = false })
            isText -> TextDocument(document, Modifier.fillMaxSize().padding(padding))
            else -> Box(Modifier.fillMaxSize().padding(padding).background(OpenTexColor.Background), Alignment.Center) {
                val pageBitmap = bitmap
                if (pageBitmap == null) {
                    if (document.type == "PDF") CircularProgressIndicator() else UnsupportedFormat(document.type)
                } else Image(pageBitmap.asImageBitmap(), null, Modifier.fillMaxWidth().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = panX, translationY = panY).transformable(state).pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1.1f) 1f else 2.25f; panX = 0f; panY = 0f }) }, contentScale = ContentScale.Fit)
            AnnotationCanvas(annotations, selectedTool, annotationColor, strokeSize, Modifier.fillMaxSize(), { points, _ -> viewModel.saveAnnotation(AnnotationEntity(documentId = document.id, pageNumber = page, tool = selectedTool ?: return@AnnotationCanvas, color = annotationColor.toArgb().toLong(), strokeWidth = strokeSize, opacity = if (selectedTool == "Resaltar") .34f else .9f, points = points)) { inserted -> undoStack.add(EditorAction.Add(inserted)); redoStack.clear() } }, { annotation -> viewModel.deleteAnnotation(annotation.id); undoStack.add(EditorAction.Remove(annotation)); redoStack.clear() })
            if (selectedTool == "Capturar" && pageBitmap != null) CaptureSelector(Modifier.fillMaxSize()) { selection -> viewModel.saveCapture(document.id, page, selection, pageBitmap) { savedCapture = it; selectedTool = null } }
            AnimatedVisibility(selectedTool == "Lápiz" || selectedTool == "Resaltar", Modifier.align(Alignment.BottomCenter)) { val tool = selectedTool ?: return@AnimatedVisibility; AnnotationOptions(tool, annotationColor, strokeSize, { annotationColorValue = it.toArgb().toLong() }, { strokeSize = it }) }
            Surface(Modifier.align(Alignment.BottomEnd).padding(OpenTexSpacing.Md), color = OpenTexColor.SurfaceElevated, shape = MaterialTheme.shapes.medium) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = OpenTexSpacing.Xs)) { IconButton({ if (page > 0) page-- }) { Icon(Icons.Outlined.ChevronLeft, "Página anterior") }; Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall); IconButton({ if (page + 1 < document.pageCount) page++ }) { Icon(Icons.Outlined.ChevronRight, "Página siguiente") } } }
            }
        }
    }
    savedCapture?.let { capture ->
        AlertDialog(
            onDismissRequest = { savedCapture = null },
            title = { Text("Captura guardada") },
            text = {
                Column {
                    CaptureThumbnail(capture.imagePath, Modifier.fillMaxWidth().height(112.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.height(OpenTexSpacing.Sm))
                    Text("Página ${capture.pageNumber + 1}. Conserva el enlace a su fuente.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ noteDraft = NoteDraft(documentId = document.id, pageNumber = capture.pageNumber, captureId = capture.id); savedCapture = null }, Modifier.fillMaxWidth()) { Text("Crear nota desde esta captura") }
                    TextButton({ flashcardDraft = FlashcardDraft(documentId = document.id, pageNumber = capture.pageNumber, captureId = capture.id, frontImage = capture.imagePath); savedCapture = null }, Modifier.fillMaxWidth()) { Text("Crear flashcard desde esta captura") }
                }
            },
            confirmButton = { TextButton({ savedCapture = null }) { Text("Guardar captura") } },
            dismissButton = { TextButton({ viewModel.deleteCapture(capture); savedCapture = null }) { Text("Cancelar") } },
        )
    }
    noteDraft?.let { draft ->
        NoteEditorDialog(
            draft = draft,
            dismiss = { noteDraft = null },
            save = { updated -> viewModel.saveNote(NoteEntity(id = updated.id, documentId = updated.documentId, pageNumber = updated.pageNumber, type = updated.type, title = updated.title.trim(), body = updated.body.trim(), captureId = updated.captureId, tags = updated.tags.trim().ifBlank { null }, createdAt = updated.createdAt)) { noteDraft = null } },
            remove = if (draft.id != 0L) ({ viewModel.deleteNote(draft.id); noteDraft = null }) else null,
        )
    }
    flashcardDraft?.let { draft ->
        FlashcardEditorDialog(
            draft = draft,
            dismiss = { flashcardDraft = null },
            save = { updated -> viewModel.saveFlashcard(updated) { flashcardDraft = null } },
        )
    }
}
@Composable private fun ReaderToolbar(selected: String?, canUndo: Boolean, canRedo: Boolean, select: (String) -> Unit, undo: () -> Unit, redo: () -> Unit) = Surface(color = OpenTexColor.Surface, tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = OpenTexSpacing.Sm), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("Lápiz" to Icons.Outlined.Edit, "Resaltar" to Icons.Outlined.Highlight, "Borrador" to Icons.Outlined.DeleteOutline, "Capturar" to Icons.Outlined.CropFree, "Nota" to Icons.Outlined.NoteAdd).forEach { (label, icon) -> IconButton({ select(label) }) { Icon(icon, label, tint = if (selected == label) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }; IconButton(undo, enabled = canUndo) { Icon(Icons.Outlined.Undo, "Deshacer") }; IconButton(redo, enabled = canRedo) { Icon(Icons.Outlined.Redo, "Rehacer") } } }
@Composable private fun AnnotationOptions(tool: String, selectedColor: Color, strokeSize: Float, selectColor: (Color) -> Unit, selectSize: (Float) -> Unit) = Surface(Modifier.padding(bottom = 56.dp), color = OpenTexColor.SurfaceElevated, shape = MaterialTheme.shapes.medium) { Row(Modifier.padding(OpenTexSpacing.Sm), horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Sm), verticalAlignment = Alignment.CenterVertically) { Text(tool, style = MaterialTheme.typography.labelSmall); listOf(OpenTexColor.AnnotationYellow, OpenTexColor.AnnotationBlue, OpenTexColor.AnnotationGreen, OpenTexColor.AnnotationPink, OpenTexColor.AnnotationPurple).forEach { color -> IconButton({ selectColor(color) }, Modifier.size(28.dp)) { Box(Modifier.size(if (color == selectedColor) 20.dp else 16.dp).clip(RoundedCornerShape(10.dp)).background(color)) } }; TextButton({ selectSize(if (strokeSize < .012f) .016f else .009f) }) { Text("Grosor") } } }
private sealed interface EditorAction { val annotation: AnnotationEntity; data class Add(override val annotation: AnnotationEntity) : EditorAction; data class Remove(override val annotation: AnnotationEntity) : EditorAction }
@Composable private fun AnnotationCanvas(annotations: List<AnnotationEntity>, tool: String?, color: Color, strokeSize: Float, modifier: Modifier, onStroke: (String, Float) -> Unit, onErase: (AnnotationEntity) -> Unit) { var draft by remember { mutableStateOf<List<Offset>>(emptyList()) }; val drawingModifier = when (tool) { "Lápiz", "Resaltar" -> Modifier.pointerInput(tool, color, strokeSize) { detectDragGestures(onDragStart = { draft = listOf(it) }, onDrag = { change, _ -> change.consume(); draft = draft + change.position }, onDragEnd = { if (draft.size > 1) onStroke(draft.joinToString(";") { "${it.x / size.width},${it.y / size.height}" }, minOf(size.width, size.height).toFloat()); draft = emptyList() }) }; "Borrador" -> Modifier.pointerInput(annotations) { detectTapGestures { point -> annotations.lastOrNull { annotation -> annotationPoints(annotation.points).any { kotlin.math.hypot((it.x - point.x / size.width).toDouble(), (it.y - point.y / size.height).toDouble()) < .035 } }?.let(onErase) } }; else -> Modifier }; Canvas(modifier.then(drawingModifier)) { annotations.forEach { annotation -> drawAnnotation(annotation, size) }; if (draft.size > 1) drawPath(pointsToPath(draft), color.copy(alpha = if (tool == "Resaltar") .34f else .9f), style = Stroke(width = strokeSize * minOf(size.width, size.height), cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
@Composable private fun CaptureSelector(modifier: Modifier, onCapture: (CaptureSelection) -> Unit) { var start by remember { mutableStateOf<Offset?>(null) }; var end by remember { mutableStateOf<Offset?>(null) }; val primary = MaterialTheme.colorScheme.primary; Canvas(modifier.pointerInput(Unit) { detectDragGestures(onDragStart = { start = it; end = it }, onDrag = { change, _ -> change.consume(); end = change.position }, onDragEnd = { val first = start; val last = end; if (first != null && last != null) { val left = minOf(first.x, last.x) / size.width; val top = minOf(first.y, last.y) / size.height; val width = kotlin.math.abs(first.x - last.x) / size.width; val height = kotlin.math.abs(first.y - last.y) / size.height; if (width > .02f && height > .02f) onCapture(CaptureSelection(left, top, width, height)) }; start = null; end = null }) }) { val first = start; val last = end; if (first != null && last != null) { val rect = Rect(minOf(first.x, last.x), minOf(first.y, last.y), maxOf(first.x, last.x), maxOf(first.y, last.y)); drawRect(Color.Black.copy(alpha = .28f)); drawRect(Color.Transparent, topLeft = rect.topLeft, size = rect.size); drawRect(primary, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 2.dp.toPx())) } } }
data class CaptureSelection(val left: Float, val top: Float, val width: Float, val height: Float)
private fun cropCapture(bitmap: Bitmap, selection: CaptureSelection): Bitmap? = runCatching { val left = (selection.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1); val top = (selection.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1); val width = (selection.width * bitmap.width).toInt().coerceIn(1, bitmap.width - left); val height = (selection.height * bitmap.height).toInt().coerceIn(1, bitmap.height - top); Bitmap.createBitmap(bitmap, left, top, width, height) }.getOrNull()
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(annotation: AnnotationEntity, size: androidx.compose.ui.geometry.Size) { val points = annotationPoints(annotation.points).map { Offset(it.x * size.width, it.y * size.height) }; if (points.size > 1) drawPath(pointsToPath(points), Color(annotation.color.toInt()).copy(alpha = annotation.opacity), style = Stroke(width = annotation.strokeWidth * minOf(size.width, size.height), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
private fun annotationPoints(encoded: String): List<Offset> = encoded.split(';').mapNotNull { pair -> pair.split(',').takeIf { it.size == 2 }?.let { runCatching { Offset(it[0].toFloat(), it[1].toFloat()) }.getOrNull() } }
private fun pointsToPath(points: List<Offset>): Path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
private data class NoteDraft(
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val captureId: Long? = null,
    val type: String = "normal",
    val title: String = "",
    val body: String = "",
    val tags: String = "",
    val createdAt: Long = 0L,
)

data class FlashcardDraft(
    val id: Long = 0,
    val documentId: Long? = null,
    val pageNumber: Int? = null,
    val captureId: Long? = null,
    val frontImage: String? = null,
    val deck: String = "General",
    val type: String = "basic",
    val front: String = "",
    val back: String = "",
    val explanation: String = "",
    val options: String? = null,
    val correct: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlashcardEditorDialog(draft: FlashcardDraft, dismiss: () -> Unit, save: (FlashcardDraft) -> Unit, remove: (() -> Unit)? = null) {
    var front by remember(draft) { mutableStateOf(draft.front) }
    var back by remember(draft) { mutableStateOf(draft.back) }
    var explanation by remember(draft) { mutableStateOf(draft.explanation) }
    var deck by remember(draft) { mutableStateOf(draft.deck) }
    var type by remember(draft) { mutableStateOf(draft.type) }
    var optionLines by remember(draft) { mutableStateOf(optionsToLines(draft.options, draft.correct)) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (draft.id != 0L) "Editar flashcard" else "Nueva flashcard") },
        text = {
            Column {
                if (draft.frontImage != null) {
                    CaptureThumbnail(draft.frontImage, Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.height(OpenTexSpacing.Md))
                }
                if (draft.pageNumber != null) Text("Página ${draft.pageNumber + 1} · se guardará con su fuente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text("Sin fuente vinculada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(OpenTexSpacing.Md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Sm)) {
                    listOf("basic" to "Básica", "reversed" to "Invertida", "latex" to "LaTeX", "multiple_choice" to "Opción múltiple").forEach { (value, label) ->
                        FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(OpenTexSpacing.Md))
                OutlinedTextField(front, { front = it }, label = { Text(if (type == "latex") "Fórmula (anverso)" else "Pregunta") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(OpenTexSpacing.Md))
                OutlinedTextField(back, { back = it }, label = { Text(if (type == "latex") "Fórmula (reverso)" else "Respuesta") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (type == "multiple_choice") {
                    Spacer(Modifier.height(OpenTexSpacing.Md))
                    OutlinedTextField(optionLines, { optionLines = it }, label = { Text("Opciones") }, supportingText = { Text("Una por línea; marca la correcta con *") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(OpenTexSpacing.Md))
                OutlinedTextField(explanation, { explanation = it }, label = { Text("Explicación (opcional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(OpenTexSpacing.Md))
                OutlinedTextField(deck, { deck = it }, label = { Text("Mazo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (remove != null) {
                    TextButton(onClick = remove, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Eliminar flashcard") }
                }
            }
        },
        confirmButton = { TextButton({ save(applyDraft(draft, type, front, back, explanation, deck, optionLines)) }, enabled = front.isNotBlank() && back.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(dismiss) { Text("Cancelar") } },
    )
}

private fun optionsToLines(options: String?, correct: String?): String {
    if (options.isNullOrBlank()) return ""
    val labels = FlashcardPort.options(FlashcardEntity(deckId = 0, frontText = "", options = options, correctOptionIds = correct))
    val right = FlashcardPort.correct(FlashcardEntity(deckId = 0, frontText = "", options = options, correctOptionIds = correct))
    return labels.mapIndexed { index, label -> if (index in right) "*$label" else label }.joinToString("\n")
}

private fun applyDraft(draft: FlashcardDraft, type: String, front: String, back: String, explanation: String, deck: String, optionLines: String): FlashcardDraft {
    val lines = optionLines.lines().map(String::trim).filter(String::isNotBlank)
    val isChoice = type == "multiple_choice" && lines.isNotEmpty()
    val options = if (isChoice) JSONArray(lines.map { it.removePrefix("*").trim() }).toString() else null
    val correct = if (isChoice) JSONArray(lines.mapIndexedNotNull { index, line -> if (line.startsWith("*")) index else null }).toString() else null
    return draft.copy(type = type, front = front, back = back, explanation = explanation, deck = deck, options = options, correct = correct)
}

@Composable
private fun ReaderNotes(
    page: Int,
    notes: List<NoteEntity>,
    captures: List<CaptureEntity>,
    modifier: Modifier,
    createNote: (Int) -> Unit,
    editNote: (NoteEntity) -> Unit,
    deleteNote: (Long) -> Unit,
    goToPage: (Int) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val capturesById = remember(captures) { captures.associateBy { it.id } }
    Column(modifier.fillMaxSize()) {
        TabRow(tab, containerColor = Color.Transparent) {
            listOf("Notas", "Resumen", "Índice").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> NotesList(notes.sortedBy { it.pageNumber }, capturesById, Modifier.weight(1f), { createNote(page) }, editNote, deleteNote, goToPage)
            1 -> {
                val summaries = notes.filter { it.type == "summary" }.sortedBy { it.pageNumber }
                if (summaries.isEmpty()) NotesEmpty("Sin resúmenes", "Crea una nota con el tipo Resumen para condensar las ideas del documento.", Modifier.weight(1f))
                else NotesList(summaries, capturesById, Modifier.weight(1f), { createNote(page) }, editNote, deleteNote, goToPage)
            }
            else -> DocumentIndex(notes, captures, Modifier.weight(1f), goToPage)
        }
    }
}

@Composable
private fun NotesList(notes: List<NoteEntity>, capturesById: Map<Long, CaptureEntity>, modifier: Modifier, createNote: () -> Unit, editNote: (NoteEntity) -> Unit, deleteNote: (Long) -> Unit, goToPage: (Int) -> Unit) {
    if (notes.isEmpty()) {
        NotesEmpty("Sin notas en este documento", "Usa la herramienta Nota o crea una desde una captura.", modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = OpenTexSpacing.Lg), verticalArrangement = Arrangement.spacedBy(OpenTexSpacing.Md)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Notas", "${notes.size}", Modifier.weight(1f))
                IconButton(onClick = createNote) { Icon(Icons.Outlined.Add, "Añadir nota") }
            }
        }
        items(notes, key = { it.id }) { note -> NoteCard(note, capturesById[note.captureId], { editNote(note) }, { deleteNote(note.id) }, goToPage) }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, capture: CaptureEntity?, edit: () -> Unit, remove: () -> Unit, goToPage: (Int) -> Unit) {
    val accent = noteColor(note.type)
    val date = remember(note.updatedAt) { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(note.updatedAt)) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min).clickable(onClick = edit)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(OpenTexSpacing.Lg).weight(1f)) {
                Text("Página ${note.pageNumber + 1} · $date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(OpenTexSpacing.Xs))
                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(OpenTexSpacing.Xs))
                    Text(note.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (note.tags.isNullOrBlank().not()) {
                    Spacer(Modifier.height(OpenTexSpacing.Xs))
                    Text(note.tags.orEmpty(), style = MaterialTheme.typography.labelSmall, color = accent)
                }
                if (capture != null) {
                    Spacer(Modifier.height(OpenTexSpacing.Md))
                    CaptureThumbnail(capture.imagePath, Modifier.fillMaxWidth().height(116.dp).clip(MaterialTheme.shapes.small))
                }
                Spacer(Modifier.height(OpenTexSpacing.Xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { goToPage(if (capture != null) capture.pageNumber else note.pageNumber) }) { Text("Ver en fuente", style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = remove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Eliminar", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun DocumentIndex(notes: List<NoteEntity>, captures: List<CaptureEntity>, modifier: Modifier, goToPage: (Int) -> Unit) {
    val entries = remember(notes, captures) {
        (notes.map { it.pageNumber to it.title.ifBlank { "Nota" } } + captures.map { it.pageNumber to (it.title ?: "Captura") })
            .groupBy({ it.first }, { it.second })
            .toList()
            .sortedBy { it.first }
    }
    if (entries.isEmpty()) {
        NotesEmpty("Sin contenido indexado", "Las notas y capturas aparecerán aquí, agrupadas por página.", modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = OpenTexSpacing.Lg), verticalArrangement = Arrangement.spacedBy(OpenTexSpacing.Xs)) {
        item(key = "header") { SectionTitle("Índice", "${entries.size} páginas", Modifier.padding(bottom = OpenTexSpacing.Sm)) }
        items(entries, key = { it.first }) { (pageNumber, titles) ->
            Row(
                Modifier.fillMaxWidth().clickable { goToPage(pageNumber) }.padding(vertical = OpenTexSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pág. ${pageNumber + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(76.dp))
                Text(titles.distinct().joinToString(" · "), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotesEmpty(title: String, text: String, modifier: Modifier) = Box(modifier.fillMaxSize().padding(OpenTexSpacing.Xxxl), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.NoteAlt, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(OpenTexSpacing.Md))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(OpenTexSpacing.Xs))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CaptureThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded.asImageBitmap(), contentDescription = null, modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CropFree, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorDialog(draft: NoteDraft, dismiss: () -> Unit, save: (NoteDraft) -> Unit, remove: (() -> Unit)?) {
    var type by remember(draft.id, draft.pageNumber) { mutableStateOf(draft.type) }
    var title by remember(draft.id, draft.pageNumber) { mutableStateOf(draft.title) }
    var body by remember(draft.id, draft.pageNumber) { mutableStateOf(draft.body) }
    var tags by remember(draft.id, draft.pageNumber) { mutableStateOf(draft.tags) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (draft.id == 0L) "Nueva nota" else "Editar nota") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(OpenTexSpacing.Sm)) {
                    listOf("normal" to "Nota", "reminder" to "Recordatorio", "summary" to "Resumen").forEach { (value, label) ->
                        FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(OpenTexSpacing.Md))
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(OpenTexSpacing.Md))
                OutlinedTextField(body, { body = it }, label = { Text("¿Para qué sirve?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(OpenTexSpacing.Md))
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (remove != null) {
                    TextButton(onClick = remove, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Eliminar nota") }
                }
            }
        },
        confirmButton = { TextButton({ save(draft.copy(type = type, title = title, body = body, tags = tags)) }, enabled = title.isNotBlank() || body.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(dismiss) { Text("Cancelar") } },
    )
}

private fun noteColor(type: String): Color = when (type) {
    "reminder" -> OpenTexColor.AnnotationYellow
    "summary" -> OpenTexColor.AnnotationPurple
    else -> OpenTexColor.AnnotationBlue
}
@Composable
private fun TextDocument(document: DocumentEntity, modifier: Modifier) {
    val context = LocalContext.current
    val content by produceState<String?>(null, document.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(Uri.parse(document.uri))?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        }
    }
    val text = content
    Box(modifier.background(OpenTexColor.Background), contentAlignment = Alignment.Center) {
        if (text == null) {
            CircularProgressIndicator()
        } else {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(OpenTexSpacing.Lg)) {
                Text(text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
private fun UnsupportedFormat(type: String, modifier: Modifier = Modifier) = Box(modifier, Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(OpenTexSpacing.Xxxl)) {
        Icon(Icons.Outlined.Block, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(OpenTexSpacing.Md))
        Text("Vista previa no disponible", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(OpenTexSpacing.Sm))
        Text("OpenTex todavía no puede abrir archivos $type. Puedes convertirlos a PDF e importarlos de nuevo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun renderPdfPage(context: android.content.Context, uri: String, pageIndex: Int): Bitmap? = runCatching {
    val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r") ?: return@runCatching null
    var renderer: PdfRenderer? = null
    try {
        val pdf = PdfRenderer(descriptor).also { renderer = it }
        if (pdf.pageCount == 0) return@runCatching null
        val page = pdf.openPage(pageIndex.coerceIn(0, pdf.pageCount - 1))
        try {
            val metrics = context.resources.displayMetrics
            val ratio = minOf(metrics.widthPixels * 2f / page.width, metrics.heightPixels * 2f / page.height, 3f)
            val width = (page.width * ratio).roundToInt().coerceAtLeast(1)
            val height = (page.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        } finally {
            page.close()
        }
    } finally {
        runCatching { renderer?.close() }
        runCatching { descriptor.close() }
    }
}.getOrNull()
