# Fern product review — October 2026

Input for [Roadmap_Master.md](Roadmap_Master.md). A review of where Fern stands against its own vision and criteria, how professional creative tools and competitors solve the same problems, and which opportunities matter most.

**How to read the evidence labels.**
- **Verified (code):** read in Fern's source at the commit this review was written on; file and line given.
- **Verified (source):** stated in the vendor's own documentation or a cited page. Vendor marketing copy is called out as such.
- **Analysis:** a conclusion drawn from verified facts.
- **Assumption:** not checked; stated so it can be checked.

**Limits of this review.** Nothing was run on a device; no usability sessions or performance measurements were made, so every UX finding comes from the code, not from watching people. Competitor facts come from documentation and store listings found in about 40 minutes of research; tools that were not looked at are listed in §8.

---

## 1. Executive summary

Fern is broad but shallow where it matters most. Its history in this repository (231 commits since 12 October 2026) gave it an infinite vector canvas with real engineering depth (Double precision with a floating origin, a spatial index, tile caching, front-buffered ink), plus raster painting, animation, a bone rig, 3D painting, game logic, scripting and about 25 export formats. Very few of the deep features match how professionals use the equivalent tools. Four foundations are the cause, and each one blocks a family of features:

1. **The UI is not built on the project's own design rules.** Tokens exist and are on the §7 scale (`FernTokens.kt`), but `MaterialTheme` receives no typography or shapes (`FernTheme.kt:64`), and Material components keep their own off-scale sizes. Features were added as buttons in shared bars: about 70 controls in the pen's properties bar and about 27 in the toolbar strip (`Toolbar.kt`). This breaks priorities 1, 3 and 7 of the vision.
2. **Guides are a tool, not an aid.** Turning the ruler on makes it the active tool, so drawing stops until the pen is picked again (`CanvasState.kt:4445`, `Toolbar.kt`). There is one ruler kind and two perspective modes. Professional tools treat guides as persistent aids the pen snaps to.
3. **Brushes are fixed behaviours, not data.** Eight hard-coded pen types; raster brushes have four settings (`RasterBrush.kt:34`); a custom tip is only an image file for the stamp pen. Procreate, Clip Studio and Krita all treat a brush as an editable, shareable set of parameters, and let artists turn their own drawing into a brush.
4. **The rig model can't rig a drawing.** A bone is two points, a pose is a full copy per frame, and a whole stroke belongs to one bone (`Skeleton.kt:16`, `Stroke.kt:75`, `CanvasState.kt:1887`). Spine's model (slots, attachments, meshes with weights, keyed timelines, constraints) is what artists expect from "rigging".

**Recommendation.** Stop adding features on the current foundations. Rebuild these four, in the order UI foundation → groundwork → guides and brushes (in parallel) → colour → rig data model, then resume feature work on top. Roadmap_Master.md already holds this order (Phases 1–6 and 11–17); this review supplies the evidence, sharpens several items, and adds four missing initiatives (§7).

**Most important risk.** About 30 design items from D15 to D46 were written in a batch without being compiled; they now compile and pass unit tests (fixed in this branch), but none has been tried on a device (`Roadmap_Master.md`, items marked "Written, not yet compiled or tested"). Building more on them before a device pass compounds the risk.

---

## 2. Current product assessment

### 2.1 Vision and criteria (confirmed requirements)

From `README.md`, `CLAUDE.md` and the ground rules in `Roadmap_Master.md`:

- An infinite-canvas drawing app for Android, inspired by Endless Paper on iOS (`README.md`).
- **Phone first**; tablet, keyboard and mouse after, never at the phone's expense.
- **100 % bilingual** English and French.
- Eight absolute priorities in order: fast to pick up and deep to master; accessibility; clean and efficient; user friendly; zero friction; tool use as second nature; at most one level of menus; entirely customisable.
- **No external platforms** for now (CLAUDE.md §8.1): no cloud, accounts, Play Services or hosted AI.
- **Design tokens on the §7 scale**, radii by §7.3, and functional completeness of every named feature (§6).

Who the target users are is not written down anywhere beyond "drawing app" and the specialty modes in the roadmap (comics, pixel art, notes, animation, game logic). See open question Q1.

### 2.2 What exists (verified, code)

