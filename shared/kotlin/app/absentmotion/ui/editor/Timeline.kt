package app.absentmotion.ui.editor

import app.absentmotion.model.withEdgeMotions
import app.absentmotion.ui.adaptive.deskLayout
import app.absentmotion.ui.tutorial.tutorialGuard
import app.absentmotion.model.Scene
import app.absentmotion.model.shownName
import app.absentmotion.model.defaultCameraId
import androidx.compose.ui.unit.DpSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import app.absentmotion.platform.PlatformFeatures
import app.absentmotion.platform.resizeCursor
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.absentmotion.R
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.absentmotion.platform.SystemClock
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.editor.InlineKey
import app.absentmotion.editor.InlineKeyMove
import app.absentmotion.editor.InlineSection
import app.absentmotion.editor.inlineTrackSections
import app.absentmotion.editor.openInlineEditor
import app.absentmotion.editor.toggleLayer3D
import app.absentmotion.editor.stepBack
import app.absentmotion.editor.renameLayer
import app.absentmotion.editor.copySelected
import app.absentmotion.editor.deleteChosen
import app.absentmotion.editor.duplicateChosen
import app.absentmotion.editor.pasteLayer
import app.absentmotion.editor.canPaste
import app.absentmotion.editor.split
import app.absentmotion.editor.groupLayersCentred
import app.absentmotion.editor.chosenLayers
import app.absentmotion.editor.isPlainGroup
import app.absentmotion.editor.askUngroup
import app.absentmotion.editor.EditorPanel
import app.absentmotion.editor.MultiSelect
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.PreviewCache
import app.absentmotion.editor.toggleBookmark
import app.absentmotion.editor.toggleHidden
import app.absentmotion.editor.toggleLock
import app.absentmotion.editor.setLock
import app.absentmotion.editor.selectAll
import app.absentmotion.editor.deselectLayer
import app.absentmotion.editor.clearSelection
import app.absentmotion.media.VideoProxies
import app.absentmotion.model.LabelColour
import app.absentmotion.model.Layer
import app.absentmotion.ai.trimmedClip
import app.absentmotion.ai.withClip
import app.absentmotion.model.KeyScope
import app.absentmotion.ui.theme.Am
import app.absentmotion.ui.theme.Motion
import app.absentmotion.ui.tutorial.TourFacts
import app.absentmotion.ui.tutorial.kindOf
import app.absentmotion.ui.tutorial.tutorialRegion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import app.absentmotion.ui.home.DialogPill

private val RulerHeight = 42.dp
private val RowHeight get() = if (deskLayout) 30.dp else 28.dp


private val PlayheadWidth = 1.5.dp
private val TickWidth = 1.dp
private val TickShort = 5.dp
private val TickHalfSecond = 12.dp
private val TickSecond = 18.dp
private const val MIN_TICK_GAP_DP = 3f

private val PillWidth = 70.dp
private val PillTop = 17.8.dp
private val PillHeight = 16.7.dp
private val PillNotch = 5.dp

private val ReadoutHalfWidth = PillWidth / 2 + 12.dp
private val PillInset = 2.dp
private const val SelectedRowShade = 0.16f
private val ReadoutTop = PillTop + PillHeight / 2 - 24.dp

private val BookmarkColor get() = Am.Bookmark
private const val BOOKMARK_ALPHA = 0.58f
private val BookmarkLineWidth = 0.7.dp
private val BookmarkFlagTop = 2.1.dp
private val BookmarkFlagBottom = 5.dp
private val BookmarkFlagSide = 2.1.dp
private val BookmarkPeakHalf = 2.5.dp
private val PillOutline = 1.1.dp

private val EyeZone = 32.dp
private val LockZone = 22.dp
private val ThreeDZone = 22.dp
private val ThumbZone = 22.dp
private val DisclosureZone = 24.dp
internal val HeaderWidth: Dp = EyeZone + ThumbZone + DisclosureZone
private val ExpandedControls = LockZone + ThreeDZone
private val HeaderInset = 1.dp
private val TagStripWidth = 2.1.dp
private val EyeCentre = EyeZone / 2
private val LockCentre = EyeZone + LockZone / 2
private val CubeLeft = EyeZone + LockZone
private val ThumbSize = 16.dp
private fun thumbLeft(controls: Boolean) = EyeZone + (if (controls) ExpandedControls else 0.dp) + (ThumbZone - ThumbSize) / 2
private val ClipTrim = 8.dp
private val DeskClipTrim = 12.dp
private const val HIDDEN_ALPHA = 0.55f

private val BarInset = 2.5.dp
private val BarRadius get() = if (deskLayout) 4.dp else 2.dp
private val BarShadowHeight = 2.dp
private val LabelInset = 14.dp
private const val MAX_LABEL_PX = 16_000

private val ProxyLineHeight = 2.dp
private val ProxyLineInset = 1.dp

private val GripZone = 30.dp
private val GripGap = 1.8.dp

private val HandleInset = 9.dp
private val HandleZone = 22.dp

private val MultiBarHeight = 48.dp

private val SnapZone = 8.dp
private val SnapLine = 1.dp

private const val LIFT_SCALE = 1.02f
private const val LIFT_DIM = 0.3f
private const val LIFT_SHADOW_ALPHA = 0.07f
private val LiftShadowStep = 1.5.dp
private const val LIFT_MS = 100
private const val SLIDE_MS = 120
private const val DROP_MS = 150
private val PushZone get() = if (deskLayout) 28.dp else 56.dp
private const val PUSH_SLOWEST_ROWS = 1f
private const val PUSH_FASTEST_ROWS = 12f
private const val PUSH_MAX_STEP_S = 0.1f

private const val MIN_ZOOM = 10f
private const val FAR_OUT_MS = 600_000f
private const val MAX_ZOOM = 1000f

private val TickColor get() = Am.Line
private val CacheBarColor = Color(0xFF45C46A)
private val CacheBarHeight = 3.dp
private val WorkAreaHitZone = 14.dp
private val RulerMajor get() = Am.Inactive
private val RulerMinor get() = Am.Inactive.copy(alpha = 0.7f)
private val RetimeMarkColor get() = Am.Gold.copy(alpha = 0.67f)
private val PillColor get() = Am.Button
private val HeaderFill get() = Am.Button
private val HeaderEdge get() = Am.Line
private val BarStripe get() = Am.LayerBarStripe
private val TextBar get() = Am.TextBar
private val TextStripe get() = Am.TextBarStripe
private val CameraBar = Color(0xFFC32F5B)
private val CameraStripe = Color(0xFFC83D66)
private val Layer3DColor = Color(0xFFFF8F66)
private val GroupBar = Color(0xFFE88C3B)
private val GroupStripe = Color(0xFFD9772E)
private val NullBar get() = Am.Inactive
private val NullStripe get() = Am.Inactive.copy(alpha = 0.85f).compositeOver(Am.Line)
private val BarShadow get() = Am.PreviewBg
private val GripColor get() = Am.Inactive
private val HandleColor get() = Am.Inactive
private val LimitColor get() = Am.Warning
private val SnapColor get() = Am.Accent


private class TimelineStyles(font: androidx.compose.ui.text.font.FontFamily?) {
    val ruler = TextStyle(color = Am.OnDarkDim, fontSize = 12.sp, fontFamily = font)
    val timecode = TextStyle(color = Am.OnDark, fontSize = 14.sp, fontFamily = font)
    val label = TextStyle(color = Am.OnBar, fontSize = if (deskLayout) 12.sp else 11.sp, fontFamily = font, textDirection = androidx.compose.ui.text.style.TextDirection.Content)
    val mediaLabel = label.copy(color = Color.White)
    val audioLabel = label.copy(color = Am.OnAudioBar)
    val header = TextStyle(color = Am.OnDark, fontSize = 13.sp, fontFamily = font)
    val hiddenHeader = TextStyle(color = Am.OnDarkDim, fontSize = 13.sp, fontFamily = font)
}

@Composable
fun Timeline(state: EditorState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val wide = LocalWide.current
    val view = remember(state, density) {
        TimelineView(state, density, scope).also { v ->
            v.onTick = { if (state.settings.vibration) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
        }
    }
    view.wide = wide
    view.rail = LocalRail.current
    val measurer = rememberTextMeasurer(cacheSize = if (deskLayout) 128 else 64)
    val font = androidx.compose.material3.LocalTextStyle.current.fontFamily
    val styles = remember(Am.palette, font) { TimelineStyles(font) }
    val eye = rememberVectorPainter(TimelineIcons.Eye)
    val eyeOff = rememberVectorPainter(TimelineIcons.EyeOff)
    val locks = listOf(rememberVectorPainter(TimelineIcons.LockOpen), rememberVectorPainter(TimelineIcons.Lock), rememberVectorPainter(TimelineIcons.LockTiming))
    val outline = remember { Path() }
    val strips by remember(view) { derivedStateOf { view.stripWindow() } }
    val proxies by VideoProxies.progress.collectAsState()
    val mediaPictures = buildMap {
        for (row in strips.rows) {
            val layer = view.layerAt(row) ?: continue
            val info = layer.mediaFill?.let { state.scene.media(it.uri) }
            val turned = info?.orientation == 90 || info?.orientation == 270
            val width = (if (turned) info.height else info?.width)?.coerceAtLeast(1) ?: 1
            val height = (if (turned) info.width else info?.height)?.coerceAtLeast(1) ?: 1
            val tileMs = 23f * width / height * 1000 / view.dpPerSecond
            key(layer.id) { mediaStripPicture(layer, state.scene, state.engine, strips.fromMs, strips.toMs, tileMs)?.let { put(layer.id, it) } }
        }
    }

    LoadWaveforms(state.scene)
    val effectCatalog by state.engine.effects.collectAsState()
    SideEffect { view.catalog = effectCatalog }
    LaunchedEffect(view, state.groupPath) { view.restoreExpandedRows() }
    LaunchedEffect(view, state.inlineProperty, view.size) { if (state.inlineProperty != null) view.revealRow() }
    LaunchedEffect(view) {
        snapshotFlow { view.layers.map { it.id }.toSet() }.collect(view::retainInlinePositions)
    }
    DisposableEffect(view) {
        val visibleKeys: () -> List<Int> = view::inlineKeyframes
        state.inlineKeyframes = visibleKeys
        onDispose {
            if (state.inlineKeyframes === visibleKeys) {
                state.inlineKeyframes = null
                state.timelineDetailsOpen = false
            }
        }
    }
    LaunchedEffect(view, state.selectedId, state.revealCount) { if (deskLayout) view.revealRow() else view.revealSelected() }
    if (deskLayout) LaunchedEffect(view, view.layers.isEmpty()) { view.refit() }
    val appear = remember(state.selectedId) { Animatable(if (state.selectedId == null) 1f else 0f) }
    LaunchedEffect(appear) { appear.animateTo(1f, Motion.enter()) }
    LaunchedEffect(view, state.playing) { if (state.playing) view.followPlayback() }
    if (deskLayout) LaunchedEffect(view) {
        snapshotFlow { state.frame to state.playing }.collect { (_, playing) -> if (playing) view.pagePlayback() else view.revealPlayhead() }
    }
    if (deskLayout) app.absentmotion.platform.BackHandler(view.desktopDragging || (state.drawing !is app.absentmotion.editor.PointMode && (state.selectedId != null || MultiSelect.active))) {
        if (!view.cancelDesktopDrag()) {
            if (state.inlineProperty != null) state.stepBack() else state.clearSelection()
        }
    }
    if (deskLayout) app.absentmotion.platform.BackHandler(view.menuAt != null) { view.menuAt = null }
    if (deskLayout) app.absentmotion.platform.BackHandler(view.renameId != null) { view.renameId = null }
    if (deskLayout) DisposableEffect(view) {
        val zoom: (Float) -> Unit = { view.zoomBy(it) }
        val fit = { view.fit() }
        val frameAt: (Offset) -> Int? = { point -> view.frameAtWindow(point) }
        val before = Triple(DeskTimelineControls.zoom, DeskTimelineControls.fit, DeskDrop.frameAt)
        DeskTimelineControls.zoom = zoom
        DeskTimelineControls.fit = fit
        DeskDrop.frameAt = frameAt
        onDispose {
            if (DeskTimelineControls.zoom === zoom) DeskTimelineControls.zoom = before.first
            if (DeskTimelineControls.fit === fit) DeskTimelineControls.fit = before.second
            if (DeskDrop.frameAt === frameAt) DeskDrop.frameAt = before.third
        }
    }

    TimelineSurface(
        view,
        modifier
            .then(if (deskLayout) Modifier else app.absentmotion.platform.wheel { delta, at, zoom ->
                if (zoom) return@wheel view.zoomBy(1.1f.pow(-delta.y))
                view.stopMotion(); state.playing = false
                val horizontal = kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y) || at.y < view.rulerH
                view.dragBy(if (horizontal) Drag.Scrub else Drag.Scroll, if (horizontal) Offset(-(if (delta.x != 0f) delta.x else delta.y) * 18f, 0f) else Offset(0f, -delta.y * 18f), at)
                view.finishWheelScrub()
            })
            .onSizeChanged { view.resizeViewport(it) }
            .then(if (deskLayout) Modifier.onGloballyPositioned { view.coordinates = it } else Modifier)
            .tutorialRegion("ruler") { listOf(Rect(0f, 0f, view.size.width.toFloat(), view.rulerH)) }
            .tutorialRegion("clip") { view.clipRows(media = false) }
            .tutorialRegion("clip_media") { view.clipRows(media = true) }
            .pointerInput(view) {
                if (deskLayout) desktopTimelineGestures(view, focus)
                else timelineGestures(view) { if (state.settings.vibration) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            }
            .drawBehind { drawTimeline(view, measurer, styles, eye, eyeOff, locks, outline, mediaPictures, proxies, appear.value) }
            .drawWithContent {
                drawContent()
                view.keyBox?.let { rect ->
                    drawRect(Am.Cool.copy(alpha = 0.20f), rect.topLeft, rect.size)
                    drawRect(Am.Cool, rect.topLeft, rect.size, style = Stroke(1.dp.toPx()))
                }
                val time = view.timeScale()
                val left = if (deskLayout) view.headerWidth else 0f
                if (time.originX in left..size.width) {
                    val onBookmark = state.scene.bookmarks.any { state.scene.frameAt(it) == state.frame }
                    drawPlayhead(time.originX, TimelineMath.timecode(state.playheadFrame, view.fps), measurer, styles, outline,
                        onBookmark, if (deskLayout) left + PillInset.toPx() else Float.NEGATIVE_INFINITY)
                }
            },
    ) {
        InlineRows(view, measurer, styles)
        HeaderPictures(view) { drawLifted(view, measurer, styles, eye, eyeOff, locks, outline, mediaPictures, appear.value) }
        RowSemantics(view)
        LockMenu(view)
        if (deskLayout) {
            DesktopTimelineMenu(view)
            DesktopTimelineRename(view)
        }
    }
}

@Composable
private fun TimelineSurface(view: TimelineView, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val cursor = if (!deskLayout) Modifier else if (view.resizeHover) Modifier.resizeCursor() else Modifier.pointerHoverIcon(if (view.handHover) PointerIcon.Hand else PointerIcon.Default)
    Box(modifier.then(cursor), content = content)
}

@Composable
private fun DesktopTimelineMenu(view: TimelineView) {
    var stretch by remember { mutableStateOf<Set<Long>?>(null) }
    val scope = rememberCoroutineScope()
    stretch?.let { StretchSheet(view.state, it) { stretch = null } }
    val at = view.menuAt ?: return
    val state = view.state
    val id = view.menuId
    val layer = id?.let { state.scene.layer(it) }
    val menuColors = MenuDefaults.itemColors(textColor = Am.OnDark, disabledTextColor = Am.OnDark.copy(alpha = 0.38f))
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = { view.menuAt = null }, containerColor = Am.Field) {
            fun close(action: () -> Unit) { view.menuAt = null; action() }
            if (id != null) {
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.timeline_cut), fontSize = 14.sp) }, onClick = { close { state.copySelected(); state.deleteChosen() } })
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.crash_copy), fontSize = 14.sp) }, onClick = { close { state.copySelected() } })
            }
            DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.downloader_paste), fontSize = 14.sp) }, enabled = state.canPaste, onClick = { close { state.pasteLayer() } })
            if (id != null) {
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.home_duplicate), fontSize = 14.sp) }, onClick = { close { state.duplicateChosen() } })
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.timeline_split), fontSize = 14.sp) }, onClick = { close { state.split() } })
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.delete), fontSize = 14.sp) }, onClick = { close { state.deleteChosen() } })
                if (MultiSelect.active && MultiSelect.ids.size > 1) DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.structure_group), fontSize = 14.sp) }, onClick = { close { scope.launch { state.groupLayersCentred(MultiSelect.ids) } } })
                if (MultiSelect.active && MultiSelect.ids.size > 1) DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.multi_stretch_timing), fontSize = 14.sp) }, enabled = state.chosenLayers.any { it.endTime > it.startTime }, onClick = { close { state.playing = false; stretch = MultiSelect.ids } })
                if (layer?.isPlainGroup == true && !MultiSelect.active) {
                    DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.layer_tool_edit_group), fontSize = 14.sp) }, onClick = { close { state.enterGroup(id) } })
                    DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.layer_tool_ungroup), fontSize = 14.sp) }, onClick = { close { state.askUngroup() } })
                }
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(if (layer?.hidden == true) R.string.a11y_show_layer else R.string.a11y_hide_layer), fontSize = 14.sp) }, onClick = { close { state.toggleHidden(id) } })
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.rename), fontSize = 14.sp) }, enabled = !MultiSelect.active, onClick = { close { view.renameId = id } })
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.timeline_bring_forward), fontSize = 14.sp) }, onClick = { close { state.edit { scene -> scene.reorder(id, (scene.layers.indexOfFirst { it.id == id } + 1).coerceAtMost(scene.layers.lastIndex)) } } })
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.timeline_send_backward), fontSize = 14.sp) }, onClick = { close { state.edit { scene -> scene.reorder(id, (scene.layers.indexOfFirst { it.id == id } - 1).coerceAtLeast(0)) } } })
            } else {
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.timeline_add_bookmark), fontSize = 14.sp) }, onClick = { close { if (state.scene.bookmarks.none { state.scene.frameAt(it) == state.frame }) state.toggleBookmark() } })
                DropdownMenuItem(colors = menuColors, text = { Text(stringResource(R.string.menu_select_all_layers), fontSize = 14.sp) }, onClick = { close { state.selectAll() } })
            }
        }
    }
}

