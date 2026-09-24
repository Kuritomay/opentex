import 'package:flutter/services.dart';

class PickedDocument {
  const PickedDocument({
    required this.uri,
    required this.name,
    required this.cachePath,
  });

  final String uri;
  final String name;
  final String cachePath;
}

class DocumentPicker {
  static const _channel = MethodChannel('com.localstudy.study_pdf/documents');

  Future<PickedDocument?> pickPdf() async {
    final result = await _channel.invokeMapMethod<String, dynamic>('pickPdf');
    if (result == null) return null;
    return PickedDocument(
      uri: result['uri'] as String,
      name: result['name'] as String,
      cachePath: result['cachePath'] as String,
    );
  }
}
