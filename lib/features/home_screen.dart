import 'package:flutter/material.dart';

import '../core/services/document_picker.dart';
import '../database/app_database.dart' hide Tab;
import '../database/app_database.dart' as database show Tab;
import '../repositories/study_repository.dart';
import 'reader_screen.dart';
import 'study_views.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.repository});
  final StudyRepository repository;
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int section = 0;
  Future<void> _openPicker() async {
    try {
      final picked = await DocumentPicker().pickPdf();
      if (picked == null || !mounted) return;
      final document = await widget.repository.addDocument(picked);
      await widget.repository.openTab(document.id);
      if (mounted) {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) =>
                ReaderScreen(repository: widget.repository, document: document),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('No se pudo abrir el PDF: $e')));
      }
    }
  }

  Future<void> _open(Document document) async {
    await widget.repository.touchDocument(document.id);
    await widget.repository.openTab(document.id);
    if (mounted) {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) =>
              ReaderScreen(repository: widget.repository, document: document),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      LibraryView(repository: widget.repository, onOpen: _open),
      RecentView(repository: widget.repository, onOpen: _open),
      FlashcardsView(repository: widget.repository, onOpenDocument: _openById),
      DictionaryView(repository: widget.repository),
      SettingsView(repository: widget.repository),
    ];
    return Scaffold(
      appBar: AppBar(
        title: Text(
          [
            'Biblioteca',
            'Recientes',
            'Flashcards',
            'Diccionario',
            'Ajustes',
          ][section],
        ),
        actions: section < 2
            ? [IconButton(onPressed: _openPicker, icon: const Icon(Icons.add))]
            : null,
      ),
      body: Column(
        children: [
          if (section == 0)
            OpenTabsStrip(repository: widget.repository, onOpen: _open),
          Expanded(child: pages[section]),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: section,
        onDestinationSelected: (v) => setState(() => section = v),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.library_books_outlined),
            selectedIcon: Icon(Icons.library_books),
            label: 'Biblioteca',
          ),
          NavigationDestination(icon: Icon(Icons.history), label: 'Recientes'),
          NavigationDestination(
            icon: Icon(Icons.style_outlined),
            label: 'Flashcards',
          ),
          NavigationDestination(
            icon: Icon(Icons.menu_book_outlined),
            label: 'Diccionario',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            label: 'Ajustes',
          ),
        ],
      ),
      floatingActionButton: section == 0
          ? FloatingActionButton.extended(
              onPressed: _openPicker,
              icon: const Icon(Icons.picture_as_pdf),
              label: const Text('Abrir PDF'),
            )
          : null,
    );
  }

  Future<void> _openById(int documentId) async {
    final document = await widget.repository.documentById(documentId);
    if (document != null) await _open(document);
  }
}

class OpenTabsStrip extends StatelessWidget {
  const OpenTabsStrip({
    super.key,
    required this.repository,
    required this.onOpen,
  });
  final StudyRepository repository;
  final Future<void> Function(Document) onOpen;

  @override
  Widget build(BuildContext context) => StreamBuilder<List<database.Tab>>(
    stream: repository.watchTabs(),
    builder: (_, tabsSnapshot) => StreamBuilder<List<Document>>(
      stream: repository.watchLibrary(),
      builder: (_, documentsSnapshot) {
        final documents = {
          for (final d in documentsSnapshot.data ?? []) d.id: d,
        };
        final tabs = tabsSnapshot.data ?? [];
        if (tabs.isEmpty) return const SizedBox.shrink();
        return SizedBox(
          height: 46,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            children: [
              for (final tab in tabs)
                if (documents[tab.documentId] case final document?)
                  Padding(
                    padding: const EdgeInsets.only(right: 6),
                    child: InputChip(
                      label: Text(
                        document.name,
                        overflow: TextOverflow.ellipsis,
                      ),
                      avatar: const Icon(Icons.picture_as_pdf, size: 16),
                      onPressed: () => onOpen(document),
                      onDeleted: () => repository.closeTab(document.id),
                    ),
                  ),
            ],
          ),
        );
      },
    ),
  );
}

class LibraryView extends StatelessWidget {
  const LibraryView({
    super.key,
    required this.repository,
    required this.onOpen,
  });
  final StudyRepository repository;
  final Future<void> Function(Document) onOpen;
  @override
  Widget build(BuildContext context) => StreamBuilder<List<Document>>(
    stream: repository.watchLibrary(),
    builder: (_, snapshot) {
      final docs = snapshot.data ?? [];
      if (docs.isEmpty) {
        return const Center(
          child: Text('Abre un PDF para comenzar a estudiar.'),
        );
      }
      return ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: docs.length,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (_, i) =>
            DocumentTile(document: docs[i], onTap: () => onOpen(docs[i])),
      );
    },
  );
}

class RecentView extends StatelessWidget {
  const RecentView({super.key, required this.repository, required this.onOpen});
  final StudyRepository repository;
  final Future<void> Function(Document) onOpen;
  @override
  Widget build(BuildContext context) => StreamBuilder<List<Document>>(
    stream: repository.watchRecent(),
    builder: (_, s) {
      final docs = s.data ?? [];
      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          for (final d in docs)
            DocumentTile(document: d, onTap: () => onOpen(d)),
        ],
      );
    },
  );
}

class DocumentTile extends StatelessWidget {
  const DocumentTile({super.key, required this.document, required this.onTap});
  final Document document;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      onTap: onTap,
      leading: const Icon(Icons.picture_as_pdf, size: 34),
      title: Text(document.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        'Página ${document.currentPage}${document.pageCount > 0 ? ' / ${document.pageCount}' : ''}  •  ${(document.progress * 100).round()} %',
      ),
      trailing: const Icon(Icons.chevron_right),
    ),
  );
}
