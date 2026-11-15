package com.zovex.pc.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/**
 * An abstract bloom in the spirit of a Windows 11 desktop, drawn rather than
 * shipped: the stock wallpaper is Microsoft artwork and is not ours to bundle.
 */
@Composable
fun BloomWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0B3C72),
                    Color(0xFF12528F),
                    Color(0xFF0A2C57),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )

        val centre = Offset(size.width * 0.5f, size.height * 0.52f)
        val reach = min(size.width, size.height)

        // Soft light behind the petals.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x4423A0FF), Color(0x00000000)),
                center = centre,
                radius = reach * 0.9f,
            ),
            radius = reach * 0.9f,
            center = centre,
        )

        // Overlapping translucent petals, each a little larger and dimmer.
        val petals = 6
        repeat(petals) { index ->
            val scale = 0.34f + index * 0.1f
            val alpha = 0.16f - index * 0.018f
            rotate(degrees = index * 31f, pivot = centre) {
                drawPath(
                    path = petalPath(centre, reach * scale),
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = alpha),
                            Color(0xFF7FD2FF).copy(alpha = alpha * 0.6f),
                        ),
                        start = Offset(centre.x - reach * scale, centre.y - reach * scale),
                        end = Offset(centre.x + reach * scale, centre.y + reach * scale),
                    ),
                )
            }
        }

        // Darken the lower edge so the taskbar reads against it.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x55000000)),
                startY = size.height * 0.72f,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height * 0.72f),
            size = Size(size.width, size.height * 0.28f),
        )
    }
}

/** A teardrop/petal shape curving out from [centre]. */
private fun petalPath(centre: Offset, radius: Float): Path = Path().apply {
    moveTo(centre.x, centre.y - radius)
    cubicTo(
        centre.x + radius * 0.95f, centre.y - radius * 0.72f,
        centre.x + radius * 0.82f, centre.y + radius * 0.62f,
        centre.x, centre.y + radius * 0.34f,
    )
    cubicTo(
        centre.x - radius * 0.55f, centre.y + radius * 0.5f,
        centre.x - radius * 0.86f, centre.y - radius * 0.5f,
        centre.x, centre.y - radius,
    )
    close()
}
