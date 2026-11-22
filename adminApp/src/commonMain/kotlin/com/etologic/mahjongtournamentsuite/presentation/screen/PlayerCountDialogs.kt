package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationButton
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import mahjongtournamentsuite.adminapp.generated.resources.search
import mahjongtournamentsuite.adminapp.generated.resources.pc_no_match
import androidx.compose.material3.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountBlocker
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountRules
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountData
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountSlot
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountWaiting
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_label
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_slots
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_waiting
import mahjongtournamentsuite.adminapp.generated.resources.pc_back
import mahjongtournamentsuite.adminapp.generated.resources.pc_choose_people
import mahjongtournamentsuite.adminapp.generated.resources.pc_choose_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_chosen
import mahjongtournamentsuite.adminapp.generated.resources.pc_confirm
import mahjongtournamentsuite.adminapp.generated.resources.pc_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_continue
import mahjongtournamentsuite.adminapp.generated.resources.pc_count
import mahjongtournamentsuite.adminapp.generated.resources.pc_done_text
import mahjongtournamentsuite.adminapp.generated.resources.pc_done_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_empty_slot
import mahjongtournamentsuite.adminapp.generated.resources.pc_from_to
import mahjongtournamentsuite.adminapp.generated.resources.pc_hint
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_label
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_people
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_rounds
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_less
import mahjongtournamentsuite.adminapp.generated.resources.pc_loading
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_completed
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_pending
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_started
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_more
import mahjongtournamentsuite.adminapp.generated.resources.pc_new_slots
import mahjongtournamentsuite.adminapp.generated.resources.pc_new_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_note
import mahjongtournamentsuite.adminapp.generated.resources.pc_now
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_label
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_people
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_registrations
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_tables
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_saving
import mahjongtournamentsuite.adminapp.generated.resources.pc_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_waiting_none
import mahjongtournamentsuite.adminapp.generated.resources.pc_waiting_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_calculating
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The change of the number of players of a tournament before its first hand (docs/player-count-change.md), from
 * Edit tournament > Data and from Players. It loads the slots, asks for the new number and who leaves or comes,
 * confirms what the new draw deletes and keeps, computes the draw and shows the result.
 */
