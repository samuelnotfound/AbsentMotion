package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.InlineTrack
import app.absentmotion.editor.withInlineGraph
import app.absentmotion.editor.InlineSection
import app.absentmotion.model.KeyScope
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.SpeedRef
import app.absentmotion.model.FrameRef
import app.absentmotion.model.Scene
import app.absentmotion.editor.withFrameHold
import app.absentmotion.model.withSpeedKeyframeToggled
import app.absentmotion.model.valueAt
import app.absentmotion.model.formatFloats
import app.absentmotion.model.withKeyframesMoved
import app.absentmotion.ui.editor.panels.PanelIcon
import app.absentmotion.ui.editor.panels.RulerSpinner
import app.absentmotion.ui.editor.panels.formatFixed
import app.absentmotion.ui.theme.Am
import kotlin.math.abs

import kotlin.math.roundToInt

internal val InlinePositionHeading = 25.dp
internal val InlinePositionAxisHeight = 26.dp
internal val InlinePositionControlHeight = 42.dp

internal data class InlinePositionOpen(
    val collapsed: Set<String> = emptySet(),
    val controls: Set<PropRef> = emptySet(),
) {
    fun tracks(sections: List<InlineSection>): List<InlineTrack> = sections.filterNot { it.id in collapsed }.flatMap { it.tracks }

    fun height(sections: List<InlineSection>) = InlinePositionHeading * maxOf(1, sections.size) +
        tracks(sections).fold(0.dp) { height, track ->
            height + InlinePositionAxisHeight + if (track.ref in controls) InlinePositionControlHeight else 0.dp
        }
}

