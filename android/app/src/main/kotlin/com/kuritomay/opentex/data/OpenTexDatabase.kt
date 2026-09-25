package com.kuritomay.opentex.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Entity(tableName = "documents", indices = [Index(value = ["uri"], unique = true)])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val name: String,
    val type: String,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val progress: Float = 0f,
    val thumbnailPath: String? = null,
    val alias: String? = null,
    val isPinned: Boolean = false,
    val lastOpenedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "document_albums",
    primaryKeys = ["documentId", "albumId"],
    foreignKeys = [
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AlbumEntity::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("albumId")],
)
data class DocumentAlbumEntity(val documentId: Long, val albumId: Long, val position: Int = 0)

@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId"), Index(value = ["documentId", "pageNumber"])],
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val tool: String,
    val color: Long,
    val strokeWidth: Float,
    val opacity: Float,
    val points: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "captures",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId"), Index(value = ["documentId", "pageNumber"])],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val imagePath: String,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId"), Index(value = ["documentId", "pageNumber"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val type: String,
    val title: String,
    val body: String,
    val captureId: Long? = null,
    val tags: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "decks", indices = [Index(value = ["name"], unique = true)])
data class FlashcardDeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deckId"), Index("documentId")],
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val type: String = "basic",
    val frontText: String,
    val frontImage: String? = null,
    val frontLatex: String? = null,
    val backText: String = "",
    val backLatex: String? = null,
    val backExplanation: String? = null,
    val options: String? = null,
    val correctOptionIds: String? = null,
    val documentId: Long? = null,
    val pageNumber: Int? = null,
    val captureId: Long? = null,
    val tags: String? = null,
    val reviewCount: Int = 0,
    val lastRating: String? = null,
    val lastReviewedAt: Long = 0,
    val ease: Float = 2.5f,
    val intervalDays: Int = 0,
    val dueAt: Long = 0,
    val reps: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class FlashcardWithDeck(
    @Embedded val card: FlashcardEntity,
    @Embedded(prefix = "deck_") val deck: DeckRef,
)

data class DeckRef(val deckId: Long, val deckName: String)

@Entity(tableName = "open_tabs", indices = [Index(value = ["documentId"], unique = true)])
data class OpenTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    val lastActiveAt: Long = System.currentTimeMillis(),
)

data class AlbumWithDocuments(
    val id: Long,
    val name: String,
    val coverPath: String?,
    val documentCount: Int,
)

