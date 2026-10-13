package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner

/** Design roadmap D23: a layout survives a file round trip; other files aren't layouts. */
@RunWith(RobolectricTestRunner::class)
class LayoutPresetsTest {
    @Test
    fun layoutsRoundTripThroughJson() {
        for (preset in BUILT_IN_LAYOUTS) assertEquals(preset.copy(label = null), LayoutPresets.read(LayoutPresets.write(preset)))
    }

    @Test
    fun otherJsonIsNotALayout() {
        assertNull(LayoutPresets.read(JSONObject().put("name", "x")))
    }
}
