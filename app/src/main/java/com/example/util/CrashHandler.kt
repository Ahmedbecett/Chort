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
 * content providers is captured.
 *
 * Because some devices/ROMs block starting the isolated `:crash` reporter
 * Activity from a dying process, the report is ALSO written to a copy in
 * the device's Downloads folder (no permission needed on API 29+) plus a
 * Toast pointing at it — the stack trace is therefore retrievable even
 * when the app can never launch. Any crash during Application startup is
 * routed here via [failFast], which catches [Throwable] (including [Error]).
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
        val appContext = try {
            context.applicationContext ?: context
        } catch (_: Exception) {
            context
        }
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(appContext, thread, throwable, defaultHandler)
        }
    }

    /**
     * Handle a fatal startup failure synchronously (called from guarded
     * Application code). Never returns.
     */
    fun failFast(context: Context, throwable: Throwable) {
        try {
            val appContext = try {
                context.applicationContext ?: context
            } catch (_: Exception) {
                context
            }
            handleCrash(appContext, Thread.currentThread(), throwable, Thread.getDefaultUncaughtExceptionHandler())
        } catch (_: Exception) {
        }
        try {
            Process.killProcess(Process.myPid())
        } catch (_: Exception) {
        }
        exitProcess(1)
    }

    private fun handleCrash(
        appContext: Context,
        thread: Thread,
        throwable: Throwable,
        defaultHandler: Thread.UncaughtExceptionHandler?,
    ) {
        var reporterStarted = false
        try {
            val report = buildReport(appContext, thread, throwable)
            try {
                File(appContext.filesDir, CRASH_FILE).writeText(report)
            } catch (_: Exception) {
            }
            saveCopyToDownloads(appContext, report)
            showSavedToast(appContext)
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
            try {
                Process.killProcess(Process.myPid())
            } catch (_: Exception) {
            }
            exitProcess(1)
        }
    }

    /**
     * Best-effort copy of the report into the shared Downloads folder so
     * the user can open/send it from any file manager even when the app
     * itself can never launch. No permission needed on API 29+.
     */
    private fun saveCopyToDownloads(context: Context, report: String) {
        try {
            if (Build.VERSION.SDK_INT < 29) return
            val stamp = try {
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            } catch (_: Exception) {
                "report"
            }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "rivo_crash_$stamp.txt")
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(report.toByteArray())
            }
        } catch (_: Exception) {
        }
    }

    private fun showSavedToast(context: Context) {
        try {
            val looper = try {
                android.os.Looper.getMainLooper()
            } catch (_: Exception) {
                null
            } ?: return
            try {
                @Suppress("DEPRECATION")
                android.os.Handler(looper).post {
                    try {
                        android.widget.Toast.makeText(
                            context,
                            "Rivo stopped \u2014 crash report saved to Downloads",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
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

    fun clearLastCrash(context: Context) {
        try {
            File(context.filesDir, CRASH_FILE).delete()
        } catch (_: Exception) {
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

    fun processName(context: Context): String {
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

    fun isMainProcess(context: Context): Boolean {
        return try {
            processName(context) == context.packageName
        } catch (_: Exception) {
            true
        }
    }
}
