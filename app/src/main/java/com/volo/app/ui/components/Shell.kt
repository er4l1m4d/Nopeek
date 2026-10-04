package com.volo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.navigation.Destination
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

// Bottom-nav icon mapping: unselected = Phosphor Regular, selected = Fill.
// Every destination maps to a distinct Regular icon and a different Fill
// object, so the mapping is covered by NavIconMappingTest (selected differs
// per destination; all five Regular icons are pairwise distinct).
// Internal because Destination is internal (NavGraph behavior unchanged;
// Task 16 rewires the bar itself). Same-module screens (T8-T15) consume it.
internal fun navIcon(item: Destination, selected: Boolean): ImageVector = when (item) {
    Destination.Home -> if (selected) Phosphor.HouseFill else Phosphor.House
    Destination.Challenges -> if (selected) Phosphor.FlagFill else Phosphor.Flag
    Destination.Activity -> if (selected) Phosphor.ClockCounterClockwiseFill else Phosphor.ClockCounterClockwise
    Destination.Profile -> if (selected) Phosphor.UserFill else Phosphor.User
    Destination.Create -> if (selected) Phosphor.PlusFill else Phosphor.Plus
}

@Composable
fun CreateAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        color = VoloColors.copper,
        contentColor = VoloColors.bg,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Phosphor.PlusBold,
                contentDescription = Destination.Create.label,
                tint = VoloColors.bg,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun RowScope.BottomTab(
    item: Destination,
    selected: Boolean,
    onSelect: (Destination) -> Unit,
) {
    val labelColor = if (selected) VoloColors.copper else VoloColors.textMuted
    Column(
        modifier = Modifier
            .weight(1f)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { this.selected = selected }
            .clickable(onClick = { onSelect(item) }, role = Role.Tab)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = navIcon(item, selected),
            // The Satoshi word label beside the glyph carries the accessible
            // name, so the icon itself stays decorative.
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = item.label,
            style = VoloType.label,
            color = labelColor,
        )
    }
}

@Composable
internal fun VoloBottomBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = VoloColors.surface,
        contentColor = VoloColors.text,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VoloColors.line),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomTab(
                    item = Destination.Home,
                    selected = current == Destination.Home,
                    onSelect = onSelect,
                )
                BottomTab(
                    item = Destination.Challenges,
                    selected = current == Destination.Challenges,
                    onSelect = onSelect,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CreateAction(onClick = { onSelect(Destination.Create) })
                }
                BottomTab(
                    item = Destination.Activity,
                    selected = current == Destination.Activity,
                    onSelect = onSelect,
                )
                BottomTab(
                    item = Destination.Profile,
                    selected = current == Destination.Profile,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
internal fun VoloScaffold(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = VoloColors.bg,
        contentColor = VoloColors.text,
        bottomBar = { VoloBottomBar(current = current, onSelect = onSelect) },
    ) { padding ->
        content(padding)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun VoloBottomBarPreview() {
    VoloTheme {
        VoloBottomBar(current = Destination.Home, onSelect = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun CreateActionPreview() {
    VoloTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CreateAction(onClick = {})
        }
    }
}
