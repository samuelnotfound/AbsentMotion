package app.absentmotion

import app.absentmotion.editor.EditorPanel
import app.absentmotion.editor.grouped
import app.absentmotion.editor.groupMemberTrack
import app.absentmotion.editor.withFocused
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.ui.editor.timelineGroupRows
import app.absentmotion.editor.InlineKey
import app.absentmotion.editor.InlineKeyMove
import app.absentmotion.editor.inlineNavigationStops
import app.absentmotion.editor.inlineEditorPanel
import app.absentmotion.editor.inlineTrackSections
import app.absentmotion.editor.navigationStops
import app.absentmotion.model.Easing
import app.absentmotion.model.KeyScope
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.Scene
import app.absentmotion.model.SpeedRef
import app.absentmotion.model.VideoTiming
import app.absentmotion.model.XmlNode
import app.absentmotion.model.keyframes
import app.absentmotion.model.withKeyframesMoved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale
import kotlin.math.roundToInt

class KeyScopeTest {
    private val blank = Scene.create("t", 100, 100, 30, 0)
    private val end = 1999
    private val radius = PropRef.effect(0, "radius")
    private val alpha = PropRef.effect(0, "alpha")
    private val tiles = PropRef.effect(1, "size")

    private fun kf(frame: Int, v: String = "1") =
        XmlNode("kf", mapOf("t" to String.format(Locale.US, "%.6f", blank.frameTime(frame).toFloat() / end), "v" to v))

    private fun property(name: String, vararg frames: Int) = XmlNode("property", mapOf("name" to name, "type" to "float"), frames.map { kf(it) })

    private fun layer(extra: List<XmlNode> = emptyList()) = shapeLayer(end = end, children = listOf(
        XmlNode("transform", children = listOf(XmlNode("location", children = listOf(kf(10), kf(45))))),
        XmlNode("effect", mapOf("id" to "glow"), listOf(property("radius", 10, 30), property("alpha", 10, 50))),
        XmlNode("effect", mapOf("id" to "tiles"), listOf(property("size", 10, 20))),
    ) + extra)

    private val scene = blank.add(layer())

    private fun Layer.frames(scope: KeyScope? = null) = keyframeTimes(scope).map { scene.frameAt(it) }
    private fun Layer.keys(ref: PropRef) = keyframes(ref).map { scene.frameAt(timeOf(it.t)) }

    @Test fun groupDisclosureKeepsChildTimingAndIndependentExpansion() {
        val host = blank.add(shapeLayer(id = 2, start = 500, end = 1999))
            .add(shapeLayer(id = 3, start = 200, end = 2199))
            .grouped(setOf(2L, 3L), null, 7, "Folder")
        val group = host.layer(7)!!
        val rows = timelineGroupRows(host, group, EffectCatalog()) { it == listOf(7L, 2L) }
        assertEquals(listOf(3L, 2L), rows.map { it.layer.id })
        assertEquals(null, rows[0].details)
        assertEquals(emptyList<app.absentmotion.editor.InlineSection>(), rows[1].details)
        assertEquals(500..1999, rows[1].visibleRange())
        assertEquals(300, rows[1].localTime(500))
    }

    @Test fun editingAGroupMemberUsesItsLocalTimeAndUpdatesTheParentScene() {
        val host = blank.add(shapeLayer(id = 2, start = 500, end = 1999))
            .add(shapeLayer(id = 3, start = 200, end = 2199))
            .grouped(setOf(2L, 3L), null, 7, "Folder")
        val groups = listOf(7L)
        val member = host.groupMemberTrack(groups, 2)!!
        val local = member.timeAt(500.0, activeOnly = true, quantize = false)!!.ms
        assertEquals(300.0, local, 0.001)
        assertEquals(500.0, member.hostTime(local)!!, 0.001)

        val changed = member.scene.update(2, local.roundToInt()) { it.withFill("color", 0xFF12AB34.toInt()) }
        val edited = host.withFocused(groups, changed)
        assertEquals(host.layers.map { it.id }, edited.layers.map { it.id })
        assertEquals(0xFF12AB34.toInt(), edited.groupMemberTrack(groups, 2)!!.layer.fillColor)
        assertEquals(host.groupMemberTrack(groups, 3)!!.layer.node, edited.groupMemberTrack(groups, 3)!!.layer.node)
    }

