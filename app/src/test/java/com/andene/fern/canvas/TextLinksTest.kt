package com.andene.fern.canvas

import com.andene.fern.FernScale
import com.andene.fern.FernPanelHost
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextLinksTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pin = PinMeta("p1", "Kitchen", 10.0, 20.0, 2.0)
    private val document = DocumentMeta("d2", "Floor plan", 0L)

    @Test
    fun linksPersist() {
        val doc = CanvasStorage.createDocument(context, "Links")
        val items = listOf(
            TextItem("a", "To pin", WorldPoint.Zero, 12.0, Color.Black, link = LinkTarget.Pin("p1")),
            TextItem("b", "To doc", WorldPoint.Zero, 12.0, Color.Black, link = LinkTarget.Document("d2")),
            TextItem("c", "Plain", WorldPoint.Zero, 12.0, Color.Black),
        )
        CanvasStorage.saveTextItems(context, doc.id, items)
        assertEquals(items, CanvasStorage.loadTextItems(context, doc.id))
    }

    @Test
    fun chooseAPinThenFollowSavesAndNavigates() {
        val events = mutableListOf<String>()
        var confirmed: TextEditResult? = null
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                TextEditDialog(
                    pending = CanvasState.PendingTextEdit(null, WorldPoint.Zero, "Go to kitchen"),
                    onConfirm = { confirmed = it; events += "confirm" },
                    onDismiss = {},
                    linkChoices = LinkChoices(listOf(pin), listOf(document)),
                    onFollowLink = { events += "follow $it" },
                )
                // The editor is a panel (design roadmap D42), shown through its host.
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithText("Pin").performScrollTo().performClick()
        compose.onNodeWithText("Kitchen").performClick()
        compose.onNodeWithText("Links to: Kitchen").performScrollTo()
        compose.onNodeWithText("Follow link").performClick()
        assertEquals(LinkTarget.Pin("p1"), confirmed?.link)
        assertEquals(listOf("confirm", "follow ${LinkTarget.Pin("p1")}"), events) // saved before navigating
    }

    @Test
    fun aDeletedTargetIsReported() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                TextEditDialog(
                    pending = CanvasState.PendingTextEdit("x", WorldPoint.Zero, "Old", initialLink = LinkTarget.Document("gone")),
                    onConfirm = {},
                    onDismiss = {},
                    linkChoices = LinkChoices(listOf(pin), listOf(document)),
                )
                // The editor is a panel (design roadmap D42), shown through its host.
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithText("Links to something that no longer exists").performScrollTo()
    }

    @Test
    fun editingKeepsAndClearsTheLink() {
        val state = CanvasState()
        state.loadTextItems(listOf(TextItem("t", "Hi", WorldPoint.Zero, 12.0, Color.Black, link = LinkTarget.Pin("p1"))))
        state.beginTextEdit(WorldPoint(1.0, 1.0))
        assertEquals(LinkTarget.Pin("p1"), state.pendingTextEdit?.initialLink)
        state.confirmTextEdit("Hi", link = null)
        assertTrue(state.textItems.single().link == null)
    }
}
