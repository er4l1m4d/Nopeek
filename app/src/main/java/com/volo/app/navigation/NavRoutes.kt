package com.volo.app.navigation

/**
 * Route definitions for the Volo NavHost.
 *
 * Baseline routes from the plan (Phase 1.2):
 *  - home
 *  - create_challenge
 *  - active_challenge/{pubkey}
 *  - profile
 *
 * Extended with the UI spec's five-tab shell (Home, Challenges, Create,
 * Activity, Profile) as additional routes so the approved bottom-bar
 * navigation and visual design stay intact. The core four plan routes
 * remain the canonical entry points consumed by later phases.
 */
object NavRoutes {
    const val HOME = "home"
    const val CHALLENGES = "challenges"
    const val CREATE_CHALLENGE = "create_challenge"
    const val ACTIVE_CHALLENGE = "active_challenge/{pubkey}"
    const val RECOVERY_LOCK = "recovery_lock/{pubkey}"
    const val ACTIVITY = "activity"
    const val PROFILE = "profile"
    const val OPEN_DUELS = "open_duels"

    fun activeChallenge(pubkey: String) = "active_challenge/$pubkey"

    fun recoveryLock(pubkey: String) = "recovery_lock/$pubkey"

    fun All(): List<String> = listOf(HOME, CREATE_CHALLENGE, ACTIVE_CHALLENGE, PROFILE)
}
