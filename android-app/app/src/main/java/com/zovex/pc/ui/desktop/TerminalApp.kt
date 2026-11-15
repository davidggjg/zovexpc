package com.zovex.pc.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zovex.pc.runtime.RuntimeLauncher
import kotlinx.coroutines.launch
import java.io.File

private val Ink = Color(0xFFCCCCCC)
private val Prompt = Color(0xFF16C60C)
private val Bad = Color(0xFFE74856)

/**
 * A terminal that runs each command inside the rootfs under PRoot.
 *
 * Commands run one at a time rather than through a persistent shell, so
 * `cd` does not carry over yet and nothing can be typed into a running
 * program. That is a real limitation and the prompt says so rather than
 * pretending to be an interactive shell.
 */
@Composable
fun TerminalApp(
    runtimeRoot: File? = null,
    revision: Long = 0L,
    initialCommand: String? = null,
) {
    val scope = rememberCoroutineScope()
    val installDir = remember(runtimeRoot) {
        runtimeRoot?.let { File(it, "windows-runtime") }
    }
    val launcher = remember(installDir, revision) { installDir?.let { RuntimeLauncher(it) } }
    val ready = launcher?.isInstalled == true

    var lines by remember {
        mutableStateOf(
            if (ready) {
                listOf(
                    "ZOVEX Terminal — commands run inside the Linux rootfs.",
                    "Each command is a fresh process: cd does not persist yet.",
                    "",
                )
            } else {
                listOf(
                    "The Windows runtime is not installed.",
                    "Open \"מנוע Windows\" from the Start menu to install it.",
                    "",
                )
            }
        )
    }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    fun run(commandLine: String) {
        val cmd = commandLine.trim()
        if (cmd.isEmpty() || launcher == null || busy) return
        lines = lines + "C:\\> $cmd"
        input = ""
        busy = true
        scope.launch {
            val result = launcher.runInProot(listOf("/bin/sh", "-lc", cmd))
            val body = result.combined.trimEnd()
            lines = lines +
                (if (body.isNotEmpty()) body.lines() else emptyList()) +
                buildString {
                    append("[exit ").append(result.exitCode)
                    result.killedBy?.let { append(" — ").append(it) }
                    append(", ").append(result.durationMs).append("ms]")
                } +
                ""
            busy = false
        }
    }

    LaunchedEffect(initialCommand, ready) {
        if (ready && !initialCommand.isNullOrBlank()) run(initialCommand)
    }
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
            .padding(12.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
        ) {
            lines.forEach { line ->
                Text(
                    text = line.ifEmpty { " " },
                    color = when {
                        line.startsWith("C:\\>") -> Prompt
                        line.startsWith("[exit 0") -> Color(0xFF6A6A6A)
                        line.startsWith("[exit") -> Bad
                        else -> Ink
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (busy) "..." else "C:\\>",
                color = Prompt,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                enabled = ready && !busy,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Prompt),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { run(input) }),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clickable(enabled = ready && !busy) { run(input) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("▶", color = if (ready && !busy) Prompt else Color(0xFF4A4A4A), fontSize = 13.sp)
            }
        }
    }
}