@Composable
internal fun InlinePositionDetails(
    state: EditorState, layer: Layer, open: InlinePositionOpen, sections: List<InlineSection>, modifier: Modifier,
    onChange: (InlinePositionOpen) -> Unit, xOf: (Int) -> Float, frameAt: (Float) -> Int,
) {
    val scene = state.scene
    val sample = state.sampleMs
    val at = layer.at(sample, scene.fps)
    val canEdit = !state.lookOnly && !layer.locked && sample in layer.startTime..layer.endTime
    BoxWithConstraints(modifier) {
        val labelWidth = minOf(168.dp, maxWidth * 0.56f)
        val valueWidth = minOf(58.dp, labelWidth * 0.39f)
        val labelPx = with(LocalDensity.current) { labelWidth.toPx() }
        Column(Modifier.fillMaxWidth()) {
            if (sections.isEmpty()) Row(Modifier.height(InlinePositionHeading).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inline_no_keyframes), color = Am.OnDarkDim, fontSize = 11.sp)
            }
            for (section in sections) {
                val shown = section.id !in open.collapsed
                val toggle = { onChange(open.copy(collapsed = if (shown) open.collapsed + section.id else open.collapsed - section.id)) }
                Row(Modifier.height(InlinePositionHeading).fillMaxWidth().inlineTap(toggle).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PanelIcon(if (shown) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, Am.OnDarkDim, size = 14.dp)
                    Text(section.label, Modifier.padding(start = 5.dp), color = Am.OnDark, fontSize = 11.sp)
                }
                if (!shown) continue
                for (track in section.tracks) androidx.compose.runtime.key(track.ref) {
                    val expanded = track.ref in open.controls
                    val toggleControl = { onChange(open.copy(controls = if (expanded) open.controls - track.ref else open.controls + track.ref)) }
                    val frames = track.frames(scene, layer)
                    val linked = track.linked(layer)
                    val raw = at.element(track.ref)?.valueAt(at.t)
                    val value = raw?.toFloatOrNull()?.times(track.multiplier)
                    val display = value?.let { formatFixed(it, if (track.integer) 0 else 1) + track.unit } ?: raw.orEmpty()
                    Box(Modifier.fillMaxWidth().height(InlinePositionAxisHeight)
                        .background((if (expanded) Am.Field else Am.TimelineBg).copy(alpha = 0.65f))) {
                        InlinePositionKeys(state, layer, track, frames, !state.lookOnly && !layer.locked && !layer.timingLocked && !linked,
                            Modifier.matchParentSize(), xOf, frameAt, labelPx, toggleControl)
                        Row(Modifier.width(labelWidth).fillMaxHeight().background(Am.TimelineBg.copy(alpha = 0.32f))
                            .semantics { onClick { toggleControl(); true } }.padding(start = 22.dp, end = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            PanelIcon(if (expanded) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight, Am.OnDarkDim, size = 14.dp)
                            Text(track.label, Modifier.padding(start = 4.dp).weight(1f), color = Am.OnDark, maxLines = 1,
                                fontSize = 11.sp, overflow = TextOverflow.Ellipsis)
                            Text(display, Modifier.width(valueWidth), color = Am.Accent, fontSize = 10.5.sp,
                                textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (expanded) InlineControls(state, layer, track, scene, frames, value, canEdit && !linked, labelWidth)
                }
            }
        }
    }
}

private fun Modifier.inlineTap(action: () -> Unit): Modifier = semantics { onClick { action(); true } }
    .pointerInput(action) {
        awaitEachGesture {
            awaitFirstDown()
            if (waitForUpOrCancellation() != null) action()
        }
    }

@Composable
private fun InlineControls(
    state: EditorState, layer: Layer, track: InlineTrack, scene: Scene, frames: List<Int>, value: Float?, canEdit: Boolean, labelWidth: Dp,
) {
    val ref = track.ref
    var pending by androidx.compose.runtime.remember(layer.id, ref) { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    Row(Modifier.height(InlinePositionControlHeight).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(labelWidth).fillMaxHeight().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val here = state.frame in frames
            InlinePositionButton(
                if (here) PanelIcons.KeyframeRemove else PanelIcons.Keyframe,
                stringResource(if (here) R.string.a11y_remove_keyframe else R.string.a11y_add_keyframe),
                canEdit && !layer.timingLocked,
            ) {
                state.playing = false
                state.edit { it.update(layer.id, state.sampleMs) { at ->
                    if (ref == SpeedRef) at.withSpeedKeyframeToggled(state.sampleMs, scene.fps)
                    else at.withKeyframeToggled(ref, at.element(ref)?.valueAt(at.t).orEmpty()).let { if (ref == FrameRef) it.withFrameHold() else it }
                } }
            }
            InlinePositionButton(PanelIcons.Easing, stringResource(R.string.a11y_easing), canEdit) {
                state.withInlineGraph(layer.id, ref, frames)
            }
        }
        if (value != null && ref != SpeedRef) {
            val gesture = "inline-value-${layer.id}-$ref"
            RulerSpinner(Am.Accent, onDrag = { delta ->
                val target = (pending ?: value) + delta * track.sensitivity
                val next = track.range?.let { target.coerceIn(it) } ?: target
                pending = next
                state.edit(gesture) { it.update(layer.id, state.sampleMs) { at ->
                    val shown = if (track.integer) next.roundToInt().toFloat() else next
                    val changed = at.withValue(ref, formatFloats(floatArrayOf(shown / track.multiplier)))
                    if (ref == FrameRef) changed.withFrameHold() else changed
                } }
            }, onEnd = { pending = null; state.endGesture() }, onStart = { pending = value; state.playing = false },
                modifier = Modifier.width(148.dp).height(InlinePositionControlHeight), enabled = canEdit, reserveGesture = true)
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
    state: EditorState, layer: Layer, track: InlineTrack, frames: List<Int>, editable: Boolean, modifier: Modifier,
    xOf: (Int) -> Float, frameAt: (Float) -> Int, labelWidth: Float, toggleControl: () -> Unit,
) {
    val currentFrames by rememberUpdatedState(frames)
    val position by rememberUpdatedState(xOf)
    val toFrame by rememberUpdatedState(frameAt)
    val toggle by rememberUpdatedState(toggleControl)
    val gesture = Modifier.pointerInput(layer.id, track.ref, editable, labelWidth) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val from = currentFrames.minByOrNull { abs(position(it) - down.position.x) }
                ?.takeIf { abs(position(it) - down.position.x) <= 9.dp.toPx() }
            if (from == null || !editable) {
                if (down.position.x < labelWidth && waitForUpOrCancellation() != null) toggle()
                return@awaitEachGesture
            }
            val base = state.scene.layer(layer.id) ?: return@awaitEachGesture
            val scope = KeyScope(track.ref)
            val key = "inline-key-${layer.id}-${track.ref}"
            var moved = false
            val start = position(from)
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
                    val frame = toFrame(start + travel).coerceIn(0, state.scene.lastFrame)
                    state.edit(key) { scene -> scene.update(layer.id) { base.withKeyframesMoved(from, frame, scene, scope) } }
                } while (true)
                if (!moved) { state.playing = false; state.frame = from }
            } finally {
                if (moved) state.endGesture()
            }
        }
    }
    Canvas(modifier.then(gesture)) {
        val y = center.y
        val radius = 4.5.dp.toPx()
        for (frame in frames) {
            val x = xOf(frame)
            if (x !in 0f..size.width) continue
            val diamond = Path().apply {
                moveTo(x - radius, y)
                lineTo(x, y - radius)
                lineTo(x + radius, y)
                lineTo(x, y + radius)
                close()
            }
            drawPath(diamond, if (state.frame == frame) Am.Accent else Am.OnDark)
        }
    }
}
