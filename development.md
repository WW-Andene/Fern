# Fern tool development plan

Ideas from an audit of the app's tools, in three groups: improving existing tools, developing them further, and creating new ones. Part A covers the drawing tools; part B the animation, rigging, 3D and game-logic side. Effort is an estimate from reading the code, not from prototyping: **S** small, **M** medium, **L** large. Items already in [ROADMAP.md](ROADMAP.md) as done are not repeated here.

The tools today: `Tool` in `CanvasState.kt` (pen, eraser, select, shape, fill, text, ruler, eyedropper, connect, bone, bezier, measure) and `PenType` in `Stroke.kt` (marker, pencil, highlighter, calligraphy, airbrush, crayon, pixel, stamp).

---

# Part A — Drawing tools

## 1. Tool improvement — making existing tools work better

### Eyedropper
- **S–M** `pickColorAt` (`CanvasState.kt`) only checks vector strokes. Raster paint, images, fills and the paper give nothing, and the colour doesn't change. Sample the colour actually shown, with an option to sample only the current layer.
- **S** Show a magnified loupe while held, with the current and new colour side by side.
- **S** Adjustable sampling area (1, 3 or 5 px average): textured brushes give noisy single-pixel picks.
- **S** Tell the user when nothing was picked instead of silently doing nothing.

### Pen
- **M** A pressure curve per pen type. Pressure maps straight to width today; a curve editor (reusing the easing curve editor) suits light or heavy hands.
- **S** A minimum width so light pressure doesn't vanish.
- **S** Show the stabiliser on the canvas as a "lag string" while drawing.
- **M** Width profiles are only uniform or tapered (`WidthProfile` in `LineStyle.kt`): add a custom profile drawn as a curve.

### Eraser
Whole-object, cut-the-stroke, lasso and raster erasing already exist.
- **S** Show the eraser size as an outline while hovering or pressing.
- **S** When cutting strokes, optionally taper the cut ends instead of leaving them blunt.
- **S** Option to erase on the current layer only.
- **S** Lasso erase: remove only what's fully inside the loop, or anything it touches.

### Shape
- **S** Keep a finished shape editable with handles (corner radius, side count, arrowhead size) until the next one is started.
- **S** Live dimensions and angle while drawing, in the document's real-world scale.
- **S** Switch between drawing from the centre and from a corner without a keyboard, e.g. with a second finger held down.

### Fill
- **M** Gap-closing fill only works within the visible view rectangle, so areas running off-screen fail. Fall back to the enclosing shapes' bounds.
- **S** Preview the region before committing (a translucent flash), showing where gaps were closed.
- **S** A clear "all layers" versus "this layer only" setting.
- **S** A tolerance slider for raster flood fills.

### Select
- **S** On-canvas buttons to add to, subtract from or intersect with the selection.
- **S** Show size, position and rotation in the readout, and allow typing values.
- **M** Nudge by a fixed step with arrow buttons, for precise placement on a phone.

### Text
- **S** Edit text in place on the canvas, not only through the dialog.
- **S** Baseline and bounding-box guides while moving text.

### Bezier
- **S** Tap a node to switch it between sharp corner and smooth curve, with visible handles.
- **S** Close the path by tapping the first node, with a magnetic snap.
- **M** Add, delete or split nodes on an existing path.

### Ruler and Measure
- **M** Merge the two overlapping tools: a measurement can be pinned as a dimension line in real-world units.
- **S** Angle measurement (three points).

### Gestures and input (all tools)
- **M** The gesture loop in `DrawingCanvas.kt` handles every tool in one place. Add tests for fingers landing and lifting at different times, a pan turning into a pinch, and stylus plus palm, before changing it further.
- **S** Long-press delay and multi-finger tap tolerances in Settings, instead of a fixed 500 ms.

### Brush engine (raster)
A raster brush today is a mode, a tip, wetness and strength (`RasterBrush.kt`); stamp brushes add spacing, scatter, size and angle jitter and follow-direction.
- **M** Stroke taper settings per brush: taper length and size at start and end, linked to pressure or not.
- **S** Spacing, jitter and scatter shown live in the brush editor preview as the stroke is drawn, not only on a fixed sample.
- **S** A brush "scratchpad" in the editor to test the brush with pressure before applying.
- **S** Brush size and opacity remembered per brush, not globally, with an optional link between them.
- **S** Brush size steps in a fixed set (like 1, 2, 3, 5, 8, 13…) for quick jumps on the rail.

