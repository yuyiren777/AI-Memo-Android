package cn.aimemo.mobile

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cn.aimemo.mobile.data.BudgetSettings
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import java.time.YearMonth

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ai_memo_settings", Context.MODE_PRIVATE)
    private val secureApiKeyStore = SecurePreferenceValueStore(
        preferences = preferences,
        legacyPreferenceKey = KEY_API_KEY,
        encryptedPreferenceKey = KEY_ENCRYPTED_API_KEY,
        ivPreferenceKey = KEY_API_KEY_IV,
        keystoreAlias = "ai_memo_api_key_v1",
    )
    private val secureDeepSeekApiKeyStore = SecurePreferenceValueStore(
        preferences = preferences,
        encryptedPreferenceKey = KEY_ENCRYPTED_DEEPSEEK_API_KEY,
        ivPreferenceKey = KEY_DEEPSEEK_API_KEY_IV,
        keystoreAlias = "ai_memo_deepseek_api_key_v1",
    )
    private val secureExpenseCategoriesStore = SecurePreferenceValueStore(
        preferences = preferences,
        encryptedPreferenceKey = KEY_ENCRYPTED_EXPENSE_CATEGORIES,
        ivPreferenceKey = KEY_EXPENSE_CATEGORIES_IV,
        keystoreAlias = "ai_memo_expense_categories_v1",
    )
    private val secureIncomeCategoriesStore = SecurePreferenceValueStore(
        preferences = preferences,
        encryptedPreferenceKey = KEY_ENCRYPTED_INCOME_CATEGORIES,
        ivPreferenceKey = KEY_INCOME_CATEGORIES_IV,
        keystoreAlias = "ai_memo_income_categories_v1",
    )
    private val secureBudgetStore = SecurePreferenceValueStore(
        preferences = preferences,
        encryptedPreferenceKey = KEY_ENCRYPTED_BUDGET_SETTINGS,
        ivPreferenceKey = KEY_BUDGET_SETTINGS_IV,
        keystoreAlias = "ai_memo_budget_settings_v1",
    )
    private val secureCapturePasswordStore = SecurePreferenceValueStore(
        preferences = preferences,
        encryptedPreferenceKey = KEY_ENCRYPTED_CAPTURE_PASSWORD,
        ivPreferenceKey = KEY_CAPTURE_PASSWORD_IV,
        keystoreAlias = "ai_memo_capture_password_v1",
    )

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
        get() = secureApiKeyStore.read()
        set(value) = secureApiKeyStore.write(value.trim())

    var deepSeekApiKey: String
        get() = secureDeepSeekApiKeyStore.read()
        set(value) = secureDeepSeekApiKeyStore.write(value.trim())

    var aiProvider: String
        get() = preferences.getString(KEY_AI_PROVIDER, "zhipu")
            ?.takeIf { it == "zhipu" || it == "deepseek" }
            ?: "zhipu"
        set(value) = preferences.edit()
            .putString(KEY_AI_PROVIDER, value.takeIf { it == "zhipu" || it == "deepseek" } ?: "zhipu")
            .apply()

    var activeApiKey: String
        get() = if (aiProvider == "deepseek") deepSeekApiKey else apiKey
        set(value) {
            if (aiProvider == "deepseek") deepSeekApiKey = value else apiKey = value
        }

    val hasDeepSeekApiKey: Boolean
        get() = secureDeepSeekApiKeyStore.hasValue()

    val hasApiKey: Boolean
        get() = if (aiProvider == "deepseek") hasDeepSeekApiKey else secureApiKeyStore.hasValue()

    var modelMode: String
        get() = preferences.getString(KEY_MODEL_MODE, "separate")
            ?.takeIf { it == "separate" || it == "unified" }
            ?: "separate"
        set(value) = preferences.edit().putString(KEY_MODEL_MODE, value).apply()

    var unifiedModel: String
        get() = preferences.getString(modelPreferenceKey(KEY_UNIFIED_MODEL), "").orEmpty()
        set(value) = preferences.edit().putString(modelPreferenceKey(KEY_UNIFIED_MODEL), value.trim()).apply()

    var textModel: String
        get() = preferences.getString(modelPreferenceKey(KEY_TEXT_MODEL), "").orEmpty()
        set(value) = preferences.edit().putString(modelPreferenceKey(KEY_TEXT_MODEL), value.trim()).apply()

    var imageModel: String
        get() = preferences.getString(modelPreferenceKey(KEY_IMAGE_MODEL), "").orEmpty()
        set(value) = preferences.edit().putString(modelPreferenceKey(KEY_IMAGE_MODEL), value.trim()).apply()

    private fun modelPreferenceKey(baseKey: String, provider: String = aiProvider): String =
        if (provider == "deepseek") "${baseKey}_deepseek" else baseKey

    var themeMode: String
        get() {
            val stored = preferences.getString(KEY_THEME_MODE, null)
            if (stored in THEME_MODES) return stored.orEmpty()
            return if (preferences.contains(KEY_DARK_MODE)) {
                if (preferences.getBoolean(KEY_DARK_MODE, false)) "dark" else "light"
            } else {
                "soft"
            }
        }
        set(value) {
            val normalized = value.takeIf { it in THEME_MODES } ?: "soft"
            preferences.edit()
                .putString(KEY_THEME_MODE, normalized)
                .putBoolean(KEY_DARK_MODE, normalized == "dark")
                .apply()
        }

    var darkMode: Boolean
        get() = themeMode == "dark"
        set(value) { themeMode = if (value) "dark" else "light" }

    var customExpenseCategories: Set<String>
        get() = readSecureCategories(secureExpenseCategoriesStore, KEY_CUSTOM_EXPENSE_CATEGORIES)
        set(value) = writeSecureCategories(secureExpenseCategoriesStore, KEY_CUSTOM_EXPENSE_CATEGORIES, value)

    var customIncomeCategories: Set<String>
        get() = readSecureCategories(secureIncomeCategoriesStore, KEY_CUSTOM_INCOME_CATEGORIES)
        set(value) = writeSecureCategories(secureIncomeCategoriesStore, KEY_CUSTOM_INCOME_CATEGORIES, value)

    var budgetSettings: BudgetSettings
        get() = runCatching {
            val json = JSONObject(secureBudgetStore.read())
            val months = json.optJSONObject("months")
            val years = json.optJSONObject("years")
            if (months != null || years != null) {
                BudgetSettings(
                    monthlyBudgetsCents = buildMap {
                        months?.keys()?.forEach { key ->
                            val month = runCatching { YearMonth.parse(key) }.getOrNull()
                            val amount = months.optLong(key, 0L).coerceAtLeast(0L)
                            if (month != null && amount > 0L) put(month, amount)
                        }
                    },
                    yearlyBudgetsCents = buildMap {
                        years?.keys()?.forEach { key ->
                            val year = key.toIntOrNull()?.takeIf { it in 1..9999 }
                            val amount = years.optLong(key, 0L).coerceAtLeast(0L)
                            if (year != null && amount > 0L) put(year, amount)
                        }
                    },
                )
            } else {
                val currentMonth = YearMonth.now()
                val legacyMonth = json.optLong("monthly", 0L).coerceAtLeast(0L)
                val legacyYear = json.optLong("yearly", 0L).coerceAtLeast(0L)
                val migrated = BudgetSettings(
                    monthlyBudgetsCents = if (legacyMonth > 0L) mapOf(currentMonth to legacyMonth) else emptyMap(),
                    yearlyBudgetsCents = if (legacyYear > 0L) mapOf(currentMonth.year to legacyYear) else emptyMap(),
                )
                budgetSettings = migrated
                migrated
            }
        }.getOrDefault(BudgetSettings())
        set(value) {
            secureBudgetStore.write(
                JSONObject()
                    .put("version", 2)
                    .put("months", JSONObject().apply {
                        value.monthlyBudgetsCents.toSortedMap().forEach { (month, amount) ->
                            if (amount > 0L) put(month.toString(), amount)
                        }
                    })
                    .put("years", JSONObject().apply {
                        value.yearlyBudgetsCents.toSortedMap().forEach { (year, amount) ->
                            if (year in 1..9999 && amount > 0L) put(year.toString(), amount)
                        }
                    })
                    .toString(),
            )
        }

    var captureProtectionEnabled: Boolean
        get() = preferences.getBoolean(KEY_CAPTURE_PROTECTION_ENABLED, true)
        private set(value) {
            check(preferences.edit().putBoolean(KEY_CAPTURE_PROTECTION_ENABLED, value).commit()) {
                "无法保存截图保护设置"
            }
        }

    val hasCaptureProtectionPassword: Boolean
        get() = secureCapturePasswordStore.hasValue()

    fun enableCaptureProtection() {
        captureProtectionEnabled = true
    }

    fun disableCaptureProtection(password: CharArray, confirmation: CharArray?) {
        try {
            require(password.size >= CAPTURE_PASSWORD_MIN_LENGTH) {
                "安全密码至少需要 $CAPTURE_PASSWORD_MIN_LENGTH 位"
            }
            val stored = secureCapturePasswordStore.read()
            if (stored.isBlank()) {
                require(confirmation != null && password.contentEquals(confirmation)) { "两次输入的密码不一致" }
                secureCapturePasswordStore.write(createCapturePasswordCredential(password))
            } else {
                require(verifyCapturePassword(password, stored)) { "安全密码不正确" }
            }
            captureProtectionEnabled = false
        } finally {
            password.fill('\u0000')
            confirmation?.fill('\u0000')
        }
    }

    private fun createCapturePasswordCredential(password: CharArray): String {
        val salt = ByteArray(CAPTURE_PASSWORD_SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = deriveCapturePasswordHash(password, salt, CAPTURE_PASSWORD_ITERATIONS)
        return try {
            JSONObject()
                .put("version", 1)
                .put("iterations", CAPTURE_PASSWORD_ITERATIONS)
                .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                .toString()
        } finally {
            hash.fill(0)
            salt.fill(0)
        }
    }

    private fun verifyCapturePassword(password: CharArray, encoded: String): Boolean = runCatching {
        val json = JSONObject(encoded)
        require(json.optInt("version", 0) == 1)
        val iterations = json.optInt("iterations", 0)
        require(iterations in 100_000..500_000)
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val expected = Base64.decode(json.getString("hash"), Base64.NO_WRAP)
        require(salt.size == CAPTURE_PASSWORD_SALT_BYTES && expected.size == CAPTURE_PASSWORD_HASH_BYTES)
        val actual = deriveCapturePasswordHash(password, salt, iterations)
        try {
            MessageDigest.isEqual(expected, actual)
        } finally {
            actual.fill(0)
            expected.fill(0)
            salt.fill(0)
        }
    }.getOrDefault(false)

    private fun deriveCapturePasswordHash(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, CAPTURE_PASSWORD_HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun cleanCategories(categories: Set<String>): Set<String> =
        categories.map(String::trim).filter(String::isNotBlank).toSet()

    private fun readSecureCategories(store: SecurePreferenceValueStore, legacyKey: String): Set<String> {
        var encoded = store.read()
        if (encoded.isBlank()) {
            val legacy = cleanCategories(preferences.getStringSet(legacyKey, emptySet()).orEmpty())
            if (legacy.isEmpty()) return emptySet()
            encoded = JSONArray(legacy.toList()).toString()
            store.write(encoded)
            preferences.edit().remove(legacyKey).apply()
        }
        return runCatching {
            val array = JSONArray(encoded)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun writeSecureCategories(
        store: SecurePreferenceValueStore,
        legacyKey: String,
        categories: Set<String>,
    ) {
        val cleaned = cleanCategories(categories)
        store.write(if (cleaned.isEmpty()) "" else JSONArray(cleaned.toList()).toString())
        preferences.edit().remove(legacyKey).apply()
    }

    companion object {
        private const val KEY_REMINDER_LEAD = "reminder_lead_minutes"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FINAL_REMINDER = "final_reminder_minutes"
        private const val KEY_FIRST_REMINDER = "first_reminder_minutes"
        private const val KEY_SECOND_REMINDER = "second_reminder_minutes"
        private const val KEY_API_KEY = "model_api_key"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_MODEL_MODE = "model_mode"
        private const val KEY_UNIFIED_MODEL = "unified_model"
        private const val KEY_TEXT_MODEL = "text_model"
        private const val KEY_IMAGE_MODEL = "image_model"
        private const val KEY_CUSTOM_EXPENSE_CATEGORIES = "custom_expense_categories"
        private const val KEY_CUSTOM_INCOME_CATEGORIES = "custom_income_categories"
        private const val KEY_ENCRYPTED_API_KEY = "model_api_key_encrypted"
        private const val KEY_API_KEY_IV = "model_api_key_iv"
        private const val KEY_ENCRYPTED_DEEPSEEK_API_KEY = "deepseek_api_key_encrypted"
        private const val KEY_DEEPSEEK_API_KEY_IV = "deepseek_api_key_iv"
        private const val KEY_ENCRYPTED_EXPENSE_CATEGORIES = "expense_categories_encrypted"
        private const val KEY_EXPENSE_CATEGORIES_IV = "expense_categories_iv"
        private const val KEY_ENCRYPTED_INCOME_CATEGORIES = "income_categories_encrypted"
        private const val KEY_INCOME_CATEGORIES_IV = "income_categories_iv"
        private const val KEY_ENCRYPTED_BUDGET_SETTINGS = "budget_settings_encrypted"
        private const val KEY_BUDGET_SETTINGS_IV = "budget_settings_iv"
        private const val KEY_CAPTURE_PROTECTION_ENABLED = "capture_protection_enabled"
        private const val KEY_ENCRYPTED_CAPTURE_PASSWORD = "capture_password_encrypted"
        private const val KEY_CAPTURE_PASSWORD_IV = "capture_password_iv"
        private const val CAPTURE_PASSWORD_MIN_LENGTH = 8
        private const val CAPTURE_PASSWORD_ITERATIONS = 210_000
        private const val CAPTURE_PASSWORD_SALT_BYTES = 16
        private const val CAPTURE_PASSWORD_HASH_BITS = 256
        private const val CAPTURE_PASSWORD_HASH_BYTES = CAPTURE_PASSWORD_HASH_BITS / 8
        private val THEME_MODES = setOf("light", "soft", "dark")
    }
}

private class SecurePreferenceValueStore(
    private val preferences: SharedPreferences,
    private val encryptedPreferenceKey: String,
    private val ivPreferenceKey: String,
    private val keystoreAlias: String,
    private val legacyPreferenceKey: String? = null,
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun hasValue(): Boolean =
        !preferences.getString(encryptedPreferenceKey, null).isNullOrBlank() ||
            legacyPreferenceKey?.let { !preferences.getString(it, null).isNullOrBlank() } == true

    fun read(): String {
        val encrypted = preferences.getString(encryptedPreferenceKey, null)
            ?: return migrateLegacyValue()
        val iv = preferences.getString(ivPreferenceKey, null)
            ?: return clearUnreadableValue()
        return runCatching {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrElse { clearUnreadableValue() }
    }

    fun write(value: String) {
        if (value.isBlank()) {
            preferences.edit()
                .remove(encryptedPreferenceKey)
                .remove(ivPreferenceKey)
                .also { editor -> legacyPreferenceKey?.let(editor::remove) }
                .apply()
            return
        }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(
            preferences.edit()
                .putString(encryptedPreferenceKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivPreferenceKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .also { editor -> legacyPreferenceKey?.let(editor::remove) }
                .commit()
        ) { "无法安全保存敏感设置" }
    }

    private fun migrateLegacyValue(): String {
        val legacyKey = legacyPreferenceKey ?: return ""
        val legacy = preferences.getString(legacyKey, "").orEmpty()
        if (legacy.isBlank()) return ""
        return runCatching {
            write(legacy)
            legacy
        }.getOrElse {
            check(preferences.edit().remove(legacyKey).commit()) {
                "Unable to remove an insecure legacy preference"
            }
            ""
        }
    }

    private fun clearUnreadableValue(): String {
        preferences.edit()
            .remove(encryptedPreferenceKey)
            .remove(ivPreferenceKey)
            .apply()
        return ""
    }

    private fun getOrCreateSecretKey(): SecretKey {
        (keyStore.getKey(keystoreAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keystoreAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