| Area | State | Evidence |
|---|---|---|
| Codebase | 210 Kotlin files, about 39,000 lines; 128 unit and Robolectric UI test files; Jetpack Compose; min SDK 26, target 34 | `find app/src`, `app/build.gradle.kts` |
| Concentration | `CanvasState.kt` 6,269 lines, `DrawingCanvas.kt` 2,211, `MainActivity.kt` 1,809, `Toolbar.kt` 1,633, `CanvasStorage.kt` 1,352 | `wc -l` |
| Canvas engine | Double precision, floating origin, spatial index, tile cache, GPU tile renderer, front-buffered ink, motion prediction | `README.md`, `CanvasState.kt:40–62`, `SceneTileCache.kt`, `FrontBufferedInk.kt` |
| Tools | 12 tools; 8 pen types; 7 shape kinds; symmetry (5 modes); 1- and 2-point perspective; one straight ruler | `CanvasState.kt:37`, `Stroke.kt:24`, `Shapes.kt:14`, `Symmetry.kt:13`, `PerspectiveGuide.kt:17` |
| Raster | Brush with mode, tip, wetness, strength; raster engines (paint, wet, smudge, blur, liquify, spray, bristle) | `RasterBrush.kt:34`, `RasterBrushControls.kt` |
| Colour | Current, 16 saved (cap), 8 recent, 5 harmonies, a fixed palette, all in one row; GPL and ASE import | `ColorStrip.kt:44–67`, `CanvasState.kt:124–127` |
| Rig | Bones (head, tail, limits, dynamic flag), per-frame pose copies, one bone per stroke plus point weights, CCD IK up to 4 bones, Spine/DragonBones export | `Skeleton.kt:16–35`, `Stroke.kt:75`, `SpineExport.kt:10–25`, `SpineImport.kt` |
| 3D | Built-in shapes and OBJ, painted colour/roughness/metallic, posable hand and head, GLB/USDZ export; placed on the canvas as a still image | `Model3d.kt`, `Model3dEditor.kt`, `Render3d.kt` |
| Game logic | Objects, rules, state machines, 2D physics, input mapping, play mode, sandboxed scripting | Roadmap items 155–164 |
| Languages | 1,058 strings in English and 1,058 in French | `res/values*/strings.xml` |
| UI structure | Tool rail (4 fixed + pinned), properties bar, toolbar strip, labelled menus, command palette, panels (`FernPanel`) with 34 `AlertDialog`s still in use | `ToolRail.kt`, `Toolbar.kt`, `FernPanel.kt` |
| CI | Builds and tests on pushes to `main` only, not on branches or pull requests | `.github/workflows/build-apk.yml` |

### 2.3 Strengths worth protecting (analysis)

- **The infinite canvas is genuinely engineered.** The precision design is documented and sound; few mobile apps go this far. It is the differentiator against Endless Paper's Android absence and against fixed-canvas painters.
- **Breadth in one document.** Drawing, animation, rig, game logic and 3D share one canvas and file. No single competitor looked at here combines these (see §3).
- **Offline and private by design.** §8.1 makes this a feature, not a limitation, for artists wary of cloud and AI.
- **Bilingual parity is real**: the string counts match exactly.

### 2.4 Weaknesses (verified unless marked)

