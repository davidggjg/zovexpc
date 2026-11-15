package com.zovex.pc.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One open window on the ZOVEX desktop. */
class WindowState(
    val id: Long,
    val title: String,
    val icon: ImageVector,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    val content: @Composable () -> Unit,
) {
    var x by mutableStateOf(x)
    var y by mutableStateOf(y)
    var width by mutableStateOf(width)
    var height by mutableStateOf(height)
    var minimized by mutableStateOf(false)
    var maximized by mutableStateOf(false)
    var zIndex by mutableStateOf(0f)
}

/**
 * Holds the open windows and their stacking order.
 *
 * Deliberately a plain state holder rather than anything tied to Wine: the
 * shell has to keep working when the runtime is missing or has crashed.
 */
class DesktopState {
    val windows = mutableStateListOf<WindowState>()
    private var nextId = 1L
    private var topZ = 1f

    var startMenuOpen by mutableStateOf(false)

    fun open(
        title: String,
        icon: ImageVector,
        width: Dp = 560.dp,
        height: Dp = 380.dp,
        content: @Composable () -> Unit,
    ) {
        // Reuse an existing window of the same title instead of stacking
        // duplicates, the way a taskbar button does.
        val existing = windows.firstOrNull { it.title == title }
        if (existing != null) {
            existing.minimized = false
            focus(existing)
            return
        }
        val offset = (windows.size % 5) * 24
        val w = WindowState(
            id = nextId++,
            title = title,
            icon = icon,
            x = (40 + offset).dp,
            y = (40 + offset).dp,
            width = width,
            height = height,
            content = content,
        )
        windows.add(w)
        focus(w)
        startMenuOpen = false
    }

    fun focus(window: WindowState) {
        topZ += 1f
        window.zIndex = topZ
    }

    fun close(window: WindowState) {
        windows.remove(window)
    }

    fun toggleMinimize(window: WindowState) {
        window.minimized = !window.minimized
        if (!window.minimized) focus(window)
    }
}
