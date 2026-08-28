package com.catkiss62.supergemmatest

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveApiSettings(endpoint: String, model: String, apiKey: String) {
        preferences.edit()
            .putString(KEY_ENDPOINT, endpoint.trim())
            .putString(KEY_MODEL, model.trim())
            .putString(KEY_API_KEY, encrypt(apiKey.trim()))
            .apply()
    }

    fun endpoint(): String = preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
    fun model(): String = preferences.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun apiKey(): String {
        val encrypted = preferences.getString(KEY_API_KEY, null) ?: return ""
        return runCatching { decrypt(encrypted) }.getOrDefault("")
    }

    fun saveImportedModel(info: ImportedModelInfo) {
        preferences.edit()
            .putString(KEY_MODEL_PATH, info.path)
            .putString(KEY_MODEL_FILE, info.fileName)
            .putLong(KEY_MODEL_SIZE, info.sizeBytes)
            .putString(KEY_MODEL_SHA, info.sha256)
            .apply()
    }

    fun importedModel(): ImportedModelInfo? {
        val path = preferences.getString(KEY_MODEL_PATH, null) ?: return null
        return ImportedModelInfo(
            path = path,
            fileName = preferences.getString(KEY_MODEL_FILE, "model.litertlm") ?: "model.litertlm",
            sizeBytes = preferences.getLong(KEY_MODEL_SIZE, 0L),
            sha256 = preferences.getString(KEY_MODEL_SHA, "") ?: "",
        )
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        val parts = value.split('.', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
        private const val PREFS_NAME = "local_settings"
        private const val KEY_ALIAS = "supergemma_test_deepseek_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ENDPOINT = "deepseek_endpoint"
        private const val KEY_MODEL = "deepseek_model"
        private const val KEY_API_KEY = "deepseek_api_key"
        private const val KEY_MODEL_PATH = "local_model_path"
        private const val KEY_MODEL_FILE = "local_model_file"
        private const val KEY_MODEL_SIZE = "local_model_size"
        private const val KEY_MODEL_SHA = "local_model_sha"
    }
}
