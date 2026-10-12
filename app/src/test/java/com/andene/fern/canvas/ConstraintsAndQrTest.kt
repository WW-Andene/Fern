package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Roadmap item 144: constraints followed when a frame is resized, and the QR code tool. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConstraintsAndQrTest {
    private fun box(left: Double, top: Double, right: Double, bottom: Double) = Stroke(Color.Black, 2.0).apply {
        for (p in listOf(WorldPoint(left, top), WorldPoint(right, top), WorldPoint(right, bottom), WorldPoint(left, bottom))) addPoint(p)
    }

    @Test
    fun eachPinFollowsTheFrame() {
        // Frame 0..100 grows to 0..200; an object at 10..30.
        fun at(pin: ConstraintPin, v: Double) = constrainedCoordinate(v, 10.0, 30.0, 0.0, 100.0, 0.0, 200.0, pin)
        assertEquals(10.0, at(ConstraintPin.START, 10.0), 1e-9)
        assertEquals(110.0, at(ConstraintPin.END, 10.0), 1e-9)
        assertEquals(60.0, at(ConstraintPin.CENTER, 10.0), 1e-9)
        assertEquals(60.0, at(ConstraintPin.SCALE, 30.0), 1e-9)
        assertEquals(10.0, at(ConstraintPin.BOTH, 10.0), 1e-9)
        assertEquals(130.0, at(ConstraintPin.BOTH, 30.0), 1e-9)
    }

    @Test
    fun constraintsPackAndSurviveTheStrokeFile() {
        val all = ConstraintPin.entries.flatMap { h -> ConstraintPin.entries.map { v -> LayoutConstraints(h, v) } }
        for (c in all) assertEquals(c, LayoutConstraints.unpack(c.pack()))
        val stroke = box(0.0, 0.0, 10.0, 10.0).apply { setConstraints(LayoutConstraints(ConstraintPin.END, ConstraintPin.SCALE)) }
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), listOf(stroke, box(0.0, 0.0, 1.0, 1.0))) }.toByteArray()
        val read = StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(LayoutConstraints(ConstraintPin.END, ConstraintPin.SCALE), read[0].constraints)
        assertNull(read[1].constraints)
        // Copies and erased pieces keep them.
        assertEquals(stroke.constraints, stroke.deepCopy().constraints)
    }

    @Test
    fun resizingAFrameMovesConstrainedStrokesUndoably() {
        val state = CanvasState()
        val pinnedRight = box(80.0, 10.0, 90.0, 20.0)
        val stretched = box(10.0, 40.0, 90.0, 50.0)
        val free = box(10.0, 70.0, 20.0, 80.0)
        val outside = box(500.0, 500.0, 510.0, 510.0)
        state.loadStrokes(listOf(pinnedRight, stretched, free, outside))
        state.loadFrames(listOf(Frame("f", "F", WorldPoint(0.0, 0.0), WorldPoint(100.0, 100.0))))
        state.selection.add(pinnedRight)
        state.setSelectionConstraints(LayoutConstraints(ConstraintPin.END, ConstraintPin.START))
        state.selection.clear()
        state.selection.add(stretched)
        state.setSelectionConstraints(LayoutConstraints(ConstraintPin.BOTH, ConstraintPin.START))
        state.selection.clear()
        state.resizeFrame("f", 200.0, 100.0)
        assertEquals(WorldPoint(200.0, 100.0), state.frames.single().max)
        assertEquals(180.0, pinnedRight.minX, 1e-9)
        assertEquals(10.0, stretched.minX, 1e-9)
        assertEquals(190.0, stretched.maxX, 1e-9)
        assertEquals(10.0, free.minX, 1e-9)
        assertEquals(500.0, outside.minX, 1e-9)
        state.undo()
        assertEquals(80.0, state.strokes[0].minX, 1e-9)
    }

    @Test
    fun aQrCodeHasFinderPatternsAndIsPlacedAsOneGroup() {
        val qr = qrOutlines("https://example.com", QrErrorCorrection.MEDIUM)!!
        assertEquals(25, qr.size) // version 2 for this text at M
        // The top-left finder pattern: its 7 x 7 ring's dark corner module, and a light one inside it.
        assertTrue(qr.outlines.any { polygonContains(it, WorldPoint(0.5, 0.5)) })
        assertTrue(qr.outlines.none { polygonContains(it, WorldPoint(1.5, 1.5)) })
        assertTrue(qr.outlines.any { polygonContains(it, WorldPoint(3.5, 3.5)) }) // the finder's centre
        assertNull(qrOutlines("", QrErrorCorrection.LOW))
        assertNull(qrOutlines("x".repeat(8000), QrErrorCorrection.HIGH))

        val state = CanvasState()
        state.insertQrCode(qr, lightBackground = true)
        assertEquals(qr.outlines.size + 1, state.strokes.size)
        assertEquals(Color.White, state.strokes.first().color)
        assertEquals(1, state.strokes.map { it.groupId }.distinct().size)
        assertEquals(state.strokes.size, state.selection.size)
    }
}
