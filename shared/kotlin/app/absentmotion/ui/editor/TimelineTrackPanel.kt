package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.KeypadField
import app.absentmotion.editor.KeypadRequest
import app.absentmotion.editor.TimelineTrack
import app.absentmotion.editor.TrackKey
import app.absentmotion.editor.TrackSection
import app.absentmotion.editor.editTimelineTrack
import app.absentmotion.editor.keyFrames
import app.absentmotion.editor.timelineMotionBlur
import app.absentmotion.editor.withTimelineKeyDelta
import app.absentmotion.editor.withoutTimelineKeys
import app.absentmotion.model.Layer
import app.absentmotion.ui.editor.panels.PanelIcon
import app.absentmotion.ui.editor.panels.formatFixed
import app.absentmotion.ui.theme.Am
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal val LayerSwitchSize = 44.dp
internal val TrackRowHeight = 88.dp
internal val TrackSectionHeight = 44.dp

internal class TimelineTrackPanelState(private val state: EditorState) {
    private val key get() = (listOf(state.projectId) + state.groupPath.map(Long::toString)).joinToString("/")
    private var expansions by mutableStateOf<Map<String, Set<Long>>>(emptyMap())
    private var sections by mutableStateOf<Set<Triple<String, Long, Int>>>(emptySet())
    val expanded: Set<Long> get() = expansions[key] ?: state.settings.expandedTimelineRows(key)
    var animatedOnly by mutableStateOf(false)
    var selectedKeys by mutableStateOf<Set<TrackKey>>(emptySet())
    var extending by mutableStateOf(false)

    fun expand(rows: Set<Long>) {
        if (rows == expanded) return
        val scene = key
        expansions = expansions + (scene to rows)
        state.settings.rememberTimelineRows(scene, rows)
    }

    fun toggle(id: Long) = expand(if (id in expanded) expanded - id else expanded + id)

    fun sectionOpen(id: Long, i: Int) = Triple(key, id, i) !in sections

    fun toggleSection(id: Long, i: Int) {
        val section = Triple(key, id, i)
        sections = if (section in sections) sections - section else sections + section
    }

    fun select(key: TrackKey) {
        selectedKeys = if (extending) {
            if (key in selectedKeys) selectedKeys - key else selectedKeys + key
        } else setOf(key)
    }

    fun deleteSelection() {
        state.playing = false
        state.edit { it.withoutTimelineKeys(selectedKeys, state.sampleMs) }
        selectedKeys = emptySet()
        extending = false
    }
}

internal sealed interface DetailTrackRow {
    data class Section(val index: Int, val title: String, val expanded: Boolean) : DetailTrackRow
    data class Property(val track: TimelineTrack) : DetailTrackRow
}

internal fun trackRows(layer: Layer, sections: List<TrackSection>, panel: TimelineTrackPanelState): List<DetailTrackRow> = buildList {
    sections.forEachIndexed { index, section ->
        val tracks = if (panel.animatedOnly) section.tracks.filter { it.keys(layer).isNotEmpty() } else section.tracks
        if (tracks.isEmpty()) return@forEachIndexed
        val expanded = panel.sectionOpen(layer.id, index)
        add(DetailTrackRow.Section(index, section.name, expanded))
        if (expanded) tracks.forEach { add(DetailTrackRow.Property(it)) }
    }
}

@Composable
internal fun TimelineTrackTools(state: EditorState, panel: TimelineTrackPanelState, modifier: Modifier) {
    Row(modifier.height(44.dp).background(Am.Panel), verticalAlignment = Alignment.CenterVertically) {
        val expand = state.scene.layers.any { it.kind != "audio" && it.id !in panel.expanded }
        TrackCommand(stringResource(if (expand) R.string.timeline_tracks_expand_all else R.string.timeline_tracks_collapse_all), Modifier.weight(1f), enabled = !state.lookOnly) {
            panel.expand(if (expand) state.scene.layers.filter { it.kind != "audio" }.map { it.id }.toSet() else emptySet())
        }
        TrackCommand(stringResource(R.string.timeline_tracks_animated), Modifier.weight(1f), active = panel.animatedOnly) {
            panel.animatedOnly = !panel.animatedOnly
        }
        if (panel.selectedKeys.isNotEmpty()) {
            TrackCommand(stringResource(R.string.delete), Modifier.weight(0.75f), enabled = !state.lookOnly, onClick = panel::deleteSelection)
        } else {
            TrackCommand(stringResource(R.string.timeline_tracks_shutter), Modifier.weight(0.75f), enabled = !state.lookOnly) { editTimelineShutter(state) }
        }
    }
}

