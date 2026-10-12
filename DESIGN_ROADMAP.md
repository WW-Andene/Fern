# Fern design roadmap

The UI and UX rework, from a deep audit of the current app (October 2026). It runs beside
[ROADMAP.md](ROADMAP.md), which tracks features. This file tracks how those features are reached,
learned and used.

**Status key:** `[x]` done · `[~]` partly done · `[ ]` not started.

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

- [ ] D1. **Design tokens.** One `FernTokens` file for spacing, size, radius (§7.3), type scale,
  elevation and motion, all on the §7 scale. Replace every raw `dp`, `sp` and `Color(0x…)` in UI
  code with a token.
- [ ] D2. **Semantic colour roles.** Canvas chrome, accent, selection, guide, danger and HUD,
  each in light, dark, high-contrast and colour-blind-safe themes. Remove the hard-coded colours
  listed in the audit.
- [ ] D3. **Touch targets.** 48 dp minimum hit area everywhere. Swatches can stay 24 dp to look
  at, with a 48 dp hit area.
- [ ] D4. **Screen reader pass.** Every control gets a label and a role, and the state is
  announced ("Pen, 4 px, selected"). The canvas gets a live description (tool, zoom, selection
  count). No more `contentDescription = null` on actionable elements.
- [ ] D5. **Font scale and reduced motion.** Layouts hold up to 200 % font scale. Nothing is
  under 12 sp. Animations respect the system's "remove animations" setting.
- [ ] D6. **Snackbars replace toasts.** Messages can be acted on ("Layer deleted — Undo"),
  TalkBack announces them, and they're placed away from the drawing hand.
- [ ] D7. **Haptics vocabulary.** Light ticks on snaps, guide hits, the zoom at 100 %, tool
  switches and long-press menus. Each one can be switched off.

## Phase D2 — The new shell: canvas first, nothing more than one level deep

Goal: the screen layout that replaces the single scrolling toolbar and the ⋮ junk drawer.

- [ ] D8. **The tool rail.** A short, fixed rail of the main tools (pen, eraser, select, shape,
  fill, text, eyedropper, plus user-pinned ones) that never scrolls. A tool keeps its position
  for good, so muscle memory works. It docks left, right or bottom, and follows the hand setting.
- [ ] D9. **A contextual properties bar.** Only the settings of the active tool or the current
  selection, shown inline, never in a dialog: pen size, opacity and smoothing; shape kind;
  fill tolerance; text style; the selection's transform and style. This replaces the stroke
  options, fill and text toolbar groups.
- [ ] D10. **Docked side panels instead of modal dialogs** for layers, frames, symbols,
  pins, versions, objects and game logic. They're non-modal, can be resized or collapsed, and
  stay open while you draw. On a phone they're bottom sheets with peek heights. These are the
  dialogs that today block the canvas.
- [ ] D11. **A workspace switcher**: Draw, Paint (raster), Design (vector), Animate, Game, Notes
  (PDF and audio). Each one shows the tools and panels its work needs. The user can edit
  workspaces or create new ones. Animation, replay and guides stop crowding a drawing session.
- [ ] D12. **The ⋮ menu split by purpose**: Insert (image, video, 3D, symbol, QR, signature),
  Import, Export, Document (versions, statistics, search, frames) and App (settings, plugins,
  help). Each is one level deep and opens from a labelled entry, not hidden in an overflow.
- [ ] D13. **Export by purpose.** First choose Share, Print, Image, Vector, Layered (for another
  app), Animation or Game engine; then the format, with a recommended default. The last used
  export is a single tap.
- [ ] D14. **Command palette.** Search every action, tool, setting and recent document by name
  or synonym, in English and French, with the shortcut and location shown beside each. It opens
  from a button, a gesture or Ctrl/⌘ K.

## Phase D3 — Second nature: muscle memory, gestures, stylus, keys

Goal: switching tools and adjusting without looking or opening anything.

- [ ] D15. **A gesture map you can change.** Taps with two, three or four fingers; a pinch with a
  rotation lock; long-press on the canvas as the eyedropper; a three-finger swipe to the quick
  menu; an edge swipe to show or hide panels. Each one is assigned in Settings.
- [ ] D16. **Stylus mapping.** Barrel buttons, the eraser end and double-tap (where the hardware
  has it) assigned to actions. Hover preview of the brush cursor. Palm rejection with a
  sensitivity setting.
