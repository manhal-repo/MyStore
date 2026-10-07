package com.example.mystore

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Installer {
    suspend fun download(ctx: Context, app: AppItem, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "apks").apply { mkdirs() }
            val f = File(dir, "${app.pkg}.apk")
            val c = URL(app.apkUrl).openConnection() as HttpURLConnection
            val total = c.contentLengthLong.takeIf { it > 0 } ?: app.size
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { i ->
                f.outputStream().use { o ->
                    val buf = ByteArray(32768); var done = 0L
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) onProgress(done / total.toFloat())
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (app.sha256 != null && !hex.equals(app.sha256, true)) {
                f.delete(); throw IllegalStateException("فشل التحقق من سلامة الملف (SHA-256)")
            }
            f
        }

    fun install(ctx: Context, file: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