1. **Off-scale UI by default.** No `Typography` or `Shapes` passed to `MaterialTheme` (`FernTheme.kt:64`); Material defaults include text at 11, 22, 28 and 57 sp and radii of 28 dp, and components such as text fields (56 dp tall) and buttons (40 dp) are used directly in 43 text-field and many button call sites. Material's default sizes are from its own specification (assumption: not re-checked against the exact Material 3 version in the build).
2. **Chrome grows with every feature.** The pen's properties bar holds about 70 controls in one horizontally scrolling row and the toolbar about 27 buttons (`Toolbar.kt:595–1150`, `1150–1350`). The colour row can reach about 30 swatches (`ColorStrip.kt`).
3. **Guides block drawing** (see §1, item 2), and the ruler switches off perspective (`CanvasState.kt:3739`).
4. **Eyedropper reads vector strokes only** (`CanvasState.kt:2976`): raster paint, images, fills and paper give nothing.
5. **Rigging works on whole strokes**, bound by their bounding-box centre (`CanvasState.kt:1887`); raster paint, images and fills can't be rigged.
6. **Spine round trip is lossy**: export bakes each bone's ink into one image, so smooth bending becomes rigid; import drops scale, shear, constraints and attachments (`SpineExport.kt:22`, `SpineImport.kt` header).
7. **Unverified UI batch.** Items D15–D46 were written without compiling; the first build after the merge failed with compile errors and 10 failing tests, two of which were real product bugs (multi-finger taps rejected as drags; the gradient panel's controls unreachable). Both are fixed on this branch. The rest has not been tried on a device.
8. **CI runs only after merge**, which is why the uncompiled batch reached `main`.

---

## 3. Professional tool and competitor analysis

Only principles that bear on Fern's decisions are kept. Each point is labelled.

### 3.1 Drawing and painting

**Brushes are data with a studio around them.**
- Procreate: a brush is a shape that holds a texture (grain); settings cover stroke path (spacing, streamline, jitter), taper (start/end, pressure-based), shape, grain, dynamics and rendering, edited in the Brush Studio — **verified (source)**, Procreate Handbook, Brush Studio Settings.
- Clip Studio Paint: a selection on a raster layer can be registered as an image material with "Use as brush tip shape", then chosen as the tip of any brush — **verified (source)**, Clip Studio manual and tips.
- Krita: about 18 brush engines (Pixel, Color Smudge, Bristle, Hatching, Particle, Sketch, Spray…), each a parameter set — **verified (source)**, Krita manual, Brush Engines.
- Infinite Painter (Android): "almost 100 parameters" per brush, import and export of brushes and sets — **verified (source)**, developer documentation (product page wording).
- **Analysis.** The common model is one engine (or a few) driven by an editable parameter set, with presets as data and the artist's own marks as tips. Fern's fixed pen types are the opposite model; adding settings to each hard-coded type would multiply code. This supports items 349, 189–202, 347 and 348.

**Guides are persistent aids the pen snaps to.**
- Procreate: four drawing guides (2D grid, isometric, perspective, symmetry); perspective with up to three vanishing points; "Drawing Assist" makes strokes follow the guide; edited in a guide editor and committed with Done — **verified (source)**, Procreate Handbook, Guides.
- Clip Studio Paint: rulers (straight, curve, figure, ruler pen) and special rulers (special ruler, guide, perspective, symmetrical), several on the canvas at once, snapping toggled from the command bar; perspective rulers with 1, 2 and 3 points — **verified (source)**, Clip Studio tips (rulers, snapping).
- Krita: painting assistants including vanishing point, concentric ellipse and fisheye point, with "snap to assistants" on the brush — **verified (source)**, Krita manual, Painting with Assistants.
- Infinite Painter: five perspective guides, shapes in perspective, an isometric option and a hatching guide — **verified (source)**, developer page.
- **Analysis.** Every tool separates *placing/editing* a guide from *drawing with* it, and lets the pen snap without switching tools. Fern's ruler-as-tool is the root problem behind "it prevents me from drawing". Supports items 340–346.

**Colour.** Procreate, Krita and Clip Studio all have palettes as their own panel with multiple named sets — **assumption** for this review (not re-verified in this session; well known but not cited). Supports items 350–352; mark as needing a quick check before design.

### 3.2 Animation

- Blender Grease Pencil: keyframes hold stroke data per layer; auto-key while drawing; a dedicated dope sheet mode; onion skinning of neighbouring keyframes; objects can also be animated by transform, hooks or an armature for cut-out animation — **verified (source)**, Blender manual, Grease Pencil animation.
- FlipaClip (Android): frame-by-frame, a timeline, onion skin, layers (3 free, 10 paid, with a developer warning about performance above 6), audio tracks — **verified (source)**, store listing (marketing copy).
- Spine: setup mode vs. animate mode; keys per property interpolated between; dopesheet (timing of many properties) and graph (values over time) — **verified (source)**, Spine user guide (keys, graph).
- **Analysis.** Two models coexist in pro tools: drawn frames (Grease Pencil, FlipaClip) and keyed properties (Spine, Blender objects). Fern has the first and a per-frame copy of the second. The keyed-timeline model (item 251) is the missing core; it serves rigs, layer properties and the camera with one mechanism.

### 3.3 Rigging

- Spine slots and attachments: attachments sit in slots, a slot is attached to a bone and shows one attachment at a time; attachment kinds are region, mesh, bounding box, clipping and path; skins choose attachments — **verified (source)**, Spine user guide, Attachments.
- Spine weights: each mesh vertex has a weight per bound bone; tools Auto (by mesh topology, not plain distance), Smooth, Prune (with a bone cap per vertex), Weld, Swap, Lock, Update Bindings; weights always total 100 % — **verified (source)**, Spine user guide, Weights.
- Spine IK: one or two bones only, because three or more is "nondeterministic"; bend direction, mix (FK/IK blend), softness, stretch and compress — **verified (source)**, Spine user guide, IK constraints.
- **Analysis.** Fern's IK solves chains of up to four bones with CCD (`Roadmap_Master.md` item 85 history). Spine's documented choice to limit IK to two bones and expose bend direction gives predictable, keyable results; Fern's item 288 already proposes an exact two-bone solver and should make it the default, keeping CCD only as an option. Fern's automatic weights are inverse-distance (history of item 84); Spine computes them from mesh topology, which is what stops an arm pulling the torso. Supports items 282–285.
- **Analysis.** Spine is the export target Fern already supports, so matching its data model is the lowest-risk path: what Fern can express, Spine can import, and the runtime ecosystem follows.

### 3.4 3D modeling and sculpting

- Nomad Sculpt (Android): clay, flatten, smooth and mask brushes; layers for sculpt and paint operations; multiresolution; voxel remesh; dynamic topology; PBR with HDRI and matcap — **verified (source)**, store listing via APKMirror (marketing copy).
- Shapr3D: direct modeling with constraints as aids rather than drivers; sketch then sweep, revolve or pull; touch and pencil manipulation with one hand steering the view — **verified (source)**, vendor page and a 2021 review.
- **Analysis.** Fern should not compete with dedicated 3D tools. The 3D value for a drawing app is *reference*: a posable or blocked-out model whose camera matches the drawing's perspective (item 321) — the same reason Clip Studio ships 3D figures (assumption: not re-verified here). Sculpting and full modeling are out of scope; extrude and lathe (item 323) are the cheap, drawing-adjacent exceptions.

### 3.5 CAD and precision

- AutoCAD object snaps: endpoint, midpoint, centre, intersection, perpendicular, tangent, extension, apparent intersection, parallel and more; a recommended small running set because enabling everything causes mis-picks; one-time overrides; object snap tracking and polar tracking; dynamic input showing values at the cursor — **verified (source)**, Autodesk help and training pages.
- Concepts (Android and iOS): vector infinite canvas with shape guides, "live snap" and measurement — **verified (source)**, developer page (marketing copy).
- **Analysis.** Two principles transfer to Fern: (1) a *small, chosen* set of snap targets with a per-stroke override, not "snap to everything"; (2) values shown and typed at the point of action. Fern has smart guides and object snapping (`ObjectSnapping.kt`) and a drawing scale (item 143); item 172 (on-canvas numeric entry) and the guide model (340–341) carry these principles. A full CAD constraint solver is not justified by the vision.

### 3.6 Competitors and alternatives

| Product | Platform | What it is best at | Relevance to Fern | Label |
|---|---|---|---|---|
| Endless Paper | iOS only | Infinite zoom canvas, "Fractile" vector engine claimed at up to 120 fps with millions of strokes | Fern's stated inspiration; absent on Android, which is Fern's opening | verified (source, marketing) |
| Concepts | Android, iOS | Vector infinite canvas with precision (guides, snap, measure) | Closest Android alternative for the "infinite canvas + precision" user | verified (source, marketing) |
| Infinite Painter | Android | Deep brush editor, perspective and hatching guides | The bar for brushes and guides on Android phones | verified (source) |
| ibis Paint X | Android, iOS | Very large brush library, rulers (radial, symmetry), stabiliser, recording | Mass-market painting on phones | verified (source, marketing) |
| FlipaClip | Android, iOS | Simple frame-by-frame animation | The bar for approachable animation on phones | verified (source, marketing) |
| Clip Studio Paint | Android, iOS, desktop | Rulers, brush materials, comics, 3D references | The professional bar for comics and line art | verified (source) for the cited features |
| Krita | Desktop (Android status not confirmed here) | Brush engines, assistants | Open-source reference for engines and assistants | verified (source); Android availability unverified |
| Spine | Desktop | 2D skeletal animation for games | The export target and the rig model to match | verified (source) |
| Nomad Sculpt | Android, iOS | Mobile sculpting and painting | Shows what mobile 3D can be; out of Fern's scope | verified (source, marketing) |

**Where Fern can win (analysis).** No product above combines an infinite vector canvas, painting, animation, Spine-compatible rigging and game logic offline on an Android phone. The opportunity is not to out-feature each specialist but to make the *combination* coherent: draw, rig your own drawing, animate, export to Spine or play it — on one canvas. That only works if each step meets the bar of the specialist for its core workflow, which today it doesn't (§2.4).

---

## 4. UI/UX assessment

Judged against the eight priorities. "Material" means it changes how well people can work; "cosmetic" means it changes looks only. Findings come from code; none were observed with users (see §8).

| Criterion | Finding | Evidence | Kind |
|---|---|---|---|
| Information architecture | Tool settings, colour, layers and guides compete in one properties row of about 70 controls; the toolbar strip adds about 27 more. | `Toolbar.kt` counts | Material |
| Workspace customisation | Strong on paper: workspaces, layout presets, pinned tools, gesture map (`Workspaces.kt`, `LayoutPresets.kt`, `GestureMap.kt`); unverified on device. | Roadmap D11, D22–D23 | Material |
| Workflow efficiency | Ruler use takes three tool switches per guided stroke set (ruler on → pen → ruler to adjust). Eyedropper fails on raster and images. | `CanvasState.kt:4445`, `2976` | Material |
| Discoverability | Icons without labels in long scrolling strips; D95 (labels) not started. Command palette helps (`CommandPalette.kt`). | `Toolbar.kt`, Roadmap D95 | Material |
| Visual hierarchy | The active tool's essentials (size, opacity, colour) are not separated from rare options; everything has the same weight in one row. | `Toolbar.kt:595+` | Material |
| Consistency | Panels and 34 modal dialogs coexist; off-scale Material defaults beside on-scale tokens. | `FernPanel.kt`, `FernTheme.kt:64` | Material (dialogs) and cosmetic (radii, type) |
| Accessibility | Good base: 48 dp targets, labels, live region, colour-blind theme (Roadmap D1–D7 history). Text sizes from Material defaults are not checked against the 12 sp floor in all components (assumption). | `FernTokens.kt`, D5 | Material |
| Perceived performance | Engine is strong; panel opening times not measured (D70). | Roadmap D31, D70 | Unknown |
| Onboarding | Coach marks per tool (`CoachMarks.kt`), practice page; unverified. | D29 | Material |
| Feedback | Mode chips, gesture badges, readouts exist (D37–D40); the ruler gives no "locked" state. | `ModeChips.kt` | Material |
| Error handling | Messages say what to do (D30, D65 partly). Silent failures remain: the eyedropper on empty or raster areas does nothing. | `CanvasState.kt:2976` | Material |
| Scalability as features grow | The core problem: each feature adds a button to a shared bar; nothing limits growth. | `Toolbar.kt` | Material |

**Core workflows to prioritise (analysis, pending Q1).** (1) Sketch and ink on a phone; (2) colour and paint; (3) draw with guides (perspective, rulers); (4) rig and animate your own drawing; (5) export to share or to Spine. Workflows 1–3 are what a new user meets first; 4–5 are Fern's differentiator.

**Cosmetic vs. material.** The type and shape scale (items 334–335) are mostly cosmetic but cheap and required by CLAUDE.md §7. The chrome budget, retiring the toolbar, guides that don't block drawing, and panels instead of dialogs (336–340) materially change usability and come first in effort terms.

---

## 5. Opportunity backlog

Ranked by priority, then by dependency. "Roadmap" names the Roadmap_Master.md items each opportunity maps to; **new** means it is not in the roadmap yet. Effort: S (days), M (one to three weeks), L (a month or more), for one developer — an estimate from the code, not measured.

### O1. Verify before building further — Critical
- **Problem.** About 30 UI items (D15–D46) were written without compiling; the first build failed and two of ten failing tests were real bugs. CI runs only on `main`, so broken work lands before anyone sees it.
- **Evidence.** §2.4 items 7–8; `.github/workflows/build-apk.yml` triggers on `push` to `main` only.
- **Solution.** Run the build and tests on every pull request and branch push; then a device pass over D15–D46 on a phone (items D71), fixing or reverting what fails.
- **Impact.** Stops broken work reaching `main`; turns 30 "written" items into known states.
- **Effort.** S for CI; M for the device pass.
- **Dependencies, risks.** Needs a phone and the owner's time (CLAUDE.md §2 says tests aren't run unless asked: Q4).
- **Next action.** Add `pull_request` and branch triggers to the workflow.
- **Roadmap.** **New** (item 355); D71.

