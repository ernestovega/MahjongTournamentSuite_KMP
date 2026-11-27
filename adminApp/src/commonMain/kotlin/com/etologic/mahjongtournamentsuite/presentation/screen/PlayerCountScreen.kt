package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import com.etologic.mahjongtournamentsuite.presentation.components.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountBlocker
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountRules
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTooltipBox
import com.etologic.mahjongtournamentsuite.presentation.components.DrawBackupRunningNote
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationButton
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.InfoNotice
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformBackHandler
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.screenFullBleed
import com.etologic.mahjongtournamentsuite.presentation.components.screenListPadding
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountData
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountSlot
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountWaiting
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.pc_add_locked
import mahjongtournamentsuite.adminapp.generated.resources.pc_add_table
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_label
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_slots
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_tables
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_added_waiting
import mahjongtournamentsuite.adminapp.generated.resources.pc_after
import mahjongtournamentsuite.adminapp.generated.resources.pc_back
import mahjongtournamentsuite.adminapp.generated.resources.pc_come_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_confirm
import mahjongtournamentsuite.adminapp.generated.resources.pc_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_continue
import mahjongtournamentsuite.adminapp.generated.resources.pc_done_text
import mahjongtournamentsuite.adminapp.generated.resources.pc_done_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_emails_failed
import mahjongtournamentsuite.adminapp.generated.resources.pc_emails_none
import mahjongtournamentsuite.adminapp.generated.resources.pc_emails_sent
import mahjongtournamentsuite.adminapp.generated.resources.pc_empty_slot
import mahjongtournamentsuite.adminapp.generated.resources.pc_from_to
import mahjongtournamentsuite.adminapp.generated.resources.pc_info
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_label
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_people
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_rounds
import mahjongtournamentsuite.adminapp.generated.resources.pc_kept_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_leave_people
import mahjongtournamentsuite.adminapp.generated.resources.pc_leave_people_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_less_disabled
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_completed
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_pending
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_started
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_fits
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_less
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_more_or_less
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_none
import mahjongtournamentsuite.adminapp.generated.resources.pc_minimum
import mahjongtournamentsuite.adminapp.generated.resources.pc_new_slots
import mahjongtournamentsuite.adminapp.generated.resources.pc_new_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_no_match
import mahjongtournamentsuite.adminapp.generated.resources.pc_note
import mahjongtournamentsuite.adminapp.generated.resources.pc_now
import mahjongtournamentsuite.adminapp.generated.resources.pc_remove_locked
import mahjongtournamentsuite.adminapp.generated.resources.pc_remove_table
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_label
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_people
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_registrations
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_tables
import mahjongtournamentsuite.adminapp.generated.resources.pc_saving
import mahjongtournamentsuite.adminapp.generated.resources.pc_step_choose
import mahjongtournamentsuite.adminapp.generated.resources.pc_step_done
import mahjongtournamentsuite.adminapp.generated.resources.pc_step_review
import mahjongtournamentsuite.adminapp.generated.resources.pc_step_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_sure_text
import mahjongtournamentsuite.adminapp.generated.resources.pc_team_changes
import mahjongtournamentsuite.adminapp.generated.resources.pc_team_count
import mahjongtournamentsuite.adminapp.generated.resources.pc_teams_complete
import mahjongtournamentsuite.adminapp.generated.resources.pc_teams_incomplete
import mahjongtournamentsuite.adminapp.generated.resources.pc_teams_propose
import mahjongtournamentsuite.adminapp.generated.resources.pc_teams_propose_info
import mahjongtournamentsuite.adminapp.generated.resources.pc_teams_reset
import mahjongtournamentsuite.adminapp.generated.resources.pc_teams_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_top_bar
import mahjongtournamentsuite.adminapp.generated.resources.pc_waiting_none
import mahjongtournamentsuite.adminapp.generated.resources.pc_waiting_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_calculating
import mahjongtournamentsuite.adminapp.generated.resources.search
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton

/**
 * The change of the number of players of a tournament before its first hand (docs/player-count-change.md), on its
 * own screen, opened from Players and from Edit tournament > Data. The organizer marks who leaves, or adds tables,
 * reviews what the new draw deletes and keeps, and sees the result with the emails sent. [started] is true when the
 * screen that opens it knows that a table already has data.
 */
