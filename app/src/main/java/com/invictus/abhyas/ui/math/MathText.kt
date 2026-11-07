package com.invictus.abhyas.ui.math

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException

/**
 * The single place that renders question text. Plain text is shown with Compose `Text`; text that
 * contains LaTeX ($..$, $$..$$, \(..\), \[..\]) is rendered with offline KaTeX in a WebView when
 * the KaTeX files exist in `assets/katex/` (see assets/katex/README.md), otherwise it falls back
 * to plain text. Swap the renderer by changing only this file.
 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val katex = remember { KatexSupport.available(context) }
    if (katex && KatexSupport.hasMath(text)) {
        KatexView(text, modifier, style)
    } else {
        Text(text, modifier = modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

internal object KatexSupport {
    private val REQUIRED = listOf("katex.min.js", "katex.min.css", "auto-render.min.js")

    fun available(context: Context): Boolean = try {
        val files = context.assets.list("katex")?.toSet().orEmpty()
        REQUIRED.all { it in files }
    } catch (e: IOException) {
        false
    }

    fun hasMath(text: String): Boolean = '$' in text || "\\(" in text || "\\[" in text

    fun html(text: String, colorHex: String, sizePx: Float): String {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="katex.min.css"><script src="katex.min.js"></script><script src="auto-render.min.js"></script>
<style>html,body{margin:0;padding:0;background:transparent}body{color:$colorHex;font-size:${sizePx}px;line-height:1.45;font-family:sans-serif;word-wrap:break-word}</style>
</head><body><div id="c">$escaped</div><script>
renderMathInElement(document.getElementById('c'),{delimiters:[{left:'$$',right:'$$',display:true},{left:'$',right:'$',display:false},{left:'\\(',right:'\\)',display:false},{left:'\\[',right:'\\]',display:true}],throwOnError:false});
</script></body></html>"""
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KatexView(text: String, modifier: Modifier, style: TextStyle) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val hex = String.format("#%06X", 0xFFFFFF and color)
    val size = if (style.fontSize.isSpecified) style.fontSize.value else 16f
    val html = remember(text, hex, size) { KatexSupport.html(text, hex, size) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { web ->
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
            }
        },
    )
}