@Composable
private fun LockMenu(view: TimelineView) {
    val (id, at) = view.lockMenu ?: return
    val state = view.state
    val current = state.scene.layer(id)?.node?.get("lock")
    val colors = MenuDefaults.itemColors(textColor = Am.OnDark)
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = { view.lockMenu = null }, containerColor = Am.Field) {
            listOf("all" to R.string.layer_lock, "timing" to R.string.layer_lock_timing, null to R.string.layer_unlock).forEach { (lock, label) ->
                DropdownMenuItem(text = { Text(stringResource(label), fontSize = 14.sp) }, trailingIcon = { if (current == lock) Text("✓", color = Am.Accent) }, colors = colors, onClick = { view.lockMenu = null; state.setLock(id, lock) })
            }
        }
    }
}

@Composable
private fun DesktopTimelineRename(view: TimelineView) {
    val id = view.renameId ?: return
    val layer = view.state.scene.layer(id) ?: return
    var name by remember(id) { mutableStateOf(layer.label) }
    val focus = remember(id) { FocusRequester() }
    fun confirm() {
        if (name.isNotBlank()) view.state.renameLayer(id, name.trim())
        view.renameId = null
    }
    AlertDialog(
        onDismissRequest = { view.renameId = null },
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(name, { name = it }, Modifier.focusRequester(focus), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { confirm() }))
            LaunchedEffect(id) { focus.requestFocus() }
        },
        confirmButton = { DialogPill(stringResource(R.string.ok), enabled = name.isNotBlank()) { confirm() } },
        dismissButton = { DialogPill(stringResource(R.string.cancel), primary = false) { view.renameId = null } },
    )
}

@Composable
private fun RowSemantics(view: TimelineView) {
    val rows by remember(view) { derivedStateOf { view.visibleRows() } }
    val select = stringResource(R.string.a11y_select_layer)
    val hide = stringResource(R.string.a11y_hide_layer)
    val show = stringResource(R.string.a11y_show_layer)
    val hidden = stringResource(R.string.a11y_layer_hidden)
    val lock = stringResource(R.string.layer_lock)
    val unlock = stringResource(R.string.layer_unlock)
    val locked = stringResource(R.string.a11y_layer_locked)
    val timingLocked = stringResource(R.string.a11y_layer_timing_locked)
    val transform = stringResource(R.string.inline_keyframes)
    val threeD = stringResource(R.string.three_d_layer)
    Box(Modifier.fillMaxSize().padding(top = RulerHeight).clipToBounds()) {
        for (row in rows) {
            val layer = view.layerAt(row) ?: continue
            key(layer.id) {
                val chosen = layer.id == view.state.selectedId || layer.id in MultiSelect.ids
                Box(Modifier.offset { IntOffset(0, (view.rowTop(row) - view.rulerH).roundToInt()) }.fillMaxWidth().height(RowHeight).tutorialGuard(any = true).semantics {
                    contentDescription = layer.shownName()
                    selected = chosen
                    listOfNotNull(hidden.takeIf { layer.hidden }, if (layer.locked) locked else timingLocked.takeIf { layer.timingLocked }).takeIf { it.isNotEmpty() }?.let { stateDescription = it.joinToString(", ") }
                    onClick(select) { view.tap(Hit.Header(layer.id)); true }
                    customActions = if (app.absentmotion.ui.tutorial.Tutorial.running || view.state.lookOnly) emptyList() else listOf(
                        CustomAccessibilityAction(if (layer.hidden) show else hide) { view.state.toggleHidden(layer.id); true },
                        ) + listOfNotNull(
                        CustomAccessibilityAction(if (layer.node["lock"] == null) lock else unlock) { view.state.toggleLock(layer.id); true }.takeIf { view.showsLayerControls(layer) },
                        CustomAccessibilityAction(transform) { view.toggleInlinePosition(layer.id); true },
                        CustomAccessibilityAction(threeD) { view.state.toggleLayer3D(layer.id); true }.takeIf { view.showsLayerControls(layer) && layer.canToggle3D && !layer.locked },
                    )
                })
            }
        }
    }
}

@Composable
private fun InlineRows(view: TimelineView, measurer: TextMeasurer, styles: TimelineStyles) {
    if (!view.hasInlinePositions) return
    val rows by remember(view) {
        derivedStateOf {
            view.visibleRows().mapNotNull { row ->
                view.layerAt(row)?.takeIf { view.inlinePosition(it) && view.liftId != it.id }?.let { row to it }
            }
        }
    }
    val visible = rows
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = RulerHeight).clipToBounds(),
        content = {
            for ((_, layer) in visible) key(layer.id) {
                Column(Modifier.fillMaxWidth()) {
                    val sections = view.inlineSections(layer)
                    if (sections.isNotEmpty() || !layer.isPlainGroup) {
                        InlinePositionDetails(view.state, layer, sections, Modifier.fillMaxWidth(),
                            xOf = view::inlineKeyX, selection = view.inlineKeySelection,
                            onToggleEffect = { view.toggleInlineEffect(listOf(layer.id), it) })
                    }
                    TimelineGroupContents(view.state, view.groupRows(layer), view.inlineKeySelection,
                        view::projectTimeX, view::expandedPath, view::toggleGroupRow, view::openGroupRow, view::toggleInlineEffect,
                        drawClip = { child, outline, left, right ->
                            val stripe = TimelineMath.frameTimeMs(2 * TimelineMath.tickStep(view.fps, view.dpPerSecond), view.fps) * view.timeScale().pxPerMs
                            drawGroupClip(child, outline, left, right, stripe, measurer, styles)
                        })
                }
            }
        },
    ) { measurables, constraints ->
        val unbounded = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val content = measurables.map { it.measure(unbounded) }
        val viewport = (view.rowsEnd - view.rulerH).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, viewport) {
            content.forEachIndexed { index, child ->
                val (row, layer) = visible[index]
                val top = view.shownTop(row, layer.id) + view.rowH - view.rulerH
                child.placeRelative(0, top.roundToInt())
            }
        }
    }
}

@Composable
private fun HeaderPictures(view: TimelineView, drawLift: DrawScope.() -> Unit) {
    val rows by remember(view) { derivedStateOf { view.visibleRows() } }
    Box(Modifier.fillMaxSize().padding(top = RulerHeight).clipToBounds()) {
        for (row in rows) {
            val layer = view.layerAt(row)
            if (layer != null) {
                key(layer.id) {
                    val lifted = layer.id == view.liftId
                    val open = view.inlinePosition(layer)
                    val controls = view.showsLayerControls(layer)
                    val thumbStart = thumbLeft(controls)
                    if (deskLayout) {
                        val eyeLabel = stringResource(if (layer.hidden) R.string.a11y_show_layer else R.string.a11y_hide_layer)
                        Box(Modifier.offset { IntOffset(0, (view.rowTop(row) - view.rulerH).roundToInt()) }) {
                            app.absentmotion.platform.Hint(eyeLabel) { Box(Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)) }
                        }
                        val lockLabel = stringResource(if (layer.node["lock"] == null) R.string.layer_lock else R.string.layer_unlock)
                        if (controls) Box(Modifier.offset { IntOffset(EyeZone.roundToPx(), (view.rowTop(row) - view.rulerH).roundToInt()) }) {
                            app.absentmotion.platform.Hint(lockLabel) { Box(Modifier.size(LockZone, 28.dp).pointerHoverIcon(PointerIcon.Hand)) }
                        }
                    }
                    val thumb = Modifier
                            .zIndex(if (lifted) 2f else 0f)
                            .offset {
                                val top = view.shownTop(row, layer.id) - view.rulerH + (RowHeight - ThumbSize).toPx() / 2
                                IntOffset(thumbStart.roundToPx(), top.roundToInt())
                            }
                            .then(if (lifted && !deskLayout) Modifier.graphicsLayer {
                                val grow = 1 + (LIFT_SCALE - 1) * view.liftAmount
                                scaleX = grow
                                scaleY = grow
                                transformOrigin = TransformOrigin((view.size.width / 2f - thumbStart.toPx()) / ThumbSize.toPx(), 0.5f)
                            } else Modifier)
                            .size(ThumbSize)
                            .alpha(if (layer.hidden) HIDDEN_ALPHA else 1f)
                    LayerThumbnail(layer, view.state.engine, thumb)
                    if (controls) Box(Modifier.offset { IntOffset(CubeLeft.roundToPx(), (view.shownTop(row, layer.id) - view.rulerH).roundToInt()) }
                        .size(ThreeDZone, RowHeight), contentAlignment = Alignment.Center) {
                        app.absentmotion.ui.editor.panels.PanelIcon(
                            ChromeIcons.ObjectTab, if (layer.uses3D) Layer3DColor else Am.OnDarkDim, size = 16.dp)
                    }
                    run {
                        val width = with(LocalDensity.current) { DisclosureZone.toPx() }
                        val left = view.rowHeaderWidth(layer) - width
                        Box(Modifier.offset { IntOffset(left.roundToInt(), (view.shownTop(row, layer.id) - view.rulerH).roundToInt()) }
                            .size(with(LocalDensity.current) { width.toDp() }, RowHeight), contentAlignment = Alignment.Center) {
                            app.absentmotion.ui.editor.panels.PanelIcon(
                                if (open) ChromeIcons.ChevronDown else ChromeIcons.ChevronRight,
                                if (open) Am.Accent else Am.OnDarkDim, size = 16.dp)
                        }
                    }
                }
            }
        }
        if (view.liftId != null && !deskLayout) Box(Modifier.fillMaxSize().zIndex(1f).drawBehind(drawLift))
    }
}

internal object TimelineMath {
    fun timeToX(timeMs: Float, nowMs: Float, originX: Float, pxPerMs: Float): Float = originX + (timeMs - nowMs) * pxPerMs

    fun xToTime(x: Float, nowMs: Float, originX: Float, pxPerMs: Float): Float = nowMs + (x - originX) / pxPerMs

    fun frameTimeMs(frame: Int, fps: Int): Float = frame * 1000f / fps

    fun nearestFrame(timeMs: Float, fps: Int): Int = (timeMs * fps / 1000f).roundToInt()

    fun timecode(frame: Int, fps: Int): String {
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${two(frame / fps / 60)}:${two(frame / fps % 60)}:${two(frame % fps)}"
    }

    fun tickStep(fps: Int, dpPerSecond: Float): Int =
        (1..fps).firstOrNull { fps % it == 0 && it * dpPerSecond / fps >= MIN_TICK_GAP_DP } ?: fps

    fun rowsBottom(height: Float, panelTop: Float?, barHeight: Float): Float = min(panelTop ?: height, height - barHeight)

    fun maxScroll(rows: Int, rowH: Float, top: Float, bottom: Float): Float = max(0f, rows * rowH - (bottom - top))

    fun centredScroll(row: Int, rowH: Float, top: Float, bottom: Float): Float = (row + 0.5f) * rowH - (bottom - top) / 2

    fun visibleRows(rows: Int, rowH: Float, top: Float, bottom: Float, scroll: Float): IntRange =
        max(0, floor(scroll / rowH).toInt())..min(rows - 1, ceil((bottom - top + scroll) / rowH).toInt() - 1)

    fun released(end: PointerInputChange?): Boolean = end == null || !end.isConsumed

    fun aboveAddMenu(y: Float, height: Float, menu: Float, ruler: Float): Boolean = y >= 0f && y < minOf(height - menu, ruler)

    fun snapFrame(wantedMs: Float, targets: Collection<Int>, fps: Int, zoneMs: Float): Int? {
        val best = targets.minByOrNull { abs(frameTimeMs(it, fps) - wantedMs) } ?: return null
        return best.takeIf { abs(frameTimeMs(it, fps) - wantedMs) <= zoneMs }
    }

    fun moveTarget(wantedMs: Float, spanMs: Float, spanFrames: Int, targets: Collection<Int>, fps: Int, zoneMs: Float): MoveTarget {
        val snapped = listOf(0f to 0, spanMs to spanFrames).flatMap { (offsetMs, offsetFrames) ->
            targets.map { hit -> Triple(hit - offsetFrames, hit, abs(frameTimeMs(hit, fps) - wantedMs - offsetMs)) }
        }.filter { it.first >= 0 && it.third <= zoneMs }.minByOrNull { it.third }
        return snapped?.let { MoveTarget(it.first, it.second) } ?: MoveTarget(nearestFrame(wantedMs, fps).coerceAtLeast(0), null)
    }

    fun movedStart(scene: Scene, layer: Layer, frame: Int): Int =
        if (frame == scene.firstShownFrame(layer.startTime)) layer.startTime else scene.frameStart(frame)

    fun snapTargets(scene: Scene, skip: Long, playhead: Int): Set<Int> = snapTargets(scene, setOf(skip), playhead)

    fun snapTargets(scene: Scene, skip: Set<Long>, playhead: Int): Set<Int> = buildSet {
        add(playhead)
        scene.bookmarks.forEach { add(scene.firstShownFrame(it)) }
        for (layer in scene.layers) if (layer.id !in skip) {
            add(scene.firstShownFrame(layer.startTime))
            add(scene.lastShownFrame(layer.endTime) + 1)
            layer.keyframeTimes().forEach { add(scene.frameAt(it)) }
        }
    }

    fun groupTarget(scene: Scene, grabbed: Layer, others: List<Layer>, found: MoveTarget): MoveTarget {
        if (others.isEmpty()) return found
        val first = scene.firstShownFrame(grabbed.startTime)
        val lowest = minOf(first, others.minOf { scene.firstShownFrame(it.startTime) })
        return if (found.frame - first >= -lowest) found else MoveTarget(first - lowest, null)
    }

    fun movedClips(scene: Scene, grabbed: Layer, others: List<Layer>, frame: Int): Scene {
        val shift = frame - scene.firstShownFrame(grabbed.startTime)
        return (listOf(grabbed) + others).fold(scene) { acc, layer ->
            acc.update(layer.id) { acc.movedClip(layer, movedStart(acc, layer, acc.firstShownFrame(layer.startTime) + shift)) }
        }
    }

    fun edgeMs(layer: Layer, start: Boolean): Float = (if (start) layer.startTime else layer.endTime + 1).toFloat()

    fun trimmedEdge(from: Scene, base: Layer, start: Boolean, wantedMs: Float): EdgeTrim {
        val frame = nearestFrame(wantedMs, from.fps)
        val edge = if (start) frame.coerceIn(0, from.lastShownFrame(base.endTime).coerceAtLeast(0)) else (frame - 1).coerceAtLeast(from.firstShownFrame(base.startTime)) + 1
        val time = if (start) from.frameStart(edge) else from.frameEnd(edge - 1)
        val trimmed = from.withEdgeMotions(base, if (start) from.trimmedClip(base, time, base.endTime) else from.trimmedClip(base, base.startTime, time))
        return EdgeTrim(trimmed, edge, time, if (start) trimmed.startTime > time else trimmed.endTime < time)
    }

    fun trimmedEdges(from: Scene, grabbed: Layer, others: List<Layer>, start: Boolean, wantedMs: Float): List<EdgeTrim> {
        val anchor = edgeMs(grabbed, start)
        return (listOf(grabbed) + others).map { trimmedEdge(from, it, start, wantedMs + (edgeMs(it, start) - anchor)) }
    }
}

internal data class MoveTarget(val frame: Int, val snap: Int?)

internal class EdgeTrim(val layer: Layer, val edge: Int, val time: Int, val limited: Boolean)

private data class StripWindow(val rows: IntRange, val fromMs: Float, val toMs: Float)

private const val STRIP_WINDOW_MS = 2000f