@Composable
fun PlayerCountScreen(
    tournamentId: String,
    started: Boolean = false,
    onBack: () -> Unit,
) {
    val presenter = koinInject<PlayerCountPresenter>()
    val store = koinInject<AppMemoryStore>()
    LoadMissingSession()
    val tournaments by store.tournaments.collectAsState()
    val stored = tournaments.firstOrNull { it.id == tournamentId }
    val scope = rememberCoroutineScope()
    var data by remember(tournamentId) { mutableStateOf<PlayerCountData?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var form by rememberSaveable(tournamentId, stateSaver = PlayerCountForm.Saver) { mutableStateOf(PlayerCountForm()) }
    var reviewing by rememberSaveable(tournamentId) { mutableStateOf(false) }
    // With teams, the step to reorganize the teams that stay incomplete (Ernesto, 24-nov).
    var teaming by rememberSaveable(tournamentId) { mutableStateOf(false) }
    var sure by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var tries by remember { mutableStateOf<Long?>(null) }
    var backup by remember { mutableStateOf(false) }
    // The tournament before the change: the store has the new one once it is saved.
    var before by remember { mutableStateOf<Tournament?>(null) }
    var result by remember { mutableStateOf<PlayerCountOutcome?>(null) }
    val tournament = before ?: stored
    val blocker = tournament?.takeIf { result == null }?.let { PlayerCountRules.blockerOf(it.copy(hasTableData = it.hasTableData || started)) }

    LaunchedEffect(tournamentId, tournament != null, blocker) {
        val loadable = tournament ?: return@LaunchedEffect
        if (blocker != null || data != null) return@LaunchedEffect
        when (val loaded = presenter.load(loadable)) {
            is AppResult.Success -> data = loaded.value
            is AppResult.Failure -> error = loaded.error.toUiText()
        }
    }
    // The draw runs in this screen: leaving it now would lose it.
    PlatformBackHandler(enabled = working) {}

    val loaded = data
    val withTeams = tournament != null && loaded != null && form.needsTeams(tournament, loaded)
    val review = if (withTeams) 2 else 1
    val step = when {
        tournament == null || blocker != null -> null
        result != null -> review + 1
        loaded == null -> null
        working || reviewing -> review
        teaming && withTeams -> 1
        else -> 0
    }
    PlayerCountFrame(
        subtitle = tournament?.name,
        shortSubtitle = tournament?.shortName,
        step = step,
        withTeams = withTeams,
        isLoading = blocker == null && (tournament == null || loaded == null),
        onBack = if (working) null else onBack,
    ) {
        error?.let {
            AppErrorDialog(message = it, onDismiss = {
                error = null
                if (data == null) onBack()
            })
        }
        when {
            tournament == null -> Unit
            blocker != null -> LockedContent(blocker, onBack)
            result != null -> {
                val changed = result!!
                // The people without an email, by their names in the lists of the screen.
                val names = loaded?.slots.orEmpty().filter { it.emaId != null }.associate { it.emaId!! to it.label() } +
                    loaded?.waiting.orEmpty().associate { it.emaId to it.name }
                DoneContent(
                    previous = tournament.numPlayers,
                    now = changed.tournament.numPlayers,
                    emailsSent = changed.emailsSent,
                    noEmail = changed.noEmail.map { names[it] ?: it },
                    emailFailed = changed.emailFailed.map { names[it] ?: it },
                    onClose = onBack,
                )
            }
            // The logo with a progress covers the screen while it loads.
            loaded == null -> Unit
            working -> WorkingContent(
                text = tries?.let { stringResource(Res.string.reg_draw_calculating, it) } ?: stringResource(Res.string.pc_saving),
                backup = tries != null && backup,
            )
            reviewing -> ReviewContent(
                summary = PlayerCountSummary.of(tournament, loaded, form),
                onConfirm = { sure = true },
                // Back goes to the step before: the teams, when there is that step.
                onBack = { reviewing = false },
            )
            teaming && withTeams -> TeamsContent(
                data = loaded,
                form = form,
                onFormChange = { form = it },
                onContinue = { reviewing = true },
                onBack = { teaming = false },
            )
            else -> ChooseContent(
                tournament = tournament,
                data = loaded,
                form = form,
                // Other people leaving make other teams: the moves start again.
                onFormChange = { form = if (it.chosen != form.chosen) it.copy(teamMoves = emptyMap()) else it },
                onContinue = { if (withTeams) teaming = true else reviewing = true },
                onCancel = onBack,
            )
        }
    }

    if (sure && tournament != null && data != null) {
        SureDialog(
            onConfirm = {
                sure = false
                val loaded = data!!
                before = tournament
                working = true
                tries = 0
                scope.launch {
                    val removed = PlayerCountRules.removedSlots(form.chosen)
                    val target = form.targetOf(tournament)
                    val changed = presenter.change(tournament, loaded.slots, target, removed, form.waiting, form.teamMoves) {
                        tries = it.tried
                        backup = it.backup
                    }
                    when (changed) {
                        is AppResult.Success -> {
                            store.updateTournament(changed.value.tournament)
                            result = changed.value
                        }
                        is AppResult.Failure -> {
                            before = null
                            error = changed.error.toUiText()
                        }
                    }
                    working = false
                    tries = null
                }
            },
            onDismiss = { sure = false },
        )
    }
}

/**
 * The choice of the organizer: the people marked to leave (also with teams, Ernesto 24-nov), the tables added, and the people of the waiting
 * list who take the new places. Removing and adding never go together.
 */
internal data class PlayerCountForm(
    val chosen: Set<Int> = emptySet(),
    val addedTables: Int = 0,
    val waiting: List<String> = emptyList(),
    /** The search of the list: it only hides lines, the people marked stay marked. */
    val query: String = "",
    /** With teams, the people who stay and change team to complete one: slot to its new team. */
    val teamMoves: Map<Int, Int> = emptyMap(),
) {
    /** True when, with teams, some team stays incomplete after the removal: the step Teams shows. */
    fun needsTeams(tournament: Tournament, data: PlayerCountData): Boolean =
        tournament.isTeams && chosen.isNotEmpty() && PlayerCountRules.incompleteTeams(data.players(), chosen).isNotEmpty()

    /** [slot] goes to [team]; back in its own team it is not a move. */
    fun movedTo(slot: PlayerCountSlot, team: Int): PlayerCountForm =
        copy(teamMoves = if (team == slot.team) teamMoves - slot.id else teamMoves + (slot.id to team))

    fun toggleChosen(id: Int, max: Int): PlayerCountForm = when {
        id in chosen -> copy(chosen = chosen - id)
        chosen.size < max && addedTables == 0 -> copy(chosen = chosen + id)
        else -> this
    }

    fun toggleWaiting(emaId: String, max: Int): PlayerCountForm = when {
        emaId in waiting -> copy(waiting = waiting - emaId)
        waiting.size < max -> copy(waiting = waiting + emaId)
        else -> this
    }

    /** One table more or less; the waiting people beyond the new places are unmarked. */
    fun withAddedTables(value: Int): PlayerCountForm =
        copy(addedTables = value, waiting = waiting.take(value * PlayerCountRules.STEP))

    fun targetOf(tournament: Tournament): Int = PlayerCountRules.targetOf(tournament.numPlayers, chosen.size, addedTables)

    companion object {
        /** Keeps the choice through a recreation of the screen, such as a rotation. */
        val Saver: Saver<PlayerCountForm, Any> = Saver(
            save = { listOf(it.chosen.toList(), it.addedTables, it.waiting, it.query, it.teamMoves.flatMap { (slot, team) -> listOf(slot, team) }) },
            restore = { saved ->
                val values = saved as List<*>
                PlayerCountForm(
                    chosen = (values[0] as List<*>).map { it as Int }.toSet(),
                    addedTables = values[1] as Int,
                    waiting = (values[2] as List<*>).map { it as String },
                    query = values[3] as String,
                    teamMoves = (values[4] as List<*>).map { it as Int }.chunked(2).associate { it[0] to it[1] },
                )
            },
        )
    }
}

/** What the review lists, worked out from the form. */
internal data class PlayerCountSummary(
    val from: Int,
    val to: Int,
    val rounds: Int,
    val isTeams: Boolean,
    val hasRegistrations: Boolean,
    val leaving: List<String>,
    /** The people who change team in the step Teams, as "#4 · Ana → Tigers". */
    val teamChanges: List<String> = emptyList(),
    val newSlots: Int,
    val fromWaiting: List<String>,
) {
    companion object {
        fun of(tournament: Tournament, data: PlayerCountData, form: PlayerCountForm): PlayerCountSummary {
            val leaving = data.slots.filter { it.id in form.chosen }.map { it.label() }
            val teamNames = data.teams.associate { it.id to it.name }
            val changes = data.slots.filter { it.id in form.teamMoves && it.id !in form.chosen }.map { slot ->
                val team = form.teamMoves.getValue(slot.id)
                "${slot.label()} → ${teamNames[team] ?: "Team $team"}"
            }
            val waitingNames = data.waiting.associate { it.emaId to it.name }
            val target = form.targetOf(tournament)
            return PlayerCountSummary(
                from = tournament.numPlayers,
                to = target,
                rounds = tournament.numRounds,
                isTeams = tournament.isTeams,
                hasRegistrations = tournament.registration != null,
                leaving = leaving,
                teamChanges = changes,
                newSlots = PlayerCountRules.newSlots(tournament.numPlayers, target),
                fromWaiting = form.waiting.map { waitingNames[it] ?: it },
            )
        }
    }
}

internal fun PlayerCountSlot.label(): String = "#$id · $name"

/** The slots as the rules of the domain read them: number and team. */
internal fun PlayerCountData.players(): List<TournamentPlayer> = slots.map { TournamentPlayer(it.id, it.name, it.team) }

/** The lines that match the search: by slot number, name, EMA number or the name of the team. */
internal fun PlayerCountSlot.matches(query: String, teamName: String? = null): Boolean = query.isBlank() ||
    "#$id $name ${emaId.orEmpty()} ${teamName.orEmpty()}".matchesSearch(query) ||
    id.toString() == query.trim().removePrefix("#")

internal fun PlayerCountWaiting.matches(query: String): Boolean =
    query.isBlank() || "$name $emaId".matchesSearch(query) || position.toString() == query.trim()

/** The top bar, the steps ([step] null hides them) and the content of each step, in the width of a form. */
@Composable
internal fun PlayerCountFrame(
    subtitle: String?,
    /** In place of [subtitle] when it does not fit: the short name of the tournament. */
    shortSubtitle: String? = null,
    step: Int?,
    isLoading: Boolean = false,
    /** With the step Teams, between choosing and reviewing. */
    withTeams: Boolean = false,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.pc_top_bar),
        subtitle = subtitle,
        shortSubtitle = shortSubtitle,
        isLoading = isLoading,
        // It loads only at the start, while it has nothing to show: the logo with a progress covers it.
        contentLoading = isLoading,
        onBack = onBack,
        // The ⓘ is the first control: focused at once, it would open its tooltip.
        autoFocusFirst = false,
    ) {
        ScreenColumn(
            maxWidth = MtsTheme.contentWidth.screen,
            contentPadding = PaddingValues(horizontal = MtsTheme.layout.screenGutter, vertical = MtsTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        ) {
            if (step != null) StepIndicator(step, withTeams)
            content()
        }
    }
}

