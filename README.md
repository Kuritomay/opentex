# OpenTex

OpenTex es un lector y entorno de estudio Android nativo, offline, construido con Kotlin, Jetpack Compose, Material 3 y Room.

## Ejecutar

Requiere Android Studio o Android SDK, Java 17 y un dispositivo/emulador con Android 8.0 o superior.

```bash
./android/gradlew -p android :app:assembleDebug
```

La APK se genera en `android/app/build/outputs/apk/debug/app-debug.apk`.

## Estado actual

La Fase 1 incluye identidad OpenTex, biblioteca responsive con portadas cacheadas de PDF, selector SAF con permisos persistentes, lectura PDF local, zoom multitáctil y doble toque, progreso persistente, recientes, pestañas persistentes y álbumes lógicos sin duplicar archivos físicos.

EPUB, DOC y DOCX se contemplan para una fase posterior de visores y portadas específicos. No hay cuentas, red, analítica ni servicios cloud.
