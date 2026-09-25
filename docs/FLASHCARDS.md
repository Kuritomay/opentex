# Flashcards en OpenTex

Guía para crear, importar y exportar flashcards en OpenTex (pestaña **Flashcards** del panel inferior).

## 1. Crear flashcards en la app

### Desde una captura del documento (método principal)
1. Abre un PDF en la biblioteca.
2. Selecciona una región del texto/imagen (modo captura).
3. Toca **«Crear flashcard desde esta captura»**.
4. En el editor, rellena el anverso y el reverso (la captura queda como imagen del anverso) y guarda.

### Desde el editor manual
- En la pestaña **Flashcards**, toca el icono de lápiz (Editar) de una tarjeta o crea una nueva pulsando el botón de añadir.
- Campos disponibles: **mazo** (deck), **tipo**, **anverso**, **reverso**, **explicación** y, para opción múltiple, las **opciones** y sus **respuestas correctas**.

### Tipos de tarjeta
| Tipo (`type`)  | Comportamiento en el repaso |
|---------------|-----------------------------|
| `basic`       | Anverso → Reverso (tarjeta normal). Explicación opcional. |
| `reversed`    | Se muestra dos veces en cada sesión: normal y en sentido inverso (alterna cada repaso mediante la paridad del índice). |
| `latex`       | Muestra los campos anverso/reverso como bloques en monoespaciada (`frontLatex`/`backLatex`); no aplica estilos de texto plano. |
| `multiple_choice` | Muestra las `options`; tras elegir, marca en verde las correctas y en rojo la elegida si falló. Requiere `options` y `correct`. |
| `explained`   | Tipo interno heredado; **se exporta como `basic`** con su `explanation`. |

### Sistema de repaso (SM-2)
- Cada tarjeta se puntúa al revelar con: **Otra vez**, **Difícil**, **Bien**, **Fácil**.
- Los intervalos se calculan con `ReviewScheduler` (`again` resetea, `hard` ≈ ×1.2, `good` progresa 1→3→n×ease, `easy` salta antes).
- Las tarjetas importadas **suelen entrar como nuevas** (`reviewCount = 0`, `dueAt = 0`) y aparecen en el repaso al instante.

## 2. Importar y exportar

En la pestaña **Flashcards**, la barra superior ofrece dos acciones:

- **Importar flashcards** (icono descarga) → selector de archivo `*/*`.
- **Exportar flashcards** (icono subida) → se crea un ZIP llamado `opentex-flashcards.zip`.

### Exportación
Genera un **ZIP** con:
- `flashcards.json` en la raíz (con el esquema de abajo).
- El resto de archivos son las imágenes anverso referidas por el campo `image` (p. ej. `media/card_0.webp`).

### Importación
Acepta dos formatos:
- Un **JSON plano** con el esquema de abajo.
- Un **ZIP** (se detecta por la firma `PK`) que contenga `flashcards.json` en la raíz y, opcionalmente, sus imágenes.

Al importar:
- Los mazos se crean por nombre; si ya existe un mazo con el mismo nombre, **las tarjetas se añaden a él** (importación aditiva: reimportar **duplica** tarjetas).
- Nombre de mazo en blanco → se usa `General`.
- Si una tarjeta incluye `source` con `uri` y ese documento ya está en la biblioteca, la tarjeta queda enlazada a él (y se usa `document` por nombre como respaldo).
- El importador valida: el campo `format` debe ser `opentex-flashcards` y `version ≤ 1`; si el archivo no contiene tarjetas, se rechaza.

## 3. Esquema del archivo de importación

Version 1, formato `opentex-flashcards`:

```json
{
  "format": "opentex-flashcards",
  "version": 1,
  "exportedAt": 1700000000000,
  "decks": [
    {
      "name": "Anatomía",
      "cards": [
        {
          "type": "basic",
          "front": "¿Qué es el hígado?",
          "back": "Órgano que filtra la sangre.",
          "explanation": "Extra explicación opcional",
          "tags": ["medicina", "digestivo"]
        },
        {
          "type": "latex",
          "frontLatex": "\\int x^2 dx",
          "backLatex": "\\frac{x^3}{3} + C"
        },
        {
          "type": "multiple_choice",
          "front": "¿Capital de Francia?",
          "options": ["París", "Londres", "Berlín", "Madrid"],
          "correct": [0],
          "explanation": "París es la capital francesa."
        },
        {
          "type": "reversed",
          "front": "Definición",
          "back": "Término"
        }
      ]
    }
  ]
}
```