/** The steps, with the current one highlighted: choose, (teams,) review, done. */
@Composable
private fun StepIndicator(current: Int, withTeams: Boolean) {
    val labels = listOfNotNull(
        stringResource(Res.string.pc_step_choose),
        if (withTeams) stringResource(Res.string.pc_step_teams) else null,
        stringResource(Res.string.pc_step_review),
        stringResource(Res.string.pc_step_done),
    )
    // On a phone only the current step has its name; the others keep their number.
    val compact = LocalCompactLayout.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("playerCountSteps"),
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) HorizontalDivider(Modifier.width(24.dp))
            val active = index == current
            val colors = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("playerCountStep-$index")) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(if (index <= current) colors.primary else colors.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index <= current) colors.onPrimary else colors.onSurfaceVariant,
                    )
                }
                if (!compact || active) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The first step: the tables now and after, the tables added and who leaves. Continue fits only in whole tables. */
@Composable
internal fun ColumnScope.ChooseContent(
    tournament: Tournament,
    data: PlayerCountData,
    form: PlayerCountForm,
    onFormChange: (PlayerCountForm) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val current = tournament.numPlayers
    val isTeams = tournament.isTeams
    val target = form.targetOf(tournament)
    val newSlots = PlayerCountRules.newSlots(current, target)
    val maxRemovable = PlayerCountRules.maxRemovable(current)
    val maxChosen = maxRemovable
    val complete = PlayerCountRules.isChoiceComplete(current, target, form.chosen, form.waiting)
    val teamNames = data.teams.associate { it.id to it.name }
    val adding = form.addedTables > 0
    LazyColumnWithScrollbar(
        state = rememberLazyListState(),
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("playerCountList"),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
        scrollbar = MtsScrollbar.Margin,
    ) {
        item(key = "summary") { NowAndAfter(current, target) }
        item(key = "come") {
            Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                HorizontalDivider()
                SectionTitle(stringResource(Res.string.pc_come_title))
                AddTables(
                    added = form.addedTables,
                    canAdd = form.chosen.isEmpty() && PlayerCountRules.canAddTable(current, form.addedTables),
                    // Why − and + do nothing, on the button itself: − only takes back the tables added here.
                    lessDisabledReason = if (maxChosen == 0) {
                        stringResource(Res.string.pc_minimum, current)
                    } else {
                        stringResource(Res.string.pc_less_disabled)
                    },
                    moreDisabledReason = if (form.chosen.isNotEmpty()) stringResource(Res.string.pc_add_locked) else null,
                    onChange = { onFormChange(form.withAddedTables(it)) },
                )
                when {
                    form.chosen.isNotEmpty() -> InfoNotice(stringResource(Res.string.pc_add_locked), Modifier.testTag("playerCountAddLocked"))
                    adding -> Hint(
                        if (isTeams) {
                            pluralStringResource(Res.plurals.pc_new_teams, newSlots / PlayerCountRules.STEP, newSlots / PlayerCountRules.STEP)
                        } else {
                            pluralStringResource(Res.plurals.pc_new_slots, newSlots, newSlots)
                        },
                    )
                }
            }
        }
        if (adding && tournament.registration != null) {
            item(key = "waiting-title") {
                Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                    ChoiceHeader(pluralStringResource(Res.plurals.pc_waiting_title, newSlots, newSlots))
                    if (data.waiting.isEmpty()) Hint(stringResource(Res.string.pc_waiting_none)) else ChoiceSearch(form, onFormChange)
                }
            }
            val shown = data.waiting.filter { it.matches(form.query) }
            if (shown.isEmpty() && form.query.isNotBlank()) item(key = "waiting-none") { Hint(stringResource(Res.string.pc_no_match)) }
            items(shown, key = { "waiting-${it.emaId}" }) { person ->
                ChoiceRow(
                    title = "${person.position}. ${person.name}",
                    detail = person.emaId,
                    checked = person.emaId in form.waiting,
                    enabled = true,
                    tag = "playerCountWaiting-${person.emaId}",
                    onToggle = { onFormChange(form.toggleWaiting(person.emaId, newSlots)) },
                )
            }
        }
        item(key = "leave") {
            Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                HorizontalDivider()
                SectionTitle(stringResource(Res.string.pc_leave_people))
                MarkedStatus(current, form.chosen.size)
                if (isTeams) Hint(stringResource(Res.string.pc_leave_people_teams))
                when {
                    adding -> InfoNotice(stringResource(Res.string.pc_remove_locked), Modifier.testTag("playerCountRemoveLocked"))
                    maxChosen == 0 -> InfoNotice(stringResource(Res.string.pc_minimum, current), Modifier.testTag("playerCountMinimum"))
                }
                if (!adding && maxChosen > 0) ChoiceSearch(form, onFormChange)
            }
        }
        if (!adding && maxChosen > 0) {
            val full = form.chosen.size >= maxChosen
            val shown = data.slots.filter { it.matches(form.query, teamNames[it.team]) }
            if (shown.isEmpty() && form.query.isNotBlank()) item(key = "slots-none") { Hint(stringResource(Res.string.pc_no_match)) }
            items(shown, key = { "slot-${it.id}" }) { slot ->
                val checked = slot.id in form.chosen
                // With teams, the team of each person under the name, so the organizer sees which teams break.
                val team = if (isTeams) teamNames[slot.team] ?: "Team ${slot.team}" else null
                ChoiceRow(
                    title = if (slot.emaId == null) stringResource(Res.string.pc_empty_slot, slot.id) else slot.label(),
                    detail = listOfNotNull(team, slot.emaId).joinToString(" · ").ifEmpty { null },
                    checked = checked,
                    enabled = checked || !full,
                    tag = "playerCountSlot-${slot.id}",
                    onToggle = { onFormChange(form.toggleChosen(slot.id, maxChosen)) },
                )
            }
        }
    }
    MtsDialogButtons {
        Button(
            onClick = onContinue,
            enabled = complete,
            buttonModifier = Modifier.testTag("playerCountContinue"),
        ) { ButtonText(stringResource(Res.string.pc_continue)) }
        TextButton(onClick = onCancel) { ButtonText(stringResource(Res.string.cancel)) }
    }
}