private class TimeScale(val nowMs: Float, val originX: Float, val pxPerMs: Float) {
    fun x(timeMs: Float) = TimelineMath.timeToX(timeMs, nowMs, originX, pxPerMs)
    fun time(x: Float) = TimelineMath.xToTime(x, nowMs, originX, pxPerMs)
    fun slid(dx: Float) = TimeScale(nowMs, originX + dx, pxPerMs)
}

private sealed interface Hit {
    data object Ruler : Hit
    data object Corner : Hit
    data object InlineBody : Hit
    data object Empty : Hit

    data object Readout : Hit
    data class WorkAreaEdge(val start: Boolean) : Hit
    data class Inline(val id: Long) : Hit
    data class Cube(val id: Long) : Hit
    data class Eye(val id: Long) : Hit
    data class Lock(val id: Long) : Hit
    data class Locked(val id: Long) : Hit

    data class Key(val key: InlineKey) : Hit
    data class Bar(val id: Long) : Hit
    data class Header(val id: Long) : Hit
    data class Grip(val id: Long, val start: Boolean) : Hit
    data class Handle(val id: Long) : Hit
    data class Row(val id: Long) : Hit
}

private enum class Drag(val edit: Boolean = false) { Scrub, Scroll, Pinch, Spent, Select, TrimStart(true), TrimEnd(true), Reorder(true), Lift(true), KeyBox(true), Keys(true), WorkAreaStart(true), WorkAreaEnd(true) }

private const val MOVE_EDIT = "clip-move"
private const val KEYFRAME_HOLD_MS = 250L


private class TimelineView(val state: EditorState, private val density: Density, private val scope: CoroutineScope) {
    var wide = false
    var rail = false
    private val scale = density.density
    private val Dp.px get() = value * scale
    private val decay = splineBasedDecay<Float>(density)

    val rulerH = RulerHeight.px
    val rowH = RowHeight.px
    var coordinates: LayoutCoordinates? = null
    var headerDp by mutableFloatStateOf(DeskTimelineControls.headerDp)
    val headerWidth: Float get() = if (deskLayout) headerDp * scale else HeaderWidth.px
    fun showsLayerControls(layer: Layer): Boolean = layer.id == state.selectedId || layer.id in MultiSelect.ids || inlinePosition(layer)
    fun rowHeaderWidth(layer: Layer): Float = if (!deskLayout && showsLayerControls(layer)) headerWidth + ExpandedControls.px else headerWidth
    private var inlinePositions by mutableStateOf<Set<List<Long>>>(emptySet())
    private var inlineEffects by mutableStateOf<Set<Pair<List<Long>, String>>>(emptySet())
    val inlineKeySelection = InlineKeySelection()
    val hasInlinePositions: Boolean get() = layers.any(::inlinePosition)

    var catalog by mutableStateOf(EffectCatalog())
    private val sections by derivedStateOf {
        layers.filter(::inlinePosition).associate {
            val active = state.inlineProperty.takeIf { _ -> it.id == state.selectedId }
            it.id to inlineTrackSections(it, catalog, active) { effect -> inlineEffectExpanded(listOf(it.id), effect) }
        }
    }

    fun expandedPath(path: List<Long>): Boolean = (state.groupPath + path) in inlinePositions

    fun inlinePosition(layer: Layer): Boolean = expandedPath(listOf(layer.id))

    private fun inlineEffectExpanded(path: List<Long>, effect: String): Boolean =
        (state.groupPath + path to effect) in inlineEffects

    fun toggleInlineEffect(path: List<Long>, section: InlineSection) {
        val key = state.groupPath + path to section.id
        if (key in inlineEffects) {
            inlineEffects = inlineEffects - key
            if (path.size == 1) {
                val refs = section.tracks.map { it.ref }.toSet()
                inlineKeySelection.keys = inlineKeySelection.keys.filterTo(mutableSetOf()) {
                    it.layerId != path.single() || it.ref !in refs
                }
            }
        } else {
            inlineEffects = inlineEffects + key
        }
    }

    private val groupRows by derivedStateOf {
        layers.filter { inlinePosition(it) && it.isPlainGroup }.associate { layer ->
            layer.id to timelineGroupRows(state.scene, layer, catalog, ::inlineEffectExpanded, ::expandedPath)
        }
    }

    fun groupRows(layer: Layer): List<TimelineGroupRow> = groupRows[layer.id].orEmpty()

    fun toggleGroupRow(path: List<Long>) {
        val absolute = state.groupPath + path
        inlinePositions = if (absolute in inlinePositions) inlinePositions - setOf(absolute) else inlinePositions + setOf(absolute)
    }

    fun openGroupRow(row: TimelineGroupRow, ref: app.absentmotion.model.PropRef?) {
        if (!row.editable || !state.selectGroupMember(row.groups, row.layer.id)) return
        if (ref != null) state.selected?.let { state.openInlineEditor(it, ref) }
    }

    fun projectTimeX(time: Double): Float = timeScale().x(time.toFloat())

    fun inlineSections(layer: Layer): List<InlineSection> = sections[layer.id].orEmpty()

    fun toggleInlinePosition(id: Long) {
        val layer = state.scene.layer(id) ?: return
        toggleGroupRow(listOf(id))
        inlineKeySelection.retain(layers.filter(::inlinePosition).map { it.id }.toSet())
        state.timelineDetailsOpen = hasInlinePositions
        state.endGesture()
        if (!layer.locked) {
            state.selectedId = id
            state.panel = EditorPanel.Tools
        }
        state.easingOpen = false
        state.volumeOpen = false
        state.keyTarget = null
        state.inlineProperty = null
        state.inlinePanelHidden = true
    }

    fun retainInlinePositions(layers: Set<Long>) {
        val path = state.groupPath
        inlinePositions = inlinePositions.filterTo(mutableSetOf()) { row ->
            row.size <= path.size || row.take(path.size) != path || row[path.size] in layers
        }
        inlineKeySelection.retain(this.layers.filter(::inlinePosition).map { it.id }.toSet())
        state.timelineDetailsOpen = hasInlinePositions
    }

    fun restoreExpandedRows() {
        inlineKeySelection.clear()
        state.timelineDetailsOpen = hasInlinePositions
    }

    private fun inlineFrames(layer: Layer): List<Int> =
        inlineSections(layer).flatMap { section -> section.visibleTracks.flatMap { it.frames(state.scene, layer) } }

    private fun groupFrames(layer: Layer): List<Int> = groupRows(layer).flatMap { row ->
        row.details.orEmpty().flatMap { section ->
            section.visibleTracks.flatMap { track ->
                track.frames(row.owner, row.layer).mapNotNull { frame ->
                    row.hostTime(row.owner.frameStart(frame))?.roundToInt()?.let(state.scene::frameAt)
                }
            }
        }
    }

    fun inlineKeyframes(): List<Int> = layers.filter(::inlinePosition).flatMap { inlineFrames(it) + groupFrames(it) }
        .filter { it in 0..state.scene.lastFrame }.distinct().sorted()

    fun inlineKeyX(frame: Int): Float = timeScale().x(state.scene.frameStart(frame).toFloat())
    fun inlineFrameAt(x: Float): Int = TimelineMath.nearestFrame(timeScale().time(x), fps)

    private var viewStartMs by mutableFloatStateOf(0f)
    var desktopCancel by mutableStateOf(false)
    var rubber by mutableStateOf<Rect?>(null)
    var keyBox by mutableStateOf<Rect?>(null)
        private set
    private var keyBoxStart = Offset.Unspecified
    private var keyMove: InlineKeyMove? = null
    private var keyMoveFrame = 0
    private var keyMoveOffset = 0f
    private var keyMoveDelta: Int? = null

    private fun keyPoints(row: Int, layer: Layer) = sequence {
        if (layer.locked || layer.timingLocked || state.lookOnly) return@sequence
        val top = rowTop(row)
        val ref = scopeOf(layer)?.ref
        if (ref != null && !app.absentmotion.editor.InlineTrack(ref, "").linked(layer)) {
            for (frame in keyFrames(layer)) {
                val at = Offset(inlineKeyX(frame), top + rowH / 2)
                if (at.x >= rowHeaderWidth(layer)) yield(InlineKey(layer.id, ref, frame) to at)
            }
        }
        for (section in inlineSections(layer)) for (track in section.visibleTracks) {
            if (track.linked(layer)) continue
            val offset = with(density) { inlineTrackOffset(inlineSections(layer), track.ref) } ?: continue
            val center = top + rowH + offset + with(density) { inlineTrackHeight.roundToPx() } / 2f
            for (frame in track.frames(state.scene, layer)) {
                yield(InlineKey(layer.id, track.ref, frame) to Offset(inlineKeyX(frame), center))
            }
        }
    }

    private fun keyAt(at: Offset, row: Int, layer: Layer): InlineKey? {
        val labelWidth = minOf(172.dp.px, size.width * 0.59f)
        if (at.y >= rowTop(row) + rowH && at.x < labelWidth) return null
        return keyPoints(row, layer).filter { abs(it.second.y - at.y) < 10.dp.px && abs(it.second.x - at.x) < 10.dp.px }
            .minByOrNull { (it.second - at).getDistance() }?.first
    }

    private fun visibleKeyPoints() = visibleRows().asSequence().flatMap { row ->
        layerAt(row)?.let { keyPoints(row, it) } ?: emptySequence()
    }.filter { (_, at) -> at.x in 0f..size.width.toFloat() && at.y in rulerH..rowsEnd }

    fun canBoxKeys(hit: Hit): Boolean = !MultiSelect.active && when (hit) {
        is Hit.Key, Hit.InlineBody, Hit.Empty, is Hit.Row, is Hit.Bar -> visibleKeyPoints().any()
        else -> false
    }

    fun startKeyBox(at: Offset, seed: InlineKey? = null) {
        state.playing = false
        keyBoxStart = at
        updateKeyBox(at)
        if (seed != null) inlineKeySelection.keys = inlineKeySelection.keys + seed
    }

    private fun updateKeyBox(at: Offset) {
        if (!keyBoxStart.isSpecified) return
        val start = keyBoxStart
        val rect = Rect(minOf(start.x, at.x), minOf(start.y, at.y), maxOf(start.x, at.x), maxOf(start.y, at.y))
            .inflate(1.dp.px).intersect(Rect(0f, rulerH, size.width.toFloat(), rowsEnd))
        keyBox = rect
        inlineKeySelection.keys = visibleKeyPoints().filter { (_, point) -> rect.contains(point) }.map { it.first }.toSet()
    }

    fun startKeyMove(hit: Hit.Key, from: Offset) {
        state.playing = false
        val key = hit.key
        val group = inlineKeySelection.keys
        val active = if (key in group) group else setOf(key)
        inlineKeySelection.keys = active
        keyMove = InlineKeyMove(state.scene, active)
        keyMoveFrame = key.frame
        keyMoveOffset = from.x - inlineKeyX(key.frame)
        keyMoveDelta = null
    }

    private fun updateKeyMove(at: Offset) {
        val move = keyMove ?: return
        val delta = inlineFrameAt(at.x - keyMoveOffset) - keyMoveFrame
        if (delta == keyMoveDelta) return
        keyMoveDelta = delta
        state.edit("inline-key-move") { scene ->
            val (updated, keys) = move.apply(scene, delta)
            inlineKeySelection.keys = keys
            updated
        }
    }

    private fun tapKey(key: InlineKey) {
        state.playing = false
        val selection = inlineKeySelection.keys
        if (selection.isEmpty()) state.stepTo(key.frame)
        else inlineKeySelection.keys = if (key in selection) selection - key else selection + key
    }
    var resizingHeader by mutableStateOf(false)
    var hoveredId by mutableStateOf<Long?>(null)
    var resizeHover by mutableStateOf(false)
    var handHover by mutableStateOf(false)
    var menuAt by mutableStateOf<Offset?>(null)
    var menuId by mutableStateOf<Long?>(null)
    var renameId by mutableStateOf<Long?>(null)
    var lockMenu by mutableStateOf<Pair<Long, Offset>?>(null)
    var snapDisabled = false
    private var lockHint: Int? = null

    var size by mutableStateOf(IntSize.Zero)
        private set
    private var initialFit = false
    fun resizeViewport(value: IntSize) {
        size = value
        if (deskLayout && value.width > headerWidth && value.height > 0) {
            if (!initialFit) { initialFit = true; fit() } else refit()
        }
    }

    val dpPerSecond: Float get() = state.timelineZoom
    private var scrollPx by mutableFloatStateOf(0f)

    private var scrubMs by mutableStateOf<Float?>(null)
    private var scrubFrame = 0
    private var motion: Job? = null

    private var dragId = 0L
    private var edgeMs = 0f
    private var trimBase: Layer? = null
    private var trimScene: Scene? = null
    private var trimGroup: List<Layer> = emptyList()
    var limits by mutableStateOf<Map<Long, Boolean>>(emptyMap())
        private set
    var onTick: () -> Unit = {}
    var snapAt by mutableStateOf<Int?>(null)
        private set
    private var snapTargets = emptySet<Int>()

    private class Slide(val from: Float, val at: Long)

    private var selectAnchor: Long? = null
    private var selectBase = emptySet<Long>()
    private var selectAdd = true
    private var selectStart = 0f
    private var selectFinger = 0f
    private var selectSlop = 0f
    private var selectPush = true
    private var selectArmed = false
    private var selectRows = 1

    var liftId by mutableStateOf<Long?>(null)
        private set
    private var liftTop by mutableFloatStateOf(0f)
    private var frameMs by mutableLongStateOf(0L)
    private var liftJob: Job? = null
    private var liftAt = 0L
    private var liftGrab = 0f
    private var liftFinger = 0f
    private var liftStart = 0f
    private var liftSlop = 0f
    private var liftArmed = false
    private var liftPush = true
    private var dropping = false
    private var dropAt = 0L
    private var dropFrom = 0f
    private val slides = HashMap<Long, Slide>()

    var moveId by mutableStateOf<Long?>(null)
        private set
    var moveSlide by mutableFloatStateOf(0f)
        private set
    private var moveLayer: Layer? = null
    private var moveGroup: List<Layer> = emptyList()
    private var moveGroupIds: Set<Long> = emptySet()
    private var moveBase: Scene? = null
    private var moveDx = 0f
    private var moveArmed = false
    private var liftMoves = false
    private var liftStartX = 0f

    val liftMoved: Boolean get() = liftArmed || moveArmed

    private val layersState = derivedStateOf(referentialEqualityPolicy()) { state.scene.layers }

    val layers: List<Layer> get() = layersState.value
    val fps: Int get() = state.scene.fps.coerceAtLeast(1)

    private val nowMs: Float
        get() = scrubMs?.takeIf { state.playheadFrame == scrubFrame } ?: TimelineMath.frameTimeMs(state.playheadFrame, fps)

    fun timeScale(): TimeScale {
        val pxPerMs = dpPerSecond * scale / 1000f
        val origin = if (deskLayout) headerWidth + (nowMs - viewStartMs) * pxPerMs else (size.width - PlayheadWidth.px) / 2f
        return TimeScale(nowMs, origin, pxPerMs)
    }

    fun frameAtWindow(point: Offset): Int? {
        val local = coordinates?.takeIf { it.isAttached }?.windowToLocal(point) ?: return null
        if (local.x < headerWidth || local.x >= size.width || local.y < rulerH || local.y >= rowsBottom) return null
        return TimelineMath.nearestFrame(timeScale().time(local.x), fps).coerceAtLeast(0)
    }

    private val maxZoom: Float get() = fps * 40f

    private val fitZoom: Float
        get() = ((size.width - headerWidth).coerceAtLeast(1f) / scale * 1000f / TimelineMath.frameTimeMs(state.scene.lastFrame + 1, fps).coerceAtLeast(1f)).coerceAtMost(maxZoom)

    private val minZoom: Float get() = min(fitZoom / 2f, (size.width - headerWidth).coerceAtLeast(1f) / scale * 1000f / FAR_OUT_MS)

    private val endMs: Float
        get() = max(TimelineMath.frameTimeMs(state.scene.lastFrame + 1, fps), (layers.maxOfOrNull { it.endTime + 1 } ?: 0).toFloat())

    private val visibleMs: Float get() = (size.width - headerWidth).coerceAtLeast(0f) / timeScale().pxPerMs

    private var followFit = true

