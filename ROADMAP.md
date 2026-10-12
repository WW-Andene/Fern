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
- [ ] 7. Low-latency ink: front-buffered rendering (`GLFrontBufferedRenderer` / Ink API), under 20 ms pen-to-pixel
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
- [~] 18. Storage that scales to 1M strokes — thumbnail no longer re-rendered on every autosave; strokes, history and versions in a compact binary format (16 bytes per finger point instead of ~90); drawing appends to a journal instead of rewriting the file; stroke points held in primitive arrays (~41 bytes per point in memory instead of ~106). Probe: 100k strokes x 32 points load in 0.5 s, autosave after a stroke 34 ms (JVM); not yet measured at 1M or on a device (item 48)
- [x] 19. Undo history that survives restarts; version-history timeline with restore
- [x] 20. Document library: thumbnails, folders, search, sort, favourites, 30-day trash with restore
- [~] 21. Crash reporting — on-device reports, shared only by the user's choice; no analytics (by design)

## Phase 3 — Tools & expressive drawing

Goal: a full, professional tool set.

- [x] 22. Eraser — ink eraser (partial) and object eraser (whole strokes and images)
- [x] 23. Pen types: marker, pencil, highlighter, calligraphy, airbrush, crayon, pixel art
- [x] 24. Stylus pressure and tilt; palm rejection
- [x] 25. Shapes (line, rectangle, ellipse, arrow, speech bubble), vector fill, text, images
- [x] 26. Ruler/guide; opacity and blend modes; background styles
- [~] 27. Stylus eraser end and side button erase; no double-tap/squeeze gestures yet
- [x] 28. Shape recognition: draw-and-hold snaps to a clean line, rectangle or ellipse
- [~] 29. Brush engine — airbrush and crayon procedural textures; no stamp brushes or brush editor
- [~] 30. Colour workflow — eyedropper, touch-and-hold pick, recent colours, saved palette, .gpl import; no .ase import
- [~] 31. Quick tool switching — touch-and-hold eyedropper, keyboard shortcuts; no radial menu or favourite slots
- [~] 32. Erase modes — ink vs object eraser; no lasso eraser
- [~] 33. Rich text — bold, italic, four sizes, sticky notes, links; no fonts, alignment or wrapping boxes
- [~] 34. Gradients and pattern fills — fade fill, screentone fill; no two-colour or directional gradients
- [~] 35. Symmetry (mirror, four-way, radial) done; perspective guides not started
- [~] 36. Snapping — shapes snap to grid on grid/dot paper, 15° angles, square/circle; no snap-to-object

## Phase 4 — Editing, organisation & layers

Goal: anything drawn can be rearranged later.

- [x] 37. Selection: move, duplicate, delete, rotate, scale (strokes; images and text can be picked, moved and deleted)
- [x] 38. Minimap, zoom-to-fit (includes images and text), named pins
- [x] 39. Layers: add, reorder, hide, lock, opacity, merge, rename, delete, remove empty/hidden
- [x] 40. Lasso selection, select-by-colour, select-all-on-layer
- [x] 41. Recolour and change the width of a selection
- [~] 42. Z-order, align and distribute done; grouping not started
- [~] 43. Copy and paste within and between documents; not with the system clipboard
- [~] 44. Smooth and straighten selected strokes; no node editing
- [x] 45. Frames/artboards: named, navigable, exportable, presentable
- [ ] 46. Links between canvas regions and documents
- [~] 47. Search text labels and jump to them; handwriting search ⏸ (needs item 67)

## Phase 5 — Performance at scale

Goal: 120 fps at any zoom, with any amount of content.

