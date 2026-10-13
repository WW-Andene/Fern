# Fern roadmap

Everything a best-in-class drawing app should do, in the order it should be built. Phases
are in dependency order; within a phase, items are in build order.

**Status key:** `[x]` done · `[~]` partly done (what exists is noted) · `[ ]` not started ·
**⏸** deferred: needs an external platform, which is off-limits for now (CLAUDE.md §8.1).

Ordering rules:

1. **Trust before features.** Nobody uses a drawing app that loses work, so storage and safety come before new tools.
2. **Data model before UI.** Layers, groups and the file format change how strokes are stored; building them late means rewriting every tool.
3. **Performance before scale features.** Sync, collaboration and huge documents all assume the renderer is already fast.
4. **Polish last, but not never.** Accessibility and onboarding decide whether people stay.

Update this file in the same commit as the work that changes an item's status.

---

## Phase 1 — Core canvas & ink

Goal: ink that feels like it comes straight out of the pen tip.

- [x] 1. Infinite canvas with Double precision and floating origin
- [x] 2. One-finger draw, two-finger pan and pinch-zoom
- [x] 3. Live stroke rendering
- [x] 4. Stroke smoothing (midpoint quadratic Béziers)
- [x] 5. Undo / redo (snapshot-based: reverts any action, including erase, move, fill, clear, images and text)
- [x] 6. Brush width and full HSV/hex colour picker
- [x] 7. Low-latency ink: front-buffered rendering (`CanvasFrontBufferedRenderer`, API 29+) for opaque marker strokes, plus motion prediction; pen-to-pixel latency not yet measured on a device
- [x] 8. Motion prediction (`androidx.input` `MotionEventPredictor`)
- [x] 9. Variable-width stroke geometry (tessellated outlines instead of per-segment lines) — pressure/tilt and calligraphy strokes filled as one mitred outline
- [x] 10. Zoom-level-aware simplification — sub-pixel strokes drawn as a dot; points under a screen pixel apart skipped at render time
- [x] 11. Two-finger canvas rotation, snap to upright, reset

## Phase 2 — Documents, persistence & reliability

Goal: no drawing is ever lost.

- [x] 12. Persist strokes locally across restarts
- [x] 13. Debounced autosave after every action
- [x] 14. Multiple documents: create, rename, duplicate, delete, switch
- [x] 15. Automated tests (unit + UI) — JVM unit tests, plus storage and Compose UI tests on Robolectric; run in CI
- [x] 16. Crash-safe atomic writes, corrupt-file quarantine instead of a crash
- [x] 17. Versioned file format with migration path
- [x] 18. Storage that scales to 1M strokes — compact binary format (16 bytes per finger point); drawing appends to a journal instead of rewriting the file; stroke points in primitive arrays; undo history keeps each step's list plus only the strokes changed after it, not a copy of the document; the spatial index is patched, not rebuilt, after edits; persisted history capped by stroke count. Measured at 1M strokes x 16 points (JVM, ScaleBenchmark): load 3.9 s, autosave after a stroke 34 ms, object-eraser touch with undo 0.19 s, zoomed-in frame 1.6 ms; not yet measured on a device (item 48)
- [x] 19. Undo history that survives restarts; version-history timeline with restore
- [x] 20. Document library: thumbnails, folders, search, sort, favourites, 30-day trash with restore
- [x] 21. Crash reporting — on-device reports of Java crashes, plus ANRs and native crashes from the system's exit records (API 30+), shared only by the user's choice; no analytics (by design)

## Phase 3 — Tools & expressive drawing

Goal: a full, professional tool set.

- [x] 22. Eraser — ink eraser (partial) and object eraser (whole strokes and images)
- [x] 23. Pen types: marker, pencil, highlighter, calligraphy, airbrush, crayon, pixel art
- [x] 24. Stylus pressure and tilt; palm rejection
- [x] 25. Shapes (line, rectangle, ellipse, arrow, speech bubble), vector fill, text, images
- [x] 26. Ruler/guide; opacity and blend modes; background styles
- [x] 27. Stylus eraser end and side button erase; stylus barrel/tail buttons (Android 14 key events) toggle eraser and previous tool, the platform counterpart of double-tap/squeeze
- [x] 28. Shape recognition: draw-and-hold snaps to a clean line, rectangle or ellipse
- [x] 29. Brush engine — airbrush and crayon procedural textures; stamp brush (circle, soft, square, star tips) with a brush editor for spacing, scatter, size/angle jitter and direction following, and named brush presets; image tips made from any picture (its darkness, or its transparency when it has some)
- [x] 30. Colour workflow — eyedropper, touch-and-hold pick, recent colours, saved palette, .gpl and .ase import
- [x] 31. Quick tool switching — touch-and-hold eyedropper, keyboard shortcuts, four favourite pen slots on the toolbar, radial quick menu (tools, undo/redo)
- [x] 32. Erase modes — ink vs object eraser, each also as a lasso eraser
- [x] 33. Rich text — bold, italic, four sizes, sticky notes, links, four fonts (system families), left/centre/right alignment, wrapping text boxes dragged out with the text tool (width fixed by the drag; no resize handle)
- [x] 34. Gradients and pattern fills — fade fill, screentone fill, two-colour linear gradients in eight directions (also in SVG export); no radial gradients
- [x] 35. Symmetry (mirror, four-way, radial); one- and two-point perspective guides with draggable vanishing points and assisted drawing (no three-point)
- [x] 36. Snapping — shapes snap to grid on grid/dot paper, 15° angles, square/circle, and to existing strokes' ends and corners (snap-to-object, with an on-screen ring; toggle in Settings)

## Phase 4 — Editing, organisation & layers

Goal: anything drawn can be rearranged later.