    val rowsEnd: Float get() = rowsBottom
    val maxScroll: Float get() = if (hasInlinePositions) max(0f, inlineOffsets.last() - (rowsBottom - rulerH)) else TimelineMath.maxScroll(layers.size, rowH, rulerH, rowsBottom)
    private val panMax: Float get() = (endMs - visibleMs / 2f).coerceAtLeast(0f)
    val panSpan: Float get() = (visibleMs / (panMax + visibleMs).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val panFraction: Float get() = if (panMax > 0f) viewStartMs / panMax else 0f

    private fun setViewStart(value: Float) {
        viewStartMs = value.coerceIn(0f, panMax)
    }

    fun refit() {
        if (followFit && size.width > headerWidth) fit()
    }

    fun pagePlayback() {
        if (size.width <= headerWidth) return
        val x = timeScale().originX
        if (x >= size.width || x < headerWidth) setViewStart(nowMs)
    }

    fun revealRow() {
        if (desktopDragging) return
        if (state.editingGroupMember) revealGroupMember() else state.selectedId?.let(::revealRow)
    }

    private fun revealGroupMember() {
        val groups = state.selectedGroups
        val id = state.selectedId ?: return
        val rootRow = groups.firstOrNull()?.let(::rowOf) ?: return
        val rootLayer = layerAt(rootRow) ?: return
        val target = groups + id
        var top = rowTop(rootRow) + rowH
        val sections = inlineSections(rootLayer)
        if (sections.isNotEmpty()) top += with(density) { inlineDetailsHeight(sections) }
        for (row in groupRows(rootLayer)) {
            val bottom = top + row.heightPx(density)
            if (row.path == target) {
                keepRowVisible(top, top + row.headerHeightPx(density))
                return
            }
            top = bottom
        }
    }

    private fun revealRow(id: Long) {
        val row = rowOf(id) ?: return
        val top = rowTop(row)
        keepRowVisible(top, top + rowH)
    }

    private fun keepRowVisible(top: Float, bottom: Float) {
        if (top < rulerH) scrollPx = scroll - (rulerH - top)
        else if (bottom > rowsBottom) scrollPx = scroll + (bottom - rowsBottom)
    }

    fun revealPlayhead() {
        if (size.width <= headerWidth || desktopDragging) return
        val x = timeScale().originX
        if (x > size.width || x < headerWidth) setViewStart(nowMs - visibleMs * 0.1f)
    }

    fun pan(dx: Float) {
        setViewStart(viewStartMs + dx / timeScale().pxPerMs)
    }

    fun seek(x: Float) {
        state.playing = false
        state.stepTo(TimelineMath.nearestFrame(timeScale().time(x.coerceIn(headerWidth, size.width.toFloat().coerceAtLeast(headerWidth))), fps).coerceIn(0, state.scene.lastFrame + 1))
        scrubMs = null
    }

    fun fit() {
        state.timelineZoom = fitZoom
        followFit = true
        viewStartMs = 0f
    }

    fun wheel(delta: Offset, at: Offset, zoom: Boolean, sideways: Boolean) {
        stopMotion()
        when {
            zoom -> zoomBy(1.12f.pow(-delta.y), at.x)
            sideways || abs(delta.x) > abs(delta.y) -> pan((if (delta.x != 0f) delta.x else delta.y) * 48.dp.px)
            else -> scrollBy(-delta.y * 32.dp.px)
        }
    }

    fun hover(at: Offset?) {
        val hit = at?.let { hitAt(it) }
        hoveredId = hit?.let { clipId(it) }
        resizeHover = hit is Hit.Grip || at != null && nearHeaderEdge(at.x)
        handHover = hit is Hit.Inline || hit is Hit.Cube || hit is Hit.Eye || hit is Hit.Lock || hit is Hit.Locked || hit is Hit.Header || hit is Hit.Bar || hit == Hit.Readout || hit == Hit.Ruler
    }

    fun clipId(hit: Hit): Long? = when (hit) {
        is Hit.Bar -> hit.id
        is Hit.Grip -> hit.id
        is Hit.Header -> hit.id
        else -> null
    }

    fun cancelDesktopDrag(): Boolean {
        if (!desktopDragging) return false
        desktopCancel = true
        snapDisabled = false
        cancelAction?.invoke()
        cancelAction = null
        desktopDragging = false
        return true
    }

    var cancelAction: (() -> Unit)? = null
    var desktopDragging by mutableStateOf(false)
    private var desktopAnchor: Long? = null
    fun selectDesktop(id: Long, toggle: Boolean, range: Boolean) {
        val anchor = state.selectedId ?: desktopAnchor?.takeIf { it in MultiSelect.ids } ?: MultiSelect.ids.firstOrNull()
        val a = if (range) anchor?.let { rowOf(it) } else null
        if (a == null) desktopAnchor = id
        if (a != null) {
            val b = rowOf(id) ?: return
            MultiSelect.ids = (min(a, b)..max(a, b)).mapNotNull { layerAt(it)?.takeUnless { it.locked }?.id }.toSet()
            MultiSelect.active = true
            state.deselectLayer()
        } else if (toggle) {
            val chosen = MultiSelect.ids + listOfNotNull(state.selectedId)
            MultiSelect.ids = if (id in chosen) chosen - id else chosen + id
            MultiSelect.active = MultiSelect.ids.isNotEmpty()
            state.deselectLayer()
        } else if (range || !(MultiSelect.active && id in MultiSelect.ids)) {
            val same = state.selectedId == id
            MultiSelect.clear()
            state.selectedId = id
            if (!same) state.panel = EditorPanel.Tools
        }
    }

    private var rubberTime = 0f
    private var rubberY = 0f
    private var rubberAt: Offset? = null
    private var rubberJob: Job? = null

    fun rubberStart(at: Offset) {
        rubberTime = timeScale().time(at.x)
        rubberY = at.y + scroll
    }

    fun rubberTo(at: Offset) {
        rubberAt = at
        rubberRefresh()
        if (rubberJob?.isActive != true) rubberJob = scope.launch { rubberLoop() }
    }

    fun rubberEnd() {
        rubberAt = null
        rubber = null
    }

    fun rubberRefresh() {
        val at = rubberAt ?: return
        val time = timeScale()
        val x = at.x.coerceIn(headerWidth, size.width.toFloat().coerceAtLeast(headerWidth))
        val y = at.y.coerceIn(rulerH, rowsBottom.coerceAtLeast(rulerH))
        val startX = time.x(rubberTime)
        val startY = rubberY - scroll
        val rect = Rect(min(startX, x), min(startY, y), max(startX, x), max(startY, y))
        rubberSelect(rect)
        rubber = rect.intersect(Rect(headerWidth, rulerH, size.width.toFloat(), rowsBottom))
    }

    private suspend fun rubberLoop() {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val step = min((now - last) / 1e9f, 0.05f)
            last = now
            val at = rubberAt ?: break
            val zone = 32.dp.px
            val down = when {
                at.y > rowsBottom - zone -> (at.y - (rowsBottom - zone)) / zone
                at.y < rulerH + zone -> -(rulerH + zone - at.y) / zone
                else -> 0f
            }.coerceIn(-1.5f, 1.5f)
            val right = when {
                at.x > size.width - zone -> (at.x - (size.width - zone)) / zone
                at.x < headerWidth + zone -> -(headerWidth + zone - at.x) / zone
                else -> 0f
            }.coerceIn(-1.5f, 1.5f)
            if (down == 0f && right == 0f) break
            if (down != 0f) scrollBy(-down * 600.dp.px * step)
            if (right != 0f) pan(right * 700.dp.px * step)
            rubberRefresh()
        }
    }

    fun rubberSelect(rect: Rect) {
        val time = timeScale()
        val ids = layers.indices.mapNotNull { row ->
            val layer = layerAt(row) ?: return@mapNotNull null
            val clip = Rect(time.x(layer.startTime.toFloat()), rowTop(row), time.x(layer.endTime + 1f), rowTop(row) + rowH)
            layer.id.takeIf { clip.overlaps(rect) && !layer.locked }
        }.toSet()
        state.deselectLayer()
        MultiSelect.ids = ids
        MultiSelect.active = ids.isNotEmpty()
    }

    fun resizeHeader(x: Float) { headerDp = (x / scale).coerceIn(120f, 320f); DeskTimelineControls.headerDp = headerDp; refit() }
    fun nearHeaderEdge(x: Float) = x <= headerWidth && x >= headerWidth - 5.dp.px

    private val covered: Boolean get() = !wide && !deskLayout && state.showsLayerInspector

    private val multiBar: Float get() = if (MultiSelect.active && !deskLayout && !rail) MultiBarHeight.px else 0f

    private val panelTop: Float get() = size.height - PanelHeight.px

    private val rowsBottom: Float
        get() = TimelineMath.rowsBottom(size.height.toFloat(), if (covered) panelTop else null, multiBar)

    val scroll: Float
        get() = scrollPx.coerceIn(0f, maxScroll)

    fun layerAt(row: Int): Layer? = layers.getOrNull(layers.lastIndex - row)

    private val inlineOffsets by derivedStateOf {
        var offset = 0f
        buildList {
            for (layer in layers.asReversed()) {
                add(offset)
                offset += rowH
                if (inlinePosition(layer)) {
                    val sections = inlineSections(layer)
                    if (sections.isNotEmpty() || !layer.isPlainGroup) offset += with(density) { inlineDetailsHeight(sections) }
                    offset += groupRows(layer).sumOf { it.heightPx(density) }
                }
            }
            add(offset)
        }
    }

    private fun rowOffset(row: Int): Float = if (hasInlinePositions) inlineOffsets[row] else row * rowH

    fun rowTop(row: Int): Float = rulerH + rowOffset(row) - scroll

    private fun rowAt(y: Float): Int {
        val offset = y - rulerH + scroll
        if (!hasInlinePositions) return floor(offset / rowH).toInt()
        val index = inlineOffsets.binarySearch(offset)
        return if (index >= 0) index else -index - 2
    }

    fun visibleRows(): IntRange =
        if (hasInlinePositions) max(0, rowAt(rulerH))..min(layers.lastIndex, rowAt(rowsBottom))
        else TimelineMath.visibleRows(layers.size, rowH, rulerH, rowsBottom, scroll)

    fun clipRows(media: Boolean): List<Rect> {
        val time = timeScale()
        val from = headerWidth
        val to = size.width.toFloat()
        return visibleRows().mapNotNull { row ->
            val layer = layerAt(row) ?: return@mapNotNull null
            val kind = kindOf(layer)
            val top = maxOf(rowTop(row), rulerH)
            val bottom = minOf(rowTop(row) + rowH, rowsBottom)
            val left = maxOf(time.x(layer.startTime.toFloat()), from)
            val right = minOf(time.x(layer.endTime + 1f), to)
            if (bottom > top && right > left && (kind == TourFacts.MEDIA || !media && kind == TourFacts.TEXT)) Rect(left, top, right, bottom) else null
        }
    }

    fun stripWindow(): StripWindow {
        val time = timeScale()
        return StripWindow(visibleRows(), floor(time.time(0f) / STRIP_WINDOW_MS) * STRIP_WINDOW_MS,
            ceil(time.time(size.width.toFloat()) / STRIP_WINDOW_MS) * STRIP_WINDOW_MS)
    }

    private val keyFrames = HashMap<Pair<Long, KeyScope?>, List<Int>>()
    private val keyFrameNodes = HashMap<Pair<Long, KeyScope?>, app.absentmotion.model.XmlNode>()

    fun scopeOf(layer: Layer): KeyScope? =
        if (layer.id == state.selectedId && state.showsLayerInspector &&
            (state.panel != EditorPanel.Tools || state.volumeOpen)) state.keyScope else null

    private var keyFramesFps = 0

    fun keyFrames(layer: Layer): List<Int> {
        if (layer.id != state.selectedId) return emptyList()
        val scope = scopeOf(layer)
        if (scope == null && inlinePosition(layer)) return inlineFrames(layer).distinct()
        val fps = state.scene.fps
        if (keyFramesFps != fps || keyFrames.size > layers.size * 4 + 16) {
            keyFrames.clear()
            keyFrameNodes.clear()
            keyFramesFps = fps
        }
        val key = layer.id to scope
        val cached = keyFrames[key]
        if (cached != null && keyFrameNodes[key] === layer.node) return cached
        return layer.keyframeTimes(scope).map { state.scene.frameAt(it) }.distinct().also {
            keyFrames[key] = it
            keyFrameNodes[key] = layer.node
        }
    }

    private var stepsFps = 0
    private var stepsZoom = 0f
    private var stepsCache: DeskRuler.Steps? = null
    private var labelFps = 0
    private var labelMajor = 0
    private val rulerLabels = HashMap<Int, String>()

    fun rulerSteps(): DeskRuler.Steps {
        val cached = stepsCache
        if (cached != null && stepsFps == fps && stepsZoom == dpPerSecond) return cached
        stepsFps = fps
        stepsZoom = dpPerSecond
        return DeskRuler.steps(fps, dpPerSecond / fps).also { stepsCache = it }
    }

    fun rulerLabel(frame: Int, major: Int): String {
        if (labelFps != fps || labelMajor != major || rulerLabels.size > 512) {
            rulerLabels.clear()
            labelFps = fps
            labelMajor = major
        }
        return rulerLabels.getOrPut(frame) { DeskRuler.label(frame, fps, major) }
    }

    private var scrollBeforeSelect: Float? = null

    fun revealSelected() {
        val id = state.selectedId
        if (id == null) {
            scrollBeforeSelect?.takeUnless { MultiSelect.active }?.let { scrollPx = it }
            scrollBeforeSelect = null
            return
        }
        if (state.editingGroupMember) {
            revealGroupMember()
            return
        }
        val index = layers.indexOfFirst { it.id == id }
        if (index < 0) return
        if (scrollBeforeSelect == null) scrollBeforeSelect = scrollPx
        scrollPx = rowOffset(layers.lastIndex - index) + (rowH - (rowsBottom - rulerH)) / 2
    }

    fun isCovered(at: Offset): Boolean = (covered && at.y >= panelTop) || (multiBar > 0f && at.y >= size.height - multiBar)

    fun dismissZone(at: Offset): Boolean = state.addOpen && TimelineMath.aboveAddMenu(at.y, size.height.toFloat(), AddPopupHeight.px, rulerH)

    fun dismissAddMenu(): Boolean {
        if (!state.addOpen) return false
        state.addOpen = false
        return true
    }

    fun hitAt(at: Offset): Hit {
        if (at.y < rulerH) {
            if (deskLayout && at.x < headerWidth) return Hit.Corner
            if (!state.lookOnly && layers.isNotEmpty()) {
                val time = timeScale()
                val startX = time.x(TimelineMath.frameTimeMs(state.scene.workAreaStartFrame, fps))
                val endX = time.x(TimelineMath.frameTimeMs(state.scene.workAreaEndFrame, fps))
                val inTimeline = { x: Float -> !deskLayout || x >= headerWidth }
                val nearStart = inTimeline(startX) && abs(at.x - startX) <= WorkAreaHitZone.px
                val nearEnd = inTimeline(endX) && abs(at.x - endX) <= WorkAreaHitZone.px
                if (nearStart || nearEnd) return Hit.WorkAreaEdge(nearStart && (!nearEnd || abs(at.x - startX) <= abs(at.x - endX)))
            }
            val origin = timeScale().originX
            if (deskLayout && origin < headerWidth) return Hit.Ruler
            val centre = (origin + PlayheadWidth.px / 2).let { if (deskLayout) maxOf(it, headerWidth + PillInset.px + PillWidth.px / 2) else it }
            return if (at.y >= ReadoutTop.px && abs(at.x - centre) <= ReadoutHalfWidth.px) Hit.Readout else Hit.Ruler
        }
        val row = rowAt(at.y)
        val layer = layerAt(row) ?: return Hit.Empty
        keyAt(at, row, layer)?.let { return Hit.Key(it) }
        if (at.y >= rowTop(row) + rowH) return Hit.InlineBody
        val time = timeScale()
        val left = time.x(layer.startTime.toFloat())
        val right = time.x(layer.endTime + 1f)
        val selected = layer.id == state.selectedId
        val gripped = selected || !deskLayout && layer.id in MultiSelect.ids
        val zone = edgeZone(left, right)
        val header = rowHeaderWidth(layer)
        val controls = showsLayerControls(layer)
        return when {
            at.x < EyeZone.px -> Hit.Eye(layer.id)
            controls && at.x < CubeLeft.px -> Hit.Lock(layer.id)
            controls && at.x < (CubeLeft + ThreeDZone).px && layer.canToggle3D -> Hit.Cube(layer.id)
            at.x in (header - DisclosureZone.px)..header -> Hit.Inline(layer.id)
            at.x < header -> Hit.Header(layer.id)
            deskLayout && at.x - left in -zone..zone -> Hit.Grip(layer.id, start = true)
            deskLayout && right - at.x in -zone..zone -> Hit.Grip(layer.id, start = false)
            !deskLayout && gripped && at.x in left - GripZone.px..left -> Hit.Grip(layer.id, start = true)
            !deskLayout && gripped && at.x in right..right + GripZone.px -> Hit.Grip(layer.id, start = false)
            !deskLayout && !gripped && at.x >= size.width - HandleZone.px -> Hit.Handle(layer.id)
            at.x in left..right -> Hit.Bar(layer.id)
            else -> Hit.Row(layer.id)
        }.let { if (layer.locked && (it is Hit.Header || it is Hit.Bar || it is Hit.Grip || it is Hit.Handle)) Hit.Locked(layer.id) else it }
    }

    fun openLockMenu(id: Long) {
        lockMenu = id to Offset(EyeZone.px, rowTop(rowOf(id) ?: return) + rowH)
    }

    private fun showLockHint() {
        lockHint?.let { lockHint = null; state.hint(it) }
    }

    private fun edgeZone(left: Float, right: Float) = min(6.dp.px, (right - left) / 3f)

