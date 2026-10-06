package com.nopeek.app.data.repositories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveNextStatusTest {

    @Test
    fun onChainStatusMapsToLocalLabels() {
        assertEquals("CREATED", resolveNextStatus("ACTIVE", 0))
        assertEquals("ACTIVE", resolveNextStatus("CREATED", 1))
        assertEquals("SETTLED", resolveNextStatus("ACTIVE", 2))
        assertEquals("REFUNDED", resolveNextStatus("CREATED", 3))
        assertEquals("BOTH_FAIL_LOCKED", resolveNextStatus("ACTIVE", 4))
    }

    @Test
    fun unchangedStatus_returnsNull() {
        assertNull(resolveNextStatus("CREATED", 0))
        assertNull(resolveNextStatus("ACTIVE", 1))
        assertNull(resolveNextStatus("SETTLED", 2))
    }

    @Test
    fun unknownOnChainStatus_returnsNull() {
        assertNull(resolveNextStatus("ACTIVE", 99))
        assertNull(resolveNextStatus("ACTIVE", -1))
    }
}