### Vector ink
- **M** Stroke smoothing modes: streamline (lag), pulled string, and average of the last N points, each with its own setting; today one stabiliser serves all.
- **S** End smoothing: straighten the last few points when the pen lifts, to avoid hooks at stroke ends.
- **S** Pressure smoothing separate from position smoothing, so width stays steady on a jittery stylus.
- **M** Live stroke trimming: an option that cuts a stroke's overshoot at the nearest crossing line (vector line art cleanup, as in Clip Studio's "vector eraser to intersection").

### Layers
Layers have visibility, lock, opacity, blend, reference, skin, raster, clipping, alpha lock and masks.
- **S** Layer thumbnails large enough to read, with a size setting.
- **S** Swipe on a layer row: hide, lock, alpha lock (as in Procreate).
- **S** Pinch layers together to merge, select several layers with a second finger.
- **S** Show which layer a stroke belongs to by long-pressing it (jump to layer).
- **S** Colour labels on layers, filterable.

### Colour
- **S** Recent colours history is short: keep a longer history with pinned favourites.
- **S** Show colour values (hex, RGB, HSB) editable in the picker, with copy and paste.
- **S** Colour picker: a disc mode, a square mode, sliders and a value-only (greyscale) check.
- **M** A "harmony lock": as the main colour changes, the palette's harmony colours follow.

### Canvas and view
- **S** Flip the view horizontally (a temporary mirror to check proportions) without changing the drawing. Not found in the code.
- **S** Quick zoom presets: fit, 100 %, fill, and a tap-to-reset rotation.
- **S** A greyscale view toggle for checking values.
- **M** A reference window: a floating image or a second view of the canvas, kept on screen while drawing elsewhere.

### Transform
- **S** Transform handles show snapping to 15° and to other objects, with a readout.
- **S** Uniform and free scaling as a visible toggle instead of a gesture.
- **S** Flip horizontal and vertical on the selection strip.
- **M** Interpolation choice for raster transforms (nearest for pixel art, bilinear, bicubic).

---

## 2. Tool development — new abilities for existing tools

### Select
- **M** Magic wand: select by same colour, width, pen type, layer, or connected (touching) strokes.
- **S** "Select similar" in the selection action strip.
- **M** Named selections that can be saved and reselected.
- **M** A smart lasso that snaps to stroke edges.

### Bezier and path editing
- **M** Curvature mode: tap points and smooth curves form through them automatically.
- **M** Offset path: inset or outset a shape by a distance, reusing the contour code in `ShapeBuilder.kt`.
- **S** Outline stroke: turn a pen stroke into an editable filled shape.
- **S** Simplify a path with a live slider showing the node count.
- **M** Join two open paths at their nearest ends; reverse a path's direction.

### Shape
- **S** New kinds: polygon (3–12 sides), star (points and inner radius), rounded rectangle, arc, pie, ring, spiral.
- **M** Repeat a shape along a line, a grid or a circle, staying linked to the original.
- **S** More comic bubble tails: several tails, curved tails, tails attached to the speaker.

### Fill
- **M** Pattern and texture fills, using stamp-brush tips as the tile.
- **S** More gradient types: radial, angular, and more than two colour stops (only solid, fade and two-colour exist).
- **M** Fill by reference layer: the line-art layer bounds the fill while it is painted onto the current layer.
- **S** Optionally grow fills slightly under the line to remove white halos.

### Text
- **M** Fit text inside a shape, sized automatically.
- **M** Extend text on a path: inside a shape, offset along the path, flipped to the other side.
- **S** Text style presets (font, size, colour, OpenType features).
- **M** Text on a curved, distorted baseline for comic sound effects.

### Symmetry
- **M** Mirrored radial (kaleidoscope); only plain radial exists.
- **S** Move and rotate the axis directly on the canvas.
- **M** Symmetry limited to a selected frame or region.

