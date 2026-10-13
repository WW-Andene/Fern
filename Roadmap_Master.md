# Fern roadmap — master

Everything left to build in Fern, features and design together, in build order: phases are in
dependency order, and within a phase items are in build order. It replaces `ROADMAP.md` and
`DESIGN_ROADMAP.md`. Finished items were removed; their history is in git.

Item numbers are permanent IDs, not positions: numbered items came from the feature roadmap,
`D` items from the design roadmap, 334–354 from the October 2026 tool audit, and 355 onward from
[Product_Review.md](Product_Review.md), which holds the evidence for the rebuild phases. Code
comments citing "roadmap item N" or "design roadmap DN" still point here.

**Status key:** `[~]` partly done (the note says what exists) · `[ ]` not started ·
**⏸** deferred: needs an external platform, off-limits for now (CLAUDE.md §8.1).

**Ordering rules.**

1. **Trust before features.** Nobody uses a drawing app that loses work.
2. **Data model before UI.** A model built late means rewriting every tool on top of it.
3. **Foundations before the features that sit on them.** The UI parts, guides, brush engine,
   colour system and rig model are rebuilt before the tools that use them.
4. **Performance before scale features.**
5. **Polish last, but not never.**

**Ground rules (set by the project owner).** They hold for every item below and override any
item that seems to say otherwise.

- **Phone first, for now.** The phone held in one or two hands is the reference device. Every
  item is designed, built and judged on a phone first: thumb reach, small screens, portrait and
  landscape, a finger as often as a stylus. Tablet, foldable, keyboard and mouse work comes after
  the phone version of the same item and must never make the phone version worse. Items that
  only matter on larger devices are marked **(after phone)**.
- **100 % bilingual, English and French.** Every word the user can see or hear exists in both
  languages and reads naturally in each, with nothing left in only one: labels, content
  descriptions for TalkBack, errors, script and import messages, default names, help, examples
  and store text. No item is done until both languages are.

**Absolute priorities.** Every item here serves at least one of them. When two items conflict,
the earlier priority wins.

1. **Fast and easy to pick up, complete and deep to master.** A first stroke takes zero taps,
   and every advanced feature can be reached without slowing down the basic ones.
2. **Accessibility.** The app works with TalkBack, Switch Access, a keyboard, large text, colour
   blindness and one hand.
3. **Clean and efficient.** The canvas comes first, the chrome stays quiet, and the same thing
   never appears in two places.
4. **User friendly.** The app speaks plainly, shows what happened and offers a way back.
5. **Zero friction, zero frustration.** Nothing is lost, nothing blocks, nothing has to be hunted for.
6. **Tool use as second nature.** Muscle memory comes first: gestures, the stylus, keys and
   positions that never move.
7. **As few sub-menus as possible.** At most one level between the canvas and any action.
8. **Entirely customisable.** Every surface, shortcut and gesture can be rearranged, and the
   layout can be saved and shared.

Every dimension follows the CLAUDE.md §7 scale. Update this file in the same commit as the
work that changes an item's status; remove an item once it is done.


---

## Owner decisions (October 2026)

Answers to the questions in [Product_Review.md](Product_Review.md) §8:

- **Users: everyone.** "Easy to come in, better to master": the first minute stays simple and every tool goes deep.
- **Devices: phone first, then tablet.**
- **Rigging: mostly Spine's model.**
- **CI: only when the owner asks to merge to `main`.** Item 355 (CI on every branch) is dropped.
- **No deadline.**
- **No AI features.**

Set aside for the owner (needs a device and a person):

- [ ] D71. **Real-device passes** on phones first (a small and a large phone), with TalkBack,
  Switch Access, a stylus and one hand, in English and in French; keyboard and tablet passes after.
  The results go in the commit that closes each phase.

---

## Phase 1 — UI foundation (rebuild)

The app's look and layout rebuilt on the CLAUDE.md §7 scale, so every later screen is built from compliant parts. Comes first: every phase below adds UI.

