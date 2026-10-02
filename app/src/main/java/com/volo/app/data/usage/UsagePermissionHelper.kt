package com.volo.app.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

enum class UsagePermissionState {
    GRANTED,
    NEEDS_PERMISSION,
    UNAVAILABLE;

    fun label(): String = when (this) {
        GRANTED -> "Enabled"
        NEEDS_PERMISSION -> "Not enabled — tap to grant"
        UNAVAILABLE -> "Unavailable on this device"
    }
}

/**
 * Pure mapping from an AppOpsManager mode to a UI-safe permission state.
 * Split out so it is testable on the JVM without Android classes.
 */
fun usagePermissionStateForMode(mode: Int): UsagePermissionState = when (mode) {
    0 /* MODE_ALLOWED */, 4 /* MODE_FOREGROUND */ -> UsagePermissionState.GRANTED
    1 /* MODE_IGNORED */, 3 /* MODE_DEFAULT */ -> UsagePermissionState.NEEDS_PERMISSION
    2 /* MODE_ERRORED */ -> UsagePermissionState.UNAVAILABLE
    else -> UsagePermissionState.UNAVAILABLE
}

object UsagePermissionHelper {

    fun state(context: Context): UsagePermissionState {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            usagePermissionStateForMode(mode)
        } catch (e: Exception) {
            UsagePermissionState.UNAVAILABLE
        }
    }

    fun isGranted(context: Context): Boolean = state(context) == UsagePermissionState.GRANTED

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
