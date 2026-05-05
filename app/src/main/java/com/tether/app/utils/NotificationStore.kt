package com.tether.app.utils

import android.content.Context
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

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun timeLabel(): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

    fun addNotification(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDate = prefs.getString(KEY_DATE, "")
        val today = todayKey()

        // Reset if new day
        val existingJson = if (storedDate == today) {
            prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        } else {
            "[]"
        }

        val array = JSONArray(existingJson)
        val obj = JSONObject().apply {
            put("message", message)
            put("timestamp", timeLabel())
            put("timeMillis", System.currentTimeMillis())
        }
        // Insert at index 0 (newest first)
        val newArray = JSONArray()
        newArray.put(obj)
        for (i in 0 until array.length()) newArray.put(array.get(i))

        prefs.edit()
            .putString(KEY_DATE, today)
            .putString(KEY_ITEMS, newArray.toString())
            .putBoolean(KEY_UNREAD, true)
            .apply()
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
    }
}