### O2. UI foundation on the project's own rules — Critical
- **Problem.** Default Material type, shapes and component sizes break CLAUDE.md §7; chrome grows with every feature.
- **Evidence.** `FernTheme.kt:64`; about 70 + 27 controls in shared rows (§4).
- **Solution.** Fern typography and shapes on the scale; Fern component wrappers; a chrome budget; retire the toolbar strip; remaining dialogs to panels.
- **Impact.** Every later screen is built compliant; the canvas gets its space back (priorities 1, 3, 7).
- **Effort.** L overall; type and shapes S; component wrappers M; toolbar retirement M–L.
- **Dependencies, risks.** Touches most UI files; do it before new UI, or every new screen is rework. Risk of regressions without device checks (O1 first).
- **Next action.** Write the `Typography` and `Shapes` for `FernTheme` and a test that fails on off-scale values.
- **Roadmap.** 334–339, D42, D95.

### O3. Split the tool code before adding tools — High
- **Problem.** Every tool lives in the same `when` blocks of three very large files; the compile errors in the D-batch came from exactly there.
- **Evidence.** `CanvasState.kt` 6,269 lines, `DrawingCanvas.kt` 2,211, `Toolbar.kt` 1,633; CLAUDE.md §4.5.
- **Solution.** One handler per tool (press, move, release, cancel) with its own state and properties UI; gesture tests first.
- **Impact.** New tools stop touching shared code; reviewable changes; fewer regressions.
- **Effort.** L, done tool by tool.
- **Dependencies, risks.** Behaviour must stay identical: gesture tests (168) come first.
- **Next action.** Write the gesture tests, then move one simple tool (eyedropper) to the new structure as the pattern.
- **Roadmap.** 167–172.

