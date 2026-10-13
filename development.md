# Fern tool development plan

Ideas from an audit of the drawing tools, in three groups: improving existing tools, developing them further, and creating new ones. Effort is an estimate from reading the code, not from prototyping: **S** small, **M** medium, **L** large. Items already in [ROADMAP.md](ROADMAP.md) as done are not repeated here.

The tools today: `Tool` in `CanvasState.kt` (pen, eraser, select, shape, fill, text, ruler, eyedropper, connect, bone, bezier, measure) and `PenType` in `Stroke.kt` (marker, pencil, highlighter, calligraphy, airbrush, crayon, pixel, stamp).

---

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

## Suggested order

1. Eyedropper fix, fill preview, shape live dimensions — small fixes to tools used constantly.
2. Pressure curve, lasso fill, colour replace brush, palette from image — small to medium and self-contained.
3. Width tool, blend tool, magic wand, new shape kinds.
4. Curvature and offset paths, clone/heal, hatching.

Before category 3, split the per-tool handling out of `DrawingCanvas.kt` (about 2,200 lines) and `CanvasState.kt` (about 6,300 lines), per CLAUDE.md §4.5: every new tool currently touches the same few large `when` blocks.
