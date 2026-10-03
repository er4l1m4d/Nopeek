package com.volo.app.ui.components

import com.volo.app.ui.theme.VoloColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTest {

    @Test
    fun lifecycleLabelsAndColorsMatchSpec() {
        assertEquals(StatusVariant("ACTIVE", VoloColors.safe, VoloColors.bg), lifecycleVariant("ACTIVE"))
        assertEquals(StatusVariant("PENDING", VoloColors.copper, VoloColors.bg), lifecycleVariant("CREATED"))
        assertEquals(StatusVariant("VERIFY", VoloColors.risk, VoloColors.bg), lifecycleVariant("PENDING_VERIFICATION"))
        assertEquals(StatusVariant("LOCKED", VoloColors.fail, VoloColors.bg), lifecycleVariant("BOTH_FAIL_LOCKED"))
        assertEquals(StatusVariant("SETTLED", VoloColors.surfaceHigh, VoloColors.textMuted), lifecycleVariant("SETTLED"))
        assertEquals(StatusVariant("REFUNDED", VoloColors.surfaceHigh, VoloColors.textMuted), lifecycleVariant("REFUNDED"))
        assertEquals(lifecycleVariant("SOMETHING_NEW").label, "SOMETHING_NEW") // passthrough, neutral colors
        assertEquals(VoloColors.surfaceHigh, lifecycleVariant("SOMETHING_NEW").container)
    }

    @Test
    fun usageVariantsMatchSpec() {
        assertEquals(StatusVariant("PASSING", VoloColors.safe, VoloColors.bg), usageVariant(UsageState.PASSING))
        assertEquals(StatusVariant("AT RISK", VoloColors.risk, VoloColors.bg), usageVariant(UsageState.AT_RISK))
        assertEquals(StatusVariant("FAILED", VoloColors.fail, VoloColors.bg), usageVariant(UsageState.FAILED))
        assertEquals(StatusVariant("UNVERIFIED", VoloColors.surfaceHigh, VoloColors.textMuted), usageVariant(UsageState.UNVERIFIED))
    }

    @Test
    fun noVariantIsColorOnly() {
        UsageState.entries.forEach { assertTrue(usageVariant(it).label.isNotBlank()) }
        listOf("CREATED", "ACTIVE", "PENDING_VERIFICATION", "BOTH_FAIL_LOCKED", "SETTLED", "REFUNDED", "x")
            .forEach { assertTrue(lifecycleVariant(it).label.isNotBlank()) }
    }
}
