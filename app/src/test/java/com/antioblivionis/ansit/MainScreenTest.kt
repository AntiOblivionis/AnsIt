package com.antioblivionis.ansit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], qualifiers = "zh-rCN-w393dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun status(running: Boolean) = MainState(
        root = RootAccess.GRANTED, hookCurrent = true, serviceCurrent = running, overlayLoading = false,
        snapshot = RootServiceStarter.Snapshot(
            if (running) RootServiceStarter.State.RUNNING else RootServiceStarter.State.STOPPED,
            BuildConfig.VERSION_NAME, "", RootServiceStarter.Hook.LOADED, "", ""),
        checkedAt = 1792825800000L,
    )

    @Test fun stoppedButtonKeepsStartLabelDuringBackgroundCheck() {
        val state = mutableStateOf(status(false))
        compose.setContent { AnsItTheme { AnsItContent(state.value) } }
        compose.onNodeWithTag("service_control").assertTextEquals("开始")
        compose.runOnIdle { state.value = state.value.copy(operation = Operation.CHECK) }
        compose.onNodeWithTag("service_control").assertTextEquals("开始").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(operation = Operation.NONE) }
        compose.onNodeWithTag("service_control").assertTextEquals("开始")
        render("stopped-light")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w393dp-h900dp-night-xhdpi")
    fun runningButtonKeepsStopLabelDuringBackgroundCheck() {
        val state = mutableStateOf(status(true))
        compose.setContent { AnsItTheme(dark = true) { AnsItContent(state.value) } }
        compose.runOnIdle { state.value = state.value.copy(operation = Operation.CHECK) }
        compose.onNodeWithTag("service_control").assertTextEquals("停止")
        compose.runOnIdle { state.value = state.value.copy(operation = Operation.NONE) }
        render("running-dark")
    }

    @Test fun firstUseExplainsRootScopeAndRestartWithoutInventingGrantedState() {
        compose.setContent { AnsItTheme { AnsItContent(MainState(showGuide = true)) } }
        compose.onNodeWithText("允许 Root 权限").assertIsDisplayed()
        compose.onNodeWithText("激活 LSPosed 模块").assertIsDisplayed()
        compose.onNodeWithText("重启 SystemUI").performScrollTo().assertIsNotEnabled()
        render("first-use", "setup_guide")
    }

    @Test fun failedStatusOffersCheckRatherThanPretendingStopped() {
        compose.setContent { AnsItTheme { AnsItContent(MainState(root = RootAccess.UNAVAILABLE)) } }
        compose.onNodeWithTag("service_control").assertTextEquals("授权并检查")
        compose.onNodeWithText("状态暂时不可用").assertIsDisplayed()
    }

    @Test fun controlUsesTheProvidedActionOnce() {
        var actions = 0
        compose.setContent { AnsItTheme { AnsItContent(status(true), onToggle = { actions++ }) } }
        compose.onNodeWithTag("service_control").performClick()
        compose.runOnIdle { assertEquals(1, actions) }
    }

    private fun render(name: String, tag: String = "main_screen") {
        val directory = System.getProperty("ansit.renderDir") ?: return
        val file = File(directory, name + ".png")
        file.parentFile?.mkdirs()
        compose.waitForIdle()
        compose.runOnIdle {
            val window = if (tag == "setup_guide")
                WindowInspector.getGlobalWindowViews().lastOrNull { it.width > 0 && it.height > 0 }
                    ?: compose.activity.window.decorView
                else compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
            window.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
