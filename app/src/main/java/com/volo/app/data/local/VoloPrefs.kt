package com.volo.app.data.local

import android.content.Context

class VoloPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("volo_prefs", Context.MODE_PRIVATE)

    var seenOnboarding: Boolean
        get() = prefs.getBoolean("seen_onboarding_v1", false)
        set(value) = prefs.edit().putBoolean("seen_onboarding_v1", value).apply()

    var knownUser: Boolean
        get() = prefs.getBoolean("known_user_v1", false)
        set(value) = prefs.edit().putBoolean("known_user_v1", value).apply()

    val routedBefore: Boolean
        get() = seenOnboarding || knownUser
}
