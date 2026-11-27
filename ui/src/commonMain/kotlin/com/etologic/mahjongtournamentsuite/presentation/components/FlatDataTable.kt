package com.etologic.mahjongtournamentsuite.presentation.components
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.actions
import org.jetbrains.compose.resources.stringResource

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

/** Table column titles are always bold and centered in their column, in every table. */
@Composable
fun dataTableHeaderStyle(base: TextStyle = MaterialTheme.typography.labelLarge): TextStyle =
    base.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

/** One column title of a table: bold and centered in its column. */
@Composable
fun DataTableHeaderText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = modifier,
        style = dataTableHeaderStyle(style),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun DataTableRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    clickFocusable: Boolean = true,
    highlighted: Boolean = false,
    backgroundColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    // The focus lights the row only while the keyboard is in use: with the mouse only the hover shows.
    val keyboard = keyboardInUse()
    val resolvedBackgroundColor = if (isHovered || (isFocused && keyboard) || highlighted) hoverColor else backgroundColor

    val interactiveModifier = if (onClick == null) {
        Modifier.hoverable(interactionSource = interactionSource)
    } else {
        Modifier
            .hoverable(interactionSource = interactionSource)
            .then(if (clickFocusable) Modifier.focusOnMousePress() else Modifier)
            .focusProperties { canFocus = clickFocusable }
            .onPreviewKeyEvent { event ->
                if (!clickFocusable || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(resolvedBackgroundColor)
            .then(interactiveModifier)
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
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
    buttonIcon: ImageVector = Icons.Default.MoreVert,
    buttonIconSize: Dp = 24.dp,
    buttonContentColor: Color? = null,
    focusRequester: FocusRequester? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val defaultButtonColors = MtsButtons.textColors()

    FocusedTextButton(
        enabled = enabled,
        onClick = { expanded = true },
        modifier = modifier,
        focusRequester = focusRequester,
        colors = buttonContentColor?.let { ButtonDefaults.textButtonColors(contentColor = it) } ?: defaultButtonColors,
    ) {
        Icon(
            imageVector = buttonIcon,
            contentDescription = stringResource(Res.string.actions),
            modifier = Modifier.size(buttonIconSize),
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

@Preview(device = Devices.DESKTOP)
@Composable
private fun FlatDataTablePreview() {
    MtsTheme(useDarkTheme = false) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            DataTableHeaderRow {
                DataTableHeaderText(text = "Column A", modifier = Modifier.weight(1f))
                DataTableHeaderText(text = "Column B", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            DataTableDivider()
            DataTableRow {
                Text(
                    text = "Row A",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Row B",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                RowActionsMenu(
                    enabled = true,
                    items = listOf(
                        RowActionMenuItem("Open", onClick = {}),
                        RowActionMenuItem("Delete", onClick = {}),
                    ),
                    modifier = Modifier.width(44.dp),
                )
            }
            DataTableDivider()
        }
    }
}