### O4. Guides that don't block drawing — High
- **Problem.** The ruler is a tool, so drawing stops while it is on; one ruler kind; perspective 1–2 points without a grid; the ruler turns perspective off.
- **Evidence.** `CanvasState.kt:4445–4488`, `3739`; `PerspectiveGuide.kt:17`.
- **Solution.** Guides as placed, locked objects the pen snaps to, edited in an explicit mode; ruler kinds; 1–3-point perspective with a grid; several guides at once; per-stroke snap override.
- **Impact.** Unblocks a core workflow (drawing with guides) that every reference tool supports (§3.1).
- **Effort.** M for the model and straight ruler; M for perspective; S–M per extra kind.
- **Dependencies, risks.** Handle framework (171). Keep a small default snap set (§3.5) to avoid mis-snaps.
- **Next action.** Move the ruler out of `Tool` into a guide list on `CanvasState`, snapping while the pen is active.
- **Roadmap.** 340–346.

### O5. One brush engine, with brushes from your own drawing — High
- **Problem.** Fixed pen behaviours; four raster brush settings; custom tips only from a file for the stamp pen.
- **Evidence.** `Stroke.kt:24`, `RasterBrush.kt:34`, `StampBrush.kt:13–20`, `BrushEditorDialog.kt:80`.
- **Solution.** Brushes as parameter sets (pressure curve, spacing, taper, shape and colour dynamics, grain); pen types become presets; tip and grain from a selection; brush studio; import and export.
- **Impact.** Pen customisation the owner asked for; parity with the Android bar (Infinite Painter) and pro tools (§3.1).
- **Effort.** L.
- **Dependencies, risks.** Stroke file format changes (keep old strokes readable: §6). Performance of per-stamp dynamics on phones must be measured.
- **Next action.** Design the brush parameter record and its storage version; map the 8 pen types onto it without visual change.
- **Roadmap.** 349, 189–204, 347, 348, D82.

