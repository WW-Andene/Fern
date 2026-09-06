# Fern

A native Android infinite-canvas drawing app, inspired by Endless Paper on iOS.

## Concept

- One continuous, unbounded sheet — no page edges, no fixed canvas size.
- One finger draws; two fingers pan and pinch-zoom (zoom is centered on your
  fingers, so content under them stays put).
- Zoom range is roughly 0.001x–4096x (`CanvasState.MIN_SCALE` /
  `MAX_SCALE` in `CanvasState.kt`). This is the same approach real
  infinite-canvas apps use — it isn't literally infinite (that would need
  arbitrary-precision math), but it comfortably covers zooming from a whole
  layout down to fine detail.

## Architecture

- `canvas/Stroke.kt` — a freehand stroke stored in **world-space**
  coordinates (independent of the current pan/zoom), with a cached bounding
  box for cheap visibility checks.
- `canvas/CanvasState.kt` — the camera model: current pan offset and scale,
  plus world↔screen coordinate conversion and the active drawing tool state.
- `canvas/DrawingCanvas.kt` — the Compose `Canvas` and gesture handling.
  Only strokes whose bounding box intersects the current viewport are drawn
  each frame (basic culling), so performance doesn't degrade as the sheet
  fills up.
- `canvas/Toolbar.kt` — floating color picker / undo / clear / reset-view
  controls.
- `MainActivity.kt` — wires it together.

## Not yet built (natural next steps)

- Persistence (save/load the sheet, e.g. to Room or a flat file).
- Stylus pressure / tilt support (currently a fixed stroke width).
- Multiple sheets / documents.
- Export to image or PDF.
- Smoothing/interpolation for faster strokes (currently raw point-to-point
  line segments).

## Building

Requires the Android SDK (compileSdk 34) and JDK 17.

```
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Open the project in
Android Studio for the full IDE experience (emulator, layout inspector,
etc.) — it will pick up `local.properties` automatically; that file is
gitignored since it's machine-specific.
