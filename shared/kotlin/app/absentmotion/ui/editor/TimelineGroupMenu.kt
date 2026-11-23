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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.KeypadField
import app.absentmotion.editor.KeypadRequest
import app.absentmotion.editor.focused
import app.absentmotion.editor.isPlainGroup
import app.absentmotion.editor.withFocused
import app.absentmotion.model.Layer
import app.absentmotion.model.Scene
import app.absentmotion.model.nestedTime
import app.absentmotion.model.shownName
import app.absentmotion.ui.editor.panels.PanelIcon
import app.absentmotion.ui.theme.Am
import kotlin.math.roundToInt

@Composable
internal fun TimelineGroupMenu(state: EditorState, group: Layer, modifier: Modifier, onDismiss: () -> Unit) {
    Box(modifier) {
        DropdownMenu(true, onDismiss, Modifier.width(282.dp).heightIn(max = 350.dp), containerColor = Am.Field) {
            for (child in group.nested?.layers.orEmpty().asReversed()) key(child.id) {
                GroupMember(state, child, listOf(group.id), onDismiss)
            }
        }
    }
}

@Composable
private fun GroupMember(state: EditorState, layer: Layer, groups: List<Long>, onDismiss: () -> Unit) {
    var expanded by remember(groups, layer.id) { mutableStateOf(false) }
    val indent = ((groups.size - 1).coerceAtMost(3) * 10).dp
    val clocks = groupClocks(state.scene, groups) ?: return
    val owner = state.scene.focused(groups) ?: return
    val canEdit = !state.lookOnly && !layer.locked && clocks.none { it.first.locked }
    Row(Modifier.padding(start = indent, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        DropdownMenuItem(
            text = { Text(layer.shownName(), color = Am.OnDark, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { expanded = !expanded },
            modifier = Modifier.weight(1f),
            leadingIcon = { PanelIcon(if (expanded) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, Am.OnDark, size = 16.dp) },
        )
        IconButton(onClick = { editTimelineLayer(state, groups, layer.id) { it.withHidden(!it.hidden) } }, enabled = canEdit) {
            val label = stringResource(if (layer.hidden) R.string.a11y_show_layer else R.string.a11y_hide_layer)
            PanelIcon(if (layer.hidden) TimelineIcons.EyeOff else TimelineIcons.Eye, if (canEdit) Am.OnDark else Am.OnDarkDim, size = 18.dp, description = label)
        }
    }
    if (!expanded) return
    val at = state.sampleMs
    val local = localTimelineTime(at, clocks, insideGroups = true) ?: return
    val member = owner.layerAt(layer.id, owner.frameTime(owner.frameAt(local))) ?: return
    val inRange = local in member.startTime..member.endTime
    if (member.kind != "audio") {
        val axes = if (member.uses3D) "XYZ" else "XY"
        val transform = Modifier.padding(start = indent + 20.dp)
        val editable = canEdit && inRange
        GroupValue(state, groups, member.id, at, R.string.panel_position, member.location.copyOf(axes.length), axes, editable, transform, onDismiss) { item, v ->
            item.withTransform("location", v[0], v[1], v.getOrNull(2) ?: item.location[2])
        }
        GroupValue(state, groups, member.id, at, R.string.a11y_tab_rotate, floatArrayOf(member.rotation), axes, editable, transform, onDismiss) { item, v -> item.withRotation(v[0]) }
        GroupValue(state, groups, member.id, at, R.string.panel_size, member.scale, axes, editable, transform, onDismiss) { item, v -> item.withTransform("scale", v[0], v[1]) }
    }
    if (layer.isPlainGroup) {
        val path = groups + layer.id
        for (child in layer.nested?.layers.orEmpty().asReversed()) key(child.id) { GroupMember(state, child, path, onDismiss) }
    }
}

@Composable
private fun GroupValue(
    state: EditorState, groups: List<Long>, id: Long, time: Int, name: Int, numbers: FloatArray, axes: String,
    enabled: Boolean, modifier: Modifier, onDismiss: () -> Unit, change: (Layer, List<Float>) -> Layer,
) {
    val label = stringResource(name)
    DropdownMenuItem(text = { Text(label, color = if (enabled) Am.OnDark else Am.OnDarkDim) }, enabled = enabled, modifier = modifier, onClick = {
        val fields = numbers.mapIndexed { i, number -> KeypadField(if (numbers.size == 1) label else axes[i].toString(), number) }
        onDismiss()
        state.keypad = KeypadRequest(fields, 0) { values -> editTimelineLayer(state, groups, id, time) { change(it, values) } }
    })
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
    state: EditorState, groups: List<Long>, id: Long, time: Int = state.sampleMs, change: (Layer) -> Layer,
) {
    if (state.lookOnly) return
    state.edit { scene ->
        val clocks = groupClocks(scene, groups)?.takeUnless { it.any { (group, _) -> group.locked } } ?: return@edit scene
        val owner = scene.focused(groups) ?: return@edit scene
        val local = localTimelineTime(time, clocks) ?: return@edit scene
        val at = owner.frameTime(owner.frameAt(local))
        val layer = owner.layerAt(id, at)?.takeUnless { it.locked } ?: return@edit scene
        val next = change(layer)
        if (next.node == layer.node) scene else scene.withFocused(groups, owner.update(id, at) { next })
    }
}
