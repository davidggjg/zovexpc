package com.zovex.pc.ui.desktop

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.zovex.pc.runtime.PrefixLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TaskbarHeight = 52.dp
private val TaskbarFill = Color(0xF21F1F1F)
private val FlyoutFill = Color(0xF72C2C2C)
private val Hairline = Color(0x1FFFFFFF)

data class AppEntry(
    val title: String,
    val icon: ImageVector,
    val labelHe: String,
    val content: @Composable () -> Unit,
)

@Composable
fun DesktopScreen() {
    val context = LocalContext.current
    val desktop = remember { DesktopState() }
    val scope = rememberCoroutineScope()

    val layout = remember { PrefixLayout.default(context.filesDir).also { it.create() } }
    // Bumped after an import so the Explorer re-reads the folder.
    var contentRevision by remember { mutableLongStateOf(0L) }

    // Brings a file in from Android storage through the system picker, so no
    // broad storage permission is needed: the user grants one file at a time.
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var copied = 0
            withContext(Dispatchers.IO) {
                layout.downloads.mkdirs()
                for (uri in uris) {
                    val name = displayName(context, uri) ?: "imported-${System.currentTimeMillis()}"
                    val target = uniqueFile(layout.downloads, name)
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        } ?: error("no stream")
                        copied++
                    }
                }
            }
            contentRevision++
            Toast.makeText(
                context,
                if (copied == uris.size) "הועתקו $copied קבצים אל C:\\Users\\ZOVEX\\Downloads"
                else "הועתקו $copied מתוך ${uris.size} קבצים",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val runtimeRoot = remember { File(context.filesDir, "runtime").also { it.mkdirs() } }
    var runtimeRevision by remember { mutableLongStateOf(0L) }
    // Set when a program is double-clicked in the Explorer; opens a Terminal
    // already running it, which is how a desktop is expected to behave.
    var pendingRun by remember { mutableStateOf<File?>(null) }

    // What is actually on the drive, newest first — the Recommended list
    // shows real files rather than invented entries.
    val recentFiles = remember(contentRevision) {
        layout.downloads.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    val apps = remember(layout) {
        listOf(
            AppEntry("Windows Runtime", Icons.Rounded.Memory, "מנוע Windows") {
                RuntimeApp(runtimeRoot) { runtimeRevision++ }
            },
            AppEntry("Browser", Icons.Rounded.Language, "דפדפן") {
                BrowserApp(layout) { contentRevision++ }
            },
            AppEntry("File Explorer", Icons.Rounded.Folder, "סייר הקבצים") {
                FileExplorerApp(layout, contentRevision) { file ->
                    pendingRun = file
                }
            },
            AppEntry("Terminal", Icons.Rounded.Terminal, "טרמינל") {
                TerminalApp(runtimeRoot, runtimeRevision)
            },
            AppEntry("System", Icons.Rounded.Info, "מידע מערכת") { SystemInfoApp() },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val workHeight = maxHeight - TaskbarHeight
        val workWidth = maxWidth
        desktop.workWidth = workWidth
        desktop.workHeight = workHeight

        BloomWallpaper(
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { desktop.startMenuOpen = false }
        )

        Column(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp).width(92.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            apps.forEach { app ->
                DesktopIcon(app.icon, app.labelHe) {
                    desktop.open(
                        app.title,
                        app.icon,
                        width = if (app.title == "Browser") 900.dp else 560.dp,
                        height = if (app.title == "Browser") 620.dp else 380.dp,
                        content = app.content,
                    )
                }
            }
            DesktopIcon(Icons.Rounded.Upload, "ייבוא מהטלפון") {
                importer.launch(arrayOf("*/*"))
            }
        }

        Box(Modifier.fillMaxWidth().height(workHeight)) {
            desktop.windows.forEach { window ->
                WindowFrame(window, desktop, workWidth, workHeight)
            }
        }

        if (desktop.startMenuOpen) {
            StartMenu(
                apps = apps,
                maxHeight = workHeight,
                recent = recentFiles,
                onLaunch = { desktop.open(it.title, it.icon, content = it.content) },
                onImport = { importer.launch(arrayOf("*/*")) },
                onOpenRecent = { file ->
                    desktop.startMenuOpen = false
                    pendingRun = file
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TaskbarHeight + 10.dp),
            )
        }

        pendingRun?.let { file ->
            val wineCmd = wineCommandFor(file)
            desktop.open(file.name, Icons.Rounded.Terminal) {
                TerminalApp(runtimeRoot, runtimeRevision, initialCommand = wineCmd)
            }
            pendingRun = null
        }

        Taskbar(desktop, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DesktopIcon(icon: ImageVector, label: String, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp)
            .width(92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color(0xFF0E4D92),
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(5.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                // The wallpaper is pale, so the label carries its own
                // shadow the way desktop icons do.
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xCC00213F),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun Taskbar(desktop: DesktopState, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(20_000)
        }
    }

    Box(modifier.fillMaxWidth().height(TaskbarHeight).background(TaskbarFill)) {
        // Centred cluster, the way Windows 11 arranges it.
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartButton(desktop.startMenuOpen) { desktop.startMenuOpen = !desktop.startMenuOpen }
            TaskbarIcon(Icons.Rounded.Search, "חיפוש") { desktop.startMenuOpen = true }
            desktop.windows.forEach { window ->
                TaskbarIcon(window.icon, window.title, active = !window.minimized) {
                    desktop.toggleMinimize(window)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White, fontSize = 12.sp)
            Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now), color = Color(0xFFD0D0D0), fontSize = 10.sp)
        }
    }
}

/** Four panes, drawn rather than using the Windows logo. */
@Composable
private fun StartButton(open: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(46.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (open) Color(0x26FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(21.dp)) {
            val gap = size.width * 0.14f
            val pane = (size.width - gap) / 2f
            val blue = Color(0xFF4CC2FF)
            listOf(
                Offset(0f, 0f),
                Offset(pane + gap, 0f),
                Offset(0f, pane + gap),
                Offset(pane + gap, pane + gap),
            ).forEach { at ->
                drawRoundRect(
                    color = blue,
                    topLeft = at,
                    size = Size(pane, pane),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pane * 0.16f),
                )
            }
        }
        if (open) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 16.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CC2FF))
            )
        }
    }
}

