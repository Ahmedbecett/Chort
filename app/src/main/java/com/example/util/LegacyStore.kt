package com.example.util

import android.content.Context
import java.io.File

/**
 * One-time migration of legacy SharedPreferences stores to their current
 * names (TokPulse/Zevora era -> Rivo). Old installs keep working: every
 * entry is copied to the new store, then the legacy file is deleted so no
 * stale remnant survives on-device. Best-effort — any failure leaves the
 * fresh store in place and startup continues.
 */
object LegacyStore {

    fun migratePrefs(context: Context, oldName: String, newName: String) {
        if (oldName == newName) return
        try {
            val appCtx = try {
                context.applicationContext ?: context
            } catch (_: Exception) {
                context
            }
            val dir = File(appCtx.applicationInfo.dataDir, "shared_prefs")
            val oldFile = File(dir, "$oldName.xml")
            val newFile = File(dir, "$newName.xml")
            if (!oldFile.exists()) return
            if (!newFile.exists()) {
                val all: Map<String, *> = try {
                    appCtx.getSharedPreferences(oldName, Context.MODE_PRIVATE).all
                } catch (_: Exception) {
                    null
                } ?: emptyMap()
                if (all.isNotEmpty()) {
                    val ed = appCtx.getSharedPreferences(newName, Context.MODE_PRIVATE).edit()
                    for ((k, v) in all) {
                        try {
                            when (v) {
                                is String -> ed.putString(k, v)
                                is Int -> ed.putInt(k, v)
                                is Long -> ed.putLong(k, v)
                                is Float -> ed.putFloat(k, v)
                                is Boolean -> ed.putBoolean(k, v)
                                is Set<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    ed.putStringSet(k, (v as? Set<String>) ?: emptySet())
                                }
                                else -> {}
                            }
                        } catch (_: Exception) {
                        }
                    }
                    try {
                        ed.commit()
                    } catch (_: Exception) {
                    }
                }
            }
            try {
                oldFile.delete()
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }
}
