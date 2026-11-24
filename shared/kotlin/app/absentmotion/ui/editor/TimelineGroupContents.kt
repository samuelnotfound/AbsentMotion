package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.InlineSection
import app.absentmotion.editor.InlineTrack
import app.absentmotion.editor.KeypadField
import app.absentmotion.editor.KeypadRequest
import app.absentmotion.editor.focused
import app.absentmotion.editor.withFocused
import app.absentmotion.editor.inlineTrackSections
import app.absentmotion.editor.isPlainGroup
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.model.Layer
import app.absentmotion.model.LayerTrack
import app.absentmotion.model.SpeedRef
import app.absentmotion.model.effects
import app.absentmotion.model.formatFloats
import app.absentmotion.model.PropRef
import app.absentmotion.model.Scene
import app.absentmotion.model.nestedHostTime
import app.absentmotion.model.shownName
import app.absentmotion.ui.editor.panels.PanelIcon
import app.absentmotion.ui.theme.Am
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val GroupRowHeight = 28.dp

internal class TimelineGroupRow(
    val layer: Layer, val owner: Scene, val groups: List<Long>, private val clocks: List<Pair<Layer, Int>>,
    val details: List<InlineSection>? = null, val add: Boolean = false,
) {
    val path get() = groups + layer.id
    val height get() = GroupRowHeight + (details?.let(::inlineDetailsHeight) ?: 0.dp)
    val parentsUnlocked get() = clocks.none { it.first.locked }
    val editable get() = !layer.locked && parentsUnlocked

    private val track = LayerTrack(layer, owner, clocks)

    fun localTime(hostMs: Int): Int = track.timeAt(hostMs.toDouble(), activeOnly = false, quantize = false)?.ms?.roundToInt() ?: hostMs

    fun visibleRange(): IntRange {
        var start = layer.startTime.toDouble()
        var end = layer.endTime.toDouble()
        for ((group, fps) in clocks.asReversed()) {
            val a = group.nestedHostTime(start, fps)
            val b = group.nestedHostTime(end, fps)
            start = maxOf(group.startTime.toDouble(), minOf(a, b))
            end = minOf(group.endTime.toDouble(), maxOf(a, b))
            if (start > end) return IntRange.EMPTY
        }
        return ceil(start).toInt()..floor(end).toInt()
    }

    fun hostTime(localMs: Int): Double? {
        var time = localMs.toDouble()
        for ((group, fps) in clocks.asReversed()) {
            time = group.nestedHostTime(time, fps)
            if (time < group.startTime || time > group.endTime) return null
        }
        return time.takeIf { it.isFinite() }
    }
}

internal fun timelineGroupRows(
    host: Scene, group: Layer, catalog: EffectCatalog,
    effectExpanded: (List<Long>, String) -> Boolean = { _, _ -> false }, expanded: (List<Long>) -> Boolean,
): List<TimelineGroupRow> = buildList {
    fun visit(parent: Layer, owner: Scene, path: List<Long>, clocks: List<Pair<Layer, Int>>) {
        val child = parent.nested ?: return
        val childPath = path + parent.id
        val childClocks = clocks + (parent to owner.fps)
        add(TimelineGroupRow(parent, owner, path, clocks, add = true))
        for (layer in child.layers.asReversed()) {
            val path = childPath + layer.id
            val open = expanded(path)
            val details = if (open) {
                inlineTrackSections(layer, catalog, effectControls = true) { effectExpanded(path, it) }
                    .takeIf { it.isNotEmpty() || !layer.isPlainGroup }
            } else null
            add(TimelineGroupRow(layer, child, childPath, childClocks, details))
            if (open && layer.isPlainGroup) visit(layer, child, childPath, childClocks)
        }
    }
    if (group.isPlainGroup) visit(group, host, emptyList(), emptyList())
}