@Dao
interface OpenTexDao {
    @Query("SELECT * FROM documents ORDER BY isPinned DESC, createdAt DESC") fun observeDocuments(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT :limit") fun observeRecent(limit: Int = 24): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE id = :id") suspend fun document(id: Long): DocumentEntity?
    @Query("SELECT * FROM albums ORDER BY createdAt DESC") fun observeAlbums(): Flow<List<AlbumEntity>>
    @Query("SELECT albums.id, albums.name, albums.coverPath, COUNT(document_albums.documentId) AS documentCount FROM albums LEFT JOIN document_albums ON albums.id = document_albums.albumId GROUP BY albums.id ORDER BY albums.createdAt DESC") fun observeAlbumSummaries(): Flow<List<AlbumWithDocuments>>
    @Query("SELECT documents.* FROM documents INNER JOIN document_albums ON documents.id = document_albums.documentId WHERE document_albums.albumId = :albumId ORDER BY document_albums.position, documents.name") fun observeAlbumDocuments(albumId: Long): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM open_tabs ORDER BY position, lastActiveAt DESC") fun observeTabs(): Flow<List<OpenTabEntity>>
    @Query("SELECT documents.* FROM documents INNER JOIN open_tabs ON documents.id = open_tabs.documentId ORDER BY open_tabs.position, open_tabs.lastActiveAt DESC") fun observeTabDocuments(): Flow<List<DocumentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDocument(document: DocumentEntity): Long
    @Insert suspend fun insertAlbum(album: AlbumEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addDocumentToAlbum(entry: DocumentAlbumEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTab(tab: OpenTabEntity): Long
    @Query("DELETE FROM document_albums WHERE documentId = :documentId AND albumId = :albumId") suspend fun removeDocumentFromAlbum(documentId: Long, albumId: Long)
    @Query("DELETE FROM open_tabs WHERE documentId = :documentId") suspend fun closeTab(documentId: Long)
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM open_tabs") suspend fun nextTabPosition(): Int
    @Query("UPDATE documents SET currentPage = :page, zoom = :zoom, progress = :progress, lastOpenedAt = :openedAt WHERE id = :id") suspend fun saveReadingState(id: Long, page: Int, zoom: Float, progress: Float, openedAt: Long)
    @Query("UPDATE open_tabs SET lastActiveAt = :openedAt WHERE documentId = :documentId") suspend fun touchTab(documentId: Long, openedAt: Long)
    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1") suspend fun findByUri(uri: String): DocumentEntity?
    @Query("SELECT * FROM documents WHERE name = :name LIMIT 1") suspend fun findByName(name: String): DocumentEntity?
    @Query("SELECT * FROM annotations WHERE documentId = :documentId AND pageNumber = :pageNumber ORDER BY createdAt") fun observeAnnotations(documentId: Long, pageNumber: Int): Flow<List<AnnotationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAnnotation(annotation: AnnotationEntity): Long
    @Query("DELETE FROM annotations WHERE id = :id") suspend fun deleteAnnotation(id: Long)
    @Insert suspend fun insertCapture(capture: CaptureEntity): Long
    @Query("SELECT * FROM captures WHERE documentId = :documentId ORDER BY createdAt DESC") fun observeCaptures(documentId: Long): Flow<List<CaptureEntity>>
    @Query("DELETE FROM captures WHERE id = :id") suspend fun deleteCapture(id: Long)
    @Query("SELECT * FROM notes WHERE documentId = :documentId ORDER BY pageNumber, createdAt DESC") fun observeNotes(documentId: Long): Flow<List<NoteEntity>>
    @Insert suspend fun insertNote(note: NoteEntity): Long
    @Query("UPDATE notes SET type = :type, title = :title, body = :body, tags = :tags, updatedAt = :updatedAt WHERE id = :id") suspend fun updateNote(id: Long, type: String, title: String, body: String, tags: String?, updatedAt: Long)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: Long)
    @Query("SELECT flashcards.*, decks.id AS deck_deckId, decks.name AS deck_deckName FROM flashcards INNER JOIN decks ON decks.id = flashcards.deckId ORDER BY flashcards.createdAt DESC") fun observeFlashcards(): Flow<List<FlashcardWithDeck>>
    @Query("SELECT * FROM decks ORDER BY name") fun observeDecks(): Flow<List<FlashcardDeckEntity>>
    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1") suspend fun findDeck(name: String): FlashcardDeckEntity?
    @Insert suspend fun insertDeck(deck: FlashcardDeckEntity): Long
    @Insert suspend fun insertFlashcard(card: FlashcardEntity): Long
    @Query("SELECT * FROM flashcards WHERE id = :id") suspend fun flashcard(id: Long): FlashcardEntity?
    @Update suspend fun updateFlashcard(card: FlashcardEntity)
    @Query("DELETE FROM flashcards WHERE id = :id") suspend fun deleteFlashcard(id: Long)
    @Query("SELECT COUNT(*) FROM flashcards WHERE frontImage = :path AND id != :id") suspend fun otherCardsUsingImage(path: String, id: Long): Int
    @Query("UPDATE flashcards SET reviewCount = reviewCount + 1, lastRating = :rating, lastReviewedAt = :at, ease = :ease, intervalDays = :intervalDays, dueAt = :dueAt, reps = :reps, updatedAt = :at WHERE id = :id") suspend fun rateFlashcard(id: Long, rating: String, at: Long, ease: Float, intervalDays: Int, dueAt: Long, reps: Int)
    @Query("UPDATE documents SET isPinned = :pinned WHERE id = :id") suspend fun setPinned(id: Long, pinned: Boolean)
    @Query("UPDATE documents SET alias = :alias WHERE id = :id") suspend fun setAlias(id: Long, alias: String?)
    @Query("DELETE FROM open_tabs WHERE documentId = :documentId") suspend fun deleteTabsForDocument(documentId: Long)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: Long)
}

