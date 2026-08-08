package cn.aimemo.mobile

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ai_memo_settings", Context.MODE_PRIVATE)

    var reminderLeadMinutes: Int
        get() = preferences.getInt(KEY_REMINDER_LEAD, 30)
        set(value) = preferences.edit().putInt(KEY_REMINDER_LEAD, value.coerceAtLeast(0)).apply()

    var finalReminderMinutes: Int
        get() = preferences.getInt(KEY_FINAL_REMINDER, reminderLeadMinutes)
        set(value) {
            val safe = value.coerceAtLeast(0)
            preferences.edit().putInt(KEY_FINAL_REMINDER, safe).putInt(KEY_REMINDER_LEAD, safe).apply()
        }

    var firstReminderMinutes: Int?
        get() = preferences.getInt(KEY_FIRST_REMINDER, -1).takeIf { it >= 0 }
        set(value) = preferences.edit().putInt(KEY_FIRST_REMINDER, value ?: -1).apply()

    var secondReminderMinutes: Int?
        get() = preferences.getInt(KEY_SECOND_REMINDER, -1).takeIf { it >= 0 }
        set(value) = preferences.edit().putInt(KEY_SECOND_REMINDER, value ?: -1).apply()

    var apiKey: String
        get() = preferences.getString(KEY_API_KEY, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_API_KEY, value.trim()).apply()

    var modelMode: String
        get() = preferences.getString(KEY_MODEL_MODE, "separate") ?: "separate"
        set(value) = preferences.edit().putString(KEY_MODEL_MODE, value).apply()

    var unifiedModel: String
        get() = preferences.getString(KEY_UNIFIED_MODEL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_UNIFIED_MODEL, value.trim()).apply()

    var textModel: String
        get() = preferences.getString(KEY_TEXT_MODEL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_TEXT_MODEL, value.trim()).apply()

    var imageModel: String
        get() = preferences.getString(KEY_IMAGE_MODEL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_IMAGE_MODEL, value.trim()).apply()

    var darkMode: Boolean
        get() = preferences.getBoolean(KEY_DARK_MODE, false)
        set(value) = preferences.edit().putBoolean(KEY_DARK_MODE, value).apply()

    companion object {
        private const val KEY_REMINDER_LEAD = "reminder_lead_minutes"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_FINAL_REMINDER = "final_reminder_minutes"
        private const val KEY_FIRST_REMINDER = "first_reminder_minutes"
        private const val KEY_SECOND_REMINDER = "second_reminder_minutes"
        private const val KEY_API_KEY = "model_api_key"
        private const val KEY_MODEL_MODE = "model_mode"
        private const val KEY_UNIFIED_MODEL = "unified_model"
        private const val KEY_TEXT_MODEL = "text_model"
        private const val KEY_IMAGE_MODEL = "image_model"
    }
}
