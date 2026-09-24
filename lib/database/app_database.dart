import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path_provider/path_provider.dart';

part 'app_database.g.dart';

class Documents extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get uri => text().unique()();
  TextColumn get cachePath => text()();
  TextColumn get name => text()();
  IntColumn get pageCount => integer().withDefault(const Constant(0))();
  IntColumn get currentPage => integer().withDefault(const Constant(1))();
  RealColumn get scrollOffset => real().withDefault(const Constant(0))();
  RealColumn get zoom => real().withDefault(const Constant(1))();
  RealColumn get progress => real().withDefault(const Constant(0))();
  DateTimeColumn get lastOpenedAt => dateTime().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

class Annotations extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get documentId => integer().references(Documents, #id)();
  IntColumn get pageNumber => integer()();
  TextColumn get type => text()();
  RealColumn get x => real().withDefault(const Constant(0))();
  RealColumn get y => real().withDefault(const Constant(0))();
  RealColumn get width => real().withDefault(const Constant(0))();
  RealColumn get height => real().withDefault(const Constant(0))();
  TextColumn get content => text().nullable()();
  TextColumn get drawingData => text().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
}

class Notes extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get documentId => integer().references(Documents, #id)();
  IntColumn get pageNumber => integer()();
  IntColumn get annotationId =>
      integer().nullable().references(Annotations, #id)();
  TextColumn get title => text().withDefault(const Constant('Nota'))();
  TextColumn get content => text()();
  TextColumn get imagePath => text().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
}

class Flashcards extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get documentId => integer().references(Documents, #id)();
  IntColumn get pageNumber => integer()();
  IntColumn get annotationId =>
      integer().nullable().references(Annotations, #id)();
  TextColumn get front => text()();
  TextColumn get back => text()();
  TextColumn get imagePath => text().nullable()();
  IntColumn get difficulty => integer().withDefault(const Constant(0))();
  IntColumn get repetitions => integer().withDefault(const Constant(0))();
  DateTimeColumn get nextReview => dateTime()();
  DateTimeColumn get lastReview => dateTime().nullable()();
}

class Summaries extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get documentId => integer().unique().references(Documents, #id)();
  TextColumn get content => text().withDefault(const Constant(''))();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
}

class Tabs extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get documentId => integer().unique().references(Documents, #id)();
  IntColumn get position => integer()();
  DateTimeColumn get lastActiveAt =>
      dateTime().withDefault(currentDateAndTime)();
}

class DictionaryHistories extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get word => text().unique()();
  DateTimeColumn get searchedAt => dateTime().withDefault(currentDateAndTime)();
  BoolColumn get favorite => boolean().withDefault(const Constant(false))();
}

class Settings extends Table {
  TextColumn get key => text()();
  TextColumn get value => text()();
  @override
  Set<Column> get primaryKey => {key};
}

@DriftDatabase(
  tables: [
    Documents,
    Annotations,
    Notes,
    Flashcards,
    Summaries,
    Tabs,
    DictionaryHistories,
    Settings,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());
  @override
  int get schemaVersion => 2;
  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) async {
      await m.createAll();
      await customStatement(
        'CREATE INDEX notes_document_page ON notes(document_id, page_number)',
      );
      await customStatement(
        'CREATE INDEX annotations_document_page ON annotations(document_id, page_number)',
      );
      await customStatement(
        'CREATE INDEX flashcards_review ON flashcards(next_review)',
      );
    },
    onUpgrade: (m, from, to) async {
      if (from < 2) {
        await m.addColumn(notes, notes.imagePath);
        await m.addColumn(flashcards, flashcards.imagePath);
      }
    },
    beforeOpen: (details) async => customStatement('PRAGMA foreign_keys = ON'),
  );
}

LazyDatabase _openConnection() => LazyDatabase(() async {
  final directory = await getApplicationDocumentsDirectory();
  return NativeDatabase(File('${directory.path}/study_pdf.sqlite'));
});