@Database(entities = [DocumentEntity::class, AlbumEntity::class, DocumentAlbumEntity::class, AnnotationEntity::class, CaptureEntity::class, NoteEntity::class, FlashcardDeckEntity::class, FlashcardEntity::class, OpenTabEntity::class], version = 8, exportSchema = true)
abstract class OpenTexDatabase : RoomDatabase() {
    abstract fun dao(): OpenTexDao
    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN alias TEXT")
                database.execSQL("ALTER TABLE documents ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS annotations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, documentId INTEGER NOT NULL, pageNumber INTEGER NOT NULL, tool TEXT NOT NULL, color INTEGER NOT NULL, strokeWidth REAL NOT NULL, opacity REAL NOT NULL, points TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_documentId ON annotations(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_documentId_pageNumber ON annotations(documentId, pageNumber)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS captures (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, documentId INTEGER NOT NULL, pageNumber INTEGER NOT NULL, left REAL NOT NULL, top REAL NOT NULL, width REAL NOT NULL, height REAL NOT NULL, imagePath TEXT NOT NULL, title TEXT, description TEXT, tags TEXT, createdAt INTEGER NOT NULL, FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_documentId ON captures(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_documentId_pageNumber ON captures(documentId, pageNumber)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, documentId INTEGER NOT NULL, pageNumber INTEGER NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, captureId INTEGER, tags TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_documentId ON notes(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_documentId_pageNumber ON notes(documentId,pageNumber)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS decks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_decks_name ON decks(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS flashcards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, deckId INTEGER NOT NULL, type TEXT NOT NULL, frontText TEXT NOT NULL, frontImage TEXT, frontLatex TEXT, backText TEXT NOT NULL, backLatex TEXT, backExplanation TEXT, options TEXT, correctOptionIds TEXT, documentId INTEGER, pageNumber INTEGER, captureId INTEGER, tags TEXT, reviewCount INTEGER NOT NULL, lastRating TEXT, lastReviewedAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_flashcards_deckId ON flashcards(deckId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_flashcards_documentId ON flashcards(documentId)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN ease REAL NOT NULL DEFAULT 2.5")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN dueAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN reps INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun create(context: Context): OpenTexDatabase = Room.databaseBuilder(context, OpenTexDatabase::class.java, "opentex.db").addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
    }
}

class DocumentRepository(private val context: Context, private val dao: OpenTexDao) {
    val documents = dao.observeDocuments()
    val recent = dao.observeRecent()
    val albums = dao.observeAlbumSummaries()
    val tabs = dao.observeTabDocuments()
    fun albumDocuments(albumId: Long) = dao.observeAlbumDocuments(albumId)
    fun annotations(documentId: Long, pageNumber: Int) = dao.observeAnnotations(documentId, pageNumber)
    fun notes(documentId: Long) = dao.observeNotes(documentId)
    fun captures(documentId: Long) = dao.observeCaptures(documentId)
    val flashcards = dao.observeFlashcards()
    val decks = dao.observeDecks()
    suspend fun document(id: Long) = dao.document(id)

    suspend fun ensureDeck(name: String): Long {
        val clean = name.trim().ifBlank { "General" }
        dao.findDeck(clean)?.let { return it.id }
        return dao.insertDeck(FlashcardDeckEntity(name = clean))
    }

    suspend fun saveFlashcard(card: FlashcardEntity): FlashcardEntity = card.copy(id = dao.insertFlashcard(card))
    suspend fun updateFlashcard(card: FlashcardEntity) = dao.updateFlashcard(card)
    suspend fun flashcard(id: Long) = dao.flashcard(id)
    suspend fun deleteFlashcard(card: FlashcardEntity) {
        dao.deleteFlashcard(card.id)
        val path = card.frontImage ?: return
        if (dao.otherCardsUsingImage(path, card.id) == 0) runCatching { File(path).delete() }
    }
    suspend fun rateFlashcard(id: Long, rating: String) {
        val card = dao.flashcard(id) ?: return
        val now = System.currentTimeMillis()
        val schedule = ReviewScheduler.schedule(card, rating, now)
        dao.rateFlashcard(id, rating, now, schedule.ease, schedule.intervalDays, schedule.dueAt, schedule.reps)
    }
    suspend fun flashcardsSnapshot(): List<FlashcardWithDeck> = dao.observeFlashcards().first()

