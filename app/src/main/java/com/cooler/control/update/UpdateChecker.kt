package com.cooler.control.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data class UpToDate(val latest: String) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/** 通过 GitHub Releases API 检查更新 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/mingzun09/CoolerControl/releases/latest"

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CoolerControl")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext UpdateResult.Failed("HTTP " + conn.responseCode)
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isBlank()) return@withContext UpdateResult.Failed("响应缺少 tag_name")

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name")
                    if (name.endsWith(".apk")) { apkUrl = a.optString("browser_download_url"); break }
                }
            }

            if (VersionUtil.isNewer(tag, currentVersion)) {
                UpdateResult.Available(
                    UpdateInfo(
                        version = tag,
                        notes = json.optString("body").trim(),
                        apkUrl = apkUrl,
                        pageUrl = json.optString("html_url"),
                    ),
                )
            } else {
                UpdateResult.UpToDate(tag)
            }
        } catch (t: Throwable) {
            UpdateResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}