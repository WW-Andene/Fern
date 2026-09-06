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

## Architecture

- `canvas/WorldPoint.kt` — a Double-precision 2D point type (Compose's
  `Offset` is Float-only, so this is what world-space coordinates and the
  pan offset are actually stored as).
- `canvas/Stroke.kt` — a freehand stroke stored in **world-space**
  `WorldPoint`s (independent of the current pan/zoom), with a cached
  bounding box for cheap visibility checks, and a `shiftBy` used during
  origin rebasing.
- `canvas/CanvasState.kt` — the camera model: pan offset and scale (both
  Double), world↔screen coordinate conversion, the floating-origin rebase,
  and the active drawing tool state.
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
