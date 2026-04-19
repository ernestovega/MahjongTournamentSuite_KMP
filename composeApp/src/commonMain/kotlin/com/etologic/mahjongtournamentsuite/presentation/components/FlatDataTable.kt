package com.etologic.mahjongtournamentsuite.presentation.components
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun DataTableHeaderRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun DataTableRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val backgroundColor = if (isHovered) hoverColor else Color.Transparent

    val interactiveModifier = if (onClick == null) {
        Modifier.hoverable(interactionSource = interactionSource)
    } else {
        Modifier
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(interactiveModifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun DataTableDivider() {
    HorizontalDivider()
}

data class RowActionMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun RowActionsMenu(
    enabled: Boolean,
    items: List<RowActionMenuItem>,
    modifier: Modifier = Modifier,
    buttonLabel: String = "⋮",
    buttonTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    buttonContentColor: Color? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val defaultButtonColors = ButtonDefaults.textButtonColors()

    TextButton(
        enabled = enabled,
        onClick = { expanded = true },
        modifier = modifier,
        colors = buttonContentColor?.let { ButtonDefaults.textButtonColors(contentColor = it) } ?: defaultButtonColors,
    ) {
        Text(
            text = buttonLabel,
            style = buttonTextStyle,
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.label) },
                enabled = enabled && item.enabled,
                onClick = {
                    expanded = false
                    item.onClick()
                },
            )
        }
    }
}