### O6. Colour as a system — High
- **Problem.** No palette panel; 16-colour cap; everything in one row; eyedropper blind to raster, images and fills.
- **Evidence.** `ColorStrip.kt:44–67`, `CanvasState.kt:124`, `2976`.
- **Solution.** Palette panel with named palettes; compact colour row; screen-sampling eyedropper with a loupe.
- **Impact.** Core colouring workflow; removes a silent failure.
- **Effort.** M.
- **Dependencies, risks.** Screen sampling (170) needs a render of the composite at a point; reuse the tile renderer.
- **Next action.** Implement screen sampling and switch the eyedropper to it.
- **Roadmap.** 350–352, 170, 173–174, D20.

### O7. Rig data model the Spine way — High
- **Problem.** Fern's rig can't rig an artist's drawing and loses data in the Spine round trip.
- **Evidence.** §2.4 items 5–6; Spine's model §3.3.
- **Solution.** Local bone transforms with scale and shear; keyed timelines with curves; setup pose; then slots and attachments (rig from a selection or from layers), meshes and topology-based weights, two-bone IK with bend direction as the default, other constraints, and a lossless Spine export.
- **Impact.** The differentiating workflow (draw → rig → animate → export or play) becomes real.
- **Effort.** L (several months across Phases 11–17).
- **Dependencies, risks.** Existing documents must convert (per-frame poses to keys; bound strokes to slots). The game logic plays rig animations (item 158), so it must move to the new model too. Biggest single risk in the roadmap; do it in the listed phase order with a converter and tests at each step.
- **Next action.** Write the new bone and timeline data classes and a converter from today's per-frame poses, behind the existing UI.
- **Roadmap.** 250–255, 272–309, 353–354; this review sharpens 288 (two-bone IK default, CCD optional) and 285 (topology-based automatic weights).

### O8. Define the target users and core workflows — High
- **Problem.** The roadmap serves many audiences (artists, note-takers, animators, game makers, CAD-like precision) without saying who comes first, so every feature looks equally important.
- **Evidence.** §2.1: no user definition in README, CLAUDE.md or the roadmap.
- **Solution.** The owner names a primary user and three to five core workflows; the roadmap orders by them; usability sessions (D72) test them.
- **Impact.** Makes "what not to build" decidable.
- **Effort.** S (a decision), then D72.
- **Next action.** Answer Q1.
- **Roadmap.** **New** (item 356).

