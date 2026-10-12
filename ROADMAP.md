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
- [~] 47. Search text labels and jump to them; handwriting search ⏸ (needs item 67)

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
- [ ] 91. Raster selections, masks, clipping masks, alpha lock
- [ ] 92. Adjustments: curves, levels, hue/saturation, liquify, warp
- [ ] 93. Full Photoshop blend modes and adjustment layers
- [x] 94. Reference layers (fills take their boundaries from them), gap-closing bucket fill (areas closed by several strokes, gaps up to ~8 px bridged, within the view)
- [ ] 95. PSD / Procreate / Krita import and export

## Phase 11 — Professional vector & design

- [x] 96. Pen tool with Bézier nodes (tap for corners, drag for smooth handles, tap the first node to close; finish, remove last node, cancel); boolean operations on selected closed shapes (union, intersect, subtract, exclude; holes kept). Finished paths aren't re-editable as curves (node editing works on their points)
- [x] 97. Stroke styles — dashed lines; caps (round, square, flat), joins (round, sharp, bevelled) and width profiles (even, taper either or both ends), saved per stroke and in SVG/Lottie exports (tapers drawn on canvas and in images only)
- [~] 98. Text on a path (from a selected line; letters follow and turn with it; SVG textPath), outlined text; no OpenType feature controls (the platform's generic fonts offer few)
- [~] 99. Smart guides (a moved selection snaps edges/centres to nearby objects, guide lines shown), numeric transform dialog (X, Y, width, height, proportions, rotation), live shape dimensions; no screen rulers
- [~] 100. Shared colour styles (a saved colour changed everywhere it's used, opacity kept), symbol library across documents (save a selection, place copies); placed symbols are copies, not linked instances
- [x] 101. Image trace: a selected image's dark shapes (Otsu threshold) become filled vector outlines, holes kept, in the active colour over the image; one colour (no colour separation)
- [~] 102. CMYK entry in the colour picker (uncalibrated device CMYK); no spot colours, ICC colour management or CMYK PDF (the platform PDF writer is RGB-only)
- [~] 103. EPS export (paths, fills, caps, joins, dashes; checked by rendering); no AI import/export, Figma ⏸ (external platform); SVG export done in 57

## Phase 12 — Specialty modes

- [x] 104. Pixel art — grid-aligned pixel pen; indexed palette (pixels snap to saved colours; reduce a selection to them; palette swaps via 100); sprites are animation frames exported as sprite sheets (80)
- [~] 105. Comics — comic page template, speech bubbles, screentones
- [x] 106. Notes — sticky notes, connectors between them (68), mind-map and planner templates (72)
- [~] 107. Whiteboard — links in text labels open in the browser or another document; PDF pages imported onto the canvas to annotate; no embedded video (needs a device to verify a live video surface)
- [~] 108. Posable mannequin in 2D (a rigged figure posed with the bone tool); no 3D painting
- [ ] 109. AR placement and tracing ⏸ (ARCore is a Google Play Services platform)
- [ ] 110. Generative AI ⏸

## Phase 13 — Ecosystem

- [~] 111. Scripting: sandboxed drawing scripts (see 164); no plugin API
- [ ] 112. Marketplace ⏸ (hosted service)
- [ ] 113. Community gallery and live-streaming ⏸ (hosted service)
- [ ] 114. Team workspaces ⏸
- [ ] 115. iOS, Windows, macOS and web versions (separate apps, outside this Android project)

## Competitor parity additions (116–148)

Found by comparing with the official feature pages of Procreate, Clip Studio Paint, Krita,
Concepts, GoodNotes, Notability, Infinite Painter, Aseprite, Spine and Affinity Designer.

| # | Feature | Seen in | Phase | Status |
|---|---|---|---|---|
| 116 | Stroke stabiliser | Procreate, Krita, Affinity, Clip Studio | 1 | done |
| 117 | Velocity dynamics | Clip Studio | 3 | done |
| 118 | Drawing assist / live snap | Procreate, Concepts | 3 | partly (shape recognition) |
| 119 | Colour harmonies | Aseprite, Affinity | 3 | done |
| 120 | COPIC and PANTONE libraries | Concepts, Affinity | 3 | not started (licensing) |
| 121 | Style picker | Affinity | 4 | not started |
| 122 | Warp, puppet warp, mesh transform | Procreate, Clip Studio | 4 | not started |
| 123 | Layer states / comps | Affinity, Clip Studio | 4 | not started |
| 124 | Multiple views / split screen | Affinity, Clip Studio | 4 | not started |
| 125 | Presentation mode | Concepts | 4 | done |
| 126 | Snapshots; delete empty/hidden layers | Affinity, Clip Studio | 4 | done |
| 127 | DXF export | Concepts | 6 | not started |
| 128 | Slices, @2x/@3x, batch export | Affinity | 6 | not started |
| 129 | Document signing, markup | Concepts | 6 | not started |
| 130 | Audio recording with notes | GoodNotes, Notability | 8 | partly (voice notes; not synced to strokes) |
| 131 | AI over notes | GoodNotes, Notability | 8 | ⏸ |
| 132 | Usage statistics | Clip Studio | 8 | not started |
| 133 | Customisable panels and QuickMenu | Krita, Procreate | 8 | partly (More menu) |
| 134 | Dopesheet and curve editor | Spine | 9 | not started |
| 135 | Free-form deformation, procedural animation | Spine | 9 | not started |
| 136 | Game-engine runtimes, texture packer | Spine | 9 | partly (sprite sheet) |
| 137 | Wrap-around tiled mode | Krita, Aseprite | 10 | not started |
| 138 | Filter / file layers, global selection mask | Krita | 10 | not started |
| 139 | HDR / OpenEXR, OpenColorIO | Krita | 10 | not started |
| 140 | Multiple brush engines, brush tagging | Krita | 10 | not started |
| 141 | Shape builder, knife, contour | Affinity | 11 | not started |
| 142 | Isometric panel, layout grids | Affinity | 11 | not started |
| 143 | Real-world scale and measurement | Concepts, Affinity | 11 | not started |
| 144 | UI constraints; QR code tool | Affinity | 11 | not started |
| 145 | Red/blue onion skin, frame tags, RotSprite | Aseprite | 12 | not started |
| 146 | 3D hands/heads, hand pose scanner | Clip Studio | 12 | not started |
| 147 | 3D texture maps, lighting, AR export | Procreate | 12 | not started |
| 148 | Command-line batch interface | Aseprite | 13 | not started |

## Phase 14 — Co-creation: animate, rig and build logic together

Shared, multiplayer parts need a server and are deferred ⏸; single-player parts can be built.

**Multiplayer animation and rigging**

- [ ] 149. Shared timeline with locks ⏸
- [ ] 150. Live presence on the timeline and rig ⏸
- [ ] 151. Shared rig library ⏸
- [ ] 152. Roles and permissions ⏸
- [ ] 153. Branches and merges for animations
- [ ] 154. Voice and text chat in the canvas ⏸

**From drawing to logic**

- [ ] 155. Turn a drawing into an object with properties and behaviours
- [ ] 156. Visual scripting (nodes / blocks)
- [~] 157. Drawn behaviours — a drawn path becomes motion (animate along path); hitboxes and links not started
- [ ] 158. State machines linking rig animations to logic
- [ ] 159. 2D physics
- [ ] 160. Input mapping (touch, stylus, gamepad, keyboard)
- [ ] 161. Play mode
- [ ] 162. Online multiplayer play ⏸
- [ ] 163. AI logic assistant ⏸
- [~] 164. Text scripting (sandboxed): a turtle-graphics drawing language run from the ⋮ menu, step-limited, no file or network access; drawing only
- [ ] 165. Publishing (link, gallery, Android/web export)
- [ ] 166. Moderation and safety for shared content
