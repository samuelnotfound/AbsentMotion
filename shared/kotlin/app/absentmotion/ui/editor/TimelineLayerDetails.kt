package app.absentmotion.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.KeypadField
import app.absentmotion.editor.KeypadRequest
import app.absentmotion.editor.focused
import app.absentmotion.editor.isPlainGroup
import app.absentmotion.editor.withFocused
import app.absentmotion.editor.withFrameHold
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.model.FrameRef
import app.absentmotion.model.KeyScope
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.Scene
import app.absentmotion.model.SpeedRef
import app.absentmotion.model.XmlNode
import app.absentmotion.model.effects
import app.absentmotion.model.formatFloats
import app.absentmotion.model.keyframes
import app.absentmotion.model.nestedHostTime
import app.absentmotion.model.nestedTime
import app.absentmotion.model.shownName
import app.absentmotion.model.valueAt
import app.absentmotion.model.withKeyframesMoved
import app.absentmotion.model.withSpeedKeyframeToggled
import app.absentmotion.ui.editor.panels.PanelIcon
import app.absentmotion.ui.theme.Am
import kotlin.math.roundToInt

internal fun Layer.hasTimelineDetails(): Boolean =
    keyframeTimes().isNotEmpty() || keyframes(SpeedRef).isNotEmpty()

private fun Layer.animatedProperties(): List<PropRef> = buildList {
    fun walk(parent: XmlNode, prefix: String) {
        val occurrences = HashMap<String, Int>()
        for (child in parent.children) {
            val index = occurrences.getOrDefault(child.name, 0)
            occurrences[child.name] = index + 1
            if (child.name in setOf("scene", "kf", "effect", "property")) continue
            val segment = if (index == 0) child.name else "${child.name}[$index]"
            val path = if (prefix.isEmpty()) segment else "$prefix/$segment"
            if (child.keyframes.isNotEmpty()) {
                add(if (prefix == "transform") PropRef(PropRef.Kind.Transform, child.name) else PropRef(PropRef.Kind.Child, path))
            }
            walk(child, path)
        }
    }
    walk(node, "")
    for (child in node.children) {
        if (child.name == "property" && child.keyframes.isNotEmpty()) child["name"]?.let { add(PropRef.shape(it)) }
    }
    for ((index, effect) in effects.withIndex()) {
        for (child in effect.children) {
            if (child.name == "property" && child.keyframes.isNotEmpty()) child["name"]?.let { add(PropRef.effect(index, it)) }
        }
    }
}

