package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.InlineKey
import app.absentmotion.editor.InlineKeyMove
import app.absentmotion.editor.InlineSection
import app.absentmotion.editor.InlineTrack
import app.absentmotion.editor.inlineEditorPanel
import app.absentmotion.editor.openInlineEditor
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.valueAt
import app.absentmotion.ui.editor.panels.formatFixed
import app.absentmotion.ui.theme.Am
import kotlin.math.abs

private val SectionHeight = 27.dp
internal val inlineTrackHeight = 27.dp
private val DetailsSeparator = 7.dp
private val TreeTrunk = 17.dp
private val TrackIndent = 30.dp

internal fun inlineDetailsHeight(sections: List<InlineSection>) =
    SectionHeight * maxOf(1, sections.size) + inlineTrackHeight * sections.sumOf { it.tracks.size } + DetailsSeparator

internal fun inlineTrackOffset(sections: List<InlineSection>, ref: PropRef): androidx.compose.ui.unit.Dp? {
    var y = 0.dp
    for (section in sections) {
        y += SectionHeight
        val index = section.tracks.indexOfFirst { it.ref == ref }
        if (index >= 0) return y + inlineTrackHeight * index
        y += inlineTrackHeight * section.tracks.size
    }
    return null
}

@Composable
internal fun InlinePositionDetails(
    state: EditorState, layer: Layer, sections: List<InlineSection>, modifier: Modifier,
    xOf: (Int) -> Float, frameAt: (Float) -> Int, selection: InlineKeySelection,
) {
    val scene = state.scene
    val at = layer.at(state.sampleMs, scene.fps)
    val canEdit = !state.lookOnly && !layer.locked
    fun editor(track: InlineTrack): (() -> Unit)? = if (canEdit && inlineEditorPanel(layer, track.ref) != null) {
        { state.openInlineEditor(layer, track.ref) }
    } else null
    BoxWithConstraints(modifier) {
        val labelWidth = minOf(172.dp, maxWidth * 0.59f)
        val valueWidth = minOf(58.dp, labelWidth * 0.39f)
        val tapWidth = with(LocalDensity.current) { (labelWidth - valueWidth).toPx() }
        Column(Modifier.fillMaxWidth()) {
            if (sections.isEmpty()) Row(Modifier.height(SectionHeight).padding(start = TreeTrunk), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inline_no_keyframes), color = Am.OnDarkDim, fontSize = 11.sp)
            }
            for (section in sections) {
                val heading = section.tracks.firstNotNullOfOrNull(::editor)
                Row(Modifier.height(SectionHeight).fillMaxWidth().then(if (heading == null) Modifier else Modifier.inlineTap(heading))
                    .drawBehind {
                        val x = TreeTrunk.toPx()
                        drawLine(Am.Line, Offset(x, size.height - 5.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                    }.padding(start = TreeTrunk), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.label, color = Am.OnDark, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                for ((index, track) in section.tracks.withIndex()) key(track.ref) {
                    val openEditor = editor(track)
                    val raw = at.element(track.ref)?.valueAt(at.t)
                    val value = raw?.toFloatOrNull()?.times(track.multiplier)
                    val display = value?.let { formatFixed(it, if (track.integer) 0 else 1) + track.unit } ?: raw.orEmpty()
                    val active = layer.id == state.selectedId && state.inlineProperty != null &&
                        (state.keyTarget?.ref ?: state.inlineProperty) == track.ref
                    Box(Modifier.fillMaxWidth().height(inlineTrackHeight)
                        .background((if (active) Am.Field else Am.TimelineBg).copy(alpha = 0.65f))) {
                        InlinePositionKeys(state, layer, track, track.frames(scene, layer), canEdit && !layer.timingLocked && !track.linked(layer),
                            Modifier.matchParentSize(), xOf, frameAt, tapWidth, openEditor, selection)
                        Row(Modifier.width(labelWidth).fillMaxHeight().background(Am.TimelineBg.copy(alpha = 0.32f))
                            .then(if (openEditor == null) Modifier else Modifier.semantics { onClick { openEditor(); true } })
                            .drawBehind {
                                val x = TreeTrunk.toPx()
                                val middle = size.height / 2f
                                val end = if (index == section.tracks.lastIndex) middle else size.height
                                drawLine(Am.Line, Offset(x, 0f), Offset(x, end), 1.dp.toPx())
                                drawLine(Am.Line, Offset(x, middle), Offset(TrackIndent.toPx() - 4.dp.toPx(), middle), 1.dp.toPx())
                            }.padding(start = TrackIndent, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(track.label, Modifier.weight(1f).padding(end = 5.dp), color = Am.OnDark,
                                maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis)
                            Text(display, Modifier.width(valueWidth), color = Am.Accent, fontSize = 10.5.sp,
                                textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(Modifier.height(DetailsSeparator).fillMaxWidth().drawBehind {
                drawLine(Am.OnDarkDim.copy(alpha = 0.48f), Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
            })
        }
    }
}

@Composable
private fun Modifier.inlineTap(action: () -> Unit): Modifier {
    val current by rememberUpdatedState(action)
    return semantics { onClick { current(); true } }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            if (waitForUpOrCancellation() != null) current()
        }
    }
}

internal class InlineKeySelection {
    var keys by mutableStateOf<Set<InlineKey>>(emptySet())

    fun retain(layers: Set<Long>) { keys = keys.filterTo(mutableSetOf()) { it.layerId in layers } }

    fun clear() { keys = emptySet() }
}

@Composable
private fun InlinePositionKeys(
    state: EditorState, layer: Layer, track: InlineTrack, frames: List<Int>, editable: Boolean, modifier: Modifier,
    xOf: (Int) -> Float, frameAt: (Float) -> Int, labelWidth: Float, openEditor: (() -> Unit)?, selection: InlineKeySelection,
) {
    val currentFrames by rememberUpdatedState(frames)
    val position by rememberUpdatedState(xOf)
    val toFrame by rememberUpdatedState(frameAt)
    val open by rememberUpdatedState(openEditor)
    val haptic = LocalHapticFeedback.current
    val gesture = Modifier.pointerInput(layer.id, track.ref, editable, labelWidth, selection) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val action = open.takeIf { down.position.x < labelWidth }
            val from = currentFrames.minByOrNull { abs(position(it) - down.position.x) }
                ?.takeIf { abs(position(it) - down.position.x) <= 9.dp.toPx() }
            if (from == null || !editable) {
                if (action != null) waitForUpOrCancellation()?.let { it.consume(); action() }
                return@awaitEachGesture
            }
            val hit = InlineKey(layer.id, track.ref, from)
            val start = position(from)
            var pointer = down
            var group = selection.keys
            val wasSelected = hit in group
            down.consume()
            suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.nextPointer() =
                awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            val held = if (wasSelected) false else withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                do {
                    pointer = nextPointer() ?: return@withTimeoutOrNull false
                    if (!pointer.pressed || pointer.isConsumed) return@withTimeoutOrNull false
                    pointer.consume()
                } while ((pointer.position - down.position).getDistance() <= viewConfiguration.touchSlop)
                false
            } == null
            if (held) {
                state.playing = false
                group = group + hit
                selection.keys = group
                if (state.settings.vibration) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (!pointer.pressed) {
                pointer.consume()
                if (action != null && group.isEmpty()) action()
                else if (group.isNotEmpty()) selection.keys = if (hit in group) group - hit else group + hit
                else { state.playing = false; state.frame = from }
                return@awaitEachGesture
            }
            if (!wasSelected && !held && action != null) return@awaitEachGesture
            if (hit !in group) group = setOf(hit)
            val move = InlineKeyMove(state.scene, group)
            var moved = false
            var lastDelta: Int? = null
            try {
                while (true) {
                    val travel = pointer.position.x - down.position.x
                    if (moved || abs(travel) > viewConfiguration.touchSlop) {
                        moved = true
                        state.playing = false
                        val delta = toFrame(start + travel) - from
                        if (delta != lastDelta) {
                            lastDelta = delta
                            state.edit("inline-key-move") { scene ->
                                val (updated, movedKeys) = move.apply(scene, delta)
                                if (wasSelected || held) selection.keys = movedKeys
                                updated
                            }
                        }
                    }
                    pointer = nextPointer() ?: break
                    if (pointer.isConsumed) break
                    pointer.consume()
                    if (!pointer.pressed) {
                        if (!moved && !held && wasSelected) selection.keys = selection.keys - hit
                        break
                    }
                }
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
            val selected = InlineKey(layer.id, track.ref, frame) in selection.keys
            if (selected) drawPath(diamond, Am.Accent.copy(alpha = 0.30f), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
            drawPath(diamond, if (selected || state.frame == frame) Am.Accent else Am.OnDark)
        }
    }
}