/** "Now" and "After the change", with what a change does in the ⓘ. */
@Composable
private fun NowAndAfter(current: Int, target: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(Res.string.pc_now, current, current / PlayerCountRules.STEP),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("playerCountNow"),
            )
            // A number that does not fit in tables of 4 is not a tournament: the line waits until it fits.
            if (target % PlayerCountRules.STEP == 0) {
                Text(
                    stringResource(Res.string.pc_after, target, target / PlayerCountRules.STEP),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (target != current) MtsTheme.colors.link else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("playerCountTarget"),
                )
            }
        }
        InfoTooltipIcon(stringResource(Res.string.pc_info))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Marked 7: mark 1 more or unmark 3". Colored when it fits in whole tables. */
@Composable
private fun MarkedStatus(current: Int, marked: Int) {
    val fit = PlayerCountRules.markedFit(current, marked)
    val text = when {
        marked == 0 -> stringResource(Res.string.pc_marked_none)
        fit.fits -> stringResource(Res.string.pc_marked_fits, marked, marked / PlayerCountRules.STEP)
        fit.missing > 0 -> stringResource(Res.string.pc_marked_more_or_less, marked, fit.missing, fit.extra)
        else -> stringResource(Res.string.pc_marked_less, marked, fit.extra)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = when {
            marked == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
            fit.fits -> MtsTheme.colors.link
            else -> MaterialTheme.colorScheme.error
        },
        modifier = Modifier.testTag("playerCountChosen"),
    )
}

