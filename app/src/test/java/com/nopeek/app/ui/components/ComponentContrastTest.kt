package com.nopeek.app.ui.components

import androidx.compose.ui.graphics.Color
import com.nopeek.app.ui.AA_LARGE_TEXT
import com.nopeek.app.ui.AA_NON_TEXT
import com.nopeek.app.ui.AA_TEXT
import com.nopeek.app.ui.DESIGN_SYSTEM_TEXT
import com.nopeek.app.ui.composited
import com.nopeek.app.ui.contrastMessage
import com.nopeek.app.ui.contrastOf
import com.nopeek.app.ui.theme.NopeekDarkColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate that should have existed before the audit. It asserts the colour pairs components
 * actually paint, on the surfaces they actually sit on — not the palette in the abstract, which is
 * what [com.nopeek.app.ui.theme.NopeekTokensTest] already covers and what stayed green while every
 * status pill rendered Pitch text on a Pitch background at 1:1.
 *
 * Floors: the design system's own 5.5 for text, WCAG AA 4.5 where a translucent panel is part of
 * the treatment, 3.0 for disabled controls and for non-text cues that carry state.
 */
class ComponentContrastTest {

    private val s = NopeekDarkColors

    private data class TextPair(val what: String, val fg: Color, val bg: Color, val floor: Double)

    private data class NonTextPair(val what: String, val fg: Color, val bg: Color)

    private val gatedTextPairs = listOf(
        // Body and heading text on each surface tier.
        TextPair("onSurface on background", s.onSurface, s.background, DESIGN_SYSTEM_TEXT),
        TextPair("onSurface on surfaceRaised", s.onSurface, s.surfaceRaised, DESIGN_SYSTEM_TEXT),
        TextPair("onSurface on surfaceHigh (AppTile, quiet chip)", s.onSurface, s.surfaceHigh, DESIGN_SYSTEM_TEXT),
        TextPair("onSurfaceMuted on background", s.onSurfaceMuted, s.background, DESIGN_SYSTEM_TEXT),
        TextPair("onSurfaceMuted on surfaceRaised", s.onSurfaceMuted, s.surfaceRaised, DESIGN_SYSTEM_TEXT),
        // Buttons.
        TextPair("PrimaryButton label", s.onPrimary, s.primary, DESIGN_SYSTEM_TEXT),
        TextPair("OutlineButton label", s.onSurface, s.background, DESIGN_SYSTEM_TEXT),
        TextPair("TextButton label", s.onSurfaceMuted, s.background, DESIGN_SYSTEM_TEXT),
        TextPair("PresetChip unselected", s.onSurface, s.surfaceHigh, DESIGN_SYSTEM_TEXT),
        TextPair("PresetChip selected", s.onPrimary, s.you, DESIGN_SYSTEM_TEXT),
        TextPair("PrimaryButton disabled (inert, exempt from AA text)", s.onSurfaceMuted, s.surfaceHigh, AA_NON_TEXT),
        TextPair("TextButton disabled (inert, exempt from AA text)", composited(s.onSurfaceMuted.copy(alpha = 0.6f), s.background), s.background, AA_NON_TEXT),
        // Bottom navigation.
        TextPair("selected nav pill", s.onPrimary, s.primary, DESIGN_SYSTEM_TEXT),
        TextPair("unselected nav label", s.onSurfaceMuted, s.surfaceRaised, DESIGN_SYSTEM_TEXT),
        // Scoreboard / duel surfaces.
        TextPair("your clock on surfaceRaised", s.you, s.surfaceRaised, DESIGN_SYSTEM_TEXT),
        TextPair("opponent clock on surfaceRaised", s.opponent, s.surfaceRaised, DESIGN_SYSTEM_TEXT),
        TextPair("muted caption on surfaceRaised", s.onSurfaceMuted, s.surfaceRaised, DESIGN_SYSTEM_TEXT),
        // InlineError paints danger over a 12% danger tint; the tint is part of the treatment, so
        // the pair is judged as composited and held to plain AA rather than the 5.5 house bar.
        TextPair(
            "InlineError text on its tinted panel",
            s.danger,
            composited(s.danger.copy(alpha = 0.12f), s.background),
            AA_TEXT,
        ),
        // Full-bleed moment surfaces.
        TextPair("onWin on win", s.onWin, s.win, DESIGN_SYSTEM_TEXT),
        TextPair("onWinMuted on win", s.onWinMuted, s.win, DESIGN_SYSTEM_TEXT),
        TextPair("onLose on lose", s.onLose, s.lose, DESIGN_SYSTEM_TEXT),
        TextPair("onWarning on warning (recovery lock)", s.onWarning, s.warning, DESIGN_SYSTEM_TEXT),
        // onLoseMuted composites to 3.61:1 over coral: legal for display-size text only. It is
        // unused today; this floor is the guard rail, not an endorsement for BodyMd.
        TextPair("onLoseMuted on lose (large text only)", composited(s.onLoseMuted, s.lose), s.lose, AA_LARGE_TEXT),
    )

    private val stateCarryingNonTextCues = listOf(
        NonTextPair("selected nav pill vs bar", s.primary, s.surfaceRaised),
        NonTextPair("your tape marker vs track", s.you, s.surfaceHigh),
        NonTextPair("opponent tape marker vs track", s.opponent, s.surfaceHigh),
        NonTextPair("live status pill vs background", s.you, s.background),
        NonTextPair("warning status pill vs background", s.warning, s.background),
        NonTextPair("danger status pill vs background", s.danger, s.background),
        NonTextPair("selected chip vs background", s.you, s.background),
    )

    @Test
    fun everyRenderedTextPairClearsItsFloor() {
        gatedTextPairs.forEach { pair ->
            val ratio = contrastOf(pair.fg, pair.bg)
            assertTrue(contrastMessage(pair.what, ratio, pair.floor), ratio >= pair.floor)
        }
    }

    @Test
    fun everyStateCarryingNonTextCueClearsThreeToOne() {
        stateCarryingNonTextCues.forEach { pair ->
            val ratio = contrastOf(pair.fg, pair.bg)
            assertTrue(contrastMessage(pair.what, ratio, AA_NON_TEXT), ratio >= AA_NON_TEXT)
        }
    }

    /**
     * Ratchet, not approval. These hairlines and tracks sit below 3:1 today (audit AX-03, UX-17)
     * because they are decorative — the fill, the tick or the word carries the meaning. If this test
     * fails, someone raised one of them: promote it into [stateCarryingNonTextCues] instead of
     * lowering the bar.
     */
    @Test
    fun decorativeHairlinesAndTracksStayDecorative() {
        val decorative = listOf(
            NonTextPair("outline hairline on background", s.outline, s.background),
            NonTextPair("outline hairline on surfaceRaised", s.outline, s.surfaceRaised),
            NonTextPair("tape track on background", s.surfaceHigh, s.background),
        )
        decorative.forEach { pair ->
            val ratio = contrastOf(pair.fg, pair.bg)
            assertTrue(
                "'${pair.what}' is now ${"%.2f".format(ratio)}:1 — it stopped being decorative. " +
                    "Move it into the gated non-text list.",
                ratio < AA_NON_TEXT,
            )
        }
    }
}
