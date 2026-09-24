# Publicar OpenTex

## Compilar

Desde la raíz del proyecto:

```bash
flutter pub get
dart run build_runner build
flutter analyze
flutter test
flutter build apk --release
./android/gradlew -p android :app:copyOpenTexApk
```

El archivo que se debe adjuntar es `build/app/outputs/flutter-apk/opentex.apk`.

## Crear la release en GitHub

1. Abre `https://github.com/Kuritomay/opentex/releases/new`.
2. Crea una etiqueta, por ejemplo `v1.0.0`.
3. Usa el título `OpenTex 1.0.0`.
4. Describe los cambios principales y adjunta `opentex.apk`.
5. Publica la release.

La descarga directa de la última versión quedará disponible en:

`https://github.com/Kuritomay/opentex/releases/latest/download/opentex.apk`

## Comprobación

Descarga la APK desde la release con un dispositivo Android, instálala y confirma que abre, selecciona un PDF y conserva su posición tras reiniciar la aplicación.
