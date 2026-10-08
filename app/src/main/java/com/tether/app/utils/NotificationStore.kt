package com.tether.app.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TetherNotification(
    val message: String,
    val timestamp: String,
    val timeMillis: Long
)

object NotificationStore {

    private const val PREFS_NAME = "tether_notifications"
    private const val KEY_DATE = "notif_date"
    private const val KEY_ITEMS = "notif_items"
    private const val KEY_UNREAD = "notif_unread"
    private const val KEY_SEEN = "notif_seen_ids"

    private val _unread = MutableStateFlow(false)
    private var unreadLoaded = false

    private fun todayKey(): String = DateKeys.today()

    private fun timeLabel(timeMillis: Long): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timeMillis))

    /** Observable unread state for the bell dot. */
    fun unreadFlow(context: Context): StateFlow<Boolean> {
        if (!unreadLoaded) {
            unreadLoaded = true
            _unread.value = hasUnread(context)
        }
        return _unread.asStateFlow()
    }

    /**
     * Adds an event to today's activity list (newest first).
     * [id] makes it idempotent: the watcher replays today's events on every app
     * start, so things that happened while the app was closed still show up,
     * exactly once.
     */
    fun addNotification(
        context: Context,
        message: String,
        id: String? = null,
        timeMillis: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDate = prefs.getString(KEY_DATE, "")
        val today = todayKey()
        val sameDay = storedDate == today

        val seen = if (sameDay) prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty() else emptySet()
        if (id != null && id in seen) return

        val existing = JSONArray(if (sameDay) prefs.getString(KEY_ITEMS, "[]") ?: "[]" else "[]")
        val items = (0 until existing.length()).map { existing.getJSONObject(it) }.toMutableList()
        items.add(JSONObject().apply {
            put("message", message)
            put("timestamp", timeLabel(timeMillis))
            put("timeMillis", timeMillis)
        })
        items.sortByDescending { it.optLong("timeMillis") }

        prefs.edit()
            .putString(KEY_DATE, today)
            .putString(KEY_ITEMS, JSONArray(items).toString())
            .putStringSet(KEY_SEEN, if (id != null) seen + id else seen)
            .putBoolean(KEY_UNREAD, true)
            .apply()
        _unread.value = true
    }

    fun getNotifications(context: Context): List<TetherNotification> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDate = prefs.getString(KEY_DATE, "")
        if (storedDate != todayKey()) return emptyList()

        val json = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            TetherNotification(
                message = obj.getString("message"),
                timestamp = obj.getString("timestamp"),
                timeMillis = obj.getLong("timeMillis")
            )
        }
    }

    fun hasUnread(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDate = prefs.getString(KEY_DATE, "")
        if (storedDate != todayKey()) return false
        return prefs.getBoolean(KEY_UNREAD, false)
    }

    fun markRead(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_UNREAD, false).apply()
        _unread.value = false
    }
}
