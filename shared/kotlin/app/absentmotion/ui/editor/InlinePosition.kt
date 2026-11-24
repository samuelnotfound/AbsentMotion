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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.InlineKey
import app.absentmotion.editor.InlineSection
import app.absentmotion.editor.InlineTrack
import app.absentmotion.editor.inlineEditorPanel
import app.absentmotion.editor.openInlineEditor
import app.absentmotion.model.Layer
import app.absentmotion.model.PropRef
import app.absentmotion.model.Scene
import app.absentmotion.model.valueAt
import app.absentmotion.ui.editor.panels.PanelIcon
import app.absentmotion.ui.editor.panels.formatFixed
import app.absentmotion.ui.theme.Am

private val SectionHeight = 27.dp
internal val inlineTrackHeight = 27.dp
private val DetailsSeparator = 7.dp
private val TreeTrunk = 17.dp
private val TrackIndent = 30.dp
private val TopTrackIcon = TrackIndent - TreeTrunk

internal fun Density.inlineDetailsHeight(sections: List<InlineSection>): Int =
    SectionHeight.roundToPx() * maxOf(1, sections.size) +
        inlineTrackHeight.roundToPx() * sections.sumOf { it.visibleTracks.size } + DetailsSeparator.roundToPx()

internal fun Density.inlineTrackOffset(sections: List<InlineSection>, ref: PropRef): Int? {
    var y = 0
    val trackHeight = inlineTrackHeight.roundToPx()
    for (section in sections) {
        y += SectionHeight.roundToPx()
        val index = section.visibleTracks.indexOfFirst { it.ref == ref }
        if (index >= 0) return y + trackHeight * index
        y += trackHeight * section.visibleTracks.size
    }
    return null
}

@Composable
internal fun InlinePositionDetails(
    state: EditorState, layer: Layer, sections: List<InlineSection>, modifier: Modifier,
    xOf: (Int) -> Float, selection: InlineKeySelection,
    scene: Scene = state.scene, sampleMs: Int = state.sampleMs, nested: Boolean = false, editable: Boolean = true,
    onOpen: (PropRef) -> Unit = { state.openInlineEditor(layer, it) },
    onToggleEffect: (InlineSection) -> Unit,
    onEditValue: ((InlineTrack, Float) -> Unit)? = null,
) {
    val at = layer.at(sampleMs, scene.fps)
    val canEdit = editable && !state.lookOnly && !layer.locked
    fun editor(track: InlineTrack): (() -> Unit)? = if (canEdit && inlineEditorPanel(layer, track.ref) != null) {
        { onOpen(track.ref) }
    } else null
    BoxWithConstraints(modifier) {
        val labelWidth = minOf(172.dp, maxWidth * 0.59f)
        val valueWidth = minOf(58.dp, labelWidth * 0.39f)
        Column(Modifier.fillMaxWidth()) {
            if (sections.isEmpty()) Row(Modifier.height(SectionHeight).padding(start = TreeTrunk), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inline_no_keyframes), color = Am.OnDarkDim, fontSize = 11.sp)
            }
            for (section in sections) {
                val expansion = section.effectExpanded
                val heading = if (expansion == null) section.visibleTracks.firstNotNullOfOrNull(::editor)
                    else { { onToggleEffect(section) } }
                Row(Modifier.height(SectionHeight).fillMaxWidth().then(if (heading == null) Modifier else Modifier.inlineTap(heading))
                    .drawBehind {
                        if (section.visibleTracks.isEmpty()) return@drawBehind
                        val x = TreeTrunk.toPx()
                        drawLine(Am.Line, Offset(x, size.height - 5.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                    }.padding(start = TreeTrunk), verticalAlignment = Alignment.CenterVertically) {
                    if (expansion != null) {
                        Box(Modifier.size(TopTrackIcon), contentAlignment = Alignment.Center) {
                            PanelIcon(if (expansion) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight,
                                if (expansion) Am.Accent else Am.OnDarkDim, size = 14.dp)
                        }
                    }
                    Text(section.label, color = Am.OnDark, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                for ((index, track) in section.visibleTracks.withIndex()) key(track.ref) {
                    val openEditor = editor(track)
                    val raw = at.element(track.ref)?.valueAt(at.t) ?: track.default
                    val value = raw?.toFloatOrNull()?.times(track.multiplier)
                    val display = value?.let { formatFixed(it, if (track.integer) 0 else 1) + track.unit } ?: raw.orEmpty()
                    val active = !nested && layer.id == state.selectedId && state.inlineProperty != null &&
                        (state.keyTarget?.ref ?: state.inlineProperty) == track.ref
                    Box(Modifier.fillMaxWidth().height(inlineTrackHeight)
                        .background((if (active) Am.Field else Am.TimelineBg).copy(alpha = 0.65f))) {
                        InlinePositionKeys(state, layer, track, track.frames(scene, layer), Modifier.matchParentSize(), xOf, selection.takeUnless { nested })
                        Row(Modifier.width(labelWidth).fillMaxHeight().background(Am.TimelineBg.copy(alpha = 0.32f))
                            .then(if (openEditor == null) Modifier else Modifier.inlineTap(openEditor))
                            .drawBehind {
                                val x = TreeTrunk.toPx()
                                val middle = size.height / 2f
                                val end = if (index == section.visibleTracks.lastIndex) middle else size.height
                                drawLine(Am.Line, Offset(x, 0f), Offset(x, end), 1.dp.toPx())
                                drawLine(Am.Line, Offset(x, middle), Offset(TrackIndent.toPx() - 4.dp.toPx(), middle), 1.dp.toPx())
                            }.padding(start = TrackIndent, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(track.label, Modifier.weight(1f).padding(end = 5.dp), color = Am.OnDark,
                                maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis)
                            val valueTap = if (canEdit && value != null && onEditValue != null && !track.linked(layer)) {
                                Modifier.inlineTap { onEditValue(track, value) }
                            } else Modifier
                            Text(display, Modifier.width(valueWidth).then(valueTap), color = Am.Accent, fontSize = 10.5.sp,
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
internal fun Modifier.inlineTap(action: () -> Unit): Modifier {
    val current by rememberUpdatedState(action)
    return semantics { onClick { current(); true } }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            waitForUpOrCancellation()?.let { it.consume(); current() }
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
    state: EditorState, layer: Layer, track: InlineTrack, frames: List<Int>, modifier: Modifier,
    xOf: (Int) -> Float, selection: InlineKeySelection?,
) {
    Canvas(modifier) {
        val y = center.y
        val radius = 4.5.dp.toPx()
        val diamond = Path()
        for (frame in frames) {
            val x = xOf(frame)
            if (x !in 0f..size.width) continue
            diamond.apply {
                rewind()
                moveTo(x - radius, y)
                lineTo(x, y - radius)
                lineTo(x + radius, y)
                lineTo(x, y + radius)
                close()
            }
            val selected = selection?.keys?.contains(InlineKey(layer.id, track.ref, frame)) == true
            drawTimelineKey(diamond, selected, frame == state.playheadFrame)
        }
    }
}

internal fun DrawScope.drawTimelineKey(diamond: Path, selected: Boolean, current: Boolean) {
    val highlight = when {
        selected -> Am.Cool
        current -> Am.Accent
        else -> null
    }
    val outline = highlight ?: Am.TimelineBg.copy(alpha = 0.7f)
    drawPath(diamond, outline, style = Stroke((if (highlight == null) 2.dp else 3.5.dp).toPx(), join = StrokeJoin.Round))
    drawPath(diamond, Am.OnDark)
}
