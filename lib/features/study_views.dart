// ignore_for_file: deprecated_member_use

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

import '../database/app_database.dart';
import '../repositories/study_repository.dart';

class NotesPanel extends StatefulWidget {
  const NotesPanel({
    super.key,
    required this.repository,
    required this.documentId,
    required this.page,
    required this.onGoToPage,
  });
  final StudyRepository repository;
  final int documentId;
  final int page;
  final Future<void> Function(int) onGoToPage;
  @override
  State<NotesPanel> createState() => _NotesPanelState();
}

class _NotesPanelState extends State<NotesPanel> {
  final input = TextEditingController();
  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.all(8),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: input,
                decoration: InputDecoration(
                  hintText: 'Nota para página ${widget.page}',
                  isDense: true,
                ),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.add_comment),
              onPressed: () async {
                await widget.repository.addNote(
                  widget.documentId,
                  widget.page,
                  input.text,
                );
                input.clear();
              },
            ),
          ],
        ),
      ),
      Expanded(
        child: StreamBuilder<List<Note>>(
          stream: widget.repository.watchNotes(widget.documentId),
          builder: (_, s) {
            final notes = s.data ?? [];
            return ListView(
              children: [
                for (final n in notes)
                  ListTile(
                    dense: true,
                    leading:
                        n.imagePath != null && File(n.imagePath!).existsSync()
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: Image.file(
                              File(n.imagePath!),
                              width: 42,
                              height: 42,
                              fit: BoxFit.cover,
                            ),
                          )
                        : Text('p.${n.pageNumber}'),
                    title: Text(
                      n.content,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    onTap: () => widget.onGoToPage(n.pageNumber),
                  ),
              ],
            );
          },
        ),
      ),
    ],
  );
}

class SummaryPanel extends StatefulWidget {
  const SummaryPanel({
    super.key,
    required this.repository,
    required this.documentId,
  });
  final StudyRepository repository;
  final int documentId;
  @override
  State<SummaryPanel> createState() => _SummaryPanelState();
}

class _SummaryPanelState extends State<SummaryPanel> {
  final input = TextEditingController();
  Timer? timer;
  bool loaded = false;
  @override
  void dispose() {
    timer?.cancel();
    widget.repository.saveSummary(widget.documentId, input.text);
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!loaded) {
      widget.repository.summary(widget.documentId).then((v) {
        if (mounted) {
          input.text = v?.content ?? '';
          setState(() => loaded = true);
        }
      });
    }
    return Padding(
      padding: const EdgeInsets.all(8),
      child: TextField(
        controller: input,
        expands: true,
        maxLines: null,
        textAlignVertical: TextAlignVertical.top,
        decoration: const InputDecoration(
          border: OutlineInputBorder(),
          hintText: 'Escribe tu resumen. Se guarda automáticamente.',
        ),
        onChanged: (value) {
          timer?.cancel();
          timer = Timer(
            const Duration(milliseconds: 600),
            () => widget.repository.saveSummary(widget.documentId, value),
          );
        },
      ),
    );
  }
}

class IndexPanel extends StatelessWidget {
  const IndexPanel({
    super.key,
    required this.repository,
    required this.documentId,
    required this.onGoToPage,
  });
  final StudyRepository repository;
  final int documentId;
  final Future<void> Function(int) onGoToPage;
  @override
  Widget build(BuildContext context) => StreamBuilder<List<Note>>(
    stream: repository.watchNotes(documentId),
    builder: (_, s) {
      final notes = s.data ?? [];
      if (notes.isEmpty) {
        return const Center(child: Text('Aún no hay anotaciones.'));
      }
      return ListView(
        children: [
          for (final n in notes)
            ListTile(
              leading: const Icon(Icons.sticky_note_2_outlined),
              title: Text('p.${n.pageNumber}'),
              subtitle: Text(
                n.content,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              onTap: () => onGoToPage(n.pageNumber),
            ),
        ],
      );
    },
  );
}

class FlashcardsView extends StatefulWidget {
  const FlashcardsView({
    super.key,
    required this.repository,
    required this.onOpenDocument,
  });
  final StudyRepository repository;
  final Future<void> Function(int documentId) onOpenDocument;
  @override
  State<FlashcardsView> createState() => _FlashcardsViewState();
}

class _FlashcardsViewState extends State<FlashcardsView> {
  bool answer = false;
  @override
  Widget build(BuildContext context) => StreamBuilder<List<Flashcard>>(
    stream: widget.repository.watchFlashcards(),
    builder: (_, s) {
      final cards = s.data ?? [];
      if (cards.isEmpty) {
        return const Center(
          child: Text('Crea flashcards desde una nota próximamente.'),
        );
      }
      final card = cards.first;
      return Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'p.${card.pageNumber}',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            TextButton.icon(
              onPressed: () => widget.onOpenDocument(card.documentId),
              icon: const Icon(Icons.picture_as_pdf_outlined),
              label: const Text('Ver en PDF'),
            ),
            Expanded(
              child: Card(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (card.imagePath != null &&
                        File(card.imagePath!).existsSync())
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Image.file(
                            File(card.imagePath!),
                            fit: BoxFit.contain,
                          ),
                        ),
                      ),
                    Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(
                        answer ? card.back : card.front,
                        style: Theme.of(context).textTheme.headlineSmall,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (!answer)
              FilledButton(
                onPressed: () => setState(() => answer = true),
                child: const Text('Ver respuesta'),
              )
            else
              Wrap(
                alignment: WrapAlignment.spaceEvenly,
                children: [
                  for (var i = 0; i < 4; i++)
                    TextButton(
                      onPressed: () async {
                        await widget.repository.review(card, i);
                        setState(() => answer = false);
                      },
                      child: Text(['Otra vez', 'Difícil', 'Bien', 'Fácil'][i]),
                    ),
                ],
              ),
          ],
        ),
      );
    },
  );
}

