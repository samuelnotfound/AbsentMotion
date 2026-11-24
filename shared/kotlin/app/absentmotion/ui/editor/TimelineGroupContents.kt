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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.InlineKey
import app.absentmotion.editor.MultiSelect
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
import app.absentmotion.model.KeyScope
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
private val GroupIconWidth = 22.dp
private val GroupDisclosureWidth = 26.dp
private val GroupSidebarStart = 4.dp
private val GroupSidebarEnd = 2.dp

internal class TimelineGroupRow(
    val layer: Layer, val owner: Scene, val groups: List<Long>, private val clocks: List<Pair<Layer, Int>>,
    val details: List<InlineSection>? = null,
) {
    val path get() = groups + layer.id

    fun indent(state: EditorState): Dp = if (selected(state) && state.showsLayerInspector) 0.dp else (groups.size.coerceAtMost(4) * 14 + 8).dp
    fun selected(state: EditorState): Boolean = state.selectedId == layer.id && state.selectedGroups == groups
    fun sidebarWidth(state: EditorState, expanded: Boolean): Dp =
        indent(state) + GroupSidebarStart + GroupSidebarEnd + GroupDisclosureWidth + GroupIconWidth * (if (expanded || selected(state)) 5 else 2)
    fun thumbnailHit(state: EditorState, expanded: Boolean, x: Float, density: Density): Boolean = with(density) {
        val right = (sidebarWidth(state, expanded) - GroupDisclosureWidth - GroupSidebarEnd).toPx()
        x >= right - GroupIconWidth.toPx() && x < right
    }

    fun headerHeightPx(density: Density): Int = with(density) { GroupRowHeight.roundToPx() }
    fun heightPx(density: Density): Int = headerHeightPx(density) + with(density) { details?.let { inlineDetailsHeight(it) } ?: 0 }
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

    fun hostTime(localMs: Int): Double? = track.hostTime(localMs.toDouble())
}

