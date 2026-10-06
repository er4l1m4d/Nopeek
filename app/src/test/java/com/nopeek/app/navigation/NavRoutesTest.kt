package com.nopeek.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavRoutesTest {

    @Test
    fun fourPlanRoutesAreDefined() {
        val routes = NavRoutes.All()
        assertTrue("home" in routes)
        assertTrue("create_challenge" in routes)
        assertTrue(routes.any { it.startsWith("active_challenge") })
        assertTrue("profile" in routes)
    }

    @Test
    fun activeChallengeRouteIncludesPubkeyPlaceholder() {
        val pattern = NavRoutes.ACTIVE_CHALLENGE
        assertTrue(pattern.contains("{pubkey}"))
    }

    @Test
    fun createChallengeRouteHasNoArgs() {
        val create = NavRoutes.CREATE_CHALLENGE
        assertEquals("create_challenge", create)
        assertTrue(!create.contains("{"))
    }
}