    @Test fun viewerRotationUsesTheLayerAxesAfterItHasTurned() {
        val initial = floatArrayOf(0f, 0f, 90f)
        val result = app.absentmotion.ui.editor.panels.localRotation(initial, 0, (Math.PI / 4).toFloat())
        assertEquals(-45f, result[0], 0.0001f)
        assertEquals(0f, result[1], 0.0001f)
        assertEquals(90f, result[2], 0.0001f)
    }

    @Test fun spatialProjectionKeepsDirectionsAndTheDepthOfTheLayerFace() {
        val projection = app.absentmotion.ui.editor.SpatialGizmoProjection(
            floatArrayOf(0f, -2f, 0f, 100f, 2f, 0f, 0f, 80f, 0f, 0f, 1f, 100f, 0f, 0f, 0f, 1f),
            floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
        )
        val point = projection.point(floatArrayOf(10f, 0f, 20f))!!
        assertEquals(100f, point.at.x, 0.0001f)
        assertEquals(100f, point.at.y, 0.0001f)
        assertEquals(120f, point.depth, 0.0001f)
        assertEquals(100f, projection.layerDepth(point.at, 0f)!!, 0.0001f)
        assertEquals(10f, projection.positionDelta(0, 10f)[1], 0.0001f)
    }

    @Test fun expandedTracksKeepBothWorkAreaEdgesInTheTransport() {
        val area = scene.withWorkArea(12, 42)
        assertEquals(listOf(0, 10, 12, 30, 42, 45, 60), area.inlineNavigationStops(listOf(10, 30, 45)))
        assertEquals(listOf(0, 60), scene.inlineNavigationStops(emptyList()))
    }

    @Test fun expandedTracksKeepBookmarksInsideAndOutsideTheWorkArea() {
        val marked = scene.withWorkArea(12, 42).withBookmarks(listOf(5, 25, 45, 50).map(scene::frameStart))
        assertEquals(listOf(0, 5, 10, 12, 25, 30, 42, 45, 50, 60),
            marked.inlineNavigationStops(listOf(10, 30, 45)))
    }

    @Test fun selectedLayerEdgesRemainStopsWithAWorkAreaAndExpandedTracks() {
        val area = scene.withWorkArea(12, 42)
        val clip = shapeLayer(start = scene.frameStart(15), end = scene.frameStart(36))
        assertEquals(listOf(0, 12, 15, 36, 42, 59), area.navigationStops(clip))
        assertEquals(listOf(0, 10, 12, 15, 30, 36, 42, 45, 60), area.inlineNavigationStops(listOf(10, 30, 45), clip))
    }

    @Test fun movingSelectedTracksTogetherStopsBeforeAnUnselectedKey() {
        val base = scene.layers.single()
        val picked = setOf(InlineKey(base.id, radius, 10), InlineKey(base.id, alpha, 10), InlineKey(base.id, alpha, 50))
        val (moved, selection) = InlineKeyMove(scene, picked).apply(scene, 28)
        val layer = moved.layers.single()
        assertEquals(listOf(19, 30), layer.keys(radius))
        assertEquals(listOf(19, 59), layer.keys(alpha))
        assertEquals(listOf(10, 20), layer.keys(tiles))
        assertEquals(setOf(InlineKey(base.id, radius, 19), InlineKey(base.id, alpha, 19), InlineKey(base.id, alpha, 59)), selection)
    }

    @Test fun aSingleTimelineKeyCanCrossAnotherKeyWithoutReplacingIt() {
        val base = scene.layers.single()
        val key = InlineKey(base.id, radius, 30)
        val move = InlineKeyMove(scene, setOf(key))
        val (atNeighbor, _) = move.apply(scene, -20)
        assertEquals(listOf(10, 11), atNeighbor.layers.single().keys(radius))
        val (moved, selection) = move.apply(atNeighbor, -30)
        assertEquals(listOf(0, 10), moved.layers.single().keys(radius))
        assertEquals(setOf(key.copy(frame = 0)), selection)
    }