### O9. Measure performance where it decides adoption — Medium
- **Problem.** No measured pen-to-pixel latency, stroke-count limits or panel opening times, although the engine is the main strength.
- **Evidence.** Roadmap item 7 history ("latency not yet measured on a device"), D31, D70.
- **Solution.** A small benchmark set on one low-end and one high-end phone: ink latency, frame time with 100 k and 1 M strokes, panel open time; tracked over releases.
- **Impact.** Protects the strength that differentiates Fern from Endless Paper's absence on Android; gives evidence for brush-engine choices (O5).
- **Effort.** M.
- **Next action.** Add a macrobenchmark module (assumption: Jetpack Macrobenchmark fits; not checked against the build setup).
- **Roadmap.** **New** (item 357); D70.

### O10. Drawing-tool completeness fixes — Medium
- **Problem.** Remaining gaps in shape, fill, eraser, select, text, Bézier (Roadmap Phase 7).
- **Evidence.** Roadmap items 175–187, each grounded in code read during the audit.
- **Solution, effort, next action.** As listed per item; most are S.
- **Dependencies.** O3 (tool split) and the handle framework.
- **Roadmap.** 175–187.

### O11. 3D as a drawing reference, not a modeler — Medium
- **Problem.** 3D models are painted and placed as still images; their value for a drawing app is as perspective reference.
- **Evidence.** `Model3d.kt`; §3.4.
- **Solution.** Prioritise items 316 (re-render at a new angle on the canvas) and 321 (camera matching the perspective guide) over new 3D features; keep sculpting and modeling out of scope.
- **Dependencies.** O4 (perspective guide).
- **Roadmap.** 313–325, reordered: 316 and 321 first after the fixes.

### O12. Precision for design and CAD-like users — Low (pending Q1)
- **Problem.** Snapping targets and typed values exist in part; a CAD-grade constraint system would be costly.
- **Evidence.** `ObjectSnapping.kt`, item 143; §3.5.
- **Solution.** A small chosen set of snap targets (endpoint, midpoint, intersection, centre, perpendicular, tangent) with a per-stroke override and values typed at the cursor; no parametric constraint solver.
- **Roadmap.** Partly 172, 341; a snap-target set is **new** (item 358), only if Q1 includes design users.

---

## 6. Technical considerations

- **File format versioning.** The brush engine (O5) and rig model (O7) change what strokes and bones store. Old documents must open: add explicit format versions and converters with golden-file tests before changing the model (`CanvasStorage.kt`, `StrokeCodec.kt`). Analysis.
- **State concentration.** `CanvasState.kt` holds nearly every feature's state; Compose recomposition and undo snapshots both scale with it. Splitting per tool (O3) also limits what each undo snapshot captures. Analysis; undo cost not measured.
- **Rendering cost of richer brushes.** Per-stamp dynamics, grain and dual tips multiply GPU work; the tile cache and GPU tile renderer are the place to absorb it. Measure first (O9).
- **Spine compatibility as a contract.** Treat the Spine JSON schema (4.x) as the rig model's external contract: round-trip tests (export → import → compare) for every rig feature added.
- **Component wrappers.** Wrapping Material components (O2) keeps accessibility semantics Material provides; replacing them outright would lose them. Recommendation: wrap, don't rewrite.
- **CI.** Robolectric UI tests need the `ui-test-manifest` dependency and API 29 for the motion-prediction display issue (already handled in tests on this branch). Branch CI (O1) costs runner time per push; acceptable at this repository's scale (assumption).
- **§8.1 constraint.** All recommendations here work offline; none needs a platform service.

---

## 7. Phased roadmap recommendations

Roadmap_Master.md's phase order already matches this review's dependency analysis. Recommended changes:

1. **Add a Phase 0, "Verify and protect"**, before Phase 1: branch and pull-request CI (new 355) and the device pass over the unverified D-items (D71 moved here). Finding O1.
2. **Add item 356 (target users and core workflows)** to Phase 0 as a decision. Finding O8.
3. **Add item 357 (performance benchmarks)** to Phase 2, before the brush engine. Finding O9.
4. **Sharpen item 288**: two-bone IK with bend direction as the default solver, CCD kept as an option for longer chains. Evidence §3.3.
5. **Sharpen item 285**: automatic weights from mesh topology, not distance alone. Evidence §3.3.
6. **Reorder Phase 19 (3D)**: items 316 and 321 first after the camera and painting fixes. Finding O11.
7. **Add item 358 (snap target set)** to Phase 4 as conditional on Q1. Finding O12.
8. **Add format versioning** as a prerequisite note on Phases 5 and 11. §6.

