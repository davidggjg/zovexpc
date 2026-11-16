package com.zovex.pc.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/**
 * A bloom of folded blue ribbons on a pale sky, in the spirit of a Windows 11
 * desktop. Drawn rather than shipped: Microsoft's wallpaper is their artwork
 * and is not ours to bundle, so this is an original approximation.
 */
@Composable
fun BloomWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        // Pale sky, brighter towards the upper left, as the original is.
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFDCEBFA),
                    Color(0xFFC3DCF4),
                    Color(0xFF9FC6EC),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )

        val centre = Offset(size.width * 0.52f, size.height * 0.54f)
        val reach = min(size.width, size.height)

        // Diffuse glow so the ribbons sit in light rather than on a flat field.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x33FFFFFF), Color(0x00FFFFFF)),
                center = Offset(size.width * 0.34f, size.height * 0.3f),
                radius = reach * 0.85f,
            ),
            radius = reach * 0.85f,
            center = Offset(size.width * 0.34f, size.height * 0.3f),
        )

        // Ribbons, outermost first so nearer folds overlap the ones behind.
        val folds = listOf(
            Fold(0.92f, -18f, Color(0xFF2F6FC4), Color(0xFF1B4E99)),
            Fold(0.80f, 26f, Color(0xFF3F84D8), Color(0xFF2561B4)),
            Fold(0.68f, -46f, Color(0xFF2E74CE), Color(0xFF17509F)),
            Fold(0.56f, 62f, Color(0xFF4A95E4), Color(0xFF2A6CC0)),
            Fold(0.44f, -78f, Color(0xFF2B6FCB), Color(0xFF144A97)),
            Fold(0.32f, 104f, Color(0xFF5AA6EE), Color(0xFF3178CC)),
            Fold(0.22f, -130f, Color(0xFF2A6CC8), Color(0xFF123F8A)),
        )

        folds.forEach { fold ->
            rotate(degrees = fold.rotation, pivot = centre) {
                drawRibbon(centre, reach * fold.scale, fold.light, fold.dark)
            }
        }

        // The taskbar reads against a slightly deeper edge.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x1A0A2A52)),
                startY = size.height * 0.80f,
                endY = size.height,
            ),
        )
    }
}

private data class Fold(
    val scale: Float,
    val rotation: Float,
    val light: Color,
    val dark: Color,
)

/**
 * One folded sheet: a wide curved lobe with a shaded inner edge, which is
 * what gives the bloom its sense of thickness.
 */
private fun DrawScope.drawRibbon(centre: Offset, radius: Float, light: Color, dark: Color) {
    val outer = Path().apply {
        moveTo(centre.x, centre.y - radius)
        cubicTo(
            centre.x + radius * 1.02f, centre.y - radius * 0.78f,
            centre.x + radius * 0.88f, centre.y + radius * 0.55f,
            centre.x - radius * 0.06f, centre.y + radius * 0.30f,
        )
        cubicTo(
            centre.x - radius * 0.62f, centre.y + radius * 0.14f,
            centre.x - radius * 0.74f, centre.y - radius * 0.62f,
            centre.x, centre.y - radius,
        )
        close()
    }

    drawPath(
        path = outer,
        brush = Brush.linearGradient(
            colors = listOf(light, dark),
            start = Offset(centre.x - radius * 0.5f, centre.y - radius),
            end = Offset(centre.x + radius * 0.6f, centre.y + radius * 0.4f),
        ),
    )

    // The underside of the fold, a shade darker and slightly inset.
    val inner = Path().apply {
        moveTo(centre.x, centre.y - radius * 0.96f)
        cubicTo(
            centre.x + radius * 0.52f, centre.y - radius * 0.70f,
            centre.x + radius * 0.46f, centre.y + radius * 0.18f,
            centre.x - radius * 0.04f, centre.y + radius * 0.24f,
        )
        cubicTo(
            centre.x - radius * 0.30f, centre.y + radius * 0.10f,
            centre.x - radius * 0.42f, centre.y - radius * 0.52f,
            centre.x, centre.y - radius * 0.96f,
        )
        close()
    }

    drawPath(
        path = inner,
        brush = Brush.linearGradient(
            colors = listOf(dark.copy(alpha = 0.85f), light.copy(alpha = 0.55f)),
            start = Offset(centre.x, centre.y - radius),
            end = Offset(centre.x, centre.y + radius * 0.3f),
        ),
    )

    // A thin highlight along the leading edge.
    drawPath(
        path = outer,
        brush = Brush.linearGradient(
            colors = listOf(Color(0x55FFFFFF), Color.Transparent),
            start = Offset(centre.x - radius * 0.3f, centre.y - radius),
            end = Offset(centre.x + radius * 0.2f, centre.y - radius * 0.2f),
        ),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.012f),
    )
}