- [x] 37. Selection: move, duplicate, delete, rotate, scale (strokes; images and text can be picked, moved and deleted)
- [x] 38. Minimap, zoom-to-fit (includes images and text), named pins
- [x] 39. Layers: add, reorder, hide, lock, opacity, merge, rename, delete, remove empty/hidden
- [x] 40. Lasso selection, select-by-colour, select-all-on-layer
- [x] 41. Recolour and change the width of a selection
- [x] 42. Z-order, align and distribute; grouping (select one member, get the group; group / ungroup; duplicates and pastes form new groups)
- [x] 43. Copy and paste through the system clipboard: strokes between documents and across restarts, a PNG of the selection for other apps, and text or images pasted from other apps
- [x] 44. Smooth and straighten selected strokes; node editing (drag a stroke's nodes; freehand strokes bend smoothly between nodes)
- [x] 45. Frames/artboards: named, navigable, exportable, presentable
- [x] 46. Links between canvas regions and documents — a text label can link to a named pin or another document (underlined; "Follow link" in its dialog)

## Phase 5 — Performance at scale

Goal: 120 fps at any zoom, with any amount of content.

- [x] 48. Benchmark suite (10k / 100k / 1M strokes) — JVM suite (storage, rendering, hit-testing; `-Pbenchmark`) and an on-device suite (`DeviceScaleBenchmark`, androidx.benchmark: frames at two zooms, eraser with undo, autosave; 1M by runner argument); the device suite builds but hasn't been run on a device here
- [x] 49. Spatial index — multi-level grid for rendering, the eraser and object snapping (JVM, 1M strokes: zoomed-in frame 251 -> 1.6 ms, eraser move ~0 ms, rebuild after an action avoided: the index is patched for the strokes that changed, ~60 ms)
- [x] 50. Level of detail — sub-pixel strokes drawn as one dot; sub-pixel point spacing decimated, cached per stroke per zoom octave
- [x] 51. Tile caching — world-aligned 256 px tiles per zoom octave for documents of 20k+ strokes while nothing is edited live (JVM, 1M strokes: whole-document pan frame 1899 -> 16 ms; refilling after an edit ~3 s)
- [x] 52. GPU rendering path — the canvas draws through Android's hardware pipeline; scene tiles are rasterized on the GPU (RenderNode + HardwareRenderer into hardware bitmaps, API 29+) instead of on the CPU, falling back to the CPU if the GPU path fails; GPU speed not yet measured on a device
- [x] 53. Background loading / streaming of off-screen regions — documents load and switch off the main thread behind a loading cover; strokes are stored in chunks with bounds, and each document reopens at its saved view with the chunks in view read and shown first, the rest after; once loaded the whole document is held in memory (no unloading of distant regions)
- [x] 54. Large images and PDF pages downscaled to 2048 px
- [x] 55. Battery and thermal checks — battery saver or severe heat (thermal status, API 29+) lengthens the autosave batch (0.5 s -> 3 s) and caps animation playback at 12 fps

## Phase 6 — Import, export & sharing

Goal: work can leave the app and come back.

- [x] 56. Export PNG: whole document, a frame, the selected area, transparent background
- [x] 57. Vector export: SVG and multi-page PDF (one page per frame)
- [x] 58. `.fern` backup format: export and import a whole document
- [x] 59. Import PDFs to annotate; drag and drop images and text from other apps onto the canvas
- [x] 60. Share sheet and in-app time-lapse replay; replay exports as an animated GIF or an MP4 video (platform H.264 encoder; not yet verified on a device)

## Phase 7 — Sync & collaboration ⏸

All deferred: they need a server or cloud platform.

- [ ] 61. Accounts and end-to-end-encrypted cloud backup ⏸
- [ ] 62. Multi-device sync (CRDT per document) ⏸
- [ ] 63. Read-only share links and a web viewer ⏸
- [ ] 64. Real-time co-editing ⏸
- [ ] 65. Comments pinned to canvas locations ⏸
- [ ] 66. Desktop or web companion app ⏸

## Phase 8 — Intelligence, accessibility, polish & platform

- [ ] 67. Handwriting recognition ⏸ (ML Kit depends on Google Play Services)
- [x] 68. Smart diagramming: connectors between labels and sticky notes (follow moves, undoable, saved, exported); "Tidy diagram" lays connected labels out as a layered flowchart
- [x] 69. Accessibility — button descriptions, canvas screen-reader description, left-handed layout, high-contrast theme (light and dark, every pair 7:1 or better)
- [x] 70. Localisation: every UI string in res/values, full French translation (checked key-for-key by a test); right-to-left supported, with hand-side controls kept on physical sides. Names generated inside documents ("Layer 2", "Page 3") stay English
- [x] 71. Two-finger tap undo, three-finger tap redo, toolbar top or bottom; customisable toolbar (Settings → Customise toolbar hides optional button groups; core tools always stay)
- [x] 72. Gesture guide on first launch, templates (graph, dot, lined, comic, storyboard, weekly planner in the locale's week order, mind map of connected sticky notes)
- [x] 73. Dark mode for the UI; light and dark paper presets (paper menu), with a pattern tone for dark paper and the pen switched to a readable ink when it would vanish
- [x] 74. Keyboard shortcuts, mouse wheel and trackpad; large-screen layout: from 768dp wide the toolbar wraps onto several rows with every tool in view instead of scrolling
- [x] 75. Multi-window drag-and-drop: images, text and Fern strokes dropped onto the canvas; a selection dragged out from its handle to other apps (as a PNG) or another Fern window (as strokes). The cross-window drag itself is untested here (needs a device)
- [ ] 76. Generative assists ⏸ (hosted AI)
- [ ] 77. Release work: Play Store listing, privacy policy, staged rollouts ⏸ (owner: the listing and rollouts are Play Console steps; a privacy-policy document is a new top-level file needing approval; R8 shrinking needs device testing, since saved documents store enum names)

## Phase 9 — Animation & skeletal rigging

- [x] 78. Flipbook frames: insert blank or copied, delete, step
- [x] 79. Onion skin, playback at 6/8/12/24 fps, loop and ping-pong
- [x] 80. Export animated GIF, animated PNG (APNG, full colour and alpha), sprite sheet, and video: MP4 (H.264) and WebM (VP8) through the platform encoders (videos not yet verified on a device)
- [x] 81. Automatic in-betweens and motion along a drawn path, with easing (linear, ease in, ease out, ease in-out) or a custom curve shaped in a graph editor (cubic Bézier handles, overshoot allowed)
- [x] 82. Shape tweening — strokes morphed pairwise in in-betweens, each paired with its nearest counterpart, closed outlines lined up (start point and direction) so they don't twist
- [x] 83. Bones and skeletons: a bone tool draws bones (chained when started at a tail), binds strokes to the nearest bones, and poses per frame (turn a bone with its children and ink, move a root); copied frames carry the pose, in-betweens tween it; saved, in bundles, undoable
- [x] 84. Weighted deformation: smooth joints bend bound ink around a turned bone's joint by inverse-distance weights (rotation-blended); weights can be painted per point with the bone tool (overlay shows them; saved with the stroke) and override the automatic ones; free-form mesh warp of a selection (4 x 4 Bézier lattice)
- [x] 85. Inverse kinematics: dragging a limb's end pulls its chain (up to four bones, CCD solver) so the tip follows the finger; per-joint angle limits (kept by IK and by hand-turning); dynamic bones simulated as damped springs across the animation for follow-through (lag, overshoot, settle)
- [x] 86. Skins and animation blending — layers can belong to a named skin (an alternative look on the same rig), chosen in the layers panel; a frame's pose can be blended with another frame's at any weight, its ink following each bone
- [x] 87. Lottie export (frame by frame, vector, checked by rendering with an independent Lottie library); Spine export (skeleton JSON + an image per bone, animation keyed per frame) and import (bones and their animation, posed frame by frame); DragonBones export (armature, texture atlas, animation). Imported skeletons bring bones only: attachments are images, which can't bind to bones
- [x] 88. X-sheet: per-frame holds (playback, GIF, APNG and Lottie follow them; saved with the fps, undoable); a soundtrack played in step with playback; a mouth chart (Preston Blair shapes) per frame for lip-sync, by hand or filled from the soundtrack's loudness (decoding needs a device; videos are still exported silent)

## Phase 10 — Raster painting

- [x] 89. Raster layers with tiled pixel storage, up to 16-bit — 256 px tiles made only where there is paint, 8 or 16 bits per channel (16-bit shown through half-float bitmaps); the pen paints and the eraser erases on them with pressure; tile-level undo; saved tile by tile, in .fern bundles too; raster layers on one grid merge
- [x] 90. Pixel brush engine: wet mixing (the brush picks up and carries colour), smudge, blur, tip-shaped dabs; brush tips imported from Photoshop .abr (sampled brushes) and Procreate .brush (shape); Procreate grain and brush dynamics not read
- [x] 91. Raster selections (lasso or rectangle: painting stays inside; clear, fill, copy to a new layer, drag to move pixels), layer masks on raster or vector layers (pen hides, eraser reveals), clipping masks, alpha lock
- [x] 92. Adjustments on raster layers (or their selection), previewed live: curves (monotone), levels, hue/saturation/lightness; liquify brush; warp with the 4 x 4 lattice (drawn at 8 bits per channel)
- [x] 93. Full Photoshop blend modes (all 27; the ten the GPU lacks composited per pixel) and adjustment layers (levels, curves, hue/saturation, editable, at the layer's opacity)
- [x] 94. Reference layers (fills take their boundaries from them), gap-closing bucket fill (areas closed by several strokes, gaps up to ~8 px bridged, within the view)
- [x] 95. PSD / Procreate / Krita import and export — PSD (layers with name, visibility, opacity, blend mode; 8 or 16 bits, raw or RLE in; 8 bits out) and Krita .kra (paint layers, 8-bit) both ways; Procreate in as its flattened picture only (its layer archive is undocumented; it can't be written)

## Phase 11 — Professional vector & design

- [x] 96. Pen tool with Bézier nodes (tap for corners, drag for smooth handles, tap the first node to close; finish, remove last node, cancel); boolean operations on selected closed shapes (union, intersect, subtract, exclude; holes kept). Finished paths aren't re-editable as curves (node editing works on their points)
- [x] 97. Stroke styles — dashed lines; caps (round, square, flat), joins (round, sharp, bevelled) and width profiles (even, taper either or both ends), saved per stroke and in SVG/Lottie exports (tapers drawn on canvas and in images only)
- [x] 98. Text on a path (from a selected line; letters follow and turn with it; SVG textPath), outlined text, OpenType feature controls (ligatures off, small caps, oldstyle and tabular figures, fractions; saved, in SVG); fonts without a feature ignore it
- [x] 99. Smart guides (a moved selection snaps edges/centres to nearby objects, guide lines shown), numeric transform dialog (X, Y, width, height, proportions, rotation), live shape dimensions; screen rulers (world units, ticks that rescale with zoom) and guides dragged out of them, which moved selections snap to, saved with the document
- [x] 100. Shared colour styles (a saved colour changed everywhere it's used, opacity kept), symbol library across documents (save a selection, place copies); placed symbols are linked instances - redefining a symbol from a selected instance redraws every instance at its own place and size (in other documents when they open), and an instance can be detached
- [x] 101. Image trace: a selected image's dark shapes (Otsu threshold) become filled vector outlines, holes kept, in the active colour over the image; one colour (no colour separation)
- [x] 102. CMYK entry in the colour picker; ICC colour management (CMYK profiles loaded in Settings, v2/v4 lookup tables, perceptual intent); spot colours (named inks); print PDF in CMYK with spots as Separation colour spaces (own PDF writer; no transparency, text or images)
- [x] 103. EPS export (paths, fills, caps, joins, dashes; checked by rendering); Illustrator export as a PDF-compatible .ai (Fern layers as PDF layers, opacity kept; no text or images) and vector import of PDF and PDF-compatible .ai files (paths, fills with their fill rule, colours incl. CMYK and opacity, widths, caps, joins, dashes, PDF layers onto Fern layers; text, images, shadings, clipping and encrypted files not read; legacy PostScript-based .ai not read); Figma ⏸ (external platform, deferred); SVG export done in 57

## Phase 12 — Specialty modes

- [x] 104. Pixel art — grid-aligned pixel pen; indexed palette (pixels snap to saved colours; reduce a selection to them; palette swaps via 100); sprites are animation frames exported as sprite sheets (80)
- [x] 105. Comics — comic pages in six sizes (2:3, manga B5, A4, US comic, square, webtoon strip) and seven panel layouts (1 to 9 panels, 4-koma), each with a page frame; a panel cutter that divides crossed panels with a gutter; speech, thought (cloud with trail) and shout (burst) balloons; speed and focus lines (tapered, over the selection or the view); screentones
- [x] 106. Notes — sticky notes, connectors between them (68), mind-map and planner templates (72)
- [x] 107. Whiteboard — links in text labels open in the browser or another document; PDF pages imported onto the canvas to annotate; embedded videos (copied in, shown by their first frame with a play badge, played over the canvas with play/pause, seek and mute; saved in .fern bundles). Playback isn't verified on a device here, and videos play in a player over the canvas rather than live on it
- [x] 108. 3D painting: built-in shapes (sphere, cube, cylinder, torus, plane) or imported OBJ models, painted in colour, roughness and metallic with a soft round brush that keeps its on-screen size, turned and zoomed with two fingers, under an adjustable light (software renderer); placed on the canvas as their render and saved with the document and in bundles. Brush dabs stop at texture seams; no layers on the model; interactive speed not yet measured on a device
- [ ] 109. AR placement and tracing ⏸ (ARCore is a Google Play Services platform)
- [ ] 110. Generative AI ⏸

## Phase 13 — Ecosystem

- [x] 111. Scripting: sandboxed drawing scripts (see 164); a plugin API: JavaScript plugins (Mozilla Rhino, interpreted, no Java access, instruction-limited) that read the selection, view and active pen and add, edit or remove strokes as one undo step, with declared number parameters; five built in, more installed from .js files or written in the app
- [ ] 112. Marketplace ⏸ (hosted service)
- [ ] 113. Community gallery and live-streaming ⏸ (hosted service)
- [ ] 114. Team workspaces ⏸

## Competitor parity additions (116–148)

Found by comparing with the official feature pages of Procreate, Clip Studio Paint, Krita,
Concepts, GoodNotes, Notability, Infinite Painter, Aseprite, Spine and Affinity Designer.

| # | Feature | Seen in | Phase | Status |
|---|---|---|---|---|
| 116 | Stroke stabiliser | Procreate, Krita, Affinity, Clip Studio | 1 | done |
| 117 | Velocity dynamics | Clip Studio | 3 | done |
| 118 | Drawing assist / live snap | Procreate, Concepts | 3 | done: a held stroke snaps while the pen is still down and the pen then resizes/rotates it; lines, polylines, arcs, rectangles, polygons, ellipses |
| 119 | Colour harmonies | Aseprite, Affinity | 3 | done |
| 120 | COPIC and PANTONE libraries | Concepts, Affinity | 3 | skipped: proprietary colour data that needs a licence to ship |
| 121 | Style picker | Affinity | 4 | done: copy a selected object's style, paste it onto the selection |
| 122 | Warp, puppet warp, mesh transform | Procreate, Clip Studio | 4 | done: mesh warp (items 84, 92), corner distort with a perspective map, puppet warp (moving-least-squares rigid); vector selections and raster layers |
| 123 | Layer states / comps | Affinity, Clip Studio | 4 | done: named comps of each layer's visibility, opacity and blend, applied, updated, renamed and deleted from the layers panel; saved with the document |
| 124 | Multiple views / split screen | Affinity, Clip Studio | 4 | done: a second view (⋮ menu) with its own pan and zoom, live, drawn and erased in at its own zoom, the main view's frame outlined |
| 125 | Presentation mode | Concepts | 4 | done |
| 126 | Snapshots; delete empty/hidden layers | Affinity, Clip Studio | 4 | done |
| 127 | DXF export | Concepts | 6 | done: ASCII DXF R12 - layers, polylines with width, dashes, nearest basic and true colours; fills as closed outlines |
| 128 | Slices, @2x/@3x, batch export | Affinity | 6 | done: every frame as PNG slices at 1x, @2x and @3x in one ZIP, from the frames panel |
| 129 | Document signing, markup | Concepts | 6 | done: a signature pad (⋮ > Sign) that saves the signature and places it as selected strokes; markup with the existing tools on imported PDF pages |
| 130 | Audio recording with notes | GoodNotes, Notability | 8 | done: recording carries on while drawing; playback fades ink not yet reached and a tap on ink jumps to when it was drawn |
| 131 | AI over notes | GoodNotes, Notability | 8 | ⏸ |
| 132 | Usage statistics | Clip Studio | 8 | done: per-document time drawing (idle gaps capped at a minute), strokes drawn, erasures, first and last worked on, and what the document holds; ⋮ > Statistics |
| 133 | Customisable panels and QuickMenu | Krita, Procreate | 8 | done: toolbar groups shown or hidden (item 71); the quick menu ring holds the actions the user picks (up to 12 of 14) |
| 134 | Dopesheet and curve editor | Spine | 9 | done: bones x frames with keys and holds, tap to go to a frame; each bone's angle curve, dragged to turn it (children, ink, joint limits); hold previous frame |
| 135 | Free-form deformation, procedural animation | Spine | 9 | done: FFD per frame with the mesh warp (item 84), carried by shape tweening (item 82); procedural sway (sine or smooth wiggle) of a bone across every frame |
| 136 | Game-engine runtimes, texture packer | Spine | 9 | done: packed sprite atlas (trimmed frames, power-of-two sheet, TexturePacker JSON with offsets and durations) beside the Spine, DragonBones and Lottie exports that those runtimes load |
| 137 | Wrap-around tiled mode | Krita, Aseprite | 10 | done: a "Tile" frame repeated across the view, ink drawn on any repeat moved onto the tile, the frame exported seamless |
| 138 | Filter / file layers, global selection mask | Krita | 10 | done: blur, sharpen, pixelate, posterize, threshold and invert on raster and filter (adjustment) layers; file layers that reload from their image; the raster selection painted with the pen and eraser |
| 139 | HDR / OpenEXR, OpenColorIO | Krita | 10 | done: HDR raster layers (light up to ~22x paper white, painted at an intensity), an exposure + clip/filmic view transform in place of OpenColorIO (no Android port; .ocio configs aren't read), OpenEXR export and import keeping HDR |
| 140 | Multiple brush engines, brush tagging | Krita | 10 | done: raster engines paint, wet, smudge, blur, liquify, spray and bristle beside the stamp engine and vector pens; brush presets tagged and filtered by tag |
| 141 | Shape builder, knife, contour | Affinity | 11 | done: the shape builder splits the selected closed shapes into their regions (each styled like the topmost shape over it), then drags merge or delete regions; the knife cuts selected shapes into closed pieces and lines at the crossings; contour outlines the selection at a distance (outward, or inward for closed shapes) with round, mitred or bevelled corners |
| 142 | Isometric panel, layout grids | Affinity | 11 | done: isometric paper (lattice snapping for shapes); shapes drawn on the top, left or right plane of an isometric cube, and a selection placed onto a plane; per-frame layout grids (columns, rows, gutter, margin) shown as bands, saved with the frames, attracting shape corners and moved selections |
| 143 | Real-world scale and measurement | Concepts, Affinity | 11 | done: a per-document drawing scale in mm, cm, m, km, in, ft, yd or mi, set directly or calibrated from a measured line; a measure tool (drag: distance and angle, snapping to stroke ends; tap: a shape's perimeter and area or a line's length); rulers, shape readouts and the transform dialog in the scale's unit; saved, in bundles and duplicates |
| 144 | UI constraints; QR code tool | Affinity | 11 | done: per-stroke constraints (left, right, both/stretch, centre or scale, across and down; groups follow as one) that strokes follow when a frame is resized from the frames dialog (resizing isn't undoable, the content's move is); a QR code tool (ZXing core encoder) placing text or a link as a vector group, with error-correction level and an optional white backing with the quiet zone |
| 145 | Red/blue onion skin, frame tags, RotSprite | Aseprite | 12 | done: onion skin of up to 4 frames before and after, fading with distance, optionally tinted red (before) and blue (after); frame tags (named ranges with colour and direction: forward, reverse, ping-pong) that follow inserted and deleted frames, play on their own, are saved and go into the sprite atlas JSON as Aseprite frameTags; raster pixels turned with RotSprite (Scale2x x3, then nearest) or nearest-neighbour |
| 146 | 3D hands/heads, hand pose scanner | Clip Studio | 12 | done: a posable 3D hand (left or right; seven ready poses; curl and spread per finger, thumb across the palm) and a 3D head with Loomis construction lines, both turned with the 3D view and paintable; a hand pose scanner (MediaPipe hand landmarker, on the device) that poses the hand from a camera shot or photo. The scanner isn't verified on a device here (its landmark-to-pose step is tested); it adds ~25 MB to the APK |
| 147 | 3D texture maps, lighting, AR export | Procreate | 12 | done: colour, roughness and metallic maps painted on models (item 108), exported as PNGs, a colour texture imported from a picture; a light with direction, height, strength, colour (white, warm, cool, sunset), ambient light and exposure; export for AR and 3D apps as glTF binary (.glb) and USDZ (text USD, 64-byte aligned), 30 cm across. Neither export has been opened in an AR viewer here |
| 148 | Command-line batch interface | Aseprite | 13 | not a priority (see Non prioritaire) |

## Phase 14 — Co-creation: animate, rig and build logic together

Shared, multiplayer parts need a server and are deferred ⏸; single-player parts can be built.

**Multiplayer animation and rigging**

- [ ] 149. Shared timeline with locks ⏸
- [ ] 150. Live presence on the timeline and rig ⏸
- [ ] 151. Shared rig library ⏸
- [ ] 152. Roles and permissions ⏸
- [x] 153. Branches and merges for animations — branch an animation into alternative takes (the work so far becomes "main"), switch between them, and merge one into another frame by frame against the frame as it was when the branch began (frames only it changed are taken; frames both changed are kept or taken as chosen); strokes, bones and holds; saved with the document and in bundles. Frames are matched by number, so a frame inserted mid-animation on one side shifts the rest; switching clears the undo history
- [ ] 154. Voice and text chat in the canvas ⏸

**From drawing to logic**

- [x] 155. Turn a drawing into an object with properties and behaviours — a selection (grouped, so its strokes on every frame go together) becomes a named object with number properties and behaviours (move, rotate, swing, follow a drawn path, bounce off or wrap around the edges, blink, pulse) with their settings; Play runs them in the view, editing hidden, the drawing untouched; saved with the document and in bundles. Objects aren't part of undo, like frames and layers
- [x] 156. Visual scripting (nodes / blocks) — per object, rules built from blocks: "when" blocks (the state-machine conditions of item 158) and "do" blocks (set or add to a property, set speed, turn to, show, hide, remove, remove what it touches, go to a state), run in order each step while their conditions hold; reordered, saved with the document. Blocks, not a node graph
- [x] 157. Drawn behaviours — a drawn path becomes motion (animate along path; an object's follow-path behaviour, item 155); a closed line drawn over an object becomes its hitbox; a line drawn between two objects becomes a link (rod, rope or attachment) held while the game plays; hitboxes and links shown dashed while editing
- [x] 158. State machines linking rig animations to logic — per object: states that play frame tags (drawn frames or a rig posed frame by frame), looping or not, and transitions from a state or any state when conditions hold (action held or pressed, on the ground or in the air, moving or still, touching an object, a property compared to a value, time in state, animation finished)
- [x] 159. 2D physics — arcade physics for game objects: fixed (solid) or dynamic bodies as boxes or circles, with mass, bounce, friction and gravity scale; world gravity in any direction and scene edges as walls; contacts and standing-on-something reported for rules and scripts. Bodies are axis-aligned (an object's turn doesn't tilt its body)
- [x] 160. Input mapping (touch, stylus, gamepad, keyboard) — named actions bound to keys, gamepad buttons and stick directions (and the d-pad hat), on-screen buttons (a d-pad and A/B shown while playing), a finger or the stylus on the game (with its world position); defaults for arrows/WASD, the d-pad, A/B, space and enter; keys bound by pressing them; platformer and top-down controls use them. Real gamepads are untested here (no device)
- [x] 161. Play mode — the game plays full screen with editing hidden: in a chosen frame fitted to the screen (or the view as it is), the camera easing after a chosen object within the scene, pause and resume, restart, stop back to the view as it was; objects' properties shown on screen when asked
- [ ] 162. Online multiplayer play ⏸
- [ ] 163. AI logic assistant ⏸
- [x] 164. Text scripting (sandboxed) — one language, step-limited, no file or network access, with variables, arithmetic, texts, `if`/`else`, `while`, `repeat`, procedures (recursion depth-limited) and maths functions; used by drawing scripts (turtle graphics, ⋮ menu) and by object scripts that run every step of the game, reading the object, input, contacts and properties and moving, showing, removing, scoring and switching states. A script that fails during play stops and shows its line on screen

## Phase 15 — Groundwork for the tool expansion

Goal: make the phases below cheap and safe to build. Every later phase adds tools to the same few files, so they are split first.

- [ ] 167. Split per-tool gesture handling out of `DrawingCanvas.kt` into one handler per tool (press, move, release, cancel), and per-tool state out of `CanvasState.kt` (CLAUDE.md §4.5): both are thousands of lines and every new tool touches the same `when` blocks.
- [ ] 168. Gesture tests: fingers landing and lifting at different times, a pan becoming a pinch, stylus with a resting palm, long press versus a slow stroke.
- [ ] 169. Long-press delay and multi-finger tap tolerances in Settings, instead of fixed values.
- [ ] 170. Screen sampling: read the colour actually shown at a point (all layers, raster, images, fills, paper), or on one layer only. Needed by the eyedropper, colour replace, palette from screen and smart fill.
- [ ] 171. On-canvas handle framework: one way to draw, hit-test and drag handles (shape parameters, joint limits, gradient stops, vanishing points, IK targets, constraint targets) instead of each tool doing its own.
- [ ] 172. On-canvas numeric readout and entry: any handle can show its value and accept a typed one.

## Phase 16 — Drawing tool fixes

Goal: the existing tools work fully before new ones arrive. Builds on Phase 15.

- [ ] 173. Eyedropper samples what is shown (screen sampling), with "all layers" or "this layer"; a magnified loupe with old and new colour; sampling size 1, 3 or 5 px; a message when nothing was picked. Today it reads vector strokes only and ignores raster, images, fills and paper.
- [ ] 174. Eyedropper picks a stroke's whole style (width, pen type) as an option, reusing the style picker; the last 8 picked colours kept in the colour strip.
- [ ] 175. Shape: live dimensions and angle while drawing, in the document scale; draw from centre or corner with a second finger instead of a keyboard.
- [ ] 176. Shape stays editable with handles (corner radius, side count, arrowhead size) until the next shape starts.
- [ ] 177. Fill: preview the region before committing, showing where gaps were closed; a clear "all layers" or "this layer" setting; a tolerance slider for raster flood fill.
- [ ] 178. Fill reaches areas extending past the view, falling back to the enclosing shapes' bounds (today the visible rectangle limits it).
- [ ] 179. Eraser: size outline on hover and press; tapered ends when cutting strokes; current-layer-only option; lasso erase removes what is fully inside or anything touched.
- [ ] 180. Select: on-canvas add, subtract and intersect; size, position and rotation in the readout with typed values; arrow-button nudge by a fixed step.
- [ ] 181. Select: invert selection; grow and shrink by a distance; flip horizontal and vertical on the selection strip.
- [ ] 182. Transform: snapping to 15° and to other objects shown on the handles; uniform or free scaling as a visible toggle; interpolation choice for raster (nearest, bilinear, bicubic).
- [ ] 183. Text: edit in place on the canvas; baseline and bounding-box guides while moving; outline and shadow.
- [ ] 184. Bezier: tap a node to switch corner and smooth; close the path by tapping the first node; add, delete and split nodes on an existing path.
- [ ] 185. Ruler and Measure merged: a measurement pinned as a dimension line in real-world units; angle (three points), area and perimeter; copy a measurement into a text label.
- [ ] 186. View: flip the view horizontally (a mirror to check proportions, the drawing unchanged); greyscale view; zoom presets (fit, 100 %, fill) and tap to reset rotation.
- [ ] 187. Layers: larger thumbnails with a size setting; swipe a row to hide, lock or alpha-lock; pinch rows to merge; long-press a stroke to jump to its layer; colour labels with a filter.
- [ ] 188. Colour: a longer history with pinned favourites; hex, RGB and HSB values editable with copy and paste; disc, square and slider pickers; a value (greyscale) check.

## Phase 17 — Ink and brush engine

Goal: brushes as rich as Procreate's and Krita's. Data before editor: the brush model gains its settings first, the studio edits them, import fills them.

- [ ] 189. Pressure curve per pen and brush, edited as a curve (reusing the easing curve editor), with a minimum width so light pressure never vanishes and pressure-to-opacity besides pressure-to-width.
- [ ] 190. Smoothing modes: streamline (lag), pulled string, average of the last N points, each with its own setting; end smoothing (no hook when the pen lifts); pressure smoothed separately from position; the stabiliser shown as a lag string while drawing.
- [ ] 191. Custom width profile drawn as a curve, besides uniform and tapered.
- [ ] 192. Brush taper per brush: length and size at start and end, linked to pressure or not.
- [ ] 193. Size and opacity remembered per brush, optionally linked; size steps on the rail for quick jumps.
- [ ] 194. Shape dynamics: tip rotation from direction, tilt or random; roundness from tilt.
- [ ] 195. Colour dynamics: hue, saturation and brightness jitter per stamp and per stroke, linked to pressure or tilt.
- [ ] 196. Tilt shading: the stylus flat paints wide and light, upright narrow and dark.
- [ ] 197. Grain: a paper texture inside the stroke, moving with it or fixed to the canvas, with scale, depth and contrast.
- [ ] 198. Dual brush: a second tip masking the first, for dry-media edges.
- [ ] 199. Flow versus opacity (per stamp or per stroke), build-up and wet edges.
- [ ] 200. Colour mixing: paint picks up and drags what is beneath, extending the wet engine.
- [ ] 201. Brush studio: every setting above in one editor, in sections, with a test pad using pressure and a live preview of spacing, jitter and scatter; brushes saved as presets and shared as files.
- [ ] 202. Brush import: tips from Photoshop `.abr` files and shapes and grains from Procreate `.brushset` files.
- [ ] 203. Vector art and pattern brushes: a shape stretched along the path, or repeated along it with corner pieces.
- [ ] 204. Vector line cleanup while drawing: cut a stroke's overshoot at the nearest crossing line; a pen that joins a stroke ending near another line.

## Phase 18 — Layers, colour, selection and guides

Goal: the structures the new tools draw into. Groups and adjustment layers come before tools that use them (gradient map, smart fill); soft selections before clone and pattern painting.

- [ ] 205. Layer groups that fold, with group opacity and blend, and clipping to a group.
- [ ] 206. Adjustment layers: hue/saturation, curves, levels, colour balance and gradient map, non-destructive on the layers beneath.
- [ ] 207. Layer effects: outline (stroke), drop shadow and glow.
- [ ] 208. "Select layer contents" as a selection.
- [ ] 209. Feathered (soft-edged) selections for raster editing.
- [ ] 210. Selection painting with a brush (quick mask).
- [ ] 211. Magic wand: select by same colour, width, pen type, layer, or connected strokes; "select similar" on the selection strip.
- [ ] 212. Smart lasso snapping to stroke edges; named selections saved and reselected.
- [ ] 213. Colour from anywhere on screen, including the reference window and photos.
- [ ] 214. Harmony lock: harmony colours follow the main colour as it changes; colour assist showing the complement and the value against neighbours.
- [ ] 215. Palette sorting by hue or value.
- [ ] 216. Perspective grids with 1, 2 and 3 points, vanishing points dragged on the canvas, pen snapping to them.
- [ ] 217. Parallel and concentric (radiating) line guides; an ellipse guide at any angle.
- [ ] 218. Guides saved with the document and toggled per frame.
- [ ] 219. Symmetry: mirrored radial (kaleidoscope); axis moved and rotated on the canvas; symmetry limited to a selected frame or region.
- [ ] 220. Reference window: a floating image or second view kept on screen while drawing.

## Phase 19 — Paths, shapes and text

Goal: vector editing at the level of Illustrator and Affinity. Node editing (Phase 16) comes first; these build on it.

- [ ] 221. New shape kinds: polygon (3–12 sides), star (points and inner radius), rounded rectangle, arc, pie, ring and spiral.
- [ ] 222. Curvature mode for the Bezier tool: tap points and smooth curves form through them.
- [ ] 223. Simplify a path with a live slider and node count; join two open paths at their nearest ends; reverse a path.
- [ ] 224. Offset path (inset or outset), reusing the contour code in `ShapeBuilder.kt`; outline stroke (a stroke becomes an editable filled shape).
- [ ] 225. Repeat a shape along a line, a grid or a circle, linked to the original.
- [ ] 226. Comic bubbles: several tails, curved tails, tails attached to the speaker.
- [ ] 227. Line weight correction: thicker outer contours and thinner interior lines on a selection.
- [ ] 228. Text inside a shape, sized to fit; text on a path offset along it and flipped to the other side.
- [ ] 229. Warped text (arc, wave, flag) and text on a curved, distorted baseline for sound effects.
- [ ] 230. Text style presets (font, size, colour, OpenType features).

## Phase 20 — New drawing tools

Goal: tools Fern doesn't have yet. Each builds on the phases above.

- [ ] 231. Lasso fill: draw a loop and it fills with the current colour.
- [ ] 232. Colour replace brush: paint over strokes to change their colour without moving them.
- [ ] 233. Palette from image: 5–8 representative colours from an image or a selection into the saved colours.
- [ ] 234. Knife on the rail: cut through shapes along a drawn line (the shape builder's knife as a direct tool).
- [ ] 235. Gradient tool: drag to lay a linear, radial or angular gradient on a raster layer or selection, with stops on the canvas; more than two stops and radial and angular kinds in the gradient fill too.
- [ ] 236. Pattern and texture fills from stamp-brush tips; fill by reference layer (line art bounds the fill on the current layer); fills grown slightly under the line to remove halos.
- [ ] 237. Smart fill for colouring: fill every closed area of the line art with flat colours at once, then recolour by tapping.
- [ ] 238. Width tool: drag on a stroke to thicken or thin it at that point, blending along the stroke.
- [ ] 239. Blend tool: N in-betweens between two shapes, in shape and colour (`Tweening.kt`).
- [ ] 240. Vector smudge and push: move stroke points with a soft circular brush (liquify for vector ink).
- [ ] 241. Line cleanup: replace selected rough strokes with one clean averaged stroke.
- [ ] 242. Ink trap: tighten rounded corners on a selection of strokes.
- [ ] 243. Hatching: fill a region with parallel or cross-hatched lines (angle, spacing, wobble).
- [ ] 244. Perspective pen: strokes snap to the active vanishing points while drawing.
- [ ] 245. Vector scatter: copies of a symbol or shape scattered along the path with size and rotation jitter.
- [ ] 246. Pattern stamp: paint a repeating pattern or texture on a raster layer.
- [ ] 247. Clone and heal brushes on raster layers.
- [ ] 248. Vector blur and smudge: soften vector ink by rasterising it under the brush, with a confirm.
- [ ] 249. Annotate: arrows, callouts and comments on their own layer, hidden on export.

## Phase 21 — Rig and animation data model

Goal: the foundation for Spine-level rigging and keyed animation. Today a bone is a head and a tail point, a pose is a full copy of every bone per frame, a stroke belongs to one bone, and only rotation and position exist. Everything in Phases 22–27 depends on this phase.

- [ ] 250. Local bone transform: rotation, x, y, scale x/y and shear x/y relative to the parent, with inherit settings (rotation, scale, reflection) per bone.
- [ ] 251. Timelines: per bone, separate keyed tracks for rotate, translate, scale and shear, each key with its own curve (linear, stepped, Bézier). The per-frame pose becomes the baked result, and existing documents convert to keys.
- [ ] 252. Setup pose versus animated pose: a Setup mode edits the rest pose; animations store only differences from it.
- [ ] 253. Several named animations per rig (idle, walk, jump), each with its own length and timelines.
- [ ] 254. Bone tree panel: names, colours, folders, hierarchy; tapping ink selects its bone.
- [ ] 255. The same keyed timelines for non-rig properties: layer and object opacity, position, scale, rotation and colour with easing.

## Phase 22 — Animation on timelines

Goal: animation tools that use the Phase 21 model, before rig-specific tools build on them.

- [ ] 256. Dopesheet with one row per timeline (bone rotate, translate, scale, shear; slot attachment and colour; draw order; events), collapsible per bone; key filters by bone or channel.
- [ ] 257. Graph editor for every curve, not only angle: separate x and y curves, tangent handles, stepped keys, copy and paste of curves, curve presets.
- [ ] 258. Auto-key: changing a bone or property records a key; key all channels or only changed ones.
- [ ] 259. Offset keys in time across several bones with one drag (a tail wave).
- [ ] 260. Per-layer timing: a layer holds its drawing for its own number of frames.
- [ ] 261. A camera: a framed view panning, zooming and rotating over time, used by playback and export.
- [ ] 262. Play range (in and out markers); playback speed; loop helpers (an in-between from the last frame to the first); time remapping of a range (slow, fast, reverse).
- [ ] 263. Scrubbing with audio, and the soundtrack's waveform under the frames with markers.
- [ ] 264. Onion skin: separate colours and opacity for past and future, keyframes only; rig ghosting (previous and next poses faint).
- [ ] 265. Motion paths of selected objects or bone tips across frames, edited by dragging.
- [ ] 266. In-between preview as ghost outlines before committing; shape-tweening pairs shown as numbered markers and re-paired by hand.
- [ ] 267. Light table: pin any frames as tracing references, beyond the onion skin's neighbours.
- [ ] 268. Frame thumbnails that scale with the panel.
- [ ] 269. Squash and stretch with volume preserved, in one step.
- [ ] 270. Timing chart: draw the spacing chart on a key and in-betweens follow it.
- [ ] 271. Motion blur and smear frames: fast ink stretched along its motion, and a smear brush.

## Phase 23 — Slots, attachments and draw order

Goal: Spine's display model. Builds on Phase 21.

- [ ] 272. Slots: a slot hangs from a bone and shows one attachment at a time; ink and images go into slots instead of binding to bones directly. Existing bound ink converts to one slot per bone.
- [ ] 273. Region attachments (an image or group of strokes) in slots.
- [ ] 274. Attachment timeline: swap a slot's attachment over time (open hand, fist, pointing).
- [ ] 275. Draw order timeline: change which slot is in front over time.
- [ ] 276. Slot colour and alpha timelines, with a second dark tint colour.
- [ ] 277. Point attachments (a named point on a bone) and bounding-box attachments (polygons usable by game logic for hits).
- [ ] 278. Clipping attachments: a polygon clipping the slots after it.
- [ ] 279. Skins on attachments: a skin replaces some attachments and leaves the rest; several skins combined (hat, shirt, shoes). Layer skins become this.
- [ ] 280. Slot and attachment panel: the tree of bones, slots and attachments with visibility and draw order.
- [ ] 281. Rig from layers: each layer of an imported PSD becomes a slot with an attachment on a new bone, in place.

## Phase 24 — Meshes and weights

Goal: smooth bending that survives export. Builds on Phase 23.

- [ ] 282. Mesh attachments: a drawing with a hull and inner vertices, triangulated, deforming smoothly.
- [ ] 283. Mesh tool: add, delete and move vertices, draw edges, triangulate from the outline, trace a hull from the drawing's alpha.
- [ ] 284. Vertex weights across several bones, with a weights panel per bone and a lock per bone.
- [ ] 285. Weight tools: paint add, subtract, replace and smooth; automatic weights by distance or by heat (inside the shape, so an arm doesn't pull the torso); normalise; prune small weights; mirror left to right. Today's point weights convert to this.
- [ ] 286. Deform timeline: vertex offsets keyed over time on top of bone deformation (today's per-frame mesh warp becomes this).
- [ ] 287. Linked meshes: attachments sharing one mesh, its weights and deform keys.

## Phase 25 — Constraints

Goal: rig behaviour beyond direct posing. Builds on Phases 21 and 24.

- [ ] 288. IK constraint as an object: target bone, one- or two-bone chain, mix, softness and bend direction; an exact two-bone solver with a pole direction (the CCD solver stays for longer chains); IK/FK switch and blend per animation; the IK target dragged on the canvas.
- [ ] 289. Joint limits shown as arcs on the canvas and dragged at their ends.
- [ ] 290. Transform constraint: copy rotation, position, scale or shear between bones with offsets and mix.
- [ ] 291. Path constraint: bones follow a drawn path, with position, spacing (length, fixed, percent) and rotation modes.
- [ ] 292. Physics constraint: inertia, strength, damping, mass, wind and gravity per bone, in playback and export. Dynamic bones become this, with their settings and a preview while scrubbing.
- [ ] 293. Constraint order list and keyed constraint mix.
- [ ] 294. Constraint tool: add each constraint by picking bones on the canvas.

## Phase 26 — Rigging workflow

Goal: rigging fast and reusable. Builds on Phases 21–25.

- [ ] 295. Bone names and side colours (left and right) on the canvas; binding shown and changed by tapping a stroke.
- [ ] 296. Mirror a rig, its attachments, poses and animations from left to right, matching `_L` and `_R` names.
- [ ] 297. Pose library: named poses with thumbnails, applied whole or to selected bones.
- [ ] 298. Rig controls: a slider or on-canvas handle driving several bones (fist, blink, mouth shape), keyed as one value.
- [ ] 299. Events: named timeline markers with int, float, text and audio payloads, used by export and game logic.
- [ ] 300. Animation mixing and tracks: preview a crossfade between two animations, or a second track adding a blink or aim over a walk.
- [ ] 301. Retarget an animation to another rig with a bone map; copy animations between documents.
- [ ] 302. Rig checker: unbound ink, empty slots, bones without influence, weights not summing to one, joints past their limits.
- [ ] 303. Auto-rig: biped, quadruped or face templates fitted by dragging markers.
- [ ] 304. Walk and run cycle generator for a rig, with stride and bounce.

## Phase 27 — Spine and DragonBones round trip

Goal: rigs leave and come back without loss. Today export bakes each bone's ink into one image (bending becomes rigid) and import keeps only rotation and translation.

- [ ] 305. Spine export with slots, attachments, meshes, weights, skins, constraints, events, draw order and colour timelines, and a packed atlas.
- [ ] 306. Spine import of all of the above (JSON 3.8 and 4.x).
- [ ] 307. Spine binary (`.skel`) export; export settings (scale, nonessential data, pretty JSON, chosen animations).
- [ ] 308. DragonBones export and import at the same level.
- [ ] 309. Runtime preview: play the exported data back as an engine would, before handing it over.

## Phase 28 — Face and 2.5D rigging

Goal: Live2D-style turns from flat art. Builds on meshes, deform keys and rig controls.

- [ ] 310. Parameters (head angle, mouth open) blending between keyed mesh shapes.
- [ ] 311. Warp and rotation deformers nested on a face.
- [ ] 312. Blink, breath and lip-sync parameters linked to the mouth chart and soundtrack.

## Phase 29 — 3D

Goal: 3D as a drawing aid and an asset, not only a painted model.

- [ ] 313. Orbit camera: double-tap to frame, reset, snap to front, side and top.
- [ ] 314. Painting: brush circle on the surface at its painted size; painting past UV seams so no lines show.
- [ ] 315. Lighting presets (studio, rim, flat).
- [ ] 316. Placed models re-rendered at a new angle on the canvas without opening the editor (today a placed model is a still image).
- [ ] 317. Layers for 3D painting with opacity and blend; normal, height and emission channels.
- [ ] 318. Projection painting: paint on the screen and project it onto the model.
- [ ] 319. Toon shading and outline rendering.
- [ ] 320. Turntable export (GIF or MP4).
- [ ] 321. A model as a drawing reference whose camera matches the canvas perspective guide.
- [ ] 322. The 3D mannequin and hand posed per animation frame, rendered into each frame as a reference layer.
- [ ] 323. Extrude and lathe: drawn shapes become 3D objects.
- [ ] 324. Scene blocks: several primitives placed together to block out a room or prop.
- [ ] 325. Full-body 3D mannequin, extending the hand and head.

## Phase 30 — Game logic tools

Goal: building and debugging games from drawings. Builds on Phase 14 and on rig events and bounding boxes (Phases 23 and 26).

- [ ] 326. Debug overlay while playing: colliders, hitboxes and object names; the last rule fired per object; step one frame at a time while paused.
- [ ] 327. Collider tool: draw boxes, circles and polygons directly on an object.
- [ ] 328. Path follower editor: speed per segment and pauses.
- [ ] 329. Sound actions from rules, and rig events triggering sounds and rules.
- [ ] 330. Prefabs: reusable objects whose changes reach every copy.
- [ ] 331. Particle emitter: drawn particles with speed, spread and lifetime.
- [ ] 332. Tilemaps: levels painted from tiles cut from a drawing, collision per tile.
- [ ] 333. Scenes: several levels with transitions.

## Non prioritaire

What is left of the roadmap that can be built without an external platform, set aside by the
project owner: not to be started unless asked. Numbers are kept from the phase each item came from.

- [~] 47. Search text labels and jump to them; handwriting search ⏸ (needs item 67) — from Phase 4
- [ ] 148. Command-line batch interface (competitor parity, Aseprite)
- [ ] 115. iOS, Windows, macOS and web versions (separate apps, outside this Android project) — from Phase 13
- [ ] 165. Publishing (link, gallery, Android/web export) — from Phase 14
- [ ] 166. Moderation and safety for shared content — from Phase 14
