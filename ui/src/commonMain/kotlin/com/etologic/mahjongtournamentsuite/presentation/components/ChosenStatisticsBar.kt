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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.scope_tournament
import mahjongtournamentsuite.ui.generated.resources.stats_all_tournaments
import org.jetbrains.compose.resources.stringResource

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
            ButtonText(changeLabel, maxLines = 1, softWrap = false)
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

/**
 * The bar of the tournaments of a player: the chosen tournament, or all of them, and the button to choose another one.
 * The button swaps the bar for a tournament dropdown with "All tournaments" first and the newest tournaments next.
 * [chosenId] is null for all the tournaments.
 */
@Composable
fun ChosenTournamentBar(
    tournaments: List<Tournament>,
    chosenId: String?,
    onChoose: (String?) -> Unit,
    changeLabel: String,
    maxWidth: Dp,
) {
    var changing by remember { mutableStateOf(false) }
    val all = stringResource(Res.string.stats_all_tournaments)
    if (!changing) {
        ChosenStatisticsBar(
            title = tournaments.firstOrNull { it.id == chosenId }?.name ?: all,
            changeLabel = changeLabel,
            onChange = { changing = true },
            maxWidth = maxWidth,
            leading = { Icon(Icons.Default.EmojiEvents, contentDescription = null, Modifier.size(20.dp), tint = mtsTabContentColor()) },
        )
        return
    }
    val options = remember(tournaments, all) {
        listOf(ALL_TOURNAMENTS_KEY to all) +
            tournaments.sortedByDescending { it.eventStartDate.orEmpty() }.map { it.id to it.name }
    }
    Row(
        Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectField(
            stringResource(Res.string.scope_tournament),
            options,
            chosenId ?: ALL_TOURNAMENTS_KEY,
            {
                onChoose(it.takeIf { id -> id != ALL_TOURNAMENTS_KEY })
                changing = false
            },
            Modifier.weight(1f),
        )
        DebouncedTextButton(onClick = { changing = false }) { ButtonText(stringResource(Res.string.action_cancel)) }
    }
}

/** The key of "All tournaments" in the dropdown. No tournament id is empty. */
private const val ALL_TOURNAMENTS_KEY = ""