### Eyedropper
- **S** Pick the stroke's whole style (width, pen type), not only its colour, reusing the style picker.
- **S** A history of the last 8 picked colours in the colour strip.

### Connect and Bone
- **M** Drag an IK target handle on the canvas to pose a whole limb.
- **M** Show joint rotation limits as arcs while dragging.
- **M** Mirror a pose from left to right.

### Measure
- **S** Area and perimeter of a closed shape.
- **S** Copy a measurement into a text label.

### Brush engine (raster)
- **M** Dual brush: a second tip that masks the first, for broken, dry-media edges.
- **M** Grain: a paper texture applied inside the stroke, either moving with the stroke or fixed to the canvas, with scale, depth and contrast.
- **M** Colour dynamics: hue, saturation and brightness jitter per stamp and per stroke, linked to pressure or tilt.
- **M** Shape dynamics: tip rotation from stroke direction, tilt or random; roundness from tilt.
- **M** Tilt shading: the stylus tilted flat paints wide and light, upright paints narrow and dark (pencil shading).
- **M** Wet edges, build-up and flow (opacity per stamp versus per stroke), as in Photoshop.
- **M** Colour mixing between strokes (paint picks up and drags what's beneath), extending the wet engine.
- **L** A full brush studio: every setting above in one editor with a live pad, grouped in sections, saved as presets that can be shared as a file.
- **M** Brush import from `.abr` tip images and Procreate `.brushset` shapes and grains.

### Vector ink
- **M** Vector brush strokes: a drawn or imported shape stretched along the path (art brush), or repeated along it (pattern brush), and corners handled for pattern brushes.
- **M** Variable-width editing on any finished stroke (see the width tool below).
- **M** Line weight correction: thicken outer contours and thin interior lines automatically on a selection.
- **M** Pressure-to-opacity as well as pressure-to-width for vector ink.

### Layers
- **M** Layer groups that fold, with group opacity and blend, and clipping to a group.
- **M** Adjustment layers: hue/saturation, curves, levels, gradient map, colour balance, applied non-destructively to the layers beneath.
- **S** Gradient map as an adjustment (not found in the code).
- **M** Layer effects: stroke (outline), drop shadow, glow.
- **S** "Select layer contents" as a selection.

### Colour
- **M** Colour from the screen: pick from anywhere on screen including the reference window and photos.
- **M** Palette from a photo (see the creation table), and palette sorting by hue or value.
- **M** Colour assist: show the complementary and the value of the current colour against its neighbours.

### Selection
- **M** Feathered selections (soft edges) for raster editing.
- **M** Selection painting: paint a selection with a brush (quick mask).
- **S** Grow and shrink a selection by a distance.
- **S** Invert selection; select all on layer exists, but invert does not appear in the selection strip.

### Guides
- **M** Perspective: 1, 2 and 3-point grids with draggable vanishing points shown on the canvas, and pen snapping to them.
- **M** Concentric and parallel line guides (a ruler that makes every stroke parallel, or radiating from a point).
- **M** Ellipse guide: draw perfect ellipses at any angle by following a guide.
- **S** Guides saved with the document and toggled per frame.

### Text
- **M** Warp text (arc, wave, flag) for titles and sound effects (not found in the code).
- **S** Text outline (stroke) and shadow.

---

## 3. Tool creation — new tools

| # | Tool | What it does | Builds on | Effort |
|---|---|---|---|---|
| 1 | Width tool | Drag on a stroke to thicken or thin it at that point, blending smoothly along the stroke. | Per-point pressure in strokes | M |
| 2 | Blend tool | Pick two shapes and generate N in-betweens, in shape and colour. | `Tweening.kt` | M |
| 3 | Clone / heal brush | Raster: pick a source, paint a copy of it elsewhere; heal blends it into its surroundings. | Raster brush engines | M–L |
| 4 | Lasso fill | Draw a loop and it fills straight away with the current colour. | Lasso erase path | S |
| 5 | Vector smudge / push | Push stroke points with a soft circular brush, like liquify for vector strokes. | Stroke spatial index, warp maths | M |
| 6 | Palette from image | Pull 5–8 representative colours from an image or a selection into the saved colours. | Image decoding, swatch import | S |
| 7 | Hatching | Fill a region with parallel or cross-hatched lines: angle, spacing, wobble. | Gap-fill region outline | M |
| 8 | Perspective pen | Pen strokes snap to the active vanishing points while drawing. | `PerspectiveGuide.kt` | M |
| 9 | Vector scatter | Scatter copies of a symbol or shape along the path, with size and rotation jitter. | Symbol library, stamp jitter | M |
| 10 | Knife | Cut through shapes along a drawn line, as a tool on the rail. | `ShapeBuilder.kt` knife | S |
| 11 | Annotate / redline | Arrows, callouts and comments on their own layer, hidden on export. | Pins, text | S–M |
| 12 | Colour replace brush | Paint over strokes to change their colour without moving them. | Stroke hit-testing in `eraseAt` | S |
| 13 | Gradient tool | Drag on the canvas to lay a gradient (linear, radial, angular) on a raster layer or a selection, with on-canvas stops. | Gradient fill | M |
| 14 | Mirror view | Flip the view horizontally to check proportions, without changing the drawing. | View rotation | S |
| 15 | Line cleanup | Select rough strokes and replace them with one clean averaged stroke. | Stroke fitting, simplify | M |
| 16 | Close-gap pen | Strokes that end near another line join it automatically. | Gap fill tolerance | S–M |
| 17 | Smart fill for colouring | Fill every closed area of the line art at once with flat colours, then recolour by tapping. | Gap-closing fill | M |
| 18 | Ink trap / sharpen corners | Tighten rounded corners on a selection of strokes. | Path editing | M |
| 19 | Pattern stamp | Paint with a repeating pattern or texture on a raster layer. | Stamp brush, raster engine | M |
| 20 | Blur / smudge vector | Soften a region of vector ink by rasterising it under the brush, with a confirm. | Raster blur | M |

---

# Part B — Animation, rigging, 3D and game logic

What exists (ROADMAP.md phases 9, 12 and 14): flipbook frames with onion skin, holds and an X-sheet; in-betweens with easing and shape tweening; motion along a path; bones with weighted deformation, IK (CCD), joint limits, dynamic bones, skins and pose blending; dopesheet and curve editor; lip-sync; animation branches; Lottie, Spine, DragonBones, GIF, APNG, MP4 and WebM export. In 3D: built-in shapes and OBJ models painted in colour, roughness and metallic, a posable hand and head, GLB and USDZ export. Game logic: objects, rules, state machines, 2D physics, input mapping, play mode and scripting.

## 4. Animation

### Improvement
- **S** Onion skin: separate colours and opacity for past and future frames, and an option to show only keyframes rather than in-betweens.
- **S** Scrubbing: drag along the animation bar with audio scrubbing (hear the soundtrack while dragging), for lip-sync timing.
- **S** Playback: a play range (in and out markers) instead of always the whole animation.
- **M** In-between preview: show the generated in-betweens as ghost outlines before committing them.
- **S** Shape tweening pairs each stroke with its nearest counterpart: show the pairing with numbered markers and let the user re-pair two strokes when it guesses wrong.
- **S** Frame thumbnails in the frames panel scale with the zoom of the panel, so long animations stay readable.

### Development
- **M** Per-layer timing: a layer can hold a drawing for its own number of frames (a background held while a character moves), not only whole-frame holds.
- **M** Animate layer properties: opacity, position, scale, rotation and colour as keys with easing, not only drawings and poses.
- **M** A camera: a framed view that pans, zooms and rotates across the canvas over time, used by playback and export.
- **S** Motion paths: show the path every selected object or bone tip takes across frames, editable by dragging its points.
- **M** Squash and stretch as a one-step deformation with volume preserved.
- **S** Time remapping of a range: slow down, speed up or reverse frames without redrawing.
- **S** Loop helpers: make the last frame lead back into the first (an in-between generated from last to first).

### Creation
| Tool | What it does | Builds on | Effort |
|---|---|---|---|
| Motion blur and smear frames | Stretch fast-moving ink along its motion between frames, or a smear-frame brush | Motion between frames, raster blur | M |
| Timing chart | Draw the classic spacing chart on a key and generate in-betweens to match it | Easing, in-betweens | M |
| Light table | Pin any frames from anywhere as tracing references, beyond the onion skin's neighbours | Onion skin | S |
| Cycle generator | Pick a walk or run cycle template for a rig and adjust stride and bounce | Bones, pose blending | L |
| Audio waveform track | Show the soundtrack's waveform under the frames, with markers | Audio sync | S–M |

## 5. Rigging

### Where the rig stands today
Read from `Skeleton.kt`, `Stroke.kt`, `SpineExport.kt` and `SpineImport.kt`:
- A bone is a head point and a tail point, with optional joint limits and a "dynamic" flag. There is no scale, shear, length animation or inheritance setting.
- Poses are a full copy of every bone per animation frame. There are no per-property keys (timelines), so a bone can't have its rotation keyed on frames 1 and 10 and its position on 5.
- A stroke belongs to one bone (`boneId`), with optional painted weights per point. There are no slots, no attachments, no draw order to animate, and no image swapping.
- Inverse kinematics is one CCD solver on up to four bones. There are no transform, path or physics constraints.
- Spine export bakes each bone's ink into one image, so smooth bending becomes rigid in Spine. Import keeps only rotation and translation of the first animation; scale, shear, constraints, attachments, skins and events are dropped.
- The dopesheet shows keys per bone and frame; the curve editor shows only the angle.

This is a strong start, but a long way from what Spine, DragonBones or Live2D-style rigging offer. The plan below builds that up in layers.

### 5.1 Data model (foundation — do first)
- **L** Bone transform as local values: rotation, x, y, scale x/y, shear x/y relative to the parent, with an "inherit" setting (rotation, scale, reflection) per bone. Everything below depends on this.
- **L** Animation as timelines: per bone, separate keyed tracks for rotate, translate, scale and shear, each key with its own curve (linear, stepped, Bézier). The per-frame full-pose copy becomes the baked result.
- **M** Setup pose versus animated pose, explicitly: edit the setup pose in a "Setup" mode; animations store only differences from it.
- **M** Multiple named animations per rig (idle, walk, jump), each with its own length and timelines, instead of the single frame sequence.
- **S** Bone colour, name and an optional icon; bone hierarchy shown as a tree with folders.

### 5.2 Slots, attachments and draw order
- **L** Slots: a slot hangs from a bone and holds one visible attachment at a time; ink and images go into slots instead of being bound to bones directly.
- **M** Attachment swapping over time: a hand slot switches between open, fist and point drawings on keys (the "attachment" timeline).
- **M** Draw order timeline: change which slot is in front over time (an arm moving behind the body).
- **M** Slot colour and alpha timelines, including a second "dark" tint colour, for flashes, fades and tinting.
- **M** Region attachments (an image or a group of strokes) and mesh attachments (below).
- **M** Point attachments (a named point on a bone, for spawning particles or attaching a weapon) and bounding-box attachments (polygons for hit detection, usable by the game logic).
- **M** Clipping attachments: a polygon that clips the slots after it, for masking inside a rig.
- **S** Skins on attachments, not only on layers: a skin replaces some attachments and leaves the rest; mix several skins (hat + shirt + shoes).

### 5.3 Meshes and weights
- **L** Mesh attachments: a drawing with a hull and inner vertices, triangulated, so it bends smoothly instead of moving rigidly.
- **M** Mesh editing tools: add, delete and move vertices; draw edges; automatic triangulation from the outline; "trace" a hull from the drawing's alpha.
- **M** Weights per mesh vertex across several bones, with a weights panel showing each bone's influence and a "lock" per bone.
- **M** Weight tools: paint add/subtract/replace/smooth, automatic weights by bone distance or by heat (inside the shape), normalise, prune small weights, mirror weights left to right.
- **M** Deform keys: free-form vertex offsets keyed over time on top of the bone deformation (Spine's deform timeline); today's mesh warp per frame can become this.
- **M** Linked meshes: several attachments share one mesh and its weights and deform keys (outfits on the same body).
- **M** Export mesh attachments and weights to Spine and DragonBones so bending survives export.

### 5.4 Constraints
- **M** IK constraint as an object: a target bone, a chain of one or two bones, mix, softness, and bend direction; exact two-bone solve; IK/FK switch and blend per animation.
- **M** Transform constraint: copy rotation, position, scale or shear from one bone to others with offsets and mix (a gear driving another, eyes following a target).
- **M** Path constraint: bones follow a drawn path with position, spacing (length, fixed, percent) and rotation modes (tails, ropes, snakes, text on a path).
- **M** Physics constraint: per bone, inertia, strength, damping, mass, wind and gravity, simulated in playback and export (hair, cloth, ears). The current "dynamic bone" flag becomes this, with settings.
- **S** Constraint order: a list to choose which constraint applies first.
- **S** Constraint mix keys: animate how strongly each constraint applies over time.

### 5.5 Animation tools for rigs
- **M** Dopesheet with one row per timeline (bone rotate, translate, scale, shear; slot attachment, colour; draw order; events), collapsible per bone.
- **M** Graph editor for every curve, not only angle: per-channel curves (x and y separately), tangent handles, stepped keys, copy and paste curves, presets.
- **S** Auto-key mode: changing a bone records a key on the current frame; a toggle to key all channels or only changed ones.
- **S** Key filters: show only selected bones, only rotation, etc.
- **S** Ghosting of the rig (previous and next poses drawn faintly), as onion skin for bones.
- **M** Events: named markers on the timeline with int, float, string and audio payloads, used by export and by the game logic (a footstep sound).
- **M** Animation mixing and tracks: preview two animations crossfading, or a second track adding an aim or blink on top of a walk.
- **S** Playback speed, loop, and a preview of the mix duration between two animations.
- **M** Offset keys in time across several bones (a tail wave) with one drag.

### 5.6 Rigging workflow
- **M** Mirror a rig: duplicate bones and attachments from left to right, matching `_L`/`_R` names; mirror poses and animations.
- **M** Pose library: save named poses with thumbnails and apply them, whole or to selected bones.
- **M** Retarget an animation between rigs with a bone map; copy an animation between projects.
- **M** Auto-rig: place a biped, quadruped or face template by dragging markers (head, hands, feet), then fit it.
- **M** Rig controls: a slider or on-canvas handle driving several bones (fist, blink, mouth shape), keyed as one value.
- **S** Rig checker: unbound ink, empty slots, bones without influence, weights not summing to one, joints past their limits.
- **S** Bone selection by tapping ink: tapping a drawing selects its bone or slot.
- **M** Rig from layers: each layer of an imported PSD becomes a slot with an attachment on a new bone, placed where it was (the standard Photoshop-to-Spine workflow). PSD import exists.

### 5.7 Import, export and runtime preview
- **M** Spine export with slots, attachments, meshes, weights, skins, constraints, events, draw order and colour timelines, and a packed atlas instead of one image per bone.
- **M** Spine import of the same, not only bones with rotation and translation.
- **M** DragonBones export and import at the same level.
- **M** A "runtime preview" panel playing the exported data back as the game engine would, to check before handing it over.
- **S** Export settings: scale, nonessential data, pretty-printed JSON, per-animation selection.
- **M** Spine binary (`.skel`) export for smaller files.

### 5.8 Face and 2.5D rigging (Live2D-style)
- **L** Parameter-driven deformers: a parameter (head angle X, mouth open) blends between keyed mesh shapes, giving a turn-the-head look from flat art.
- **M** Warp and rotation deformers nested on a face.
- **M** Blink, breath and lip-sync parameters connected to the existing mouth chart and audio.

### Creation
| Tool | What it does | Builds on | Effort |
|---|---|---|---|
| Slot and attachment panel | Tree of bones, slots and attachments, with visibility and draw order | 5.2 | M |
| Mesh tool | Create and edit mesh attachments: hull, vertices, edges, triangulation | Mesh warp, `MeshBuilder.kt` | M–L |
| Weights tool | Paint, smooth, normalise and auto-compute weights across bones | Point weights | M |
| Constraint tool | Add IK, transform, path and physics constraints by picking bones on the canvas | 5.4 | M |
| Auto-rig | Template skeleton fitted by markers | Bones, mannequin | M–L |
| Pose library | Saved poses with thumbnails | Pose copies | M |
| Rig controls | Sliders driving several bones | Pose blending | M |
| Rig checker | Lists rig problems | Skeleton data | S |
| Runtime preview | Plays exported Spine data back | Spine export | M |
| Parameter deformer | Live2D-style face turns from flat art | Mesh, deform keys | L |

## 6. 3D

### Improvement
- **S** Orbit camera: double-tap to frame the model, a reset view, and snap to front, side and top views.
- **S** Painting: show the brush as a circle on the model's surface, sized as it will paint.
- **S** Painting across UV seams leaves visible lines: paint a few texels past each seam.
- **S** Lighting: preview presets (studio, rim, flat) besides the direction, height, strength and ambient sliders.
- **S** Placed models are a still image (a poster) on the canvas: show which view it is and allow re-rendering at a new angle without opening the editor.

### Development
- **M** Use a model as a drawing reference: a placed 3D model whose camera matches the canvas's perspective guide, turned and lit on the canvas.
- **M** Pose the 3D mannequin and hand per animation frame, rendered into each frame as a reference layer.
- **M** More channels: normal and height painting, and emission.
- **M** Toon shading and outline rendering, so a model renders like ink and flat colour.
- **S** Turntable export: a GIF or MP4 of the model spinning.
- **M** Layers for 3D painting, with opacity and blend modes like the 2D layers.

### Creation
| Tool | What it does | Builds on | Effort |
|---|---|---|---|
| Extrude and lathe | Turn a drawn closed shape into a 3D object by extruding it, or a profile into one by spinning it | `MeshBuilder.kt` | M |
| 3D scene blocks | Place several primitives together (boxes, cylinders) to block out a room or a prop as a perspective reference | Built-in shapes, renderer | M–L |
| Full-body mannequin | A posable 3D figure, extending the hand and head | `Hand3d.kt`, `Head3d.kt` | L |
| Projection painting | Paint on the screen and project it onto the model, for decals and labels | Picking, paint dabs | M |

## 7. Game logic

### Improvement
- **S** Play mode: show colliders, hitboxes and object names as a debug overlay while playing.
- **S** Rules: show which rule last fired on each object, to explain behaviour.
- **S** Physics: a step-by-step mode (advance one frame at a time while paused).

### Development
- **M** Tilemaps: paint a level from tiles cut from a drawing, with collision per tile.
- **M** Prefabs: reuse an object with its rules and properties, with changes to the original reaching every copy.
- **M** Sound actions: play a sound or an audio note from a rule.
- **M** Scenes: several levels with transitions between them.

### Creation
| Tool | What it does | Builds on | Effort |
|---|---|---|---|
| Collider tool | Draw boxes, circles and polygons as colliders directly on an object | Hitbox from a drawn line | S |
| Path follower editor | Edit an object's path with speed per segment and pauses | Follow-path behaviour | M |
| Particle emitter | An object that emits drawn particles with speed, spread and lifetime | Objects, physics | M |

---

## Suggested order

1. Eyedropper fix, fill preview, shape live dimensions — small fixes to tools used constantly.
2. Pressure curve, lasso fill, colour replace brush, palette from image — small to medium and self-contained.
3. Width tool, blend tool, magic wand, new shape kinds.
4. Curvature and offset paths, clone/heal, hatching.

Part B:
1. Play range, audio scrubbing, IK two-bone solver, on-canvas joint limits, 3D framing and views — small, everyday fixes.
2. Rig data model (5.1): local bone transforms with scale and shear, timelines with keys, setup pose, several animations. Most of section 5 depends on it, so it comes before slots, meshes and constraints.
3. Slots, attachments and draw order (5.2), then meshes and weights (5.3), then constraints (5.4), then Spine export and import at that level (5.7).
4. Per-layer timing, property keys, camera, pose library, rig mirroring.
5. 3D model as a drawing reference, extrude and lathe, auto-rig, Live2D-style deformers.
6. Retargeting, full-body mannequin, tilemaps and scenes.

Before category 3, split the per-tool handling out of `DrawingCanvas.kt` (about 2,200 lines) and `CanvasState.kt` (about 6,300 lines), per CLAUDE.md §4.5: every new tool currently touches the same few large `when` blocks.
