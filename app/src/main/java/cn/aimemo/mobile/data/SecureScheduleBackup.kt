package cn.aimemo.mobile.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SecureScheduleBackup {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val secretKey: SecretKey by lazy(::loadOrCreateKey)

    fun encode(contents: SecureBackupContents): String = encodeWithKey(contents, secretKey)

    fun decode(content: String): SecureBackupContents = decodeWithKey(content, secretKey)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "ai_memo_backup_v1"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT = "ai-memo-keystore-backup"
        private const val VERSION = 1
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private val BACKUP_AAD = FORMAT.toByteArray(Charsets.UTF_8)

        internal fun encodeWithKey(contents: SecureBackupContents, key: SecretKey): String {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(BACKUP_AAD)
            val encrypted = cipher.doFinal(ScheduleBackup.encodePayload(contents).toByteArray(Charsets.UTF_8))
            return JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("iv", Base64.getEncoder().withoutPadding().encodeToString(cipher.iv))
                put("ciphertext", Base64.getEncoder().withoutPadding().encodeToString(encrypted))
            }.toString()
        }

        internal fun decodeWithKey(content: String, key: SecretKey): SecureBackupContents = try {
            val root = JSONObject(content)
            require(root.optString("format") == FORMAT) { "请选择由当前 AI备忘录导出的自动加密备份" }
            require(root.optInt("version", 0) == VERSION) { "该备份版本暂不受支持，请升级应用后重试" }
            val iv = Base64.getDecoder().decode(root.getString("iv"))
            val ciphertext = Base64.getDecoder().decode(root.getString("ciphertext"))
            require(iv.size == IV_BYTES && ciphertext.size > GCM_TAG_BYTES) { "备份加密数据无效" }
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(BACKUP_AAD)
            ScheduleBackup.decodePayload(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("备份无法在本设备解密，文件可能已损坏或来自其他设备", error)
        }

        fun isEncrypted(content: String): Boolean = runCatching {
            JSONObject(content).optString("format") == FORMAT
        }.getOrDefault(false)
    }

    private fun loadOrCreateKey(): SecretKey {
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }
}