    fun tap(hit: Hit) {
        if (state.lookOnly) return
        when (hit) {
            is Hit.Inline -> if (MultiSelect.active) select(hit.id) else toggleInlinePosition(hit.id)
            is Hit.Cube -> if (MultiSelect.active) select(hit.id) else state.toggleLayer3D(hit.id)
            is Hit.Eye -> if (MultiSelect.active) select(hit.id) else state.toggleHidden(hit.id)
            is Hit.Lock -> state.toggleLock(hit.id)
            is Hit.Locked -> state.hint(R.string.layer_locked_hint)
            is Hit.Header -> select(hit.id, toggle = true)
            is Hit.Bar -> select(hit.id, toggle = true)
            is Hit.Key -> tapKey(hit.key)
            Hit.Readout -> state.toggleBookmark()
            Hit.Empty, Hit.Ruler, is Hit.Row -> state.clearSelection()
            Hit.InlineBody -> inlineKeySelection.clear()
            Hit.Corner, is Hit.Grip, is Hit.Handle, is Hit.WorkAreaEdge -> Unit
        }
    }

    private fun select(id: Long, toggle: Boolean = false) {
        if (state.scene.layer(id)?.locked == true) return state.hint(R.string.layer_locked_hint)
        if (MultiSelect.active) MultiSelect.toggle(id) else if (toggle && state.selectedId == id && !state.inlinePanelHidden) state.clearSelection() else {
            state.selectedId = id
            state.panel = EditorPanel.Tools
        }
    }

    private fun snapNear(ms: Float): Int? = if (deskLayout && snapDisabled) null else TimelineMath.snapFrame(ms, snapTargets, fps, SnapZone.px / timeScale().pxPerMs)

    private fun showSnap(snap: Int?, landed: Int) {
        val shown = snap?.takeIf { it == landed }
        if (shown != null && shown != snapAt) onTick()
        snapAt = shown
    }

    private fun dropTrim(key: String) {
        val base = trimBase ?: return
        if ((listOf(base) + trimGroup).all { state.scene.layer(it.id)?.node == it.node }) state.cancelGesture(key)
    }

    fun longPress(hit: Hit, y: Float, slop: Float): Boolean {
        if (hit is Hit.Lock) openLockMenu(hit.id)
        val id = when (hit) { is Hit.Eye -> hit.id; is Hit.Header -> hit.id; else -> return false }
        if (state.scene.layer(id)?.locked == true) { state.hint(R.string.layer_locked_hint); return false }
        state.deselectLayer()
        if (MultiSelect.active) MultiSelect.toggle(id) else {
            MultiSelect.ids = setOf(id)
            MultiSelect.active = true
        }
        selectAnchor = id
        selectBase = MultiSelect.ids
        selectAdd = id in selectBase
        selectStart = y
        selectFinger = y
        selectSlop = slop
        selectPush = true
        selectArmed = false
        selectRows = 1
        liftJob?.cancel()
        liftJob = scope.launch { liftLoop() }
        return true
    }

    fun selectDrag(y: Float, pushing: Boolean) {
        selectFinger = y
        selectPush = pushing
        if (abs(y - selectStart) > selectSlop) selectArmed = true
        selectUpdate()
    }

    fun endSelect() {
        selectAnchor = null
        liftJob?.cancel()
    }

    private fun selectUpdate() {
        val anchor = rowOf(selectAnchor ?: return) ?: return
        val finger = when {
            !selectArmed -> anchor
            hasInlinePositions -> rowAt(selectFinger.coerceIn(rulerH, rowsBottom)).coerceIn(0, layers.lastIndex)
            else -> RowDrag.fingerRow(selectFinger, rowH, rulerH, rowsBottom, scroll, layers.size)
        }
        val rows = abs(finger - anchor) + 1
        if (rows > selectRows) onTick()
        selectRows = rows
        MultiSelect.ids = RowDrag.slideSelection(selectBase, layers.asReversed().map { it.id }, anchor, finger, selectAdd) - layers.filter { it.locked }.map { it.id }.toSet()
    }

    fun beginDrag(hit: Hit, moved: Offset): Drag {
        return when {
            hit is Hit.WorkAreaEdge && !state.lookOnly -> {
                state.playing = false
                if (hit.start) Drag.WorkAreaStart else Drag.WorkAreaEnd
            }
            hit is Hit.Grip && !state.lookOnly -> {
                state.playing = false
                val layer = layers.firstOrNull { it.id == hit.id }
                dragId = hit.id
                trimBase = layer
                trimScene = state.scene
                trimGroup = if (MultiSelect.active && hit.id in MultiSelect.ids) layers.filter { it.id in MultiSelect.ids && it.id != hit.id } else emptyList()
                if ((listOfNotNull(layer) + trimGroup).any { it.timingLocked }) {
                    lockHint = R.string.layer_timing_locked_hint
                    return Drag.Spent
                }
                edgeMs = (if (hit.start) layer?.startTime else layer?.endTime?.plus(1))?.toFloat() ?: 0f
                snapTargets = TimelineMath.snapTargets(state.scene, trimGroup.map { it.id }.toSet() + hit.id, state.playheadFrame)
                if (hit.start) Drag.TrimStart else Drag.TrimEnd
            }
            hit is Hit.Handle && !state.lookOnly -> {
                dragId = hit.id
                collapseForReorder(hit.id)
                Drag.Reorder
            }
            abs(moved.x) > abs(moved.y) -> {
                state.playing = false
                Drag.Scrub
            }
            else -> Drag.Scroll
        }
    }

    fun dragBy(drag: Drag, delta: Offset, at: Offset) {
        if (drag == Drag.Spent) showLockHint()
        when (drag) {
            Drag.Scrub -> { state.transitionButtonDismissed = false; scrubBy(delta.x) }
            Drag.Scroll -> scrollBy(delta.y)
            Drag.KeyBox -> updateKeyBox(at)
            Drag.Keys -> updateKeyMove(at)
            Drag.TrimStart, Drag.TrimEnd -> {
                trimBy(delta.x, start = drag == Drag.TrimStart)
                if (deskLayout) autoEdge(at.x, drag == Drag.TrimStart)
            }
            Drag.Reorder -> reorderTo(at.y)
            Drag.WorkAreaStart, Drag.WorkAreaEnd -> {
                val last = state.scene.lastFrame.coerceAtLeast(0)
                val frame = TimelineMath.nearestFrame(timeScale().time(at.x), fps).coerceIn(0, last + 1)
                if (drag == Drag.WorkAreaStart) {
                    state.edit("work-area-start") { it.withWorkArea(frame.coerceAtMost(it.workAreaEndFrame - 1), it.workAreaEndFrame) }
                } else {
                    state.edit("work-area-end") { it.withWorkArea(it.workAreaStartFrame, frame.coerceAtLeast(it.workAreaStartFrame + 1)) }
                }
            }
            Drag.Pinch, Drag.Spent, Drag.Lift, Drag.Select -> Unit
        }
    }

    fun endDrag(drag: Drag?, velocity: Velocity, lifted: Boolean = true) {
        lockHint = null
        autoStop()
        limits = emptyMap()
        snapAt = null
        if (drag == Drag.Lift) {
            drop()
            dropMove(lifted)
            if (lifted && liftArmed) liftId?.let(::revealRow)
        }
        if (drag == Drag.Reorder) {
            if (lifted) revealRow(dragId) else state.cancelGesture("reorder")
        }
        if (drag == Drag.Select) endSelect()
        if (drag == Drag.KeyBox) {
            keyBox = null
            keyBoxStart = Offset.Unspecified
        }
        if (drag == Drag.Keys) keyMove = null
        if (drag == Drag.TrimStart || drag == Drag.TrimEnd) {
            val key = if (drag == Drag.TrimStart) "trim-start" else "trim-end"
            if (deskLayout && !lifted) state.cancelGesture(key) else dropTrim(key)
        }
        if (drag?.edit == true) state.endGesture()
        motion = scope.launch {
            if (drag == Drag.Scroll) launch { fling(velocity.y, ::scrollBy) }
            if (drag == Drag.Scrub) fling(velocity.x) { state.frame == scrubFrame && scrubBy(it) }
            settle()
        }
    }

    fun stopMotion() {
        motion?.cancel()
    }

    fun followPlayback() {
        stopMotion()
        scrubMs = null
    }

