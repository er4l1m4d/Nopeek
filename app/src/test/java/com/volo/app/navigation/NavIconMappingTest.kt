package com.volo.app.navigation

import com.volo.app.ui.components.navIcon
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test

class NavIconMappingTest {

    @Test
    fun everyDestinationHasIcons() = Destination.entries.forEach { d ->
        assertNotNull(navIcon(d, false))
        assertNotNull(navIcon(d, true))
    }

    @Test
    fun selectedUsesFillVariant() = Destination.entries.forEach { d ->
        assertNotSame(navIcon(d, false), navIcon(d, true))
    }

    @Test
    fun destinationsAreDistinctInRegularSet() {
        val regulars = Destination.entries.map { navIcon(it, false) }
        for (i in regulars.indices) {
            for (j in i + 1 until regulars.size) {
                assertNotSame(
                    "regular icons for ${Destination.entries[i]} and ${Destination.entries[j]} must differ",
                    regulars[i],
                    regulars[j],
                )
            }
        }
    }
}
