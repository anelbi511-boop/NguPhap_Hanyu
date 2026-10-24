package com.gia.chinesegrammar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gia.chinesegrammar.hybrid.ModeManager
import com.gia.chinesegrammar.hybrid.SecureTokenStore
import com.gia.chinesegrammar.ui.navigation.AppNavHost
import com.gia.chinesegrammar.ui.theme.ChineseGrammarTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var modeManager: ModeManager
    @Inject lateinit var tokenStore: SecureTokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by modeManager.settings.collectAsState()
            ChineseGrammarTheme(darkOverride = settings.darkTheme) {
                AppNavHost(modeConsentLabel = settings.mode.name, paired = tokenStore.isPaired())
            }
        }
    }
}
