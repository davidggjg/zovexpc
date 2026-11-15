package com.zovex.pc.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zovex.pc.core.DeviceCapabilityScanner
import com.zovex.pc.core.NativeBridge
import com.zovex.pc.core.Support
import com.zovex.pc.runtime.PrefixLayout
import java.io.File

/**
 * A file browser over the real virtual drive. It shows what is actually on
 * disk, so an empty C:\ looks empty rather than being faked with sample files.
 */
@Composable
fun FileExplorerApp(layout: PrefixLayout) {
    var current by remember { mutableStateOf(layout.driveC) }
    val scheme = MaterialTheme.colorScheme

    val entries = remember(current.path, current.lastModified()) {
        current.listFiles()
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }
    val atRoot = current.canonicalPath == layout.driveC.canonicalPath

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = !atRoot) { current = current.parentFile ?: current },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = if (atRoot) scheme.outline else scheme.onSurface, fontSize = 17.sp)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = windowsPath(layout, current),
                color = scheme.onSurface,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This folder is empty", color = scheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.absolutePath }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = file.isDirectory) { if (file.isDirectory) current = file }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (file.isDirectory) "📁" else "📄", fontSize = 16.sp)
                        Spacer(Modifier.size(10.dp))
                        Text(file.name, color = scheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (file.isFile) {
                            Text(humanSize(file.length()), color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun windowsPath(layout: PrefixLayout, file: File): String {
    val rel = file.canonicalPath.removePrefix(layout.driveC.canonicalPath).trim('/')
    return if (rel.isEmpty()) "C:\\" else "C:\\" + rel.replace('/', '\\')
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1024 -> "%.0f KB".format(bytes.toDouble() / 1024)
    else -> "$bytes B"
}

/**
 * The terminal window. It says plainly that no engine is installed rather
 * than imitating a shell that cannot run anything.
 */
@Composable
fun TerminalApp() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
            .padding(14.dp),
    ) {
        Text(
            text = "ZOVEX Terminal",
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "The Windows runtime is not installed yet.\n" +
                "cmd.exe needs Wine and Box64, which arrive in stage 2.\n\n" +
                "Nothing here is simulated: once the engine is in place,\n" +
                "this window runs the real cmd.exe.",
            color = Color(0xFF9A9A9A),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(14.dp))
        Row {
            Text("C:\\> ", color = Color(0xFF16C60C), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Text("_", color = Color(0xFFCCCCCC), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "stage 1 · engine not installed",
            color = scheme.outline,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** The device report from stage 1, now living in a window. */
@Composable
fun SystemInfoApp() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val profile = remember { DeviceCapabilityScanner.scan(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("System", color = scheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(profile.socModel, color = scheme.onSurfaceVariant, fontSize = 13.sp)
        Text("${profile.abi} · ${NativeBridge.version()}", color = scheme.onSurfaceVariant, fontSize = 13.sp)

        profile.capabilities.forEach { capability ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .background(
                            when (capability.support) {
                                Support.YES -> Color(0xFF6CCB5F)
                                Support.PARTIAL -> Color(0xFFFCE100)
                                Support.NO -> Color(0xFFFF99A4)
                            },
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(capability.titleHe, color = scheme.onSurface, fontSize = 14.sp)
                    Text(capability.noteHe, color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}
