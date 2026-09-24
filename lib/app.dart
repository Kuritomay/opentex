import 'package:flutter/material.dart';

import 'database/app_database.dart';
import 'features/home_screen.dart';
import 'repositories/study_repository.dart';

class StudyPdfApp extends StatefulWidget {
  const StudyPdfApp({super.key});
  @override
  State<StudyPdfApp> createState() => _StudyPdfAppState();
}

class _StudyPdfAppState extends State<StudyPdfApp> {
  final repository = StudyRepository(AppDatabase());
  @override
  void dispose() {
    repository.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'OpenTex',
    theme: ThemeData(
      brightness: Brightness.dark,
      colorSchemeSeed: const Color(0xff8ab4f8),
      scaffoldBackgroundColor: const Color(0xff101114),
      useMaterial3: true,
    ),
    home: HomeScreen(repository: repository),
  );
}