    @Test fun movingAnExpandedKeyPastEitherClipEdgeKeepsTheClipTiming() {
        val position = XmlNode("location", children = listOf(
            XmlNode("kf", mapOf("t" to "0", "v" to "20,10,0")),
            XmlNode("kf", mapOf("t" to "1", "v" to "40,10,0")),
        ))
        val clip = shapeLayer(id = 2, start = 500, end = 999, children = listOf(XmlNode("transform", children = listOf(position))))
        val withClip = scene.add(clip)
        val ref = PropRef.Location
        val first = InlineKey(clip.id, ref, 15)
        val last = InlineKey(clip.id, ref, 29)
        val (earlier, atStart) = InlineKeyMove(withClip, setOf(first)).apply(withClip, -10)
        val (later, atEnd) = InlineKeyMove(earlier, setOf(last)).apply(earlier, 15)
        val moved = later.layer(clip.id)!!
        assertEquals(clip.startTime, moved.startTime)
        assertEquals(clip.endTime, moved.endTime)
        assertEquals(listOf(5, 44), moved.keys(ref))
        assertEquals(setOf(first.copy(frame = 5)), atStart)
        assertEquals(setOf(last.copy(frame = 44)), atEnd)
        assertEquals(listOf(5, 44), Scene.parse(later.toXml()).layer(clip.id)!!.keys(ref))
    }

    @Test fun timelinePropertiesOpenTheirNativeEditors() {
        val shape = layer()
        assertEquals(EditorPanel.MoveTransform, inlineEditorPanel(shape, PropRef.Tilt.copy(component = 1)))
        assertEquals(EditorPanel.Blending, inlineEditorPanel(shape, PropRef.Opacity))
        assertEquals(EditorPanel.Effects, inlineEditorPanel(shape, radius))
        assertEquals(EditorPanel.ColorFill, inlineEditorPanel(shape, PropRef.FillColor))
        assertEquals(EditorPanel.EditShape, inlineEditorPanel(shape, PropRef.shape("size")))
    }

    @Test fun positionUsesOneVectorKeyUntilDimensionsAreSeparated() {
        val location = XmlNode("location", children = listOf(kf(10, "20,60,0"), kf(45, "20,80,0")))
        val base = layer()
        val original = base.copy(node = base.node.withChild("transform", XmlNode("transform", children = listOf(location))))
        val at = original.at(scene.frameTime(20), scene.fps)
        val x = PropRef.Location.copy(component = 0)
        val y = PropRef.Location.copy(component = 1)

        val combined = at.withTransform("location", 20f, 90f, 0f)
        assertEquals(false, combined.positionDimensionsSeparated)
        assertEquals(listOf(10, 20, 45), combined.keys(PropRef.Location))
        assertEquals(listOf(10, 20, 45), combined.keys(x))
        assertEquals(listOf(10, 20, 45), combined.keys(y))

        val separated = at.withPositionDimensionsSeparated(true)!!.withTransform("location", 20f, 90f, 0f)
        assertEquals(true, separated.positionDimensionsSeparated)
        assertEquals(listOf(10, 45), separated.keys(x))
        assertEquals(listOf(10, 20, 45), separated.keys(y))
        assertEquals(null, separated.withPositionDimensionsSeparated(false))
    }

    @Test fun positionAxesWithMatchingKeysJoinWithoutChangingTheMotion() {
        val location = XmlNode("location", children = listOf(kf(10, "20,60,30"), kf(45, "40,80,30")))
        val base = layer()
        val original = base.copy(node = base.node.withChild("transform", XmlNode("transform", children = listOf(location))))
        val at = original.at(scene.frameTime(10), scene.fps)
        val edited = at.withPositionDimensionsSeparated(true)!!.withTransform("location", 30f, at.location[1], at.location[2])
        val joined = edited.withPositionDimensionsSeparated(false)!!
        assertEquals(false, joined.positionDimensionsSeparated)
        assertEquals(listOf(10, 45), joined.keys(PropRef.Location))
        for (frame in 0..50) {
            val time = scene.frameTime(frame)
            assertEquals(edited.at(time, scene.fps).location.toList(), joined.at(time, scene.fps).location.toList())
        }
    }

    @Test fun editingOneAxisInTheNativePanelDoesNotAddKeysToOtherAxes() {
        val location = XmlNode("location", children = listOf(kf(10, "20,60,0"), kf(45, "20,80,0")))
        val original = layer().copy(node = layer().node.withChild("transform", XmlNode("transform", children = listOf(location))))
        val at = original.at(scene.frameTime(20), scene.fps)
        val changed = at.withTransformAxes(PropRef.Location, at.location[0], 90f, 0f)
        assertEquals(listOf(10, 45), changed.keys(PropRef.Location.copy(component = 0)))
        assertEquals(listOf(10, 20, 45), changed.keys(PropRef.Location.copy(component = 1)))
    }

