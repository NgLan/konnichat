package com.example.konnichat.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Class quản lý lưu trữ Key-Value
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "konnichat_prefs"
        private const val KEY_USER_ID = "USER_ID"
        private const val KEY_USER_NAME = "USER_NAME"
        private const val KEY_USER_EMAIL = "USER_EMAIL"
        private const val KEY_SAVED_EMAIL = "SAVED_EMAIL"
        private const val KEY_SAVED_PASS = "SAVED_PASS"

        // Key mẫu cho Mute (prefix)
        private const val PREFIX_MUTE_GROUP = "MUTE_GROUP_"
        private const val PREFIX_MUTE_USER = "MUTE_NOTIFY_"
    }

    // --- User Info ---
    fun saveLoginSession(id: Int, name: String, email: String) {
        prefs.edit().apply {
            putInt(KEY_USER_ID, id)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            apply()
        }
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    // --- Auto Login ---
    fun saveCredentials(email: String, passHash: String) {
        prefs.edit().apply {
            putString(KEY_SAVED_EMAIL, email)
            putString(KEY_SAVED_PASS, passHash)
            apply()
        }
    }

    fun getSavedEmail(): String? = prefs.getString(KEY_SAVED_EMAIL, null)
    fun getSavedPass(): String? = prefs.getString(KEY_SAVED_PASS, null)

    // --- Settings (Mute) ---
    fun isGroupMuted(groupId: Int): Boolean {
        return prefs.getBoolean("$PREFIX_MUTE_GROUP$groupId", false)
    }

    fun setGroupMute(groupId: Int, isMuted: Boolean) {
        prefs.edit { putBoolean("$PREFIX_MUTE_GROUP$groupId", isMuted) }
    }

    fun isUserMuted(userId: Int): Boolean {
        return prefs.getBoolean("$PREFIX_MUTE_USER$userId", false)
    }

    fun setUserMute(userId: Int, isMuted: Boolean) {
        prefs.edit { putBoolean("$PREFIX_MUTE_USER$userId", isMuted) }
    }

    // --- Logout ---
    fun clearSession() {
        prefs.edit { clear() }
    }
}
