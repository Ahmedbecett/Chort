package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.example.ui.screens.debug.CrashReportActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Last-resort crash reporter. Installed as the first statement of
 * [com.example.ZevoraApplication.attachBaseContext] so any crash after the
 * content providers (Firebase/Facebook/lifecycle) is captured: the full
 * stack trace is written to internal storage and a dedicated reporter
 * Activity is launched in an isolated `:crash` process, where the user can
 * read, copy, or report it — or wipe stale app data and restart.
 */
object CrashHandler {

    const val CRASH_FILE = "rivo_crash_last.txt"

    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        if (!isMainProcess(context)) return
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            var reporterStarted = false
            try {
                val report = buildReport(appContext, thread, throwable)
                try {
                    File(appContext.filesDir, CRASH_FILE).writeText(report)
                } catch (_: Exception) {
                }
                val intent = Intent(appContext, CrashReportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                appContext.startActivity(intent)
                reporterStarted = true
            } catch (_: Exception) {
            } finally {
                // If our reporter could not start, fall back to the system dialog
                // so the crash is never completely silent.
                if (!reporterStarted) {
                    try {
                        defaultHandler?.uncaughtException(thread, throwable)
                    } catch (_: Exception) {
                    }
                }
                try {
                    Thread.sleep(400)
                } catch (_: Exception) {
                }
                Process.killProcess(Process.myPid())
                exitProcess(1)
            }
        }
    }

    fun readLastCrash(context: Context): String? {
        return try {
            val f = File(context.filesDir, CRASH_FILE)
            if (f.exists()) f.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        val stamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        } catch (_: Exception) {
            "-"
        }
        val pkg = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            "${pi.versionName} ($code)"
        } catch (_: Exception) {
            "?"
        }
        sb.appendLine("Rivo Chort crash report")
        sb.appendLine("time: $stamp")
        sb.appendLine("app: $pkg")
        sb.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine("thread: ${thread.name}")
        sb.appendLine("process: ${processName(context)}")
        sb.appendLine("---")
        var t: Throwable? = throwable
        var depth = 0
        while (t != null && depth < 5) {
            sb.appendLine("${t.javaClass.name}: ${t.message}")
            val frames = t.stackTrace.take(60)
            for (f in frames) sb.appendLine("  at $f")
            t = t.cause
            if (t != null) sb.appendLine("Caused by:")
            depth++
        }
        return sb.toString()
    }

    private fun processName(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                android.app.Application.getProcessName()
            } else {
                @Suppress("DEPRECATION")
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val pid = Process.myPid()
                am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: "?"
            }
        } catch (_: Exception) {
            "?"
        }
    }

    private fun isMainProcess(context: Context): Boolean {
        return try {
            processName(context) == context.packageName
        } catch (_: Exception) {
            true
        }
    }
}
