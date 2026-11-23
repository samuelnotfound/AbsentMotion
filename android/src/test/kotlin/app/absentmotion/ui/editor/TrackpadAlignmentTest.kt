package app.absentmotion.ui.editor

import androidx.compose.ui.geometry.Offset
import app.absentmotion.ui.editor.panels.TrackpadAlignment
import app.absentmotion.ui.editor.panels.snapAxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadAlignmentTest {
    @Test fun straightDragsStayOnAxisUntilTheUserTurns() {
        val alignment = TrackpadAlignment()
        assertEquals(Offset(18f, 0f), alignment.constrain(Offset(18f, 2f)))
        assertEquals(Offset(45f, 0f), alignment.constrain(Offset(45f, 8f)))
        assertEquals(Offset(47f, 16f), alignment.constrain(Offset(47f, 16f)))
        assertEquals(Offset(70f, 10f), alignment.constrain(Offset(70f, 10f)))
    }

    @Test fun canvasSnapMatchesTheLayerEdgeInsteadOfThePivot() {
        val (x, y) = snapAxes(400f, 300f, floatArrayOf(110f, 80f), floatArrayOf(70f, 50f, 150f, 110f),
            emptyList(), 1f, 1f, canvasEdges = true)
        assertEquals(-70f, x.map(-74f), 0.01f)
        assertEquals(-50f, y.map(-55f), 0.01f)
        assertTrue(x.guidesAt(-70f).any { it.vertical && it.at == 0f })
    }
}
