package com.andene.fern.canvas

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D34: a saved macro on a key of its own, one key doing one thing. */
@RunWith(RobolectricTestRunner::class)
class MacroKeysTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aMacroTakesAKeyFromAShortcutAndGivesItBack() {
        val ctrlZ = KeyCombo(KeyEvent.KEYCODE_Z, ctrl = true)
        Macros.setSaved(context, listOf(Macro("tidy", listOf(QuickMenuAction.UNDO))))
        Macros.setKey(context, "tidy", ctrlZ)
        assertEquals("tidy", Macros.forKey(context, ctrlZ)?.name)
        assertEquals(UNBOUND, ShortcutSettings.state(context).value.getValue(ShortcutAction.UNDO))
        // Giving the key back to undo takes it from the macro. (ShortcutSettings caches its map in
        // a static, so this also leaves it as other tests expect.)
        ShortcutSettings.set(context, ShortcutAction.UNDO, ctrlZ)
        assertNull(Macros.forKey(context, ctrlZ))
        assertEquals(ctrlZ, ShortcutSettings.state(context).value.getValue(ShortcutAction.UNDO))
    }
}
