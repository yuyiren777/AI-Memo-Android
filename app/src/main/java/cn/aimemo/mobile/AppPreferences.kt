package cn.aimemo.mobile

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ai_memo_settings", Context.MODE_PRIVATE)

    var reminderLeadMinutes: Int
        get() = preferences.getInt(KEY_REMINDER_LEAD, 30)
        set(value) = preferences.edit().putInt(KEY_REMINDER_LEAD, value.coerceAtLeast(0)).apply()

    var darkMode: Boolean
        get() = preferences.getBoolean(KEY_DARK_MODE, false)
        set(value) = preferences.edit().putBoolean(KEY_DARK_MODE, value).apply()

    companion object {
        private const val KEY_REMINDER_LEAD = "reminder_lead_minutes"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}