private fun editTimelineShutter(state: EditorState) {
    val node = state.scene.node
    val fields = listOf(
        KeypadField(app.absentmotion.i18n.AppText.get(R.string.timeline_tracks_shutter_angle), node["layerShutterAngle"]?.toFloatOrNull() ?: 180f, "°", 0f, 360f),
        KeypadField(app.absentmotion.i18n.AppText.get(R.string.timeline_tracks_shutter_phase), node["layerShutterPhase"]?.toFloatOrNull() ?: -90f, "°", -360f, 360f),
        KeypadField(app.absentmotion.i18n.AppText.get(R.string.timeline_tracks_shutter_preview), node["layerShutterPreview"]?.toFloatOrNull() ?: 8f, min = 2f, max = 32f),
        KeypadField(app.absentmotion.i18n.AppText.get(R.string.timeline_tracks_shutter_export), node["layerShutterExport"]?.toFloatOrNull() ?: 24f, min = 2f, max = 32f),
    )
    state.keypad = KeypadRequest(fields, 0) { v ->
        state.edit { app.absentmotion.model.Scene(it.node.with("layerShutterAngle", v[0].roundToInt().toString()).with("layerShutterPhase", v[1].roundToInt().toString())
            .with("layerShutterPreview", v[2].roundToInt().toString()).with("layerShutterExport", v[3].roundToInt().toString())) }
    }
}

@Composable
private fun TrackCommand(label: String, modifier: Modifier, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 4.dp), Alignment.Center) {
        Text(label, color = if (!enabled) Am.OnDarkDim else if (active) Am.Accent else Am.OnDark, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
    }
}

@Composable
internal fun TimelineLayerControls(state: EditorState, layer: Layer, panel: TimelineTrackPanelState) {
    val opened = layer.id in panel.expanded
    val mb = layer.kind !in setOf("audio", "light", "camera", "nullobj")
    Row(Modifier.background(Am.Panel)) {
        LayerSwitch(if (layer.hidden) TimelineIcons.EyeOff else TimelineIcons.Eye, null, stringResource(if (layer.hidden) R.string.a11y_show_layer else R.string.a11y_hide_layer), !layer.hidden, !state.lookOnly)
        LayerSwitch(if (layer.locked) TimelineIcons.Lock else if (layer.timingLocked) TimelineIcons.LockTiming else TimelineIcons.LockOpen, null, stringResource(if (layer.node["lock"] == null) R.string.layer_lock else R.string.layer_unlock), layer.node["lock"] != null, !state.lookOnly)
        LayerSwitch(null, "MB", stringResource(R.string.export_motion_blur), layer.timelineMotionBlur, mb && !layer.locked && !state.lookOnly)
        LayerSwitch(null, "3D", stringResource(R.string.three_d_layer), layer.uses3D, layer.canToggle3D && !layer.locked && !state.lookOnly)
        LayerSwitch(if (opened) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, null, stringResource(if (opened) R.string.timeline_tracks_collapse else R.string.timeline_tracks_expand), opened, !state.lookOnly)
    }
}

