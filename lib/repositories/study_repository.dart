import 'package:drift/drift.dart';

import '../core/services/document_picker.dart';
import '../database/app_database.dart';

class StudyRepository {
  StudyRepository(this.db);
  final AppDatabase db;

  Stream<List<Document>> watchLibrary() => (db.select(
    db.documents,
  )..orderBy([(d) => OrderingTerm.desc(d.lastOpenedAt)])).watch();
  Stream<List<Document>> watchRecent() =>
      (db.select(db.documents)
            ..orderBy([(d) => OrderingTerm.desc(d.lastOpenedAt)])
            ..limit(20))
          .watch();
  Stream<List<Tab>> watchTabs() => (db.select(
    db.tabs,
  )..orderBy([(t) => OrderingTerm.asc(t.position)])).watch();
  Stream<List<Note>> watchNotes(int documentId) =>
      (db.select(db.notes)
            ..where((n) => n.documentId.equals(documentId))
            ..orderBy([(n) => OrderingTerm.asc(n.pageNumber)]))
          .watch();
  Stream<List<Flashcard>> watchFlashcards() => (db.select(
    db.flashcards,
  )..orderBy([(f) => OrderingTerm.asc(f.nextReview)])).watch();
  Stream<List<Annotation>> watchAnnotations(int documentId) =>
      (db.select(db.annotations)
            ..where((a) => a.documentId.equals(documentId))
            ..orderBy([(a) => OrderingTerm.asc(a.pageNumber)]))
          .watch();
  Stream<List<DictionaryHistory>> watchDictionaryHistory() => (db.select(
    db.dictionaryHistories,
  )..orderBy([(d) => OrderingTerm.desc(d.searchedAt)])).watch();

  Future<Document> addDocument(PickedDocument picked) async {
    final existing = await (db.select(
      db.documents,
    )..where((d) => d.uri.equals(picked.uri))).getSingleOrNull();
    if (existing != null) {
      await touchDocument(existing.id);
      return (db.select(
        db.documents,
      )..where((d) => d.id.equals(existing.id))).getSingle();
    }
    final id = await db
        .into(db.documents)
        .insert(
          DocumentsCompanion.insert(
            uri: picked.uri,
            cachePath: picked.cachePath,
            name: picked.name,
            lastOpenedAt: Value(DateTime.now()),
          ),
        );
    return (db.select(db.documents)..where((d) => d.id.equals(id))).getSingle();
  }

  Future<void> touchDocument(int id) =>
      (db.update(db.documents)..where((d) => d.id.equals(id))).write(
        DocumentsCompanion(lastOpenedAt: Value(DateTime.now())),
      );
  Future<Document?> documentById(int id) => (db.select(
    db.documents,
  )..where((d) => d.id.equals(id))).getSingleOrNull();

  Future<void> updatePage(
    Document document,
    int page, {
    int? pageCount,
    double? zoom,
    double? scrollOffset,
  }) => (db.update(db.documents)..where((d) => d.id.equals(document.id))).write(
    DocumentsCompanion(
      currentPage: Value(page),
      pageCount: pageCount == null ? const Value.absent() : Value(pageCount),
      progress: Value(
        pageCount == null || pageCount == 0
            ? document.progress
            : page / pageCount,
      ),
      zoom: zoom == null ? const Value.absent() : Value(zoom),
      scrollOffset: scrollOffset == null
          ? const Value.absent()
          : Value(scrollOffset),
      lastOpenedAt: Value(DateTime.now()),
    ),
  );

  Future<void> openTab(int documentId) async {
    final current = await (db.select(
      db.tabs,
    )..where((t) => t.documentId.equals(documentId))).getSingleOrNull();
    if (current != null) {
      await (db.update(db.tabs)..where((t) => t.id.equals(current.id))).write(
        TabsCompanion(lastActiveAt: Value(DateTime.now())),
      );
      return;
    }
    final count = await db.select(db.tabs).get().then((items) => items.length);
    if (count >= 10) {
      final oldest =
          await (db.select(db.tabs)
                ..orderBy([(t) => OrderingTerm.asc(t.lastActiveAt)])
                ..limit(1))
              .getSingle();
      await (db.delete(db.tabs)..where((t) => t.id.equals(oldest.id))).go();
    }
    await db
        .into(db.tabs)
        .insert(TabsCompanion.insert(documentId: documentId, position: count));
  }

  Future<void> closeTab(int documentId) =>
      (db.delete(db.tabs)..where((t) => t.documentId.equals(documentId))).go();