    fun zoomBy(factor: Float, anchor: Float = timeScale().originX) {
        if (deskLayout) {
            val at = anchor.coerceIn(headerWidth, max(size.width.toFloat(), headerWidth))
            val before = timeScale().time(at)
            val far = if (dpPerSecond >= fitZoom) 1f else (ln(dpPerSecond / minZoom) / ln(fitZoom / minZoom)).coerceIn(0f, 1f)
            state.timelineZoom = (dpPerSecond * factor.pow(0.3f + 0.7f * far)).coerceIn(minZoom, maxZoom)
            followFit = abs(state.timelineZoom - fitZoom) <= fitZoom * 0.001f
            setViewStart(before - (at - headerWidth) / timeScale().pxPerMs)
        } else state.timelineZoom = (dpPerSecond * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    private fun scrubBy(dx: Float): Boolean {
        val time = timeScale()
        val wanted = time.nowMs - dx / time.pxPerMs
        val to = wanted.coerceIn(0f, TimelineMath.frameTimeMs(state.scene.lastFrame + 1, fps))
        scrubFrame = TimelineMath.nearestFrame(to, fps)
        scrubMs = to
        state.stepTo(scrubFrame)
        return to == wanted
    }

    private fun scrollBy(dy: Float): Boolean {
        val wanted = scroll - dy
        scrollPx = wanted
        return scroll == wanted
    }

    fun finishWheelScrub() { motion = scope.launch { kotlinx.coroutines.delay(120); settle() } }

    private suspend fun settle() {
        val from = scrubMs ?: return
        if (state.playheadFrame == scrubFrame) {
            animate(from, TimelineMath.frameTimeMs(scrubFrame, fps), animationSpec = tween(120)) { value, _ -> scrubMs = value }
        }
        scrubMs = null
    }

    private suspend fun fling(velocity: Float, move: (Float) -> Boolean) {
        var last = 0f
        AnimationState(0f, velocity).animateDecay(decay) {
            if (!move(value - last)) cancelAnimation()
            last = value
        }
    }

    private fun trimBy(dx: Float, start: Boolean) {
        edgeMs += dx / timeScale().pxPerMs
        val shown = state.scene
        if (shown.layer(dragId) == null) return
        val base = trimBase ?: return
        val from = trimScene ?: return
        val snap = snapNear(edgeMs)
        val bases = listOf(base) + trimGroup
        val trims = TimelineMath.trimmedEdges(from, base, trimGroup, start, snap?.let { TimelineMath.frameTimeMs(it, fps) } ?: edgeMs)
        val changed = bases.indices.filter { i -> shown.layer(bases[i].id)?.let { trims[i].time != if (start) it.startTime else it.endTime } == true }
        if (changed.isNotEmpty()) state.edit(if (start) "trim-start" else "trim-end") { s -> changed.fold(s) { acc, i -> acc.withClip(bases[i].id, trims[i].layer, from) } }
        val hits = bases.indices.filter { trims[it].limited }.associate { bases[it].id to start }
        if (hits.isNotEmpty() && limits.isEmpty()) onTick()
        limits = hits
        showSnap(snap.takeUnless { trims[0].limited }, trims[0].edge)
    }

    private var autoX: Float? = null
    private var autoTrim: Boolean? = null
    private var autoJob: Job? = null
    private var panBias = 0f

    private fun autoEdge(x: Float, trimStart: Boolean?) {
        autoX = x
        autoTrim = trimStart
        if (autoJob?.isActive != true) autoJob = scope.launch { autoLoop() }
    }

    private fun autoStop() {
        autoX = null
        panBias = 0f
    }

    private suspend fun autoLoop() {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val step = min((now - last) / 1e9f, 0.05f)
            last = now
            val x = autoX ?: break
            val zone = 48.dp.px
            val over = when {
                x > size.width - zone -> (x - (size.width - zone)) / zone
                x < headerWidth + zone -> -(headerWidth + zone - x) / zone
                else -> 0f
            }.coerceIn(-1.5f, 1.5f)
            if (over == 0f) continue
            val before = viewStartMs
            pan(over * 700.dp.px * step)
            val moved = viewStartMs - before
            if (moved == 0f) continue
            val trim = autoTrim
            if (trim != null) {
                edgeMs += moved
                trimBy(0f, trim)
            } else {
                panBias += moved
                moveApply()
            }
        }
    }

    private fun moveBegin(id: Long): Boolean {
        panBias = 0f
        val layer = state.scene.layer(id) ?: return false
        moveLayer = layer
        moveGroup = if (MultiSelect.active && id in MultiSelect.ids) state.scene.layers.filter { it.id in MultiSelect.ids && it.id != id } else emptyList()
        moveGroupIds = moveGroup.map { it.id }.toSet()
        moveBase = state.scene
        moveId = id
        moveDx = 0f
        moveSlide = 0f
        moveArmed = false
        snapTargets = TimelineMath.snapTargets(state.scene, moveGroupIds + id, state.playheadFrame)
        return true
    }

    private fun moveApply() {
        val original = moveLayer ?: return
        state.playing = false
        val scene = state.scene
        val pxPerMs = timeScale().pxPerMs
        val wanted = original.startTime + moveDx / pxPerMs + panBias
        val spanFrames = scene.lastShownFrame(original.endTime) + 1 - scene.firstShownFrame(original.startTime)
        val spanMs = (original.endTime + 1 - original.startTime).toFloat()
        val found = TimelineMath.moveTarget(wanted, spanMs, spanFrames, if (deskLayout && snapDisabled) emptySet() else snapTargets, fps, SnapZone.px / pxPerMs)
        val target = TimelineMath.groupTarget(scene, original, moveGroup, found)
        val start = TimelineMath.movedStart(scene, original, target.frame)
        showSnap(target.snap, target.snap ?: target.frame)
        moveSlide = if (target.snap != null || target != found || wanted <= 0f) 0f else (wanted - start) * pxPerMs
        val group = moveGroup
        if (scene.layer(original.id)?.startTime != start) state.edit(MOVE_EDIT) { TimelineMath.movedClips(it, original, group, target.frame) }
    }

    private fun dropMove(commit: Boolean) {
        if (!commit || state.scene == moveBase) state.cancelGesture(MOVE_EDIT)
        moveId = null
        moveLayer = null
        moveGroup = emptyList()
        moveGroupIds = emptySet()
        moveBase = null
        moveSlide = 0f
    }

    fun timeFor(id: Long, time: TimeScale) = if (id == moveId || id in moveGroupIds) time.slid(moveSlide) else time

    private fun collapseForReorder(id: Long) {
        if (inlinePositions.isEmpty() && inlineEffects.isEmpty()) return
        val row = rowOf(id) ?: return
        val anchoredScroll = rulerH + row * rowH - rowTop(row)
        inlinePositions = emptySet()
        inlineEffects = emptySet()
        inlineKeySelection.clear()
        state.timelineDetailsOpen = false
        scrollPx = anchoredScroll.coerceIn(0f, maxScroll)
    }

    private fun reorderTo(y: Float) {
        val layers = layers
        if (layers.isEmpty()) return
        val index = layers.lastIndex - rowAt(y).coerceIn(0, layers.lastIndex)
        val id = dragId
        if (layers[index].id != id) state.edit("reorder") { it.reorder(id, index) }
    }

    fun rowOf(id: Long): Int? = layers.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { layers.lastIndex - it }

    private fun progress(ms: Long, total: Int) = Motion.EaseOut.transform((ms / total.toFloat()).coerceIn(0f, 1f))

    val liftAmount: Float
        get() {
            val now = frameMs
            return if (dropping) dropFrom * (1 - progress(now - dropAt, DROP_MS)) else progress(now - liftAt, LIFT_MS)
        }

    fun shownTop(row: Int, id: Long): Float {
        val now = frameMs
        val top = rowTop(row)
        if (id == liftId) return if (dropping) liftTop + (top - liftTop) * progress(now - dropAt, DROP_MS) else liftTop
        val slide = slides[id] ?: return top
        return top + slide.from * (1 - progress(now - slide.at, SLIDE_MS)) * rowH
    }

    fun lift(id: Long, at: Offset, slop: Float, moves: Boolean): Boolean {
        val row = rowOf(id) ?: return false
        if (state.scene.layer(id)?.locked == true) { state.hint(R.string.layer_locked_hint); return false }
        if (!moveBegin(id)) return false
        liftMoves = moves && (listOfNotNull(moveLayer) + moveGroup).none { it.timingLocked }
        if (moves && !liftMoves) lockHint = R.string.layer_timing_locked_hint
        liftStartX = at.x
        liftJob?.cancel()
        liftAt = SystemClock.uptimeMillis()
        liftGrab = at.y - rowTop(row)
        liftFinger = at.y
        liftStart = at.y
        liftSlop = slop
        liftArmed = false
        liftPush = true
        dropping = false
        liftTop = rowTop(row)
        liftId = id
        liftJob = scope.launch { liftLoop() }
        liftUpdate()
        return true
    }

    fun liftDrag(at: Offset, pushing: Boolean) {
        liftFinger = at.y
        liftPush = pushing
        if (!liftArmed && abs(at.y - liftStart) > liftSlop) {
            liftArmed = true
            liftId?.let(::collapseForReorder)
        }
        if (deskLayout && !liftArmed) liftFinger = liftStart
        if (!liftMoves && abs(at.x - liftStartX) > liftSlop) showLockHint()
        if (liftMoves) {
            if (abs(at.x - liftStartX) > liftSlop) moveArmed = true
            if (moveArmed) {
                moveDx = at.x - liftStartX
                moveApply()
                if (deskLayout) autoEdge(at.x, null)
            }
        }
        liftUpdate()
    }

    fun grabScroll(dy: Float) {
        scrollBy(dy)
    }

    fun liftScroll(dy: Float) {
        scrollBy(dy)
        liftUpdate()
        selectUpdate()
    }

    private fun drop() {
        if (liftId == null || dropping) return
        dropFrom = liftAmount
        dropAt = SystemClock.uptimeMillis()
        dropping = true
    }

    private fun liftUpdate() {
        val id = liftId ?: return
        if (dropping) return
        val layers = layers
        val index = layers.indexOfFirst { it.id == id }
        if (index < 0) return
        liftTop = if (hasInlinePositions) (liftFinger - liftGrab).coerceIn(rowTop(0), rowTop(layers.lastIndex))
            else RowDrag.clampTop(liftFinger - liftGrab, rowH, rulerH, rowsBottom, scroll, layers.size)
        val from = layers.lastIndex - index
        val to = if (hasInlinePositions) rowAt(liftTop + rowH / 2).coerceIn(0, layers.lastIndex)
            else RowDrag.slot(liftTop, rowH, rulerH, scroll, layers.size)
        if (to == from) return
        val now = SystemClock.uptimeMillis()
        for ((i, layer) in layers.withIndex()) {
            val shift = RowDrag.shift(layers.lastIndex - i, from, to)
            if (shift == 0) continue
            val left = slides[layer.id]?.let { it.from * (1 - progress(now - it.at, SLIDE_MS)) } ?: 0f
            slides[layer.id] = Slide(left - shift, now)
        }
        state.edit(MOVE_EDIT) { it.reorder(id, layers.lastIndex - to) }
        onTick()
    }

    private fun edgeSpeed(y: Float) = RowDrag.edgeSpeed(y, rulerH, rowsBottom, PushZone.px, PUSH_SLOWEST_ROWS * rowH, PUSH_FASTEST_ROWS * rowH)

    private suspend fun liftLoop() {
        val me = coroutineContext.job
        var last = SystemClock.uptimeMillis()
        try {
            while (true) {
                val now = withFrameNanos { SystemClock.uptimeMillis() }
                val step = min((now - last) / 1000f, PUSH_MAX_STEP_S)
                last = now
                frameMs = now
                slides.values.removeAll { now - it.at >= SLIDE_MS }
                if (dropping) {
                    if (now - dropAt >= DROP_MS && slides.isEmpty()) break
                } else if (selectAnchor != null) {
                    val speed = RowDrag.toward(edgeSpeed(selectFinger), selectFinger, selectStart, selectSlop)
                    if (selectPush && speed != 0f) {
                        scrollBy(-speed * step)
                        selectUpdate()
                    }
                } else if (liftArmed && liftPush) {
                    val speed = edgeSpeed(liftFinger)
                    if (speed != 0f) {
                        scrollBy(-speed * step)
                        liftUpdate()
                    }
                }
            }
        } finally {
            if (liftJob === me) {
                liftId = null
                dropping = false
                slides.clear()
            }
        }
    }
}

private val KeyFaint = DpSize(6.4.dp, 9.6.dp)
private val GripStub = 2.5.dp

private val MacOs = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

private suspend fun PointerInputScope.desktopTimelineGestures(view: TimelineView, focus: androidx.compose.ui.focus.FocusManager) {
    awaitPointerEventScope {
        var down: Offset? = null
        var hit: Hit? = null
        var drag: Drag? = null
        var resizing = false
        var rubbering = false
        var moved = false
        var previous = Offset.Zero
        var pressTime = 0L
        var boxHold = false
        var lastClickId: Long? = null
        var wasSelected = false
        var lastHeaderId: Long? = null
        var lastClickTime = 0L
        var grab: Offset? = null
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            val at = change.position
            val modifiers = event.keyboardModifiers
            val command = if (MacOs) modifiers.isMetaPressed else modifiers.isCtrlPressed
            val plain = !command && !modifiers.isShiftPressed
            when (event.type) {
                PointerEventType.Scroll -> {
                    if (drag == null && !change.isConsumed) view.wheel(change.scrollDelta, at, modifiers.isCtrlPressed || modifiers.isMetaPressed, modifiers.isShiftPressed)
                    if (rubbering) view.rubberRefresh()
                    if (down == null) view.hover(at)
                    change.consume()
                    continue
                }
                PointerEventType.Exit -> if (down == null) view.hover(null)
                PointerEventType.Enter, PointerEventType.Move -> if (down == null) view.hover(at)
                else -> Unit
            }
            if (grab != null) {
                if (event.buttons.isTertiaryPressed) {
                    val last = grab
                    view.pan(last.x - at.x)
                    view.grabScroll(at.y - last.y)
                    grab = at
                } else grab = null
                change.consume()
                continue
            }
            if (event.type == PointerEventType.Press && event.buttons.isTertiaryPressed && down == null) {
                view.stopMotion()
                grab = at
                change.consume()
                continue
            }
            if (event.type == PointerEventType.Press && down == null && app.absentmotion.platform.textFieldFocused()) focus.clearFocus()
            if (event.type == PointerEventType.Press && (event.buttons.isSecondaryPressed || MacOs && modifiers.isCtrlPressed) && down == null && !view.state.lookOnly) {
                val pressed = view.hitAt(at)
                if (pressed is Hit.Lock) view.openLockMenu(pressed.id)
                else if (pressed is Hit.Locked) view.openLockMenu(pressed.id)
                else {
                    val id = view.clipId(pressed)
                    if (id != null) view.selectDesktop(id, false, false)
                    view.menuId = id
                    view.menuAt = at
                }
                change.consume()
                continue
            }
            val primary = event.buttons.isPrimaryPressed || change.type == PointerType.Touch || change.type == PointerType.Stylus
            val lock = if (event.type == PointerEventType.Press && primary && down == null && !view.state.lookOnly && !view.isCovered(at)) view.hitAt(at) as? Hit.Lock else null
            if (lock != null) {
                change.consume()
                val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { while (awaitPointerEvent().changes.onEach { it.consume() }.any { it.pressed }) {} } == null
                if (held) view.openLockMenu(lock.id) else view.state.toggleLock(lock.id)
                continue
            }
            if (event.type == PointerEventType.Press && primary && down == null && !change.isConsumed && !view.isCovered(at)) {
                view.stopMotion()
                down = at
                previous = at
                hit = view.hitAt(at).takeIf { !view.state.lookOnly || it == Hit.Ruler }
                moved = false
                resizing = hit !is Hit.WorkAreaEdge && view.nearHeaderEdge(at.x)
                view.resizingHeader = resizing
                rubbering = false
                view.desktopCancel = false
                view.desktopDragging = true
                pressTime = change.uptimeMillis
                boxHold = true
                val pressedId = (hit as? Hit.Bar)?.id ?: (hit as? Hit.Header)?.id
                wasSelected = plain && !MultiSelect.active && !view.state.inlinePanelHidden && pressedId != null && pressedId == view.state.selectedId
                if (!resizing) when (val h = hit) {
                    is Hit.Eye -> view.state.toggleHidden(h.id)
                    is Hit.Locked -> view.state.hint(R.string.layer_locked_hint)
                    is Hit.Grip -> { view.selectDesktop(h.id, false, false); drag = view.beginDrag(h, Offset.Zero) }
                    is Hit.WorkAreaEdge -> drag = view.beginDrag(h, Offset.Zero)
                    is Hit.Bar -> {
                        if (plain) {
                            val chooseKeys = view.canBoxKeys(h)
                            if (!chooseKeys || view.state.selectedId != h.id) view.selectDesktop(h.id, false, false)
                            if (!chooseKeys && view.lift(h.id, at, 4.dp.toPx(), true)) drag = Drag.Lift
                        }
                        else view.selectDesktop(h.id, command, modifiers.isShiftPressed)
                    }
                    is Hit.Header -> {
                        view.selectDesktop(h.id, command, modifiers.isShiftPressed)
                        if (plain && view.lift(h.id, at, 4.dp.toPx(), false)) drag = Drag.Lift
                    }
                    Hit.Ruler -> { view.state.transitionButtonDismissed = false; view.seek(at.x) }
                    Hit.Empty, is Hit.Row -> view.rubberStart(at)
                    else -> Unit
                }
                view.cancelAction = { view.endDrag(drag, Velocity.Zero, false); view.rubberEnd(); drag = null }
                if (hit != Hit.InlineBody) change.consume()
                continue
            }
            val start = down ?: continue
            if (view.desktopCancel) {
                if (!change.pressed) { down = null; hit = null; view.desktopCancel = false; view.resizingHeader = false }
                change.consume()
                continue
            }
            if (change.pressed) {
                if (!moved && (at - start).getDistance() > 2f) {
                    moved = true
                    boxHold = change.uptimeMillis - pressTime >= minOf(KEYFRAME_HOLD_MS, viewConfiguration.longPressTimeoutMillis)
                }
                view.snapDisabled = modifiers.isAltPressed
                when {
                    resizing -> view.resizeHeader(at.x)
                    drag == null && moved && boxHold && hit?.let(view::canBoxKeys) == true &&
                        ((hit as? Hit.Key)?.key?.let { it in view.inlineKeySelection.keys } != true) -> {
                        view.startKeyBox(start)
                        drag = Drag.KeyBox
                        view.dragBy(Drag.KeyBox, Offset.Zero, at)
                    }
                    drag == null && moved && hit is Hit.Key -> {
                        view.startKeyMove(hit, start)
                        drag = Drag.Keys
                        view.dragBy(Drag.Keys, at - start, at)
                    }
                    drag == Drag.Lift -> view.liftDrag(at, true)
                    drag == null && moved && !boxHold && plain && hit is Hit.Bar &&
                        abs(at.y - start.y) > maxOf(4.dp.toPx(), abs(at.x - start.x)) &&
                        view.lift(hit.id, start, 4.dp.toPx(), false) -> {
                        drag = Drag.Lift
                        view.liftDrag(at, true)
                    }
                    drag != null -> drag?.let { view.dragBy(it, at - previous, at) }
                    moved && hit == Hit.InlineBody -> {
                        val delta = at - previous
                        if (abs(at.x - start.x) > abs(at.y - start.y)) view.pan(-delta.x) else view.grabScroll(delta.y)
                    }
                    hit == Hit.Ruler || hit == Hit.Readout && moved -> view.seek(at.x)
                    moved && (hit == Hit.Empty || hit is Hit.Row) -> {
                        rubbering = true
                        view.rubberTo(at)
                    }
                }
                previous = at
                if (hit != Hit.InlineBody || moved) change.consume()
            } else {
                if (!moved && !resizing && !rubbering) when (val h = hit) {
                    is Hit.Bar -> {
                        if (plain && MultiSelect.active) { MultiSelect.clear(); view.state.selectedId = h.id; view.state.panel = EditorPanel.Tools }
                        if (plain && lastClickId == h.id && change.uptimeMillis - lastClickTime <= viewConfiguration.doubleTapTimeoutMillis) {
                            val layer = view.state.scene.layer(h.id)
                            if (layer?.isPlainGroup == true) view.state.enterGroup(h.id)
                            else {
                                view.state.selectedId = h.id
                                MultiSelect.clear()
                                view.state.panel = if (layer?.kind == "text") EditorPanel.EditText else EditorPanel.Tools
                            }
                            lastClickId = null
                        } else {
                            if (wasSelected) view.state.clearSelection()
                            lastClickId = h.id; lastHeaderId = null; lastClickTime = change.uptimeMillis
                        }
                    }
                    is Hit.Header -> {
                        if (plain && MultiSelect.active) { MultiSelect.clear(); view.state.selectedId = h.id; view.state.panel = EditorPanel.Tools }
                        if (lastHeaderId == h.id && change.uptimeMillis - lastClickTime <= viewConfiguration.doubleTapTimeoutMillis) {
                            if (plain && view.state.scene.layer(h.id)?.isPlainGroup == true) view.state.enterGroup(h.id)
                            lastHeaderId = null
                        } else {
                            if (wasSelected) view.state.clearSelection()
                            lastHeaderId = h.id; lastClickId = null; lastClickTime = change.uptimeMillis
                        }
                    }
                    is Hit.Inline, is Hit.Cube, is Hit.Key, Hit.InlineBody, Hit.Readout -> view.tap(h)
                    Hit.Empty, is Hit.Row -> view.state.clearSelection()
                    else -> Unit
                }
                view.endDrag(drag, Velocity.Zero, true)
                view.rubberEnd()
                rubbering = false
                view.desktopDragging = false
                view.resizingHeader = false
                view.cancelAction = null
                view.snapDisabled = false
                down = null
                hit = null
                drag = null
                change.consume()
            }
        }
    }
}

private suspend fun PointerInputScope.timelineGestures(view: TimelineView, onLongPress: () -> Unit) = awaitEachGesture {
    val down = awaitFirstDown()
    view.stopMotion()
    if (view.isCovered(down.position)) return@awaitEachGesture
    val hit = view.hitAt(down.position)
    val tracker = VelocityTracker().apply { addPointerInputChange(down) }
    var drag: Drag? = null
    var lifted = false
    var held = false
    var last: PointerInputChange? = null
    var finger = down.position
    var second: PointerId? = null
    val dismissing = view.dismissZone(down.position)
    val keyHold = !view.state.lookOnly && !dismissing && view.canBoxKeys(hit)
    val holdable = !view.state.lookOnly && !dismissing &&
        (hit is Hit.Eye || hit is Hit.Lock || hit is Hit.Locked || hit is Hit.Header || hit is Hit.Row || hit is Hit.Bar || keyHold)
    val longPress = viewConfiguration.longPressTimeoutMillis
    val deadline = down.uptimeMillis + if (keyHold) minOf(KEYFRAME_HOLD_MS, longPress) else longPress
    try {
        while (true) {
            val event = if (holdable && drag == null && !held) {
                withTimeoutOrNull((deadline - SystemClock.uptimeMillis()).coerceAtLeast(1)) { awaitPointerEvent() }
            } else awaitPointerEvent()
            if (event == null) {
                when {
                    hit is Hit.Key && hit.key in view.inlineKeySelection.keys -> {
                        view.startKeyMove(hit, down.position)
                        drag = Drag.Keys
                    }
                    keyHold -> {
                        view.startKeyBox(finger, (hit as? Hit.Key)?.key)
                        drag = Drag.KeyBox
                    }
                    hit is Hit.Locked -> {
                        view.openLockMenu(hit.id)
                        held = true
                    }
                    hit is Hit.Row && view.lift(hit.id, finger, viewConfiguration.touchSlop, moves = false) -> drag = Drag.Lift
                    hit is Hit.Bar && view.lift(hit.id, finger, viewConfiguration.touchSlop, moves = true) -> drag = Drag.Lift
                    view.longPress(hit, finger.y, viewConfiguration.touchSlop) -> drag = Drag.Select
                    else -> held = true
                }
                onLongPress()
                continue
            }
            val fingers = event.changes.count { it.pressed }
            val first = event.changes.firstOrNull { it.id == down.id }
            last = first
            if (first != null) {
                tracker.addPointerInputChange(first)
                finger = first.position
            }
            when {
                fingers == 0 -> break
                drag == Drag.Lift || drag == Drag.Select -> {
                    if (first == null || !first.pressed) break
                    for (change in event.changes) {
                        if (change.id == down.id) continue
                        if (second == null && change.changedToDown() && change.position.y >= view.rulerH) second = change.id
                        if (change.id != second) continue
                        if (change.pressed) view.liftScroll(change.position.y - change.previousPosition.y) else second = null
                    }
                    if (drag == Drag.Lift) view.liftDrag(first.position, pushing = second == null)
                    else view.selectDrag(first.position.y, pushing = second == null)
                    event.changes.forEach { it.consume() }
                }
                fingers > 1 && drag?.edit != true -> {
                    drag = Drag.Pinch
                    view.zoomBy(event.calculateZoom())
                    event.changes.forEach { it.consume() }
                }
                drag == Drag.Pinch || drag == Drag.Spent -> drag = Drag.Spent
                first == null || !first.pressed -> break
                drag == null -> {
                    val moved = first.position - down.position
                    if (moved.getDistance() > viewConfiguration.touchSlop) {
                        val started = if (hit is Hit.Key) { view.startKeyMove(hit, down.position); Drag.Keys } else view.beginDrag(hit, moved)
                        drag = started
                        if (started == Drag.WorkAreaStart || started == Drag.WorkAreaEnd || started == Drag.Keys || started == Drag.Reorder) {
                            view.dragBy(started, moved, first.position)
                        }
                        first.consume()
                    }
                }
                else -> {
                    view.dragBy(drag, first.position - first.previousPosition, first.position)
                    first.consume()
                }
            }
        }
        lifted = TimelineMath.released(last)
        if (lifted && drag == Drag.Reorder) view.dragBy(Drag.Reorder, Offset.Zero, finger)
    } finally {
        if (lifted && (drag == null && !held || drag == Drag.Lift && hit is Hit.Bar && !view.liftMoved)) {
            if (dismissing) view.dismissAddMenu() else view.tap(hit)
        }
        view.endDrag(drag, if (lifted) tracker.calculateVelocity() else Velocity.Zero, lifted)
    }
}

