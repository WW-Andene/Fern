# Fern design roadmap

The UI and UX rework, from a deep audit of the current app (October 2026). It runs beside
[ROADMAP.md](ROADMAP.md), which tracks features. This file tracks how those features are reached,
learned and used.

**Status key:** `[x]` done · `[~]` partly done · `[ ]` not started.

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

Update this file in the same commit as the work that changes an item's status. Every dimension
follows the CLAUDE.md §7 scale.

---

## Audit findings

Numbers measured in the source at the time of the audit.

### Structure and navigation

- **One toolbar holds everything.** `Toolbar.kt` is 1,844 lines with 100 icon buttons and 11
  groups (favourites, palette, stroke options, guides, animation, layers, fill, text, view,
  replay, background). On a phone it scrolls sideways, so most tools are off-screen. You can't
  tell what's past the edge, and a tool's position depends on how far you scrolled.
  *(priorities 1, 6, 3)*
- **The ⋮ overflow menu is a junk drawer.** It holds 21 unrelated entries: insert image, video
  and 3D; four kinds of import; search; pins; frames; second view; audio; versions; statistics;
  symbols; signature; QR code; comic; script; plugins; settings. Nothing groups them, and
  frequent and rare actions sit at the same level. *(1, 3, 7)*
- **Export is a list of 24 formats.** The format comes first. People think in purpose (share,
  print, animation, game engine, layered for another app), not in formats. *(4, 5)*
- **47 modal `AlertDialog`s across 44 dialog files.** Layers, frames, symbols, states, rules,
  brushes and colour all block the canvas while they're open. Nested dialogs are common: the
  object dialog opens states, rules or the script; brushes open the brush editor. *(5, 7)*
- **Two customisation systems that don't talk to each other.** Toolbar groups are shown or
  hidden as a whole (item 71 of ROADMAP.md), and the quick-menu ring holds up to 12 of 14
  actions. You can't reorder tools, move them, set gestures, or save and share a layout. *(8)*
- **Settings is thin and mixed.** It has 11 entries: left-handed, toolbar at the bottom, high
  contrast, snapping, the customisation dialogs, the guide again, and CMYK profiles (a
  colour-management setting next to UI preferences). Pressure curves, gestures, the palm
  rejection level, theme and haptics have no home. *(8, 4)*

### Interaction and muscle memory

- **Gestures:** one finger draws, two fingers pan and zoom, a two-finger tap undoes, a
  three-finger tap redoes. There's no long-press eyedropper on the canvas, no gesture for the
  quick menu or the last tool, and none of the gestures can be changed. *(6, 8)*
- **No stylus button or eraser-end mapping** is visible in the input path (`DrawingCanvas.kt`
  reads tool type, pressure and tilt only). Stylus input itself is untested on hardware. *(6)*
- **No keyboard shortcuts for drawing.** Keys are read only for game input mapping. Tablets and
  Chromebooks with keyboards get nothing. *(6, 2)*
- **No haptic feedback anywhere** (0 uses): not on snapping, the end of a zoom, a tool switch or
  a long-press. *(6, 4)*
- **Feedback goes through 22 `Toast`s and 0 snackbars.** You can't act on a toast (no "Undo"),
  TalkBack reads it late or not at all, and it covers the canvas. *(4, 5, 2)*
- **The colour flow is split.** Saved colours, recent colours, the palette group and the full
  picker dialog each live somewhere different. Changing a colour everywhere is hidden behind a
  long-press on a 24 dp swatch. *(1, 6)*

### Accessibility

- **Touch targets under 48 dp:** palette swatches are 24 dp; elements also come in at 16, 32
  and 12 dp. Material and WCAG 2.5.8 call for 48 dp, or 24 dp with spacing. *(2)*
- **18 `contentDescription = null`**, and only 12 files use `semantics`. The canvas itself has no
  accessible description: what's selected, the zoom level, the active tool. *(2)*
- **53 hard-coded `Color(0x…)`** values outside 3D code, which bypass the theme and high
  contrast. The HUD is white on a fixed dark overlay. *(2, 3)*
- **Fixed `sp` sizes** (12 sp ×3, 8 sp ×1): 8 sp can't be read and doesn't respect a reasonable
  minimum. No layout is checked at 200 % font scale. *(2)*
- **High contrast is one switch.** There is no colour-blind-safe UI palette, no reduced-motion
  option and no left/right/one-hand layout beyond "left-handed". *(2, 8)*