  Future<void> addNote(
    int documentId,
    int page,
    String content, {
    String? imagePath,
  }) async {
    if (content.trim().isEmpty && imagePath == null) return;
    await db
        .into(db.notes)
        .insert(
          NotesCompanion.insert(
            documentId: documentId,
            pageNumber: page,
            content: content.trim().isEmpty
                ? 'Captura de la página $page'
                : content.trim(),
            imagePath: Value(imagePath),
          ),
        );
  }

  Future<int> addAnnotation({
    required int documentId,
    required int page,
    required String type,
    required double x,
    required double y,
    required double width,
    required double height,
    String? content,
    String? drawingData,
  }) => db
      .into(db.annotations)
      .insert(
        AnnotationsCompanion.insert(
          documentId: documentId,
          pageNumber: page,
          type: type,
          x: Value(x),
          y: Value(y),
          width: Value(width),
          height: Value(height),
          content: Value(content),
          drawingData: Value(drawingData),
        ),
      );

  Future<void> addPostIt({
    required int documentId,
    required int page,
    required String content,
    required bool createFlashcard,
  }) async {
    final annotationId = await addAnnotation(
      documentId: documentId,
      page: page,
      type: 'postit',
      x: .42,
      y: .42,
      width: .25,
      height: .16,
      content: content.trim(),
    );
    if (content.trim().isEmpty) return;
    if (createFlashcard) {
      await db
          .into(db.flashcards)
          .insert(
            FlashcardsCompanion.insert(
              documentId: documentId,
              pageNumber: page,
              annotationId: Value(annotationId),
              front: content.trim(),
              back: 'Añade la respuesta al editar la tarjeta.',
              nextReview: DateTime.now(),
            ),
          );
    } else {
      await db
          .into(db.notes)
          .insert(
            NotesCompanion.insert(
              documentId: documentId,
              pageNumber: page,
              annotationId: Value(annotationId),
              content: content.trim(),
            ),
          );
    }
  }

  Future<void> addFlashcard(
    int documentId,
    int page,
    String front,
    String back, {
    String? imagePath,
  }) async {
    if ((front.trim().isEmpty && imagePath == null) || back.trim().isEmpty) {
      return;
    }
    await db
        .into(db.flashcards)
        .insert(
          FlashcardsCompanion.insert(
            documentId: documentId,
            pageNumber: page,
            front: front.trim().isEmpty
                ? 'Identifica esta captura'
                : front.trim(),
            back: back.trim(),
            imagePath: Value(imagePath),
            nextReview: DateTime.now(),
          ),
        );
  }

  Future<void> review(Flashcard card, int quality) {
    final days = [1, 1, 2, 4, 7][quality.clamp(0, 4)] * (card.repetitions + 1);
    return (db.update(db.flashcards)..where((f) => f.id.equals(card.id))).write(
      FlashcardsCompanion(
        difficulty: Value(quality),
        repetitions: Value(card.repetitions + 1),
        lastReview: Value(DateTime.now()),
        nextReview: Value(DateTime.now().add(Duration(days: days))),
      ),
    );
  }

  Future<Summary?> summary(int documentId) => (db.select(
    db.summaries,
  )..where((s) => s.documentId.equals(documentId))).getSingleOrNull();
  Future<void> saveSummary(int documentId, String content) => db
      .into(db.summaries)
      .insertOnConflictUpdate(
        SummariesCompanion.insert(
          documentId: documentId,
          content: Value(content),
          updatedAt: Value(DateTime.now()),
        ),
      );
  Future<void> setSetting(String key, String value) => db
      .into(db.settings)
      .insertOnConflictUpdate(SettingsCompanion.insert(key: key, value: value));
  Future<String?> setting(String key) =>
      (db.select(db.settings)..where((s) => s.key.equals(key)))
          .getSingleOrNull()
          .then((entry) => entry?.value);
  Future<void> searchDictionary(String word) async {
    final normalized = word.trim().toLowerCase();
    if (normalized.isEmpty) return;
    final old = await (db.select(
      db.dictionaryHistories,
    )..where((d) => d.word.equals(normalized))).getSingleOrNull();
    await db
        .into(db.dictionaryHistories)
        .insertOnConflictUpdate(
          DictionaryHistoriesCompanion.insert(
            word: normalized,
            searchedAt: Value(DateTime.now()),
            favorite: Value(old?.favorite ?? false),
          ),
        );
  }

  Future<void> toggleDictionaryFavorite(DictionaryHistory entry) =>
      (db.update(
        db.dictionaryHistories,
      )..where((d) => d.id.equals(entry.id))).write(
        DictionaryHistoriesCompanion(favorite: Value(!entry.favorite)),
      );
  Future<void> dispose() => db.close();
}
