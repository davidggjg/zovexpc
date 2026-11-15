package com.zovex.pc.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.CompositionLocalProvider

private val TitleBarHeight = 40.dp
private val MinWidth = 260.dp
private val MinHeight = 160.dp

/**
 * A draggable, resizable window.
 *
 * Touch sizing matters more than fidelity here: the title bar is 40dp rather
 * than Windows' ~32px, and the resize grip is a 36dp corner rather than a
 * 4px edge, because a finger cannot hit a 4px target.
 */
@Composable
fun WindowFrame(
    window: WindowState,
    desktop: DesktopState,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
) {
    if (window.minimized) return
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme

    val x = if (window.maximized) 0.dp else window.x
    val y = if (window.maximized) 0.dp else window.y
    val w = if (window.maximized) maxWidth else window.width
    val h = if (window.maximized) maxHeight else window.height

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width = w, height = h)
            .zIndex(window.zIndex)
            .shadow(16.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(10.dp))
            .pointerInput(window.id) {
                detectDragGestures(onDragStart = { desktop.focus(window) }) { _, _ -> }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Title bar: drag to move, double-tap area replaced by an explicit
            // maximise button since double-tap is unreliable on touch.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TitleBarHeight)
                    .background(scheme.surfaceVariant)
                    .pointerInput(window.id) {
                        detectDragGestures(
                            onDragStart = { desktop.focus(window) },
                        ) { change, drag ->
                            change.consume()
                            if (window.maximized) return@detectDragGestures
                            with(density) {
                                window.x = (window.x + drag.x.toDp())
                                    .coerceIn(-(window.width - 80.dp), maxWidth - 80.dp)
                                window.y = (window.y + drag.y.toDp())
                                    .coerceIn(0.dp, maxHeight - TitleBarHeight)
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = window.icon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(17.dp),
                )
                Text(
                    text = window.title,
                    modifier = Modifier.weight(1f),
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                CaptionButton("—", scheme.onSurfaceVariant) { desktop.toggleMinimize(window) }
                CaptionButton(if (window.maximized) "❐" else "▢", scheme.onSurfaceVariant) {
                    window.maximized = !window.maximized
                }
                CaptionButton("✕", scheme.onSurfaceVariant, Color(0xFFC42B1C)) {
                    desktop.close(window)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.background),
            ) {
                // Window contents are program surfaces: left-to-right, like the
                // Windows programs they stand in for.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    window.content()
                }
            }
        }

        if (!window.maximized) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .pointerInput(window.id) {
                        detectDragGestures(
                            onDragStart = { desktop.focus(window) },
                        ) { change, drag ->
                            change.consume()
                            with(density) {
                                window.width = (window.width + drag.x.toDp())
                                    .coerceIn(MinWidth, maxWidth)
                                window.height = (window.height + drag.y.toDp())
                                    .coerceIn(MinHeight, maxHeight)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("◢", color = scheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CaptionButton(
    glyph: String,
    tint: Color,
    hoverBackground: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(TitleBarHeight)
            .background(if (hoverBackground == Color.Transparent) Color.Transparent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = tint, fontSize = 13.sp)
    }
}
