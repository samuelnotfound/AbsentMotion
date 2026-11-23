package app.absentmotion.editor

import app.absentmotion.R
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.i18n.AppText
import app.absentmotion.model.Keyframe
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.Scene
import app.absentmotion.model.XmlNode
import app.absentmotion.model.axisTrack
import app.absentmotion.model.effects
import app.absentmotion.model.formatFloats
import app.absentmotion.model.isKeyframed
import app.absentmotion.model.keyframes
import app.absentmotion.model.valueAt
import app.absentmotion.model.withAxis
import app.absentmotion.model.withKeyframes
import app.absentmotion.model.withKeyframeToggled
import app.absentmotion.model.withValueAt
import kotlin.math.round

internal data class TimelineTrack(
    val ref: PropRef, val label: String, val defaults: String, val component: Int? = null,
    val type: String = "float", val lower: Float = -Float.MAX_VALUE, val upper: Float = Float.MAX_VALUE,
    val displayScale: Float = 1f, val unit: String = "", val discrete: Boolean = false,
) {
    private fun root(layer: Layer): XmlNode = layer.element(ref) ?: XmlNode(ref.name, mapOf("type" to type, "value" to defaults))
    internal fun node(layer: Layer): XmlNode = root(layer).let { node -> component?.let { node.axisTrack(it, defaults) } ?: node }
    val canScrub: Boolean get() = type != "color" && type != "string"
    fun rawValue(layer: Layer): String = node(layer).valueAt(layer.t).orEmpty()
    fun keys(layer: Layer): List<Keyframe> = node(layer).keyframes
    fun value(layer: Layer): Float = when (val raw = node(layer).valueAt(layer.t)) {
        "true" -> 1f
        "false" -> 0f
        else -> raw?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
    } * displayScale
    fun linked(layer: Layer): Boolean = root(layer).children.any {
        it.name == "valueLink" && (it["component"]?.toIntOrNull() ?: 0) == (component ?: 0)
    }
    fun clamp(value: Float): Float = value.coerceIn(lower, upper).let { if (discrete) round(it) else it }
    fun sensitivity(): Float = if (upper - lower in 0f..1_000_000f) (upper - lower) / 600f else 1f
    fun reset(layer: Layer): Layer = change(layer, when (val raw = defaults.split(',').getOrElse(component ?: 0) { "0" }) {
        "true" -> displayScale
        "false" -> 0f
        else -> (raw.toFloatOrNull() ?: 0f) * displayScale
    })

    private fun raw(value: Float): String = if (type == "bool") (value >= 0.5f).toString() else formatFloats(floatArrayOf(value / displayScale))

    private fun store(layer: Layer, at: XmlNode): Layer {
        val property = if (component == null) at else root(layer).withAxis(component, at)
        return layer.withElement(ref, property)
    }

    fun change(layer: Layer, value: Float): Layer {
        if (linked(layer) || !value.isFinite()) return layer
        val next = clamp(value)
        return if (this.value(layer) == next) layer else store(layer, node(layer).withValueAt(layer.t, raw(next), layer.snap))
    }

    fun toggleKey(layer: Layer): Layer {
        if (linked(layer)) return layer
        return store(layer, node(layer).withKeyframeToggled(layer.t, layer.snap))
    }

    fun stopAnimating(layer: Layer): Layer {
        val original = node(layer)
        if (linked(layer) || !original.isKeyframed) return layer
        return store(layer, original.copy(children = original.children.filterNot { it.name == "kf" }).with("value", original.valueAt(layer.t)))
    }

    fun withKeys(layer: Layer, keys: List<Keyframe>, at: Float): Layer {
        if (keys.isNotEmpty()) return store(layer, node(layer).withKeyframes(keys))
        val original = node(layer)
        return store(layer, original.copy(children = original.children.filterNot { it.name == "kf" }).with("value", original.valueAt(at)))
    }
}

internal data class TrackSection(val name: String, val tracks: List<TimelineTrack>)

