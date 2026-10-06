package com.nopeek.app.ui.components

import com.nopeek.app.ui.theme.NopeekColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTest {

    @Test
    fun lifecycleLabelsAndColorsMatchSpec() {
        assertEquals(StatusVariant("ACTIVE", NopeekColors.safe, NopeekColors.bg), lifecycleVariant("ACTIVE"))
        assertEquals(StatusVariant("PENDING", NopeekColors.copper, NopeekColors.bg), lifecycleVariant("CREATED"))
        assertEquals(StatusVariant("VERIFY", NopeekColors.risk, NopeekColors.bg), lifecycleVariant("PENDING_VERIFICATION"))
        assertEquals(StatusVariant("LOCKED", NopeekColors.fail, NopeekColors.bg), lifecycleVariant("BOTH_FAIL_LOCKED"))
        assertEquals(StatusVariant("SETTLED", NopeekColors.surfaceHigh, NopeekColors.textMuted), lifecycleVariant("SETTLED"))
        assertEquals(StatusVariant("REFUNDED", NopeekColors.surfaceHigh, NopeekColors.textMuted), lifecycleVariant("REFUNDED"))
        assertEquals(lifecycleVariant("SOMETHING_NEW").label, "SOMETHING_NEW") // passthrough, neutral colors
        assertEquals(NopeekColors.surfaceHigh, lifecycleVariant("SOMETHING_NEW").container)
    }

    @Test
    fun usageVariantsMatchSpec() {
        assertEquals(StatusVariant("PASSING", NopeekColors.safe, NopeekColors.bg), usageVariant(UsageState.PASSING))
        assertEquals(StatusVariant("AT RISK", NopeekColors.risk, NopeekColors.bg), usageVariant(UsageState.AT_RISK))
        assertEquals(StatusVariant("FAILED", NopeekColors.fail, NopeekColors.bg), usageVariant(UsageState.FAILED))
        assertEquals(StatusVariant("UNVERIFIED", NopeekColors.surfaceHigh, NopeekColors.textMuted), usageVariant(UsageState.UNVERIFIED))
    }

    @Test
    fun noVariantIsColorOnly() {
        UsageState.entries.forEach { assertTrue(usageVariant(it).label.isNotBlank()) }
        listOf("CREATED", "ACTIVE", "PENDING_VERIFICATION", "BOTH_FAIL_LOCKED", "SETTLED", "REFUNDED", "x")
            .forEach { assertTrue(lifecycleVariant(it).label.isNotBlank()) }
    }
}
