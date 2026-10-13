package com.andene.fern

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Design roadmap D58: one icon set, Lucide's, read correctly from its SVG markup. */
@RunWith(RobolectricTestRunner::class)
class FernIconsTest {
    @Test
    fun shapesBecomePathData() {
        assertEquals("M2.0 3.0L4.0 5.0", svgPathData("line", mapOf("x1" to "2", "y1" to "3", "x2" to "4", "y2" to "5")))
        assertEquals("M1 1L2 2L3 1Z", svgPathData("polygon", mapOf("points" to "1 1 2 2 3,1")))
        assertEquals("M8.0 12.0A4.0 4.0 0 1 0 16.0 12.0A4.0 4.0 0 1 0 8.0 12.0Z", svgPathData("circle", mapOf("cx" to "12", "cy" to "12", "r" to "4")))
        assertEquals("M2.0 3.0H6.0V9.0H2.0Z", svgPathData("rect", mapOf("x" to "2", "y" to "3", "width" to "4", "height" to "6")))
        // A rounded rect: rx alone sets ry too.
        assertTrue(svgPathData("rect", mapOf("x" to "0", "y" to "0", "width" to "10", "height" to "10", "rx" to "2")).startsWith("M2.0 0.0H8.0A2.0 2.0 0 0 1 10.0 2.0"))
    }

    @Test
    fun everyIconBuilds() {
        val icons = FernIcons::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.returnType == ImageVector::class.java && it.parameterCount == 0 }
        assertTrue(icons.size > 100)
        for (getter in icons) {
            val icon = getter.invoke(FernIcons) as ImageVector
            assertTrue(getter.name, icon.root.size > 0)
        }
    }

    @Test
    fun noMaterialIconsInUse() {
        val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
        val offenders = sources.filter { "androidx.compose.material.icons" in it.readText() }.map { it.name }.toList()
        assertTrue("Material icons used; add the Lucide icon to FernIcons instead: $offenders", offenders.isEmpty())
    }
}
