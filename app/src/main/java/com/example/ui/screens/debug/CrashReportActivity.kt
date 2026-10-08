package com.example.ui.screens.debug

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.util.CrashHandler
import kotlin.system.exitProcess

/**
 * Isolated crash reporter (runs in the `:crash` process with a framework
 * theme, so it works even when the app's own Compose/theme stack is what
 * crashed). Shows the captured stack trace and offers copy / wipe-data /
 * close actions. Plain framework Views only — zero app dependencies.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = CrashHandler.readLastCrash(this)
            ?: "No crash report was captured.\n\nIf the app keeps stopping on launch, use \"Clear app data & restart\" below: it wipes stale local data (accounts stay safe on the server) and restarts fresh."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(32, 48, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Rivo Chort stopped"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        val hint = TextView(this).apply {
            text = "Send a screenshot of this page to support — it shows exactly what went wrong."
            setTextColor(Color.parseColor("#9AA0B4"))
            textSize = 14f
            setPadding(0, 12, 0, 12)
        }
        root.addView(hint)

        val trace = TextView(this).apply {
            text = report
            setTextColor(Color.parseColor("#D7DBE7"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply {
            addView(trace)
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        val copyBtn = Button(this).apply {
            text = "Copy"
            setOnClickListener {
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Rivo crash", report))
                    Toast.makeText(this@CrashReportActivity, "Copied", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
            }
        }
        val wipeBtn = Button(this).apply {
            text = "Clear data & restart"
            setOnClickListener {
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    @Suppress("DEPRECATION")
                    am.clearApplicationUserData()
                } catch (_: Exception) {
                    Toast.makeText(this@CrashReportActivity, "Could not clear data", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            setOnClickListener {
                finish()
                Process.killProcess(Process.myPid())
                exitProcess(0)
            }
        }
        buttons.addView(copyBtn)
        buttons.addView(wipeBtn)
        buttons.addView(closeBtn)
        root.addView(buttons)

        setContentView(root)
    }
}
