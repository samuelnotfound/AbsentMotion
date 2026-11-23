package app.absentmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.ui.editor.panels.formatFixed
import app.absentmotion.ui.theme.Am
import kotlin.math.roundToInt

internal const val AXIS_GESTURE = "viewport-axis"

@Composable
internal fun MoveAxisGizmo(projection: PivotProjection, point: FloatArray, scale: Float, offset: Offset, active: Int?) {
    val density = LocalDensity.current.density
    val gizmo = PivotDrag(projection, point, null, scale, offset, true, density)
    val centre = gizmo.point() ?: return
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val colors = listOf(Color(0xFFF87370), Color(0xFF73D58D), Color(0xFF72ADFF))
            for (axis in colors.indices) {
                val tip = gizmo.handle(axis) ?: continue
                val vector = gizmo.axisDirection(axis) ?: continue
                val normal = Offset(-vector.y, vector.x)
                val a = tip - vector * 7.dp.toPx() + normal * 3.dp.toPx()
                val b = tip - vector * 7.dp.toPx() - normal * 3.dp.toPx()
                val tint = colors[axis]
                drawLine(Color.Black.copy(alpha = 0.65f), centre, tip, 4.dp.toPx(), StrokeCap.Round)
                drawLine(tint, centre, tip, (if (active == axis) 3.dp else 1.5.dp).toPx(), StrokeCap.Round)
                drawLine(tint, a, tip, 1.7.dp.toPx(), StrokeCap.Round)
                drawLine(tint, b, tip, 1.7.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(Color.White, 2.5.dp.toPx(), centre)
        }
        for (axis in 0..2) {
            val tip = gizmo.handle(axis) ?: continue
            Text(if (active == axis) "${"XYZ"[axis]} ${formatFixed(point[axis], 1)}" else "XYZ"[axis].toString(),
                Modifier.offset { IntOffset((tip.x + 4 * density).roundToInt(), (tip.y - 15 * density).roundToInt()) }
                    .background(Am.Panel.copy(alpha = 0.9f), RoundedCornerShape(5.dp)).padding(horizontal = 4.dp),
                color = Am.OnDark, fontSize = 10.sp, maxLines = 1)
        }
    }
}