    @Test fun incompatibleScaleAxesKeepTheirOwnEditableCurvesWithTheChainOn() {
        val scale = XmlNode("scale", children = listOf(
            kf(10, "1,1"),
            XmlNode("axis", mapOf("index" to "0"), listOf(kf(15, "1"), kf(25, "2"))),
            XmlNode("axis", mapOf("index" to "1"), listOf(kf(20, "1"), kf(35, "2"))),
        ))
        val base = layer()
        val layer = base.copy(node = base.node.withChild("transform", XmlNode("transform", children = listOf(scale))))
        val ref = PropRef.Scale
        fun tracks(of: Layer) = inlineTrackSections(of, EffectCatalog()).flatMap { it.visibleTracks }.map { it.ref }

        val x = ref.copy(component = 0)
        val y = ref.copy(component = 1)
        assertEquals(listOf(x, y), tracks(layer).filter { it.name == ref.name })
        assertEquals(listOf(15, 20, 25, 35), layer.frames(KeyScope(ref)))
        val at = layer.at(scene.frameTime(22), scene.fps)
        val curve = Easing("cubicBezier", listOf(0.25f, 0f, 0.75f, 1f))
        assertEquals(Easing.Linear, at.easing(x))
        assertEquals(curve, at.withEasing(x, curve).easing(x))
        assertEquals(at.keyframes(y), at.withEasing(x, curve).keyframes(y))
        val unlinked = layer.withScaleLinked(false)
        assertEquals(listOf(x, y), tracks(unlinked).filter { it.name == ref.name })
        val separated = unlinked.withScaleDimensionsSeparated(true)!!
        assertEquals(listOf(ref.copy(component = 0), ref.copy(component = 1)), tracks(separated).filter { it.name == ref.name })
        assertEquals(listOf(15, 25), separated.keys(ref.copy(component = 0)))
        assertEquals(listOf(20, 35), separated.keys(ref.copy(component = 1)))
        val chained = separated.withScaleLinked(true)
        assertEquals(listOf(x, y), tracks(chained).filter { it.name == ref.name })
        assertEquals(listOf(15, 20, 25, 35), chained.frames(KeyScope(ref)))
    }

    @Test fun unlockedScaleCanUseSharedKeysOrEditOneAxis() {
        val scale = XmlNode("scale", children = listOf(kf(10, "1,1"), kf(45, "2,3")))
        val base = layer()
        val layer = base.copy(node = base.node.withChild("transform", XmlNode("transform", children = listOf(scale))))
        val shared = layer.withScaleLinked(false)
        val x = PropRef.Scale.copy(component = 0)
        val y = PropRef.Scale.copy(component = 1)
        val at = shared.at(scene.frameTime(20), scene.fps)
        val curve = Easing("cubicBezier", listOf(0.25f, 0f, 0.75f, 1f))
        assertEquals(curve, at.withEasing(PropRef.Scale, curve).easing(PropRef.Scale))
        val combined = at.withTransform("scale", 3f, at.scale[1])
        assertEquals(false, combined.scaleLinked)
        assertEquals(false, combined.scaleDimensionsSeparated)
        assertEquals(listOf(10, 20, 45), combined.keys(x))
        assertEquals(listOf(10, 20, 45), combined.keys(y))

        val atSplit = combined.withScaleDimensionsSeparated(true)!!.at(scene.frameTime(25), scene.fps)
        val separated = atSplit.withTransform("scale", 4f, atSplit.scale[1])
        assertEquals(true, separated.scaleDimensionsSeparated)
        assertEquals(listOf(10, 20, 25, 45), separated.keys(x))
        assertEquals(listOf(10, 20, 45), separated.keys(y))
        assertEquals(null, separated.withScaleDimensionsSeparated(false))
        assertEquals(true, separated.withScaleLinked(true).scaleDimensionsSeparated)
    }

    @Test fun aSeparatedPositionCurveWritesOnlyTheActiveAxis() {
        val location = XmlNode("location", children = listOf(kf(10, "10,20,30"), kf(45, "20,40,60")))
        val base = layer()
        val layer = base.copy(node = base.node.withChild("transform", XmlNode("transform", children = listOf(location))))
        val at = layer.withPositionDimensionsSeparated(true)!!.at(scene.frameTime(25), scene.fps)
        val x = PropRef.Location.copy(component = 0)
        val y = PropRef.Location.copy(component = 1)
        val z = PropRef.Location.copy(component = 2)
        val curve = Easing("cubicBezier", listOf(0.4f, 0f, 0.6f, 1f))
        val changed = at.withEasing(z, curve)
        assertEquals(at.keyframes(x), changed.keyframes(x))
        assertEquals(at.keyframes(y), changed.keyframes(y))
        assertEquals(curve, changed.easing(z))
    }