internal fun timelineTrackSections(layer: Layer, catalog: EffectCatalog): List<TrackSection> = buildList {
    if (layer.kind != "audio") {
        val location = AppText.get(R.string.panel_position)
        val rotation = AppText.get(R.string.a11y_tab_rotate)
        val scale = AppText.get(R.string.panel_size)
        val xyz = if (layer.uses3D) 3 else 2
        val tracks = buildList {
            for (i in 0 until xyz) add(TimelineTrack(PropRef.Location, "$location ${"XYZ"[i]}", "0,0,0", i, "vec3"))
            for (i in 0..1) add(TimelineTrack(PropRef.Scale, "$scale ${"XY"[i]}", "1,1", i, "vec2", displayScale = 100f, unit = "%"))
            add(TimelineTrack(PropRef.Rotation, if (layer.uses3D) "$rotation Z" else rotation, "0", displayScale = -1f, unit = "°"))
            if (layer.uses3D) for (i in 0..1) add(TimelineTrack(PropRef.Tilt, "$rotation ${"XY"[i]}", "0,0", i, "vec2", unit = "°"))
            add(TimelineTrack(PropRef.Opacity, AppText.get(R.string.history_opacity), "1", lower = 0f, upper = 100f, displayScale = 100f, unit = "%"))
        }
        add(TrackSection(AppText.get(R.string.layer_tool_transform), tracks))
    }
    for ((index, instance) in layer.effects.withIndex()) {
        val definition = instance["id"]?.let(catalog::find) ?: continue
        val tracks = definition.params.flatMap { param ->
            val multiplier = if (param.percent) 100f else 1f
            val unit = if (param.percent) "%" else if (param.kind == "angle") "°" else param.unit
            val discrete = param.kind == "switch" || param.kind == "choice"
            val min = if (param.kind == "switch") 0f else param.min
            val max = if (param.kind == "switch") 1f else param.max
            val bound = { value: Float -> (value.toDouble() * multiplier).coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat() }
            val ref = PropRef.effect(index, param.id)
            val axis = if (param.kind == "point") listOf(0, 1) else listOf(null)
            axis.map { i ->
                val label = if (i == null) param.label else "${param.label} ${"XY"[i]}"
                TimelineTrack(ref, label, param.missingDefault, i, param.type, bound(min), bound(max), multiplier, unit, discrete)
            }
        }
        if (tracks.isNotEmpty()) add(TrackSection(definition.name, tracks))
    }
}

internal data class TrackKey(val layerId: Long, val track: TimelineTrack, val frame: Int)

internal fun TimelineTrack.keyFrames(scene: Scene, layer: Layer): List<Int> =
    keys(layer).map { scene.frameAt(layer.timeOf(it.t)) }.distinct()

internal fun Scene.withTimelineKeyDelta(picked: Set<TrackKey>, requested: Int): Pair<Scene, Int> {
    if (picked.isEmpty()) return this to 0
    val groups = picked.groupBy { it.layerId to it.track }
    var low = Int.MIN_VALUE
    var high = Int.MAX_VALUE
    for ((owner, selection) in groups) {
        val layer = layer(owner.first)?.takeUnless { it.locked || it.timingLocked } ?: return this to 0
        val frames = selection.map { it.frame }.toSet()
        val blocked = owner.second.keyFrames(this, layer).filterNot { it in frames }
        val first = firstShownFrame(layer.startTime)
        val last = lastShownFrame(layer.endTime)
        for (frame in frames) {
            low = maxOf(low, first - frame, (blocked.lastOrNull { it < frame } ?: (first - 1)) + 1 - frame)
            high = minOf(high, last - frame, (blocked.firstOrNull { it > frame } ?: (last + 1)) - 1 - frame)
        }
    }
    if (low > high) return this to 0
    val delta = requested.coerceIn(low, high)
    if (delta == 0) return this to 0
    var out = this
    for ((owner, selection) in groups) {
        val (id, track) = owner
        val moved = selection.map { it.frame }.toSet()
        out = out.update(id) { layer ->
            track.withKeys(layer, track.keys(layer).map { key ->
                val frame = frameAt(layer.timeOf(key.t))
                if (frame in moved) key.copy(t = layer.at(frameStart(frame + delta), fps).t) else key
            }, layer.t)
        }
    }
    return out to delta
}

internal fun Scene.withoutTimelineKeys(picked: Set<TrackKey>, at: Int): Scene =
    picked.groupBy { it.layerId to it.track }.entries.fold(this) { scene, (owner, selection) ->
        val (id, track) = owner
        scene.update(id, at) { layer ->
            if (layer.locked || layer.timingLocked || track.linked(layer)) return@update layer
            val frames = selection.map { it.frame }.toSet()
            track.withKeys(layer, track.keys(layer).filterNot { frameAt(layer.timeOf(it.t)) in frames }, layer.t)
        }
    }

internal fun EditorState.editTimelineTrack(id: Long, at: Int, key: Any? = null, change: (Layer) -> Layer) {
    if (lookOnly) return
    edit(key) { scene ->
        scene.update(id, at) { layer -> if (layer.locked || at !in layer.startTime..layer.endTime) layer else change(layer) }
    }
}

internal val Layer.timelineMotionBlur: Boolean get() = node["layerMotionBlur"] == "true"

internal fun EditorState.toggleTimelineMotionBlur(id: Long) {
    if (lookOnly) return
    edit { scene ->
        scene.update(id) { layer ->
            if (layer.locked || layer.kind in setOf("audio", "camera", "light", "nullobj")) layer else
                layer.copy(node = layer.node.with("layerMotionBlur", if (layer.timelineMotionBlur) null else "true"))
        }
    }
}

internal fun locationTimelineTrack(axis: Int) = TimelineTrack(PropRef.Location, "XYZ"[axis].toString(), "0,0,0", axis, "vec3")
