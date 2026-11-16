package com.zovex.pc.ui.desktop

import android.annotation.SuppressLint
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zovex.pc.runtime.PrefixLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A browser inside the desktop, so programs can be fetched without leaving
 * ZOVEX. Downloads land on the virtual C: drive, where the Explorer and the
 * runtime can reach them.
 *
 * It renders with Android's engine rather than a Windows browser running
 * under Wine: that would need the X11 display layer, and would pay Box64's
 * translation cost on every page. This is the fast path to the thing that
 * actually matters — getting installers onto the drive.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserApp(layout: PrefixLayout, onDownloaded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var address by remember { mutableStateOf("https://www.google.com") }
    var editing by remember { mutableStateOf("https://www.google.com") }
    var progress by remember { mutableIntStateOf(100) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun download(url: String, suggested: String?) {
        scope.launch {
            val name = suggested?.takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
            Toast.makeText(context, "מוריד $name…", Toast.LENGTH_SHORT).show()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    layout.downloads.mkdirs()
                    val target = uniqueDownload(layout.downloads, name)
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 30_000
                        readTimeout = 60_000
                        // Some sites refuse an unidentified client outright.
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ZOVEX-PC")
                        CookieManager.getInstance().getCookie(url)
                            ?.let { setRequestProperty("Cookie", it) }
                    }
                    connection.inputStream.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    target.name
                }
            }
            result.fold(
                onSuccess = {
                    onDownloaded()
                    Toast.makeText(
                        context,
                        "$it נשמר ב-C:\\Users\\ZOVEX\\Downloads",
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onFailure = {
                    Toast.makeText(
                        context,
                        "ההורדה נכשלה: ${it.message ?: "שגיאת רשת"}",
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton(Icons.Rounded.ArrowBack, webView?.canGoBack() == true) { webView?.goBack() }
            NavButton(Icons.Rounded.ArrowForward, webView?.canGoForward() == true) { webView?.goForward() }
            NavButton(Icons.Rounded.Refresh, true) { webView?.reload() }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(scheme.background)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = editing,
                    onValueChange = { editing = it },
                    singleLine = true,
                    textStyle = TextStyle(color = scheme.onSurface, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color(0xFF4CC2FF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { address = normalize(editing) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        // Sites hand a phone the mobile layout; the desktop
                        // layout is what belongs in a desktop window, and it
                        // is what offers .exe downloads rather than app links.
                        userAgentString = DESKTOP_UA
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            // Keep non-web schemes from throwing the user out
                            // of the desktop into another Android app.
                            return !url.startsWith("http")
                        }

                        override fun doUpdateVisitedHistory(v: WebView?, url: String?, r: Boolean) {
                            url?.let { editing = it }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    setDownloadListener { url, _, contentDisposition, _, _ ->
                        download(url, fileNameFrom(contentDisposition))
                    }
                    webView = this
                    loadUrl(address)
                }
            },
            update = { view ->
                if (view.url != address) view.loadUrl(address)
            },
        )
    }
}

@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) scheme.onSurface else scheme.outline,
            modifier = Modifier.size(17.dp),
        )
    }
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun normalize(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
    }
}

private fun fileNameFrom(contentDisposition: String?): String? =
    contentDisposition
        ?.substringAfter("filename=", "")
        ?.trim('"', ' ', ';')
        ?.takeIf { it.isNotBlank() }

private fun uniqueDownload(dir: File, name: String): File {
    val safe = name.replace('/', '_').replace('\\', '_')
    var candidate = File(dir, safe)
    if (!candidate.exists()) return candidate
    val stem = safe.substringBeforeLast('.', safe)
    val ext = safe.substringAfterLast('.', "")
    var n = 1
    while (candidate.exists()) {
        candidate = File(dir, if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext")
        n++
    }
    return candidate
}
