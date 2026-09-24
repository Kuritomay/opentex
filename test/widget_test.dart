import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:study_pdf/database/app_database.dart';
import 'package:study_pdf/features/home_screen.dart';

void main() {
  testWidgets('document tile shows reading progress', (tester) async {
    final document = Document(
      id: 1,
      uri: 'content://test',
      cachePath: '/tmp/test.pdf',
      name: 'Algebra.pdf',
      pageCount: 100,
      currentPage: 37,
      scrollOffset: 0,
      zoom: 1,
      progress: .37,
      lastOpenedAt: null,
      createdAt: DateTime(2026),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: DocumentTile(document: document, onTap: () {}),
      ),
    );
    expect(find.text('Algebra.pdf'), findsOneWidget);
    expect(find.textContaining('Página 37 / 100'), findsOneWidget);
  });
}