@Composable
private fun TaskbarIcon(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            // 46dp target: Windows uses far less, which no finger can hit.
            .size(46.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) Color(0x1FFFFFFF) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 16.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CC2FF))
            )
        }
    }
}

@Composable
private fun StartMenu(
    apps: List<AppEntry>,
    maxHeight: Dp,
    recent: List<File>,
    onLaunch: (AppEntry) -> Unit,
    onImport: () -> Unit,
    onOpenRecent: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val shown = remember(query) {
        if (query.isBlank()) apps
        else apps.filter { it.labelHe.contains(query) || it.title.contains(query, true) }
    }
    val searching = query.isNotBlank()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .width(420.dp)
                .height(minOf(maxHeight - 20.dp, 520.dp))
                .shadow(32.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(FlyoutFill)
                .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color(0xFF9A9A9A), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("הקלד כאן כדי לחפש", color = Color(0xFF9A9A9A), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFF4CC2FF)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (searching) "תוצאות" else if (showAll) "כל האפליקציות" else "מוצמד",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (!searching) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x14FFFFFF))
                            .clickable { showAll = !showAll }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (showAll) "חזרה" else "כל האפליקציות",
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (showAll) "‹" else "›", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 230.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown.size) { index ->
                    val app = shown[index]
                    PinnedTile(app.icon, app.labelHe) { onLaunch(app) }
                }
                if (!searching) {
                    item { PinnedTile(Icons.Rounded.Upload, "ייבוא מהטלפון", onImport) }
                }
            }

            // Recommended: what is actually on the drive, newest first, the
            // way Windows lists recent files rather than inventing entries.
            if (!searching && recent.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("מומלץ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    recent.take(4).forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenRecent(file) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Description,
                                null,
                                tint = Color(0xFF9FC6EC),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                                Text(
                                    "%.1f MB".format(file.length() / 1024.0 / 1024.0),
                                    color = Color(0xFF9A9A9A),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF4CC2FF)),
                    contentAlignment = Alignment.Center,
                ) { Text("Z", color = Color(0xFF00283A), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Text("ZOVEX", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Power, "כיבוי", tint = Color(0xFFD0D0D0), modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PinnedTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFFE8F3FF), modifier = Modifier.size(27.dp))
        Spacer(Modifier.height(7.dp))
        Text(label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

/** Never silently overwrite a file already on the drive. */
private fun uniqueFile(dir: File, name: String): File {
    var candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    val stem = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "")
    var n = 1
    while (candidate.exists()) {
        val next = if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext"
        candidate = File(dir, next)
        n++
    }
    return candidate
}

/**
 * Wine is an x86_64 binary, so it runs under Box64, and both live inside the
 * rootfs view that PRoot sets up at /zovex.
 */
private fun wineCommandFor(file: File): String {
    val quoted = file.absolutePath.replace("'", "'\\''")
    return "/zovex/box64/bin/box64 /zovex/wine/bin/wine64 '" + quoted + "'"
}