@Composable
internal fun TimelineGroupContents(
    state: EditorState, rows: List<TimelineGroupRow>, selection: InlineKeySelection,
    xAt: (Double) -> Float, isExpanded: (List<Long>) -> Boolean, onExpand: (List<Long>) -> Unit,
    onOpen: (TimelineGroupRow, PropRef?) -> Unit, onAdd: (TimelineGroupRow) -> Unit,
    onToggleEffect: (List<Long>, InlineSection) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        for (row in rows) key(row.path, row.add) {
            val indent = (row.groups.size.coerceAtMost(4) * 14 + if (row.add) 28 else 8).dp
            if (row.add) {
                if (row.editable && !state.lookOnly) {
                    val add = stringResource(R.string.add_layer)
                    Row(Modifier.height(GroupRowHeight).padding(start = indent).inlineTap { onAdd(row) },
                        verticalAlignment = Alignment.CenterVertically) {
                        PanelIcon(ChromeIcons.Plus, Am.Accent, size = 15.dp)
                        Text(add, Modifier.padding(start = 6.dp, end = 10.dp), color = Am.Accent, fontSize = 11.sp)
                    }
                } else Box(Modifier.height(GroupRowHeight))
            } else {
                val layer = row.layer
                Box(Modifier.fillMaxWidth().height(GroupRowHeight).background(Am.TimelineBg.copy(alpha = 0.65f))) {
                    Canvas(Modifier.matchParentSize()) {
                        val range = row.visibleRange()
                        if (range.isEmpty()) return@Canvas
                        val left = xAt(range.first.toDouble()).coerceIn(0f, size.width)
                        val right = xAt(range.last + 1.0).coerceIn(0f, size.width)
                        val height = 14.dp.toPx()
                        if (right > left) drawRoundRect(if (layer.isPlainGroup) Color(0xFFE88C3B) else Am.LayerBar,
                            Offset(left, (size.height - height) / 2), Size(right - left, height), CornerRadius(2.dp.toPx()), alpha = if (layer.hidden) 0.27f else 0.58f)
                    }
                    Row(Modifier.width(if (isExpanded(row.path)) 210.dp else 166.dp).fillMaxHeight().background(Am.TimelineBg.copy(alpha = 0.88f)).padding(start = indent),
                        verticalAlignment = Alignment.CenterVertically) {
                        val open = isExpanded(row.path)
                        Box(Modifier.size(26.dp).inlineTap { onExpand(row.path) }, contentAlignment = Alignment.Center) {
                            PanelIcon(if (open) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight,
                                if (open) Am.Accent else Am.OnDarkDim, size = 14.dp)
                        }
                        if (open) {
                            val lockLabel = stringResource(if (layer.node["lock"] == null) R.string.layer_lock else R.string.layer_unlock)
                            val lockAction = if (!state.lookOnly && row.parentsUnlocked) Modifier.inlineTap {
                                state.editGroupMember(row) { it.withLock(if (it.node["lock"] == null) "all" else null) }
                            } else Modifier
                            Box(Modifier.size(22.dp).then(lockAction), contentAlignment = Alignment.Center) {
                                PanelIcon(if (layer.locked || layer.timingLocked) TimelineIcons.Lock else TimelineIcons.LockOpen,
                                    Am.OnDarkDim, size = 13.dp, description = lockLabel)
                            }
                            val dimension = stringResource(R.string.three_d_layer)
                            val toggle3d = if (!state.lookOnly && row.editable && layer.canToggle3D) Modifier.inlineTap {
                                state.editGroupMember(row) { it.copy(node = it.node.with("threeD", if (it.uses3D) null else "true")) }
                            } else Modifier
                            Box(Modifier.size(22.dp).then(toggle3d), contentAlignment = Alignment.Center) {
                                PanelIcon(ChromeIcons.ObjectTab, if (layer.uses3D) Color(0xFFFF8F66) else Am.OnDarkDim,
                                    size = 14.dp, description = dimension)
                            }
                        }
                        LayerThumbnail(layer, state.engine, Modifier.size(15.dp))
                        Text(layer.shownName(), Modifier.weight(1f).then(if (row.editable && !state.lookOnly) Modifier.inlineTap { onOpen(row, null) } else Modifier)
                            .padding(start = 5.dp, end = 5.dp), color = if (layer.hidden) Am.OnDarkDim else Am.OnDark,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                row.details?.let { sections ->
                    InlinePositionDetails(state, layer, sections, Modifier.fillMaxWidth(),
                        xOf = { frame -> row.hostTime(row.owner.frameStart(frame))?.let(xAt) ?: -Float.MAX_VALUE },
                        selection = selection, scene = row.owner, sampleMs = row.localTime(state.sampleMs),
                        nested = true, editable = row.editable, onOpen = { onOpen(row, it) },
                        onToggleEffect = { onToggleEffect(row.path, it) },
                        onEditValue = { track, value ->
                            if (track.ref == SpeedRef) onOpen(row, track.ref)
                            else state.editGroupValue(row, track, value)
                        })
                }
            }
        }
    }
}

private fun EditorState.editGroupMember(row: TimelineGroupRow, change: (Layer) -> Layer) {
    val local = row.localTime(sampleMs).coerceIn(row.layer.startTime, row.layer.endTime)
    edit { project ->
        val owner = project.focused(row.groups) ?: return@edit project
        val next = owner.update(row.layer.id, local, change)
        project.withFocused(row.groups, next)
    }
}

private fun EditorState.editGroupValue(row: TimelineGroupRow, track: InlineTrack, value: Float) {
    if (!row.editable || lookOnly) return
    val ref = track.ref
    val instance = row.layer.effects.getOrNull(ref.effectIndex)
    val parameter = instance?.get("id")?.let(engine.effects.value::find)?.params?.firstOrNull { it.id == ref.name }
    val min = parameter?.min?.times(track.multiplier)?.takeIf(Float::isFinite) ?: -Float.MAX_VALUE
    val max = parameter?.max?.times(track.multiplier)?.takeIf(Float::isFinite) ?: Float.MAX_VALUE
    if (max < min) return
    playing = false
    keypad = KeypadRequest(listOf(KeypadField(track.label, value, track.unit, min, max)), 0) { values ->
        val exact = (values.single().coerceIn(min, max) / track.multiplier)
        val raw = formatFloats(floatArrayOf(if (track.integer) exact.roundToInt().toFloat() else exact))
        editGroupMember(row) { it.withValue(ref, raw) }
    }
}