/** − tables added +, with whole tables of 4. */
@Composable
private fun AddTables(
    added: Int,
    canAdd: Boolean,
    lessDisabledReason: String,
    moreDisabledReason: String?,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppTooltipBox(lessDisabledReason, enabled = added == 0) {
            FocusedIconButton(
                onClick = { onChange(added - 1) },
                enabled = added > 0,
                buttonModifier = Modifier.stepBorder(added > 0).testTag("playerCountLess"),
            ) { Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.pc_remove_table)) }
        }
        Text(
            stringResource(Res.string.pc_added_tables, added),
            modifier = Modifier.padding(horizontal = 16.dp).testTag("playerCountAdded"),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        AppTooltipBox(moreDisabledReason.orEmpty(), enabled = !canAdd && moreDisabledReason != null) {
            FocusedIconButton(
                onClick = { onChange(added + 1) },
                enabled = canAdd,
                buttonModifier = Modifier.stepBorder(canAdd).testTag("playerCountMore"),
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.pc_add_table)) }
        }
    }
}

/** The search field of the lists, with the short hint of every search of the app. */
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
private fun ChoiceHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

/** The outline of the − and + buttons, as an outlined icon button. */
@Composable
private fun Modifier.stepBorder(enabled: Boolean): Modifier {
    val outline = MaterialTheme.colorScheme.outline
    return border(1.dp, if (enabled) outline else outline.copy(alpha = 0.38f), CircleShape)
}

