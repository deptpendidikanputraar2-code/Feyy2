package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Slate900
import kotlin.math.PI
import kotlin.math.sin

private data class WaterDropletSpec(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val elongation: Float = 1.0f, // > 1.0 gives natural gravity teardrop curve
    val phaseOffset: Float = 0f,
    val driftAmplitudeDp: Float = 0.6f
)

/**
 * Physically-inspired realistic water droplet renderer:
 * 1) Offset soft cast shadow beneath the drop
 * 2) Refracted translucent body with inverted internal caustic lighting
 * 3) Bright bottom internal refraction pool
 * 4) Crisp Fresnel glass rim highlight
 * 5) Upper-left specular gloss arc + lower-right micro pinpoint gleam
 */
private fun DrawScope.drawRealisticWaterDrop(
    center: Offset,
    radiusPx: Float,
    elongation: Float,
    isDarkSurface: Boolean,
    shimmerPulse: Float
) {
    val rx = radiusPx
    val ry = radiusPx * elongation

    // Build organic droplet path (subtle teardrop taper at top when elongated)
    val dropPath = Path().apply {
        val topY = center.y - ry
        val botY = center.y + ry
        val leftX = center.x - rx
        val rightX = center.x + rx
        val topCtrlOffset = rx * (if (elongation > 1.15f) 0.42f else 0.56f)
        val botCtrlOffset = rx * 0.62f

        moveTo(center.x, topY)
        cubicTo(
            center.x + topCtrlOffset, topY,
            rightX, center.y - ry * 0.35f,
            rightX, center.y + ry * 0.1f
        )
        cubicTo(
            rightX, center.y + ry * 0.68f,
            center.x + botCtrlOffset, botY,
            center.x, botY
        )
        cubicTo(
            center.x - botCtrlOffset, botY,
            leftX, center.y + ry * 0.68f,
            leftX, center.y + ry * 0.1f
        )
        cubicTo(
            leftX, center.y - ry * 0.35f,
            center.x - topCtrlOffset, topY,
            center.x, topY
        )
        close()
    }

    // 1. Soft offset cast shadow (depth below water meniscus)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = if (isDarkSurface) 0.40f else 0.20f),
                Color.Black.copy(alpha = if (isDarkSurface) 0.14f else 0.06f),
                Color.Transparent
            ),
            center = Offset(center.x + rx * 0.22f, center.y + ry * 0.30f),
            radius = (rx * 1.35f).coerceAtLeast(1f)
        ),
        topLeft = Offset(center.x - rx * 0.9f, center.y - ry * 0.72f),
        size = Size(rx * 2.2f, ry * 2.25f)
    )

    // 2. Refracted translucent lens body (darker top edge, bright lower caustic)
    val bodyBrush = Brush.linearGradient(
        colors = if (isDarkSurface) {
            listOf(
                Color(0xFF021F17).copy(alpha = 0.52f),
                Color(0xFF28B485).copy(alpha = 0.24f),
                Color(0xFFD8FFF0).copy(alpha = 0.42f + shimmerPulse * 0.10f)
            )
        } else {
            listOf(
                Color(0xFF0F766E).copy(alpha = 0.18f),
                Color(0xFFFFFFFF).copy(alpha = 0.34f),
                Color(0xFFE6FFF7).copy(alpha = 0.72f + shimmerPulse * 0.12f)
            )
        },
        start = Offset(center.x - rx * 0.5f, center.y - ry),
        end = Offset(center.x + rx * 0.45f, center.y + ry)
    )
    drawPath(path = dropPath, brush = bodyBrush)

    // 3. Internal bottom caustic refraction glow (light focused through liquid dome)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (if (isDarkSurface) 0.52f else 0.76f) + shimmerPulse * 0.12f),
                Color(0xFFA7F3D0).copy(alpha = if (isDarkSurface) 0.24f else 0.30f),
                Color.Transparent
            ),
            center = Offset(center.x + rx * 0.06f, center.y + ry * 0.46f),
            radius = (rx * 0.85f).coerceAtLeast(1f)
        ),
        topLeft = Offset(center.x - rx * 0.68f, center.y + ry * 0.04f),
        size = Size(rx * 1.38f, ry * 0.84f)
    )

    // 4. Crisp Fresnel rim reflection around droplet edge
    drawPath(
        path = dropPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDarkSurface) 0.68f else 0.90f),
                Color(0xFF6EE7B7).copy(alpha = if (isDarkSurface) 0.32f else 0.40f),
                Color.White.copy(alpha = if (isDarkSurface) 0.55f else 0.75f)
            ),
            start = Offset(center.x - rx, center.y - ry),
            end = Offset(center.x + rx, center.y + ry)
        ),
        style = Stroke(width = (radiusPx * 0.13f).coerceIn(0.8f, 2.2f))
    )

    // 5. Primary crisp specular highlight (upper-left glossy window glint)
    drawOval(
        color = Color.White.copy(alpha = (0.86f + shimmerPulse * 0.10f).coerceAtMost(0.98f)),
        topLeft = Offset(center.x - rx * 0.56f, center.y - ry * 0.62f),
        size = Size(rx * 0.44f, ry * 0.28f)
    )

    // 6. Secondary micro pinpoint reflection
    drawCircle(
        color = Color.White.copy(alpha = 0.78f),
        radius = (radiusPx * 0.12f).coerceAtLeast(1f),
        center = Offset(center.x - rx * 0.18f, center.y - ry * 0.30f)
    )
}

