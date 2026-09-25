package com.kuritomay.opentex

import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val DIRECTORY = "crash"
    private const val FILE_NAME = "latest.log"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun pendingCrash(context: Context): String? = runCatching {
        val file = File(File(context.filesDir, DIRECTORY), FILE_NAME)
        if (file.isFile) file.readText() else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(File(context.filesDir, DIRECTORY), FILE_NAME).delete() }
    }

    fun shareIntent(text: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "OpenTex - traza de cierre")
        putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            File(directory, FILE_NAME).writeText(buildString {
                appendLine("fecha: $stamp")
                appendLine("hilo: ${thread.name}")
                appendLine("android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine()
                appendLine(throwable.toString())
                throwable.stackTrace.forEach { appendLine("\tat $it") }
                var cause = throwable.cause
                while (cause != null) {
                    appendLine("Caused by: $cause")
                    cause.stackTrace.forEach { appendLine("\t  at $it") }
                    cause = cause.cause
                }
            })
        }
    }
}