@Composable
internal fun TimelineLayerDetails(
    state: EditorState, layer: Layer, scoped: PropRef?, modifier: Modifier,
    onScope: (PropRef?) -> Unit, onDismiss: () -> Unit,
) {
    val effects by state.engine.effects.collectAsState()
    Box(modifier) {
        DropdownMenu(true, onDismiss, Modifier.width(282.dp).heightIn(max = 350.dp), containerColor = Am.Field) {
            TimelineDetailLayer(state, layer, emptyList(), effects, primary = true, scoped = scoped, onScope = onScope, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun TimelineDetailLayer(
    state: EditorState, layer: Layer, groups: List<Long>, catalog: EffectCatalog,
    primary: Boolean = false, scoped: PropRef? = null, onScope: (PropRef?) -> Unit, onDismiss: () -> Unit,
) {
    var expanded by remember(groups, layer.id) { mutableStateOf(primary) }
    var property by remember(groups, layer.id) { mutableStateOf(scoped) }
    val indent = (groups.size.coerceAtMost(3) * 10).dp
    val owner = state.scene.focused(groups) ?: return
    val canEdit = !state.lookOnly && !layer.locked && groupClocks(state.scene, groups)?.none { it.first.locked } == true
    val refs = remember(layer.node, primary) {
        val animated = layer.animatedProperties()
        if (primary) animated else (animated + listOf(PropRef.Location, PropRef.Scale, PropRef.Rotation)).distinct()
    }
    Row(Modifier.padding(start = indent, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        DropdownMenuItem(
            text = { Text(layer.shownName(), color = Am.OnDark, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { expanded = !expanded },
            modifier = Modifier.weight(1f),
            leadingIcon = { PanelIcon(if (expanded) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, Am.OnDark, size = 16.dp) },
        )
        if (!primary) DetailButton(
            if (layer.hidden) TimelineIcons.EyeOff else TimelineIcons.Eye,
            if (layer.hidden) R.string.a11y_show_layer else R.string.a11y_hide_layer, canEdit,
        ) { editTimelineLayer(state, groups, layer.id) { _, item, _ -> item.withHidden(!item.hidden) } }
    }
    if (!expanded) return
    for (ref in refs) {
        val label = propertyName(ref, layer, catalog)
        DropdownMenuItem(
            text = { Text(label, color = if (property == ref) Am.Accent else Am.OnDark, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.padding(start = indent + 20.dp),
            onClick = {
                property = ref.takeUnless { it == property }
                if (primary) onScope(property)
            },
            trailingIcon = { if (property == ref) PanelIcon(PanelIcons.Check, Am.Accent, size = 14.dp) },
        )
        if (property == ref) {
            TimelinePropertyTools(state, layer, owner, groups, ref, label, canEdit, Modifier.padding(start = indent + 20.dp), onDismiss)
        }
    }
}

@Composable
private fun propertyName(ref: PropRef, layer: Layer, catalog: EffectCatalog): String {
    val label = when (ref) {
        PropRef.Location -> stringResource(R.string.panel_position)
        PropRef.Rotation -> stringResource(R.string.a11y_tab_rotate)
        PropRef.Scale -> stringResource(R.string.panel_size)
        PropRef.Pivot -> stringResource(R.string.a11y_tab_pivot)
        PropRef.Opacity -> stringResource(R.string.history_opacity)
        SpeedRef -> stringResource(R.string.a11y_speed)
        else -> ref.name.substringAfterLast('/').replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }
    return when (ref.kind) {
        PropRef.Kind.Transform -> stringResource(R.string.layer_tool_transform) + " · " + label
        PropRef.Kind.Effect -> {
            val id = layer.effects.getOrNull(ref.effectIndex)?.get("id").orEmpty()
            val def = catalog.find(id)
            (def?.name ?: id.substringAfterLast('.')) + " · " + (def?.params?.firstOrNull { it.id == ref.name }?.label ?: label)
        }
        else -> label
    }
}

@Composable
private fun TimelinePropertyTools(
    state: EditorState, layer: Layer, owner: Scene, groups: List<Long>, ref: PropRef, label: String,
    canEdit: Boolean, modifier: Modifier, onDismiss: () -> Unit,
) {
    val clocks = groupClocks(state.scene, groups) ?: return
    val localTime = localTimelineTime(state.sampleMs, clocks) ?: return
    val frame = owner.frameAt(localTime)
    val time = owner.frameTime(frame)
    val member = owner.layerAt(layer.id, time) ?: return
    val frames = remember(layer.node, ref, owner.fps) {
        layer.keyframeTimes(KeyScope(ref)).map(owner::frameAt).distinct()
    }
    val previous = frames.lastOrNull { it < frame }
    val next = frames.firstOrNull { it > frame }
    val onKey = frame in frames
    val element = member.element(ref)
    val fallback = when (ref) {
        PropRef.Location -> formatFloats(member.location)
        PropRef.Scale -> formatFloats(member.scale)
        PropRef.Rotation -> (-member.rotation).toString()
        else -> "0"
    }
    val raw = if (ref == SpeedRef) "" else element?.valueAt(member.t) ?: fallback
    val typed = raw.split(',').map { it.trim().toFloatOrNull()?.takeIf { n -> n.isFinite() } }
    val numbers = when (ref) {
        PropRef.Location -> member.location.take(if (member.uses3D) 3 else 2)
        PropRef.Rotation -> typed.map { it?.unaryMinus() }
        else -> typed
    }
    val editable = canEdit && (ref != PropRef.Tilt || member.uses3D) &&
        localTimelineTime(state.sampleMs, clocks, insideGroups = true) != null &&
        frame in owner.firstShownFrame(member.startTime)..owner.lastShownFrame(member.endTime) &&
        element?.children?.none { it.name == "valueLink" } != false
    fun seek(localFrame: Int) {
        val host = clocks.asReversed().fold(owner.frameStart(localFrame).toDouble()) { ms, (group, fps) -> group.nestedHostTime(ms, fps) }
        state.playing = false
        state.frame = state.scene.frameAt(host.roundToInt()).coerceIn(0, state.scene.lastFrame.coerceAtLeast(0))
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DetailButton(ChromeIcons.SkipBack, R.string.desk_key_previous_keyframe, previous != null) { previous?.let(::seek) }
        DetailButton(
            if (onKey) PanelIcons.KeyframeRemove else PanelIcons.Keyframe,
            if (onKey) R.string.a11y_remove_keyframe else R.string.a11y_add_keyframe, editable && (ref != SpeedRef || !member.timingLocked),
        ) {
            editTimelineLayer(state, groups, layer.id) { scene, item, at ->
                if (ref == SpeedRef) item.withSpeedKeyframeToggled(at, scene.fps)
                else item.withKeyframeToggled(ref, raw.ifEmpty { fallback }).let { if (ref == FrameRef) it.withFrameHold() else it }
            }
        }
        DetailButton(ChromeIcons.SkipForward, R.string.desk_key_next_keyframe, next != null) { next?.let(::seek) }
        if (numbers.size in 1..4 && numbers.all { it != null }) {
            TextButton(enabled = editable, onClick = {
                val fields = numbers.mapIndexed { index, value ->
                    KeypadField(if (numbers.size == 1) label else "XYZW"[index].toString(), value!!)
                }
                onDismiss()
                val at = state.sampleMs
                state.keypad = KeypadRequest(fields, 0) { values ->
                    editTimelineLayer(state, groups, layer.id, at) { _, item, _ ->
                        when (ref) {
                            PropRef.Location -> if (values.size < 2) item else item.withTransform("location", values[0], values[1], values.getOrNull(2) ?: item.location[2])
                            PropRef.Rotation -> item.withRotation(values[0])
                            else -> item.withValue(ref, formatFloats(values.toFloatArray()))
                        }
                    }
                }
            }) { Text(raw, color = Am.Accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (onKey && editable && !member.timingLocked) {
        val first = owner.firstShownFrame(member.startTime)
        val last = owner.lastShownFrame(member.endTime)
        TextButton(modifier = modifier, onClick = {
            onDismiss()
            val at = state.sampleMs
            state.keypad = KeypadRequest(listOf(KeypadField(label, frame.toFloat(), min = first.toFloat(), max = last.toFloat())), 0) { values ->
                val destination = values[0].roundToInt().coerceIn(first, last)
                editTimelineLayer(state, groups, layer.id, at) { scene, item, _ ->
                    item.withKeyframesMoved(frame, destination, scene, KeyScope(ref))
                }
                seek(destination)
            }
        }) {
            Text(TimelineMath.timecode(frame, owner.fps), color = Am.Accent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DetailButton(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        PanelIcon(icon, if (enabled) Am.OnDark else Am.OnDarkDim, size = 18.dp, description = stringResource(label))
    }
}

private fun groupClocks(scene: Scene, groups: List<Long>): List<Pair<Layer, Int>>? {
    val clocks = ArrayList<Pair<Layer, Int>>(groups.size)
    var host = scene
    for (id in groups) {
        val group = host.layer(id)?.takeIf { it.isPlainGroup } ?: return null
        clocks.add(group to host.fps)
        host = group.nested ?: return null
    }
    return clocks
}

private fun localTimelineTime(time: Int, clocks: List<Pair<Layer, Int>>, insideGroups: Boolean = false): Int? {
    var ms = time.toDouble()
    for ((group, fps) in clocks) {
        if (insideGroups && (ms < group.startTime || ms > group.endTime)) return null
        ms = group.nestedTime(ms, fps, quantize = false).ms
    }
    return ms.roundToInt()
}

private fun editTimelineLayer(
    state: EditorState, groups: List<Long>, id: Long, time: Int = state.sampleMs, change: (Scene, Layer, Int) -> Layer,
) {
    if (state.lookOnly) return
    state.edit { scene ->
        val clocks = groupClocks(scene, groups)?.takeUnless { it.any { (group, _) -> group.locked } } ?: return@edit scene
        val owner = scene.focused(groups) ?: return@edit scene
        val local = localTimelineTime(time, clocks) ?: return@edit scene
        val at = owner.frameTime(owner.frameAt(local))
        val layer = owner.layerAt(id, at)?.takeUnless { it.locked } ?: return@edit scene
        val next = change(owner, layer, at)
        if (next.node == layer.node) scene else scene.withFocused(groups, owner.update(id, at) { next })
    }
}