@Composable
private fun LayerSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String?, label: String, active: Boolean, enabled: Boolean) {
    Box(Modifier.size(LayerSwitchSize).semantics { contentDescription = label }, Alignment.Center) {
        val color = if (!enabled) Am.OnDarkDim else if (active) Am.Accent else Am.OnDark
        if (icon != null) PanelIcon(icon, color, size = 18.dp)
        else if (text != null) Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
internal fun TrackHeading(row: DetailTrackRow.Section, layer: Layer, panel: TimelineTrackPanelState, modifier: Modifier) {
    Row(modifier.height(TrackSectionHeight).background(Am.Panel).clickable(role = Role.Button) { panel.toggleSection(layer.id, row.index) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelIcon(if (row.expanded) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, Am.OnDark, size = 15.dp)
        Text(row.title, color = Am.OnDark, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
    }
}

@Composable
internal fun TrackControls(state: EditorState, layer: Layer, track: TimelineTrack, modifier: Modifier) {
    var confirming by remember(layer.id, track) { mutableStateOf(false) }
    val scene = state.scene
    val now = state.sampleMs
    val at = layer.at(now, scene.fps)
    val keys = track.keyFrames(scene, layer)
    val onKey = state.frame in keys
    val enabled = !state.lookOnly && !layer.locked && !track.linked(at) && now in layer.startTime..layer.endTime
    val keyEnabled = enabled && !layer.timingLocked
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, text = { Text(stringResource(R.string.timeline_tracks_remove_keys)) },
        confirmButton = { TextButton(onClick = { confirming = false; state.editTimelineTrack(layer.id, now, change = track::stopAnimating) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } })
    Column(modifier.height(TrackRowHeight).background(Am.PanelInset).padding(horizontal = 3.dp)) {
        Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackButton(PanelIcons.Speed, stringResource(R.string.timeline_tracks_animate), keyEnabled, keys.isNotEmpty()) {
                if (keys.isEmpty()) state.editTimelineTrack(layer.id, now, change = track::toggleKey) else confirming = true
            }
            Text(track.label, Modifier.weight(1f), color = Am.OnDark, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatFixed(track.value(at), if (track.discrete) 0 else 2) + track.unit, color = Am.OnDarkDim, fontSize = 10.sp, maxLines = 1)
        }
        Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackRing(state, layer.id, now, track, track.value(at), enabled, Modifier.weight(1f))
            val previous = keys.lastOrNull { it < state.frame }
            val next = keys.firstOrNull { it > state.frame }
            TrackButton(ChromeIcons.SkipBack, stringResource(R.string.desk_key_previous_keyframe), previous != null) { previous?.let { state.playing = false; state.frame = it } }
            TrackButton(if (onKey) PanelIcons.KeyframeRemove else PanelIcons.Keyframe, stringResource(if (onKey) R.string.a11y_remove_keyframe else R.string.a11y_add_keyframe), keyEnabled, onKey) {
                state.editTimelineTrack(layer.id, now, change = track::toggleKey)
            }
            TrackButton(ChromeIcons.SkipForward, stringResource(R.string.desk_key_next_keyframe), next != null) { next?.let { state.playing = false; state.frame = it } }
        }
    }
}

@Composable
private fun TrackButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, enabled: Boolean, active: Boolean = false, action: () -> Unit) {
    Box(Modifier.size(38.dp, 44.dp).semantics { contentDescription = title }.clickable(enabled = enabled, role = Role.Button, onClick = action), Alignment.Center) {
        PanelIcon(icon, if (!enabled) Am.OnDarkDim else if (active) Am.Accent else Am.OnDark, size = 16.dp)
    }
}

@Composable
private fun TrackRing(state: EditorState, id: Long, sampleMs: Int, track: TimelineTrack, value: Float, enabled: Boolean, modifier: Modifier) {
    var offset by remember(id, track) { mutableFloatStateOf(0f) }
    var fine by remember(id, track) { mutableStateOf<Int?>(null) }
    val currentValue by rememberUpdatedState(value)
    val currentTime by rememberUpdatedState(sampleMs)
    val tick = rememberHaptic(state)
    val up = stringResource(R.string.timeline_tracks_increase)
    val down = stringResource(R.string.timeline_tracks_decrease)
    val tier = listOf(100, 50, 10, 1)
    val green = Color(0xFF63D995)
    Box(modifier.height(32.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(5.dp)).background(Am.Well)
        .semantics {
            contentDescription = track.label
            if (enabled) customActions = listOf(1 to up, -1 to down).map { (sign, title) ->
                CustomAccessibilityAction(title) {
                    state.editTimelineTrack(id, currentTime) { track.change(it, currentValue + sign * maxOf(track.sensitivity() * 8f, if (track.discrete) 1f else 0.01f)) }
                    true
                }
            }
        }.then(if (enabled) Modifier.pointerInput(id, track) {
            detectTapGestures(onDoubleTap = { state.editTimelineTrack(id, currentTime, change = track::reset) })
        }.pointerInput(id, track) {
            var next = 0f
            var target = 0
            var lastTick = 0L
            var lastEvent = 0L
            var startY = 0f
            val key = Any()
            detectDragGestures(
                onDragStart = { at -> state.playing = false; state.fastTrackPreview = true; target = currentTime; next = currentValue; startY = at.y; fine = 0 },
                onDragCancel = { state.endGesture(); state.fastTrackPreview = false; fine = null },
                onDragEnd = { state.endGesture(); state.fastTrackPreview = false; fine = null },
            ) { change, drag ->
                change.consume()
                val index = (abs(change.position.y - startY) / 26.dp.toPx()).toInt().coerceIn(0, tier.lastIndex)
                fine = index
                val previous = track.clamp(next)
                val time = change.uptimeMillis
                val elapsed = if (lastEvent == 0L) 16L else (time - lastEvent).coerceAtLeast(8)
                lastEvent = time
                val gain = 1 + 3 * (abs(drag.x) / density / (elapsed * 1.1f)).coerceIn(0f, 1f)
                val step = drag.x / density * track.sensitivity() * gain * tier[index] / 100f
                next = (next + step).coerceIn(track.lower, track.upper)
                offset += drag.x
                val result = track.clamp(next)
                if (floor(result) != floor(previous) && time - lastTick >= 34) {
                    tick(if (result == track.lower || result == track.upper) HapticFeedbackType.SegmentTick else HapticFeedbackType.SegmentFrequentTick)
                    lastTick = time
                }
                state.editTimelineTrack(id, target, key) { track.change(it, result) }
            }
        } else Modifier), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Am.Well, Am.Panel, Am.Well)))
            val spacing = 4.dp.toPx()
            var x = (offset % spacing + spacing) % spacing
            while (x <= size.width) {
                drawLine(Am.Line, Offset(x, 2.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), 1.dp.toPx())
                x += spacing
            }
            val mid = center
            val radius = 7.dp.toPx()
            val color = green.copy(alpha = if (fine != null) 1f else 0.62f)
            for (direction in listOf(-1, 1)) {
                val tip = mid + Offset(direction * radius, 0f)
                drawLine(color, mid, tip, 1.3.dp.toPx(), StrokeCap.Round)
                drawLine(color, tip, tip + Offset(-direction * radius * 0.5f, -radius * 0.5f), 1.3.dp.toPx(), StrokeCap.Round)
                drawLine(color, tip, tip + Offset(-direction * radius * 0.5f, radius * 0.5f), 1.3.dp.toPx(), StrokeCap.Round)
            }
        }
        fine?.let { Text("${tier[it]}%", Modifier.align(Alignment.BottomEnd).background(Am.Panel).padding(horizontal = 2.dp), color = Am.OnDark, fontSize = 8.sp) }
    }
}

