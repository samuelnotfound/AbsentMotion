package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val TRANSFORM_GIZMO = "transform-gizmo"
internal const val ORBIT_RADIUS_DP = 36f

private val AxisColors = listOf(Color(0xFFFF7570), Color(0xFF80DA87), Color(0xFF77B9FF))
private val GizmoShadow = Color.Black.copy(alpha = 0.7f)

@Composable
internal fun TransformAxes(projection: PivotProjection, point: FloatArray, scale: Float, offset: Offset, resize: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val axes = PivotDrag(projection, point, null, scale, offset, true, density)
        val centre = axes.point() ?: return@Canvas
        for ((axis, base) in AxisColors.withIndex()) {
            val tip = axes.handle(axis) ?: continue
            val direction = axes.axisDirection(axis) ?: continue
            val color = base.copy(alpha = if (resize && axis == 2) 0.44f else 1f)
            drawLine(GizmoShadow, centre, tip, 4.dp.toPx(), StrokeCap.Round)
            drawLine(color, centre, tip, 1.5.dp.toPx(), StrokeCap.Round)
            if (resize && axis < 2) {
                val half = 3.5.dp.toPx()
                drawRect(color, tip - Offset(half, half), Size(half * 2, half * 2))
            } else if (!resize) {
                val back = tip - direction * 7.dp.toPx()
                val cross = Offset(-direction.y, direction.x) * 3.dp.toPx()
                drawLine(color, back - cross, tip, 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, back + cross, tip, 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        drawCircle(GizmoShadow, 4.dp.toPx(), centre)
        drawCircle(Color.White, 2.4.dp.toPx(), centre)
    }
}

@Composable
internal fun LayerRotationOrb(projection: PivotProjection, point: FloatArray, scale: Float, offset: Offset) {
    Canvas(Modifier.fillMaxSize()) {
        val centre = projection.project(point)?.let { it * scale + offset } ?: return@Canvas
        val radius = ORBIT_RADIUS_DP.dp.toPx()
        val extent = (0..2).maxOf { projection.unit(point, it)?.getDistance() ?: 0f } * scale
        if (extent < 1e-5f) return@Canvas
        drawCircle(GizmoShadow, radius, centre, style = Stroke(4.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.82f), radius, centre, style = Stroke(1.5.dp.toPx()))
        val reach = radius * 0.87f / extent
        for ((axis, color) in AxisColors.withIndex()) {
            val first = (axis + 1) % 3
            val second = (axis + 2) % 3
            val path = Path()
            var connected = false
            for (i in 0..48) {
                val angle = (2 * PI * i / 48).toFloat()
                val value = point.copyOf()
                value[first] += cos(angle) * reach
                value[second] += sin(angle) * reach
                val sample = projection.project(value)?.let { it * scale + offset }
                    ?.takeIf { (it - centre).getDistance() <= radius * 1.03f }
                if (sample == null) {
                    connected = false
                } else {
                    if (connected) path.lineTo(sample.x, sample.y) else path.moveTo(sample.x, sample.y)
                    connected = true
                }
            }
            drawPath(path, GizmoShadow, style = Stroke(3.2.dp.toPx()))
            drawPath(path, color, style = Stroke(1.35.dp.toPx()))
        }
    }
}