- **The app can't be driven without touch:** no keyboard focus order, and no D-pad or Switch
  Access path through the toolbar. *(2)*

### Languages

- **The string files match:** 906 keys in English (`values/`), 906 in French (`values-fr/`), none
  missing on either side.
- **But text still escapes translation.** The 38+ script error messages (`ScriptError(…, "…")`)
  and the 54 exception messages thrown in importers and exporters are English only; some of them
  reach the user. Default names are hard-coded in English: "Layer 1", "Layer N", "Frame",
  "Frame N", "Object". The play HUD builds its text by joining strings in code instead of using a
  translatable template. *(ground rule: bilingual)*
- **Nothing checks this mechanically.** A new string missing a translation, or a literal in a
  `Text("…")`, would pass unnoticed. *(ground rule: bilingual)*
- **Layouts aren't checked with French**, which runs 15–30 % longer than English. *(3, ground
  rule: bilingual)*

### Visual design

- **Mostly on the dp scale** (8, 4, 12, 16, 128 and 512 dominate). Off-scale and odd spots
  remain: `62.dp` (a tertiary step, not an off-scale value, but unusual), `6.dp`, `1.dp`,
  `0.dp`, and 8 sp text. *(3)*
- **No visual hierarchy between frequent and rare actions:** the same icon size, the same
  weight, and the same bar for "pen" as for "export DragonBones". *(3, 1)*
- **Three kinds of colour indicator** (squares for saved, circles for recent, palette swatches)
  without labels. *(4)*
- **The first-run guide is a single dialog of tips** (`WelcomeDialog.kt`). It doesn't teach in
  context, and once dismissed it only comes back from Settings. *(1, 4)*

### Friction and frustration

- **Modal dialogs hide the result they change.** You set a brush, a fill or a layer blend
  without seeing the canvas. *(5)*
- **Destructive actions** (delete layer, "Not an object", clear) depend on undo. Some aren't
  undoable by design: frames, layers, objects, frame resizing. Nothing warns about this.
  *(5, 4)*
- **No command search.** With 100+ actions, the only way to find one is to remember where it is.
  *(1, 5, 7)*

---

## Phase D1 — Foundations: tokens, theme, accessibility baseline

Goal: a design system everything else is built from, accessible by default.

- [x] D1. **Design tokens.** One `FernTokens` file for spacing, size, radius (§7.3), type scale,
  elevation and motion, all on the §7 scale. Replace every raw `dp`, `sp` and `Color(0x…)` in UI
  code with a token.
  Done: `FernTokens.kt` (scale, spacing, sizes, type, motion, §7.3 radius); no raw `dp`/`sp` literal left in UI code, checked by `FernTokensTest`. Named on-scale constants (`CELL`, `PAD_DP`…) remain for pixel maths.
- [x] D2. **Semantic colour roles.** Canvas chrome, accent, selection, guide, danger and HUD,
  each in light, dark, high-contrast and colour-blind-safe themes. Remove the hard-coded colours
  listed in the audit.
  Done: `FernColors.kt` roles (selection, guide, danger, measure, frame, construction, active, game, onion, neutral, scrim) in standard, high-contrast and colour-blind-safe (Okabe-Ito) sets; a Settings switch for the last. Paper, palette and ink colours are content and stay as they are.
- [x] D3. **Touch targets.** 48 dp minimum hit area everywhere. Swatches can stay 24 dp to look
  at, with a 48 dp hit area.
  Done: swatches, tool slots and small delete buttons keep their look with a 48 dp touch target (`minimumInteractiveComponentSize`).
- [x] D4. **Screen reader pass.** Every control gets a label and a role, and the state is
  announced ("Pen, 4 px, selected"). The canvas gets a live description (tool, zoom, selection
  count). No more `contentDescription = null` on actionable elements.
  Done: tool buttons announce whether they are the active tool; the canvas announces the tool, zoom and selection count as they change (polite live region). Remaining `contentDescription = null` are decorative icons inside labelled controls.