    @Test fun movingAndTogglingOnePositionAxisPreservesTheOther() {
        val location = XmlNode("location", children = listOf(kf(10, "20,60,0"), kf(45, "45,80,0")))
        val original = layer().copy(node = layer().node.withChild("transform", XmlNode("transform", children = listOf(location))))
        val x = PropRef.Location.copy(component = 0)
        val y = PropRef.Location.copy(component = 1)
        val moved = original.withKeyframesMoved(10, 15, scene, KeyScope(x))
        assertEquals(listOf(15, 45), moved.keys(x))
        assertEquals(listOf(10, 45), moved.keys(y))

        val at = moved.at(scene.frameTime(45), scene.fps)
        val removed = at.withKeyframeToggled(x, "45")
        assertEquals(listOf(15), removed.keys(x))
        assertEquals(listOf(10, 45), removed.keys(y))
        assertEquals(20f, removed.location[0], 0.0001f)
        assertEquals(80f, removed.location[1], 0.0001f)
    }

    @Test fun rotationSharesKeysUntilTheAxesAreSeparated() {
        val xyz = PropRef.RotationXYZ
        val x = PropRef.Tilt.copy(component = 0)
        val y = PropRef.Tilt.copy(component = 1)
        val z = PropRef.Rotation
        val original = layer().withThreeD(true).withTilt(20f, 15f).withRotation(30f)
        assertEquals(false, original.rotationDimensionsSeparated)
        assertEquals(20f, original.tilt[0], 0.001f)
        assertEquals(30f, original.rotation, 0.001f)

        val start = original.at(scene.frameTime(10), scene.fps).withKeyframeToggled(xyz, "0,0,0")
        val shared = start.at(scene.frameTime(45), scene.fps).withRotation(50f)
        assertEquals(listOf(10, 45), shared.keys(xyz))
        assertEquals(listOf(10, 45), shared.keys(x))
        assertEquals(listOf(10, 45), shared.keys(y))
        assertEquals(listOf(10, 45), shared.keys(z))
        assertEquals(null, shared.node.child("transform")?.child("rotationXYZ"))
        val moved = shared.withKeyframesMoved(45, 40, scene, KeyScope(xyz))
        assertEquals(listOf(10, 40), moved.keys(x))
        assertEquals(listOf(10, 40), moved.keys(z))

        val split = moved.withRotationDimensionsSeparated(true)!!.at(scene.frameTime(25), scene.fps)
        val changed = split.withTilt(split.tilt[0], 65f)
        assertEquals(split.keyframes(x), changed.keyframes(x))
        assertEquals(split.keyframes(z), changed.keyframes(z))
        assertEquals(listOf(10, 25, 40), changed.keys(y))
        assertEquals(null, changed.withRotationDimensionsSeparated(false))
        assertEquals(false, split.withRotationDimensionsSeparated(false)!!.rotationDimensionsSeparated)
    }

    @Test fun noScopeKeepsEveryKeyframeOfTheLayer() {
        assertEquals(listOf(10, 20, 30, 45, 50), layer().frames())
    }

    @Test fun aSingleParameterShowsOnlyItsOwnKeyframes() {
        val base = layer()
        assertEquals(listOf(10, 30), base.frames(KeyScope(radius)))
        assertEquals(listOf(10, 50), base.frames(KeyScope(alpha)))
        assertEquals(listOf(10, 20), base.frames(KeyScope(tiles)))
        assertEquals(listOf(10, 45), base.frames(KeyScope(PropRef.Location)))
    }

    @Test fun anOpenEffectShowsOnlyItsParametersTogether() {
        val base = layer()
        assertEquals(listOf(10, 30, 50), base.frames(KeyScope(effectIndex = 0)))
        assertEquals(listOf(10, 20), base.frames(KeyScope(effectIndex = 1)))
        assertEquals(emptyList<Int>(), base.frames(KeyScope(effectIndex = 5)))
    }

    @Test fun aParameterWithoutKeyframesOrAPropertyHasNone() {
        assertEquals(emptyList<Int>(), layer().frames(KeyScope(PropRef.effect(0, "missing"))))
        assertEquals(emptyList<Int>(), layer().frames(KeyScope(PropRef.Opacity)))
    }

    @Test fun movingOneParameterLeavesTheOthersInPlace() {
        val moved = layer().withKeyframesMoved(10, 25, scene, KeyScope(radius))
        assertEquals(listOf(25, 30), moved.keys(radius))
        assertEquals(listOf(10, 50), moved.keys(alpha))
        assertEquals(listOf(10, 20), moved.keys(tiles))
        assertEquals(listOf(10, 45), moved.keys(PropRef.Location))
    }

