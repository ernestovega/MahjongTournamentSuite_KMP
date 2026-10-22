package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TooltipScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The tooltip of both apps. The standard tooltip text is small (12 sp), so the text here uses the body size (14 sp). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipScope.AppPlainTooltip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PlainTooltip(modifier) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
    }
}