private fun DrawScope.drawTimeline(view: TimelineView, measurer: TextMeasurer, styles: TimelineStyles, eye: Painter, eyeOff: Painter, locks: List<Painter>, outline: Path, pictures: Map<Long, StripPicture>, proxies: Map<String, Float>, appear: Float = 1f) {
    val state = view.state
    val fps = view.fps
    val time = view.timeScale()
    val step = TimelineMath.tickStep(fps, view.dpPerSecond)
    val desktop = deskLayout
    if (desktop) drawDeskRuler(view, time, measurer, styles)
    else {
        if (view.layers.isNotEmpty()) drawRuler(time, fps, state.scene.lastFrame, step)
        if (view.layers.isNotEmpty() && (state.scene.isElement || state.inGroup)) drawRetimeMarks(state.scene, time)
    }
    if (view.layers.isNotEmpty()) drawWorkArea(view, time, fps, if (desktop) view.headerWidth else 0f, view.rulerH)
    if (view.layers.isNotEmpty() && !state.inGroup) drawCacheBar(state, time, fps, if (desktop) view.headerWidth else 0f, view.rulerH)
    val stripe = TimelineMath.frameTimeMs(2 * step, fps) * time.pxPerMs
    fun paint(row: Int, layer: Layer) {
        val rowTime = view.timeFor(layer.id, time)
        drawRow(layer, layer.id == state.selectedId, view.shownTop(row, layer.id), rowTime, stripe, measurer, styles, if (layer.hidden) eyeOff else eye, locks.of(layer), outline, state.scene, pictures[layer.id], view.keyFrames(layer), appear, view.limits[layer.id], view.rowHeaderWidth(layer), layer.id == view.hoveredId, view.showsLayerControls(layer), view.scopeOf(layer)?.ref != null, view.inlineKeySelection.keys.filter { it.layerId == layer.id && it.ref == view.scopeOf(layer)?.ref }.map { it.frame }.toSet(), playhead = state.playheadFrame)
        layer.mediaFill?.takeIf { it.video }?.let { proxies[it.uri] }?.let { done ->
            drawProxyProgress(rowTime.x(layer.startTime.toFloat()), rowTime.x(layer.endTime + 1f), view.shownTop(row, layer.id), done)
        }
    }
    clipRect(top = view.rulerH) {
        for (row in view.visibleRows()) {
            val layer = view.layerAt(row) ?: continue
            if (layer.id == view.liftId) continue
            paint(row, layer)
        }
        if (desktop) {
            view.liftId?.let { id -> view.rowOf(id)?.let { row -> view.layerAt(row)?.let { layer ->
                drawLiftShadow(view.shownTop(row, id), view.rowH)
                paint(row, layer)
            } } }
        }
    }
    if (desktop) drawDeskFrame(view, time)
    view.snapAt?.let { frame ->
        val half = SnapLine.toPx() / 2
        drawRect(SnapColor, Offset(time.x(state.scene.frameStart(frame).toFloat()) - half, view.rulerH), Size(2 * half, size.height - view.rulerH))
    }
    if (desktop) {
        view.rubber?.let { rect ->
            drawRect(Am.Accent.copy(alpha = 0.14f), rect.topLeft, rect.size)
            drawRect(Am.Accent, rect.topLeft, rect.size, style = Stroke(1.dp.toPx()))
        }
        if (DeskDrop.item != null && DeskDrop.at.isSpecified) view.frameAtWindow(DeskDrop.at)?.let { frame ->
            val x = time.x(state.scene.frameStart(frame).toFloat())
            drawLine(Am.Accent, Offset(x, view.rulerH), Offset(x, size.height), 2.dp.toPx())
        }
    }
    val marks = state.scene.bookmarks
    if (desktop) clipRect(left = view.headerWidth) { for (ms in marks) drawBookmark(time.x(ms.toFloat()), outline) }
    else for (ms in marks) drawBookmark(time.x(ms.toFloat()), outline)
}

private fun DrawScope.drawLiftShadow(top: Float, rowH: Float) {
    val step = LiftShadowStep.toPx()
    for (i in 1..4) {
        val alpha = LIFT_SHADOW_ALPHA * (5 - i)
        drawRect(Color.Black, Offset(0f, top - i * step), Size(size.width, step), alpha = alpha)
        drawRect(Color.Black, Offset(0f, top + rowH + (i - 1) * step), Size(size.width, step), alpha = alpha)
    }
}

private fun DrawScope.drawDeskFrame(view: TimelineView, time: TimeScale) {
    val headerWidth = view.headerWidth
    val end = max(time.x(view.state.scene.totalTime.toFloat()), headerWidth)
    if (end < size.width) drawRect(Am.PreviewBg.copy(alpha = 0.5f), Offset(end, view.rulerH), Size(size.width - end, size.height - view.rulerH))
    val line = 1.dp.toPx()
    drawRect(if (view.resizeHover || view.resizingHeader) Am.Accent else Am.Button, Offset(headerWidth - line, 0f), Size(line, size.height))
    val thick = 4.dp.toPx()
    val gap = 2.dp.toPx()
    val minThumb = 24.dp.toPx()
    val colour = Am.Inactive.copy(alpha = 0.7f)
    val viewH = view.rowsEnd - view.rulerH
    if (view.maxScroll > 0f) {
        val track = viewH - 2 * gap - thick
        val thumb = max(minThumb, track * viewH / (viewH + view.maxScroll))
        drawRoundRect(colour, Offset(size.width - thick - gap, view.rulerH + gap + (track - thumb) * view.scroll / view.maxScroll), Size(thick, thumb), CornerRadius(thick / 2))
    }
    if (view.panSpan < 0.995f) {
        val track = size.width - headerWidth - 2 * gap - thick
        val thumb = max(minThumb, track * view.panSpan)
        drawRoundRect(colour, Offset(headerWidth + gap + (track - thumb) * view.panFraction, size.height - thick - gap), Size(thumb, thick), CornerRadius(thick / 2))
    }
}

private fun DrawScope.drawDeskRuler(view: TimelineView, time: TimeScale, measurer: TextMeasurer, styles: TimelineStyles) {
    val headerWidth = view.headerWidth
    val rulerH = view.rulerH
    val fps = view.fps
    drawRect(Am.Panel, Offset.Zero, Size(headerWidth, size.height))
    drawRect(Am.Line, Offset(headerWidth, rulerH - 1.dp.toPx()), Size(size.width - headerWidth, 1.dp.toPx()))
    if (view.layers.isEmpty()) return
    val steps = view.rulerSteps()
    val first = floor(time.time(headerWidth) * fps / 1000f).toInt().coerceAtLeast(0) / steps.minor * steps.minor
    val last = ceil(time.time(size.width) * fps / 1000f).toInt()
    val width = TickWidth.toPx()
    clipRect(left = headerWidth) {
        var frame = first
        while (frame <= last) {
            val x = time.x(TimelineMath.frameTimeMs(frame, fps))
            if (frame % steps.major == 0) {
                drawRect(RulerMajor, Offset(x, 3.dp.toPx()), Size(width, rulerH - 4.dp.toPx()))
                val text = measurer.measure(view.rulerLabel(frame, steps.major), styles.ruler)
                drawText(text, topLeft = Offset(x + 4.dp.toPx(), 2.dp.toPx()))
            } else {
                val height = if (steps.major / steps.minor % 2 == 0 && frame % (steps.major / 2) == 0) 9.dp else 5.dp
                drawRect(RulerMinor, Offset(x, rulerH - 1.dp.toPx() - height.toPx()), Size(width, height.toPx()))
            }
            frame += steps.minor
        }
        if (view.state.scene.isElement || view.state.inGroup) drawRetimeMarks(view.state.scene, time)
    }
}

private fun DrawScope.drawLifted(view: TimelineView, measurer: TextMeasurer, styles: TimelineStyles, eye: Painter, eyeOff: Painter, locks: List<Painter>, outline: Path, pictures: Map<Long, StripPicture>, appear: Float) {
    val id = view.liftId ?: return
    val row = view.rowOf(id) ?: return
    val layer = view.layerAt(row) ?: return
    val state = view.state
    val time = view.timeScale()
    val stripe = TimelineMath.frameTimeMs(2 * TimelineMath.tickStep(view.fps, view.dpPerSecond), view.fps) * time.pxPerMs
    val amount = view.liftAmount
    val rowH = view.rowH
    drawRect(Am.TimelineBg, alpha = LIFT_DIM * amount)
    translate(top = -view.rulerH) {
        val top = view.shownTop(row, id)
        val grow = 1 + (LIFT_SCALE - 1) * amount
        scale(grow, grow, Offset(size.width / 2, top + rowH / 2)) {
            val step = LiftShadowStep.toPx()
            for (i in 1..4) drawRect(Color.Black, Offset(-i * step, top - i * step + step), Size(size.width + 2 * i * step, rowH + 2 * i * step), alpha = LIFT_SHADOW_ALPHA * amount)
            drawRect(Am.TimelineBg, Offset(0f, top), Size(size.width, rowH), alpha = amount)
            drawRow(layer, id == state.selectedId, top, view.timeFor(id, time), stripe, measurer, styles, if (layer.hidden) eyeOff else eye, locks.of(layer), outline, state.scene, pictures[id], view.keyFrames(layer), appear, headerWidth = view.rowHeaderWidth(layer), hovered = id == view.hoveredId, controls = view.showsLayerControls(layer), activeKeys = view.scopeOf(layer)?.ref != null, playhead = state.playheadFrame)
        }
    }
}

private fun List<Painter>.of(layer: Layer): Painter = this[if (layer.locked) 1 else if (layer.timingLocked) 2 else 0]

private fun DrawScope.drawProxyProgress(left: Float, right: Float, top: Float, done: Float) {
    val from = max(left, 0f)
    val to = min(right, size.width)
    if (from >= to) return
    val height = ProxyLineHeight.toPx()
    val y = top + (RowHeight - BarInset).toPx() - height - ProxyLineInset.toPx()
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(from, y), Size(to - from, height))
    drawRect(Am.Accent, Offset(from, y), Size((to - from) * done.coerceIn(0f, 1f), height))
}

private fun DrawScope.drawBookmark(x: Float, outline: Path) {
    if (x < -10.dp.toPx() || x > size.width + 10.dp.toPx()) return
    val colour = BookmarkColor.copy(alpha = BOOKMARK_ALPHA)
    val half = BookmarkLineWidth.toPx() / 2
    drawRect(colour, Offset(x - half, 0f), Size(2 * half, RulerHeight.toPx()))
    outline.rewind()
    if (deskLayout) {
        val side = 7.dp.toPx()
        val bottom = 11.dp.toPx()
        outline.moveTo(x + half, 1.dp.toPx())
        outline.lineTo(x + half, bottom)
        outline.quadraticTo(x + half - side * 0.85f, bottom, x + half - side, 1.dp.toPx())
    } else {
        outline.moveTo(x + half, BookmarkFlagTop.toPx())
        outline.lineTo(x + half, BookmarkFlagBottom.toPx())
        outline.lineTo(x + half - BookmarkFlagSide.toPx(), BookmarkFlagBottom.toPx())
    }
    outline.close()
    drawPath(outline, colour)
}

private fun DrawScope.drawWorkArea(view: TimelineView, time: TimeScale, fps: Int, left: Float, rulerH: Float) {
    val scene = view.state.scene
    val color = Am.Accent
    val startX = time.x(TimelineMath.frameTimeMs(scene.workAreaStartFrame, fps))
    val endX = time.x(TimelineMath.frameTimeMs(scene.workAreaEndFrame, fps))
    val clipLeft = left.coerceIn(0f, size.width)
    clipRect(left = clipLeft, right = size.width, bottom = size.height) {
        val top = 2.dp.toPx()
        val height = (rulerH - CacheBarHeight.toPx() - 5.dp.toPx() - top).coerceAtLeast(1.dp.toPx())
        val from = max(startX, clipLeft)
        val to = min(endX, size.width)
        if (to > from) drawRect(Am.Field.copy(alpha = 0.48f), Offset(from, top), Size(to - from, height))
        val handle = minOf(8.dp.toPx(), ((endX - startX) / 2).coerceAtLeast(2.dp.toPx()))
        val shape = CornerRadius(2.dp.toPx())
        drawRoundRect(color, Offset(startX, top), Size(handle, height), shape)
        drawRoundRect(color, Offset(endX - handle, top), Size(handle, height), shape)
    }
}

private fun DrawScope.drawCacheBar(state: EditorState, time: TimeScale, fps: Int, left: Float, rulerH: Float) {
    PreviewCache.revision
    val memory = PreviewCache.runsFor(state, onDisk = false)
    val storage = PreviewCache.runsFor(state, onDisk = true)
    if (memory.isEmpty() && storage.isEmpty()) return
    val height = CacheBarHeight.toPx()
    val top = rulerH - height - 1.dp.toPx()
    fun stripe(runs: List<IntRange>, color: Color) {
        clipRect(left = left) {
            for (run in runs) {
                val start = time.x(TimelineMath.frameTimeMs(run.first, fps))
                val end = time.x(TimelineMath.frameTimeMs(run.last + 1, fps))
                if (end < left || start > size.width) continue
                drawRect(color, Offset(start, top), Size(end - start, height))
            }
        }
    }
    stripe(memory, CacheBarColor)
    stripe(storage, Color(0xFF48A4F2))
}

private fun DrawScope.drawRuler(time: TimeScale, fps: Int, lastFrame: Int, tickFrames: Int) {
    val width = TickWidth.toPx()
    val firstSecond = floor(time.time(0f) / 1000f).toInt().coerceAtLeast(0)
    val lastSecond = min(floor(time.time(size.width) / 1000f).toInt(), lastFrame / fps)
    for (second in firstSecond..lastSecond) {
        for (i in 0 until fps step tickFrames) {
            val frame = second * fps + i
            if (frame > lastFrame) return
            val height = when {
                i == 0 -> TickSecond
                i * 2 == fps -> TickHalfSecond
                else -> TickShort
            }
            drawRect(TickColor, Offset(time.x(TimelineMath.frameTimeMs(frame, fps)), 0f), Size(width, height.toPx()))
        }
    }
}

private fun DrawScope.drawRetimeMarks(scene: Scene, time: TimeScale) {
    val width = TickWidth.toPx()
    val flag = TickShort.toPx()
    fun mark(ms: Int, direction: Float) {
        val x = time.x(scene.frameTime(scene.frameAt(ms)).toFloat())
        drawLine(RetimeMarkColor, Offset(x, 0f), Offset(x, flag * 2), width)
        drawLine(RetimeMarkColor, Offset(x, 0f), Offset(x + direction * flag / 2, 0f), width)
    }
    if (scene.retimeIn > 0) mark(scene.retimeIn, 1f)
    if (scene.retimeOut > 0) mark(scene.retimeOut, -1f)
}

