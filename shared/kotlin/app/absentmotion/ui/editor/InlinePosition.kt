package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorPanel
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.KeyTarget
import app.absentmotion.model.KeyScope
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.formatFloats
import app.absentmotion.model.withKeyframesMoved
import app.absentmotion.ui.editor.panels.PanelIcon
import app.absentmotion.ui.editor.panels.RulerSpinner
import app.absentmotion.ui.editor.panels.formatFixed
import app.absentmotion.ui.theme.Am
import kotlin.math.abs

internal val InlinePositionHeading = 25.dp
internal val InlinePositionAxisHeight = 26.dp
internal val InlinePositionControlHeight = 42.dp

internal data class InlinePositionOpen(val transform: Boolean = true, val axis: Int? = null) {
    val height get() = InlinePositionHeading + if (!transform) 0.dp else InlinePositionAxisHeight * 3 + if (axis == null) 0.dp else InlinePositionControlHeight
}

@Composable
internal fun InlinePositionDetails(
    state: EditorState, layer: Layer, open: InlinePositionOpen, modifier: Modifier,
    onChange: (InlinePositionOpen) -> Unit, xOf: (Int) -> Float, frameAt: (Float) -> Int,
) {
    val scene = state.scene
    val sample = state.sampleMs
    val at = layer.at(sample, scene.fps)
    val position = at.location
    val scope = KeyScope(PropRef.Location)
    val frames = layer.keyframeTimes(scope).map(scene::frameAt).distinct()
    val canEdit = !state.lookOnly && !layer.locked && sample in layer.startTime..layer.endTime
    val locationName = stringResource(R.string.panel_position)
    Column(modifier.background(Am.TimelineBg)) {
        Row(Modifier.height(InlinePositionHeading).fillMaxWidth().clickable(role = Role.Button) { onChange(open.copy(transform = !open.transform)) }
            .padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelIcon(if (open.transform) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, Am.OnDarkDim, size = 11.dp)
            Text(stringResource(R.string.layer_tool_transform), Modifier.padding(start = 5.dp), color = Am.OnDark, fontSize = 11.sp)
        }
        if (!open.transform) return@Column
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelWidth = minOf(154.dp, maxWidth * 0.50f)
            val inset = with(LocalDensity.current) { labelWidth.toPx() }
            Column {
                for (axis in 0..2) {
                    val able = axis != 2 || layer.uses3D
                    val expanded = open.axis == axis && able
                    val tint = if (able) Am.OnDark else Am.OnDarkDim
                    Row(Modifier.fillMaxWidth().height(InlinePositionAxisHeight).background(if (expanded) Am.Field else Am.TimelineBg),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(labelWidth).fillMaxHeight().clickable(enabled = able, role = Role.Button) {
                            onChange(open.copy(axis = if (expanded) null else axis))
                        }.padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PanelIcon(if (expanded) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, tint, size = 10.dp)
                            Text("${"XYZ"[axis]} $locationName", Modifier.padding(start = 4.dp).weight(1f),
                                color = tint, maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis)
                            Text(formatFixed(position[axis], 1), color = if (able) Am.Accent else Am.OnDarkDim, fontSize = 10.5.sp, maxLines = 1)
                        }
                        if (able) InlinePositionKeys(state, layer, frames, canEdit && !layer.timingLocked, Modifier.weight(1f),
                            xOf = { xOf(it) - inset }, frameAt = { frameAt(it + inset) })
                    }
                    if (expanded) {
                        val key = "inline-position-${layer.id}-$axis"
                        Row(Modifier.height(InlinePositionControlHeight).fillMaxWidth().padding(start = 21.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RulerSpinner(Am.Accent, onDrag = { delta ->
                                state.edit(key) { active -> active.update(layer.id, state.sampleMs) { current ->
                                    if (current.locked) current else current.location.copyOf().let {
                                        it[axis] += delta
                                        current.withTransform("location", *it)
                                    }
                                } }
                            }, onEnd = state::endGesture, onStart = { state.playing = false },
                                modifier = Modifier.weight(1f).height(InlinePositionControlHeight), enabled = canEdit)
                            InlinePositionButton(
                                if (state.frame in frames) PanelIcons.KeyframeRemove else PanelIcons.Keyframe,
                                stringResource(if (state.frame in frames) R.string.a11y_remove_keyframe else R.string.a11y_add_keyframe),
                                canEdit && !layer.timingLocked,
                            ) {
                                state.playing = false
                                state.edit { it.update(layer.id, state.sampleMs) { current ->
                                    current.withKeyframeToggled(PropRef.Location, formatFloats(current.location))
                                } }
                            }
                            InlinePositionButton(PanelIcons.Easing, stringResource(R.string.a11y_easing), canEdit) {
                                state.playing = false
                                state.selectedId = layer.id
                                state.panel = EditorPanel.MoveTransform
                                state.keyTarget = KeyTarget(PropRef.Location, "0.000000,0.000000,0.000000")
                                if (at.easing(PropRef.Location) != null) state.easingOpen = true else state.hintEasing(frames.size)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlinePositionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, enabled: Boolean, action: () -> Unit) {
    Box(Modifier.size(38.dp).semantics { contentDescription = title }.clickable(enabled = enabled, role = Role.Button, onClick = action), Alignment.Center) {
        PanelIcon(icon, if (enabled) Am.OnDark else Am.OnDarkDim, size = 17.dp)
    }
}

@Composable
private fun InlinePositionKeys(
    state: EditorState, layer: Layer, frames: List<Int>, editable: Boolean, modifier: Modifier,
    xOf: (Int) -> Float, frameAt: (Float) -> Int,
) {
    val currentFrames by rememberUpdatedState(frames)
    val position by rememberUpdatedState(xOf)
    val toFrame by rememberUpdatedState(frameAt)
    val gesture = if (editable) Modifier.pointerInput(layer.id) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val from = currentFrames.minByOrNull { abs(position(it) - down.position.x) }
                ?.takeIf { abs(position(it) - down.position.x) <= 9.dp.toPx() } ?: return@awaitEachGesture
            val base = state.scene.layer(layer.id) ?: return@awaitEachGesture
            val scope = KeyScope(PropRef.Location)
            val key = "inline-position-key-${layer.id}"
            var moved = false
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) break
                    pointer.consume()
                    val travel = pointer.position.x - down.position.x
                    if (!moved && abs(travel) <= viewConfiguration.touchSlop) continue
                    moved = true
                    state.playing = false
                    val frame = toFrame(position(from) + travel)
                    state.edit(key) { scene -> scene.update(layer.id) { base.withKeyframesMoved(from, frame, scene, scope) } }
                } while (true)
                if (!moved) { state.playing = false; state.frame = from }
            } finally {
                if (moved) state.endGesture()
            }
        }
    } else Modifier
    Canvas(modifier.fillMaxHeight().then(gesture)) {
        val y = center.y
        val playhead = xOf(state.frame)
        if (playhead in 0f..size.width) drawLine(Am.OnDarkDim, Offset(playhead, 0f), Offset(playhead, size.height), 1.dp.toPx())
        val radius = 4.5.dp.toPx()
        for (frame in frames) {
            val x = xOf(frame)
            if (x !in 0f..size.width) continue
            val diamond = Path().apply {
                moveTo(x - radius, y); lineTo(x, y - radius); lineTo(x + radius, y); lineTo(x, y + radius); close()
            }
            drawPath(diamond, if (state.frame == frame) Am.Accent else Am.OnDark)
        }
    }
}