- [ ] 48. Benchmark suite (10k / 100k / 1M strokes)
- [ ] 49. Spatial index (deliberately waiting for item 48's numbers)
- [~] 50. Level of detail — sub-pixel strokes drawn as one dot, sub-pixel point spacing decimated; no cached simplified geometry
- [ ] 51. Tile caching
- [ ] 52. GPU rendering path
- [ ] 53. Background loading / streaming of off-screen regions
- [x] 54. Large images and PDF pages downscaled to 2048 px
- [ ] 55. Battery and thermal checks

## Phase 6 — Import, export & sharing

Goal: work can leave the app and come back.

- [x] 56. Export PNG: whole document, a frame, the selected area, transparent background
- [x] 57. Vector export: SVG and multi-page PDF (one page per frame)
- [x] 58. `.fern` backup format: export and import a whole document
- [~] 59. Import PDFs to annotate; no drag-and-drop from other apps yet
- [~] 60. Share sheet and in-app time-lapse replay; replay can't be exported as video yet

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
- [ ] 68. Smart diagramming: connectors, flowchart cleanup
- [~] 69. Accessibility — button descriptions, canvas screen-reader description, left-handed layout; no high-contrast theme
- [ ] 70. Localisation (French first) and right-to-left
- [~] 71. Two-finger tap undo, three-finger tap redo, toolbar top or bottom; toolbar not yet customisable
- [~] 72. Gesture guide on first launch, templates (graph, dot, lined, comic, storyboard); no planner or mind-map templates
- [~] 73. Dark mode for the UI; no dark paper preset
- [~] 74. Keyboard shortcuts, mouse wheel and trackpad; no dedicated large-screen layout
- [ ] 75. Multi-window drag-and-drop
- [ ] 76. Generative assists ⏸ (hosted AI)
- [ ] 77. Release work: Play Store listing, privacy policy, staged rollouts

## Phase 9 — Animation & skeletal rigging

- [x] 78. Flipbook frames: insert blank or copied, delete, step
- [x] 79. Onion skin, playback at 6/8/12/24 fps, loop and ping-pong
- [~] 80. Export animated GIF and sprite sheet; no MP4, WebM or APNG
- [~] 81. Automatic in-betweens and motion along a drawn path; no keyframe easing or graph editor
- [~] 82. Shape tweening — strokes morphed pairwise in in-betweens
- [ ] 83. Bones and skeletons
- [ ] 84. Mesh deformation with weights
- [ ] 85. Inverse kinematics, constraints, physics
- [ ] 86. Skins and animation blending
- [ ] 87. Spine / Lottie / DragonBones export and import
- [ ] 88. Audio track with lip-sync and an X-sheet

## Phase 10 — Raster painting

- [ ] 89. Raster layers with tiled pixel storage, up to 16-bit
- [ ] 90. Pixel brush engine: wet mixing, smudge, blur, ABR/Procreate brush import
- [ ] 91. Raster selections, masks, clipping masks, alpha lock
- [ ] 92. Adjustments: curves, levels, hue/saturation, liquify, warp
- [ ] 93. Full Photoshop blend modes and adjustment layers
- [ ] 94. Reference layers, gap-closing bucket fill
- [ ] 95. PSD / Procreate / Krita import and export

## Phase 11 — Professional vector & design

- [ ] 96. Pen tool with Bézier nodes; boolean operations
- [~] 97. Stroke styles — dashed lines; no custom caps, joins or width profiles
- [ ] 98. Text on a path, outlined text, OpenType features
- [ ] 99. Rulers, dimensions, numeric transforms, smart guides
- [ ] 100. Reusable symbols and shared colour styles
- [ ] 101. Image trace
- [ ] 102. CMYK, spot colours, ICC, print-ready PDF
- [ ] 103. AI / EPS / Figma import and export (SVG export done in 57)

## Phase 12 — Specialty modes

- [~] 104. Pixel art — grid-aligned pixel pen; no indexed palettes or sprite editing
- [~] 105. Comics — comic page template, speech bubbles, screentones
- [~] 106. Notes — sticky notes; no connectors or mind maps
- [~] 107. Whiteboard — links in text labels open in the browser; no embedded video or documents
- [ ] 108. 3D painting and posable mannequins
- [ ] 109. AR placement and tracing
- [ ] 110. Generative AI ⏸

## Phase 13 — Ecosystem

- [ ] 111. Plugin and scripting API
- [ ] 112. Marketplace
- [ ] 113. Community gallery and live-streaming
- [ ] 114. Team workspaces ⏸
- [ ] 115. iOS, Windows, macOS and web versions

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
- [ ] 164. Text scripting (sandboxed)
- [ ] 165. Publishing (link, gallery, Android/web export)
- [ ] 166. Moderation and safety for shared content
