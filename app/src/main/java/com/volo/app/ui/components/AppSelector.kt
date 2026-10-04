package com.volo.app.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

data class InstalledApp(val packageName: String, val label: String)

fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun AppSelector(selectedPackage: String?, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember { loadInstalledApps(context) }
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = apps.firstOrNull { it.packageName == selectedPackage }?.label
        ?: if (selectedPackage != null) selectedPackage else "Choose an app"

    Box {
        FieldCard(label = "TARGET APP", value = selectedLabel, placeholder = selectedPackage == null) {
            expanded = true
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = VoloColors.surfaceRaised,
            shape = RoundedCornerShape(VoloDimens.radiusCard),
            border = BorderStroke(1.dp, VoloColors.line),
        ) {
            Column(Modifier.heightIn(max = 320.dp)) {
                if (apps.isEmpty()) {
                    Text(
                        "No third-party apps found",
                        style = VoloType.bodySmall,
                        color = VoloColors.textMuted,
                        modifier = Modifier.padding(VoloDimens.spaceLg),
                    )
                }
                apps.forEach { app ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                app.label,
                                style = VoloType.body,
                                color = VoloColors.text,
                            )
                        },
                        onClick = {
                            onSelected(app.packageName)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun FieldCard(label: String, value: String, placeholder: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VoloDimens.radiusControl),
        color = VoloColors.surfaceRaised,
        contentColor = VoloColors.text,
        border = BorderStroke(1.dp, VoloColors.line),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(
            modifier = Modifier.padding(VoloDimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
        ) {
            Text(
                label.uppercase(),
                style = VoloType.label,
                color = VoloColors.textMuted,
            )
            Text(
                value,
                style = VoloType.titleSmall,
                color = if (placeholder) VoloColors.textMuted else VoloColors.text,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun FieldCardPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(VoloDimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            FieldCard(label = "TARGET APP", value = "Instagram", onClick = {})
            FieldCard(label = "TARGET APP", value = "Choose an app", placeholder = true, onClick = {})
        }
    }
}