internal fun timelineGroupRows(
    host: Scene, group: Layer, catalog: EffectCatalog,
    sectionExpanded: (List<Long>, String) -> Boolean = { _, id -> id == "transform" }, expanded: (List<Long>) -> Boolean,
): List<TimelineGroupRow> = buildList {
    fun visit(parent: Layer, owner: Scene, path: List<Long>, clocks: List<Pair<Layer, Int>>) {
        val child = parent.nested ?: return
        val childPath = path + parent.id
        val childClocks = clocks + (parent to owner.fps)
        for (layer in child.layers.asReversed()) {
            val path = childPath + layer.id
            val open = expanded(path)
            val details = if (open) {
                inlineTrackSections(layer, catalog, effectControls = true) { sectionExpanded(path, it) }
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
    onOpen: (TimelineGroupRow, PropRef?) -> Unit,
    onToggleSection: (List<Long>, InlineSection) -> Unit,
    drawClip: DrawScope.(Layer, Path, Float, Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        for (row in rows) key(row.path) {
            val layer = row.layer
            val open = isExpanded(row.path)
            val selected = row.selected(state)
            val checked = MultiSelect.active && row.groups == MultiSelect.groups && layer.id in MultiSelect.ids
            val indent = row.indent(state)
            val scope = state.keyScope.takeIf { selected && state.showsLayerInspector }
            val controls = open || selected
            val sidebarWidth = row.sidebarWidth(state, open)
            val openLayer: (() -> Unit)? = if (row.editable && !state.lookOnly) { { onOpen(row, null) } } else null
            val layerTap = if (openLayer == null) Modifier else Modifier.inlineTap(openLayer)
            Box(Modifier.fillMaxWidth().height(GroupRowHeight).background(if (selected || checked) Am.Field else Am.TimelineBg)) {
                GroupMemberClip(row, sidebarWidth, xAt, openLayer, drawClip, Modifier.matchParentSize(), selected, scope, state.sampleMs, selection)
                Row(Modifier.width(sidebarWidth).fillMaxHeight().padding(start = indent, top = 2.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(50)).background(if (checked) Am.Accent else Am.Button).padding(start = GroupSidebarStart, end = GroupSidebarEnd),
                    verticalAlignment = Alignment.CenterVertically) {
                    val visibility = stringResource(if (layer.hidden) R.string.a11y_show_layer else R.string.a11y_hide_layer)
                    val toggleVisibility = if (row.editable && !state.lookOnly) Modifier.inlineTap {
                        state.editGroupMember(row) { it.withHidden(!it.hidden) }
                    } else Modifier
                    Box(Modifier.size(GroupIconWidth).then(toggleVisibility), contentAlignment = Alignment.Center) {
                        PanelIcon(if (layer.hidden) TimelineIcons.EyeOff else TimelineIcons.Eye, Am.OnDarkDim,
                            size = 16.dp, description = visibility)
                    }
                    if (controls) {
                        val lockLabel = stringResource(if (layer.node["lock"] == null) R.string.layer_lock else R.string.layer_unlock)
                        val lockAction = if (!state.lookOnly && row.parentsUnlocked) Modifier.inlineTap {
                            state.editGroupMember(row) { it.withLock(if (it.node["lock"] == null) "all" else null) }
                        } else Modifier
                        Box(Modifier.size(GroupIconWidth).then(lockAction), contentAlignment = Alignment.Center) {
                            PanelIcon(if (layer.locked || layer.timingLocked) TimelineIcons.Lock else TimelineIcons.LockOpen,
                                Am.OnDarkDim, size = 13.dp, description = lockLabel)
                        }
                        val dimension = stringResource(R.string.three_d_layer)
                        val toggle3d = if (!state.lookOnly && row.editable && layer.canToggle3D) Modifier.inlineTap {
                            state.editGroupMember(row) { it.withThreeD(!it.uses3D) }
                        } else Modifier
                        Box(Modifier.size(GroupIconWidth).then(toggle3d), contentAlignment = Alignment.Center) {
                            PanelIcon(ChromeIcons.ObjectTab, if (layer.uses3D) Color(0xFFFF8F66) else Am.OnDarkDim,
                                size = 14.dp, description = dimension)
                        }
                        val cameraLabel = stringResource(if (layer.visibleInCamera) R.string.layer_hide_in_camera else R.string.layer_show_in_camera)
                        val toggleCamera = if (!state.lookOnly && row.editable && layer.canShowInCamera) Modifier.inlineTap {
                            state.editGroupMember(row) { it.withCameraVisibility(!it.visibleInCamera) }
                        } else Modifier
                        Box(Modifier.size(GroupIconWidth).then(toggleCamera), contentAlignment = Alignment.Center) {
                            val tint = if (layer.canShowInCamera && layer.visibleInCamera) Am.Accent else Am.OnDarkDim
                            PanelIcon(PanelIcons.Camera, tint.copy(alpha = if (layer.canShowInCamera) 1f else 0.38f), size = 14.dp, description = cameraLabel)
                        }
                    }
                    Box(
                        Modifier.size(GroupIconWidth).then(layerTap).semantics { contentDescription = layer.shownName() },
                        contentAlignment = Alignment.Center,
                    ) {
                        LayerThumbnail(layer, state.engine, Modifier.size(15.dp))
                    }
                    Box(Modifier.size(GroupDisclosureWidth).inlineTap { onExpand(row.path) }, contentAlignment = Alignment.Center) {
                        PanelIcon(if (open) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight,
                            if (open) Am.Accent else Am.OnDarkDim, size = 14.dp)
                    }
                }
            }
            row.details?.let { sections ->
                InlinePositionDetails(state, layer, sections, Modifier.fillMaxWidth(),
                    xOf = { frame -> row.hostTime(row.owner.frameStart(frame))?.let(xAt) ?: -Float.MAX_VALUE },
                    selection = selection, scene = row.owner, sampleMs = row.localTime(state.sampleMs),
                    groups = row.groups, editable = row.editable, onOpen = { onOpen(row, it) },
                    onToggleSection = { onToggleSection(row.path, it) },
                    onEditValue = { track, value ->
                        if (track.ref == SpeedRef) onOpen(row, track.ref)
                        else state.editGroupValue(row, track, value)
                    })
            }
        }
    }
}

@Composable
private fun GroupMemberClip(
    row: TimelineGroupRow, sidebarWidth: Dp, xAt: (Double) -> Float, onOpen: (() -> Unit)?,
    drawClip: DrawScope.(Layer, Path, Float, Float) -> Unit, modifier: Modifier,
    selected: Boolean, scope: KeyScope?, timeMs: Int, selection: InlineKeySelection,
) {
    val range = row.visibleRange()
    if (range.isEmpty()) return
    val layer = row.layer
    val outline = remember { Path() }
    fun left() = xAt(range.first.toDouble())
    fun right() = xAt(range.last + 1.0)
    val tap = if (onOpen == null) Modifier else Modifier.inlineTap(onOpen)
    Layout(
        modifier = modifier,
        content = {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight().clipToBounds().then(tap)
                .semantics { contentDescription = layer.shownName() }.alpha(if (layer.hidden) 0.45f else 1f)) {
                val origin = floor(maxOf(sidebarWidth.toPx(), left()))
                drawClip(layer, outline, left() - origin, right() - origin)
                if (selected) {
                    val active = if (scope == null) emptySet() else layer.keyframeTimes(scope).map(row.owner::frameAt).toSet()
                    val faint = layer.keyframeTimes().map(row.owner::frameAt).toSet() - active
                    val current = row.owner.frameAt(row.localTime(timeMs))
                    for ((frames, strong) in listOf(faint to false, active to true)) for (frame in frames) {
                        val x = row.hostTime(row.owner.frameStart(frame))?.let(xAt)?.minus(origin) ?: continue
                        val picked = scope?.ref?.let { InlineKey(layer.id, it, frame, row.groups) in selection.keys } == true
                        if (x in 0f..size.width) drawTimelineKeyAt(outline, x, center.y, strong, picked, frame == current)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val minimum = sidebarWidth.toPx().coerceAtMost(width.toFloat())
        val left = left().coerceIn(minimum, width.toFloat())
        val right = right().coerceIn(minimum, width.toFloat())
        if (left < right && height > 0) {
            val x = floor(left).toInt()
            val clip = measurables.single().measure(Constraints.fixed(ceil(right).toInt() - x, height))
            layout(width, height) { clip.place(x, 0) }
        } else {
            layout(width, height) {}
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
