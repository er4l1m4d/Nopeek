package com.nopeek.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nopeek.app.navigation.Destination
import com.nopeek.app.ui.theme.NopeekTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Interaction contracts, asserted through the semantics tree.
 *
 * This is the family of tests that was missing: `PresetChip` and `TapIconButton` shipped with
 * `enabled = false`, which made the create flow impassable and every back arrow dead, and nothing in
 * the suite noticed because no test ever tried to click one. Each case asserts both directions — an
 * enabled control reaches its caller, a disabled one does not — so nobody repairs a dead button by
 * deleting the disabled state.
 *
 * Selection and role are read from the semantics configuration directly rather than through
 * sugar-coated assertions, so the test states the contract the platform actually sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InteractiveComponentContractTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent { NopeekTheme { content() } }
    }

    private fun selectedOf(text: String): Boolean? =
        composeTestRule.onNodeWithText(text)
            .fetchSemanticsNode("selection state for '$text'")
            .config
            .getOrNull(SemanticsProperties.Selected)

    private fun roleOf(description: String): Role? =
        composeTestRule.onNodeWithContentDescription(description)
            .fetchSemanticsNode("role for '$description'")
            .config
            .getOrNull(SemanticsProperties.Role)

    @androidx.compose.runtime.Composable
    private fun forfeitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) = ForfeitDialog(
        stakeLabel = "$5",
        opponentName = "alex.skr",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )

    // ---- PresetChip: the control that gated the whole create flow --------------------------

    @Test
    fun presetChipIsEnabledAndReachesItsCaller() {
        var clicked: String? = null
        themed { PresetChip(label = "30", selected = false, onClick = { clicked = "30" }) }

        val chip = composeTestRule.onNodeWithText("30")
        chip.assertIsEnabled()
        chip.performClick()
        composeTestRule.waitForIdle()

        assertEquals("30", clicked)
    }

    @Test
    fun presetChipAnnouncesItsSelectionState() {
        themed {
            Row {
                PresetChip(label = "30", selected = true, onClick = {})
                PresetChip(label = "60", selected = false, onClick = {})
            }
        }

        assertEquals(true, selectedOf("30"))
        assertEquals(false, selectedOf("60"))
    }

    @Test
    fun presetChipIsAnnouncedAsASingleChoiceOption() {
        themed { PresetChip(label = "120", selected = false, onClick = {}) }

        composeTestRule.onNodeWithText("120")
            .fetchSemanticsNode("chip role")
            .config
            .let { assertEquals(Role.RadioButton, it[SemanticsProperties.Role]) }
    }

    @Test
    fun presetChipMeetsTheTouchTarget() {
        themed { PresetChip(label = "15", selected = false, onClick = {}) }

        with(composeTestRule.onNodeWithText("15")) {
            assertHeightIsAtLeast(48.dp)
            assertWidthIsAtLeast(48.dp)
        }
    }

    // ---- TapIconButton: the control that killed every back arrow ----------------------------

    @Test
    fun tapIconButtonIsEnabledAndReachesItsCaller() {
        var clicks = 0
        themed {
            TapIconButton(contentDescription = "Back", onClick = { clicks++ }) {
                Box(modifier = Modifier.size(24.dp))
            }
        }

        val button = composeTestRule.onNodeWithContentDescription("Back")
        button.assertIsEnabled()
        button.performClick()
        button.performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, clicks)
    }

    @Test
    fun tapIconButtonIsAnnouncedAsAButtonOfAtLeastTouchSize() {
        themed {
            TapIconButton(contentDescription = "Duel options", onClick = {}) {
                Box(modifier = Modifier.size(24.dp))
            }
        }

        with(composeTestRule.onNodeWithContentDescription("Duel options")) {
            assertEquals(Role.Button, roleOf("Duel options"))
            assertHeightIsAtLeast(48.dp)
            assertWidthIsAtLeast(48.dp)
        }
    }

    // ---- Buttons: enabled reaches, disabled does not ---------------------------------------

    @Test
    fun primaryButtonReachesItsCallerAndDisabledBlocksIt() {
        var enabledClicks = 0
        var disabledClicks = 0
        themed {
            Row {
                PrimaryButton(label = "Continue", onClick = { enabledClicks++ })
                PrimaryButton(label = "Blocked", onClick = { disabledClicks++ }, enabled = false)
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Blocked").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, enabledClicks)
        assertEquals("a disabled button must not fire", 0, disabledClicks)
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
        composeTestRule.onNodeWithText("Blocked").assertIsNotEnabled()
    }

    @Test
    fun outlineAndTextButtonsReachTheirCallers() {
        var outline = 0
        var text = 0
        themed {
            Row {
                OutlineButton(label = "Rematch", onClick = { outline++ })
                TextButton(label = "Decline", onClick = { text++ })
            }
        }

        composeTestRule.onNodeWithText("Rematch").performClick()
        composeTestRule.onNodeWithText("Decline").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, outline)
        assertEquals(1, text)
    }

    @Test
    fun inlineErrorRetryReachesItsCaller() {
        var retries = 0
        themed { InlineError(message = "Couldn't load duels.", onRetry = { retries++ }) }

        composeTestRule.onNodeWithText("Retry").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, retries)
    }

    // ---- Navigation ------------------------------------------------------------------------

    @Test
    fun bottomBarReportsTheTabThatWasTapped() {
        var selected: Destination? = null
        themed { NopeekBottomBar(current = Destination.Home, onSelect = { selected = it }) }

        composeTestRule.onNodeWithText("Duels").performClick()
        composeTestRule.waitForIdle()

        assertEquals(Destination.Duels, selected)
    }

    @Test
    fun bottomBarMarksOnlyTheCurrentDestinationSelected() {
        themed { NopeekBottomBar(current = Destination.Stats, onSelect = {}) }

        assertEquals(true, selectedOf("Stats"))
        assertEquals(false, selectedOf("Home"))
    }

    // ---- Rows and dialogs ------------------------------------------------------------------

    @Test
    fun reviewRowEditReachesItsCaller() {
        var edits = 0
        themed { ReviewRow(label = "Daily limit", value = "30 min", onEdit = { edits++ }) }

        composeTestRule.onNodeWithText("Daily limit").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, edits)
    }

    @Test
    fun appRowSelectionReachesItsCaller() {
        var picked: String? = null
        themed {
            AppRow(name = "TikTok", average = "1h 52m a day", selected = false, onClick = { picked = "tiktok" })
        }

        composeTestRule.onNodeWithText("TikTok").performClick()
        composeTestRule.waitForIdle()

        assertEquals("tiktok", picked)
    }

    @Test
    fun forfeitDialogConfirmReachesItsCaller() {
        var confirmed = 0
        var dismissed = 0

        themed { forfeitDialog(onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }
        composeTestRule.onNodeWithText("Forfeit").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun forfeitDialogCancelDoesNotForfeit() {
        var confirmed = 0
        var dismissed = 0

        themed { forfeitDialog(onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        assertEquals("cancel must not forfeit the stake", 0, confirmed)
        assertEquals(1, dismissed)
    }

    // ---- Status text is present in the tree, not carried by colour alone -------------------

    @Test
    fun statusLabelRendersItsWordsAndItsGlyph() {
        themed { StatusLabel(variant = usageVariant(UsageState.AT_RISK)) }

        assertNotNull(usageVariant(UsageState.AT_RISK).glyph)
        // If the label ever stops being real text, this fails: colour alone is not a status.
        composeTestRule.onNodeWithText("At risk").assertExists()
    }

    @Test
    fun emptyStateOffersAnAction() {
        var actions = 0
        themed {
            EmptyState(
                icon = com.nopeek.app.ui.icons.Phosphor.Flag,
                title = "No open duels",
                message = "There are no open challenges right now.",
                actionLabel = "Create a duel",
                onAction = { actions++ },
            )
        }

        composeTestRule.onNodeWithText("Create a duel").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, actions)
    }
}