private val TOP_NAV_DROPLETS = listOf(
    WaterDropletSpec(0.06f, 0.24f, 4.5f, 1.12f, 0.2f),
    WaterDropletSpec(0.11f, 0.78f, 2.6f, 1.0f, 1.1f),
    WaterDropletSpec(0.28f, 0.16f, 3.2f, 1.05f, 2.0f),
    WaterDropletSpec(0.46f, 0.86f, 3.6f, 1.15f, 0.7f),
    WaterDropletSpec(0.62f, 0.18f, 2.4f, 1.0f, 3.1f),
    WaterDropletSpec(0.76f, 0.82f, 4.2f, 1.18f, 1.8f),
    WaterDropletSpec(0.84f, 0.22f, 2.8f, 1.04f, 4.0f),
    WaterDropletSpec(0.95f, 0.74f, 5.2f, 1.24f, 2.6f),
    WaterDropletSpec(0.91f, 0.28f, 2.2f, 1.0f, 5.0f)
)

private val BOTTOM_NAV_DROPLETS = listOf(
    WaterDropletSpec(0.04f, 0.22f, 4.8f, 1.16f, 0.5f),
    WaterDropletSpec(0.14f, 0.82f, 2.8f, 1.0f, 1.6f),
    WaterDropletSpec(0.32f, 0.14f, 3.4f, 1.10f, 2.7f),
    WaterDropletSpec(0.51f, 0.88f, 2.6f, 1.0f, 3.4f),
    WaterDropletSpec(0.69f, 0.18f, 4.0f, 1.18f, 0.9f),
    WaterDropletSpec(0.86f, 0.82f, 3.0f, 1.05f, 4.2f),
    WaterDropletSpec(0.95f, 0.28f, 5.4f, 1.22f, 2.1f)
)

private val SIDEBAR_DROPLETS = listOf(
    WaterDropletSpec(0.12f, 0.06f, 6.2f, 1.28f, 0.3f, 1.2f),
    WaterDropletSpec(0.84f, 0.08f, 4.6f, 1.18f, 1.4f, 0.9f),
    WaterDropletSpec(0.90f, 0.15f, 3.0f, 1.05f, 2.5f, 0.7f),
    WaterDropletSpec(0.07f, 0.23f, 3.8f, 1.15f, 3.1f, 1.0f),
    WaterDropletSpec(0.88f, 0.31f, 7.2f, 1.34f, 0.8f, 1.5f),
    WaterDropletSpec(0.82f, 0.36f, 2.8f, 1.0f, 4.0f, 0.6f),
    WaterDropletSpec(0.10f, 0.48f, 5.2f, 1.26f, 1.9f, 1.1f),
    WaterDropletSpec(0.92f, 0.57f, 4.2f, 1.16f, 5.1f, 0.8f),
    WaterDropletSpec(0.16f, 0.66f, 3.0f, 1.05f, 2.2f, 0.7f),
    WaterDropletSpec(0.86f, 0.74f, 6.4f, 1.30f, 3.7f, 1.3f),
    WaterDropletSpec(0.79f, 0.79f, 2.6f, 1.0f, 0.6f, 0.5f),
    WaterDropletSpec(0.09f, 0.86f, 5.6f, 1.25f, 4.4f, 1.2f),
    WaterDropletSpec(0.89f, 0.93f, 4.8f, 1.20f, 1.1f, 0.9f)
)

/**
 * Liquid Glass Top Navbar with frosted translucent depth, specular light sweep,
 * and photorealistic clinging water droplets.
 */
