package com.gia.chinesegrammar.hybrid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Pairing credentials live ONLY here — EncryptedSharedPreferences backed by Android Keystore. Never logged, never in Room/DataStore. */
@Singleton
class SecureTokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "secure_pairing", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) {
        // Keystore unavailable (rare, e.g. corrupted key) — fall back to private prefs so pairing UI still works; token flagged unprotected.
        context.getSharedPreferences("secure_pairing_fallback", Context.MODE_PRIVATE)
    }

    var serverBaseUrl: String?
        get() = prefs.getString("server_base_url", null)
        set(v) { prefs.edit().putString("server_base_url", v).apply() }
    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(v) { prefs.edit().putString("device_id", v).apply() }

    /** Stores the server-issued `device_token` from POST /v1/pair (returned exactly once). */
    fun saveToken(token: String) { prefs.edit().putString("device_token", token).apply() }
    fun token(): String? = prefs.getString("device_token", null)
    fun isPaired(): Boolean = !token().isNullOrBlank() && !serverBaseUrl.isNullOrBlank()
    fun clearAll() { prefs.edit().clear().apply() }
}
