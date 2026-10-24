package com.antioblivionis.ansit

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun mainEntryAndBackPreserveSettingsDraft() {
        compose.setContent { AnsItTheme { AnsItContent(MainState(triggerText = "925", triggerLoading = false)) } }
        compose.onNodeWithTag("open_about").performClick()
        compose.onNodeWithText("开发者").assertIsDisplayed()
        compose.onNodeWithContentDescription("AntiOblivionis 的 GitHub 头像").assertIsDisplayed()
        compose.onNodeWithText("版本 " + BuildConfig.VERSION_NAME).assertIsDisplayed()
        render("about-light")
        compose.onNodeWithTag("about_back").performClick()
        compose.onNodeWithTag("trigger_millis").performScrollTo().assertTextContains("925")
    }

    @Test fun destinationsUsePublicIdentityAndRepository() {
        val opened = mutableListOf<String>()
        compose.setContent { AnsItTheme { AboutScreen(onBack = {}, onOpenLink = { opened.add(it) }) } }
        for (tag in listOf("about_home", "about_github", "about_website", "about_repository")) {
            compose.onNodeWithTag(tag).performScrollTo().performClick()
        }
        compose.runOnIdle { assertEquals(listOf(AboutLinks.HOME, AboutLinks.GITHUB, AboutLinks.WEBSITE, AboutLinks.REPOSITORY), opened) }
    }

    @Test fun unavailableBrowserIsRecoverable() {
        compose.setContent { AnsItTheme { AboutScreen(onBack = {}, onOpenLink = { throw IllegalStateException("No browser") }) } }
        compose.onNodeWithTag("about_home").performScrollTo().performClick()
        compose.onNodeWithText("暂时无法打开链接").assertIsDisplayed()
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithTag("about_back").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h900dp-night-xhdpi")
    fun narrowDarkLayoutKeepsLinksReachable() {
        compose.setContent { AnsItTheme(dark = true) { AboutScreen(onBack = {}) } }
        compose.onNodeWithContentDescription("AntiOblivionis 的 GitHub 头像").assertIsDisplayed()
        compose.onNodeWithTag("about_repository").performScrollTo().assertIsDisplayed()
        render("about-dark-320")
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
