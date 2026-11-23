package app.absentmotion.editor

import app.absentmotion.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineIndependentTracksTest {
    private val x = locationTimelineTrack(0)
    private val y = locationTimelineTrack(1)
    private val z = locationTimelineTrack(2)
    private val scene = Scene.parse("""<scene width="200" height="200" fps="30" totalTime="999"><shape id="1" threeD="true" startTime="0" endTime="999" s=".rect"><transform><location value="10,20,30"/></transform></shape></scene>""")

    @Test fun aStaticAxisRemainsStaticWhenOtherAxesHaveKeys() {
        val animated = scene.update(1, 0) { x.toggleKey(it) }
        val moved = animated.update(1, 500) { z.change(it, 90f) }
        val layer = moved.layer(1)!!.at(500, moved.fps)
        assertEquals(listOf(10f, 20f, 90f), layer.location.toList())
        assertEquals(1, x.keys(layer).size)
        assertTrue(y.keys(layer).isEmpty())
        assertTrue(z.keys(layer).isEmpty())
    }

    @Test fun editingOneAxisDoesNotAddKeysToTheOtherAnimatedAxis() {
        val animated = scene.update(1, 0) { z.toggleKey(x.toggleKey(it)) }
        val moved = animated.update(1, 500) { z.change(it, 90f) }
        val layer = moved.layer(1)!!
        assertEquals(1, x.keys(layer).size)
        assertEquals(2, z.keys(layer).size)
        assertTrue(y.keys(layer).isEmpty())
    }

    @Test fun draggedKeysStopBeforeAnotherKeyOnTheirTrack() {
        val first = scene.update(1, 0) { x.toggleKey(it) }
        val withTwo = first.update(1, 500) { x.toggleKey(it) }
        val (moved, delta) = withTwo.withTimelineKeyDelta(setOf(TrackKey(1, x, 0)), 20)
        assertEquals(14, delta)
        assertEquals(listOf(14, 15), x.keyFrames(moved, moved.layer(1)!!))
    }
}
