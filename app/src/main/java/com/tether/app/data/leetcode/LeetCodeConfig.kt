package com.tether.app.data.leetcode

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Remote controls for the LeetCode integration (Firebase Remote Config, free on Spark):
 *  - leetcode_sync_enabled: kill switch, if LeetCode ever asks us to stop
 *  - leetcode_profile_query / leetcode_recent_query: replacement GraphQL queries.
 *    If LeetCode renames a field, a new query that aliases it back to the old
 *    name fixes every installed app within hours, with no APK release.
 */
object LeetCodeConfig {

    private const val KEY_SYNC_ENABLED = "leetcode_sync_enabled"

    fun init() {
        try {
            val rc = FirebaseRemoteConfig.getInstance()
            rc.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(6 * 60 * 60)
                    .build()
            )
            rc.fetchAndActivate()
        } catch (e: Exception) {
            android.util.Log.w("LeetCodeConfig", "Remote Config unavailable", e)
        }
    }

    /** Sync stays on unless the remote kill switch is explicitly set to false. */
    val syncEnabled: Boolean
        get() = try {
            val value = FirebaseRemoteConfig.getInstance().getValue(KEY_SYNC_ENABLED)
            value.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE || value.asBoolean()
        } catch (e: Exception) {
            true
        }

    /** A remotely supplied replacement for a bundled query ("profile" / "recent"), if any. */
    fun queryOverride(name: String): String? = try {
        FirebaseRemoteConfig.getInstance().getString("leetcode_${name}_query").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}
