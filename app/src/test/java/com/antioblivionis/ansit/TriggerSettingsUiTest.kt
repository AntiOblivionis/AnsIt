package com.antioblivionis.ansit

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TriggerSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun exactNumericThresholdCanBeSavedWhileServiceIsStopped() {
        val state = mutableStateOf(MainState(triggerLoading = false))
        var saved = ""
        compose.setContent { AnsItTheme { AnsItContent(state.value,
            onTriggerEdit = { state.value = state.value.copy(triggerText = it, triggerDirty = true) },
            onTriggerSave = { saved = state.value.triggerText }) } }
        compose.onNodeWithTag("trigger_millis").performScrollTo().performTextReplacement("925")
        compose.onNodeWithTag("save_trigger").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("925", saved); assertEquals(650, state.value.triggerHoldMillis) }
        render("trigger-settings")
    }
    @Test fun invalidThresholdCannotBeSaved() {
        val state = mutableStateOf(MainState(triggerLoading = false))
        compose.setContent { AnsItTheme { AnsItContent(state.value, onTriggerEdit = {
            state.value = state.value.copy(triggerText = it) }) } }
        for (value in listOf("50", "3001", "abc", "")) {
            compose.onNodeWithTag("trigger_millis").performScrollTo().performTextReplacement(value)
            compose.onNodeWithTag("save_trigger").performScrollTo().assertIsNotEnabled()
        }
    }
    @Test fun sliderAndRestoreDefaultOnlyEditDraftUntilSaved() {
        val state = mutableStateOf(MainState(triggerLoading = false))
        var saves = 0
        compose.setContent { AnsItTheme { AnsItContent(state.value,
            onTriggerEdit = { state.value = state.value.copy(triggerText = it, triggerDirty = true) },
            onTriggerSave = { saves++ }) } }
        compose.onNodeWithTag("trigger_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1200f) }
        compose.onNodeWithTag("trigger_millis").assertTextContains("1200")
        compose.onNodeWithTag("reset_trigger").performScrollTo().performClick()
        compose.onNodeWithTag("trigger_millis").assertTextContains("650")
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithTag("save_trigger").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }
    private fun render(name: String) {
        val directory = System.getProperty("ansit.renderDir") ?: return
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File(directory, name + ".png"); file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
