package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountBlocker
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountRules
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.DrawBackupRunningNote
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationButton
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
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
import mahjongtournamentsuite.adminapp.generated.resources.pc_leave_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_loading
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_completed
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_pending
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_started
import mahjongtournamentsuite.adminapp.generated.resources.pc_locked_title
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_fits
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_less
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_more_or_less
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_none
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_marked_teams_none
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
import mahjongtournamentsuite.adminapp.generated.resources.pc_removed_teams
import mahjongtournamentsuite.adminapp.generated.resources.pc_saving
import mahjongtournamentsuite.adminapp.generated.resources.pc_step_choose
import mahjongtournamentsuite.adminapp.generated.resources.pc_step_done
import mahjongtournamentsuite.adminapp.generated.resources.pc_step_review
import mahjongtournamentsuite.adminapp.generated.resources.pc_sure_text
import mahjongtournamentsuite.adminapp.generated.resources.pc_top_bar
import mahjongtournamentsuite.adminapp.generated.resources.pc_waiting_none
import mahjongtournamentsuite.adminapp.generated.resources.pc_waiting_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_calculating
import mahjongtournamentsuite.adminapp.generated.resources.search
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
    val step = when {
        tournament == null || blocker != null -> null
        result != null -> 2
        loaded == null -> null
        working || reviewing -> 1
        else -> 0
    }
    PlayerCountFrame(
        subtitle = tournament?.name,
        step = step,
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
            loaded == null -> MtsDialogText(stringResource(Res.string.pc_loading))
            working -> WorkingContent(
                text = tries?.let { stringResource(Res.string.reg_draw_calculating, it) } ?: stringResource(Res.string.pc_saving),
                backup = tries != null && backup,
            )
            reviewing -> ReviewContent(
                summary = PlayerCountSummary.of(tournament, loaded, form),
                onConfirm = { sure = true },
                onBack = { reviewing = false },
            )
            else -> ChooseContent(
                tournament = tournament,
                data = loaded,
                form = form,
                onFormChange = { form = it },
                onContinue = { reviewing = true },
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
                    val removed = PlayerCountRules.removedSlots(tournament.isTeams, form.chosen, loaded.teams)
                    val target = form.targetOf(tournament, loaded)
                    val changed = presenter.change(tournament, loaded.slots, target, removed, form.waiting) {
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
 * The choice of the organizer: the slots or teams marked to leave, the tables added, and the people of the waiting
 * list who take the new places. Removing and adding never go together.
 */
internal data class PlayerCountForm(
    val chosen: Set<Int> = emptySet(),
    val addedTables: Int = 0,
    val waiting: List<String> = emptyList(),
    /** The search of the list: it only hides lines, the people marked stay marked. */
    val query: String = "",
) {
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

    fun removedCount(isTeams: Boolean, teams: List<TournamentTeam>): Int = PlayerCountRules.removedSlots(isTeams, chosen, teams).size

    fun targetOf(tournament: Tournament, data: PlayerCountData): Int =
        PlayerCountRules.targetOf(tournament.numPlayers, removedCount(tournament.isTeams, data.teams), addedTables)

    companion object {
        /** Keeps the choice through a recreation of the screen, such as a rotation. */
        val Saver: Saver<PlayerCountForm, Any> = Saver(
            save = { listOf(it.chosen.toList(), it.addedTables, it.waiting, it.query) },
            restore = { saved ->
                val values = saved as List<*>
                PlayerCountForm(
                    chosen = (values[0] as List<*>).map { it as Int }.toSet(),
                    addedTables = values[1] as Int,
                    waiting = (values[2] as List<*>).map { it as String },
                    query = values[3] as String,
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
            val target = form.targetOf(tournament, data)
            return PlayerCountSummary(
                from = tournament.numPlayers,
                to = target,
                rounds = tournament.numRounds,
                isTeams = tournament.isTeams,
                hasRegistrations = tournament.registration != null,
                leaving = leaving,
                newSlots = PlayerCountRules.newSlots(tournament.numPlayers, target),
                fromWaiting = form.waiting.map { waitingNames[it] ?: it },
            )
        }
    }
}

internal fun PlayerCountSlot.label(): String = "#$id · $name"

/** The lines that match the search: by slot number, name or EMA number; a team also by its players. */
internal fun PlayerCountSlot.matches(query: String): Boolean =
    query.isBlank() || "#$id $name ${emaId.orEmpty()}".matchesSearch(query) || id.toString() == query.trim().removePrefix("#")

internal fun TournamentTeam.matches(query: String, slots: List<PlayerCountSlot>): Boolean =
    query.isBlank() || name.matchesSearch(query) || slots.filter { it.id in playerIds }.any { it.matches(query) }

internal fun PlayerCountWaiting.matches(query: String): Boolean =
    query.isBlank() || "$name $emaId".matchesSearch(query) || position.toString() == query.trim()

/** The top bar, the steps ([step] null hides them) and the content of each step, in the width of a form. */
@Composable
internal fun PlayerCountFrame(
    subtitle: String?,
    step: Int?,
    isLoading: Boolean = false,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.pc_top_bar),
        subtitle = subtitle,
        isLoading = isLoading,
        onBack = onBack,
        // The ⓘ is the first control: focused at once, it would open its tooltip.
        autoFocusFirst = false,
    ) {
        ScreenColumn(
            maxWidth = MtsTheme.contentWidth.screen,
            contentPadding = PaddingValues(horizontal = MtsTheme.layout.screenGutter, vertical = MtsTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        ) {
            if (step != null) StepIndicator(step)
            content()
        }
    }
}

/** The three steps, with the current one highlighted: choose, review, done. */
@Composable
private fun StepIndicator(current: Int) {
    val labels = listOf(
        stringResource(Res.string.pc_step_choose),
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
    val target = form.targetOf(tournament, data)
    val newSlots = PlayerCountRules.newSlots(current, target)
    val maxRemovable = PlayerCountRules.maxRemovable(current)
    val maxChosen = if (isTeams) maxRemovable / PlayerCountRules.STEP else maxRemovable
    val complete = PlayerCountRules.isChoiceComplete(current, target, isTeams, form.chosen, data.teams, form.waiting)
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
                    onChange = { onFormChange(form.withAddedTables(it)) },
                )
                when {
                    form.chosen.isNotEmpty() -> Hint(stringResource(Res.string.pc_add_locked))
                    adding -> Hint(
                        if (isTeams) {
                            stringResource(Res.string.pc_new_teams, newSlots / PlayerCountRules.STEP)
                        } else {
                            stringResource(Res.string.pc_new_slots, newSlots)
                        },
                    )
                }
            }
        }
        if (adding && tournament.registration != null) {
            item(key = "waiting-title") {
                Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                    ChoiceHeader(stringResource(Res.string.pc_waiting_title, newSlots))
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
                SectionTitle(stringResource(if (isTeams) Res.string.pc_leave_teams else Res.string.pc_leave_people))
                MarkedStatus(current, isTeams, form.chosen.size)
                when {
                    adding -> Hint(stringResource(Res.string.pc_remove_locked))
                    maxChosen == 0 -> Hint(stringResource(Res.string.pc_minimum, current))
                }
                if (!adding && maxChosen > 0) ChoiceSearch(form, onFormChange)
            }
        }
        if (!adding && maxChosen > 0) {
            val full = form.chosen.size >= maxChosen
            if (isTeams) {
                val shown = data.teams.filter { it.matches(form.query, data.slots) }
                if (shown.isEmpty() && form.query.isNotBlank()) item(key = "teams-none") { Hint(stringResource(Res.string.pc_no_match)) }
                items(shown, key = { "team-${it.id}" }) { team ->
                    val checked = team.id in form.chosen
                    ChoiceRow(
                        title = team.name,
                        detail = team.memberNames(data.slots),
                        checked = checked,
                        enabled = checked || !full,
                        tag = "playerCountTeam-${team.id}",
                        onToggle = { onFormChange(form.toggleChosen(team.id, maxChosen)) },
                    )
                }
            } else {
                val shown = data.slots.filter { it.matches(form.query) }
                if (shown.isEmpty() && form.query.isNotBlank()) item(key = "slots-none") { Hint(stringResource(Res.string.pc_no_match)) }
                items(shown, key = { "slot-${it.id}" }) { slot ->
                    val checked = slot.id in form.chosen
                    ChoiceRow(
                        title = if (slot.emaId == null) stringResource(Res.string.pc_empty_slot, slot.id) else slot.label(),
                        detail = slot.emaId,
                        checked = checked,
                        enabled = checked || !full,
                        tag = "playerCountSlot-${slot.id}",
                        onToggle = { onFormChange(form.toggleChosen(slot.id, maxChosen)) },
                    )
                }
            }
        }
    }
    MtsDialogButtons {
        Button(
            onClick = onContinue,
            enabled = complete,
            buttonModifier = Modifier.testTag("playerCountContinue"),
        ) { Text(stringResource(Res.string.pc_continue)) }
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
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
                    color = if (target != current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
private fun MarkedStatus(current: Int, isTeams: Boolean, marked: Int) {
    val fit = PlayerCountRules.markedFit(current, if (isTeams) marked * PlayerCountRules.STEP else marked)
    val text = when {
        isTeams && marked == 0 -> stringResource(Res.string.pc_marked_teams_none)
        isTeams -> stringResource(Res.string.pc_marked_teams, marked)
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
            fit.fits -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
        modifier = Modifier.testTag("playerCountChosen"),
    )
}

/** − tables added +, with whole tables of 4. */
@Composable
private fun AddTables(added: Int, canAdd: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FocusedIconButton(
            onClick = { onChange(added - 1) },
            enabled = added > 0,
            buttonModifier = Modifier.stepBorder(added > 0).testTag("playerCountLess"),
        ) { Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.pc_remove_table)) }
        Text(
            stringResource(Res.string.pc_added_tables, added),
            modifier = Modifier.padding(horizontal = 16.dp).testTag("playerCountAdded"),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        FocusedIconButton(
            onClick = { onChange(added + 1) },
            enabled = canAdd,
            buttonModifier = Modifier.stepBorder(canAdd).testTag("playerCountMore"),
        ) { Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.pc_add_table)) }
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

internal fun TournamentTeam.memberNames(slots: List<PlayerCountSlot>): String {
    val byId = slots.associateBy { it.id }
    return playerIds.mapNotNull { byId[it] }.joinToString(", ") { if (it.emaId == null) "#${it.id}" else it.name }
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
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("playerCountConfirm"),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        scrollbar = MtsScrollbar.Margin,
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
        ) { Text(stringResource(Res.string.pc_confirm)) }
        TextButton(onClick = onBack, modifier = Modifier.testTag("playerCountBack")) { Text(stringResource(Res.string.pc_back)) }
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
                    ) { Text(stringResource(Res.string.pc_confirm)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** A label and its bullet lines; each line hangs under its bullet. */
@Composable
private fun BulletGroup(label: String, items: List<String>, tag: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = if (tag == null) Modifier else Modifier.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        items.forEach { item ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium)
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
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("playerCountDone"),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        scrollbar = MtsScrollbar.Margin,
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
        MtsDialogText(stringResource(Res.string.pc_emails_sent, emailsSent), modifier = Modifier.testTag("playerCountEmailsSent"))
        // The organizer tells these people by hand: they have no email, or the email failed.
        if (noEmail.isNotEmpty()) BulletGroup(stringResource(Res.string.pc_emails_none), noEmail, "playerCountNoEmail")
        if (emailFailed.isNotEmpty()) BulletGroup(stringResource(Res.string.pc_emails_failed), emailFailed, "playerCountEmailFailed")
    }
    MtsDialogButtons {
        Button(onClick = onClose, buttonModifier = Modifier.testTag("playerCountClose")) { Text(stringResource(Res.string.close)) }
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
        OutlinedButton(onClick = onClose) { Text(stringResource(Res.string.close)) }
    }
}