/** A whole line that toggles, with its checkbox. */
@Composable
private fun ChoiceRow(title: String, detail: String?, checked: Boolean, enabled: Boolean, tag: String, onToggle: () -> Unit) {
    MtsCheckboxRow(
        checked = checked,
        onCheckedChange = { onToggle() },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        enabled = enabled,
        gap = 12.dp,
        contentPadding = PaddingValues(vertical = 4.dp),
        controlPadding = 0.dp,
    ) {
        Column(Modifier.weight(1f)) {
            val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * With teams, the step between choosing and reviewing: the teams that lost people, with who is left in each. Each
 * person can go to another of those teams, and Propose completes them with few moves (Ernesto, 22 and 24-nov).
 */
@Composable
internal fun ColumnScope.TeamsContent(
    data: PlayerCountData,
    form: PlayerCountForm,
    onFormChange: (PlayerCountForm) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val players = data.players()
    val teamNames = data.teams.associate { it.id to it.name }
    fun nameOf(team: Int) = teamNames[team] ?: "Team $team"
    // The teams in play: the incomplete ones, and the ones a move touched (complete now, or left without people).
    val involved = (
        PlayerCountRules.incompleteTeams(players, form.chosen).keys +
            form.teamMoves.values +
            data.slots.filter { it.id in form.teamMoves }.map { it.team }
        ).distinct().sorted()
    val after = PlayerCountRules.teamsAfter(players, form.chosen, form.teamMoves).filterKeys { it in involved }
    val open = PlayerCountRules.incompleteTeams(players, form.chosen, form.teamMoves)
    val slotsById = data.slots.associateBy { it.id }
    // Drag and drop (Ernesto, 24-nov): a person is dragged by the handle on the left and dropped on a team that has
    // room. The button on the right does the same with the keyboard.
    val teamBounds = remember { mutableStateMapOf<Int, Rect>() }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    fun canTake(team: Int, from: Int) = team == from || (after[team]?.size ?: 0) < PlayerCountRules.STEP
    val dropTeam = dragPointer?.let { pointer -> teamBounds.entries.firstOrNull { it.value.contains(pointer) }?.key }
    ScrollableColumnWithScrollbar(
        state = rememberScrollState(),
        // It reaches the edges of the window, so its shadows take the whole width; the margins go inside.
        modifier = Modifier.weight(1f).fillMaxWidth().screenFullBleed().testTag("playerCountTeams"),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        scrollbar = MtsScrollbar.Overlay,
        contentPadding = screenListPadding(),
    ) {
        SectionTitle(stringResource(Res.string.pc_teams_title))
        Text(
            if (open.isEmpty()) stringResource(Res.string.pc_teams_complete) else stringResource(Res.string.pc_teams_incomplete, open.size),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (open.isEmpty()) MtsTheme.colors.link else MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("playerCountTeamsStatus"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onFormChange(form.copy(teamMoves = PlayerCountRules.proposeTeams(players, form.chosen, form.teamMoves))) },
                enabled = open.size >= 2,
                buttonModifier = Modifier.testTag("playerCountPropose"),
            ) { ButtonText(stringResource(Res.string.pc_teams_propose)) }
            InfoTooltipIcon(stringResource(Res.string.pc_teams_propose_info))
            TextButton(
                onClick = { onFormChange(form.copy(teamMoves = emptyMap())) },
                enabled = form.teamMoves.isNotEmpty(),
                modifier = Modifier.testTag("playerCountTeamsReset"),
            ) { ButtonText(stringResource(Res.string.pc_teams_reset)) }
        }
        after.forEach { (team, members) ->
            val from = dragged?.let { id -> after.entries.first { id in it.value }.key }
            val target = from != null && dropTeam == team && team != from && canTake(team, from)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { teamBounds[team] = it.boundsInRoot() }
                    .background(if (target) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .testTag("playerCountTeam-$team"),
            ) {
                HorizontalDivider()
                Text(
                    stringResource(Res.string.pc_team_count, nameOf(team), members.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (members.size < PlayerCountRules.STEP) MaterialTheme.colorScheme.error else MtsTheme.colors.link,
                )
                members.forEach { id ->
                    key(id) {
                        val slot = slotsById.getValue(id)
                        var rowOrigin by remember { mutableStateOf(Offset.Zero) }
                        val isDragged = dragged == id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        shadowElevation = 8.dp.toPx()
                                    }
                                }
                                .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .onGloballyPositioned { rowOrigin = it.positionInRoot() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("playerCountDrag-$id")
                                    .pointerInput(id, team, form) {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                dragged = id
                                                dragOffset = Offset.Zero
                                                // The pointer in the root: the handle sits at the start of the row.
                                                dragPointer = rowOrigin + start
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                dragPointer = dragPointer?.plus(amount)
                                            },
                                            onDragEnd = {
                                                val to = dragPointer?.let { pointer -> teamBounds.entries.firstOrNull { it.value.contains(pointer) }?.key }
                                                if (to != null && to != team && canTake(to, team)) onFormChange(form.movedTo(slot, to))
                                                dragged = null
                                                dragPointer = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragged = null
                                                dragPointer = null
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    },
                            )
                            Text(
                                if (slot.emaId == null) stringResource(Res.string.pc_empty_slot, slot.id) else slot.label(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TeamPicker(
                                slotId = id,
                                current = team,
                                options = involved.map { it to (after[it]?.size ?: 0) },
                                nameOf = ::nameOf,
                                onPick = { onFormChange(form.movedTo(slot, it)) },
                            )
                        }
                    }
                }
            }
        }
    }
    MtsDialogButtons {
        // No team may stay incomplete after this step (Ernesto, 24-nov).
        Button(
            onClick = onContinue,
            enabled = open.isEmpty(),
            buttonModifier = Modifier.testTag("playerCountTeamsContinue"),
        ) { ButtonText(stringResource(Res.string.pc_continue)) }
        TextButton(onClick = onBack, modifier = Modifier.testTag("playerCountTeamsBack")) { ButtonText(stringResource(Res.string.pc_back)) }
    }
}

/** The team of a person in the step Teams: a button with its name, and a menu of the teams in play with how many they have. */
@Composable
private fun TeamPicker(slotId: Int, current: Int, options: List<Pair<Int, Int>>, nameOf: (Int) -> String, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            buttonModifier = Modifier.testTag("playerCountMove-$slotId"),
        ) { ButtonText(nameOf(current)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (team, count) ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.pc_team_count, nameOf(team), count)) },
                    enabled = team == current || count < PlayerCountRules.STEP,
                    modifier = Modifier.testTag("playerCountMoveTo-$slotId-$team"),
                    onClick = {
                        open = false
                        if (team != current) onPick(team)
                    },
                )
            }
        }
    }
}

