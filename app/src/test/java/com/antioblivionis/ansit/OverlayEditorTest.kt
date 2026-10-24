package com.antioblivionis.ansit

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun adjustmentModeCanOpenBeforeRootOrModelSetup() {
        var opened = 0
        compose.setContent { AnsItTheme { AnsItContent(
            MainState(root = RootAccess.UNAVAILABLE, overlayLoading = false), onAdjustment = { opened++ }) } }
        compose.onNodeWithTag("adjustment_mode").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
    @Test fun draggingTheFrameChangesSavedPosition() {
        val initial = OverlayConfig.defaults()
        var saved: OverlayConfig? = null
        compose.setContent { AnsItTheme { OverlayEditor(initial, onSave = { saved = it }, onCancel = {}) } }
        compose.onNodeWithTag("frame_preview").performTouchInput {
            swipe(Offset(width * .5f, height * .35f), Offset(width * .55f, height * .47f))
        }
        compose.onNodeWithTag("save_overlay").performClick()
        compose.runOnIdle {
            assertTrue(saved!!.left > initial.left); assertTrue(saved!!.top > initial.top)
            assertEquals(initial.width, saved!!.width, .0001f); assertEquals(initial.height, saved!!.height, .0001f)
        }
    }
    @Test fun draggingCornerResizesInsteadOfMoving() {
        val initial = OverlayConfig.defaults()
        var saved: OverlayConfig? = null
        compose.setContent { AnsItTheme { OverlayEditor(initial, onSave = { saved = it }, onCancel = {}) } }
        compose.onNodeWithTag("frame_preview").performTouchInput {
            swipe(Offset(width * .94f, height * .5f), Offset(width * .72f, height * .68f))
        }
        compose.onNodeWithTag("save_overlay").performClick()
        compose.runOnIdle {
            assertTrue(saved!!.width < initial.width); assertTrue(saved!!.height > initial.height)
            assertEquals(initial.left, saved!!.left, .0001f); assertEquals(initial.top, saved!!.top, .0001f)
        }
    }
    @Test fun transparencySliderIsSavedAndPreviewRenders() {
        var saved: OverlayConfig? = null
        compose.setContent { AnsItTheme { OverlayEditor(OverlayConfig.defaults(), onSave = { saved = it }, onCancel = {}) } }
        compose.onNodeWithTag("transparency_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        render("adjustment-monet")
        compose.onNodeWithTag("save_overlay").performClick()
        compose.runOnIdle { assertEquals(.75f, saved!!.transparency, .0001f) }
    }
    @Test fun cancelDoesNotPublishDraftAndResetRestoresDefaults() {
        var saves = 0; var cancelled = 0
        var saved: OverlayConfig? = null
        compose.setContent { AnsItTheme { OverlayEditor(OverlayConfig.defaults(),
            onSave = { saves++; saved = it }, onCancel = { cancelled++ }) } }
        compose.onNodeWithTag("transparency_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        compose.onNodeWithContentDescription("取消调整").performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(0, saves) }
        compose.onNodeWithTag("reset_overlay").performClick()
        compose.onNodeWithTag("save_overlay").performClick()
        compose.runOnIdle { assertEquals(OverlayConfig.defaults(), saved) }
    }
    @Test fun lightThemeUsesTheSystemMonetPalette() {
        lateinit var actual: ColorScheme
        compose.setContent { AnsItTheme(dark = false) { actual = MaterialTheme.colorScheme } }
        compose.runOnIdle {
            val expected = dynamicLightColorScheme(compose.activity)
            assertEquals(expected.primary, actual.primary)
            assertEquals(expected.secondaryContainer, actual.secondaryContainer)
            assertEquals(expected.surfaceContainer, actual.surfaceContainer)
        }
    }
    @Test
    @Config(qualifiers = "zh-rCN-w393dp-h900dp-night-xhdpi")
    fun darkThemeUsesTheSystemMonetPalette() {
        lateinit var actual: ColorScheme
        compose.setContent { AnsItTheme(dark = true) { actual = MaterialTheme.colorScheme } }
        compose.runOnIdle {
            val expected = dynamicDarkColorScheme(compose.activity)
            assertEquals(expected.primary, actual.primary)
            assertEquals(expected.surface, actual.surface)
        }
    }
    private fun render(name: String) {
        val directory = System.getProperty("ansit.renderDir") ?: return
        compose.waitForIdle()
        compose.runOnIdle {
            val window = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
            window.draw(Canvas(bitmap))
            val file = File(directory, name + ".png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