@Composable
fun PlayerCountFlow(
    tournament: Tournament,
    onDismiss: () -> Unit,
    onChanged: (Tournament) -> Unit = {},
) {
    val blocker = PlayerCountRules.blockerOf(tournament)
    if (blocker != null) {
        PlayerCountLockedDialog(blocker, onDismiss)
        return
    }
    val presenter = koinInject<PlayerCountPresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    var data by remember(tournament.id) { mutableStateOf<PlayerCountData?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var form by remember(tournament.id) { mutableStateOf(PlayerCountForm(target = tournament.numPlayers)) }
    var step by remember { mutableStateOf(PlayerCountStep.CHOOSE) }
    var tries by remember { mutableStateOf<Long?>(null) }
    var result by remember { mutableStateOf<Tournament?>(null) }

    LaunchedEffect(tournament.id) {
        when (val loaded = presenter.load(tournament)) {
            is AppResult.Success -> data = loaded.value
            is AppResult.Failure -> error = loaded.error.toUiText()
        }
    }

    error?.let {
        AppErrorDialog(message = it, onDismiss = {
            error = null
            if (data == null) onDismiss()
        })
        return
    }
    val loaded = data
    if (loaded == null) {
        PlayerCountProgressDialog(text = stringResource(Res.string.pc_loading))
        return
    }
    result?.let { changed ->
        PlayerCountDoneDialog(previous = tournament.numPlayers, now = changed.numPlayers, onClose = onDismiss)
        return
    }
    when (step) {
        PlayerCountStep.CHOOSE -> PlayerCountChooseDialog(
            tournament = tournament,
            data = loaded,
            form = form,
            onFormChange = { form = it },
            onContinue = { step = PlayerCountStep.CONFIRM },
            onDismiss = onDismiss,
        )
        PlayerCountStep.CONFIRM -> PlayerCountConfirmDialog(
            summary = PlayerCountSummary.of(tournament, loaded, form),
            onConfirm = {
                step = PlayerCountStep.WORKING
                tries = 0
                scope.launch {
                    val removed = PlayerCountRules.removedSlots(tournament.isTeams, form.chosen, loaded.teams)
                    when (val changed = presenter.change(tournament, form.target, removed, form.waiting) { tries = it.tried }) {
                        is AppResult.Success -> {
                            store.updateTournament(changed.value)
                            onChanged(changed.value)
                            result = changed.value
                        }
                        is AppResult.Failure -> {
                            error = changed.error.toUiText()
                            step = PlayerCountStep.CONFIRM
                        }
                    }
                    tries = null
                }
            },
            onBack = { step = PlayerCountStep.CHOOSE },
            onDismiss = onDismiss,
        )
        PlayerCountStep.WORKING -> PlayerCountProgressDialog(
            text = tries?.let { stringResource(Res.string.reg_draw_calculating, it) } ?: stringResource(Res.string.pc_saving),
        )
    }
}

internal enum class PlayerCountStep { CHOOSE, CONFIRM, WORKING }

/** The choice of the organizer: the new number, the slots or teams that leave, and the people of the waiting list. */
internal data class PlayerCountForm(
    val target: Int,
    val chosen: Set<Int> = emptySet(),
    val waiting: List<String> = emptyList(),
    /** The search of the list: it only hides lines, the people chosen stay chosen. */
    val query: String = "",
) {
    /** A new number clears the choice that belonged to the old one, and keeps the search. */
    fun withTarget(value: Int): PlayerCountForm = PlayerCountForm(target = value, query = query)

    fun toggleChosen(id: Int, max: Int): PlayerCountForm = when {
        id in chosen -> copy(chosen = chosen - id)
        chosen.size < max -> copy(chosen = chosen + id)
        else -> this
    }

    fun toggleWaiting(emaId: String, max: Int): PlayerCountForm = when {
        emaId in waiting -> copy(waiting = waiting - emaId)
        waiting.size < max -> copy(waiting = waiting + emaId)
        else -> this
    }
}

/** What the confirmation lists, worked out from the form. */
internal data class PlayerCountSummary(
    val from: Int,
    val to: Int,
    val rounds: Int,
    val isTeams: Boolean,
    val hasRegistrations: Boolean,
    val leaving: List<String>,
    val newSlots: Int,
    val fromWaiting: List<String>,
) {
    companion object {
        fun of(tournament: Tournament, data: PlayerCountData, form: PlayerCountForm): PlayerCountSummary {
            val leaving = if (tournament.isTeams) {
                data.teams.filter { it.id in form.chosen }.map { it.name }
            } else {
                data.slots.filter { it.id in form.chosen }.map { it.label() }
            }
            val waitingNames = data.waiting.associate { it.emaId to it.name }
            return PlayerCountSummary(
                from = tournament.numPlayers,
                to = form.target,
                rounds = tournament.numRounds,
                isTeams = tournament.isTeams,
                hasRegistrations = tournament.registration != null,
                leaving = leaving,
                newSlots = PlayerCountRules.newSlots(tournament.numPlayers, form.target),
                fromWaiting = form.waiting.map { waitingNames[it] ?: it },
            )
        }
    }
}

private fun PlayerCountSlot.label(): String = "#$id · $name"

/** The lines that match the search: by slot number, name or EMA number; a team also by its players. */
internal fun PlayerCountSlot.matches(query: String): Boolean =
    query.isBlank() || "#$id $name ${emaId.orEmpty()}".matchesSearch(query) || id.toString() == query.trim().removePrefix("#")

internal fun TournamentTeam.matches(query: String, slots: List<PlayerCountSlot>): Boolean =
    query.isBlank() || name.matchesSearch(query) || slots.filter { it.id in playerIds }.any { it.matches(query) }

internal fun PlayerCountWaiting.matches(query: String): Boolean =
    query.isBlank() || "$name $emaId".matchesSearch(query) || position.toString() == query.trim()

/** The search field of the lists of the dialog, with the short hint of every search of the app. */
@Composable
private fun ChoiceSearch(form: PlayerCountForm, onFormChange: (PlayerCountForm) -> Unit) {
    OutlinedTextField(
        value = form.query,
        onValueChange = { onFormChange(form.copy(query = it)) },
        modifier = Modifier.fillMaxWidth().testTag("playerCountSearch"),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        label = { Text(stringResource(Res.string.search)) },
    )
}

@Composable
private fun NoMatch(shown: Int, query: String) {
    if (shown == 0 && query.isNotBlank()) MtsDialogText(stringResource(Res.string.pc_no_match))
}

@Composable
internal fun PlayerCountChooseDialog(
    tournament: Tournament,
    data: PlayerCountData,
    form: PlayerCountForm,
    onFormChange: (PlayerCountForm) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = tournament.numPlayers
    val target = form.target
    val toRemove = PlayerCountRules.slotsToRemove(current, target)
    val newSlots = PlayerCountRules.newSlots(current, target)
    val teamsToRemove = toRemove / PlayerCountRules.STEP
    val maxChosen = if (tournament.isTeams) teamsToRemove else toRemove
    val complete = PlayerCountRules.isChoiceComplete(current, target, tournament.isTeams, form.chosen, data.teams, form.waiting)
    TrackedDialog("PlayerCountChoose") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().testTag("playerCountChoose"),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.pc_title), subtitle = tournament.name.ifBlank { null }) },
            text = {
                ScrollableColumnWithScrollbar(
                    state = rememberScrollState(),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MtsDialogText(stringResource(Res.string.pc_now, current, current / 4))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusedIconButton(
                            onClick = { onFormChange(form.withTarget(target - PlayerCountRules.STEP)) },
                            enabled = PlayerCountRules.canDecrease(target),
                            buttonModifier = Modifier.stepBorder(PlayerCountRules.canDecrease(target)).testTag("playerCountLess"),
                        ) { Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.pc_less)) }
                        Text(
                            stringResource(Res.string.pc_count, target, target / 4),
                            modifier = Modifier.padding(horizontal = 16.dp).testTag("playerCountTarget"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        FocusedIconButton(
                            onClick = { onFormChange(form.withTarget(target + PlayerCountRules.STEP)) },
                            enabled = PlayerCountRules.canIncrease(target),
                            buttonModifier = Modifier.stepBorder(PlayerCountRules.canIncrease(target)).testTag("playerCountMore"),
                        ) { Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.pc_more)) }
                    }
                    HorizontalDivider()
                    when {
                        toRemove > 0 && tournament.isTeams -> {
                            ChoiceHeader(stringResource(Res.string.pc_choose_teams, teamsToRemove), form.chosen.size, teamsToRemove)
                            ChoiceSearch(form, onFormChange)
                            val shown = data.teams.filter { it.matches(form.query, data.slots) }
                            NoMatch(shown.size, form.query)
                            shown.forEach { team ->
                                ChoiceRow(
                                    title = team.name,
                                    detail = team.memberNames(data.slots),
                                    checked = team.id in form.chosen,
                                    tag = "playerCountTeam-${team.id}",
                                    onToggle = { onFormChange(form.toggleChosen(team.id, maxChosen)) },
                                )
                            }
                        }
                        toRemove > 0 -> {
                            ChoiceHeader(stringResource(Res.string.pc_choose_people, toRemove), form.chosen.size, toRemove)
                            ChoiceSearch(form, onFormChange)
                            val shown = data.slots.filter { it.matches(form.query) }
                            NoMatch(shown.size, form.query)
                            shown.forEach { slot ->
                                ChoiceRow(
                                    title = if (slot.emaId == null) stringResource(Res.string.pc_empty_slot, slot.id) else slot.label(),
                                    detail = slot.emaId,
                                    checked = slot.id in form.chosen,
                                    tag = "playerCountSlot-${slot.id}",
                                    onToggle = { onFormChange(form.toggleChosen(slot.id, maxChosen)) },
                                )
                            }
                        }
                        newSlots == 0 -> MtsDialogText(stringResource(Res.string.pc_hint))
                        else -> {
                            MtsDialogText(
                                if (tournament.isTeams) {
                                    stringResource(Res.string.pc_new_teams, newSlots / PlayerCountRules.STEP)
                                } else {
                                    stringResource(Res.string.pc_new_slots, newSlots)
                                },
                            )
                            if (tournament.registration != null) {
                                ChoiceHeader(stringResource(Res.string.pc_waiting_title, newSlots), form.waiting.size, newSlots)
                                if (data.waiting.isEmpty()) {
                                    MtsDialogText(stringResource(Res.string.pc_waiting_none))
                                } else {
                                    ChoiceSearch(form, onFormChange)
                                }
                                val shown = data.waiting.filter { it.matches(form.query) }
                                NoMatch(shown.size, form.query)
                                shown.forEach { person ->
                                    ChoiceRow(
                                        title = "${person.position}. ${person.name}",
                                        detail = person.emaId,
                                        checked = person.emaId in form.waiting,
                                        tag = "playerCountWaiting-${person.emaId}",
                                        onToggle = { onFormChange(form.toggleWaiting(person.emaId, newSlots)) },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(
                        onClick = onContinue,
                        enabled = complete,
                        buttonModifier = Modifier.testTag("playerCountContinue"),
                    ) { Text(stringResource(Res.string.pc_continue)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

private fun TournamentTeam.memberNames(slots: List<PlayerCountSlot>): String {
    val byId = slots.associateBy { it.id }
    return playerIds.mapNotNull { byId[it] }.joinToString(", ") { if (it.emaId == null) "#${it.id}" else it.name }
}

@Composable
private fun ChoiceHeader(text: String, chosen: Int, max: Int) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            stringResource(Res.string.pc_chosen, chosen, max),
            style = MaterialTheme.typography.bodySmall,
            color = if (chosen == max) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("playerCountChosen"),
        )
    }
}

/** A whole line that toggles, with its checkbox, as the radio and checkbox lists of the other dialogs. */
/** The outline of the − and + buttons, as an outlined icon button. */
@Composable
private fun Modifier.stepBorder(enabled: Boolean): Modifier {
    val outline = MaterialTheme.colorScheme.outline
    return border(1.dp, if (enabled) outline else outline.copy(alpha = 0.38f), CircleShape)
}

@Composable
private fun ChoiceRow(title: String, detail: String?, checked: Boolean, tag: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The destructive confirmation: what the new draw deletes, adds and keeps. "How does the draw work?" sits above the buttons. */
@Composable
internal fun PlayerCountConfirmDialog(
    summary: PlayerCountSummary,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDrawExplanation by remember { mutableStateOf(false) }
    TrackedDialog("PlayerCountConfirm") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().testTag("playerCountConfirm"),
            onDismissRequest = onDismiss,
            title = {
                MtsDialogTitle(
                    stringResource(Res.string.pc_confirm_title),
                    subtitle = stringResource(Res.string.pc_from_to, summary.from, summary.to),
                )
            },
            text = {
                ScrollableColumnWithScrollbar(
                    state = rememberScrollState(),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BulletGroup(
                                stringResource(Res.string.pc_removed_label),
                                buildList {
                                    add(stringResource(Res.string.pc_removed_tables, summary.rounds))
                                    if (summary.leaving.isNotEmpty()) {
                                        val names = summary.leaving.joinToString(", ")
                                        add(stringResource(if (summary.isTeams) Res.string.pc_removed_teams else Res.string.pc_removed_people, names))
                                        if (summary.hasRegistrations) add(stringResource(Res.string.pc_removed_registrations))
                                    }
                                },
                            )
                            if (summary.newSlots > 0) {
                                BulletGroup(
                                    stringResource(Res.string.pc_added_label),
                                    buildList {
                                        val empty = summary.newSlots - summary.fromWaiting.size
                                        if (summary.isTeams) {
                                            add(stringResource(Res.string.pc_added_teams, summary.newSlots / PlayerCountRules.STEP))
                                        } else if (empty > 0) {
                                            add(stringResource(Res.string.pc_added_slots, empty))
                                        }
                                        if (summary.fromWaiting.isNotEmpty()) {
                                            add(stringResource(Res.string.pc_added_waiting, summary.fromWaiting.joinToString(", ")))
                                        }
                                    },
                                )
                            }
                            BulletGroup(
                                stringResource(Res.string.pc_kept_label),
                                buildList {
                                    add(stringResource(Res.string.pc_kept_people))
                                    if (summary.isTeams) add(stringResource(Res.string.pc_kept_teams))
                                    add(stringResource(Res.string.pc_kept_rounds))
                                },
                            )
                        }
                        Text(
                            stringResource(Res.string.pc_note),
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    DrawExplanationButton(onClick = { showDrawExplanation = true })
                    Spacer(Modifier.height(8.dp))
                    MtsDialogButtons {
                        Button(
                            onClick = onConfirm,
                            colors = MtsButtons.destructiveColors(),
                            buttonModifier = Modifier.testTag("playerCountConfirmButton"),
                        ) { Text(stringResource(Res.string.pc_confirm)) }
                        TextButton(onClick = onBack) { Text(stringResource(Res.string.pc_back)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    }
                }
            },
        )
    }
    if (showDrawExplanation) {
        DrawExplanationDialog(onDismiss = { showDrawExplanation = false })
    }
}

/** A label and its bullet lines; each line hangs under its bullet. */
@Composable
private fun BulletGroup(label: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        items.forEach { item ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun PlayerCountProgressDialog(text: String) {
    TrackedDialog("PlayerCountProgress") {
        AlertDialog(
            onDismissRequest = {},
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MtsDialogText(text)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
internal fun PlayerCountDoneDialog(previous: Int, now: Int, onClose: () -> Unit) {
    TrackedDialog("PlayerCountDone") {
        AlertDialog(
            modifier = Modifier.testTag("playerCountDone"),
            onDismissRequest = onClose,
            title = { MtsDialogTitle(stringResource(Res.string.pc_done_title), subtitle = stringResource(Res.string.pc_from_to, previous, now)) },
            text = { MtsDialogText(stringResource(Res.string.pc_done_text, now)) },
            confirmButton = {
                MtsDialogButtons {
                    Button(onClick = onClose) { Text(stringResource(Res.string.close)) }
                }
            },
        )
    }
}

/** Why the number of players cannot change now. */
@Composable
internal fun PlayerCountLockedDialog(blocker: PlayerCountBlocker, onDismiss: () -> Unit) {
    TrackedDialog("PlayerCountLocked") {
        AlertDialog(
            modifier = Modifier.testTag("playerCountLocked"),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.pc_locked_title)) },
            text = {
                MtsDialogText(
                    stringResource(
                        when (blocker) {
                            PlayerCountBlocker.STARTED -> Res.string.pc_locked_started
                            PlayerCountBlocker.COMPLETED -> Res.string.pc_locked_completed
                            PlayerCountBlocker.DRAW_PENDING -> Res.string.pc_locked_pending
                        },
                    ),
                )
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                }
            },
        )
    }
}