/** The second step: what the new draw deletes, adds and keeps, and how the draw works. */
@Composable
internal fun ColumnScope.ReviewContent(
    summary: PlayerCountSummary,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    var showDrawExplanation by remember { mutableStateOf(false) }
    ScrollableColumnWithScrollbar(
        state = rememberScrollState(),
        // It reaches the edges of the window, so its shadows take the whole width; the margins go inside.
        modifier = Modifier.weight(1f).fillMaxWidth().screenFullBleed().testTag("playerCountConfirm"),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        scrollbar = MtsScrollbar.Overlay,
        contentPadding = screenListPadding(),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.pc_confirm_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(Res.string.pc_from_to, summary.from, summary.to),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BulletGroup(
            stringResource(Res.string.pc_removed_label),
            buildList {
                add(stringResource(Res.string.pc_removed_tables, summary.rounds))
                if (summary.leaving.isNotEmpty()) {
                    // Each person and each team on its own line, under its bullet (Ernesto, 24-nov).
                    add(BulletItem(stringResource(Res.string.pc_removed_people), summary.leaving, "playerCountLeaving"))
                    if (summary.teamChanges.isNotEmpty()) {
                        add(BulletItem(stringResource(Res.string.pc_team_changes), summary.teamChanges, "playerCountTeamChanges"))
                    }
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
                        add(pluralStringResource(Res.plurals.pc_added_teams, summary.newSlots / PlayerCountRules.STEP, summary.newSlots / PlayerCountRules.STEP))
                    } else if (empty > 0) {
                        add(pluralStringResource(Res.plurals.pc_added_slots, empty, empty))
                    }
                    if (summary.fromWaiting.isNotEmpty()) {
                        add(BulletItem(stringResource(Res.string.pc_added_waiting), summary.fromWaiting, "playerCountFromWaiting"))
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
        Text(
            stringResource(Res.string.pc_note),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DrawExplanationButton(onClick = { showDrawExplanation = true })
        }
    }
    MtsDialogButtons {
        Button(
            onClick = onConfirm,
            colors = MtsButtons.destructiveColors(),
            buttonModifier = Modifier.testTag("playerCountConfirmButton"),
        ) { ButtonText(stringResource(Res.string.pc_confirm)) }
        TextButton(onClick = onBack, modifier = Modifier.testTag("playerCountBack")) { ButtonText(stringResource(Res.string.pc_back)) }
    }
    if (showDrawExplanation) {
        DrawExplanationDialog(onDismiss = { showDrawExplanation = false })
    }
}

/** The last word before the draw: it cannot be undone. */
@Composable
internal fun SureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TrackedDialog("PlayerCountSure") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().testTag("playerCountSure"),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.pc_confirm_title)) },
            text = { MtsDialogText(stringResource(Res.string.pc_sure_text)) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onConfirm,
                        colors = MtsButtons.destructiveColors(),
                        buttonModifier = Modifier.testTag("playerCountSureButton"),
                    ) { ButtonText(stringResource(Res.string.pc_confirm)) }
                    TextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** A bullet line with its own list under it, one entry per line (people, teams). */