- [~] 336. **Fern components.** Fern versions of every control in use, with sizes on the scale instead of Material's: text field (56 tall today), button (40), slider (20 px thumb), switch (52 wide), chip, menu (112 minimum width), dialog (280 minimum width) and list row. Raw Material controls are no longer used directly in UI code, checked by a test.
  Done: `FernComponents.kt` - `FernTextButton` (48 tall), `FernSlider` (16 dp thumb), `FernSwitch` (48 x 24 track, 16 dp thumb) and `FernTextField` (48 tall for one line); every call site moved to them. Left: menus (Material's 112 dp minimum item width), list rows, and a check that fails on direct Material calls.
- [~] 338. **Chrome budget.** A fixed limit per surface, enforced in code: the rail holds a set number of tools, the properties bar shows at most one row of the active tool's main settings with the rest one tap away (D32), no strip grows with use. Today the pen's bar holds about 70 controls and the toolbar about 27 buttons in one scrolling strip.
  Done: the top bar is a fixed set that never scrolls (undo, redo, layers, play once there is a game, search, export, More); everything else is in the More panel in labelled sections. Left: the properties bar, still one scrolling row of every setting of the active tool.
- [~] D42. **One panel pattern.** A header (title, search or filter, the main action,
  collapse), a body with rows that can be dragged, swiped and long-pressed for the same actions
  everywhere, and a footer only when it's needed.
  Written, not yet compiled or tested: `FernPanel` gains a header main action, an optional filter field and (phone) a drag handle sizing the sheet between a quarter and three quarters of the screen; `PanelRow` is the shared row: tap, long-press, swipe left to delete (with a delete action for screen readers). Panels adopt them as they're reworked in D43-D47.
- [~] D25. **UI density and size.** Compact, comfortable or large, scaled on the §7 steps,
  separate from the system font scale.
  Written, not yet compiled or tested: Settings > Appearance > Interface size: Standard, Large (×1.25), Largest (×1.5, which keeps the §7 steps: 16→24, 32→48); it scales every dp while text still follows the system font size on top. No size below standard, so touch targets never shrink; a compact mode for experts is D90.
- [ ] D90. **Expert density.** An optional compact mode for experienced users showing more tools
  at once on the phone, without shrinking hit areas below 48 dp.
- [ ] D58. **Visual identity.** A Fern brand colour, an icon set drawn on one grid with one
  stroke weight (instead of mixed Material icons), and a distinctive yet quiet chrome that lets
  artwork dominate.
- [ ] D59. **Optional sound cues** for the paper feel (pencil, marker) and confirmations, off by
  default.
- [ ] D52. **Phone layouts first, then responsive.** Phone portrait and landscape are designed first and kept the reference. Then responsive layouts for compact, medium and expanded widths (Material window size
  classes): a bottom rail and sheets on phones, side rail and docked panels on tablets, several
  panels at once on wide screens.
- [ ] D55. **One-hand mode** (phone priority). Everything reachable in the bottom third of a phone; the rail and
  the quick menu mirrored for the left hand.
- [ ] D56. **Orientation without loss.** Rotating keeps the view centre, the open panel and the
  selection, with no relayout jump.
- [~] D32. **Progressive disclosure.** Advanced settings stay one tap away inside the same panel
  (a "More" disclosure), never a separate dialog.
  Written, not yet compiled or tested: `FernDisclosure.kt` (`MoreOptions`, `MoreInline`), expanded state announced to screen readers and kept across rotation. Used for the drawing tools' line options and stabiliser in the properties bar, and for an object's physics, hitbox and links in its panel; other panels can adopt it as they're reworked (D42-D47).
- [~] D33. **Pro surfaces** for the deep features: dopesheet and timeline docked, the node or
  block editor for game logic as a panel, the script editor with syntax highlighting, line
  numbers and inline errors.
  Written, not yet compiled or tested: `CodeEditor.kt` for the drawing and object scripts: line numbers, keywords/numbers/texts/comments coloured from the theme, checked on every keystroke with the error's line number in red and its message below. The dopesheet and X-sheet open as docked panels; the rules (blocks) were already a panel (D10). Merging timeline, X-sheet and dopesheet into one strip is D45.
- [~] D43. **Layers panel.** Thumbnails, visibility and lock on the row, opacity with a drag on
  the row, the blend mode inline, drag to reorder or into groups, swipe to delete with Undo,
  multi-select, and layer comps (roadmap item 123) in the same panel.
  Written, not yet compiled or tested: each row has a thumbnail of the layer's strokes, visibility, reference and lock, its opacity set by dragging sideways on the name (shown as a %), tap to make active, swipe left to delete with Undo (raster layers still ask first); "New layer" sits in the header with a filter by name; blend inline and layer comps were already in the panel. Not done: drag-to-reorder (up/down arrows remain), layer groups and multi-select.
- [~] D44. **Brush and preset library.** A visual grid with live stroke previews, tags and search
  (roadmap item 140), favourites pinned to the rail, and editing beside the preview, not in a
  separate dialog.
  Written, not yet compiled or tested: `BrushLibrary.kt`, opened from the pen button in the properties bar (replacing the pen-kind menu): tiles for each pen kind (a sample stroke in the current colour suggesting its look) and each saved stamp brush (its real stamped preview), filtered by name or tag; tap picks, the pin adds it to a free favourite slot, Edit opens the brush editor, now a panel beside the canvas (D27).
- [~] D45. **Timeline panel.** Frames, onion skin, tags and the X-sheet in one dockable strip with
  scrubbing, play and loop at the edge of the hand. It replaces the animation toolbar group and
  the separate X-sheet and dopesheet dialogs.
  Written, not yet compiled or tested: the animation strip (already docked at the edge opposite the toolbar, with frames, onion skin, tags, play and loop/ping-pong) gains a scrubber, "Frame n of N", to drag through frames; its rarer tools (pose blending, dopesheet, sway, branches, X-sheet, easing, fps) sit behind More. The X-sheet and dopesheet open as docked panels (D33) rather than being merged into the strip.
- [~] D46. **Objects and logic panel.** Object list, properties, behaviours, rules (blocks) and
  script in one panel with tabs, instead of the object dialog opening states, rules and script
  dialogs on top of each other.
  Written, not yet compiled or tested: the object panel has tabs Object, States, Rules and Script (the editors were split out of their dialogs as `StateMachineEditor`, `RulesEditor`, `GameScriptEditor`), and chips for every object to switch between them without closing; nothing stacks any more.
- [ ] D47. **Inspector for the selection.** Transform, style, constraints (roadmap item 144) and
  the measurement for whatever is selected, always in the same place.

---

## Phase 2 — Groundwork for the tools

Make the tool phases cheap and safe to build: every tool currently goes through the same few very large files.

- [ ] 167. Split per-tool gesture handling out of `DrawingCanvas.kt` into one handler per tool (press, move, release, cancel), and per-tool state out of `CanvasState.kt` (CLAUDE.md §4.5): both are thousands of lines and every new tool touches the same `when` blocks.
- [ ] 168. Gesture tests: fingers landing and lifting at different times, a pan becoming a pinch, stylus with a resting palm, long press versus a slow stroke.
- [ ] 171. On-canvas handle framework: one way to draw, hit-test and drag handles (shape parameters, joint limits, gradient stops, vanishing points, IK targets, constraint targets) instead of each tool doing its own.
- [ ] 172. On-canvas numeric readout and entry: any handle can show its value and accept a typed one.
- [ ] 357. **Performance benchmarks on two phones** (one low-end, one high-end): pen-to-pixel latency, frame time at 100,000 and 1,000,000 strokes, panel opening time, tracked over releases. Needed before the brush engine (Phase 5) adds per-stamp work. Review finding O9.

---

## Phase 3 — Shell, interaction and customisation

Switching, adjusting and arranging without looking. Builds on Phases 1 and 2.

- [ ] D78. **Full-screen canvas on demand.** One gesture or tap hides every bar and panel; a
  second brings them back exactly as they were. Edge-to-edge drawing under the system bars, with
  the gesture navigation area protected from stray strokes.
- [ ] D79. **Undo and redo always in thumb reach** on the phone, as buttons as well as gestures,
  with a long-press on undo to scrub back through the history.
- [ ] D80. **Last-tool toggle.** One tap (or gesture) swaps between the current and the previous
  tool, with each keeping its own size, colour and settings.
- [ ] D81. **Per-tool memory.** Every tool remembers its last size, opacity, colour and options;
  the eraser never inherits the pen's size by surprise.
- [~] D15. **A gesture map you can change.** Taps with two, three or four fingers; a pinch with a
  rotation lock; long-press on the canvas as the eyedropper; a three-finger swipe to the quick
  menu; an edge swipe to show or hide panels. Each one is assigned in Settings.
  Written, not yet compiled or tested: `GestureMap.kt` (two-, three- and four-finger taps, finger held still, three-finger swipe, each assignable in Settings > Gestures). Edge swipes are left out: Android's back and home gestures own the screen edges.
- [~] D16. **Stylus mapping.** Barrel buttons, the eraser end and double-tap (where the hardware
  has it) assigned to actions. Hover preview of the brush cursor. Palm rejection with a
  sensitivity setting.
  Written, not yet compiled or tested: the stylus key button runs the action set for it in Settings > Gestures (default: pen/eraser); the pen's and eraser's footprint follows a hovering stylus; palm rejection off, while the stylus touches (default), or also while it's near. The barrel button held while drawing still erases for that stroke. Double-tap has no public Android API and is not done.
- [~] D17. **Hold to switch for a moment.** Hold a key, a stylus button or a rail tool to use
  another tool (eraser, eyedropper, pan); release to go back.
  Written, not yet compiled or tested: holding a rail tool for half a second borrows it until released (with a haptic press); holding P, E or V does the same on a keyboard, a quick press still switches; the stylus barrel button held while drawing erases for that stroke (existing).
- [~] D18. **(after phone) Keyboard shortcuts.** A full default set (B pen, E eraser, V select, Z/Y undo/redo,
  [ ] size, space pan, and so on), all of them remappable, with a cheat sheet that appears while
  you hold Ctrl.
  Written, not yet compiled or tested: `Shortcuts.kt`. Defaults P/B pen, E eraser, V select, U shape, G fill, T text, I eyedropper, N Bézier, M measure, Ctrl+Z/Y and Ctrl+Shift+Z, Ctrl+C/V/D, Del/Backspace, [ ] brush size, Ctrl+= / Ctrl+- zoom, 0 fit, Ctrl+K palette, Tab hide the bars. Each remappable in Settings > Keyboard shortcuts (a key bound elsewhere moves); the cheat sheet shows while Ctrl is held.
- [~] D19. **On-canvas adjusting.** Drag up or down on a rail tool to change its size, sideways
  for opacity, with a live preview ring at the cursor. Sizes snap to user presets.
  Written, not yet compiled or tested: dragging up/down on the rail's pen or eraser scales its size (96 px doubles it), sideways sets opacity, with a ring of the brush's size and colour in the middle of the canvas; sizes settle on 1, 2, 4, 8, 12, 16, 24 or 32 when within 8 %. User-defined presets come with D84.
- [~] D21. **The quick menu becomes a radial menu** that opens from any gesture, key or button,
  holds any action, and supports flick selection without looking.
  Written, not yet compiled or tested: the ring opens from its button, a gesture (D15), the stylus button or Q (D18); it can hold any tool, pen/eraser swap and brush size besides the earlier actions; a flick from the button toward an action runs it on release without showing the ring, ticking as each comes under the thumb.
- [~] D22. **Drag-to-arrange edit mode.** Drag tools, actions and panels onto the rail, the
  properties bar, the quick menu and the panels; remove them by dragging away. This replaces
  showing or hiding whole groups.
  Written, not yet compiled or tested: `LayoutEditor.kt` (Settings > Arrange the layout), one panel: the rail's pinned tools and the quick menu's actions reorder by dragging a handle (or Move up/down for screen readers), go with one tap and come back from chips; toolbar groups as toggles. It replaces the separate toolbar and quick-menu customisation dialogs, now deleted. Dragging straight onto the live rail/menu, rather than in this panel, is not done.
- [~] D23. **Layout presets.** Save, name, switch between, export and import whole layouts
  (rail, panels, gestures, shortcuts, workspaces). Ready-made presets: "Minimal", "Procreate-like",
  "Desktop-like", "One-hand".
  Written, not yet compiled or tested: `LayoutPresets.kt`; at the top of Arrange the layout: Minimal, Procreate-like, Desktop-like and One hand, the user's saved layouts (removable), Save this layout, Export to a .json file and Import (applied and kept). A layout holds pinned tools, toolbar groups, quick menu, gestures and shortcuts; workspaces stay separate (D11, D83).
- [~] D24. **Settings rebuilt.** Search inside Settings and plain sections: Input (gestures,
  stylus, keys, palm), Appearance (theme, density, UI size), Accessibility, Canvas, Files and
  colour management. Every setting can be reset on its own.
  Written, not yet compiled or tested: a search field (accent- and case-insensitive, either language's wording via the palette matcher) filters every row; sections Input, Appearance, Accessibility, Canvas, Files and colour management; each switch, gesture and shortcut shows a reset button while it differs from its default. Theme, density and UI size join Appearance with D25 and D60.
- [ ] D83. **Per-workspace layouts.** Each workspace (D11) remembers its own rail, panels and quick
  menu; switching workspace brings them back.
- [ ] D84. **Custom tool presets on the rail.** Save a tool with its settings as a named preset
  ("Ink 2 px blue", "Soft eraser") and pin it like a tool.
- [ ] D85. **Reset to defaults** per surface and for everything, with a preview before applying
  and an undo after.
- [~] D26. **Everything undoable, or warned about.** Bring frames, layers, objects and resizing
  into undo. Until then, ask once before a destructive action that can't be undone, with "Don't
  ask again".
  Written, not yet compiled or tested: frames, layers, game objects and game settings are now in each undo step, so deleting a frame, a layer (and its strokes) or an object, and resizing a frame, undo in one step. Deleting a raster layer still loses its pixels on undo, so it asks first (`ConfirmIrreversible`, with "Don't ask again").
- [~] D27. **Live preview** for every adjustment (blend, filters, fill, brush edits, transforms)
  on the canvas itself, with Apply and Cancel inline.
  Written, not yet compiled or tested: adjustments (already previewed live on the layer), the gradient fill, the brush editor and the numeric transform now open as non-blocking panels with Apply/Cancel at their foot, so the canvas stays in view; the transform outlines where the selection will go as values are typed. Blend modes already apply at once. Gradient and brush previews stay inside their panel (they describe the next stroke, not existing ones).
- [~] D28. **Empty states and inline hints.** An empty layers panel or object list explains
  itself in one line with the action to take. No dead ends.
  Written, not yet compiled or tested: frames, symbols, versions and search already explained themselves; pins, animation states, rules and the document list now do too, each naming what to do next. Layers is never empty (one always remains).
- [~] D29. **Learn in context.** Replace the first-run tip dialog with short coach marks that
  appear the first time a tool or gesture is relevant, can be skipped, and can be found again
  in Help. Add an optional interactive practice canvas.
  Written, not yet compiled or tested: `CoachMarks.kt`. The first-launch dialog is gone: two gesture tips show on first launch, then one tip the first time each tool is picked, as a small card that doesn't block the canvas ("Got it", "No more tips"). Settings > Show the guide lists every tip, turns in-context tips back on, and opens a practice page (a new document named Practice).
- [~] D30. **Plain-language copy pass.** Short verbs on buttons, errors that say what to do,
  consistent terms (one name per concept across the app and both languages).
  Written, not yet compiled or tested: every error message (23, both languages) now says what to do next, naming the files Fern reads; English apostrophes made consistent. A full glossary pass over all ~1000 strings is D63.
- [~] D31. **Performance as UX.** Panels and menus open in under 100 ms, no layout shift on the
  canvas when a bar appears, and long jobs (exports, imports) run in the background with
  progress and cancel.
  Written, not yet compiled or tested: exports already ran off the main thread; now each shows an "Exporting…" card with a progress bar and Cancel (`FernJobs`), and a cancelled export removes its empty file. Bars and panels float over a full-screen canvas, so none shifts the drawing when it appears. Opening times aren't measured yet (D70); progress is indeterminate (the exporters report no fraction).
- [ ] D86. **No accidental loss of state.** Leaving a tool, closing a panel or rotating the phone
  never discards a half-done action (a text being typed, a Bézier path, a selection); it's kept
  or finished.
- [ ] D87. **Smart defaults.** New documents, layers, exports and objects start with the choice
  most people make, named sensibly in the user's language; nothing asks a question it can answer
  itself.
- [ ] D88. **Interruptions handled.** A phone call, a notification or switching apps mid-stroke
  ends the stroke cleanly and saves; returning resumes without a prompt.
- [~] D34. **Recorded actions and macros.** Repeat the last action, and record a sequence and
  bind it to a key or gesture (it builds on the scripting language of roadmap item 164).
  Written, not yet compiled or tested: `Macros.kt`. Actions run from the quick menu (tap or flick) or the palette are remembered: Repeat last action on Ctrl+., any gesture (Settings) or the palette. App > Record a macro records them until Stop recording, then names and saves the macro; saved macros run from the palette. A macro holds quick-menu actions (tools, undo/redo, duplicate, delete, colour, size), not strokes. Binding one macro to its own key is not done; Repeat last is the bindable one.
- [~] D35. **Usage-aware suggestions (on-device only).** Offer to pin a tool or action you use
  often but reach through menus. No telemetry leaves the device (CLAUDE.md §8.1).
  Written, not yet compiled or tested: `UsageHints.kt`. A tool picked five times from "More tools" or the palette while not on the rail gets one offer to pin it (snackbar with Pin). Counts stay in the app's own preferences; nothing leaves the device.
- [ ] D89. **Gesture and shortcut discoverability.** Long-press any control to see what it does,
  its gesture and its shortcut, in both languages; a "tips you haven't tried" list in Help.
- [~] D36. **Brush cursor and hover preview.** The real footprint of the brush (size, shape,
  angle) under the stylus or mouse before touching, and a crosshair option for small sizes.
  Written, not yet compiled or tested: a hovering stylus (or mouse) shows the pen's or eraser's footprint at its real size (D16), switching to a gapped crosshair when the footprint is under 4 px. The footprint is drawn round; stamp brushes' own tip shapes and angles aren't drawn.
- [~] D37. **Live readouts at the point of action.** While drawing a shape, moving or scaling:
  length, angle, size and position next to the pen, in the document's scale (roadmap item 143),
  then gone. No fixed info bar.
  Written, not yet compiled or tested: the shape tool's readout (length, or width × height) now adds a line's angle; moving, scaling or turning a selection shows its position and size just above it, in the drawing's units, gone when the finger lifts. The measure tool already showed its readout.
- [~] D38. **Visible gesture feedback.** A short ghost of the undone stroke on a two-finger undo,
  the zoom percentage and rotation angle while pinching, snapping back to 0° and 100 % with a
  haptic tick.
  Written, not yet compiled or tested: while pinching or panning, the zoom % and turn in degrees show at the top; an undo or redo by tap shows a brief "Undo"/"Redo" badge where the fingers were; the turn snapping back level ticks, as zoom passing 100 % already did (D7). A ghost of the undone stroke itself isn't drawn: the badge stands in for it.
- [~] D39. **Selection that explains itself.** Handles sized for fingers (48 dp hit area), a
  floating action strip beside the selection (move, transform, style, duplicate, delete, make
  object) instead of the toolbar, and the count of what's selected.
  Written, not yet compiled or tested: `SelectionActions.kt`. Above the selection, kept on screen: "N selected", duplicate, apply colour, transform, make object, delete (with Undo); those five left the properties bar, which keeps the rarer selection actions. Scale and rotate handles take touches within 24 dp (a 48 dp target) instead of 16 px.
- [~] D40. **Mode indicators.** Symmetry, snapping, perspective, wrap-around, isometric and
  stabiliser show as small, tappable chips at the edge of the canvas while active, so a mode
  never stays on unnoticed.
  Written, not yet compiled or tested: `ModeChips.kt`. Symmetry, perspective, wrap-around, isometric plane, stabiliser and ruler show as chips on the free hand's side while on; tapping one turns it off. Snapping to objects is on by default, so it isn't flagged as a mode.
- [~] D41. **Navigation aids.** Fit, 100 %, reset rotation and "go back to where I was" one tap
  away; the minimap as an optional overlay; a double-tap with two fingers to fit.
  Written, not yet compiled or tested: `NavigationAids.kt`, a small bar on the free hand's side: Fit, 100 %, Level (while turned), Back to where I was (after any of those), and minimap on/off (saved). A two-finger double tap fits the drawing, taking back the first tap's undo.

---

## Phase 4 — Guides: rulers and perspective (rebuild)

Guides as professional tools have them: placed, locked and drawn along, many kinds, all at once. Builds on the handle framework (171).

- [~] 342. **Ruler kinds.** Straight ruler, curve ruler (drawn as a Bézier), ellipse and circle, parallel lines, concentric (radial) circles, radiating lines from a point, polyline, and a guide grid.
  Done: straight ruler, curve ruler, parallel lines, radiating lines, concentric circles, ellipse and grid (`Guides.kt`). Left: polyline.
- [~] 344. **Perspective rebuilt.** 1, 2 and 3-point grids drawn on the canvas with a horizon line, vanishing points dragged as handles (including far off the page), grid density, and the pen snapping to the nearest perspective direction. Today there are 1 and 2 points with no grid.
  Done: three-point perspective; the horizon through the first two points; vanishing points dragged in guide edit mode as well as with the pen; perspective works alongside the guides. Left: grid density, and reaching points far off the page without zooming out.
- [ ] 345. **Curvilinear perspective.** 4, 5 and 6-point (fisheye) grids for wide-angle scenes.
- [ ] 346. **Perspective tools.** Shapes, text and pasted images placed onto a perspective plane; isometric and dimetric grids joined into the same system.
- [ ] 185. Ruler and Measure merged: a measurement pinned as a dimension line in real-world units; angle (three points), area and perimeter; copy a measurement into a text label.
- [ ] 219. Symmetry: mirrored radial (kaleidoscope); axis moved and rotated on the canvas; symmetry limited to a selected frame or region.
- [ ] 220. Reference window: a floating image or second view kept on screen while drawing.
- [ ] 358. **A small set of snap targets** (endpoint, midpoint, intersection, centre, perpendicular, tangent) with a per-stroke override and values typed at the cursor, as in CAD tools; no parametric constraint solver. Users are everyone, so it is in scope. Review finding O12.

---

## Phase 5 — Pens and brushes (rebuild)

One brush engine with every setting editable, pens made from your own drawing, a brush studio. Data before editor: the engine's settings first (349, 189–200), then the studio (201), then import (202). The stroke format gains an explicit version and a converter with golden-file tests before the brush model changes, so existing documents keep opening (review §6).

- [ ] 349. **Every pen is a brush.** The fixed pen types (marker, pencil, highlighter, calligraphy, airbrush, crayon, pixel, stamp) become presets of one engine whose settings are all editable, duplicable and shareable, instead of hard-coded behaviours.
- [~] 189. Pressure curve per pen and brush, edited as a curve (reusing the easing curve editor), with a minimum width so light pressure never vanishes and pressure-to-opacity besides pressure-to-width.
  Done: a pressure curve per pen type, edited in the curve editor from the pen's properties, applied to stylus pressure as it is read (`PressureCurves.kt`); a finger's full pressure is left alone. Left: a minimum width, and pressure to opacity.
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
- [ ] D82. **Pressure curve tuning in one screen**, drawn on the screen with your own stylus or
  finger, with a live test stroke; per-pen presets.
- [ ] 201. Brush studio: every setting above in one editor, in sections, with a test pad using pressure and a live preview of spacing, jitter and scatter; brushes saved as presets and shared as files.
- [~] 347. **Brush tip from a selection.** Select part of your own drawing and make it a brush tip (and a grain), for any brush, not only the stamp pen: the way Clip Studio, Krita and Photoshop register a brush from the canvas. Today a custom tip is a file picked from the gallery, shrunk to 64×64, for the stamp pen only.
  Done: "Make a brush tip from the selection" on the selection strip draws the selected ink into a square and sets it as the tip of the stamp pen and of the raster brushes, then switches to the stamp pen. Left: grain from a selection, and tips for the other pens once they are brushes (349).
- [ ] 348. **Pen from a selection.** Select a drawn shape and make it a vector pen: stretched along the stroke (art brush) or repeated (pattern brush), with its colours kept or recoloured by the pen colour.
- [ ] 202. Brush import: tips from Photoshop `.abr` files and shapes and grains from Procreate `.brushset` files.
- [ ] 203. Vector art and pattern brushes: a shape stretched along the path, or repeated along it with corner pieces.
- [ ] 204. Vector line cleanup while drawing: cut a stroke's overshoot at the nearest crossing line; a pen that joins a stroke ending near another line.

---

## Phase 6 — Colour (rebuild)

A real palette system and an eyedropper that sees what is on screen. Builds on screen sampling (170).

- [ ] 350. **Palette panel.** A palette is its own panel, not swatches packed into the properties bar: a grid of swatches, several named palettes, switching between them, drag to reorder, add from the current colour, rename and delete. Imported `.gpl` and `.ase` files become whole palettes. Today saved colours are capped at 16 and share one scrolling row with the current colour, 8 recent colours, 5 harmonies and a fixed palette (`ColorStrip.kt`).
- [ ] 351. **Compact colour row.** The properties bar keeps only the current colour, the previous one and the active palette's first few swatches; the rest is the palette panel.
- [ ] 352. **Document palettes.** A palette can belong to a document (its colours used in the drawing) as well as to the app, and a document's colours can be collected into a palette.
- [~] D20. **One colour flow.** A single inline colour strip (current, recent, saved swatches,
  harmonies) that expands in place to the full picker. "Change everywhere" becomes a visible
  action, not a hidden long-press.
  Written, not yet compiled or tested: `ColorStrip.kt` in the properties bar for pen, shape, Bézier and fill: current colour (tap: the full picker as a live, non-blocking panel), saved, recent, five harmonies, the palette. While the current colour is a saved one, "Change everywhere" and "Remove" show as buttons. The toolbar's separate colour rows and button are gone.
- [ ] 188. Colour: a longer history with pinned favourites; hex, RGB and HSB values editable with copy and paste; disc, square and slider pickers; a value (greyscale) check.
- [~] 173. Eyedropper samples what is shown (screen sampling), with "all layers" or "this layer"; a magnified loupe with old and new colour; sampling size 1, 3 or 5 px; a message when nothing was picked. Today it reads vector strokes only and ignores raster, images, fills and paper.
  Done: the eyedropper reads what is shown (PixelCopy of the window: every layer, raster, images, fills, paper), or only the active layer's ink; sample 1, 3 or 5 px; a message when nothing was picked; options in the properties bar. Left: the magnified loupe.
- [ ] 174. Eyedropper picks a stroke's whole style (width, pen type) as an option, reusing the style picker; the last 8 picked colours kept in the colour strip.
- [ ] 213. Colour from anywhere on screen, including the reference window and photos.
- [ ] 214. Harmony lock: harmony colours follow the main colour as it changes; colour assist showing the complement and the value against neighbours.
- [ ] 215. Palette sorting by hue or value.

---

## Phase 7 — Drawing tool fixes

The remaining existing tools made complete.

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
- [ ] 186. View: flip the view horizontally (a mirror to check proportions, the drawing unchanged); greyscale view; zoom presets (fit, 100 %, fill) and tap to reset rotation.
- [ ] 187. Layers: larger thumbnails with a size setting; swipe a row to hide, lock or alpha-lock; pinch rows to merge; long-press a stroke to jump to its layer; colour labels with a filter.

---

## Phase 8 — Layers and selection

Groups, adjustment layers and soft selections, which later tools draw into.

- [ ] 205. Layer groups that fold, with group opacity and blend, and clipping to a group.
- [ ] 206. Adjustment layers: hue/saturation, curves, levels, colour balance and gradient map, non-destructive on the layers beneath.
- [ ] 207. Layer effects: outline (stroke), drop shadow and glow.
- [ ] 208. "Select layer contents" as a selection.
- [ ] 209. Feathered (soft-edged) selections for raster editing.
- [ ] 210. Selection painting with a brush (quick mask).
- [ ] 211. Magic wand: select by same colour, width, pen type, layer, or connected strokes; "select similar" on the selection strip.
- [ ] 212. Smart lasso snapping to stroke edges; named selections saved and reselected.

---

## Phase 9 — Paths, shapes and text

Vector editing at the level of Illustrator and Affinity. Builds on node editing (184).

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

---

## Phase 10 — New drawing tools

Tools Fern doesn't have yet, each built on the phases above.

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

---

## Phase 11 — Rig and animation data model (rebuild)

Rigging the way Spine works: the artist's own drawing cut into parts on a skeleton built for it. Today a bone is a head and a tail point, a pose is a full copy of every bone per frame, a stroke belongs to one bone, and only rotation and position exist. Everything in Phases 12–17 depends on this phase. The document format gains an explicit version and a converter from per-frame poses and bound strokes before the model changes; the game logic's state machines (item 158) move to the new model with it (review O7, §6).

- [ ] 250. Local bone transform: rotation, x, y, scale x/y and shear x/y relative to the parent, with inherit settings (rotation, scale, reflection) per bone.
- [ ] 251. Timelines: per bone, separate keyed tracks for rotate, translate, scale and shear, each key with its own curve (linear, stepped, Bézier). The per-frame pose becomes the baked result, and existing documents convert to keys.
- [ ] 252. Setup pose versus animated pose: a Setup mode edits the rest pose; animations store only differences from it.
- [ ] 253. Several named animations per rig (idle, walk, jump), each with its own length and timelines.
- [ ] 254. Bone tree panel: names, colours, folders, hierarchy; tapping ink selects its bone.
- [ ] 255. The same keyed timelines for non-rig properties: layer and object opacity, position, scale, rotation and colour with easing.

---

## Phase 12 — Animation on timelines

Animation tools on the Phase 11 model.

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

---

## Phase 13 — Slots, attachments and draw order

Spine's display model, and rigging your own drawing. Builds on Phase 11.

- [ ] 353. **Rig your own drawing.** Select part of the drawing (strokes, raster paint or an image) and make it an attachment on a bone, cut along the selection; a whole layer can be attached the same way. Today "bind to nearest bones" gives each whole stroke to the one bone nearest its centre (`CanvasState.kt:1887`), so an arm drawn as one outline can't bend, and raster paint, images and fills can't be rigged at all.
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
- [ ] 354. **Rigging workflow guide.** The rig tools in the order an artist uses them (cut into parts, place bones, attach, mesh and weight, set the setup pose, animate), shown as steps in the rig panel, so a rig can be built without knowing where each tool lives.

---

## Phase 14 — Meshes and weights

Smooth bending that survives export.

- [ ] 282. Mesh attachments: a drawing with a hull and inner vertices, triangulated, deforming smoothly.
- [ ] 283. Mesh tool: add, delete and move vertices, draw edges, triangulate from the outline, trace a hull from the drawing's alpha.
- [ ] 284. Vertex weights across several bones, with a weights panel per bone and a lock per bone.
- [ ] 285. Weight tools: paint add, subtract, replace and smooth; automatic weights from the mesh topology, as Spine computes them, rather than distance alone (so an arm doesn't pull the torso; review §3.3); normalise; prune small weights; mirror left to right. Today's point weights convert to this.
- [ ] 286. Deform timeline: vertex offsets keyed over time on top of bone deformation (today's per-frame mesh warp becomes this).
- [ ] 287. Linked meshes: attachments sharing one mesh, its weights and deform keys.

---

## Phase 15 — Constraints

Rig behaviour beyond direct posing.

- [ ] 288. IK constraint as an object: target bone, one- or two-bone chain, mix, softness and bend direction; an exact two-bone solver with a bend direction as the default, as Spine limits IK to one or two bones because longer chains are nondeterministic (the CCD solver stays as an option for longer chains; review §3.3); IK/FK switch and blend per animation; the IK target dragged on the canvas.
- [ ] 289. Joint limits shown as arcs on the canvas and dragged at their ends.
- [ ] 290. Transform constraint: copy rotation, position, scale or shear between bones with offsets and mix.
- [ ] 291. Path constraint: bones follow a drawn path, with position, spacing (length, fixed, percent) and rotation modes.
- [ ] 292. Physics constraint: inertia, strength, damping, mass, wind and gravity per bone, in playback and export. Dynamic bones become this, with their settings and a preview while scrubbing.
- [ ] 293. Constraint order list and keyed constraint mix.
- [ ] 294. Constraint tool: add each constraint by picking bones on the canvas.

---

## Phase 16 — Rigging workflow

Rigging fast and reusable.

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

---

## Phase 17 — Spine and DragonBones round trip

Rigs leave and come back without loss. Today export bakes each bone's ink into one image and import keeps only rotation and translation.

- [ ] 305. Spine export with slots, attachments, meshes, weights, skins, constraints, events, draw order and colour timelines, and a packed atlas.
- [ ] 306. Spine import of all of the above (JSON 3.8 and 4.x).
- [ ] 307. Spine binary (`.skel`) export; export settings (scale, nonessential data, pretty JSON, chosen animations).
- [ ] 308. DragonBones export and import at the same level.
- [ ] 309. Runtime preview: play the exported data back as an engine would, before handing it over.

---

## Phase 18 — Face and 2.5D rigging

Live2D-style turns from flat art.

- [ ] 310. Parameters (head angle, mouth open) blending between keyed mesh shapes.
- [ ] 311. Warp and rotation deformers nested on a face.
- [ ] 312. Blink, breath and lip-sync parameters linked to the mouth chart and soundtrack.

---

## Phase 19 — 3D

3D as a drawing aid and an asset. Its value for a drawing app is reference, so re-rendering a placed model (316) and matching its camera to the perspective guide (321) come right after the fixes; sculpting and full modeling stay out of scope (review O11).

- [ ] 313. Orbit camera: double-tap to frame, reset, snap to front, side and top.
- [ ] 314. Painting: brush circle on the surface at its painted size; painting past UV seams so no lines show.
- [ ] 316. Placed models re-rendered at a new angle on the canvas without opening the editor (today a placed model is a still image).
- [ ] 321. A model as a drawing reference whose camera matches the canvas perspective guide.
- [ ] 315. Lighting presets (studio, rim, flat).
- [ ] 317. Layers for 3D painting with opacity and blend; normal, height and emission channels.
- [ ] 318. Projection painting: paint on the screen and project it onto the model.
- [ ] 319. Toon shading and outline rendering.
- [ ] 320. Turntable export (GIF or MP4).
- [ ] 322. The 3D mannequin and hand posed per animation frame, rendered into each frame as a reference layer.
- [ ] 323. Extrude and lathe: drawn shapes become 3D objects.
- [ ] 324. Scene blocks: several primitives placed together to block out a room or prop.
- [ ] 325. Full-body 3D mannequin, extending the hand and head.

---

## Phase 20 — Game logic tools

Building and debugging games from drawings.

- [ ] 326. Debug overlay while playing: colliders, hitboxes and object names; the last rule fired per object; step one frame at a time while paused.
- [ ] 327. Collider tool: draw boxes, circles and polygons directly on an object.
- [ ] 328. Path follower editor: speed per segment and pauses.
- [ ] 329. Sound actions from rules, and rig events triggering sounds and rules.
- [ ] 330. Prefabs: reusable objects whose changes reach every copy.
- [ ] 331. Particle emitter: drawn particles with speed, spread and lifetime.
- [ ] 332. Tilemaps: levels painted from tiles cut from a drawing, collision per tile.
- [ ] 333. Scenes: several levels with transitions.

---

## Phase 21 — Home, documents and files

- [ ] D48. **A home screen** with recent documents as large thumbnails, a "New" button with
  templates (blank, paper, storyboard, comic page, pixel art, game scene, PDF notes), folders,
  search, sort and multi-select.
- [ ] D49. **Resume where you left off.** Reopen the last document, view, tool and panels with no
  splash and no prompt.
- [ ] D50. **Save status you can trust.** A discreet saved/saving indicator, the version history
  (ROADMAP versions) as a timeline you can scrub, and "restore" that never overwrites without a
  copy.
- [ ] D51. **Sharing in one step.** Share opens the system sheet with a sensible image right
  away; options (format, size, background) come after, not before.
- [ ] D93. **Gallery gestures.** Swipe a document card for duplicate, share, delete (with Undo);
  pinch the gallery to change thumbnail size; drag documents into folders.
- [ ] D94. **Quick capture.** A home-screen shortcut and a widget to start a blank note or a
  sketch in one tap, straight into the pen.

---

## Phase 22 — Forms of device (after phone)

- [ ] D53. **(after phone) Foldables and multi-window.** The layout follows the hinge (tabletop mode: canvas
  above, tools below); drag and drop of images and text in and out of the app.
- [ ] D54. **(after phone) Mouse and trackpad.** Hover states, right-click context menus, scroll to zoom, middle
  drag to pan, and tooltips that show the shortcut.

---

## Phase 23 — Languages and content

- [ ] D61. **Text that fits.** Every phone layout tested in English and French at the system's
  largest font size, no truncated labels, and icons with labels where a label is needed.
- [ ] D62. **(later, if more languages) Right-to-left ready.** Mirrored layouts where they should be, but the canvas and
  timeline direction kept as the user chooses.
- [ ] D63. **A glossary.** One term per concept (frame, layer, object, symbol, comp, tag) used
  the same way in UI, help and errors, in both languages.
- [ ] D64. **Built-in help.** Searchable, offline, with short animated examples per tool, reached
  from the command palette and from a long-press on any control ("What is this?").
- [ ] D74. **Everything translated.** Move every user-visible literal into string resources in
  both languages: script and import/export error messages (with their values as arguments),
  default names (layer, frame, object), the play HUD's text, example scripts and templates.
  Messages from libraries are wrapped in a translated sentence.
- [ ] D75. **Translation checks in CI.** A check fails the build when a key exists in one
  language only, when a `Text("…")` or `Toast` uses a literal, or when a French string is
  untranslated (identical to English where it shouldn't be). Lint `HardcodedText` and
  `MissingTranslation` set to errors.
- [ ] D76. **In-app language choice.** English or French chosen in Settings, independent of the
  phone's language (Android per-app language), applied without restart.
- [ ] D77. **Bilingual store and help content.** Help, coach marks (D29), templates and the store
  listing in both languages, written natively rather than translated word for word.

---

## Phase 24 — Recovery and trust

- [ ] D65. **Errors with a way out.** Every failed import, export or script says what happened in
  plain words and offers the next step (retry, choose another format, open the line).
- [ ] D66. **Crash and battery recovery.** After a crash or a dead battery, the document reopens
  with the last strokes and a short note of what was recovered.
- [ ] D67. **Permission requests in context.** Camera, microphone and files asked for at the
  moment they're needed, with why, and a working fallback when refused.
- [ ] D68. **Privacy visible.** A plain statement in Settings that everything stays on the
  device (CLAUDE.md §8.1), and nothing that suggests otherwise.
- [ ] D96. **Storage-full and low-memory handling.** Warn before the phone runs out of space with
  what to free; huge documents degrade gracefully (lower preview quality) instead of crashing.

---

## Phase 25 — Notes and handwriting on the phone

- [ ] D98. **Paper templates on the phone.** Lined, grid, dotted and Cornell pages that fit the
  phone width, with zoom that keeps handwriting a comfortable size.
- [ ] D99. **Writing comfort.** An auto-scrolling writing zone (write in a magnified strip at the
  bottom, the page fills above), palm rest area, and a shift of the page as you reach the edge.
- [ ] D100. **PDF markup flow.** Open a PDF, highlight, write, sign (roadmap item 129) and share
  it back in a few taps, with the tools a reader needs and nothing else.
- [ ] D101. **Audio notes in reach.** Record, pause and play back (roadmap item 130) from a small
  bar that stays out of the writing area.

---

## Phase 26 — Verification

- [ ] D69. **Accessibility checks in CI.** Compose UI tests with `assertHasClickAction`,
  label, touch-size and contrast checks on every screen.
- [ ] D70. **Task-time benchmarks.** On a phone, taps and seconds to do the 20 most common tasks (draw, change
  colour, undo, new layer, select and move, export to share, …), measured before and after the
  rework, and kept from regressing.
- [ ] D72. **Usability sessions.** A few people new to Fern and a few experienced artists try the
  same scripted tasks after each phase; what made them hesitate becomes items here.
- [ ] D73. **Friction log.** Every "where is…?" and "how do I…?" found in testing or feedback is
  recorded against the item that should fix it, and a phase is not closed while one of its
  entries is open.
- [ ] D97. **Contrast and colour-blind checks** of every theme, automated against WCAG 2.2 AA
  (4.5:1 text, 3:1 controls), with simulations for protanopia, deuteranopia and tritanopia.

---

## Deferred ⏸

Needs an external platform, off-limits for now (CLAUDE.md §8.1).

- [ ] 61. Accounts and end-to-end-encrypted cloud backup ⏸
- [ ] 62. Multi-device sync (CRDT per document) ⏸
- [ ] 63. Read-only share links and a web viewer ⏸
- [ ] 64. Real-time co-editing ⏸
- [ ] 65. Comments pinned to canvas locations ⏸
- [ ] 66. Desktop or web companion app ⏸
- [ ] 67. Handwriting recognition ⏸ (ML Kit depends on Google Play Services)
- [ ] 76. Generative assists ⏸ (hosted AI)
- [ ] 77. Release work: Play Store listing, privacy policy, staged rollouts ⏸ (owner: the listing and rollouts are Play Console steps; a privacy-policy document is a new top-level file needing approval; R8 shrinking needs device testing, since saved documents store enum names)
- [ ] 109. AR placement and tracing ⏸ (ARCore is a Google Play Services platform)
- [ ] 110. Generative AI ⏸
- [ ] 112. Marketplace ⏸ (hosted service)
- [ ] 113. Community gallery and live-streaming ⏸ (hosted service)
- [ ] 114. Team workspaces ⏸
- [ ] 149. Shared timeline with locks ⏸
- [ ] 150. Live presence on the timeline and rig ⏸
- [ ] 151. Shared rig library ⏸
- [ ] 152. Roles and permissions ⏸
- [ ] 154. Voice and text chat in the canvas ⏸
- [ ] 162. Online multiplayer play ⏸
- [ ] 163. AI logic assistant ⏸

---

## Non prioritaire

Set aside by the project owner: not to be started unless asked.

- [~] 47. Search text labels and jump to them; handwriting search ⏸ (needs item 67)
- [ ] 148. Command-line batch interface (competitor parity, Aseprite)
- [ ] 115. iOS, Windows, macOS and web versions (separate apps, outside this Android project)
- [ ] 165. Publishing (link, gallery, Android/web export)
- [ ] 166. Moderation and safety for shared content
