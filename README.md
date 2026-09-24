# OpenTex

Lector Android de PDF offline para estudio. OpenTex usa Flutter, Drift/SQLite, `pdfrx` y `wakelock_plus`; no tiene cuenta, servidor, analítica ni conexión de red.

## Ejecutar

Requiere Flutter 3.47+, Android SDK con cmdline-tools y licencias aceptadas.

```bash
flutter pub get
dart run build_runner build
flutter run
```

## APK

```bash
flutter build apk --release
./android/gradlew -p android :app:copyOpenTexApk
```

La APK con nombre de distribución estará en `build/app/outputs/flutter-apk/opentex.apk`.

## Instalar la APK

1. Descarga `opentex.apk` desde la sección **Releases** del repositorio de GitHub.
2. En Android, permite instalar aplicaciones desde el navegador o gestor de archivos que hayas usado para descargarla.
3. Abre el archivo y confirma la instalación.
4. Abre **OpenTex** desde el lanzador de aplicaciones y selecciona `Abrir PDF`.

La URL estable de la última versión publicada será:

`https://github.com/Kuritomay/opentex/releases/latest/download/opentex.apk`

> OpenTex funciona sin conexión. El permiso que solicita al abrir un PDF es acceso de lectura persistente al archivo elegido mediante el selector de documentos de Android.

## Arquitectura

- `core/services`: selector Android SAF con permisos de lectura persistentes.
- `database`: tablas Drift, claves foráneas, índices y SQLite local.
- `repositories`: acceso a datos, progreso y autoguardado.
- `features/library`: biblioteca y recientes (actualmente compartidos en `home_screen.dart`).
- `features/reader`: lector PDF bajo demanda y estado de lectura.
- `features/annotations`: capa independiente de anotaciones normalizadas.
- `features/notes`, `features/flashcards`, `features/dictionary`, `features/settings`: paneles de estudio (actualmente agrupados en `study_views.dart`).

## Implementado

- Biblioteca, recientes, selector SAF y URI persistente. El renderer usa una caché temporal reemplazable porque requiere una ruta de archivo; el PDF original no se modifica.
- Lector bajo demanda con zoom, navegación por página, pantalla completa y guardado automático de página/zoom al cambiar, perder foco y cerrar.
- Pestañas persistentes (máximo 10) visibles desde Biblioteca y restaurables tras reiniciar la aplicación.
- Notas vinculadas a páginas, índice instantáneo y resumen con autoguardado.
- Post-it que genera nota o flashcard, y capa de resaltador/lápiz independiente con coordenadas normalizadas.
- Captura local de la vista actual del PDF para adjuntarla a una nota o crear una flashcard visual. Las capturas PNG se guardan en el almacenamiento privado de OpenTex y no alteran el PDF.
- Revisión de flashcards con repetición espaciada sencilla y salto al PDF de origen.
- Diccionario local inicial, historial y favoritos.
- Ajuste de pantalla encendida: siempre, manual o comportamiento normal.
- Atenuación por overlay, filtro cálido de protección ocular y sonido del sistema al navegar entre páginas.

## Pendiente

- Selector de miniaturas, búsqueda textual y restauración exacta del desplazamiento dentro de la página.
- Localizar nuevamente un archivo que haya sido eliminado o movido.
- Diccionario distribuido completo, edición de tarjetas y exportación Markdown/PDF anotado.
- Tareas Android document-centric opcionales.

## Android y tareas recientes

La aplicación usa una sola `MainActivity`, por lo que mantiene los documentos como pestañas internas y conserva estado al volver desde Recientes. Si se necesitan documentos como tareas Android separadas, se puede añadir posteriormente `documentLaunchMode="always"` y lanzar actividades por URI; no es necesario para el MVP y complicaría la restauración de estado compartido.

## Capturas de estudio

En el lector abre `Más` y selecciona `Capturar vista para estudiar`. OpenTex crea una imagen de la parte visible del PDF y permite elegir `Añadir captura a una nota` o `Crear flashcard con captura`. Es útil para fórmulas, diagramas y fragmentos de documentos escaneados. La captura se conserva localmente y aparece al revisar la nota o la tarjeta.

## Privacidad

- No hay cuentas ni inicio de sesión.
- No se suben PDF, capturas, notas ni tarjetas.
- No hay analítica, anuncios ni rastreadores.
- Los datos se guardan en SQLite y en el almacenamiento privado de la aplicación.

## Publicar una versión

La distribución pública se realiza mediante una GitHub Release, no subiendo la APK al historial Git. Después de compilar `opentex.apk`, crea una release con una etiqueta de versión y adjunta el archivo como asset. GitHub ofrecerá entonces la URL estable indicada arriba.
