package com.example.ui

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.ui.DiagnosticViewModel.SavedProfileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class WireGuardProfileRepository(context: Context) {
    data class Snapshot(
        val profiles: List<SavedProfileItem> = emptyList(),
        val selectedProfileId: String? = null,
        val error: String? = null
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): Snapshot {
        val encrypted = preferences.getString(PROFILES_KEY, null) ?: return Snapshot()
        return try {
            val root = JSONObject(decrypt(encrypted))
            val array = root.optJSONArray("profiles") ?: JSONArray()
            val profiles = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    val endpoint = item.optString("endpoint")
                    val rawConfig = item.optString("rawConfig")
                    if (id.isNotBlank() && rawConfig.isNotBlank()) {
                        add(
                            SavedProfileItem(
                                id = id,
                                name = name.ifBlank { "WireGuard profile" },
                                endpoint = endpoint,
                                rawConfig = rawConfig,
                                isDefault = item.optBoolean("isDefault", false)
                            )
                        )
                    }
                }
            }
            val selected = root.optString("selectedProfileId").takeIf { it.isNotBlank() }
                ?.takeIf { id -> profiles.any { it.id == id } }
            Snapshot(profiles = profiles, selectedProfileId = selected)
        } catch (error: Exception) {
            // Keep the encrypted value intact so a recoverable Keystore problem does not
            // destroy the user's only copy of an imported profile.
            Snapshot(error = error.message ?: "Encrypted profile storage could not be read.")
        }
    }

    fun save(profiles: List<SavedProfileItem>, selectedProfileId: String?): Result<Unit> = runCatching {
        require(selectedProfileId == null || profiles.any { it.id == selectedProfileId }) {
            "Selected profile does not exist in the saved profile list."
        }

        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("endpoint", profile.endpoint)
                    .put("rawConfig", profile.rawConfig)
                    .put("isDefault", profile.isDefault)
            )
        }
        val root = JSONObject()
            .put("profiles", array)
            .put("selectedProfileId", selectedProfileId ?: JSONObject.NULL)
        val encrypted = encrypt(root.toString())

        if (!preferences.edit().putString(PROFILES_KEY, encrypted).commit()) {
            throw IOException("Android could not commit the encrypted profile store.")
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > GCM_IV_BYTES) { "Encrypted profile data is incomplete." }
        val iv = combined.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = combined.copyOfRange(GCM_IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "vianshield_wireguard_profiles_v1"
        const val PROFILES_KEY = "encrypted_profiles_blob"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vianshield_wireguard_profiles_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