    @Test fun movingOneParameterPastItsOwnKeyKeepsBoth() {
        val moved = layer().withKeyframesMoved(30, 5, scene, KeyScope(radius))
        assertEquals(listOf(5, 10), moved.keys(radius))
        assertEquals(listOf(10, 50), moved.keys(alpha))
    }

    @Test fun movingAnOpenEffectMovesAllItsParametersAndNothingElse() {
        val moved = layer().withKeyframesMoved(10, 25, scene, KeyScope(effectIndex = 0))
        assertEquals(listOf(25, 30), moved.keys(radius))
        assertEquals(listOf(25, 50), moved.keys(alpha))
        assertEquals(listOf(10, 20), moved.keys(tiles))
        assertEquals(listOf(10, 45), moved.keys(PropRef.Location))
    }

    @Test fun movingATransformPropertyLeavesEffectsAlone() {
        val moved = layer().withKeyframesMoved(10, 25, scene, KeyScope(PropRef.Location))
        assertEquals(listOf(25, 45), moved.keys(PropRef.Location))
        assertEquals(listOf(10, 30), moved.keys(radius))
        assertEquals(listOf(10, 20), moved.keys(tiles))
    }

    @Test fun movingWhereTheScopeHasNoKeyChangesNothing() {
        val base = layer()
        assertEquals(base, base.withKeyframesMoved(20, 40, scene, KeyScope(radius)))
        assertSame(base, base.withKeyframesMoved(10, 10, scene, KeyScope(radius)))
    }

    @Test fun noScopeStillMovesEveryPropertyAtTheFrame() {
        val moved = layer().withKeyframesMoved(10, 25, scene)
        assertEquals(listOf(25, 30), moved.keys(radius))
        assertEquals(listOf(25, 50), moved.keys(alpha))
        assertEquals(listOf(20, 25), moved.keys(tiles))
        assertEquals(listOf(25, 45), moved.keys(PropRef.Location))
    }

    @Test fun theSpeedMapMovesOnlyWithTheSpeedTarget() {
        val speed = XmlNode("speedMap", children = listOf(
            XmlNode("kf", mapOf("t" to "0", "v" to "1")),
            XmlNode("kf", mapOf("t" to "1000000000", "v" to "2")),
        ))
        val plain = layer(listOf(speed))
        val at = scene.frameAt(VideoTiming(plain).localMs(1000.0).roundToInt())
        val glowAtSpeed = Layer(plain.node.copy(children = plain.node.children.map { if (it.name == "effect" && it["id"] == "glow") it.copy(children = listOf(property("radius", at, 30))) else it }))
        fun speedFrames(l: Layer) = l.node.child("speedMap")!!.keyframes.map { scene.frameAt(l.startTime + VideoTiming(l).localMs(it.t / 1e6).roundToInt()) }

        assertEquals(listOf(0, at), speedFrames(glowAtSpeed))
        assertEquals(listOf(0, at), glowAtSpeed.frames(KeyScope(SpeedRef)))
        assertEquals(listOf(at, 30), glowAtSpeed.frames(KeyScope(radius)))

        val glowMoved = glowAtSpeed.withKeyframesMoved(at, 40, scene, KeyScope(radius))
        assertEquals(listOf(30, 40), glowMoved.keys(radius))
        assertEquals(glowAtSpeed.node.child("speedMap"), glowMoved.node.child("speedMap"))

        val speedMoved = glowAtSpeed.withKeyframesMoved(at, 40, scene, KeyScope(SpeedRef))
        assertEquals(listOf(0, 40), speedFrames(speedMoved))
        fun media(l: Layer) = l.keyframes(radius).map { VideoTiming(l).mediaMs(it.t.toDouble() * (l.endTime - l.startTime)) }
        media(glowAtSpeed).zip(media(speedMoved)).forEach { (before, after) -> assertEquals(before, after, 1.0) }
    }

    @Test fun skippingStepsOnlyThroughTheScopedKeyframes() {
        val base = layer()
        assertEquals(listOf(0, 10, 20, 30, 45, 50, 59), scene.navigationStops(base))
        assertEquals(listOf(0, 10, 30, 59), scene.navigationStops(base, KeyScope(radius)))
        assertEquals(listOf(0, 10, 20, 59), scene.navigationStops(base, KeyScope(effectIndex = 1)))
    }
}
