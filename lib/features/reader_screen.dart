import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pdfrx/pdfrx.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../database/app_database.dart';
import '../core/services/pdf_capture_service.dart';
import '../repositories/study_repository.dart';
import 'annotations/annotation_layer.dart';
import 'study_views.dart';

class ReaderScreen extends StatefulWidget {
  const ReaderScreen({
    super.key,
    required this.repository,
    required this.document,
  });
  final StudyRepository repository;
  final Document document;
  @override
  State<ReaderScreen> createState() => _ReaderScreenState();
}

class _ReaderScreenState extends State<ReaderScreen>
    with WidgetsBindingObserver {
  late Document document;
  final controller = PdfViewerController();
  final pdfCaptureKey = GlobalKey();
  int page = 1;
  int pageCount = 0;
  int panel = 0;
  bool controls = true;
  bool keepAwake = false;
  String annotationTool = 'none';
  double dimAmount = 0;
  bool eyeProtection = false;
  bool pageSound = true;
  Timer? saveTimer;
  @override
  void initState() {
    super.initState();
    document = widget.document;
    page = document.currentPage;
    WidgetsBinding.instance.addObserver(this);
    widget.repository.setting('keepScreenOn').then((mode) {
      if (mounted && mode == 'always') {
        setState(() => keepAwake = true);
        WakelockPlus.enable();
      }
    });
    widget.repository.setting('screenDim').then((value) {
      if (mounted && value != null) {
        setState(() => dimAmount = double.tryParse(value) ?? 0);
      }
    });
    widget.repository.setting('eyeProtection').then((value) {
      if (mounted && value != null) {
        setState(() => eyeProtection = value == 'true');
      }
    });
    widget.repository.setting('pageSound').then((value) {
      if (mounted && value != null) {
        setState(() => pageSound = value != 'false');
      }
    });
  }

  @override
  void dispose() {
    _save();
    WakelockPlus.disable();
    WidgetsBinding.instance.removeObserver(this);
    saveTimer?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) _save();
  }

  void _save() {
    widget.repository.updatePage(
      document,
      page,
      pageCount: pageCount == 0 ? null : pageCount,
      zoom: controller.currentZoom,
    );
  }

  Future<void> _createPostIt() async {
    final input = TextEditingController();
    var flashcard = false;
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Post-it'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: input,
                autofocus: true,
                maxLines: 4,
                decoration: const InputDecoration(hintText: 'Texto'),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Crear flashcard'),
                value: flashcard,
                onChanged: (value) => setDialogState(() => flashcard = value),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Crear'),
            ),
          ],
        ),
      ),
    );
    if (result == true) {
      await widget.repository.addPostIt(
        documentId: document.id,
        page: page,
        content: input.text,
        createFlashcard: flashcard,
      );
    }
    input.dispose();
  }

  void _pageChanged(int value) {
    setState(() => page = value);
    saveTimer?.cancel();
    saveTimer = Timer(const Duration(milliseconds: 700), _save);
  }

  Future<void> _jumpToPage(int target) async {
    if (target < 1 || (pageCount > 0 && target > pageCount)) return;
    await controller.goToPage(
      pageNumber: target,
      duration: const Duration(milliseconds: 180),
    );
    if (pageSound) SystemSound.play(SystemSoundType.click);
  }

  Future<void> _captureForStudy() async {
    try {
      final imagePath = await PdfCaptureService().capture(pdfCaptureKey);
      if (!mounted) return;
      final target = await showModalBottomSheet<String>(
        context: context,
        builder: (context) => SafeArea(
          child: Wrap(
            children: [
              ListTile(
                leading: const Icon(Icons.sticky_note_2_outlined),
                title: const Text('Añadir captura a una nota'),
                onTap: () => Navigator.pop(context, 'note'),
              ),
              ListTile(
                leading: const Icon(Icons.style_outlined),
                title: const Text('Crear flashcard con captura'),
                onTap: () => Navigator.pop(context, 'flashcard'),
              ),
            ],
          ),
        ),
      );
      if (target == 'note') await _captureNote(imagePath);
      if (target == 'flashcard') await _captureFlashcard(imagePath);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('No se pudo capturar el PDF: $error')),
        );
      }
    }
  }

  Future<void> _captureNote(String imagePath) async {
    final input = TextEditingController();
    final accepted = await _textDialog('Nota para esta captura', input);
    if (accepted == true) {
      await widget.repository.addNote(
        document.id,
        page,
        input.text,
        imagePath: imagePath,
      );
    }
    input.dispose();
  }

  Future<void> _captureFlashcard(String imagePath) async {
    final front = TextEditingController(
      text: '¿Qué debo recordar de esta imagen?',
    );
    final back = TextEditingController();
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Flashcard con captura'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: front,
              decoration: const InputDecoration(labelText: 'Pregunta o pista'),
            ),
            TextField(
              controller: back,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Respuesta'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Crear'),
          ),
        ],
      ),
    );
    if (accepted == true) {
      await widget.repository.addFlashcard(
        document.id,
        page,
        front.text,
        back.text,
        imagePath: imagePath,
      );
    }
    front.dispose();
    back.dispose();
  }

  Future<bool?> _textDialog(String title, TextEditingController input) =>
      showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: TextField(
            controller: input,
            autofocus: true,
            maxLines: 4,
            decoration: const InputDecoration(hintText: 'Texto opcional'),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Guardar'),
            ),
          ],
        ),
      );

  Future<void> _showComfortControls() async {
    await showModalBottomSheet<void>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setSheetState) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    const Icon(Icons.brightness_4_outlined),
                    const SizedBox(width: 8),
                    const Text('Atenuación de pantalla'),
                  ],
                ),
                Slider(
                  value: dimAmount,
                  max: .65,
                  divisions: 13,
                  label: '${(dimAmount * 100).round()} %',
                  onChanged: (value) {
                    setState(() => dimAmount = value);
                    setSheetState(() {});
                    widget.repository.setSetting('screenDim', value.toString());
                  },
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Protección ocular'),
                  subtitle: const Text('Tono cálido para lectura nocturna'),
                  value: eyeProtection,
                  onChanged: (value) {
                    setState(() => eyeProtection = value);
                    setSheetState(() {});
                    widget.repository.setSetting(
                      'eyeProtection',
                      value.toString(),
                    );
                  },
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Sonido al pasar página'),
                  value: pageSound,
                  onChanged: (value) {
                    setState(() => pageSound = value);
                    setSheetState(() {});
                    widget.repository.setSetting('pageSound', value.toString());
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pdfContent = File(document.cachePath).existsSync()
        ? PdfViewer.file(
            document.cachePath,
            controller: controller,
            initialPageNumber: page,
            params: PdfViewerParams(
              onViewerReady: (doc, _) {
                setState(() => pageCount = doc.pages.length);
              },
              onPageChanged: (value) {
                if (value != null) _pageChanged(value);
              },
              pageOverlaysBuilder: (_, _, pdfPage) => [
                AnnotationLayer(
                  repository: widget.repository,
                  documentId: document.id,
                  page: pdfPage.pageNumber,
                  tool: annotationTool,
                  onPostIt: _createPostIt,
                ),
              ],
            ),
          )
        : const Center(
            child: Text(
              'Archivo no disponible. Vuelve a localizarlo desde la biblioteca.',
            ),
          );
    final pdf = RepaintBoundary(key: pdfCaptureKey, child: pdfContent);
    final side = [
      NotesPanel(
        repository: widget.repository,
        documentId: document.id,
        page: page,
        onGoToPage: _jumpToPage,
      ),
      SummaryPanel(repository: widget.repository, documentId: document.id),
      IndexPanel(
        repository: widget.repository,
        documentId: document.id,
        onGoToPage: _jumpToPage,
      ),
    ][panel];
    return Scaffold(
      appBar: controls
          ? AppBar(
              title: Text(
                document.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              actions: [
                IconButton(
                  onPressed: () => setState(() => controls = false),
                  icon: const Icon(Icons.fullscreen),
                ),
                PopupMenuButton<String>(
                  itemBuilder: (_) => <PopupMenuEntry<String>>[
                    CheckedPopupMenuItem<String>(
                      value: 'awake',
                      checked: keepAwake,
                      child: const Text('Mantener pantalla encendida'),
                    ),
                    const PopupMenuDivider(),
                    const PopupMenuItem<String>(
                      value: 'highlight',
                      child: Text('Resaltador'),
                    ),
                    const PopupMenuItem<String>(
                      value: 'pencil',
                      child: Text('Lápiz'),
                    ),
                    const PopupMenuItem<String>(
                      value: 'postit',
                      child: Text('Añadir post-it'),
                    ),
                    const PopupMenuItem<String>(
                      value: 'capture',
                      child: Text('Capturar vista para estudiar'),
                    ),
                    const PopupMenuItem<String>(
                      value: 'comfort',
                      child: Text('Comodidad visual'),
                    ),
                    const PopupMenuItem<String>(
                      value: 'none',
                      child: Text('Mover / leer'),
                    ),
                  ],
                  onSelected: (value) {
                    if (value == 'awake') {
                      setState(() => keepAwake = !keepAwake);
                      WakelockPlus.toggle(enable: keepAwake);
                    } else if (value == 'postit') {
                      _createPostIt();
                    } else if (value == 'capture') {
                      _captureForStudy();
                    } else if (value == 'comfort') {
                      _showComfortControls();
                    } else {
                      setState(() => annotationTool = value);
                    }
                  },
                ),
              ],
            )
          : null,
      body: Stack(
        children: [
          GestureDetector(
            onTap: () => setState(() => controls = !controls),
            child: LayoutBuilder(
              builder: (_, c) {
                final split = c.maxWidth >= 760;
                final reader = Column(
                  children: [
                    Expanded(child: pdf),
                    if (controls)
                      Container(
                        padding: const EdgeInsets.all(8),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              onPressed: () => _jumpToPage(page - 1),
                              icon: const Icon(Icons.chevron_left),
                            ),
                            Text(
                              'Página $page${pageCount > 0 ? ' / $pageCount' : ''}',
                            ),
                            if (annotationTool != 'none')
                              Padding(
                                padding: const EdgeInsets.only(left: 8),
                                child: Text(
                                  annotationTool == 'pencil'
                                      ? 'Lápiz activo'
                                      : 'Resaltador activo',
                                ),
                              ),
                            IconButton(
                              onPressed: () => _jumpToPage(page + 1),
                              icon: const Icon(Icons.chevron_right),
                            ),
                          ],
                        ),
                      ),
                  ],
                );
                final panelView = Column(
                  children: [
                    SegmentedButton<int>(
                      segments: const [
                        ButtonSegment(value: 0, label: Text('Notas')),
                        ButtonSegment(value: 1, label: Text('Resumen')),
                        ButtonSegment(value: 2, label: Text('Índice')),
                      ],
                      selected: {panel},
                      onSelectionChanged: (v) =>
                          setState(() => panel = v.first),
                    ),
                    Expanded(child: side),
                  ],
                );
                return split
                    ? Row(
                        children: [
                          Expanded(flex: 3, child: reader),
                          const VerticalDivider(),
                          SizedBox(width: c.maxWidth * .34, child: panelView),
                        ],
                      )
                    : Column(
                        children: [
                          Expanded(child: reader),
                          if (controls) SizedBox(height: 260, child: panelView),
                        ],
                      );
              },
            ),
          ),
          IgnorePointer(
            child: ColoredBox(color: Colors.black.withValues(alpha: dimAmount)),
          ),
          if (eyeProtection)
            IgnorePointer(
              child: ColoredBox(
                color: const Color(0xffff9d45).withValues(alpha: .12),
              ),
            ),
        ],
      ),
    );
  }
}
