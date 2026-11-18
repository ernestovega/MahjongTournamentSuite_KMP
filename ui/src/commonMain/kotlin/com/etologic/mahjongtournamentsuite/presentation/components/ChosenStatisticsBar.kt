package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont

/** The height of the bar with the chosen player or tournament. */
private val ChosenBarHeight = 44.dp

/**
 * The bar with the chosen player or tournament of the statistics, and the button to choose another one.
 * It has the color of the tab rows and sits between them. The title never hides the button.
 */
@Composable
fun ChosenStatisticsBar(
    title: String,
    changeLabel: String,
    onChange: () -> Unit,
    maxWidth: Dp,
    /** Before the title, for example the flag of the player. */
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.widthIn(max = maxWidth).fillMaxWidth().height(ChosenBarHeight).background(mtsTabRowColor()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium.inTextFont(),
                fontWeight = FontWeight.Bold,
                color = mtsTabContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DebouncedOutlinedButton(
            onClick = onChange,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = mtsTabContentColor()),
            border = BorderStroke(1.dp, mtsTabContentColor()),
        ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(changeLabel, maxLines = 1, softWrap = false)
        }
    }
}

/** The bar of a chosen player: the flag and the name. A player of the EMA list without country is a guest: the flag is the globe. */
@Composable
fun ChosenPlayerBar(profile: PlayerProfile, changeLabel: String, onChange: () -> Unit, maxWidth: Dp) {
    ChosenStatisticsBar(
        title = profile.name,
        changeLabel = changeLabel,
        onChange = onChange,
        maxWidth = maxWidth,
        leading = if (profile.country.isNotBlank() || profile.emaId.isNotBlank()) {
            { CountryFlag(profile.country, width = 24.dp) }
        } else {
            null
        },
    )
}