- [ ] D17. **Hold to switch for a moment.** Hold a key, a stylus button or a rail tool to use
  another tool (eraser, eyedropper, pan); release to go back.
- [ ] D18. **Keyboard shortcuts.** A full default set (B pen, E eraser, V select, Z/Y undo/redo,
  [ ] size, space pan, and so on), all of them remappable, with a cheat sheet that appears while
  you hold Ctrl.
- [ ] D19. **On-canvas adjusting.** Drag up or down on a rail tool to change its size, sideways
  for opacity, with a live preview ring at the cursor. Sizes snap to user presets.
- [ ] D20. **One colour flow.** A single inline colour strip (current, recent, saved swatches,
  harmonies) that expands in place to the full picker. "Change everywhere" becomes a visible
  action, not a hidden long-press.
- [ ] D21. **The quick menu becomes a radial menu** that opens from any gesture, key or button,
  holds any action, and supports flick selection without looking.

## Phase D4 — Customisation everywhere

Goal: every surface rearrangeable, layouts portable.

- [ ] D22. **Drag-to-arrange edit mode.** Drag tools, actions and panels onto the rail, the
  properties bar, the quick menu and the panels; remove them by dragging away. This replaces
  showing or hiding whole groups.
- [ ] D23. **Layout presets.** Save, name, switch between, export and import whole layouts
  (rail, panels, gestures, shortcuts, workspaces). Ready-made presets: "Minimal", "Procreate-like",
  "Desktop-like", "One-hand".
- [ ] D24. **Settings rebuilt.** Search inside Settings and plain sections: Input (gestures,
  stylus, keys, palm), Appearance (theme, density, UI size), Accessibility, Canvas, Files and
  colour management. Every setting can be reset on its own.
- [ ] D25. **UI density and size.** Compact, comfortable or large, scaled on the §7 steps,
  separate from the system font scale.

## Phase D5 — Zero friction, zero frustration

Goal: nothing lost, nothing blocking, nothing to hunt for.

- [ ] D26. **Everything undoable, or warned about.** Bring frames, layers, objects and resizing
  into undo. Until then, ask once before a destructive action that can't be undone, with "Don't
  ask again".
- [ ] D27. **Live preview** for every adjustment (blend, filters, fill, brush edits, transforms)
  on the canvas itself, with Apply and Cancel inline.
- [ ] D28. **Empty states and inline hints.** An empty layers panel or object list explains
  itself in one line with the action to take. No dead ends.
- [ ] D29. **Learn in context.** Replace the first-run tip dialog with short coach marks that
  appear the first time a tool or gesture is relevant, can be skipped, and can be found again
  in Help. Add an optional interactive practice canvas.
- [ ] D30. **Plain-language copy pass.** Short verbs on buttons, errors that say what to do,
  consistent terms (one name per concept across the app and both languages).
- [ ] D31. **Performance as UX.** Panels and menus open in under 100 ms, no layout shift on the
  canvas when a bar appears, and long jobs (exports, imports) run in the background with
  progress and cancel.

## Phase D6 — Depth for experts without cost for beginners

Goal: complete and deep to master, while the first minute stays simple.

- [ ] D32. **Progressive disclosure.** Advanced settings stay one tap away inside the same panel
  (a "More" disclosure), never a separate dialog.
- [ ] D33. **Pro surfaces** for the deep features: dopesheet and timeline docked, the node or
  block editor for game logic as a panel, the script editor with syntax highlighting, line
  numbers and inline errors.
- [ ] D34. **Recorded actions and macros.** Repeat the last action, and record a sequence and
  bind it to a key or gesture (it builds on the scripting language of ROADMAP item 164).
- [ ] D35. **Usage-aware suggestions (on-device only).** Offer to pin a tool or action you use
  often but reach through menus. No telemetry leaves the device (CLAUDE.md §8.1).

## Phase D7 — Verification

Goal: proof that the rework met the priorities.

- [ ] D36. **Accessibility checks in CI.** Compose UI tests with `assertHasClickAction`,
  label, touch-size and contrast checks on every screen.
- [ ] D37. **Task-time benchmarks.** Taps and seconds to do the 20 most common tasks (draw, change
  colour, undo, new layer, select and move, export to share, …), measured before and after the
  rework, and kept from regressing.
- [ ] D38. **Real-device passes** with TalkBack, Switch Access, a keyboard, a stylus and one hand.
  The results go in the commit that closes each phase.