private class BulletItem(val text: String, val lines: List<String>, val tag: String)

/** A label and its bullet lines: a String, or a [BulletItem]. Each line hangs under its bullet. */
@Composable
private fun BulletGroup(label: String, items: List<Any>, tag: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = if (tag == null) Modifier else Modifier.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        items.forEach { item ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                if (item is BulletItem) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.text, style = MaterialTheme.typography.bodyMedium)
                        item.lines.forEachIndexed { index, line ->
                            Row {
                                Text("◦", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("${item.tag}-$index"))
                            }
                        }
                    }
                } else {
                    Text(item.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The draw and the save are running: the tries and, after the time limit of the first way, the second way. */
@Composable
internal fun WorkingContent(text: String, backup: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = MtsTheme.spacing.xl).testTag("playerCountWorking"),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
    ) {
        MtsDialogText(text)
        if (backup) DrawBackupRunningNote()
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ColumnScope.DoneContent(
    previous: Int,
    now: Int,
    emailsSent: Int,
    noEmail: List<String>,
    emailFailed: List<String>,
    onClose: () -> Unit,
) {
    ScrollableColumnWithScrollbar(
        state = rememberScrollState(),
        // It reaches the edges of the window, so its shadows take the whole width; the margins go inside.
        modifier = Modifier.weight(1f).fillMaxWidth().screenFullBleed().testTag("playerCountDone"),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        scrollbar = MtsScrollbar.Overlay,
        contentPadding = screenListPadding(),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.pc_done_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(Res.string.pc_from_to, previous, now),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MtsDialogText(stringResource(Res.string.pc_done_text, now))
        MtsDialogText(pluralStringResource(Res.plurals.pc_emails_sent, emailsSent, emailsSent), modifier = Modifier.testTag("playerCountEmailsSent"))
        // The organizer tells these people by hand: they have no email, or the email failed.
        if (noEmail.isNotEmpty()) BulletGroup(stringResource(Res.string.pc_emails_none), noEmail, "playerCountNoEmail")
        if (emailFailed.isNotEmpty()) BulletGroup(stringResource(Res.string.pc_emails_failed), emailFailed, "playerCountEmailFailed")
    }
    MtsDialogButtons {
        Button(onClick = onClose, buttonModifier = Modifier.testTag("playerCountClose")) { ButtonText(stringResource(Res.string.close)) }
    }
}

/** Why the number of players cannot change now. */
@Composable
internal fun ColumnScope.LockedContent(blocker: PlayerCountBlocker, onClose: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = MtsTheme.spacing.xl).testTag("playerCountLocked"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
    ) {
        Text(
            stringResource(Res.string.pc_locked_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(
                when (blocker) {
                    PlayerCountBlocker.STARTED -> Res.string.pc_locked_started
                    PlayerCountBlocker.COMPLETED -> Res.string.pc_locked_completed
                    PlayerCountBlocker.DRAW_PENDING -> Res.string.pc_locked_pending
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MtsTheme.spacing.sm))
        OutlinedButton(onClick = onClose) { ButtonText(stringResource(Res.string.close)) }
    }
}
