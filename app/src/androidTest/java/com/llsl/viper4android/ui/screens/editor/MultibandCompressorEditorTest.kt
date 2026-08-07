package com.llsl.viper4android.ui.screens.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.llsl.viper4android.R
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.ui.theme.ViperTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultibandCompressorEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultAutoStateExposesFourCrossoversAndLocksRatioAndKnee() {
        composeRule.setContent {
            ViperTheme(dynamicColor = false) {
                MultibandCompressorEditor(
                    state = EffectState(),
                    sampleRate = 48_000,
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        repeat(4) { index ->
            composeRule.onNodeWithTag("graph-handle-crossover-$index").assertExists()
        }
        composeRule.onNodeWithTag("graph-handle-threshold").assertExists()
        composeRule.onNodeWithTag("graph-handle-ratio").assertIsNotEnabled()
        composeRule.onNodeWithTag("graph-handle-knee").assertIsNotEnabled()
        composeRule.onNodeWithTag("graph-handle-crossover-4").assertDoesNotExist()
    }

    @Test
    fun bandFiveAndAdvancedControlsRemainExplicitlyReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            ViperTheme(dynamicColor = false) {
                MultibandCompressorEditor(
                    state = EffectState(),
                    sampleRate = 48_000,
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.editor_band_number, 5)).performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.editor_graph_multiband_transfer_title, 5))
            .assertExists()
        composeRule.onNodeWithTag("multiband-advanced-header").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("multiband-advanced-content").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.label_fet_knee_multi)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.label_fet_no_clip)).assertExists()
    }
}
