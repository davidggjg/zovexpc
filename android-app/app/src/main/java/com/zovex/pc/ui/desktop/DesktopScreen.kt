package com.zovex.pc.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zovex.pc.runtime.PrefixLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TaskbarHeight = 56.dp

private data class AppEntry(
    val title: String,
    val icon: String,
    val labelHe: String,
    val content: @Composable () -> Unit,
)

@Composable
fun DesktopScreen() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val desktop = remember { DesktopState() }

    val layout = remember {
        PrefixLayout.default(context.filesDir).also { it.create() }
    }

    val apps = remember(layout) {
        listOf(
            AppEntry("File Explorer", "📁", "סייר הקבצים") { FileExplorerApp(layout) },
            AppEntry("Terminal", "▣", "טרמינל") { TerminalApp() },
            AppEntry("System", "⚙", "מידע מערכת") { SystemInfoApp() },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val workAreaHeight = maxHeight - TaskbarHeight
        val workAreaWidth = maxWidth

        // Wallpaper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF04203F), Color(0xFF0A3E6B), Color(0xFF021226)),
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) { desktop.startMenuOpen = false },
        )

        // Desktop icons
        Column(
            modifier = Modifier
                .padding(start = 12.dp, top = 12.dp)
                .width(96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            apps.forEach { app ->
                DesktopIcon(app) { desktop.open(app.title, app.icon, content = app.content) }
            }
        }

        // Open windows
        Box(Modifier.fillMaxWidth().height(workAreaHeight)) {
            desktop.windows.forEach { window ->
                WindowFrame(window, desktop, workAreaWidth, workAreaHeight)
            }
        }

        if (desktop.startMenuOpen) {
            StartMenu(
                apps = apps,
                maxHeight = workAreaHeight,
                onLaunch = { app -> desktop.open(app.title, app.icon, content = app.content) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TaskbarHeight + 8.dp),
            )
        }

        Taskbar(
            desktop = desktop,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DesktopIcon(app: AppEntry, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            // A desktop icon is a tiny target on a phone, so the whole 88dp
            // cell is tappable rather than just the glyph.
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp)
            .width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(app.icon, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.labelHe,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun Taskbar(desktop: DesktopState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(Date()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TaskbarHeight)
            .background(Color(0xE6202020)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))

        // Clock sits on the leading edge, which in an RTL shell is the right.
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                color = Color.White,
                fontSize = 13.sp,
            )
            Text(
                SimpleDateFormat("dd/MM", Locale.getDefault()).format(now),
                color = Color(0xFFC5C5C5),
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        TaskbarButton("⊞", highlighted = desktop.startMenuOpen) {
            desktop.startMenuOpen = !desktop.startMenuOpen
            now = Date()
        }
        TaskbarButton("🔍") {
            desktop.startMenuOpen = true
            now = Date()
        }

        // One button per open window, like a real taskbar.
        desktop.windows.forEach { window ->
            TaskbarButton(window.icon, highlighted = !window.minimized) {
                desktop.toggleMinimize(window)
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TaskbarButton(glyph: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            // 48dp is the smallest comfortable finger target; Windows uses far
            // less, and copying that size would make the taskbar unusable.
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) Color(0x33FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 19.sp, color = Color.White)
    }
}

@Composable
private fun StartMenu(
    apps: List<AppEntry>,
    maxHeight: androidx.compose.ui.unit.Dp,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val shown = remember(query) {
        if (query.isBlank()) apps
        else apps.filter { it.labelHe.contains(query) || it.title.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .width(360.dp)
            .height(minOf(maxHeight - 24.dp, 420.dp))
            .shadow(24.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF22B2B2B))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        // Search box. Typing filters the list below as you go.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1AFFFFFF))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text("חיפוש תוכנות וקבצים", color = Color(0xFF9A9A9A), fontSize = 13.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    textDirection = TextDirection.Content,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF4CC2FF)),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(if (query.isBlank()) "מותקן" else "תוצאות", color = Color(0xFFC5C5C5), fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown.size) { index ->
                val app = shown[index]
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLaunch(app) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(app.icon, fontSize = 26.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(app.labelHe, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ZOVEX", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("⏻", color = Color(0xFFC5C5C5), fontSize = 16.sp)
        }
    }
}
