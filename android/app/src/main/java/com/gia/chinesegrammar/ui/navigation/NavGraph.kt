package com.gia.chinesegrammar.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gia.chinesegrammar.data.local.SavedVocabEntity
import com.gia.chinesegrammar.data.remote.AnalyzeResponse
import com.gia.chinesegrammar.ui.screens.*
import com.gia.chinesegrammar.ui.viewmodel.*
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"; const val ANALYZE = "analyze"; const val SYNTAX = "syntax"; const val TUTOR = "tutor"
    const val DICTIONARY = "dictionary"; const val VOCABULARY = "vocabulary"; const val HISTORY = "history"
    const val MODELS = "models"; const val PAIRING = "pairing"; const val SETTINGS = "settings"; const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun AppNavHost(modeConsentLabel: String, paired: Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    // Cross-screen shared state kept at host level (not in a global singleton):
    var syntaxResult by remember { mutableStateOf<AnalyzeResponse?>(null) }
    var pendingSample by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredResult by remember { mutableStateOf<AnalyzeResponse?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        Triple(Routes.HOME, "Trang chủ", Icons.Filled.Home),
        Triple(Routes.ANALYZE, "Phân tích", Icons.Filled.Translate),
        Triple(Routes.TUTOR, "Gia sư", Icons.Filled.School),
        Triple(Routes.DICTIONARY, "Từ điển", Icons.Filled.MenuBook),
        Triple(Routes.HISTORY, "Lịch sử", Icons.Filled.History)
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar { tabs.forEach { (route, label, icon) ->
                NavigationBarItem(selected = current == route, onClick = { nav.navigate(route) { launchSingleTop = true; popUpTo(Routes.HOME) } },
                    icon = { Icon(icon, label) }, label = { Text(label) })
            } }
        },
        topBar = {
            if (current != Routes.HOME) TopAppBar(title = { Text("Chinese Grammar AI") }, navigationIcon = {
                IconButton(onClick = { nav.navigate(Routes.HOME) { launchSingleTop = true } }) { Icon(Icons.Filled.Home, "Trang chủ") }
            }, actions = {
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } }) { Icon(Icons.Filled.Settings, "Cài đặt") }
                IconButton(onClick = { nav.navigate(Routes.VOCABULARY) { launchSingleTop = true } }) { Icon(Icons.Filled.Bookmarks, "Từ vựng") }
            })
        }
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(onNavigate = { route ->
                    if (route.startsWith("analyze_sample/")) { pendingSample = android.net.Uri.decode(route.removePrefix("analyze_sample/")); nav.navigate(Routes.ANALYZE) }
                    else nav.navigate(route) { launchSingleTop = true }
                }, settingsMode = modeConsentLabel, paired = paired)
            }
            composable(Routes.ANALYZE) {
                val vm: AnalyzeViewModel = hiltViewModel(); val vocabVm: VocabViewModel = hiltViewModel()
                LaunchedEffect(restoredResult) { restoredResult?.let { r -> vm.restore(r, pendingSample ?: ""); restoredResult = null } }
                AnalyzeScreen(vm, initialText = pendingSample,
                    onOpenSyntax = { r -> syntaxResult = r; nav.navigate(Routes.SYNTAX) },
                    onAskTutor = { text -> nav.navigate("${Routes.TUTOR}?context=" + android.net.Uri.encode(text)) },
                    onSaveVocab = { w, p, m -> vocabVm.add(w, p, m, ""); scope.launch { snackbar.showSnackbar("Đã lưu “$w” vào từ vựng") } })
            }
            composable(Routes.SYNTAX) { SyntaxTreeScreen(syntaxResult) }
            composable("${Routes.TUTOR}?context={context}", arguments = listOf(navArgument("context") { type = NavType.StringType; defaultValue = "" })) { entry ->
                val vm: TutorViewModel = hiltViewModel()
                LaunchedEffect(entry.arguments?.getString("context")) { vm.contextText = entry.arguments?.getString("context") ?: "" }
                TutorScreen(vm)
            }
            composable(Routes.DICTIONARY) {
                val vm: DictionaryViewModel = hiltViewModel()
                DictionaryScreen(vm, onSaveFeedback = { scope.launch { snackbar.showSnackbar("Đã lưu vào từ vựng") } })
            }
            composable(Routes.VOCABULARY) { VocabularyScreen(hiltViewModel()) }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = hiltViewModel()
                HistoryScreen(vm, onRestore = { r, text -> restoredResult = r; pendingSample = text; nav.navigate(Routes.ANALYZE) { launchSingleTop = true } })
            }
            composable(Routes.MODELS) { ModelManagerScreen(hiltViewModel()) }
            composable(Routes.PAIRING) { PairingScreen(hiltViewModel()) }
            composable(Routes.SETTINGS) { SettingsScreen(hiltViewModel(), onNavigate = { nav.navigate(it) { launchSingleTop = true } }) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(hiltViewModel()) }
        }
    }
}