@Composable
fun LiquidGlassTopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "liquid_top_sheen")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "top_sheen_phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                spotColor = Emerald900.copy(alpha = 0.16f),
                ambientColor = Emerald700.copy(alpha = 0.10f)
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xF5F4FBF8),
                        Color(0xECE8F7F1),
                        Color(0xF2F7FCFA)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Subtle liquid glass specular sheen wave
            val sweepX = size.width * (0.2f + 0.6f * ((sin(phase) + 1f) * 0.5f))
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.52f),
                        Color(0xFFA7F3D0).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    start = Offset(sweepX - size.width * 0.28f, 0f),
                    end = Offset(sweepX + size.width * 0.28f, size.height)
                )
            )

            // Bottom frosted glass specular rim
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Emerald700.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.92f),
                        Emerald700.copy(alpha = 0.26f),
                        Color.White.copy(alpha = 0.85f)
                    )
                ),
                start = Offset(0f, size.height - 1.5f),
                end = Offset(size.width, size.height - 1.5f),
                strokeWidth = 2.5f
            )

            // Realistic water droplets on glass
            TOP_NAV_DROPLETS.forEach { d ->
                val pulse = ((sin(phase + d.phaseOffset) + 1f) * 0.5f)
                val driftY = sin(phase + d.phaseOffset) * d.driftAmplitudeDp.dp.toPx()
                drawRealisticWaterDrop(
                    center = Offset(size.width * d.xFraction, size.height * d.yFraction + driftY),
                    radiusPx = d.radiusDp.dp.toPx(),
                    elongation = d.elongation,
                    isDarkSurface = false,
                    shimmerPulse = pulse
                )
            }
        }
        content()
    }
}

/**
 * Floating/Docked Liquid Glass Bottom Navbar with luminous glass edge and real water drops.
 */
@Composable
fun LiquidGlassBottomBarContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "liquid_bottom_sheen")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bottom_sheen_phase"
    )
    val shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = shape,
                spotColor = Emerald950.copy(alpha = 0.22f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF0F1FBF7),
                        Color(0xF6FFFFFF),
                        Color(0xF5EBF8F2)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Emerald700.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.85f)
                    )
                ),
                shape = shape
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sweepX = size.width * (0.15f + 0.7f * ((sin(phase + 1.2f) + 1f) * 0.5f))
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.58f),
                        Color(0xFF6EE7B7).copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    start = Offset(sweepX - size.width * 0.25f, 0f),
                    end = Offset(sweepX + size.width * 0.25f, size.height)
                )
            )
            // Luminous top glass lip
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(24f, 1.5f),
                end = Offset(size.width - 24f, 1.5f),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round
            )
            BOTTOM_NAV_DROPLETS.forEach { d ->
                val pulse = ((sin(phase + d.phaseOffset) + 1f) * 0.5f)
                val driftY = sin(phase + d.phaseOffset) * d.driftAmplitudeDp.dp.toPx()
                drawRealisticWaterDrop(
                    center = Offset(size.width * d.xFraction, size.height * d.yFraction + driftY),
                    radiusPx = d.radiusDp.dp.toPx(),
                    elongation = d.elongation,
                    isDarkSurface = false,
                    shimmerPulse = pulse
                )
            }
        }
        content()
    }
}

/**
 * Modern Frosted Emerald Glass Sidebar with refracted light ribbons and realistic clinging water droplets.
 */
@Composable
fun LiquidGlassSidebarContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "sidebar_glass_anim")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sidebar_phase"
    )

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Emerald950.copy(alpha = 0.96f),
                        Emerald900.copy(alpha = 0.94f),
                        Color(0xEE062E24),
                        Slate900.copy(alpha = 0.96f)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Diagonal frosted glass refraction ribbons
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.13f),
                        Emerald100.copy(alpha = 0.04f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.08f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height * 0.65f)
                )
            )

            // Soft glowing liquid ambient aura behind sidebar header
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Emerald700.copy(alpha = 0.34f),
                        Emerald800.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.14f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.85f, size.height * 0.14f)
            )

            // Right-edge frosted crystal border line
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        Emerald100.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.28f)
                    )
                ),
                start = Offset(size.width - 1.5f, 0f),
                end = Offset(size.width - 1.5f, size.height),
                strokeWidth = 2.5f
            )

            // Realistic clinging water droplets on dark frosted glass
            SIDEBAR_DROPLETS.forEach { d ->
                val pulse = ((sin(phase + d.phaseOffset) + 1f) * 0.5f)
                val driftY = sin(phase + d.phaseOffset) * d.driftAmplitudeDp.dp.toPx()
                drawRealisticWaterDrop(
                    center = Offset(size.width * d.xFraction, size.height * d.yFraction + driftY),
                    radiusPx = d.radiusDp.dp.toPx(),
                    elongation = d.elongation,
                    isDarkSurface = true,
                    shimmerPulse = pulse
                )
            }
        }
        content()
    }
}
