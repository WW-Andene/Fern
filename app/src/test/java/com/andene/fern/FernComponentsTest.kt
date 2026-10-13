package com.andene.fern

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Roadmap item 336: UI code uses the Fern controls, whose sizes are on the CLAUDE.md §7 scale,
 * never the Material controls they replace, whose sizes are off it.
 */
class FernComponentsTest {
    @Test
    fun noMaterialControlsOutsideFernComponents() {
        val replaced = listOf(
            "TextButton", "Button", "OutlinedButton", "Slider", "RangeSlider", "Switch",
            "TextField", "OutlinedTextField", "DropdownMenuItem", "ListItem", "AlertDialog",
        )
        // A call, bare or package-qualified, not a Fern* one or a member like `Foo.Slider(`.
        val call = Regex("""(?<![\w.])(androidx\.compose\.material3\.)?(${replaced.joinToString("|")})\(""")
        val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "FernComponents.kt" }
        val offenders = sources.flatMap { file ->
            file.readLines().withIndex().filter { call.containsMatchIn(it.value) }.map { "${file.name}:${it.index + 1}" }
        }.toList()
        assertTrue("Material controls used directly; use the Fern ones in FernComponents.kt: $offenders", offenders.isEmpty())
    }
}