No established goal or priority conflicts with these changes. One tension is flagged: CLAUDE.md §2 removes building and tests from the definition of done, while O1 recommends CI on every branch; CI runs on GitHub, not in the developer's loop, so it doesn't contradict §2, but the owner should confirm (Q4).

---

## 8. Open questions

- **Q1. Who is the primary user?** Illustrators, comic artists, animators, game makers, note-takers or designers? The answer orders Phases 4–20 and decides O12.
- **Q2. Phone only, or tablet soon?** The ground rules say phone first. Brush studios, dopesheets and rig panels are dense; how much depth is expected on a phone screen?
- **Q3. Spine as the rig target.** Confirm that matching Spine's model (and its export) is the goal, rather than a Fern-specific rig.
- **Q4. CI on branches and pull requests.** Acceptable alongside CLAUDE.md §2?
- **Q5. Monetisation and distribution.** Item 77 (Play Store) is deferred; does a release date change priorities?

**Gaps in this review.** Not examined: Adobe Animate, Maya, Photoshop and BIM tools in depth (only principles via the tools cited); Toon Boom Harmony; Live2D; Krita's Android status; colour palette UIs of the reference tools (marked as assumption in §3.1); Clip Studio's 3D figures (assumption in §3.4). No device testing, user sessions or performance measurement.

---

## 9. Recommended next steps

1. Owner answers Q1–Q4.
2. Add branch and pull-request CI (355).
3. Device pass over D15–D46 on a phone; fix or revert.
4. Fern typography and shapes, with an off-scale check (334–335).
5. Gesture tests, then move the eyedropper into its own handler with screen sampling (168, 167, 170, 173) — proves the tool split and fixes a silent failure in one change.
6. Guide model with the straight ruler as the first kind (340–341).
7. Brush parameter record and format version (349) — design only, measured against benchmarks (357).
8. Rig data model and converter (250–252) — design only, with Spine round-trip tests.

---

## Sources

- Procreate Handbook: [Brush Studio settings](https://help.procreate.com/procreate/handbook/brushes/brush-studio-settings), [Perspective guides](https://help.procreate.com/procreate/handbook/guides/guides-perspective), [Drawing guides](https://help.procreate.com/procreate/handbook/guides/guide-create)
- Clip Studio Paint: [How to use rulers](https://tips.clip-studio.com/en-us/articles/8559), [Snapping to rulers](https://tips.clip-studio.com/en-us/articles/10701), [Creating your own materials](https://help.clip-studio.com/en-us/manual_en/630_material/Creating_your_own_materials.htm), [Using illustrations as brush materials](https://tips.clip-studio.com/en-us/articles/1700)
- Krita manual: [Painting with Assistants](https://docs.krita.org/Painting_With_Assistants), [Brush Engines](https://docs.krita.org/Category:Brush_Engines)
- Spine user guide: [Attachments](https://us.esotericsoftware.com/spine-attachments), [Weights](https://esotericsoftware.com/spine-weights), [IK constraints](https://esotericsoftware.com/spine-ik-constraints), [Keys](https://esotericsoftware.com/spine-keys), [Graph](https://en.esotericsoftware.com/spine-graph.md)
- Blender manual: [Grease Pencil animation](https://docs.blender.org/manual/en/dev/grease_pencil/animation/introduction.html)
- Autodesk: [Object Snap Tracking](https://help.autodesk.com/cloudhelp/2022/ENU/AutoCAD-DidYouKnow/files/GUID-6FB309A8-2696-4383-8277-950E9E2756A8.htm); Noble Desktop: [Object snap](https://www.nobledesktop.com/learn/autocad/object-snap-part-1)
- Infinite Painter: [About the app](https://docs.infinitestudio.art/painter/about/app/)
- ibis Paint X: [store listing via APKMirror](https://apkmirror.com/apk/ibis-inc/ibis-paint-x/ibis-paint-x-12-2-3-release/ibis-paint-x-12-2-3-android-apk-download)
- FlipaClip: [App Store listing](https://apps.apple.com/us/app/flipaclip-create-2d-animation/id1101848914?l=en)
- Nomad Sculpt: [listing via APKMirror](https://www.apkmirror.com/apk/hexanomad/nomad-sculpt)
- Concepts: [Android page](https://concepts.tophatch.com/android)
- Shapr3D: [iPad page](https://www.shapr3d.com/download/ipad), [Digital Engineering review](https://www.digitalengineering247.com/article/shapr3d-unites-modeling-rigor-with-sketching-simplicity/reviews)
- Endless Paper: [App Store listing](https://apps.apple.com/app/id1294105620)
