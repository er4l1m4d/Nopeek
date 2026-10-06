package com.volo.app.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.volo.app.ui.theme.VoloTheme
import java.util.Locale

data class InstalledApp(val packageName: String, val label: String)

fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase(Locale.ROOT) }
}

fun loadAppIcon(context: Context, packageName: String): Painter? {
    return try {
        val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
        BitmapPainter(drawable.toBitmap().asImageBitmap())
    } catch (e: Exception) {
        null
    }
}

@Composable
fun InstalledAppList(
    apps: List<InstalledApp>,
    selectedPackage: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        apps.forEach { app ->
            val icon = remember(app.packageName) { loadAppIcon(context, app.packageName) }
            AppRow(
                name = app.label,
                average = "",
                selected = app.packageName == selectedPackage,
                onClick = { onSelected(app.packageName) },
                icon = icon,
                fallback = app.label.take(2).uppercase(Locale.ROOT),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun InstalledAppListPreview() {
    VoloTheme {
        val apps = listOf(
            InstalledApp("com.zhiliaoapp.musically", "TikTok"),
            InstalledApp("com.instagram.android", "Instagram"),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            apps.forEach { app ->
                AppRow(
                    name = app.label,
                    average = if (app.label == "TikTok") "1h 52m a day on average" else "1h 14m a day on average",
                    selected = app.label == "TikTok",
                    onClick = {},
                    fallback = app.label.take(2).uppercase(Locale.ROOT),
                )
            }
        }
    }
}
