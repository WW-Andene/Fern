package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class AdobeSwatchExchangeTest {
    /** Builds an .ase file the way Adobe apps write one. */
    private fun ase(vararg blocks: Pair<Int, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeBytes("ASEF"); writeShort(1); writeShort(0); writeInt(blocks.size)
            for ((type, body) in blocks) { writeShort(type); writeInt(body.size); write(body) }
        }
        return out.toByteArray()
    }

    private fun color(name: String, model: String, vararg values: Float): Pair<Int, ByteArray> {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeShort(name.length + 1); writeChars(name); writeShort(0)
            writeBytes(model); values.forEach { writeFloat(it) }; writeShort(2)
        }
        return 0x0001 to out.toByteArray()
    }

    private fun group(name: String): Pair<Int, ByteArray> {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply { writeShort(name.length + 1); writeChars(name); writeShort(0) }
        return 0xC001 to out.toByteArray()
    }

    private fun assertColor(expected: Color, actual: Color, tolerance: Float = 0.01f) {
        assertEquals(expected.red, actual.red, tolerance)
        assertEquals(expected.green, actual.green, tolerance)
        assertEquals(expected.blue, actual.blue, tolerance)
    }

    @Test
    fun readsEveryColourModelThroughGroups() {
        val bytes = ase(
            group("Brand"),
            color("Red", "RGB ", 1f, 0f, 0f),
            color("Cyan", "CMYK", 1f, 0f, 0f, 0f),
            0xC002 to ByteArray(0),
            color("Mid grey", "Gray", 0.5f),
            color("White", "LAB ", 1f, 0f, 0f),
        )
        val colors = parseAdobeSwatchExchange(bytes)
        assertEquals(4, colors.size)
        assertColor(Color(1f, 0f, 0f), colors[0])
        assertColor(Color(0f, 1f, 1f), colors[1])
        assertColor(Color(0.5f, 0.5f, 0.5f), colors[2])
        assertColor(Color.White, colors[3])
    }

    @Test
    fun labMidGreyIsNeutral() {
        // L*=50 with a=b=0 is sRGB ~ (119, 119, 119).
        assertColor(Color(119, 119, 119), labToColor(50.0, 0.0, 0.0), 0.01f)
    }

    @Test
    fun truncatedFileKeepsColoursReadSoFar() {
        val full = ase(color("Red", "RGB ", 1f, 0f, 0f), color("Green", "RGB ", 0f, 1f, 0f))
        val colors = parseAdobeSwatchExchange(full.copyOf(full.size - 6))
        assertEquals(1, colors.size)
    }

    @Test
    fun unknownModelIsSkipped() {
        assertEquals(1, parseAdobeSwatchExchange(ase(color("?", "XYZ ", 1f, 1f, 1f), color("Red", "RGB ", 1f, 0f, 0f))).size)
    }

    @Test
    fun signatureCheck() {
        assertTrue(isAdobeSwatchExchange("ASEF....".toByteArray()))
        assertFalse(isAdobeSwatchExchange("GIMP Palette".toByteArray()))
        assertTrue(parseAdobeSwatchExchange("GIMP Palette".toByteArray()).isEmpty())
    }
}
