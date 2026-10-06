package com.nopeek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.navigation.Destination
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekSize
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

fun navIcon(item: Destination, selected: Boolean): ImageVector = when (item) {
    Destination.Home -> if (selected) Phosphor.HouseFill else Phosphor.House
    Destination.Duels -> if (selected) Phosphor.FlagFill else Phosphor.Flag
    Destination.Stats -> if (selected) Phosphor.ClockCounterClockwiseFill else Phosphor.ClockCounterClockwise
    Destination.Profile -> if (selected) Phosphor.UserFill else Phosphor.User
}

@Composable
fun RowScope.BottomTab(item: Destination, selected: Boolean, onSelect: (Destination) -> Unit) {
    val colors = Nopeek.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .sizeIn(minWidth = NopeekSize.Touch, minHeight = NopeekSize.Touch)
            .clip(NopeekShape.Full)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = { onSelect(item) })
            .padding(top = NopeekSpace.S4),
        verticalArrangement = Arrangement.spacedBy(NopeekSpace.S4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .clip(NopeekShape.Full)
                .background(if (selected) colors.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = navIcon(item, selected),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (selected) colors.onPrimary else colors.onSurfaceMuted,
            )
        }
        Text(
            text = item.label,
            style = NopeekText.LabelSm,
            color = if (selected) colors.onSurface else colors.onSurfaceMuted,
        )
    }
}

@Composable
fun NopeekBottomBar(current: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Surface(modifier = modifier, color = colors.surfaceRaised, contentColor = colors.onSurface) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outline))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = NopeekSpace.S8, vertical = NopeekSpace.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destination.entries.forEach { item ->
                    BottomTab(item = item, selected = current == item, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
fun NopeekScaffold(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val colors = Nopeek.colors
    Scaffold(
        modifier = modifier,
        bottomBar = { NopeekBottomBar(current = current, onSelect = onSelect) },
        containerColor = colors.background,
        contentColor = colors.onSurface,
    ) { padding ->
        content(padding)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun NopeekBottomBarPreview() {
    NopeekTheme {
        NopeekBottomBar(current = Destination.Duels, onSelect = {})
    }
}