- [x] D5. **Font scale and reduced motion.** Layouts hold up to 200 % font scale. Nothing is
  under 12 sp. Animations respect the system's "remove animations" setting.
  Done: nothing is under 12 sp (`FernType.caption`, enforced with D1's check); no text sits in a fixed-height box, so text wraps instead of clipping. The app has no custom UI animations yet, and Compose animations follow the system animation scale (off when animations are removed); new ones must use `FernMotion`. Checked by reading the layouts, not yet on a device at 200 %.
- [x] D6. **Snackbars replace toasts.** Messages can be acted on ("Layer deleted — Undo"),
  TalkBack announces them, and they're placed away from the drawing hand.
  Done: `FernMessages` and `FernMessageHost`; all 22 toasts replaced. Messages sit opposite the toolbar, on the free hand's side. Deleting a selection (button or key) and clearing the canvas offer Undo.
- [x] D7. **Haptics vocabulary.** Light ticks on snaps, guide hits, the zoom at 100 %, tool
  switches and long-press menus. Each one can be switched off.
  Done: `FernHaptics` (tick and press) with a Settings switch; ticks on tool switches, zoom passing 100 %, smart-guide snaps and a held stroke snapping to its shape; a press on long-press menus (saved colours, tool slots).

## Phase D2 — The new shell: canvas first, nothing more than one level deep

Goal: the screen layout that replaces the single scrolling toolbar and the ⋮ junk drawer.

- [x] D8. **The tool rail.** On a phone, a bottom bar in thumb reach (a side rail in landscape). A short, fixed rail of the main tools (pen, eraser, select, shape,
  fill, text, eyedropper, plus user-pinned ones) that never scrolls. A tool keeps its position
  for good, so muscle memory works. It docks left, right or bottom, and follows the hand setting.
  Done: `ToolRail.kt`. Fixed: pen, eraser, select, shape (four, so a 360 dp phone still fits two pinned tools and "More tools"). Pinned by default: fill, text, eyedropper; any other tool pins or unpins from "More tools", saved. Bottom on an upright phone, the free hand's side otherwise; other bars keep clear. The tool buttons left the big toolbar, which keeps each tool's options while it's active (until D9).
- [x] D9. **A contextual properties bar.** Only the settings of the active tool or the current
  selection, shown inline, never in a dialog: pen size, opacity and smoothing; shape kind;
  fill tolerance; text style; the selection's transform and style. This replaces the stroke
  options, fill and text toolbar groups.
  Done: a separate bar above the rail (bottom centre) shows only the active tool's settings: pen kind, brush, size, opacity, line options and stabiliser for drawing tools; eraser, shape, measure, Bézier, bone, fill and connector options; the selection's actions with the select tool. Hidden for text, eyedropper and ruler. The settings still open from buttons and sliders in the bar, not dialogs; brush editing and the gradient are dialogs until D27.
- [x] D10. **Docked side panels instead of modal dialogs** for layers, frames, symbols,
  pins, versions, objects and game logic. They're non-modal, can be resized or collapsed, and
  stay open while you draw. On a phone (the reference) they're bottom sheets with peek heights
  that leave the drawing visible; side docking comes on larger screens (after phone). These are the
  dialogs that today block the canvas.
  Done: `FernPanel`/`FernPanelHost`. Layers, frames, symbols, pins, versions, the object panel and its states, rules and script open as non-modal panels: a bottom sheet of at most half the screen above the rail on an upright phone (collapsible to its header), docked beside the rail otherwise; panels stack, Back closes the top one. Footer "Close" buttons removed (the header has one). Resizing by drag is left to D42.
- [x] D11. **A workspace switcher**: Draw, Paint (raster), Design (vector), Animate, Game, Notes
  (PDF and audio). Each one shows the tools and panels its work needs. The user can edit
  workspaces or create new ones. Animation, replay and guides stop crowding a drawing session.
  Done: `Workspaces.kt`. A switcher first in the toolbar offers Draw, Paint, Design, Animate, Game and Notes, each setting the toolbar groups shown and the rail's pinned tools; the current layout saves as a new named workspace or into one of the user's, which can be deleted. Panels per workspace come with D83.
- [x] D12. **The ⋮ menu split by purpose**: Insert (image, video, 3D, symbol, QR, signature),
  Import, Export, Document (versions, statistics, search, frames) and App (settings, plugins,
  help). Each is one level deep and opens from a labelled entry, not hidden in an overflow.
  Done: the ⋮ overflow is gone. Labelled Insert, Import, Document (search, frames, pins, versions, audio, statistics, script, second view, rulers, wrap-around) and App (settings, plugins) menus, each one level; Export stays its own menu until D13. Help joins App with D64.
- [x] D13. **Export by purpose.** First choose Share, Print, Image, Vector, Layered (for another
  app), Animation or Game engine; then the format, with a recommended default. The last used
  export is a single tap.
  Done: `ExportPanel.kt`. The export button opens a panel: "Again: <last export>" first, then Share, Image, Print, Vector, For another app, Animation and Game engine; each lists its formats with the recommended one first (starred), and formats that need frames or bones say so instead of disappearing.
- [x] D14. **Command palette.** Search every action, tool, setting and recent document by name
  or synonym, in English and French, with the shortcut and location shown beside each. It opens
  from a button, a gesture or Ctrl/⌘ K.
  Done: `CommandPalette.kt`. Opens from the search button at the toolbar's start or Ctrl/⌘ K; finds tools, undo/redo, layers, documents, colour, every Insert/Import/Document/App item, every available export and the 8 most recent documents, by name in English or French, case- and accent-insensitive, showing where each lives and its shortcut; Enter runs the first. Opening it by gesture comes with D15. Synonyms beyond the two languages are not added.
- [ ] D78. **Full-screen canvas on demand.** One gesture or tap hides every bar and panel; a
  second brings them back exactly as they were. Edge-to-edge drawing under the system bars, with
  the gesture navigation area protected from stray strokes.
- [ ] D79. **Undo and redo always in thumb reach** on the phone, as buttons as well as gestures,
  with a long-press on undo to scrub back through the history.

## Phase D3 — Second nature: muscle memory, gestures, stylus, keys

Goal: switching tools and adjusting without looking or opening anything.

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
- [~] D20. **One colour flow.** A single inline colour strip (current, recent, saved swatches,
  harmonies) that expands in place to the full picker. "Change everywhere" becomes a visible
  action, not a hidden long-press.
  Written, not yet compiled or tested: `ColorStrip.kt` in the properties bar for pen, shape, Bézier and fill: current colour (tap: the full picker as a live, non-blocking panel), saved, recent, five harmonies, the palette. While the current colour is a saved one, "Change everywhere" and "Remove" show as buttons. The toolbar's separate colour rows and button are gone.
- [~] D21. **The quick menu becomes a radial menu** that opens from any gesture, key or button,
  holds any action, and supports flick selection without looking.
  Written, not yet compiled or tested: the ring opens from its button, a gesture (D15), the stylus button or Q (D18); it can hold any tool, pen/eraser swap and brush size besides the earlier actions; a flick from the button toward an action runs it on release without showing the ring, ticking as each comes under the thumb.
- [ ] D80. **Last-tool toggle.** One tap (or gesture) swaps between the current and the previous
  tool, with each keeping its own size, colour and settings.
- [ ] D81. **Per-tool memory.** Every tool remembers its last size, opacity, colour and options;
  the eraser never inherits the pen's size by surprise.
- [ ] D82. **Pressure curve tuning in one screen**, drawn on the screen with your own stylus or
  finger, with a live test stroke; per-pen presets.

## Phase D4 — Customisation everywhere

Goal: every surface rearrangeable, layouts portable.

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
- [~] D25. **UI density and size.** Compact, comfortable or large, scaled on the §7 steps,
  separate from the system font scale.
  Written, not yet compiled or tested: Settings > Appearance > Interface size: Standard, Large (×1.25), Largest (×1.5, which keeps the §7 steps: 16→24, 32→48); it scales every dp while text still follows the system font size on top. No size below standard, so touch targets never shrink; a compact mode for experts is D90.
- [ ] D83. **Per-workspace layouts.** Each workspace (D11) remembers its own rail, panels and quick
  menu; switching workspace brings them back.
- [ ] D84. **Custom tool presets on the rail.** Save a tool with its settings as a named preset
  ("Ink 2 px blue", "Soft eraser") and pin it like a tool.
- [ ] D85. **Reset to defaults** per surface and for everything, with a preview before applying
  and an undo after.

## Phase D5 — Zero friction, zero frustration

Goal: nothing lost, nothing blocking, nothing to hunt for.

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

## Phase D6 — Depth for experts without cost for beginners

Goal: complete and deep to master, while the first minute stays simple.

- [~] D32. **Progressive disclosure.** Advanced settings stay one tap away inside the same panel
  (a "More" disclosure), never a separate dialog.
  Written, not yet compiled or tested: `FernDisclosure.kt` (`MoreOptions`, `MoreInline`), expanded state announced to screen readers and kept across rotation. Used for the drawing tools' line options and stabiliser in the properties bar, and for an object's physics, hitbox and links in its panel; other panels can adopt it as they're reworked (D42-D47).
- [~] D33. **Pro surfaces** for the deep features: dopesheet and timeline docked, the node or
  block editor for game logic as a panel, the script editor with syntax highlighting, line
  numbers and inline errors.
  Written, not yet compiled or tested: `CodeEditor.kt` for the drawing and object scripts: line numbers, keywords/numbers/texts/comments coloured from the theme, checked on every keystroke with the error's line number in red and its message below. The dopesheet and X-sheet open as docked panels; the rules (blocks) were already a panel (D10). Merging timeline, X-sheet and dopesheet into one strip is D45.
- [~] D34. **Recorded actions and macros.** Repeat the last action, and record a sequence and
  bind it to a key or gesture (it builds on the scripting language of ROADMAP item 164).
  Written, not yet compiled or tested: `Macros.kt`. Actions run from the quick menu (tap or flick) or the palette are remembered: Repeat last action on Ctrl+., any gesture (Settings) or the palette. App > Record a macro records them until Stop recording, then names and saves the macro; saved macros run from the palette. A macro holds quick-menu actions (tools, undo/redo, duplicate, delete, colour, size), not strokes. Binding one macro to its own key is not done; Repeat last is the bindable one.
- [~] D35. **Usage-aware suggestions (on-device only).** Offer to pin a tool or action you use
  often but reach through menus. No telemetry leaves the device (CLAUDE.md §8.1).
  Written, not yet compiled or tested: `UsageHints.kt`. A tool picked five times from "More tools" or the palette while not on the rail gets one offer to pin it (snackbar with Pin). Counts stay in the app's own preferences; nothing leaves the device.
- [ ] D89. **Gesture and shortcut discoverability.** Long-press any control to see what it does,
  its gesture and its shortcut, in both languages; a "tips you haven't tried" list in Help.
- [ ] D90. **Expert density.** An optional compact mode for experienced users showing more tools
  at once on the phone, without shrinking hit areas below 48 dp.

## Phase D7 — The canvas itself: feedback while drawing

Goal: the canvas says what is happening without a single word or dialog.

- [~] D36. **Brush cursor and hover preview.** The real footprint of the brush (size, shape,
  angle) under the stylus or mouse before touching, and a crosshair option for small sizes.
  Written, not yet compiled or tested: a hovering stylus (or mouse) shows the pen's or eraser's footprint at its real size (D16), switching to a gapped crosshair when the footprint is under 4 px. The footprint is drawn round; stamp brushes' own tip shapes and angles aren't drawn.
- [~] D37. **Live readouts at the point of action.** While drawing a shape, moving or scaling:
  length, angle, size and position next to the pen, in the document's scale (ROADMAP item 143),
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

## Phase D8 — Panels in depth

Goal: every panel from Phase D2 designed to the same pattern and fast to work in.

- [ ] D42. **One panel pattern.** A header (title, search or filter, the main action,
  collapse), a body with rows that can be dragged, swiped and long-pressed for the same actions
  everywhere, and a footer only when it's needed.
- [ ] D43. **Layers panel.** Thumbnails, visibility and lock on the row, opacity with a drag on
  the row, the blend mode inline, drag to reorder or into groups, swipe to delete with Undo,
  multi-select, and layer comps (ROADMAP item 123) in the same panel.
- [ ] D44. **Brush and preset library.** A visual grid with live stroke previews, tags and search
  (ROADMAP item 140), favourites pinned to the rail, and editing beside the preview, not in a
  separate dialog.
- [ ] D45. **Timeline panel.** Frames, onion skin, tags and the X-sheet in one dockable strip with
  scrubbing, play and loop at the edge of the hand. It replaces the animation toolbar group and
  the separate X-sheet and dopesheet dialogs.
- [ ] D46. **Objects and logic panel.** Object list, properties, behaviours, rules (blocks) and
  script in one panel with tabs, instead of the object dialog opening states, rules and script
  dialogs on top of each other.
- [ ] D47. **Inspector for the selection.** Transform, style, constraints (ROADMAP item 144) and
  the measurement for whatever is selected, always in the same place.
- [ ] D91. **Swipe between panels.** On the phone, swipe sideways inside the bottom sheet to move
  between the open panels (layers, brushes, timeline) without closing and reopening.
- [ ] D92. **Panel peek values.** A collapsed panel still shows the one thing that matters (the
  active layer, the current frame, the brush) so most changes need no expansion.

## Phase D9 — Home, documents and files

Goal: getting into a drawing and back out is instant.

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

## Phase D10 — Forms of device

Goal: the phone layout perfected first, then the same app at home on a tablet, a foldable, a
Chromebook and with a mouse.

- [ ] D52. **Phone layouts first, then responsive.** Phone portrait and landscape are designed first and kept the reference. Then responsive layouts for compact, medium and expanded widths (Material window size
  classes): a bottom rail and sheets on phones, side rail and docked panels on tablets, several
  panels at once on wide screens.
- [ ] D53. **(after phone) Foldables and multi-window.** The layout follows the hinge (tabletop mode: canvas
  above, tools below); drag and drop of images and text in and out of the app.
- [ ] D54. **(after phone) Mouse and trackpad.** Hover states, right-click context menus, scroll to zoom, middle
  drag to pan, and tooltips that show the shortcut.
- [ ] D55. **One-hand mode** (phone priority). Everything reachable in the bottom third of a phone; the rail and
  the quick menu mirrored for the left hand.
- [ ] D56. **Orientation without loss.** Rotating keeps the view centre, the open panel and the
  selection, with no relayout jump.

## Phase D11 — Motion, sound and identity

Goal: an app with a character that never gets in the way.

- [ ] D57. **Motion system.** A few durations and easings as tokens (D1). Panels slide from where
  they're docked, menus grow from the button that opened them, and nothing animates longer than
  needed. Reduced motion is respected (D5).
- [ ] D58. **Visual identity.** A Fern brand colour, an icon set drawn on one grid with one
  stroke weight (instead of mixed Material icons), and a distinctive yet quiet chrome that lets
  artwork dominate.
- [ ] D59. **Optional sound cues** for the paper feel (pencil, marker) and confirmations, off by
  default.
- [ ] D60. **Dark, light and canvas-matched themes**, with the chrome able to follow the
  document's background to reduce glare while painting.
- [ ] D95. **Icon labels.** Each icon has a short label in both languages, shown below it, as a
  tooltip, or hidden, as the user chooses; icons are recognisable without colour.

## Phase D12 — Languages and content

Goal: the app reads naturally everywhere it's offered.

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

## Phase D13 — Recovery and trust

Goal: when something goes wrong, the user is never the one who pays.

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

## Phase D14 — Notes and handwriting on the phone

Goal: Fern as pleasant for quick notes and PDF markup as for drawing, on a phone.

- [ ] D98. **Paper templates on the phone.** Lined, grid, dotted and Cornell pages that fit the
  phone width, with zoom that keeps handwriting a comfortable size.
- [ ] D99. **Writing comfort.** An auto-scrolling writing zone (write in a magnified strip at the
  bottom, the page fills above), palm rest area, and a shift of the page as you reach the edge.
- [ ] D100. **PDF markup flow.** Open a PDF, highlight, write, sign (ROADMAP item 129) and share
  it back in a few taps, with the tools a reader needs and nothing else.
- [ ] D101. **Audio notes in reach.** Record, pause and play back (ROADMAP item 130) from a small
  bar that stays out of the writing area.

## Phase D15 — Verification

Goal: proof that the rework met the priorities.

- [ ] D69. **Accessibility checks in CI.** Compose UI tests with `assertHasClickAction`,
  label, touch-size and contrast checks on every screen.
- [ ] D70. **Task-time benchmarks.** On a phone, taps and seconds to do the 20 most common tasks (draw, change
  colour, undo, new layer, select and move, export to share, …), measured before and after the
  rework, and kept from regressing.
- [ ] D71. **Real-device passes** on phones first (a small and a large phone), with TalkBack,
  Switch Access, a stylus and one hand, in English and in French; keyboard and tablet passes after.
  The results go in the commit that closes each phase.
- [ ] D72. **Usability sessions.** A few people new to Fern and a few experienced artists try the
  same scripted tasks after each phase; what made them hesitate becomes items here.
- [ ] D73. **Friction log.** Every "where is…?" and "how do I…?" found in testing or feedback is
  recorded against the item that should fix it, and a phase is not closed while one of its
  entries is open.
- [ ] D97. **Contrast and colour-blind checks** of every theme, automated against WCAG 2.2 AA
  (4.5:1 text, 3:1 controls), with simulations for protanopia, deuteranopia and tritanopia.