@Composable
internal fun TrackDiamonds(state: EditorState, layer: Layer, track: TimelineTrack, panel: TimelineTrackPanelState,
                           modifier: Modifier, xOf: (Int) -> Float, frameAt: (Float) -> Int, otherKeys: () -> List<Int>) {
    val latched by rememberUpdatedState(layer)
    val x by rememberUpdatedState(xOf)
    val at by rememberUpdatedState(frameAt)
    val nearby by rememberUpdatedState(otherKeys)
    val tick = rememberHaptic(state)
    val scene = state.scene
    val enabled = !state.lookOnly && !layer.locked && !layer.timingLocked && !track.linked(layer)
    val gestures = if (enabled) Modifier.pointerInput(layer.id, track) {
        fun hit(pos: Offset) = track.keyFrames(state.scene, latched).minByOrNull { abs(x(it) - pos.x) }
            ?.takeIf { abs(x(it) - pos.x) <= 22.dp.toPx() && abs(pos.y - size.height / 2) <= 22.dp.toPx() }
        detectTapGestures(onLongPress = { pos -> hit(pos)?.let { frame ->
            panel.extending = true
            panel.selectedKeys = panel.selectedKeys + TrackKey(layer.id, track, frame)
        } }, onTap = { pos -> hit(pos)?.let { frame ->
            val key = TrackKey(layer.id, track, frame)
            panel.select(key)
            state.playing = false
            state.frame = frame
        } })
    }.pointerInput(layer.id, track) {
        var base = state.scene
        var start = 0
        var origin = 0f
        var marked = emptySet<TrackKey>()
        var delta = 0
        var snap: Int? = null
        val undo = Any()
        val done = {
            if (marked.isNotEmpty()) panel.selectedKeys = marked.map { it.copy(frame = it.frame + delta) }.toSet()
            state.endGesture()
            marked = emptySet()
        }
        detectDragGestures(onDragStart = { pos ->
            val key = track.keyFrames(state.scene, latched).minByOrNull { abs(x(it) - pos.x) }
                ?.takeIf { abs(x(it) - pos.x) <= 22.dp.toPx() && abs(pos.y - size.height / 2) <= 22.dp.toPx() }
            if (key != null) {
                state.playing = false
                base = state.scene
                start = key
                origin = pos.x
                val picked = TrackKey(layer.id, track, key)
                marked = panel.selectedKeys.takeIf { picked in it } ?: setOf(picked)
                delta = 0
                snap = null
            }
        }, onDragEnd = done, onDragCancel = done) { change, _ ->
            if (marked.isNotEmpty()) {
                change.consume()
                val desired = x(start) + change.position.x - origin
                val candidate = (nearby() + state.frame).minByOrNull { abs(x(it) - desired) }?.takeIf { abs(x(it) - desired) <= 8.dp.toPx() }
                if (candidate != null && candidate != snap) tick(HapticFeedbackType.SegmentTick)
                snap = candidate
                val (next, move) = base.withTimelineKeyDelta(marked, (candidate ?: at(desired)) - start)
                delta = move
                state.edit(undo) { next }
                panel.selectedKeys = marked.map { it.copy(frame = it.frame + delta) }.toSet()
            }
        }
    } else Modifier
    Canvas(modifier.height(TrackRowHeight).background(Am.TimelineBg).then(gestures)) {
        val y = size.height * 0.5f
        val left = xOf(scene.firstShownFrame(layer.startTime)).coerceAtLeast(0f)
        val right = xOf(scene.lastShownFrame(layer.endTime)).coerceAtMost(size.width)
        if (right > left) drawLine(Am.Line, Offset(left, y), Offset(right, y))
        val current = xOf(state.frame)
        if (current in 0f..size.width) drawLine(Am.OnDarkDim.copy(alpha = 0.55f), Offset(current, 0f), Offset(current, size.height), 1.dp.toPx())
        val radius = 7.dp.toPx()
        for (frame in track.keyFrames(scene, layer)) {
            val centre = xOf(frame)
            if (centre !in -radius..size.width + radius) continue
            val key = TrackKey(layer.id, track, frame)
            val path = Path().apply {
                moveTo(centre - radius, y); lineTo(centre, y - radius); lineTo(centre + radius, y); lineTo(centre, y + radius); close()
            }
            drawPath(path, if (key in panel.selectedKeys) Am.Accent else if (layer.locked) Am.OnDarkDim else Am.OnDark)
        }
    }
}
