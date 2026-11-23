package ltd.tgwgroup.ansit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConfigurationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun modelSaveButtonStartsTheCheckAction() {
        var checks = 0
        compose.setContent { AnsItTheme { AnsItContent(MainState(), onSave = { checks++ }) } }
        compose.onNodeWithTag("model_save_check").performScrollTo().assertTextEquals("检测并保存").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
    }
    @Test fun searchConfigurationCanBeEnabledAndCheckedIndependently() {
        val state = mutableStateOf(MainState(search = SearchForm(false, "test-only-key")))
        var checks = 0
        compose.setContent { AnsItTheme { AnsItContent(state.value,
            onSearchEdit = { state.value = state.value.copy(search = it) }, onSearchSave = { checks++ }) } }
        compose.onNodeWithTag("search_enabled").performScrollTo().performClick()
        compose.onNodeWithTag("search_save_check").performScrollTo().assertTextEquals("检测并保存").performClick()
        compose.runOnIdle { assertEquals(1, checks); assertEquals(true, state.value.search.enabled) }
        render("search-settings")
    }
    @Test fun longValidationCanBeCancelledAndDoesNotDisableStoppingRootService() {
        var cancels = 0
        val state = MainState(saving = true,
            snapshot = RootServiceStarter.Snapshot(RootServiceStarter.State.RUNNING, "", "", RootServiceStarter.Hook.ABSENT, "", ""))
        compose.setContent { AnsItTheme { AnsItContent(state, onCancelValidation = { cancels++ }) } }
        compose.onNodeWithTag("service_control").assertTextEquals("停止").assertIsEnabled()
        compose.onNodeWithTag("model_save_check").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("cancel_model_check").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
    }
    @Test fun disabledSearchHasAnExplicitSaveOffAction() {
        compose.setContent { AnsItTheme { AnsItContent(MainState(search = SearchForm(false, "expired-key"))) } }
        compose.onNodeWithTag("search_save_check").performScrollTo().assertTextEquals("保存关闭状态").assertIsEnabled()
    }
    @Test fun answerPromptCanBeSavedAndClearedFromSettings() {
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[MainViewModel::class.java] }
        val file = File(compose.activity.application.filesDir, AnswerPromptConfig.FILE_NAME)
        fun awaitSettings(condition: () -> Boolean) = compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
        compose.setContent {
            val state by model.state.collectAsState()
            AnsItTheme { AnsItContent(state.copy(showGuide = false),
                onPromptEdit = model::editAnswerPrompt, onPromptSave = model::saveAnswerPrompt) }
        }
        awaitSettings { !model.state.value.promptLoading }
        compose.onNodeWithTag("save_answer_prompt").performScrollTo().assertIsNotEnabled()
        val field = compose.onNodeWithTag("answer_prompt")
        field.performScrollTo().performTextReplacement("字".repeat(AnswerPromptConfig.MAX_LENGTH + 1))
        compose.onNodeWithTag("save_answer_prompt").performScrollTo().assertIsNotEnabled()

        val preference = "用英文作答\n保留关键算式"
        field.performScrollTo().performTextReplacement(preference)
        compose.onNodeWithTag("save_answer_prompt").performScrollTo().assertIsEnabled().performClick()
        awaitSettings { model.state.value.savedPromptText == preference && !model.state.value.promptSaving }
        assertEquals(preference, AnswerPromptConfig.read(file).text())

        compose.onNodeWithTag("clear_answer_prompt").performScrollTo().performClick()
        // Robolectric's AtomicFile delegates to host File.renameTo, which cannot replace a file on Windows.
        if (File.separatorChar == '\\') assertTrue(file.delete())
        compose.onNodeWithTag("save_answer_prompt").assertIsEnabled().performClick()
        awaitSettings { model.state.value.savedPromptText.isEmpty() && !model.state.value.promptSaving }
        assertEquals(AnswerPromptConfig.DEFAULT, AnswerPromptConfig.read(file))
    }
    private fun render(name: String) {
        val directory = System.getProperty("ansit.renderDir") ?: return
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
