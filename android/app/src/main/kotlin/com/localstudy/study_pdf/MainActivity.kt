package com.localstudy.study_pdf

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.security.MessageDigest

class MainActivity : FlutterActivity() {
    private val channelName = "com.localstudy.study_pdf/documents"
    private val requestOpenDocument = 9341
    private var pendingResult: MethodChannel.Result? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestOpenDocument) return
        val callback = pendingResult ?: return
        pendingResult = null
        if (resultCode != Activity.RESULT_OK) {
            callback.success(null)
            return
        }
        val uri = data?.data ?: run { callback.success(null); return }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = queryName(uri) ?: "documento.pdf"
            // Pdfium needs a path; this cache is replaceable while the URI remains authoritative.
            val cacheFile = File(cacheDir, "pdf/${sha256(uri.toString())}.pdf").apply { parentFile?.mkdirs() }
            contentResolver.openInputStream(uri)?.use { input -> cacheFile.outputStream().use(input::copyTo) }
                ?: error("No se pudo abrir el archivo")
            callback.success(mapOf("uri" to uri.toString(), "name" to name, "cachePath" to cacheFile.path))
        } catch (error: Exception) {
            callback.error("OPEN_DOCUMENT", error.message, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "pickPdf" -> {
                    if (pendingResult != null) result.error("BUSY", "Ya hay un selector abierto", null)
                    else {
                        pendingResult = result
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "application/pdf"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        }, requestOpenDocument)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun queryName(uri: Uri): String? = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
