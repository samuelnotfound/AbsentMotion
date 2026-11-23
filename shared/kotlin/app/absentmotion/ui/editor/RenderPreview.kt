package app.absentmotion.ui.editor

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.editor.EditorPanel
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.MultiSelect
import app.absentmotion.editor.PreviewCache
import app.absentmotion.editor.matchesFrames
import app.absentmotion.ui.theme.Am
import app.absentmotion.ui.theme.Presence
import app.absentmotion.ui.theme.presence
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

const val RENDER_PREVIEW_TAG = "render_preview"

internal class LiveHandoff {
    var requested by mutableLongStateOf(0L)
    var handoff by mutableLongStateOf(0L)
}

internal fun cacheMatches(state: EditorState, resources: Long): Boolean {
    val hasSelection = state.selectedId != null || MultiSelect.ids.isNotEmpty()
    val selectionCanUseCache = !hasSelection || state.playing && state.selected?.kind != "camera" && state.panel != EditorPanel.Crop
    return PreviewCache.phase == PreviewCache.Phase.Ready && !PreviewCache.paused &&
        PreviewCache.key?.matchesFrames(PreviewCache.keyOf(state, resources)) == true && state.soloMode == 0 && !state.pixelMode &&
        state.editorCamera == null && !state.gridMode && state.canvasZoom == 1f && state.canvasPan == Offset.Zero && selectionCanUseCache && state.drawing == null && state.panel != EditorPanel.EditText
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PreRenderCell(state: EditorState, modifier: Modifier) {
    val context = LocalContext.current
    val scope = LocalEditorScope.current
    val resources by state.engine.mediaRevision.collectAsState()
    val status by remember(state, resources) { derivedStateOf { PreviewCache.preRender(state, resources) } }
    val label = stringResource(if (status == PreviewCache.PreRender.Rendering) R.string.preview_cache_cancel_render else R.string.prerender)
    val enabled = state.editorCamera == null || status == PreviewCache.PreRender.Rendering
    val described = when (status) {
        PreviewCache.PreRender.Ready -> stringResource(R.string.preview_cache_state_ready)
        PreviewCache.PreRender.Outdated -> stringResource(R.string.preview_cache_state_outdated)
        else -> null
    }
    val removeCopy = {
        if (status == PreviewCache.PreRender.Ready) {
            PreviewCache.remove()
            Toast.makeText(context, app.absentmotion.i18n.AppText.get(R.string.preview_cache_removed), Toast.LENGTH_SHORT).show()
        }
    }
    Box(
        modifier.fillMaxWidth().testTag(RENDER_PREVIEW_TAG).semantics { contentDescription = label; if (described != null) stateDescription = described }.then(if (enabled) Modifier.rightClick(removeCopy) else Modifier).combinedClickable(
            enabled = enabled, onLongClick = removeCopy,
            onClick = {
                when (status) {
                    PreviewCache.PreRender.Rendering -> PreviewCache.cancel()
                    PreviewCache.PreRender.Ready -> PreviewCache.pause()
                    PreviewCache.PreRender.Outdated -> PreviewCache.forget()
                    else -> PreviewCache.render(state, context, scope)
                }
            },
        ),
        Alignment.Center,
    ) {
        Text(
            if (status == PreviewCache.PreRender.Rendering) "${(PreviewCache.progress * 100).roundToInt()}%" else stringResource(R.string.prerender),
            color = if (!enabled) Am.OnDarkDim else when (status) {
                PreviewCache.PreRender.Rendering, PreviewCache.PreRender.Ready -> Am.Accent
                PreviewCache.PreRender.Outdated -> Am.Warning
                PreviewCache.PreRender.None -> Color.White
            },
            fontSize = 10.sp, lineHeight = 11.sp, maxLines = 2, textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CachedFrame(state: EditorState, usable: Boolean, live: LiveHandoff, left: Float, top: Float, width: Float, height: Float) {
    val player = PreviewCache.player
    if (!usable || player == null) return
    LaunchedEffect(player) {
        snapshotFlow { (state.frame to state.playing).takeIf { PreviewCache.covers(it.first) } }.collect { wanted ->
            wanted?.let { (frame, playing) -> player.want(frame, playing) }
        }
    }
    val rendered by state.engine.rendered.collectAsState()
    val frame = state.frame
    val covered = PreviewCache.covers(frame)
    val handing = rendered.serial < live.handoff
    val scrubbing = covered && !state.playing && rendered.serial < live.requested && rendered.timeMs != state.timeMs
    if (!(covered && state.playing) && !handing && !scrubbing) return
    val background = Am.PreviewBg
    Canvas(Modifier.fillMaxSize()) {
        val shown = player.shown ?: return@Canvas
        if (shown.frame != frame) return@Canvas
        drawRect(background)
        drawImage(
            shown.image, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.Low,
        )
    }
    if (state.playing && covered) CachedSelection(state, frame, left, top, width, height) { player.shown?.frame == frame }
}

private data class CachedLayerControls(val contour: Path, val corners: List<Offset>, val pivot: Pair<PivotProjection, FloatArray>?)

@Composable
private fun CachedSelection(state: EditorState, frame: Int, left: Float, top: Float, width: Float, height: Float, visible: () -> Boolean) {
    val scene = state.scene
    val selected = state.selectedId
    val ids = MultiSelect.ids.toList().ifEmpty { listOfNotNull(selected) }
    if (ids.isEmpty()) return
    val time = rememberUpdatedState(scene.frameStart(frame))
    var controls by remember(scene, ids, selected, state.cameraMode) { mutableStateOf(emptyList<CachedLayerControls>()) }
    LaunchedEffect(state, scene, ids, selected, state.cameraMode) {
        snapshotFlow { time.value }.conflate().collect { ms ->
            controls = ids.mapNotNull { id ->
                val layer = scene.layerAt(id, ms)?.takeIf { it.activeAt(ms) } ?: return@mapNotNull null
                val points = state.engine.layerQuad(id, ms) ?: return@mapNotNull null
                val corners = List(4) { Offset(points[it * 2], points[it * 2 + 1]) }
                if (corners.any { !it.x.isFinite() || !it.y.isFinite() }) return@mapNotNull null
                val shape = layer.kind == "shape"
                val matrices = if (shape || id == selected) state.engine.layerMatrix(id, ms) else null
                val projection = matrices?.let(::selectionMatrix)
                val path = if (shape && projection != null) state.engine.layerOutline(id, ms)?.let { svg ->
                    runCatching { PathParser().parsePathString(svg).toPath().apply { transform(projection) } }.getOrNull()
                } else null
                val contour = path ?: Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    corners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                val pivot = if (id == selected) matrices?.let { PivotProjection.from(it) }?.let {
                    val point = layer.pivot.let { if (layer.uses3D) it else floatArrayOf(it[0], it[1], 0f) }
                    it to point
                } else null
                CachedLayerControls(contour, corners, pivot)
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (!visible()) return@Canvas
        val scaleX = width / scene.width
        val scaleY = height / scene.height
        val toScreen = Matrix(floatArrayOf(
            scaleX, 0f, 0f, 0f,
            0f, scaleY, 0f, 0f,
            0f, 0f, 1f, 0f,
            left, top, 0f, 1f,
        ))
        for ((contour, corners, pivot) in controls) {
            val outline = Path().apply { addPath(contour); transform(toScreen) }
            drawPath(outline, Color.Black.copy(alpha = 0.65f), style = Stroke(1.75.dp.toPx()))
            drawPath(outline, Am.Accent, style = Stroke(1.dp.toPx()))
            if (pivot == null) continue
            drawPivotRing(pivot.first, pivot.second, scaleX, Offset(left, top), 3.dp.toPx(), Am.OnDarkDim, 1.dp.toPx())
            for (i in corners.indices) {
                val next = corners[(i + 1) % corners.size]
                for (point in listOf(corners[i], (corners[i] + next) / 2f)) {
                    drawCircle(Color.Black, 3.5.dp.toPx(), toScreen.map(point))
                    drawCircle(Am.OnDark, 2.75.dp.toPx(), toScreen.map(point))
                }
            }
        }
    }
}

private fun selectionMatrix(m: FloatArray): Matrix = Matrix(floatArrayOf(
    m[0], m[3], 0f, m[6],
    m[1], m[4], 0f, m[7],
    0f, 0f, 1f, 0f,
    m[2], m[5], 0f, m[8],
))

@Composable
internal fun RenderPreviewNotes(state: EditorState, cachedPlaying: State<Boolean>, modifier: Modifier) {
    LaunchedEffect(state.playing) {
        if (!state.playing) return@LaunchedEffect
        val startNs = System.nanoTime()
        val startFrame = state.frame
        val startRenders = state.engine.renders
        val startShows = PreviewCache.player?.shows ?: 0L
        var cachedTime = false
        try {
            while (true) {
                delay(250)
                val cached = cachedPlaying.value
                cachedTime = cachedTime || cached
            }
        } finally {
            Log.i("AMCache", "play cached=%b frames=%d renders=%d cachedShows=%d ms=%d".format(
                cachedTime, state.frame - startFrame, state.engine.renders - startRenders, (PreviewCache.player?.shows ?: 0L) - startShows,
                (System.nanoTime() - startNs) / 1_000_000))
        }
    }
    LaunchedEffect(state.exportOpen) {
        if (state.exportOpen) PreviewCache.cancel()
    }
    LaunchedEffect(state.scene.workArea) { PreviewCache.cancel() }
    val context = LocalContext.current
    val scope = LocalEditorScope.current
    val resources by state.engine.mediaRevision.collectAsState()
    val outdated by remember(state, resources) { derivedStateOf { PreviewCache.preRender(state, resources) == PreviewCache.PreRender.Outdated } }
    var stale by remember { mutableStateOf(false) }
    LaunchedEffect(outdated, state.playing) {
        stale = outdated
        if (outdated) {
            delay(STALE_NOTE_MS)
            stale = false
        }
    }
    val rendering = PreviewCache.phase == PreviewCache.Phase.Rendering
    Box(modifier.padding(top = 10.dp)) {
        Presence(stale && !rendering) {
            Note(Modifier.presence(rise = (-8).dp)) {
                Text(stringResource(R.string.prerender_outdated), Modifier.weight(1f), color = Am.OnDark, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
                TextButton({ stale = false; PreviewCache.render(state, context, scope) }) {
                    Text(stringResource(R.string.prerender_again), color = Am.Accent, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        Presence(rendering) {
            Note(Modifier.presence(rise = (-8).dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.prerender_progress, (PreviewCache.progress * 100).roundToInt()), color = Am.OnDark, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Am.OnDark.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(PreviewCache.progress.coerceIn(0f, 1f)).height(3.dp).background(Am.Accent))
                    }
                }
                TextButton({ PreviewCache.cancel() }) { Text(stringResource(R.string.cancel), color = Am.Accent, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

private const val STALE_NOTE_MS = 5_000L

@Composable
private fun Note(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.padding(horizontal = 16.dp).widthIn(max = 280.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.clip(RoundedCornerShape(14.dp)).background(Color(0xE6222222))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}