### Campos de tarjeta
| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `type` | string | sí | `basic`, `reversed`, `latex`, `multiple_choice`. En blanco → `basic`. |
| `front` | string | sí | Texto del anverso (se ignora como Latex si hay `frontLatex`). |
| `back` | string | sí | Texto del reverso (igual para `backLatex`). |
| `explanation` | string | no | Explicación extra mostrada bajo la respuesta. |
| `frontLatex` / `backLatex` | string | no | Variante LaTeX del anverso/reverso (solo relevante en `latex`). |
| `options` | array[string] | para `multiple_choice` | Opciones de respuesta en orden. |
| `correct` | array[int] | para `multiple_choice` | Índices de las opciones correctas (0-based). |
| `tags` | array[string] | no | Etiquetas de la tarjeta. |
| `image` | string | no | Ruta relativa dentro del ZIP a la imagen del anverso (`media/card_0.webp`). Solo válida en ZIP. |
| `source` | object | no | `{ "document": "<nombre>", "page": 1, "uri": "<uri SAF>" }`. `page` aquí es 1-based. |

> Nota: en el JSON no exportes nunca el campo `sourcePage`; dentro de la app, al guardar, se resta 1 al `page` interno (0-based).

## 4. Prompt preciso para generar flashcards de OpenTex

Copia este prompt y pégalo en un asistente de IA junto al material de estudio, para que genere un archivo JSON compatible con OpenTex:

```text
Eres un generador de flashcards para la app OpenTex (formato `opentex-flashcards`, versión 1).
A partir del material de estudio que te doy, produce un único archivo JSON válido con la siguiente
estructura EXACTA:

{
  "format": "opentex-flashcards",
  "version": 1,
  "decks": [
    {
      "name": "<nombre del mazo, sin repetir mazos ya usados>",
      "cards": [
        {
          "type": "basic | reversed | latex | multiple_choice",
          "front": "<pregunta>",
          "back": "<respuesta>",
          "explanation": "<explicación o contexto opcional>",
          "tags": ["<etiqueta>", "<etiqueta>"]
        }
      ]
    }
  ]
}

REGLAS OBLIGATORIAS:
1. Usa codificación UTF-8 y solo JSON válido; no añadas comentarios ni texto de más.
2. Haz 1 tarjeta por concepto clave, breve y de una sola idea de respuesta (salvo opción múltiple).
3. Usa "type": "basic" salvo que la tarjeta se preste a:
   - "reversed": definiciones término↔definición, vocabulario bidireccional.
   - "latex": fórmulas/matemáticas (usar "frontLatex" y "backLatex" en vez de front/back).
   - "multiple_choice": solo para pruebas/repaso de identificación; incluye 3-5 opciones en "options"
     y los índices (0-based) de las correctas en "correct".
4. No pongas "image" (solo válido en ZIP) ni "source" salvo que se especifique.
5. En "explanation" añade el porqué/clave de memoria cuando aporte valor; omite el campo si no aplica.
6. Agrupa tarjetas relacionadas bajo un mismo mazo y usa nombres de mazo cortos y descriptivos.
7. Devuelve ÚNICAMENTE el JSON, sin paréntesis explicativos antes o después.
```

Después de obtener el JSON:
1. Guárdalo como `flashcards.json` (solo), o **empaquétalo en un ZIP** (`zip flashcards.zip flashcards.json`) cuando haya imágenes.
2. En OpenTex: pestaña **Flashcards** → icono **Importar** → selecciona el `.json` (o `.zip`).
3. Compruébalo con **Repasar todo el mazo**.

## 5. Limitaciones conocidas

- **Sin deduplicación**: importar dos veces el mismo archivo duplica las tarjetas.
- **Sin columnas/DSV**: solo se acepta el JSON/ZIP descrito (formato de la app). Anki/CSV requieren conversión previa.
- **Repetición**: no existe edición de mazos oculta; edita cada tarjeta con el lápiz.
- Las tarjetas `explained` internas se exportan como `basic` (con su `explanation`); al reimportarlas quedan como `basic`.