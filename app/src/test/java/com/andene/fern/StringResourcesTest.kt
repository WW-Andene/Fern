package com.andene.fern

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The French translation (roadmap item 70) covers every English string, with the same format arguments. */
@RunWith(AndroidJUnit4::class)
class StringResourcesTest {
    private fun entries(dir: String): Map<String, String> {
        val text = File("src/main/res/$dir/strings.xml").readText()
        return Regex("""<(string|plurals) name="([^"]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).associate { it.groupValues[2] to it.groupValues[3] }
    }

    private fun formatArgs(value: String) = Regex("""%\d+\$[a-z.0-9]*[sdf]""").findAll(value).map { it.value.replace(Regex("""\.\d+"""), "") }.toSortedSet()

    @Test
    fun frenchHasEveryKeyWithTheSameArguments() {
        val en = entries("values")
        val fr = entries("values-fr")
        assertEquals(en.keys, fr.keys)
        for ((key, value) in en) assertEquals(key, formatArgs(value), formatArgs(fr.getValue(key)))
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchLocaleResolvesFrenchText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Annuler", context.getString(R.string.action_undo))
        assertEquals("Canevas de dessin, 3 traits. Un doigt dessine, deux doigts déplacent et zooment.", context.resources.getQuantityString(R.plurals.canvas_description, 3, 3))
    }
}
