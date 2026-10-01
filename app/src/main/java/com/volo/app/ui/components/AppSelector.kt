package com.volo.app.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted

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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.heightIn(max = 320.dp)) {
                if (apps.isEmpty()) {
                    Text("No third-party apps found", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
                apps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label, fontSize = 14.sp) },
                        onClick = {
                            onSelected(app.packageName)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FieldCard(label: String, value: String, placeholder: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        Text(label.uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(
            value,
            color = if (placeholder) Muted else Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
