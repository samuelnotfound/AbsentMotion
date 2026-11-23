package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.absentmotion.editor.PIVOT_LIMIT
import app.absentmotion.editor.map
import app.absentmotion.model.Pt
import app.absentmotion.platform.Matrix
import app.absentmotion.ui.theme.Am
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val PIVOT_DRAG = "pivot-drag"

internal class PivotProjection(private val m: FloatArray) {
    private fun at(row: Int, v: FloatArray): Float =
        m[row] * v[0] + m[row + 1] * v[1] + m[row + 2] * v[2] + m[row + 3]

    fun project(v: FloatArray): Offset? {
        val w = at(8, v)
        if (w <= 1e-6f) return null
        val point = Offset(at(0, v) / w, at(4, v) / w)
        return point.takeIf { it.x.isFinite() && it.y.isFinite() }
    }

    fun plane(z: Float): Matrix = Matrix().apply {
        setValues(floatArrayOf(
            m[0], m[1], m[2] * z + m[3],
            m[4], m[5], m[6] * z + m[7],
            m[8], m[9], m[10] * z + m[11],
        ))
    }

    fun movedAlong(origin: FloatArray, axis: Int, target: Offset): Float? {
        val w = at(8, origin)
        val dx = m[axis] - target.x * m[8 + axis]
        val dy = m[4 + axis] - target.y * m[8 + axis]
        val norm = dx * dx + dy * dy
        if (norm < 1e-8f) return null
        val delta = (dx * (target.x * w - at(0, origin)) + dy * (target.y * w - at(4, origin))) / norm
        return (origin[axis] + delta).takeIf { it.isFinite() }
    }

    fun unit(v: FloatArray, axis: Int): Offset? {
        val from = project(v) ?: return null
        val tip = project(v.copyOf().also { it[axis] += 1f }) ?: return null
        return tip - from
    }

    companion object {
        fun from(matrices: FloatArray): PivotProjection? =
            if (matrices.size >= 30) PivotProjection(matrices.copyOfRange(18, 30)) else null

        fun location(matrices: FloatArray): PivotProjection? =
            if (matrices.size >= 42) PivotProjection(matrices.copyOfRange(30, 42)) else null
    }
}

internal class PivotDrag(
    private val projection: PivotProjection, private val origin: FloatArray, private val bounds: FloatArray?,
    private val scale: Float, private val offset: Offset, private val spatial: Boolean, private val density: Float,
) {
    private val centre = projection.project(origin)?.let(::screen)
    private val axes = (0..2).map { projection.unit(origin, it)?.times(scale) ?: Offset.Zero }
    private val unit = axes.take(if (spatial) 3 else 2).maxOf { it.getDistance() }
    private val held = arrayOfNulls<Float>(3)

    fun point() = centre

    fun axisDirection(axis: Int): Offset? {
        if (!spatial || unit < 1e-5f) return null
        val d = axes[axis]
        val length = d.getDistance()
        return when {
            length >= unit * 0.12f -> d / length
            axis == 2 -> Offset(0.7071f, -0.7071f)
            else -> null
        }
    }

    fun handle(axis: Int): Offset? = centre?.let { a -> axisDirection(axis)?.let { a + it * (36 * density) } }

    fun hit(at: Offset): Int? {
        val a = centre ?: return null
        if ((at - a).getDistance() <= 11 * density) return -1
        return (0..2).mapNotNull { axis -> handle(axis)?.let { axis to (it - at).getDistance() } }
            .minByOrNull { it.second }?.takeIf { it.second <= 13 * density }?.first
    }

    fun move(axis: Int, travel: Offset): Pair<FloatArray, Set<Int>>? {
        val centre = centre ?: return null
        val out = origin.copyOf()
        if (axis < 0) {
            val inverse = Matrix()
            if (!projection.plane(origin[2]).invert(inverse)) return null
            val target = (centre + travel - offset) / scale
            val mapped = inverse.map(Pt(target.x, target.y))
            out[0] = mapped.x
            out[1] = mapped.y
        } else {
            val line = axisDirection(axis) ?: return null
            val distance = line.x * travel.x + line.y * travel.y
            val target = (centre + line * distance - offset) / scale
            out[axis] = if (axis == 2 && axes[2].getDistance() < unit * 0.12f) {
                origin[axis] + distance / unit
            } else projection.movedAlong(origin, axis, target) ?: return null
        }
        if (out.any { !it.isFinite() }) return null
        for (i in out.indices) out[i] = out[i].coerceIn(-PIVOT_LIMIT, PIVOT_LIMIT)
        val adjusted = if (axis < 0) 0..1 else axis..axis
        val snaps = buildSet {
            for (i in adjusted) {
                val snapped = snap(i, out)
                held[i] = snapped
                if (snapped != null) { out[i] = snapped; add(i) }
            }
        }
        return out to snaps
    }

    private fun snap(axis: Int, value: FloatArray): Float? {
        val from = projection.project(value)?.let(::screen) ?: return null
        fun distance(to: Float): Float {
            if (axis == 2 && axes[2].getDistance() < unit * 0.12f) return kotlin.math.abs(value[axis] - to) * unit
            val p = projection.project(value.copyOf().also { it[axis] = to })?.let(::screen) ?: return Float.POSITIVE_INFINITY
            return (p - from).getDistance()
        }
        val last = held[axis]
        if (last != null && distance(last) <= 11 * density) return last
        if (axis == 2 || bounds == null || axes[axis].getDistance() < 1e-5f) return 0f.takeIf { distance(it) <= 6 * density }
        val left = bounds[axis]
        val right = bounds[axis + 2]
        return listOf(left, (left + right) / 2, right).minByOrNull(::distance)?.takeIf { distance(it) <= 6 * density }
    }

    private fun screen(p: Offset) = p * scale + offset
}

