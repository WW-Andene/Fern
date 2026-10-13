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

### Improvement
- **S** Bone display: show bone names and let the user rename them; colour bones by chain or side (left and right).
- **S** Binding: show which bone each stroke is bound to and let it be changed by tapping the stroke, not only by painting weights.
- **M** IK uses CCD on up to four bones, which can produce bent, unnatural joints on two-bone limbs. Add an exact two-bone solver with a pole direction (which way the knee or elbow points).
- **S** Joint limits are set in a dialog: show them as arcs on the canvas and drag their ends.
- **S** Dynamic bones: settings for stiffness and damping per bone, with a preview while scrubbing.

### Development
- **M** Bones can't change length or scale over time (a bone is only head and tail). Animate bone scale and length for stretchy limbs and squash.
- **M** Mirror a rig and a pose from left to right, with name-based matching (a `_L` bone maps to its `_R` counterpart).
- **M** Automatic weights from the shape of the ink (distance along the inside of the shape, not straight-line distance), so an arm doesn't pull on the torso next to it.
- **M** Path constraint: a chain of bones follows a drawn curve (a tail or a rope).
- **M** Pose library: save poses by name and apply them to any frame, with thumbnails.
- **M** Retargeting: apply an animation from one rig to another with a matching bone map.

### Creation
| Tool | What it does | Builds on | Effort |
|---|---|---|---|
| Auto-rig | Place a ready biped or quadruped skeleton over a drawing by dragging a few markers (head, hands, feet) | Bones, mannequin | M–L |
| Mesh pin tool | Pin points of a drawing and drag others, a puppet warp that animates per frame | Puppet warp, mesh warp | M |
| Controls | On-canvas sliders or handles that drive several bones at once (a "fist" slider closing every finger) | Pose blending | M |
| Rig checker | Lists unbound strokes, bones with no ink, and joints that pass their limits | Skeleton data | S |

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
2. Per-layer timing, property keys, camera, pose library, rig mirroring.
3. 3D model as a drawing reference, extrude and lathe, auto-rig.
4. Retargeting, full-body mannequin, tilemaps and scenes.

Before category 3, split the per-tool handling out of `DrawingCanvas.kt` (about 2,200 lines) and `CanvasState.kt` (about 6,300 lines), per CLAUDE.md §4.5: every new tool currently touches the same few large `when` blocks.
