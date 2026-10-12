# Fern

A native Android infinite-canvas drawing app, inspired by Endless Paper on iOS.

## Concept

- One continuous, unbounded sheet — no page edges, no fixed canvas size.
- One finger draws; two fingers pan and pinch-zoom (zoom is centered on your
  fingers, so content under them stays put).
- Zoom/pan range is, for any realistic drawing session, unbounded — see
  "How the infinite zoom/pan actually works" below for the honest version
  of that claim.

## How the infinite zoom/pan actually works

The first version of this app used Compose's `Offset`, which is Float
(~7 significant decimal digits) for both pan offset and scale. That caps
out fast: pan a few million units and strokes start jittering or collapsing
to a point, and zoom range was artificially capped to ~9 orders of
magnitude. That's not what "infinite" means here, so it's been replaced:

1. **Double precision** (`WorldPoint.kt`, `CanvasState.kt`) — all world
   coordinates, the pan offset, and the scale factor are Double
   (~15-17 significant digits), not Float. Float is only used at the very
   last step, converting an already on-screen coordinate for the renderer.
2. **Floating origin** — the actual trick that makes *pan* unbounded.
   Double precision is relative to magnitude: a Double near 50,000,000 has
   far less precision headroom than one near 0. So instead of ever letting
   the pan offset grow large, `CanvasState.rebaseIfNeeded()` re-anchors
   world-space (0,0) to wherever the camera currently is once you've
   drifted more than 65,536 world units away: every stored stroke point
   gets shifted by `-panWorld`, and `panWorld` resets to zero. This keeps
   every coordinate in the app permanently close to zero, no matter how far
   you've actually panned — so precision never degrades, however long the
   session runs.
3. **Scale carries zoom depth, unrebased** — because it's a pure
   multiplier applied to already-small, already-precise rebased
   coordinates, `scale` doesn't need rebasing the way translation does.
   Double covers roughly 300 orders of magnitude with full relative
   precision at every level. `MIN_SCALE`/`MAX_SCALE` in `CanvasState.kt`
   are set to 1e-250/1e250 purely as a safety margin against edge-of-range
   float conversion issues, not a meaningful practical ceiling.

Net effect: you can pan and zoom for as long as you want, in either
direction, without the precision breakdown Float-based implementations
hit — which is the same technique real infinite-canvas apps (and
large-world game engines, for the pan half of it) use. It is not literally
mathematical infinity (that would need arbitrary-precision arithmetic like
`BigDecimal`, which would be far slower per-frame for no real-world
benefit), but it is unbounded for any amount of actual drawing a person
could do.

## Features

- **Drawing:** pen types (marker, pencil, highlighter, calligraphy, airbrush, crayon, pixel art,
  stamp brush with a brush editor), stylus pressure/tilt,
  palm rejection, stylus eraser end and side button, stroke stabiliser, shape tool,
  draw-and-hold shape recognition, vector fill, eyedropper, text (bold/italic), images,
  ruler, symmetry (mirror, four-way, radial), opacity and blend modes.
- **Editing:** snapshot undo/redo (also two/three-finger tap), rectangle and lasso
  selection, move/scale/rotate, copy/paste between documents, recolour, smooth, align and
  distribute, bring to front/send to back.
- **Organisation:** layers (visibility, lock, opacity, reorder, merge), frames/artboards
  with presentation mode, named pins, minimap, text search, two-finger canvas rotation.
- **Documents:** library with thumbnails, folders, favourites, search, sort and a 30-day
  trash; crash-safe versioned storage; undo history that survives restarts; a
  version-history timeline.
- **In and out:** export PNG, PDF (one page per frame) and SVG; share sheet; import PDFs
  to annotate.
- **App:** dark mode, first-launch gesture guide, on-device crash reports shared only by
  choice.

## Architecture

- `canvas/WorldPoint.kt` — Double-precision 2D point for world-space coordinates.
- `canvas/Stroke.kt` — a stroke in world space with per-point pressure/tilt, its layer,
  and a cached bounding box.
- `canvas/CanvasState.kt` — camera (pan, zoom, rotation, floating-origin rebase), tools,
  selection, layers, frames, symmetry and snapshot undo history.
- `canvas/DrawingCanvas.kt` — gesture handling and rendering; `drawScene` draws the
  document itself and is shared with export.
- `canvas/CanvasStorage.kt` — persistence: one JSON file per concern per document, written
  atomically inside a versioned envelope (`formatVersion`), with corrupt-file quarantine.
- `canvas/ImageExport.kt`, `SvgExport.kt`, `PdfImport.kt` — export and import.
- `canvas/Toolbar.kt` and `*Dialog.kt` — UI.
- `CrashReporter.kt`, `MainActivity.kt` — app wiring.

The full feature roadmap, with the status of every item, is in [ROADMAP.md](ROADMAP.md).

## Building

Requires the Android SDK (compileSdk 34) and JDK 17.

```
./gradlew assembleDebug
```

Run the tests (plain JVM; storage and Compose UI tests use Robolectric, no device needed):

```
./gradlew testDebugUnitTest
```

The debug APK lands in `app/build/outputs/apk/debug/`. Open the project in
Android Studio for the full IDE experience (emulator, layout inspector,
etc.) — it will pick up `local.properties` automatically; that file is
gitignored since it's machine-specific.