    suspend fun exportFlashcards(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cards = flashcardsSnapshot()
            val infos = cards.mapNotNull { it.card.documentId }.distinct().associateWith { id -> dao.document(id)?.let { it.name to it.uri } }
            val info: (Long) -> Pair<String, String?>? = { id -> infos[id] }
            val media = LinkedHashMap<String, String>()
            cards.forEach { entry -> entry.card.frontImage?.let { path -> if (File(path).isFile && path !in media) media[path] = "media/card_${media.size}.webp" } }
            val json = FlashcardPort.export(cards, info) { path -> media[path] }
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: return@runCatching false
            output.use { raw ->
                ZipOutputStream(raw).use { zip ->
                    zip.putNextEntry(ZipEntry("flashcards.json"))
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    media.forEach { (path, entryName) ->
                        zip.putNextEntry(ZipEntry(entryName))
                        File(path).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun importFlashcards(uri: Uri): String = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "flashcard_import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("No se pudo leer el archivo.")
            val isZip = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            val text = if (isZip) readZipJson(java.io.ByteArrayInputStream(bytes), temp) else bytes.toString(Charsets.UTF_8)
            val decks = FlashcardPort.parse(text)
            var count = 0
            decks.forEach { deck ->
                val deckId = ensureDeck(deck.name)
                deck.cards.forEach { parsed -> insertParsedCard(deckId, parsed, temp); count++ }
            }
            if (count == 0) throw IllegalArgumentException("El archivo no contiene flashcards.")
            "Se importaron $count flashcards en ${decks.size} mazos."
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun readZipJson(input: ByteArrayInputStream, temp: File): String {
        var json: String? = null
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(temp, entry.name)
                if (target.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                    if (entry.isDirectory) target.mkdirs()
                    else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        if (entry.name.substringAfterLast('/') == "flashcards.json") json = target.readText()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return json ?: throw IllegalArgumentException("El ZIP no contiene flashcards.json.")
    }

    private suspend fun insertParsedCard(deckId: Long, parsed: ParsedCard, temp: File) {
        val imagePath = parsed.image?.let { relative ->
            val source = File(temp, relative)
            if (!source.isFile) null else {
                val directory = File(context.filesDir, "flashcards").apply { mkdirs() }
                val destination = File(directory, "flashcard_${System.currentTimeMillis()}_${source.name}")
                source.copyTo(destination, overwrite = true)
                destination.path
            }
        }
        val document = parsed.sourceUri?.let { dao.findByUri(it) } ?: parsed.sourceDocument?.let { dao.findByName(it) }
        dao.insertFlashcard(
            FlashcardEntity(
                deckId = deckId,
                type = parsed.type,
                frontText = parsed.front,
                frontImage = imagePath,
                frontLatex = parsed.frontLatex,
                backText = parsed.back,
                backLatex = parsed.backLatex,
                backExplanation = parsed.explanation,
                options = parsed.options,
                correctOptionIds = parsed.correctOptionIds,
                documentId = document?.id,
                pageNumber = if (document != null) parsed.sourcePage else null,
                tags = parsed.tags,
            )
        )
    }

    suspend fun import(uri: Uri): DocumentEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val existing = dao.findByUri(uri.toString())
        if (existing != null) { open(existing); return@withContext existing }
        val name = queryName(resolver, uri) ?: "Documento"
        val type = detectType(name, resolver.getType(uri))
        val preview = if (type == "PDF") renderPdfThumbnail(resolver, uri) else null
        val document = DocumentEntity(uri = uri.toString(), name = name, type = type, pageCount = preview?.second ?: 0, thumbnailPath = preview?.first)
        val saved = document.copy(id = dao.insertDocument(document))
        open(saved)
        saved
    }
    suspend fun importAll(uris: List<Uri>): List<DocumentEntity> = uris.distinct().mapNotNull { uri -> runCatching { import(uri) }.getOrNull() }

    suspend fun importTree(tree: Uri): Int = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val rootId = android.provider.DocumentsContract.getTreeDocumentId(tree)
        val uris = collectDocuments(tree, rootId, 0)
        importAll(uris).size
    }

    private fun collectDocuments(tree: Uri, documentId: String, depth: Int): List<Uri> {
        if (depth > 8) return emptyList()
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val found = mutableListOf<Uri>()
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val mime = cursor.getString(1).orEmpty()
                    val name = cursor.getString(2).orEmpty()
                    if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) found += collectDocuments(tree, id, depth + 1)
                    else if (isImportableName(name)) found += android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
        }
        return found
    }

    private fun isImportableName(name: String): Boolean = listOf("pdf", "epub", "txt", "md", "doc", "docx").any { name.endsWith(".$it", ignoreCase = true) }

    suspend fun open(document: DocumentEntity) {
        val now = System.currentTimeMillis()
        dao.saveReadingState(document.id, document.currentPage, document.zoom, document.progress, now)
        dao.insertTab(OpenTabEntity(documentId = document.id, position = dao.nextTabPosition(), lastActiveAt = now))
        dao.touchTab(document.id, now)
    }

    suspend fun closeTab(documentId: Long) = dao.closeTab(documentId)
    suspend fun setPinned(documentId: Long, pinned: Boolean) = dao.setPinned(documentId, pinned)
    suspend fun setAlias(documentId: Long, alias: String) = dao.setAlias(documentId, alias.trim().ifBlank { null })
    suspend fun removeFromLibrary(document: DocumentEntity): Boolean = withContext(Dispatchers.IO) {
        dao.deleteTabsForDocument(document.id)
        dao.deleteDocument(document.id)
        document.thumbnailPath?.let { path -> runCatching { File(path).delete() } }
        true
    }
    fun canDeleteFile(document: DocumentEntity): Boolean = context.checkUriPermission(Uri.parse(document.uri), android.os.Process.myPid(), android.os.Process.myUid(), android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    suspend fun deleteFileAndRemove(document: DocumentEntity): Boolean = withContext(Dispatchers.IO) {
        if (!canDeleteFile(document)) return@withContext false
        val deleted = runCatching { context.contentResolver.delete(Uri.parse(document.uri), null, null) > 0 }.getOrDefault(false)
        if (deleted) removeFromLibrary(document)
        deleted
    }
    suspend fun createAlbum(name: String) { if (name.isNotBlank()) dao.insertAlbum(AlbumEntity(name = name.trim())) }
    suspend fun addToAlbum(documentId: Long, albumId: Long) = dao.addDocumentToAlbum(DocumentAlbumEntity(documentId, albumId))
    suspend fun removeFromAlbum(documentId: Long, albumId: Long) = dao.removeDocumentFromAlbum(documentId, albumId)
    suspend fun saveAnnotation(annotation: AnnotationEntity): AnnotationEntity = annotation.copy(id = dao.insertAnnotation(annotation))
    suspend fun deleteAnnotation(id: Long) = dao.deleteAnnotation(id)
    suspend fun saveCapture(documentId: Long, pageNumber: Int, left: Float, top: Float, width: Float, height: Float, bitmap: Bitmap): CaptureEntity = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "captures").apply { mkdirs() }
        val file = File(directory, "capture_${System.currentTimeMillis()}.webp")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, it) }
        val capture = CaptureEntity(documentId = documentId, pageNumber = pageNumber, left = left, top = top, width = width, height = height, imagePath = file.path)
        capture.copy(id = dao.insertCapture(capture))
    }
    suspend fun deleteCapture(capture: CaptureEntity) {
        dao.deleteCapture(capture.id)
        runCatching { File(capture.imagePath).delete() }
    }
    suspend fun saveNote(note: NoteEntity): NoteEntity {
        val now = System.currentTimeMillis()
        if (note.id != 0L) {
            dao.updateNote(note.id, note.type, note.title, note.body, note.tags, now)
            return note.copy(updatedAt = now)
        }
        return note.copy(id = dao.insertNote(note), createdAt = now, updatedAt = now)
    }
    suspend fun deleteNote(id: Long) = dao.deleteNote(id)
    suspend fun saveState(document: DocumentEntity, page: Int, zoom: Float) {
        val progress = if (document.pageCount <= 1) 1f else page.toFloat() / (document.pageCount - 1).toFloat()
        dao.saveReadingState(document.id, page.coerceAtLeast(0), zoom, progress.coerceIn(0f, 1f), System.currentTimeMillis())
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    private fun detectType(name: String, mime: String?): String = when {
        mime == "application/pdf" || name.endsWith(".pdf", true) -> "PDF"
        name.endsWith(".epub", true) -> "EPUB"
        name.endsWith(".docx", true) -> "DOCX"
        name.endsWith(".doc", true) -> "DOC"
        name.endsWith(".txt", true) -> "TXT"
        name.endsWith(".md", true) -> "MD"
        else -> "DOCUMENTO"
    }
    private fun renderPdfThumbnail(resolver: ContentResolver, uri: Uri): Pair<String, Int>? = runCatching {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: return@runCatching null
        var renderer: PdfRenderer? = null
        try {
            val pdf = PdfRenderer(descriptor).also { renderer = it }
            if (pdf.pageCount == 0) return@runCatching null
            val page = pdf.openPage(0)
            try {
                val width = 360
                val height = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val directory = File(context.filesDir, "thumbnails").apply { mkdirs() }
                val file = File(directory, "${uri.toString().hashCode()}.webp")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, it) }
                file.path to pdf.pageCount
            } finally {
                page.close()
            }
        } finally {
            runCatching { renderer?.close() }
            runCatching { descriptor.close() }
        }
    }.getOrNull()
}
