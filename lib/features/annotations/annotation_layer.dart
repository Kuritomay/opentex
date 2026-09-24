import 'dart:convert';

import 'package:flutter/material.dart';

import '../../database/app_database.dart';
import '../../repositories/study_repository.dart';

class AnnotationLayer extends StatefulWidget {
  const AnnotationLayer({
    super.key,
    required this.repository,
    required this.documentId,
    required this.page,
    required this.tool,
    required this.onPostIt,
  });
  final StudyRepository repository;
  final int documentId;
  final int page;
  final String tool;
  final VoidCallback onPostIt;

  @override
  State<AnnotationLayer> createState() => _AnnotationLayerState();
}

class _AnnotationLayerState extends State<AnnotationLayer> {
  Offset? start;
  final points = <Offset>[];

  Future<void> _finish(Size size) async {
    final first = start;
    if (first == null || points.isEmpty || widget.tool == 'none') return;
    final last = points.last;
    double normalizedX(Offset point) => (point.dx / size.width).clamp(0, 1);
    double normalizedY(Offset point) => (point.dy / size.height).clamp(0, 1);
    if (widget.tool == 'pencil') {
      await widget.repository.addAnnotation(
        documentId: widget.documentId,
        page: widget.page,
        type: 'pencil',
        x: normalizedX(first),
        y: normalizedY(first),
        width: 0,
        height: 0,
        drawingData: jsonEncode(
          points
              .map((point) => [normalizedX(point), normalizedY(point)])
              .toList(),
        ),
      );
    } else {
      await widget.repository.addAnnotation(
        documentId: widget.documentId,
        page: widget.page,
        type: 'highlight',
        x: normalizedX(
          Offset(
            first.dx < last.dx ? first.dx : last.dx,
            first.dy < last.dy ? first.dy : last.dy,
          ),
        ),
        y: normalizedY(
          Offset(
            first.dx < last.dx ? first.dx : last.dx,
            first.dy < last.dy ? first.dy : last.dy,
          ),
        ),
        width: ((last.dx - first.dx).abs() / size.width).clamp(.015, 1),
        height: ((last.dy - first.dy).abs() / size.height).clamp(.015, 1),
      );
    }
  }

  @override
  Widget build(BuildContext context) => StreamBuilder<List<Annotation>>(
    stream: widget.repository.watchAnnotations(widget.documentId),
    builder: (_, snapshot) => LayoutBuilder(
      builder: (_, constraints) {
        final size = constraints.biggest;
        final annotations = (snapshot.data ?? [])
            .where((annotation) => annotation.pageNumber == widget.page)
            .toList();
        return GestureDetector(
          behavior: HitTestBehavior.translucent,
          onLongPress: widget.tool == 'none' ? widget.onPostIt : null,
          onPanStart: widget.tool == 'none'
              ? null
              : (details) => setState(() {
                  start = details.localPosition;
                  points
                    ..clear()
                    ..add(details.localPosition);
                }),
          onPanUpdate: widget.tool == 'none'
              ? null
              : (details) => setState(() => points.add(details.localPosition)),
          onPanEnd: widget.tool == 'none'
              ? null
              : (_) async {
                  await _finish(size);
                  if (mounted) setState(() => start = null);
                },
          child: CustomPaint(
            painter: _AnnotationPainter(annotations, points, size),
            child: const SizedBox.expand(),
          ),
        );
      },
    ),
  );
}

class _AnnotationPainter extends CustomPainter {
  const _AnnotationPainter(this.annotations, this.preview, this.size);
  final List<Annotation> annotations;
  final List<Offset> preview;
  final Size size;

  @override
  void paint(Canvas canvas, Size canvasSize) {
    for (final annotation in annotations) {
      if (annotation.type == 'highlight') {
        canvas.drawRect(
          Rect.fromLTWH(
            annotation.x * size.width,
            annotation.y * size.height,
            annotation.width * size.width,
            annotation.height * size.height,
          ),
          Paint()..color = Colors.amber.withValues(alpha: .34),
        );
      } else if (annotation.type == 'postit') {
        final rect = Rect.fromLTWH(
          annotation.x * size.width,
          annotation.y * size.height,
          annotation.width * size.width,
          annotation.height * size.height,
        );
        canvas.drawRect(
          rect,
          Paint()..color = const Color(0xfff4d35e).withValues(alpha: .9),
        );
        final painter = TextPainter(
          text: TextSpan(
            text: annotation.content ?? 'Nota',
            style: const TextStyle(color: Colors.black, fontSize: 11),
          ),
          maxLines: 4,
          ellipsis: '...',
          textDirection: TextDirection.ltr,
        )..layout(maxWidth: rect.width - 8);
        painter.paint(canvas, rect.topLeft + const Offset(4, 4));
      } else if (annotation.type == 'pencil' &&
          annotation.drawingData != null) {
        final values = jsonDecode(annotation.drawingData!) as List<dynamic>;
        if (values.length > 1) {
          final path = Path();
          for (var i = 0; i < values.length; i++) {
            final value = values[i] as List<dynamic>;
            final point = Offset(
              (value[0] as num).toDouble() * size.width,
              (value[1] as num).toDouble() * size.height,
            );
            if (i == 0) {
              path.moveTo(point.dx, point.dy);
            } else {
              path.lineTo(point.dx, point.dy);
            }
          }
          canvas.drawPath(
            path,
            Paint()
              ..color = Colors.redAccent
              ..strokeWidth = 2
              ..style = PaintingStyle.stroke,
          );
        }
      }
    }
    if (preview.length > 1) {
      final path = Path()..moveTo(preview.first.dx, preview.first.dy);
      for (final point in preview.skip(1)) {
        path.lineTo(point.dx, point.dy);
      }
      canvas.drawPath(
        path,
        Paint()
          ..color = Colors.cyanAccent
          ..strokeWidth = 2
          ..style = PaintingStyle.stroke,
      );
    }
  }

  @override
  bool shouldRepaint(_AnnotationPainter oldDelegate) => true;
}