private fun DrawScope.drawRow(
    layer: Layer, selected: Boolean, top: Float, time: TimeScale, stripe: Float,
    measurer: TextMeasurer, styles: TimelineStyles, eye: Painter, lock: Painter, outline: Path, scene: Scene, picture: StripPicture?, keyFrames: List<Int>, appear: Float = 1f, limit: Boolean? = null, headerWidth: Float = 0f, hovered: Boolean = false, controls: Boolean = false, activeKeys: Boolean = false, selectedKeys: Set<Int> = emptySet(), playhead: Int,
) {
    val barTop = top + BarInset.toPx()
    val barBottom = top + (RowHeight - BarInset).toPx()
    val centre = top + RowHeight.toPx() / 2
    val left = time.x(layer.startTime.toFloat())
    val right = time.x(layer.endTime + 1f)

    if (layer.kind == "camera" && layer.id == scene.defaultCameraId) drawRect(CameraBar.copy(alpha = 0.1f), Offset(0f, top), Size(size.width, RowHeight.toPx()))
    if (deskLayout && (selected || layer.id in MultiSelect.ids)) drawRect(Color.Black, Offset(headerWidth, top), Size(size.width - headerWidth, RowHeight.toPx()), alpha = SelectedRowShade)
    if (layer.kind == "audio") drawAudioBar(outline, layer, left, right, barTop, barBottom, time.pxPerMs)
    else drawBar(outline, left, right, barTop, barBottom, stripe, picture, layer.startTime, time.pxPerMs, layer.kind == "text", layer.kind == "camera", layer.isPlainGroup, layer.kind == "nullobj")
    layer.labelColour?.let { drawTagCorner(outline, left, barTop, (barBottom - barTop) / 2, Color(it.start)) }
    val mediaLabel = deskLayout && picture != null
    val labelStyle = if (mediaLabel) styles.mediaLabel else if (layer.kind == "audio") styles.audioLabel else styles.label
    drawLabel(layer.shownName(), measurer, if (deskLayout) max(left, headerWidth) else left, right, barTop, barBottom, labelStyle, mediaLabel)
    drawKeyframes(keyFrames, scene, time, maxOf(left, headerWidth), right, centre, outline, activeKeys, selectedKeys, playhead)
    if (deskLayout) {
        val chosen = selected || layer.id in MultiSelect.ids
        drawDeskClipFrame(left, right, barTop, barBottom, headerWidth, chosen, hovered, limit)
        drawDeskHeader(layer, chosen, hovered, top, headerWidth, eye, lock, measurer, styles, layer.node["clippingMask"] == "true", controls)
    } else {
        if (selected || layer.id in MultiSelect.ids) {
            drawGrip(outline, left, barTop, barBottom, start = true, appear, limit == true)
            drawGrip(outline, right, barTop, barBottom, start = false, appear, limit == false)
        } else {
            drawGrabLines(Offset(size.width - HandleInset.toPx(), centre))
        }
        drawHeader(top, eye, lock, layer.labelColour, layer.id in MultiSelect.ids, layer.node["clippingMask"] == "true", headerWidth, controls)
    }
}

private fun DrawScope.drawGroupClip(
    layer: Layer, outline: Path, left: Float, right: Float, stripe: Float, measurer: TextMeasurer, styles: TimelineStyles,
) {
    val top = BarInset.toPx()
    val bottom = size.height - top
    val duration = (layer.endTime + 1f - layer.startTime).coerceAtLeast(1f)
    val scale = (right - left) / duration
    if (layer.kind == "audio") drawAudioBar(outline, layer, left, right, top, bottom, scale)
    else drawBar(outline, left, right, top, bottom, stripe, null, layer.startTime, scale,
        layer.kind == "text", layer.kind == "camera", layer.isPlainGroup, layer.kind == "nullobj")
    layer.labelColour?.let { drawTagCorner(outline, left, top, (bottom - top) / 2, Color(it.start)) }
    drawLabel(layer.shownName(), measurer, max(left, 0f), right, top, bottom, if (layer.kind == "audio") styles.audioLabel else styles.label)
}

private fun DrawScope.drawDeskClipFrame(left: Float, right: Float, top: Float, bottom: Float, headerWidth: Float, chosen: Boolean, hovered: Boolean, limit: Boolean?) {
    val from = max(left, headerWidth)
    val to = min(right, size.width)
    if (to <= from || !chosen && !hovered) return
    val stroke = (if (chosen) 2.dp else 1.dp).toPx()
    val inset = stroke / 2
    drawRoundRect(if (chosen) Am.Accent else Color.White.copy(alpha = 0.65f), Offset(from + inset, top + inset), Size(to - from - stroke, bottom - top - stroke), CornerRadius(BarRadius.toPx()), style = Stroke(stroke))
    if (!chosen) return
    val width = 3.dp.toPx()
    val height = (bottom - top) * 0.5f
    for (i in 0..1) {
        val start = i == 0
        val edge = if (start) left else right
        if (edge < headerWidth || edge > size.width) continue
        val x = if (start) edge + 3.dp.toPx() else edge - 3.dp.toPx() - width
        drawRoundRect(if (limit == start) LimitColor else Am.OnDark, Offset(x, (top + bottom - height) / 2), Size(width, height), CornerRadius(width / 2))
    }
}

private fun DrawScope.drawDeskHeader(layer: Layer, chosen: Boolean, hovered: Boolean, top: Float, width: Float, eye: Painter, lock: Painter, measurer: TextMeasurer, styles: TimelineStyles, clipped: Boolean = false, controls: Boolean = false) {
    val height = RowHeight.toPx()
    drawRect(Am.TimelineBg, Offset(0f, top), Size(width, height))
    val pillHeight = height - HeaderInset.toPx() * 2
    val pillTop = Offset(-pillHeight, top + HeaderInset.toPx())
    val trim = if (clipped) DeskClipTrim.toPx() else 0f
    val pill = Size(width - 2.dp.toPx() - trim + pillHeight, pillHeight)
    if (clipped) drawClipHook(width - 2.dp.toPx() - trim + 1.dp.toPx(), top + height / 2)
    val radius = CornerRadius(pillHeight / 2)
    drawRoundRect(HeaderFill, pillTop, pill, radius)
    if (chosen) drawRoundRect(Am.Accent.copy(alpha = 0.28f), pillTop, pill, radius)
    else if (hovered) drawRoundRect(Am.Field, pillTop, pill, radius)
    else if (layer.isPlainGroup) drawRoundRect(GroupBar.copy(alpha = 0.18f), pillTop, pill, radius)
    drawRoundRect(HeaderEdge, pillTop, pill, radius, style = Stroke(0.5.dp.toPx()))
    if (chosen) drawRect(Am.Accent, Offset(0f, pillTop.y), Size(3.dp.toPx(), pillHeight))
    layer.labelColour?.let { drawRect(Color(it.start), Offset(0f, pillTop.y), Size(3.dp.toPx(), pillHeight)) }
    val icon = eye.intrinsicSize
    translate(EyeCentre.toPx() - icon.width / 2, top + (height - icon.height) / 2) { with(eye) { draw(icon) } }
    if (controls) drawPadlock(lock, top, height)
    val left = (thumbLeft(controls) + ThumbSize + 4.dp).toPx()
    val room = (width - trim - left - DisclosureZone.toPx()).toInt()
    if (room < 1) return
    val text = measurer.measure(layer.shownName(), if (layer.hidden) styles.hiddenHeader else styles.header, TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = room))
    drawText(text, topLeft = Offset(left, top + (height - text.size.height) / 2))
}

private fun DrawScope.drawBar(outline: Path, left: Float, right: Float, top: Float, bottom: Float, stripe: Float, picture: StripPicture?, startTime: Int, pxPerMs: Float, text: Boolean = false, camera: Boolean = false, group: Boolean = false, nullLayer: Boolean = false) {
    val radius = BarRadius.toPx()
    val from = max(left, -2 * radius)
    val to = min(right, size.width + 2 * radius)
    if (from >= to) return
    drawRect(BarShadow, Offset(from, bottom - radius), Size(to - from, radius + BarShadowHeight.toPx()))
    outline.rewind()
    outline.addRoundRect(RoundRect(from, top, to, bottom, CornerRadius(radius)))
    clipPath(outline) {
        drawRect(if (camera) CameraBar else if (text) TextBar else if (group) GroupBar else if (nullLayer) NullBar else Am.LayerBar, Offset(from, top), Size(to - from, bottom - top))
        if (picture != null) {
            val tileHeight = (bottom - top).roundToInt().coerceAtLeast(1)
            val tileWidth = (tileHeight.toFloat() * picture.size.width / picture.size.height).roundToInt().coerceAtLeast(1)
            var x = left + tileWidth * floor((from - left) / tileWidth)
            while (x < to) {
                val projectTime = startTime + (x - left) / pxPerMs
                val frame = picture.frames.lastOrNull { it.first <= projectTime + 1 }?.second
                val bitmap = frame ?: picture.bitmap
                drawImage(bitmap, srcOffset = if (frame == null) picture.offset else IntOffset.Zero,
                    srcSize = if (frame == null) picture.size else IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(x.roundToInt(), top.roundToInt()), dstSize = IntSize(tileWidth, tileHeight))
                x += tileWidth
            }
        } else {
            var x = left + stripe * (2 * floor((from - left) / (2 * stripe)) + 1)
            while (x < to) {
                drawRect(if (camera) CameraStripe else if (text) TextStripe else if (group) GroupStripe else if (nullLayer) NullStripe else BarStripe, Offset(x, top), Size(stripe, bottom - top))
                x += 2 * stripe
            }
        }
    }
}

private fun DrawScope.drawKeyframes(frames: List<Int>, scene: Scene, time: TimeScale, left: Float, right: Float, centre: Float, outline: Path, active: Boolean, selected: Set<Int>, playhead: Int) {
    if (frames.isEmpty() || right <= left || right <= 0f || left >= size.width) return
    val half = if (active) 4.5.dp.toPx() else KeyFaint.width.toPx() / 2
    clipRect(left = max(left, 0f), right = min(right, size.width)) {
        for (frame in frames) {
            val x = time.x(scene.frameStart(frame).toFloat())
            if (x !in -half..size.width + half) continue
            outline.rewind()
            outline.moveTo(x, centre - half)
            outline.lineTo(x + half, centre)
            outline.lineTo(x, centre + half)
            outline.lineTo(x - half, centre)
            outline.close()
            if (active) {
                drawTimelineKey(outline, frame in selected, frame == playhead)
            } else {
                drawPath(outline, Color.White.copy(alpha = 0.42f))
            }
        }
    }
}

private fun DrawScope.drawTagCorner(outline: Path, left: Float, top: Float, side: Float, colour: Color) {
    outline.rewind()
    outline.moveTo(left, top + side)
    outline.lineTo(left, top)
    outline.lineTo(left + side, top)
    outline.close()
    drawPath(outline, colour)
}

private fun DrawScope.drawLabel(label: String, measurer: TextMeasurer, left: Float, right: Float, top: Float, bottom: Float, style: TextStyle, backdrop: Boolean = false) {
    val inset = LabelInset.toPx()
    val room = min((right - left - 2 * inset).toInt(), MAX_LABEL_PX)
    if (room < 1 || left + inset > size.width || left + inset + room < 0) return
    val whole = measurer.measure(label, style, TextOverflow.Ellipsis, maxLines = 1)
    val text = if (whole.size.width <= room) whole else measurer.measure(label, style, TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = room))
    val textTop = (top + bottom - text.size.height) / 2
    if (backdrop) drawRoundRect(Color.Black.copy(alpha = 0.65f), Offset(left + inset - 3.dp.toPx(), textTop), Size(text.size.width + 6.dp.toPx(), text.size.height.toFloat()), CornerRadius(3.dp.toPx()))
    drawText(text, topLeft = Offset(left + inset, textTop))
}

private fun DrawScope.drawGrip(outline: Path, edge: Float, top: Float, bottom: Float, start: Boolean, appear: Float = 1f, limit: Boolean = false) {
    val width = (GripZone - GripGap * 2).toPx()
    val left = if (start) edge - GripGap.toPx() - width else edge + GripGap.toPx()
    if (left > size.width || left + width < 0) return
    val far = CornerRadius((bottom - top) / 2)
    val near = CornerRadius(1.dp.toPx())
    val rect = Rect(left, top, left + width, bottom)
    outline.rewind()
    outline.addRoundRect(
        if (start) RoundRect(rect, topLeft = far, bottomLeft = far, topRight = near, bottomRight = near)
        else RoundRect(rect, topLeft = near, bottomLeft = near, topRight = far, bottomRight = far),
    )
    drawPath(outline, if (limit) LimitColor else GripColor, alpha = appear)

    val centre = rect.center
    val reach = 1.95.dp.toPx() * if (start) 1 else -1
    val rise = 4.1.dp.toPx()
    val tip = Offset(centre.x - reach, centre.y)
    for (side in intArrayOf(-1, 1)) {
        drawLine(Am.TimelineBg, Offset(centre.x + reach, centre.y + side * rise), tip, 1.5.dp.toPx(), StrokeCap.Round, alpha = appear)
    }
}

private fun DrawScope.drawGrabLines(centre: Offset) {
    val half = 4.dp.toPx()
    for (line in -1..1) {
        val y = centre.y + line * 2.85.dp.toPx()
        drawLine(HandleColor, Offset(centre.x - half, y), Offset(centre.x + half, y), 0.7.dp.toPx())
    }
}

private fun DrawScope.drawPadlock(lock: Painter, top: Float, height: Float) {
    val icon = lock.intrinsicSize
    translate(LockCentre.toPx() - icon.width / 2, top + (height - icon.height) / 2) { with(lock) { draw(icon) } }
}

private fun DrawScope.drawHeader(top: Float, eye: Painter, lock: Painter, tag: LabelColour?, checked: Boolean, clipped: Boolean, width: Float, controls: Boolean) {
    val height = (RowHeight - HeaderInset * 2).toPx()
    val topLeft = Offset(-height, top + HeaderInset.toPx())
    val trim = if (clipped) ClipTrim.toPx() else 0f
    val pill = Size(width + height - trim, height)
    if (clipped) drawClipHook(width - trim + 1.dp.toPx(), top + RowHeight.toPx() / 2)
    val radius = CornerRadius(height / 2)
    drawRoundRect(if (checked) Am.Accent else HeaderFill, topLeft, pill, radius)
    drawRoundRect(HeaderEdge, topLeft, pill, radius, style = Stroke(0.5.dp.toPx()))
    if (tag != null) drawRect(Color(tag.start), Offset(0f, topLeft.y + 0.7.dp.toPx()), Size(TagStripWidth.toPx(), height - 1.8.dp.toPx()))
    if (controls) drawPadlock(lock, top, RowHeight.toPx())
    if (MultiSelect.active) {
        if (checked) {
            val centre = Offset(EyeCentre.toPx(), top + RowHeight.toPx() / 2)
            drawCircle(HeaderFill, 7.5.dp.toPx(), centre)
            val path = Path().apply {
                moveTo(centre.x - 3.5.dp.toPx(), centre.y)
                lineTo(centre.x - 1.dp.toPx(), centre.y + 2.5.dp.toPx())
                lineTo(centre.x + 4.dp.toPx(), centre.y - 3.dp.toPx())
            }
            drawPath(path, Am.Accent, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
        }
        return
    }
    val icon = eye.intrinsicSize
    translate(EyeCentre.toPx() - icon.width / 2, top + (RowHeight.toPx() - icon.height) / 2) {
        with(eye) { draw(icon) }
    }
}

private val hookPath = Path()

private fun DrawScope.drawClipHook(x: Float, y: Float) {
    val bend = 3.dp.toPx()
    val end = x + 8.dp.toPx()
    val tip = y + 11.dp.toPx()
    val head = 3.dp.toPx()
    val hook = hookPath.apply {
        rewind()
        moveTo(x, y)
        lineTo(end - bend, y)
        quadraticTo(end, y, end, y + bend)
        lineTo(end, tip)
        moveTo(end - head, tip - head)
        lineTo(end, tip)
        lineTo(end + head, tip - head)
    }
    drawPath(hook, Color.Black.copy(alpha = 0.45f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(hook, Am.OnDark, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawPlayhead(originX: Float, timecode: String, measurer: TextMeasurer, styles: TimelineStyles, outline: Path, onBookmark: Boolean, minLeft: Float = Float.NEGATIVE_INFINITY) {
    val width = PlayheadWidth.toPx()
    drawRect(if (onBookmark) BookmarkColor else Am.OnDark, Offset(originX, 0f), Size(width, size.height))
    if (onBookmark) {
        val centre = originX + width / 2
        outline.rewind()
        if (deskLayout) {
            val peak = 5.dp.toPx()
            val tip = 11.dp.toPx()
            outline.moveTo(centre - peak, 0f)
            outline.lineTo(centre + peak, 0f)
            outline.quadraticTo(centre + peak, tip * 0.65f, centre, tip)
            outline.quadraticTo(centre - peak, tip * 0.65f, centre - peak, 0f)
        } else {
            outline.moveTo(centre, 0f)
            outline.lineTo(centre + BookmarkPeakHalf.toPx(), BookmarkFlagBottom.toPx())
            outline.lineTo(centre - BookmarkPeakHalf.toPx(), BookmarkFlagBottom.toPx())
        }
        outline.close()
        drawPath(outline, BookmarkColor)
    }

    val left = maxOf(minLeft, originX + (width - PillWidth.toPx()) / 2)
    val right = left + PillWidth.toPx()
    val top = PillTop.toPx()
    val bottom = top + PillHeight.toPx()
    val notch = PillNotch.toPx()
    val round = (if (deskLayout) 4.dp else 1.dp).toPx()
    outline.rewind()
    outline.moveTo(left + notch, top)
    outline.lineTo(right - round, top)
    outline.quadraticTo(right, top, right, top + round)
    outline.lineTo(right, bottom - round)
    outline.quadraticTo(right, bottom, right - round, bottom)
    outline.lineTo(left + round, bottom)
    outline.quadraticTo(left, bottom, left, bottom - round)
    outline.lineTo(left, top + notch)
    outline.close()
    drawPath(outline, PillColor)
    if (onBookmark) clipPath(outline) { drawPath(outline, BookmarkColor, style = Stroke(2 * PillOutline.toPx())) }

    val text = measurer.measure(timecode, styles.timecode)
    drawText(text, topLeft = Offset((left + right - text.size.width) / 2, (top + bottom - text.size.height) / 2))
}
