package com.invictus.abhyas.data.pyq

import java.net.URI
import java.util.Locale

/** A file link found on a web page. */
data class FoundLink(val title: String, val url: String, val type: String)

/**
 * Finds downloadable files (.pdf and .zip) in the HTML of a simple web page. Pure string work, so it
 * only sees links that are in the page source: pages that build their links with JavaScript, or sit
 * behind a login, give an empty list.
 */
object PageLinkScanner {
    private val ANCHOR = Regex("""<a\b[^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAGS = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")

    fun parse(html: String, pageUrl: String, max: Int = 200): List<FoundLink> {
        val base = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, FoundLink>()
        for (m in ANCHOR.findAll(html)) {
            val href = decode(m.groupValues[1]).trim()
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:", true) || href.startsWith("mailto:", true)) continue
            val resolved = runCatching { base.resolve(href.replace(" ", "%20")) }.getOrNull() ?: continue
            if (resolved.scheme?.lowercase(Locale.ROOT) != "https") continue
            val path = resolved.path?.lowercase(Locale.ROOT) ?: continue
            val type = when {
                path.endsWith(".pdf") -> PaperLink.TYPE_PDF
                path.endsWith(".zip") -> PaperLink.TYPE_PACK
                else -> continue
            }
            val url = resolved.toString()
            val text = decode(m.groupValues[2].replace(TAGS, " ")).replace(SPACES, " ").trim()
            val name = text.ifEmpty { resolved.path.substringAfterLast('/').substringBeforeLast('.') }.take(80)
            out.putIfAbsent(url, FoundLink(name, url, type))
            if (out.size >= max) break
        }
        return out.values.toList()
    }

    private fun decode(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
}
