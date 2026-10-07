package com.example.mystore

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RepoInfo(val id: String, val name: String, val url: String)

data class AppItem(
    val repo: RepoInfo, val pkg: String, val name: String, val summary: String,
    val iconUrl: String?, val versionName: String, val size: Long,
    val apkUrl: String, val sha256: String?, val os: String = "android"
)

object Repos {
    val siteBase get() = Config.OWN_REPO_URL.removeSuffix("/repo")
    // مستودعك افتراضي ومفعّل دائمًا. الباقي موجود لكن غير مفعّل.
    val all = listOf(
        RepoInfo("own", Config.OWN_REPO_NAME, Config.OWN_REPO_URL),
        RepoInfo("fdroid", "F-Droid", "https://f-droid.org/repo"),
        RepoInfo("tor", "Tor Project", "https://fdroid.torproject.org/fdroid/repo"),
        RepoInfo("nethunter", "NetHunter Store", "https://store.nethunter.com/repo"),
    )
}

class RepoPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("repos", Context.MODE_PRIVATE)
    fun isEnabled(id: String) = id == "own" || p.getBoolean("en_$id", false)
    fun set(id: String, v: Boolean) = p.edit().putBoolean("en_$id", v).apply()
}

suspend fun fetchApps(repo: RepoInfo): List<AppItem> = withContext(Dispatchers.IO) {
    val base = repo.url.trimEnd('/')
    val c = (URL("$base/index-v1.json").openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 60000
    }
    val root = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    val packages = root.getJSONObject("packages")
    val apps = root.getJSONArray("apps")
    val out = ArrayList<AppItem>()
    for (i in 0 until apps.length()) {
        val a = apps.getJSONObject(i)
        val pkg = a.getString("packageName")
        val vs = packages.optJSONArray(pkg) ?: continue
        val suggested = a.optInt("suggestedVersionCode", -1)
        var best: JSONObject? = null
        for (j in 0 until vs.length()) {
            val v = vs.getJSONObject(j)
            if (v.optInt("versionCode") == suggested) { best = v; break }
            if (best == null || v.optInt("versionCode") > best.optInt("versionCode")) best = v
        }
        val v = best ?: continue
        val loc = a.optJSONObject("localized")
        val l = loc?.optJSONObject("ar") ?: loc?.optJSONObject("en-US")
            ?: loc?.keys()?.asSequence()?.firstOrNull()?.let { loc.optJSONObject(it) }
        val name = l?.optString("name")?.takeIf { it.isNotBlank() } ?: a.optString("name", pkg)
        val summary = l?.optString("summary")?.takeIf { it.isNotBlank() } ?: a.optString("summary")
        val icon = a.optString("icon").takeIf { it.isNotBlank() }
        out += AppItem(
            repo, pkg, name, summary, icon?.let { "$base/icons/$it" },
            v.optString("versionName"), v.optLong("size"),
            "$base/${v.getString("apkName")}",
            if (v.optString("hashType") == "sha256") v.optString("hash") else null
        )
    }
    out
}

// تطبيقات باقي الأنظمة من catalog/apps.json في موقعك
suspend fun fetchCatalog(siteBase: String, repo: RepoInfo): List<AppItem> = withContext(Dispatchers.IO) {
    val base = siteBase.trimEnd('/')
    val c = (URL("$base/catalog/apps.json").openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000
    }
    val apps = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("apps")
    val out = ArrayList<AppItem>()
    for (i in 0 until apps.length()) {
        val a = apps.getJSONObject(i)
        val id = a.getString("id")
        val ds = a.optJSONArray("downloads") ?: continue
        val icon = a.optString("icon").takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "$base/$it" }
        for (j in 0 until ds.length()) {
            val d = ds.getJSONObject(j)
            out += AppItem(
                repo, id, a.optString("name", id), a.optString("summary"), icon,
                d.optString("version"), d.optLong("size"), d.getString("url"),
                d.optString("sha256").takeIf { it.isNotBlank() }, d.getString("os")
            )
        }
    }
    out
}
