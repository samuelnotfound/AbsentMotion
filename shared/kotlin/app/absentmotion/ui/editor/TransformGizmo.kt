package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

internal const val TRANSFORM_GIZMO = "transform-gizmo"
private const val GizmoRadius = 52f

private val AxisColors = listOf(Color(0xFFFF7570), Color(0xFF80DA87), Color(0xFF77B9FF))
private val GizmoShadow = Color.Black.copy(alpha = 0.7f)

@Composable
internal fun ScaleAxes(projection: PivotProjection, point: FloatArray, scale: Float, offset: Offset) {
    Canvas(Modifier.fillMaxSize()) {
        val axes = PivotDrag(projection, point, null, scale, offset, true, density)
        val centre = axes.point() ?: return@Canvas
        for ((axis, base) in AxisColors.withIndex()) {
            val tip = axes.handle(axis) ?: continue
            val color = base.copy(alpha = if (axis == 2) 0.44f else 1f)
            drawLine(GizmoShadow, centre, tip, 5.dp.toPx(), StrokeCap.Round)
            drawLine(color, centre, tip, 1.5.dp.toPx(), StrokeCap.Round)
            if (axis < 2) {
                val half = 3.5.dp.toPx()
                drawRect(color, tip - Offset(half, half), Size(half * 2, half * 2))
            }
        }
        drawCircle(GizmoShadow, 4.dp.toPx(), centre)
        drawCircle(Color.White, 2.4.dp.toPx(), centre)
    }
}


internal data class GizmoPoint(val at: Offset, val depth: Float)

internal class SpatialGizmoProjection(private val view: FloatArray, private val position: FloatArray) {
    private fun row(index: Int, v: FloatArray): Float =
        view[index] * v[0] + view[index + 1] * v[1] + view[index + 2] * v[2] + view[index + 3]

    fun point(v: FloatArray): GizmoPoint? {
        val w = row(12, v)
        val depth = row(8, v)
        if (w <= 1e-5f || depth < 1f) return null
        val computed = Offset(row(0, v) / w, row(4, v) / w)
        return GizmoPoint(computed, depth).takeIf { computed.x.isFinite() && computed.y.isFinite() }
    }

    fun tangent(axis: Int): Offset {
        val w = view[15]
        if (w <= 1e-5f) return Offset.Zero
        return Offset((view[axis] - view[3] * view[12 + axis] / w) / w,
            (view[4 + axis] - view[7] * view[12 + axis] / w) / w)
    }

    fun depthAxis(axis: Int): Float = view[8 + axis]

    fun positionDelta(axis: Int, distance: Float): FloatArray =
        FloatArray(3) { position[it * 3 + axis] * distance }

    fun layerDepth(at: Offset, z: Float): Float? {
        val a = view[0] - at.x * view[12]
        val b = view[1] - at.x * view[13]
        val c = view[4] - at.y * view[12]
        val d = view[5] - at.y * view[13]
        val p = at.x * (view[14] * z + view[15]) - view[2] * z - view[3]
        val q = at.y * (view[14] * z + view[15]) - view[6] * z - view[7]
        val det = a * d - b * c
        if (abs(det) < 1e-5f) return null
        val x = (p * d - b * q) / det
        val y = (a * q - c * p) / det
        return (view[8] * x + view[9] * y + view[10] * z + view[11]).takeIf { it.isFinite() && it >= 1f }
    }

    companion object {
        fun from(matrices: FloatArray): SpatialGizmoProjection? =
            if (matrices.size >= 55) SpatialGizmoProjection(matrices.copyOfRange(30, 46), matrices.copyOfRange(46, 55)) else null
    }
}

