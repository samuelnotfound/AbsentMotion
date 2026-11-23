package app.absentmotion.editor

import app.absentmotion.R
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.i18n.AppText
import app.absentmotion.model.KeyScope
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.Scene
import app.absentmotion.model.SpeedRef
import app.absentmotion.model.XmlNode
import app.absentmotion.model.axis
import app.absentmotion.model.axisTrack
import app.absentmotion.model.effects
import app.absentmotion.model.isKeyframed
import app.absentmotion.model.keyframes

internal data class InlineTrack(
    val ref: PropRef,
    val label: String,
    val multiplier: Float = 1f,
    val unit: String = "",
    val range: ClosedFloatingPointRange<Float>? = null,
    val sensitivity: Float = 1f,
    val integer: Boolean = false,
) {
    fun frames(scene: Scene, layer: Layer): List<Int> = layer.keyframeTimes(KeyScope(ref)).map(scene::frameAt).distinct()

    fun linked(layer: Layer): Boolean = layer.propertyElement(ref)?.children?.any {
        it.name == "valueLink" && (ref.component == null || (it["component"]?.toIntOrNull() ?: 0) == ref.component)
    } == true
}

internal data class InlineSection(val id: String, val label: String, val tracks: List<InlineTrack>)

internal fun inlineTrackSections(layer: Layer, catalog: EffectCatalog): List<InlineSection> = buildList {
    val transform = mutableListOf<InlineTrack>()
    fun vector(ref: PropRef, title: String, axes: String, defaults: String, multiplier: Float = 1f, unit: String = "") {
        val node = layer.propertyElement(ref) ?: return
        for ((index, letter) in axes.withIndex()) {
            val explicit = node.axis(index)
            val axis = explicit ?: node.axisTrack(index, defaults)
            if (!axis.isKeyframed) continue
            if (letter == 'Z' && explicit == null && axis.keyframes.map { it.v.toFloatOrNull() }.distinct().size < 2) continue
            transform += InlineTrack(ref.copy(component = index), "$letter $title", multiplier, unit)
        }
    }
    val xyz = if (layer.uses3D) "XYZ" else "XY"
    val rotate = AppText.get(R.string.a11y_tab_rotate)
    vector(PropRef.Location, AppText.get(R.string.panel_position), xyz, "0,0,0")
    if (layer.propertyElement(PropRef.Rotation)?.isKeyframed == true) {
        transform += InlineTrack(PropRef.Rotation, if (layer.uses3D) "Z $rotate" else rotate, -1f, "°")
    }
    if (layer.uses3D) vector(PropRef.Tilt, rotate, "XY", "0,0", unit = "°")
    vector(PropRef.Scale, AppText.get(R.string.panel_size), "XY", "1,1", 100f, "%")
    vector(PropRef.Pivot, AppText.get(R.string.a11y_tab_pivot), xyz, "0,0,0")
    vector(PropRef.Skew, AppText.get(R.string.a11y_tab_skew), "XY", "0,0", (180 / Math.PI).toFloat(), "°")
    if (layer.propertyElement(PropRef.Opacity)?.isKeyframed == true) {
        transform += InlineTrack(PropRef.Opacity, AppText.get(R.string.layer_tool_opacity_blend), 100f, "%", 0f..100f)
    }
    if (transform.isNotEmpty()) add(InlineSection("transform", AppText.get(R.string.layer_tool_transform), transform))

    for ((index, effect) in layer.effects.withIndex()) {
        val id = effect["id"] ?: continue
        val definition = catalog.find(id)
        val tracks = effect.children.mapNotNull { property ->
            val name = property["name"]?.takeIf { property.name == "property" && property.isKeyframed } ?: return@mapNotNull null
            val parameter = definition?.params?.firstOrNull { it.id == name }
            val multiplier = if (parameter?.percent == true) 100f else 1f
            val unit = if (parameter?.percent == true) "%" else if (parameter?.kind == "angle") "°" else parameter?.unit.orEmpty()
            val bounds = parameter?.let { (it.min * multiplier)..(it.max * multiplier) }
                ?.takeIf { !it.isEmpty() && it.start.isFinite() && it.endInclusive.isFinite() }
            val span = bounds?.let { it.endInclusive - it.start }
            InlineTrack(PropRef.effect(index, name), parameter?.label ?: labelOf(name), multiplier, unit, bounds,
                span?.takeIf { it in 0.0001f..6000f }?.div(120f) ?: 1f, parameter?.kind == "choice")
        }
        if (tracks.isNotEmpty()) add(InlineSection("effect-$index", definition?.name ?: labelOf(id.substringAfterLast('.')), tracks))
    }

    fun collect(node: XmlNode, path: String, name: String, tracks: MutableList<InlineTrack>) {
        if (node.isKeyframed) {
            val ref = PropRef(PropRef.Kind.Child, path)
            val track = when (ref) {
                SpeedRef -> InlineTrack(ref, AppText.get(R.string.a11y_speed), unit = "×")
                PropRef.FillColor -> InlineTrack(ref, AppText.get(R.string.layer_tool_fill_color))
                else -> when (path) {
                    "gain" -> InlineTrack(ref, AppText.get(R.string.volume_volume), 100f, "%", 0f..400f)
                    "frame" -> InlineTrack(ref, labelOf(name), integer = true)
                    else -> InlineTrack(ref, labelOf(name))
                }
            }
            tracks += track
        }
        for ((child, segment) in childPaths(node)) collect(child, "$path/$segment", child.name, tracks)
    }
    val properties = mutableListOf<InlineTrack>()
    for ((node, path) in childPaths(layer.node)) {
        when (node.name) {
            "transform", "effect", "scene" -> Unit
            "property" -> if (node.isKeyframed) node["name"]?.let { properties += InlineTrack(PropRef.shape(it), labelOf(it)) }
            else -> {
                val tracks = mutableListOf<InlineTrack>()
                collect(node, path, node.name, tracks)
                if (tracks.size > 1) add(InlineSection(path, labelOf(node.name), tracks)) else properties += tracks
            }
        }
    }
    if (properties.isNotEmpty()) add(InlineSection("properties", AppText.get(R.string.inline_properties), properties))
}

private fun childPaths(node: XmlNode): List<Pair<XmlNode, String>> {
    val counts = node.children.groupingBy { it.name }.eachCount()
    val next = mutableMapOf<String, Int>()
    return node.children.mapNotNull { child ->
        if (child.name in setOf("kf", "valueLink", "axis", "scene")) return@mapNotNull null
        val i = next.getOrDefault(child.name, 0)
        next[child.name] = i + 1
        child to if (counts.getValue(child.name) == 1) child.name else "${child.name}[$i]"
    }
}

private fun labelOf(name: String): String = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .replace('_', ' ').replaceFirstChar { it.titlecase() }