internal fun DrawScope.drawPivotRing(projection: PivotProjection, at: FloatArray, scale: Float, offset: Offset, radius: Float, tint: Color, stroke: Float) {
    val x = projection.unit(at, 0)?.getDistance() ?: 0f
    val y = projection.unit(at, 1)?.getDistance() ?: 0f
    val extent = maxOf(x, y) * scale
    if (extent < 1e-5f) return
    val reach = radius / extent
    fun point(x: Float, y: Float) = projection.project(floatArrayOf(at[0] + x * reach, at[1] + y * reach, at[2]))?.let { it * scale + offset }
    val path = Path()
    var connected = false
    for (i in 0..24) {
        val angle = (2 * PI * i / 24).toFloat()
        val p = point(cos(angle), sin(angle))
        if (p == null) {
            connected = false
        } else {
            if (connected) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
            connected = true
        }
    }
    for (dir in listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))) {
        val from = point(dir.x * 0.75f, dir.y * 0.75f) ?: continue
        val length = if (dir.x > 0f) 1.65f else 1.28f
        val to = point(dir.x * length, dir.y * length) ?: continue
        path.moveTo(from.x, from.y)
        path.lineTo(to.x, to.y)
    }
    drawPath(path, Color.Black.copy(alpha = 0.6f), style = Stroke(stroke * 2.5f))
    drawPath(path, tint, style = Stroke(stroke))
}

@Composable
internal fun PivotGizmo(projection: PivotProjection, at: FloatArray, scale: Float, offset: Offset, spatial: Boolean, snaps: Set<Int>) {
    Canvas(Modifier.fillMaxSize()) {
        val drag = PivotDrag(projection, at, null, scale, offset, spatial, density)
        val centre = drag.point() ?: return@Canvas
        val line = 1.5.dp.toPx()
        drawPivotRing(projection, at, scale, offset, 7.dp.toPx(), Color.White, line)
        val colors = listOf(Color(0xFFFF7570), Color(0xFF80DA87), Color(0xFF77B9FF))
        for (axis in colors.indices) {
            val tip = drag.handle(axis) ?: continue
            val direction = drag.axisDirection(axis) ?: continue
            val start = centre + direction * 9.dp.toPx()
            val tint = if (axis in snaps) Color.White else colors[axis]
            drawLine(Color.Black.copy(alpha = 0.75f), start, tip, line * 2.8f, StrokeCap.Round)
            drawLine(tint, start, tip, line, StrokeCap.Round)
            drawCircle(tint, (if (axis in snaps) 5.dp else 3.5.dp).toPx(), tip)
        }
        drawCircle(if (snaps.isEmpty()) Am.Accent else Color.White, 3.dp.toPx(), centre)
    }
}
