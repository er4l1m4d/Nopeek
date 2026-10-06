package com.nopeek.app.navigation

object NavRoutes {
    const val SPLASH = "splash"
    const val CHOICE = "choice"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DUELS = "duels"
    const val STATS = "stats"
    const val OPEN_DUELS = "open_duels"
    const val PROFILE = "profile"
    const val CREATE = "create"
    const val DUEL_PATTERN = "active_challenge/{pubkey}"
    const val ACTIVE_CHALLENGE = "active_challenge/{pubkey}"
    const val RECOVERY_LOCK = "recovery_lock/{pubkey}"
    const val CREATE_CHALLENGE = "create_challenge"

    fun activeChallenge(pk: String) = "active_challenge/$pk"
    fun createChallenge() = "create_challenge"

    fun recoveryLock(pk: String) = "recovery_lock/$pk"

    fun All(): List<String> = listOf("home", "create_challenge", "active_challenge/{pubkey}", "profile")
}

enum class Destination(val label: String, val route: String) {
    Home("Home", "home"),
    Duels("Duels", "duels"),
    Stats("Stats", "stats"),
    Profile("Profile", "profile"),
}