package com.gia.chinesegrammar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Smoke test: app launches to Home with the main entry points visible. Requires a device/emulator. */
class AppLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun homeShowsEntryPoints() {
        rule.onNodeWithText("Học ngữ pháp tiếng Trung").assertIsDisplayed()
        rule.onNodeWithText("Phân tích câu").assertIsDisplayed()
    }
}
