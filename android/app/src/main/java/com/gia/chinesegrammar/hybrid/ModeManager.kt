package com.gia.chinesegrammar.hybrid

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class InferenceMode { OFFLINE, ADVANCED, AUTO }

data class HybridSettings(
    val mode: InferenceMode = InferenceMode.OFFLINE,
    val autoConsent: Boolean = false,   // explicit user consent before AUTO may send sentence text to the paired PC — default OFF
    val hskLevel: Int = 3,
    val darkTheme: Boolean? = null      // null = follow system
)

/** Plain (non-secret) prefs for mode/consent/HSK/theme. Consent is a separate explicit flag, never implied by pairing. */
@Singleton
class ModeManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("hybrid_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<HybridSettings> = _settings.asStateFlow()

    private fun load(): HybridSettings = HybridSettings(
        mode = runCatching { InferenceMode.valueOf(prefs.getString("mode", "OFFLINE")!!) }.getOrDefault(InferenceMode.OFFLINE),
        autoConsent = prefs.getBoolean("auto_consent", false),
        hskLevel = prefs.getInt("hsk_level", 3),
        darkTheme = if (prefs.contains("dark_theme")) prefs.getBoolean("dark_theme", false) else null
    )

    private fun persist(s: HybridSettings) {
        prefs.edit().putString("mode", s.mode.name).putBoolean("auto_consent", s.autoConsent)
            .putInt("hsk_level", s.hskLevel)
            .apply { if (s.darkTheme == null) remove("dark_theme") else putBoolean("dark_theme", s.darkTheme) }.apply()
        _settings.value = s
    }

    fun setMode(m: InferenceMode) = persist(_settings.value.copy(mode = m))
    fun setAutoConsent(v: Boolean) = persist(_settings.value.copy(autoConsent = v))
    fun setHsk(level: Int) = persist(_settings.value.copy(hskLevel = level.coerceIn(1, 6)))
    fun setDarkTheme(v: Boolean?) = persist(_settings.value.copy(darkTheme = v))
}
