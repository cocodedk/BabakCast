package com.cocode.babakcast.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cocode.babakcast.domain.split.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SplitChoiceDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun splitChoiceDialog_showsPromptWithChapterCount() {
        composeRule.setContent {
            MaterialTheme {
                SplitChoiceDialog(
                    prompt = SplitChoicePrompt(
                        mediaType = SplitChoiceMediaType.VIDEO,
                        chapterCount = 3
                    ),
                    onChoose = {}
                )
            }
        }

        composeRule.onNodeWithText("Choose split mode").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This video source includes 3 chapters. Split by chapters or keep standard 16 MB chunks?"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Split by chapters").assertIsDisplayed()
        composeRule.onNodeWithText("Use 16 MB chunks").assertIsDisplayed()
    }

    @Test
    fun splitChoiceDialog_confirmChoosesChaptersMode() {
        var chosen: SplitMode? = null

        composeRule.setContent {
            MaterialTheme {
                SplitChoiceDialog(
                    prompt = SplitChoicePrompt(
                        mediaType = SplitChoiceMediaType.AUDIO,
                        chapterCount = 5
                    ),
                    onChoose = { mode -> chosen = mode }
                )
            }
        }

        composeRule.onNodeWithText("Split by chapters").performClick()
        assertEquals(SplitMode.CHAPTERS, chosen)
    }

    @Test
    fun splitChoiceDialog_dismissChooses16mbMode() {
        var chosen: SplitMode? = null

        composeRule.setContent {
            MaterialTheme {
                SplitChoiceDialog(
                    prompt = SplitChoicePrompt(
                        mediaType = SplitChoiceMediaType.AUDIO,
                        chapterCount = 5
                    ),
                    onChoose = { mode -> chosen = mode }
                )
            }
        }

        composeRule.onNodeWithText("Use 16 MB chunks").performClick()
        assertEquals(SplitMode.SIZE_16MB, chosen)
    }
}