internal class SpatialGizmo(
    private val projection: SpatialGizmoProjection, private val scale: Float, private val offset: Offset,
    private val density: Float, private val planeZ: Float,
) {
    private val origin = FloatArray(3)
    val centre = project(origin)?.at
    private val tangents = (0..2).map { projection.tangent(it) * scale }
    private val unit = sqrt(tangents.sumOf { vector -> (vector.x * vector.x + vector.y * vector.y).toDouble() }.toFloat() / 2)
    private val reach = GizmoRadius * density / unit.coerceAtLeast(1e-5f)
    private val arms = (0..2).map { project(pointOnAxis(it, reach)) }
    private val depthHandle = (0..2).maxBy { abs(projection.depthAxis(it)) }.takeIf {
        tangents[it].getDistance() * reach < 12 * density
    }

    private fun project(v: FloatArray): GizmoPoint? = projection.point(v)?.let { it.copy(at = it.at * scale + offset) }
    private fun pointOnAxis(axis: Int, value: Float) = FloatArray(3).also { it[axis] = value }

    fun axisHit(at: Offset): Int? {
        val pivot = centre ?: return null
        if ((at - pivot).getDistance() <= 10 * density) return depthHandle
        return (0..2).filter { it != depthHandle }.mapNotNull { i ->
            arms[i]?.let { tip -> i to distanceToSegment(pivot, tip.at, at).first }
        }.minByOrNull { it.second }?.takeIf { it.second <= 12 * density }?.first
    }

    fun move(axis: Int, travel: Offset): FloatArray {
        val tangent = tangents[axis]
        val length = tangent.getDistance()
        val distance = if (axis == depthHandle) {
            -travel.y * kotlin.math.sign(projection.depthAxis(axis)) / unit.coerceAtLeast(1e-5f)
        } else {
            (tangent.x * travel.x + tangent.y * travel.y) / (length * length).coerceAtLeast(1e-5f)
        }
        return projection.positionDelta(axis, distance)
    }

    private fun occluded(a: GizmoPoint, b: GizmoPoint): Boolean {
        val at = (a.at + b.at) / 2f
        val face = projection.layerDepth((at - offset) / scale, planeZ) ?: return false
        return (a.depth + b.depth) / 2f > face + 0.05f
    }

    private fun ringPoint(axis: Int, angle: Float): GizmoPoint? {
        val first = (axis + 1) % 3
        val second = (axis + 2) % 3
        return project(FloatArray(3).also { point ->
            point[first] = cos(angle) * reach
            point[second] = sin(angle) * reach
        })
    }

    private data class Arc(val axis: Int, val angle: Float, val a: GizmoPoint, val b: GizmoPoint)
    private val arcs by lazy {
        buildList {
            for (axis in 0..2) {
                var a = ringPoint(axis, 0f)
                for (i in 0 until Segments) {
                    val b = ringPoint(axis, (i + 1) * AngleStep)
                    if (a != null && b != null) add(Arc(axis, i * AngleStep, a, b))
                    a = b
                }
            }
        }
    }

    data class RingGrab(val axis: Int, val angle: Float)

    fun ringHit(at: Offset): RingGrab? = nearestRing(at)?.takeIf { it.first <= 9 * density }?.second

    fun ringAngle(at: Offset, previous: RingGrab): Float? = nearestRing(at, previous)?.second?.angle

    private fun nearestRing(at: Offset, previous: RingGrab? = null): Pair<Float, RingGrab>? {
        var best: Pair<Float, RingGrab>? = null
        var bestDepth = Float.MAX_VALUE
        for (arc in arcs) {
            if (previous != null && previous.axis != arc.axis) continue
            val (distance, along) = distanceToSegment(arc.a.at, arc.b.at, at)
            val angle = arc.angle + AngleStep * along
            val continuous = if (previous == null) angle else previous.angle + angleDifference(previous.angle, angle)
            if (previous != null && abs(continuous - previous.angle) > (PI / 2).toFloat()) continue
            val depth = arc.a.depth + (arc.b.depth - arc.a.depth) * along
            val earlier = best
            if (earlier == null || distance < earlier.first - density ||
                abs(distance - earlier.first) <= density && (if (previous == null) depth < bestDepth else abs(continuous - previous.angle) < abs(earlier.second.angle - previous.angle))) {
                best = distance to RingGrab(arc.axis, continuous)
                bestDepth = depth
            }
        }
        return best
    }

    fun drawArms(draw: DrawScope, surface: Path?) = with(draw) {
        val pivot = project(origin) ?: return@with
        for ((axis, color) in AxisColors.withIndex()) {
            val tip = arms[axis] ?: continue
            if (axis == depthHandle) {
                drawCircle(GizmoShadow, 7.dp.toPx(), pivot.at, style = Stroke(3.dp.toPx()))
                drawCircle(color, 7.dp.toPx(), pivot.at, style = Stroke(2.dp.toPx()))
                continue
            }
            val vector = tip.at - pivot.at
            val length = vector.getDistance()
            if (length <= 0.5.dp.toPx()) continue
            val direction = vector / length
            val back = tip.at - direction * minOf(11.dp.toPx(), length * 0.36f)
            val cross = Offset(-direction.y, direction.x) * minOf(5.dp.toPx(), length * 0.21f)
            val base = project(pointOnAxis(axis, -reach * 0.70f))
            fun arm(a: GizmoPoint, b: GizmoPoint, positive: Boolean) {
                fun paint(alpha: Float) {
                    val tint = color.copy(alpha = alpha * if (positive) 1f else 0.66f)
                    drawLine(GizmoShadow.copy(alpha = GizmoShadow.alpha * alpha), a.at, b.at, 5.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, a.at, b.at, 2.2.dp.toPx(), StrokeCap.Round)
                    if (positive) {
                        drawLine(tint, back - cross, tip.at, 2.2.dp.toPx(), StrokeCap.Round)
                        drawLine(tint, back + cross, tip.at, 2.2.dp.toPx(), StrokeCap.Round)
                    }
                }
                if (surface != null && occluded(a, b)) {
                    clipPath(surface, ClipOp.Difference) { paint(1f) }
                    clipPath(surface) { paint(0.62f) }
                } else paint(1f)
            }
            if (base != null) arm(base, pivot, false)
            arm(pivot, tip, true)
        }
        drawCircle(Color.White, 2.2.dp.toPx(), pivot.at)
    }

    fun drawRings(draw: DrawScope, surface: Path?) = with(draw) {
        val rearPaths = Array(AxisColors.size) { Path() }
        val frontPaths = Array(AxisColors.size) { Path() }
        for (arc in arcs) {
            val path = if (occluded(arc.a, arc.b)) rearPaths[arc.axis] else frontPaths[arc.axis]
            path.moveTo(arc.a.at.x, arc.a.at.y)
            path.lineTo(arc.b.at.x, arc.b.at.y)
        }

        fun ring(path: Path, tint: Color, alpha: Float = 1f) {
            drawPath(path, GizmoShadow, alpha = alpha, style = Stroke(3.4.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, tint, alpha = alpha, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        for ((axis, color) in AxisColors.withIndex()) {
            val rear = rearPaths[axis]
            if (surface == null) {
                ring(rear, color, 0.60f)
            } else {
                clipPath(surface, ClipOp.Difference) { ring(rear, color, 0.60f) }
                clipPath(surface) { ring(rear, color, 0.32f) }
            }
        }
        for ((axis, color) in AxisColors.withIndex()) ring(frontPaths[axis], color)
        centre?.let { drawCircle(Color.White, 2.2.dp.toPx(), it) }
    }
}

private const val Segments = 72
private val AngleStep = (2 * PI / Segments).toFloat()

private fun angleDifference(from: Float, to: Float): Float {
    val period = (2 * PI).toFloat()
    val half = period / 2f
    val difference = (to - from) % period
    return if (difference > half) difference - period else if (difference < -half) difference + period else difference
}

private fun distanceToSegment(a: Offset, b: Offset, at: Offset): Pair<Float, Float> {
    val direction = b - a
    val length = direction.x * direction.x + direction.y * direction.y
    val relative = at - a
    val along = if (length < 1e-6f) 0f else ((relative.x * direction.x + relative.y * direction.y) / length).coerceIn(0f, 1f)
    return (a + direction * along - at).getDistance() to along
}
