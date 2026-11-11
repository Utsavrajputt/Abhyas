package com.invictus.abhyas.data.github

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One row of the About screen's Developers list: a GitHub account and its commit count on the repo. */
data class GitHubContributor(
    val login: String,
    val avatarUrl: String?,
    val profileUrl: String,
    val contributions: Int,
)

/**
 * Fetches the repo's contributors from GitHub's public API (no token needed) for the
 * About screen. Same behaviour as xmd's repository, but on plain HttpURLConnection so
 * Abhyas needs no extra networking dependency: a fresh 72-hour disk cache is returned
 * without touching the network, a failed fetch falls back to a stale cache, and
 * [forceRefresh] (the Retry button) skips the freshness check.
 */
object GitHubContributorsRepository {

    private const val API_URL = "https://api.github.com/repos/Utsavrajputt/Abhyas/contributors"
    private const val PER_PAGE = 100
    private const val MAX_PAGES = 3
    private const val CACHE_TTL_MS = 72L * 60L * 60L * 1000L
    private const val CACHE_FILE_NAME = "github_contributors_abhyas.json"
    private const val TIMEOUT_MS = 8_000

    private class CacheEntry(val fetchedAtMs: Long, val items: List<GitHubContributor>)

    suspend fun contributors(
        context: Context,
        forceRefresh: Boolean = false,
    ): Result<List<GitHubContributor>> = withContext(Dispatchers.IO) {
        val file = File(context.applicationContext.cacheDir, CACHE_FILE_NAME)
        val cached = readCache(file)
        if (!forceRefresh && cached != null) {
            // A negative age means the device clock moved backwards: treat as stale.
            val age = System.currentTimeMillis() - cached.fetchedAtMs
            if (age in 0 until CACHE_TTL_MS) return@withContext Result.success(cached.items)
        }
        try {
            val fresh = fetchAll()
            writeCache(file, fresh)
            Result.success(fresh)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (cached != null) Result.success(cached.items) else Result.failure(error)
        }
    }

    private fun fetchPage(page: Int): JSONArray {
        val connection = URL("$API_URL?per_page=$PER_PAGE&page=$page").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Abhyas-Android")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub contributors request failed with HTTP $code")
            }
            return JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchAll(): List<GitHubContributor> {
        val all = mutableListOf<GitHubContributor>()
        for (page in 1..MAX_PAGES) {
            val array = fetchPage(page)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("type").equals("Bot", ignoreCase = true)) continue
                val login = obj.optString("login").trim()
                if (login.isEmpty()) continue
                all += GitHubContributor(
                    login = login,
                    avatarUrl = obj.optString("avatar_url").takeIf { it.startsWith("https://") },
                    profileUrl = obj.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                        ?: "https://github.com/$login",
                    contributions = obj.optInt("contributions", 0).coerceAtLeast(0),
                )
            }
            if (array.length() < PER_PAGE) break
        }
        return all
            .distinctBy { it.login.lowercase(Locale.ROOT) }
            .sortedWith(
                compareByDescending<GitHubContributor> { it.contributions }
                    .thenBy { it.login.lowercase(Locale.ROOT) },
            )
    }

    private fun readCache(file: File): CacheEntry? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val array = json.getJSONArray("items")
        val items = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            GitHubContributor(
                login = obj.getString("login"),
                avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotEmpty() },
                profileUrl = obj.getString("profileUrl"),
                contributions = obj.optInt("contributions", 0),
            )
        }
        CacheEntry(json.getLong("fetchedAt"), items)
    }.getOrNull()

    private fun writeCache(file: File, items: List<GitHubContributor>) {
        runCatching {
            val array = JSONArray()
            items.forEach { c ->
                array.put(
                    JSONObject()
                        .put("login", c.login)
                        .put("avatarUrl", c.avatarUrl ?: "")
                        .put("profileUrl", c.profileUrl)
                        .put("contributions", c.contributions),
                )
            }
            file.writeText(
                JSONObject()
                    .put("fetchedAt", System.currentTimeMillis())
                    .put("items", array)
                    .toString(),
            )
        }
    }
}

/**
 * Fetches a GitHub user's avatar for the Developers list: in-memory LRU plus a 72-hour
 * disk cache, plain HttpURLConnection + BitmapFactory. Returns null (never throws) on
 * failure so the generic person icon stays.
 */
object GithubAvatarLoader {

    private const val MAX_CACHE_ENTRIES = 40
    private const val TARGET_PX = 128
    private const val DISK_CACHE_MAX_AGE_MS = 72 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 4_000

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {}

    /** Blocking; call from a background thread/coroutine, never the main thread. */
    fun load(context: Context, githubUsername: String): Bitmap? {
        val username = githubUsername.trim().removePrefix("@")
        if (username.isEmpty()) return null
        cache.get(username)?.let { return it }

        val dir = File(context.applicationContext.cacheDir, "github_avatars").apply { mkdirs() }
        val diskFile = File(dir, username.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")
        if (diskFile.exists() && System.currentTimeMillis() - diskFile.lastModified() < DISK_CACHE_MAX_AGE_MS) {
            BitmapFactory.decodeFile(diskFile.path)?.let { bitmap ->
                cache.put(username, bitmap)
                return bitmap
            }
        }

        val bitmap = fetch("https://github.com/$username.png?size=$TARGET_PX") ?: return null
        cache.put(username, bitmap)
        runCatching { diskFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        return bitmap
    }

    private fun fetch(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                if (connection.responseCode !in 200..299) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
