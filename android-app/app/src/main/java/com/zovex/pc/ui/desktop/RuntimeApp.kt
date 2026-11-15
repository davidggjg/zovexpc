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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zovex.pc.runtime.InstallProgress
import com.zovex.pc.runtime.InstallState
import com.zovex.pc.runtime.RuntimeCatalog
import com.zovex.pc.runtime.RuntimeInstaller
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Installs the Windows runtime and shows exactly what it is doing, including
 * when it fails. A 126 MB download that silently stalls is worse than one
 * that says why it stopped.
 */
@Composable
fun RuntimeApp(runtimeRoot: File, onInstalled: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val installer = remember(runtimeRoot) { RuntimeInstaller(runtimeRoot) }
    val asset = RuntimeCatalog.WINDOWS_RUNTIME

    var progress by remember {
        mutableStateOf(
            if (installer.isInstalled(asset)) {
                InstallProgress(asset, InstallState.READY, 1f, "מותקן")
            } else {
                InstallProgress(asset, InstallState.MISSING, null, "לא מותקן")
            }
        )
    }
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Windows Runtime", color = scheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Wine 9.0 (x86_64) · Box64 (aarch64) · Ubuntu 24.04 base",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "${asset.version} · ${"%.0f".format(asset.sizeBytes / 1024.0 / 1024.0)} MB",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(4.dp))

        val fraction = progress.fraction
        if (progress.state != InstallState.MISSING) {
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
            } else if (progress.state != InstallState.READY && progress.state != InstallState.FAILED) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
            }
        }

        Text(
            text = progress.messageHe,
            color = when (progress.state) {
                InstallState.FAILED -> scheme.error
                InstallState.READY -> Color(0xFF6CCB5F)
                else -> scheme.onSurfaceVariant
            },
            fontSize = 13.sp,
        )

        if (progress.state == InstallState.DOWNLOADING && fraction != null) {
            Text(
                "${"%.0f".format(fraction * 100)}%",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.weight(1f))

        if (progress.state != InstallState.READY) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (running) scheme.surfaceVariant else Color(0xFF0F6CBD))
                    .clickable(enabled = !running) {
                        running = true
                        scope.launch {
                            installer.install(asset).collectLatest { update ->
                                progress = update
                                if (update.state == InstallState.READY) onInstalled()
                                if (update.state == InstallState.READY ||
                                    update.state == InstallState.FAILED
                                ) {
                                    running = false
                                }
                            }
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) {
                Text(
                    if (progress.state == InstallState.FAILED) "נסה שוב" else "התקן",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF6CCB5F)))
                Spacer(Modifier.size(8.dp))
                Text("המנוע מותקן ומוכן", color = scheme.onSurface, fontSize = 13.sp)
            }
        }

        Text(
            "ההורדה מאומתת מול SHA-256. חבילה שאינה תואמת נמחקת ולא מותקנת.",
            color = scheme.outline,
            fontSize = 11.sp,
        )
    }
}
