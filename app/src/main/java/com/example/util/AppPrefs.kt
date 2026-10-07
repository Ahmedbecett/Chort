package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ZEVORA 3.1.0 — single persistent store for every user-facing setting.
 *
 * Everything here drives REAL behaviour somewhere in the app:
 * theme, language, playback, privacy gates, notification prefs, coins ledger,
 * video boosts, watch history and the block list. No decorative toggles.
 */
object AppPrefs {

    private const val FILE = "zevora_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _themeMode.value = prefs.getString("theme_mode", "dark") ?: "dark"
        _language.value = prefs.getString("app_language", "system") ?: "system"
        _autoplay.value = prefs.getBoolean("autoplay_feed", true)
        _feedMuted.value = prefs.getBoolean("feed_muted_default", false)
        _dataSaver.value = prefs.getBoolean("data_saver", false)
        _restrictedMode.value = prefs.getBoolean("restricted_mode", false)
        _privateAccount.value = prefs.getBoolean("private_account", false)
        _allowComments.value = prefs.getString("allow_comments", "everyone") ?: "everyone"
        _allowDownloads.value = prefs.getBoolean("allow_downloads", true)
        _allowReuse.value = prefs.getBoolean("allow_reuse", false)
        _coins.value = prefs.getInt("coins_balance", 0)
    }

    // ------------------------------------------------------------------ live state

    private val _themeMode = MutableStateFlow("dark") // "dark" | "black"
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow("system") // "system" | "ar" | "en" | "fr"
    val language: StateFlow<String> = _language.asStateFlow()

    private val _autoplay = MutableStateFlow(true)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _feedMuted = MutableStateFlow(false)
    val feedMuted: StateFlow<Boolean> = _feedMuted.asStateFlow()

    private val _dataSaver = MutableStateFlow(false)
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    private val _restrictedMode = MutableStateFlow(false)
    val restrictedMode: StateFlow<Boolean> = _restrictedMode.asStateFlow()

    private val _privateAccount = MutableStateFlow(false)
    val privateAccount: StateFlow<Boolean> = _privateAccount.asStateFlow()

    private val _allowComments = MutableStateFlow("everyone") // "everyone" | "followers" | "none"
    val allowComments: StateFlow<String> = _allowComments.asStateFlow()

    private val _allowDownloads = MutableStateFlow(true)
    val allowDownloads: StateFlow<Boolean> = _allowDownloads.asStateFlow()

    private val _allowReuse = MutableStateFlow(false)
    val allowReuse: StateFlow<Boolean> = _allowReuse.asStateFlow()

    private val _coins = MutableStateFlow(0)
    val coins: StateFlow<Int> = _coins.asStateFlow()

    // ------------------------------------------------------------------ setters

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun setLanguage(tag: String) {
        prefs.edit().putString("app_language", tag).apply()
        _language.value = tag
    }

    fun setAutoplay(enabled: Boolean) {
        prefs.edit().putBoolean("autoplay_feed", enabled).apply()
        _autoplay.value = enabled
    }

    fun setFeedMuted(muted: Boolean) {
        prefs.edit().putBoolean("feed_muted_default", muted).apply()
        _feedMuted.value = muted
    }

    fun setDataSaver(enabled: Boolean) {
        prefs.edit().putBoolean("data_saver", enabled).apply()
        _dataSaver.value = enabled
    }

    fun setRestrictedMode(enabled: Boolean) {
        prefs.edit().putBoolean("restricted_mode", enabled).apply()
        _restrictedMode.value = enabled
    }

    fun setPrivateAccount(enabled: Boolean) {
        prefs.edit().putBoolean("private_account", enabled).apply()
        _privateAccount.value = enabled
    }

    /** Server-reported privacy of OTHER users (my own flag is [privateAccount]). */
    fun setUserPrivate(userId: String, isPrivate: Boolean) {
        try {
            val map = JSONObject(prefs.getString("users_private", "{}") ?: "{}")
            if (isPrivate) map.put(userId, true) else map.remove(userId)
            // Cap the cache so it cannot grow without bound.
            val keys = mutableListOf<String>()
            val it = map.keys()
            while (it.hasNext()) keys.add(it.next())
            if (keys.size > 200) {
                keys.take(keys.size - 200).forEach { map.remove(it) }
            }
            prefs.edit().putString("users_private", map.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun isUserPrivate(userId: String): Boolean {
        return try {
            JSONObject(prefs.getString("users_private", "{}") ?: "{}").optBoolean(userId, false)
        } catch (_: Exception) {
            false
        }
    }

    fun setAllowComments(mode: String) {
        prefs.edit().putString("allow_comments", mode).apply()
        _allowComments.value = mode
    }

    fun setAllowDownloads(enabled: Boolean) {
        prefs.edit().putBoolean("allow_downloads", enabled).apply()
        _allowDownloads.value = enabled
    }

    fun setAllowReuse(enabled: Boolean) {
        prefs.edit().putBoolean("allow_reuse", enabled).apply()
        _allowReuse.value = enabled
    }

    fun setPushEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("push_enabled", enabled).apply()
    }

    fun isPushEnabled(): Boolean = prefs.getBoolean("push_enabled", true)

    fun setNotifTypeEnabled(type: String, enabled: Boolean) {
        prefs.edit().putBoolean("notif_$type", enabled).apply()
    }

    fun isNotifTypeEnabled(type: String): Boolean = prefs.getBoolean("notif_$type", true)

    fun setScreenTimeReminderMinutes(minutes: Int) {
        prefs.edit().putInt("screen_time_reminder_min", minutes).apply()
    }

    fun getScreenTimeReminderMinutes(): Int = prefs.getInt("screen_time_reminder_min", 60)

    // ------------------------------------------------------------------ coins (real local ledger)

    fun addCoins(delta: Int, reason: String) {
        if (delta == 0) return
        val next = (_coins.value + delta).coerceAtLeast(0)
        prefs.edit().putInt("coins_balance", next).apply()
        _coins.value = next
        appendCoinEvent(delta, reason)
    }

    /** Returns false when the balance is insufficient (no negative balances). */
    fun spendCoins(amount: Int, reason: String): Boolean {
        if (amount <= 0) return false
        if (_coins.value < amount) return false
        val next = _coins.value - amount
        prefs.edit().putInt("coins_balance", next).apply()
        _coins.value = next
        appendCoinEvent(-amount, reason)
        return true
    }

    data class CoinEvent(val timestamp: Long, val delta: Int, val reason: String)

    private fun appendCoinEvent(delta: Int, reason: String) {
        try {
            val arr = JSONArray(prefs.getString("coin_events", "[]") ?: "[]")
            val trimmed = JSONArray()
            val start = maxOf(0, arr.length() - 49)
            for (i in start until arr.length()) trimmed.put(arr.get(i))
            trimmed.put(JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("d", delta)
                put("r", reason)
            })
            prefs.edit().putString("coin_events", trimmed.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun getCoinEvents(): List<CoinEvent> {
        return try {
            val arr = JSONArray(prefs.getString("coin_events", "[]") ?: "[]")
            buildList {
                for (i in arr.length() - 1 downTo 0) {
                    val o = arr.getJSONObject(i)
                    add(CoinEvent(o.optLong("ts"), o.optInt("d"), o.optString("r")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Daily check-in: +25 coins once per calendar day. Returns false if already claimed. */
    /** Offline earn queue: entries are "amount|reason"; flushed when back online. */
    fun queuePendingEarn(amount: Int, reason: String) {
        val set = prefs.getStringSet("pending_earns", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add("$amount|${reason.take(40)}|${System.currentTimeMillis()}")
        while (set.size > 200) set.remove(set.first())
        prefs.edit().putStringSet("pending_earns", set).apply()
    }

    fun takePendingEarns(): List<Pair<Int, String>> {
        val set = prefs.getStringSet("pending_earns", emptySet()) ?: emptySet()
        prefs.edit().remove("pending_earns").apply()
        return set.mapNotNull { entry ->
            val parts = entry.split("|")
            val amount = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            amount to (parts.getOrNull(1) ?: "reward")
        }
    }

    fun pendingEarnCount(): Int {
        return prefs.getStringSet("pending_earns", emptySet())?.size ?: 0
    }

    fun claimDailyCheckIn(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString("checkin_last_date", "") == today) return false
        prefs.edit().putString("checkin_last_date", today).apply()
        addCoins(25, "daily_check_in")
        return true
    }

    fun canClaimDailyCheckIn(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return prefs.getString("checkin_last_date", "") != today
    }

    fun incrementWatchedTotal(): Long {
        val next = prefs.getLong("watched_total", 0L) + 1
        prefs.edit().putLong("watched_total", next).apply()
        return next
    }

    fun getWatchedTotal(): Long = prefs.getLong("watched_total", 0L)

    // ------------------------------------------------------------------ boosts (real local effect)

    data class Boost(val videoId: String, val expiresAt: Long)

    /** Active boosts only — expired entries are pruned lazily and honestly. */
    fun getBoosts(): List<Boost> {
        val now = System.currentTimeMillis()
        val raw = prefs.getStringSet("boosted_ids", emptySet()) ?: emptySet()
        val valid = mutableListOf<Boost>()
        val keep = mutableSetOf<String>()
        raw.forEach { entry ->
            val parts = entry.split(":")
            val id = parts[0]
            if (id.isBlank()) return@forEach
            val exp = parts.getOrNull(1)?.toLongOrNull() ?: Long.MAX_VALUE
            if (exp > now) {
                valid.add(Boost(id, exp))
                keep.add(entry)
            }
        }
        if (keep.size != raw.size) {
            try {
                prefs.edit().putStringSet("boosted_ids", keep).apply()
            } catch (_: Exception) {
            }
        }
        return valid.sortedBy { it.expiresAt }
    }

    fun isBoosted(videoId: String): Boolean = getBoosts().any { it.videoId == videoId }

    fun getBoostedIds(): Set<String> = getBoosts().map { it.videoId }.toSet()

    fun addBoost(videoId: String, days: Int = 7) {
        val set = prefs.getStringSet("boosted_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.removeAll { it.split(":")[0] == videoId }
        val expiry = System.currentTimeMillis() + days.coerceAtLeast(1) * 24L * 3600L * 1000L
        set.add("$videoId:$expiry")
        prefs.edit().putStringSet("boosted_ids", set).apply()
    }

    fun removeBoost(videoId: String) {
        val set = prefs.getStringSet("boosted_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.removeAll { it.split(":")[0] == videoId }
        prefs.edit().putStringSet("boosted_ids", set).apply()
    }

    // ------------------------------------------------------------------ watch history (real, local)

    data class WatchEntry(val videoId: String, val timestamp: Long, val caption: String, val creator: String)

    fun recordWatch(videoId: String, caption: String, creator: String) {
        try {
            val arr = JSONArray(prefs.getString("watch_history", "[]") ?: "[]")
            val fresh = JSONArray()
            fresh.put(JSONObject().apply {
                put("id", videoId)
                put("ts", System.currentTimeMillis())
                put("cap", caption.take(120))
                put("by", creator.take(60))
            })
            var added = 1
            for (i in 0 until arr.length()) {
                if (added >= 100) break
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("id") == videoId) continue
                fresh.put(o)
                added++
            }
            prefs.edit().putString("watch_history", fresh.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun getWatchHistory(): List<WatchEntry> {
        return try {
            val arr = JSONArray(prefs.getString("watch_history", "[]") ?: "[]")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(WatchEntry(o.optString("id"), o.optLong("ts"), o.optString("cap"), o.optString("by")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearWatchHistory() {
        prefs.edit().remove("watch_history").apply()
    }

    // ------------------------------------------------- hidden videos ("Not interested")

    fun getHiddenVideoIds(): Set<String> =
        prefs.getStringSet("hidden_videos", emptySet())?.toSet() ?: emptySet()

    fun hideVideo(videoId: String) {
        val set = getHiddenVideoIds().toMutableSet()
        if (set.size >= 500) return
        set.add(videoId)
        prefs.edit().putStringSet("hidden_videos", set).apply()
    }

    fun clearHiddenVideos() {
        prefs.edit().remove("hidden_videos").apply()
    }

    fun setCaptionSize(size: String) {
        prefs.edit().putString("caption_size", size).apply()
    }

    fun getCaptionSize(): String = prefs.getString("caption_size", "normal") ?: "normal"

    // ------------------------------------------------------------------ block list (real, enforced in feed)

    fun getBlockedIds(): Set<String> =
        prefs.getStringSet("blocked_ids", emptySet())?.toSet() ?: emptySet()

    fun isBlocked(userId: String): Boolean = getBlockedIds().contains(userId)

    fun toggleBlocked(userId: String): Boolean {
        val set = getBlockedIds().toMutableSet()
        val nowBlocked = if (set.contains(userId)) {
            set.remove(userId)
            false
        } else {
            set.add(userId)
            true
        }
        prefs.edit().putStringSet("blocked_ids", set).apply()
        val repo = try {
            com.example.ZevoraApplication.instance.repository
        } catch (_: Exception) {
            null
        }
        // Keep the Room/request table consistent with the enforced local list.
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repo?.toggleBlockUser(userId)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return nowBlocked
    }

    // ------------------------------------------------------------------ screen time (real, foreground-tracked)

    /** Second-precision foreground tracking (survives short sessions). */
    fun addForegroundTime(millis: Long) {
        if (millis < 5_000) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val key = "screen_sec_$today"
        prefs.edit().putLong(key, prefs.getLong(key, 0L) + millis / 1000).apply()
    }

    fun getTodayForegroundMinutes(): Long {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val seconds = prefs.getLong("screen_sec_$today", 0L) +
            prefs.getLong("screen_min_$today", 0L) * 60
        return seconds / 60
    }

    fun getWeekForegroundMinutes(): List<Pair<String, Long>> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale.US)
        val cal = java.util.Calendar.getInstance()
        return buildList {
            for (back in 6 downTo 0) {
                cal.time = Date()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -back)
                val day = fmt.format(cal.time)
                val seconds = prefs.getLong("screen_sec_$day", 0L) +
                    prefs.getLong("screen_min_$day", 0L) * 60
                add(dayFmt.format(cal.time) to seconds / 60)
            }
        }
    }

    // ------------------------------------------------------------------ family / mentions / misc

    fun setFamilyPin(pin: String) {
        prefs.edit().putString("family_pin", pin).apply()
    }

    fun getFamilyPin(): String = prefs.getString("family_pin", "") ?: ""

    fun isFamilyLocked(): Boolean = getFamilyPin().length >= 4

    fun setMentionMode(mode: String) {
        prefs.edit().putString("mention_mode", mode).apply()
        setNotifTypeEnabled("mention", mode != "none")
    }

    fun getMentionMode(): String = prefs.getString("mention_mode", "everyone") ?: "everyone"

    fun setAdsPersonalized(enabled: Boolean) {
        prefs.edit().putBoolean("ads_personalized", enabled).apply()
    }

    fun isAdsPersonalized(): Boolean = prefs.getBoolean("ads_personalized", true)

    fun setLiteMode(enabled: Boolean) {
        if (enabled) {
            prefs.edit()
                .putBoolean("lite_prev_autoplay", autoplay.value)
                .putBoolean("lite_prev_datasaver", _dataSaver.value)
                .putBoolean("lite_mode", true)
                .apply()
            setDataSaver(true)
            setAutoplay(false)
        } else {
            val prevAutoplay = prefs.getBoolean("lite_prev_autoplay", true)
            val prevSaver = prefs.getBoolean("lite_prev_datasaver", false)
            prefs.edit().putBoolean("lite_mode", false).apply()
            setDataSaver(prevSaver)
            setAutoplay(prevAutoplay)
        }
    }

    fun isLiteMode(): Boolean = prefs.getBoolean("lite_mode", false)

    fun setProfileCity(city: String, show: Boolean) {
        prefs.edit().putString("profile_city", city).putBoolean("profile_city_show", show).apply()
    }

    fun getProfileCity(): String = prefs.getString("profile_city", "") ?: ""

    fun showProfileCity(): Boolean = prefs.getBoolean("profile_city_show", false)

    data class LastAccount(val userId: String, val username: String)

    fun recordLastAccount(userId: String, username: String) {
        prefs.edit().putString("last_user_id", userId).putString("last_username", username).apply()
    }

    fun getLastAccount(): LastAccount? {
        val id = prefs.getString("last_user_id", "") ?: ""
        val name = prefs.getString("last_username", "") ?: ""
        return if (id.isBlank() || name.isBlank()) null else LastAccount(id, name)
    }

    // ------------------------------------------------- visibility & sharing prefs

    fun setFollowingVisibility(mode: String) {
        prefs.edit().putString("following_visibility", mode).apply()
    }

    fun getFollowingVisibility(): String =
        prefs.getString("following_visibility", "Everyone") ?: "Everyone"

    fun setLikedVisibility(mode: String) {
        prefs.edit().putString("liked_visibility", mode).apply()
    }

    fun getLikedVisibility(): String =
        prefs.getString("liked_visibility", "Only you") ?: "Only you"

    fun setDmMode(mode: String) {
        prefs.edit().putString("dm_mode", mode).apply()
    }

    fun getDmMode(): String = prefs.getString("dm_mode", "Everyone") ?: "Everyone"

    fun setShowProfileOnShare(enabled: Boolean) {
        prefs.edit().putBoolean("show_profile_on_share", enabled).apply()
    }

    fun showProfileOnShare(): Boolean = prefs.getBoolean("show_profile_on_share", true)

    // ------------------------------------------------- restricted-mode keyword filter

    private val RESTRICTED_KEYWORDS = setOf(
        "porn", "xxx", "onlyfans", "escort", "nude", "nudity",
        "sex tape", "sexvideo", "hentai", "playboy", "stripper"
    )

    /** True when a caption/tags pair trips the on-device restricted filter. */
    fun isRestrictedCaption(caption: String, tags: String): Boolean {
        val haystack = (caption + " " + tags).lowercase()
        return RESTRICTED_KEYWORDS.any { haystack.contains(it) }
    }

    // ------------------------------------------------------------------ locale helper (no extra deps)

    /** Effective BCP-47 tag, or null when following the system language. */
    fun effectiveLanguageTag(): String? = when (_language.value) {
        "ar" -> "ar"
        "en" -> "en"
        "fr" -> "fr"
        else -> null
    }

    /** Wraps [base] with the user-selected locale (call from attachBaseContext). */
    fun wrapLocale(base: Context): Context {
        val tag = try {
            effectiveLanguageTag()
        } catch (_: Exception) {
            null
        } ?: return base
        return try {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocale(locale)
            base.createConfigurationContext(config)
        } catch (_: Exception) {
            base
        }
    }
}
