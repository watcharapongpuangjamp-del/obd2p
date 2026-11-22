package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.core.model.DataQuality
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.TextMutedDark
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularGauge(
    value: Float,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    color: Color = CyberCyan,
    quality: DataQuality = DataQuality.VALID,
    content: @Composable () -> Unit
) {
    val sweepAngle = 240f
    val startAngle = 150f

    val targetProgress = if (max > min && (quality == DataQuality.VALID || quality == DataQuality.STALE)) {
        ((value - min) / (max - min)).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 220),
        label = "circular_gauge_progress"
    )

    val effectiveColor = when (quality) {
        DataQuality.VALID -> color
        DataQuality.STALE -> HighEnergyAmber.copy(alpha = 0.75f)
        else -> TextMutedDark.copy(alpha = 0.35f)
    }

    val progressAngle = animatedProgress * sweepAngle

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.width * 0.08f
            val arcSize = (size.width - strokeWidth).coerceAtLeast(1f)
            val arcOffset = strokeWidth / 2f

            // Background track
            drawArc(
                color = SurfaceBorderDark,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(arcSize, arcSize),
                topLeft = Offset(arcOffset, arcOffset)
            )

            // Value arc
            if (progressAngle > 0f) {
                drawArc(
                    color = effectiveColor,
                    startAngle = startAngle,
                    sweepAngle = progressAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(arcSize, arcSize),
                    topLeft = Offset(arcOffset, arcOffset)
                )
            }

            // Tick marks derived strictly from Canvas DrawScope size
            val radius = size.width / 2f
            val center = Offset(radius, radius)
            val tickCount = 10
            for (i in 0..tickCount) {
                val angle = startAngle + (i * sweepAngle / tickCount)
                val angleRad = Math.toRadians(angle.toDouble()).toFloat()

                val innerRadius = radius - strokeWidth * 1.5f
                val outerRadius = radius - strokeWidth * 0.5f

                val start = Offset(
                    x = center.x + innerRadius * cos(angleRad),
                    y = center.y + innerRadius * sin(angleRad)
                )
                val end = Offset(
                    x = center.x + outerRadius * cos(angleRad),
                    y = center.y + outerRadius * sin(angleRad)
                )

                drawLine(
                    color = SurfaceBorderDark.copy(alpha = 0.5f),
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth * 0.1f
                )
            }
        }

        content()
    }
}
