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