class DictionaryView extends StatefulWidget {
  const DictionaryView({super.key, required this.repository});
  final StudyRepository repository;
  @override
  State<DictionaryView> createState() => _DictionaryViewState();
}

class _DictionaryViewState extends State<DictionaryView> {
  final input = TextEditingController();
  String? result;
  static const words = {
    'abstracción':
        'Proceso de identificar las características esenciales de un concepto.',
    'teorema': 'Proposición demostrable a partir de axiomas y reglas lógicas.',
    'algoritmo': 'Conjunto finito de pasos para resolver un problema.',
  };
  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      children: [
        TextField(
          controller: input,
          decoration: InputDecoration(
            hintText: 'Buscar palabra...',
            suffixIcon: IconButton(
              icon: const Icon(Icons.search),
              onPressed: () async {
                final word = input.text.trim().toLowerCase();
                await widget.repository.searchDictionary(word);
                if (mounted) {
                  setState(
                    () => result =
                        words[word] ?? 'No se encontró una definición local.',
                  );
                }
              },
            ),
          ),
        ),
        const SizedBox(height: 24),
        if (result != null)
          Align(alignment: Alignment.centerLeft, child: Text(result!)),
        const SizedBox(height: 16),
        Expanded(
          child: StreamBuilder<List<DictionaryHistory>>(
            stream: widget.repository.watchDictionaryHistory(),
            builder: (_, snapshot) => ListView(
              children: [
                for (final entry in snapshot.data ?? [])
                  ListTile(
                    dense: true,
                    title: Text(entry.word),
                    leading: IconButton(
                      icon: Icon(
                        entry.favorite ? Icons.star : Icons.star_border,
                      ),
                      onPressed: () =>
                          widget.repository.toggleDictionaryFavorite(entry),
                    ),
                    onTap: () {
                      input.text = entry.word;
                      setState(
                        () => result =
                            words[entry.word] ??
                            'No se encontró una definición local.',
                      );
                    },
                  ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}

class SettingsView extends StatefulWidget {
  const SettingsView({super.key, required this.repository});
  final StudyRepository repository;
  @override
  State<SettingsView> createState() => _SettingsViewState();
}

class _SettingsViewState extends State<SettingsView> {
  String keepScreenOn = 'manual';
  @override
  void initState() {
    super.initState();
    widget.repository.setting('keepScreenOn').then((value) {
      if (mounted && value != null) setState(() => keepScreenOn = value);
    });
  }

  @override
  Widget build(BuildContext context) => ListView(
    children: [
      ListTile(
        title: Text('Datos locales'),
        subtitle: Text(
          'Los PDF, notas y progreso permanecen en este dispositivo.',
        ),
      ),
      ListTile(
        title: Text('Pantalla encendida'),
        subtitle: Text('Controla el wakelock durante la lectura.'),
      ),
      RadioListTile<String>(
        value: 'always',
        groupValue: keepScreenOn,
        title: const Text('Siempre mientras leo'),
        onChanged: _setKeepScreenOn,
      ),
      RadioListTile<String>(
        value: 'manual',
        groupValue: keepScreenOn,
        title: const Text('Solo cuando lo active manualmente'),
        onChanged: _setKeepScreenOn,
      ),
      RadioListTile<String>(
        value: 'never',
        groupValue: keepScreenOn,
        title: const Text('Comportamiento normal del teléfono'),
        onChanged: _setKeepScreenOn,
      ),
    ],
  );

  void _setKeepScreenOn(String? value) {
    if (value == null) return;
    setState(() => keepScreenOn = value);
    widget.repository.setSetting('keepScreenOn', value);
  }
}
