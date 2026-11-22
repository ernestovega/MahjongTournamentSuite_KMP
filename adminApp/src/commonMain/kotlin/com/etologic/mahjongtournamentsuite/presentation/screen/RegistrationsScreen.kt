package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MAX_FOOD_RESTRICTIONS_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.MAX_REGISTRATION_TEAM_NAME_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationUpdate
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.DateField
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationButton
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerPhoto
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberSpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.platform.saveTextFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.RegistrationsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.reg_add
import mahjongtournamentsuite.adminapp.generated.resources.reg_add_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_close_action
import mahjongtournamentsuite.adminapp.generated.resources.reg_closes_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_closes_on_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_col_food
import mahjongtournamentsuite.adminapp.generated.resources.reg_col_player
import mahjongtournamentsuite.adminapp.generated.resources.reg_col_team
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirm_remove_message
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirm_remove_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirmed_section
import mahjongtournamentsuite.adminapp.generated.resources.reg_copy
import mahjongtournamentsuite.adminapp.generated.resources.reg_copy_link
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_calculating
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_confirm
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_message
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_needs_close
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_edit
import mahjongtournamentsuite.adminapp.generated.resources.reg_edit_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_email_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_empty_message
import mahjongtournamentsuite.adminapp.generated.resources.reg_empty_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_export
import mahjongtournamentsuite.adminapp.generated.resources.reg_filter_all
import mahjongtournamentsuite.adminapp.generated.resources.reg_food
import mahjongtournamentsuite.adminapp.generated.resources.reg_food_of
import mahjongtournamentsuite.adminapp.generated.resources.reg_give_place
import mahjongtournamentsuite.adminapp.generated.resources.reg_import
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_apply
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_choose
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_info
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_ready
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_result
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_copied
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_name
import mahjongtournamentsuite.adminapp.generated.resources.reg_more_actions
import mahjongtournamentsuite.adminapp.generated.resources.reg_move_to_waiting
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_account
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_close_date
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_limit
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_matches
import mahjongtournamentsuite.adminapp.generated.resources.reg_notify
import mahjongtournamentsuite.adminapp.generated.resources.reg_open_action
import mahjongtournamentsuite.adminapp.generated.resources.reg_paid
import mahjongtournamentsuite.adminapp.generated.resources.reg_paid_count
import mahjongtournamentsuite.adminapp.generated.resources.reg_pending_section
import mahjongtournamentsuite.adminapp.generated.resources.reg_pick_person
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_value
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_already_registered
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_duplicate_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_ema_not_found
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_invalid_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_invalid_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_remove
import mahjongtournamentsuite.adminapp.generated.resources.reg_save
import mahjongtournamentsuite.adminapp.generated.resources.reg_shared_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_state_closed
import mahjongtournamentsuite.adminapp.generated.resources.reg_state_open
import mahjongtournamentsuite.adminapp.generated.resources.reg_status_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_team_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_waiting_and_pending
import mahjongtournamentsuite.adminapp.generated.resources.reg_waiting_section
import mahjongtournamentsuite.adminapp.generated.resources.reg_without_team
import mahjongtournamentsuite.adminapp.generated.resources.search
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton

/** The setting of the registrations that is open for a change on the screen, if any. */
private enum class RegistrationSettingField { PLACES, CLOSES_ON, LINK }

/** The lists of the registrations, in the order of the screen and of the export, with their titles and colors. */
private val RegistrationSections = listOf(
    RegistrationStatus.CONFIRMED to Res.string.reg_confirmed_section,
    RegistrationStatus.WAITING to Res.string.reg_waiting_section,
    RegistrationStatus.PENDING to Res.string.reg_pending_section,
)

private val PositionColumnWidth = 36.dp
private val PhotoSize = 36.dp
private val FoodColumnWidth = 48.dp
private val PaidColumnWidth = 72.dp
private val ActionsColumnWidth = 48.dp

/**
 * The registrations of a tournament before its draw: places, waiting list and people who did not confirm the email
 * yet. The organizers change the settings (open or closed, places, last day, public link) on the screen itself, add,
 * change and remove people, export the list as a CSV file that the import reads back, import the old Google Form
 * and make the draw with the confirmed people. See docs/registrations.md.
 */
@Composable
fun RegistrationsScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val presenter = koinInject<RegistrationsPresenter>()
    val store = koinInject<AppMemoryStore>()
    LoadMissingSession()
    val tournaments by store.tournaments.collectAsState()
    val tournament = tournaments.firstOrNull { it.id == tournamentId }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val compact = LocalCompactLayout.current
    var data by remember { mutableStateOf<AdminRegistrations?>(null) }
    var registry by remember { mutableStateOf<List<Player>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var info by remember { mutableStateOf<UiText?>(null) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<RegistrationStatus?>(null) }
    var editingSetting by remember { mutableStateOf<RegistrationSettingField?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminRegistration?>(null) }
    var removing by remember { mutableStateOf<AdminRegistration?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var drawConfirm by remember { mutableStateOf(false) }
    var drawTries by remember { mutableStateOf<Long?>(null) }
    val linkCopied = stringResource(Res.string.reg_link_copied)
    // Copying the link shows a short message at the bottom, never a dialog.
    val snackbar = remember { SnackbarHostState() }

    fun refresh() = scope.launch {
        loading = true
        when (val result = presenter.load(tournamentId)) {
            is AppResult.Success -> data = result.value
            is AppResult.Failure -> error = result.error.toUiText()
        }
        loading = false
    }

    /** Runs a change, shows its error, and reads the list again. */
    fun change(block: suspend () -> AppResult<*>) = scope.launch {
        saving = true
        when (val result = block()) {
            is AppResult.Success -> refresh()
            is AppResult.Failure -> error = result.error.toUiText()
        }
        saving = false
    }

    fun saveSettings(settings: RegistrationSettings) {
        editingSetting = null
        change { presenter.saveSettings(tournamentId, settings) }
    }

    LaunchedEffect(tournamentId) {
        refresh()
        (presenter.loadRegistry() as? AppResult.Success)?.let { registry = it.value }
    }

    val current = data
    val playersByEma = remember(registry) { registry.associateBy { it.emaId } }
    val normalizedQuery = normalizeSearchText(query.trim())
    val matching = current?.registrations.orEmpty().filter { registration ->
        normalizedQuery.isEmpty() ||
            normalizeSearchText("${registration.name} ${registration.emaId} ${registration.email} ${registration.country} ${current?.teamOf(registration)?.name.orEmpty()}")
                .contains(normalizedQuery)
    }
    val shown = matching.filter { statusFilter == null || it.status == statusFilter }

    AppScaffold(
        title = stringResource(Res.string.reg_title),
        subtitle = tournament?.name,
        isLoading = loading || saving,
        showBackgroundLogo = loading && current == null,
        onBack = { navController.popBackStack() },
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            AppTopBarButton(
                text = stringResource(Res.string.reg_export),
                icon = Icons.Default.Download,
                onClick = {
                    val ready = current ?: return@AppTopBarButton
                    val name = tournament?.shortName?.ifBlank { null } ?: tournament?.name.orEmpty()
                    val saved = presenter.exportCsv(name, ready, registry) { fileName, content -> saveTextFile(fileName, content) }
                    if (saved is AppResult.Failure) error = saved.error.toUiText()
                },
                enabled = current != null && current.registrations.isNotEmpty(),
            )
            AppTopBarButton(
                text = stringResource(Res.string.reg_import),
                icon = Icons.Default.UploadFile,
                onClick = { importOpen = true },
                enabled = current != null,
            )
        },
    ) {
        ScreenColumn(
            maxWidth = 1100.dp,
            contentPadding = PaddingValues(horizontal = MtsTheme.layout.screenGutter, vertical = 0.dp),
        ) {
            error?.let { AppErrorDialog(message = it, onDismiss = { error = null }) }
            info?.let { message ->
                TrackedDialog("RegistrationsInfo") {
                    AlertDialog(
                        onDismissRequest = { info = null },
                        text = { MtsDialogText(message.asString()) },
                        confirmButton = { MtsDialogButtons { FocusedButton(onClick = { info = null }) { Text(stringResource(Res.string.ok)) } } },
                    )
                }
            }
            if (current != null) {
                val busy = saving || drawTries != null
                LazyColumnWithScrollbar(
                    state = rememberLazyListState(),
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("registrations-list"),
                    contentPadding = PaddingValues(vertical = MtsTheme.layout.screenGutter),
                    scrollbar = MtsScrollbar.Margin,
                ) {
                    item(key = "settings") {
                        RegistrationSettingsPanel(
                            data = current,
                            editing = editingSetting,
                            busy = busy,
                            onEdit = { editingSetting = it },
                            onCancel = { editingSetting = null },
                            onSave = ::saveSettings,
                            onCopyLink = {
                                clipboard.setText(AnnotatedString(current.publicLink))
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    snackbar.showSnackbar(linkCopied)
                                }
                            },
                        )
                    }
                    item(key = "actions") {
                        RegistrationActions(
                            canDraw = current.settings.status == RegistrationPhase.CLOSED,
                            busy = busy,
                            onAdd = { adding = true },
                            onDraw = { drawConfirm = true },
                        )
                    }
                    if (current.registrations.isEmpty()) {
                        item(key = "empty") {
                            AppEmptyState(
                                icon = Icons.Default.HowToReg,
                                title = stringResource(Res.string.reg_empty_title),
                                message = stringResource(Res.string.reg_empty_message),
                            )
                        }
                    } else {
                        item(key = "filters") {
                            RegistrationFilters(
                                query = query,
                                onQueryChange = { query = it },
                                matching = matching,
                                selected = statusFilter,
                                onSelect = { statusFilter = it },
                                paid = current.registrations.count { it.status == RegistrationStatus.CONFIRMED && it.paid },
                                confirmed = current.registrations.count { it.status == RegistrationStatus.CONFIRMED },
                            )
                        }
                        if (shown.isEmpty()) {
                            item(key = "no-matches") {
                                Text(
                                    stringResource(Res.string.reg_no_matches),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = MtsTheme.spacing.lg),
                                )
                            }
                        } else {
                            if (!compact) {
                                item(key = "header") { RegistrationsHeaderRow(isTeams = current.isTeams) }
                            }
                            RegistrationSections.forEach { (status, title) ->
                                val rows = shown.filter { it.status == status }.sortedBy { it.position }
                                if (rows.isNotEmpty()) {
                                    item(key = "title-$status") { SectionTitle(status = status, title = title, count = rows.size) }
                                    items(rows, key = { "${it.status}-${it.emaId}" }) { registration ->
                                        val player = playersByEma[registration.emaId]
                                        RegistrationRow(
                                            registration = registration,
                                            displayName = player?.let { "${it.firstName} ${it.lastName}".trim() }?.ifBlank { null } ?: registration.name.ifBlank { registration.emaId },
                                            photoUrl = player?.photoUrl,
                                            teamName = current.teamOf(registration)?.name,
                                            isTeams = current.isTeams,
                                            enabled = !busy,
                                            onPaidChange = { paid ->
                                                change { presenter.update(tournamentId, registration.emaId, RegistrationUpdate(paid = paid)) }
                                            },
                                            onEdit = { editing = registration },
                                            onMove = { newStatus ->
                                                change { presenter.update(tournamentId, registration.emaId, RegistrationUpdate(status = newStatus)) }
                                            },
                                            onRemove = { removing = registration },
                                        )
                                        DataTableDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding && current != null) {
        RegistrationEditDialog(
            title = stringResource(Res.string.reg_add_title),
            registration = null,
            registry = registry,
            isTeams = current.isTeams,
            teamName = "",
            onSave = { emaId, email, teamName, food, paid, notify ->
                adding = false
                change {
                    presenter.add(
                        tournamentId,
                        NewRegistration(emaId = emaId, email = email, paid = paid, foodRestrictions = food, teamName = teamName, notify = notify),
                    )
                }
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { registration ->
        RegistrationEditDialog(
            title = stringResource(Res.string.reg_edit_title),
            registration = registration,
            registry = registry,
            isTeams = current?.isTeams == true,
            teamName = current?.teamOf(registration)?.name.orEmpty(),
            onSave = { _, email, teamName, food, paid, _ ->
                editing = null
                change {
                    presenter.update(
                        tournamentId,
                        registration.emaId,
                        RegistrationUpdate(
                            email = email,
                            paid = paid,
                            teamName = teamName.takeIf { current?.isTeams == true },
                            foodRestrictions = food,
                        ),
                    )
                }
            },
            onDismiss = { editing = null },
        )
    }
    removing?.let { registration ->
        var notify by remember(registration.emaId) { mutableStateOf(true) }
        TrackedDialog("RemoveRegistration") {
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { MtsDialogTitle(stringResource(Res.string.reg_confirm_remove_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MtsDialogText(stringResource(Res.string.reg_confirm_remove_message, registration.name))
                        if (registration.email.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = notify, onCheckedChange = { notify = it })
                                Text(stringResource(Res.string.reg_notify))
                            }
                        }
                    }
                },
                confirmButton = {
                    MtsDialogButtons {
                        FocusedButton(
                            onClick = {
                                removing = null
                                change { presenter.remove(tournamentId, registration.emaId, notify) }
                            },
                            colors = MtsButtons.destructiveColors(),
                        ) { Text(stringResource(Res.string.reg_remove)) }
                        FocusedTextButton(onClick = { removing = null }) { Text(stringResource(Res.string.cancel)) }
                    }
                },
            )
        }
    }
    if (importOpen) {
        RegistrationImportDialog(
            preview = { name, bytes -> presenter.previewImport(tournamentId, name, bytes) },
            apply = { name, bytes -> presenter.applyImport(tournamentId, name, bytes) },
            onDone = { imported ->
                importOpen = false
                if (imported != null) {
                    refresh()
                    info = UiText.of(Res.string.reg_import_result, imported)
                }
            },
        )
    }
    if (drawConfirm && current != null && tournament != null) {
        MakeDrawDialog(
            confirmed = current.settings.confirmed,
            rounds = tournament.numRounds,
            tries = drawTries,
            onConfirm = {
                drawTries = 0
                scope.launch {
                    val result = presenter.makeDraw(tournament, current) { progress ->
                        scope.launch(Dispatchers.Main) { if (drawTries != null) drawTries = progress.tried }
                    }
                    drawTries = null
                    drawConfirm = false
                    when (result) {
                        is AppResult.Success -> {
                            store.addTournament(result.value)
                            withContext(Dispatchers.Main) {
                                navController.navigate(TournamentRoute(tournamentId = tournamentId)) {
                                    popUpTo(TournamentsRoute)
                                }
                            }
                        }
                        is AppResult.Failure -> error = result.error.toUiText()
                    }
                }
            },
            onDismiss = { drawConfirm = false },
        )
    }
}

/**
 * Confirms the draw of the confirmed people when the registrations are closed, then shows its progress ([tries] not null).
 * As in Create tournament, "How does the draw work?" is centered just above the buttons.
 */
@Composable
internal fun MakeDrawDialog(
    confirmed: Int,
    rounds: Int,
    tries: Long?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDrawExplanation by remember { mutableStateOf(false) }
    TrackedDialog("MakeDraw") {
        AlertDialog(
            onDismissRequest = { if (tries == null) onDismiss() },
            title = { MtsDialogTitle(stringResource(Res.string.reg_draw_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tries == null) {
                        MtsDialogText(stringResource(Res.string.reg_draw_message, confirmed, rounds))
                    } else {
                        MtsDialogText(stringResource(Res.string.reg_draw_calculating, tries))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (tries == null) {
                        DrawExplanationButton(onClick = { showDrawExplanation = true })
                        Spacer(Modifier.height(8.dp))
                    }
                    MtsDialogButtons {
                        FocusedButton(enabled = tries == null, onClick = onConfirm) { Text(stringResource(Res.string.reg_draw_confirm)) }
                        if (tries == null) FocusedTextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    }
                }
            },
        )
    }
    if (showDrawExplanation) {
        DrawExplanationDialog(onDismiss = { showDrawExplanation = false })
    }
}

/**
 * A button with an icon and its text, or only the icon on a phone (the text stays as its description). Every button
 * of the screen shows its text when there is room for it.
 */
@Composable
private fun LabeledIconButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    if (LocalCompactLayout.current) {
        FocusedIconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = text)
        }
    } else {
        FocusedTextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, modifier = Modifier.padding(start = MtsTheme.spacing.sm))
        }
    }
}

/**
 * The settings of the registrations as tiles: open or closed, places, last day and public link. Each one changes on
 * the screen: the status with one button, the others with Change, which turns the tile into its field.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RegistrationSettingsPanel(
    data: AdminRegistrations,
    editing: RegistrationSettingField?,
    busy: Boolean,
    onEdit: (RegistrationSettingField) -> Unit,
    onCancel: () -> Unit,
    onSave: (RegistrationSettings) -> Unit,
    onCopyLink: () -> Unit,
) {
    val settings = data.settings
    val compact = LocalCompactLayout.current
    val pending = data.registrations.count { it.status == RegistrationStatus.PENDING }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
        val statusTile: @Composable (Modifier) -> Unit = { tileModifier ->
            val toggleText = stringResource(if (settings.isOpen) Res.string.reg_close_action else Res.string.reg_open_action)
            val toggleIcon = if (settings.isOpen) Icons.Default.Lock else Icons.Default.LockOpen
            val toggle = { onSave(settings.copy(status = if (settings.isOpen) RegistrationPhase.CLOSED else RegistrationPhase.OPEN)) }
            SettingTile(
                label = stringResource(Res.string.reg_status_label),
                modifier = tileModifier,
                // A phone has the icon in the row of the title; a wide screen, the button with its text under the status.
                action = if (compact) {
                    {
                        LabeledIconButton(
                            text = toggleText,
                            icon = toggleIcon,
                            enabled = !busy,
                            onClick = toggle,
                            modifier = Modifier.testTag("registrations-toggle-status"),
                        )
                    }
                } else {
                    null
                },
            ) {
                val color = if (settings.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(
                        stringResource(if (settings.isOpen) Res.string.reg_state_open else Res.string.reg_state_closed),
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                }
                if (!compact) {
                    OutlinedButton(onClick = toggle, enabled = !busy, modifier = Modifier.padding(top = MtsTheme.spacing.sm).testTag("registrations-toggle-status")) {
                        Icon(toggleIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(toggleText, modifier = Modifier.padding(start = MtsTheme.spacing.sm))
                    }
                }
            }
        }
        val placesTile: @Composable (Modifier) -> Unit = { tileModifier ->
            EditableSettingTile(
                label = stringResource(Res.string.reg_places_label),
                modifier = tileModifier,
                editing = editing == RegistrationSettingField.PLACES,
                busy = busy,
                onEdit = { onEdit(RegistrationSettingField.PLACES) },
                editor = {
                    var places by remember { mutableStateOf(settings.maxPlayers?.toString().orEmpty()) }
                    OutlinedTextField(
                        value = places,
                        onValueChange = { typed -> places = typed.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(Res.string.reg_places_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("registrations-places-field"),
                    )
                    EditorButtons(onCancel = onCancel, onSave = { onSave(settings.copy(maxPlayers = places.toIntOrNull())) })
                },
            ) {
                Text(
                    stringResource(Res.string.reg_places_value, settings.confirmed, settings.maxPlayers?.toString() ?: stringResource(Res.string.reg_no_limit)),
                    style = MaterialTheme.typography.titleMedium,
                )
                settings.maxPlayers?.takeIf { it > 0 }?.let { max ->
                    LinearProgressIndicator(
                        progress = { (settings.confirmed.toFloat() / max).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = MtsTheme.spacing.xs),
                    )
                }
                Text(
                    stringResource(Res.string.reg_waiting_and_pending, settings.waiting, pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val closesTile: @Composable (Modifier) -> Unit = { tileModifier ->
            EditableSettingTile(
                label = stringResource(Res.string.reg_closes_label),
                modifier = tileModifier,
                editing = editing == RegistrationSettingField.CLOSES_ON,
                busy = busy,
                onEdit = { onEdit(RegistrationSettingField.CLOSES_ON) },
                editor = {
                    var closesOn by remember { mutableStateOf(settings.closesOn.orEmpty()) }
                    DateField(
                        label = stringResource(Res.string.reg_closes_on_optional),
                        value = closesOn,
                        onChange = { closesOn = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EditorButtons(onCancel = onCancel, onSave = { onSave(settings.copy(closesOn = closesOn.ifBlank { null })) })
                },
            ) {
                val closesOn = settings.closesOn
                if (closesOn == null) {
                    Text(stringResource(Res.string.reg_no_close_date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(formatLocalizedDate(closesOn), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        val linkTile: @Composable (Modifier) -> Unit = { tileModifier ->
            EditableSettingTile(
                label = stringResource(Res.string.reg_link_label),
                modifier = tileModifier,
                editing = editing == RegistrationSettingField.LINK,
                busy = busy,
                onEdit = { onEdit(RegistrationSettingField.LINK) },
                extraAction = {
                    LabeledIconButton(
                        text = stringResource(Res.string.reg_copy),
                        icon = Icons.Default.ContentCopy,
                        onClick = onCopyLink,
                        modifier = Modifier.testTag("registrations-copy-link"),
                    )
                },
                editor = {
                    var slug by remember { mutableStateOf(settings.slug) }
                    OutlinedTextField(
                        value = slug,
                        onValueChange = { typed -> slug = typed.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.take(40) },
                        label = { Text(stringResource(Res.string.reg_link_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("registrations-slug-field"),
                    )
                    EditorButtons(onCancel = onCancel, saveEnabled = slug.isNotBlank(), onSave = { onSave(settings.copy(slug = slug)) })
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                    Icon(Icons.Default.Link, contentDescription = stringResource(Res.string.reg_copy_link), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    SelectionContainer(Modifier.weight(1f, fill = false)) {
                        Text(
                            data.publicLink,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        val showLink = data.publicLink.isNotBlank() || editing == RegistrationSettingField.LINK
        if (compact) {
            // A phone shows the settings as the rows of one card, so the list starts higher.
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(horizontal = MtsTheme.layout.cardPadding)) {
                    listOfNotNull(statusTile, placesTile, closesTile, linkTile.takeIf { showLink }).forEachIndexed { index, tile ->
                        if (index > 0) HorizontalDivider()
                        tile(Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
                listOf(statusTile, placesTile, closesTile).forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
            if (showLink) linkTile(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingTile(
    label: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val body: @Composable (Modifier) -> Unit = { padding ->
        Column(padding, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            content()
        }
    }
    if (LocalCompactLayout.current) {
        // A row of the card of the settings (see RegistrationSettingsPanel).
        body(modifier.padding(vertical = MtsTheme.spacing.sm))
    } else {
        Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            body(Modifier.padding(MtsTheme.layout.cardPadding))
        }
    }
}

/** A [SettingTile] with a Change button that turns its content into [editor], with its own Cancel and Save. */
@Composable
private fun EditableSettingTile(
    label: String,
    modifier: Modifier,
    editing: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    editor: @Composable () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SettingTile(
        label = label,
        modifier = modifier,
        action = if (editing) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    extraAction?.invoke()
                    LabeledIconButton(text = stringResource(Res.string.reg_edit), icon = Icons.Default.Edit, enabled = !busy, onClick = onEdit)
                }
            }
        },
    ) {
        if (editing) {
            Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) { editor() }
        } else {
            content()
        }
    }
}

@Composable
private fun EditorButtons(onCancel: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        FocusedTextButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.cancel), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
        }
        FocusedButton(onClick = onSave, enabled = saveEnabled) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.reg_save), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
        }
    }
}

/** Add a person and Make the draw. The info icon says why the draw waits, centered with the buttons. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RegistrationActions(canDraw: Boolean, busy: Boolean, onAdd: () -> Unit, onDraw: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = MtsTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FocusedButton(onClick = onAdd, enabled = !busy) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.reg_add), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
            }
            OutlinedButton(onClick = onDraw, enabled = !busy && canDraw) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.reg_draw), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
            }
            if (!canDraw && !LocalCompactLayout.current) InfoTooltipIcon(stringResource(Res.string.reg_draw_needs_close))
        }
        if (!canDraw && LocalCompactLayout.current) {
            // A phone has no room for the icon beside the buttons: the reason goes under them.
            Text(
                stringResource(Res.string.reg_draw_needs_close),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MtsTheme.spacing.xs),
            )
        }
    }
}

/** The search and one chip per list, with the number of people that match the search. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RegistrationFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    matching: List<AdminRegistration>,
    selected: RegistrationStatus?,
    onSelect: (RegistrationStatus?) -> Unit,
    paid: Int,
    confirmed: Int,
) {
    Column(Modifier.padding(top = MtsTheme.spacing.xl, bottom = MtsTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(Res.string.search)) },
            singleLine = true,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    FocusedIconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().testTag("registrations-search"),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.reg_filter_all, matching.size)) },
            )
            RegistrationSections.forEach { (status, title) ->
                FilterChip(
                    selected = selected == status,
                    onClick = { onSelect(if (selected == status) null else status) },
                    label = { Text(stringResource(title, matching.count { it.status == status })) },
                    leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(status))) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.reg_paid_count, paid, confirmed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusColor(status: RegistrationStatus): Color = when (status) {
    RegistrationStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
    RegistrationStatus.WAITING -> MtsTheme.colors.warning
    RegistrationStatus.PENDING -> MaterialTheme.colorScheme.outline
}

@Composable
private fun RegistrationsHeaderRow(isTeams: Boolean) {
    Column {
        DataTableHeaderRow {
            HeaderText("#", Modifier.width(PositionColumnWidth), TextAlign.Center)
            Spacer(Modifier.width(PhotoSize))
            HeaderText(stringResource(Res.string.reg_col_player), Modifier.weight(0.34f))
            HeaderText(stringResource(Res.string.reg_email), Modifier.weight(0.3f))
            if (isTeams) HeaderText(stringResource(Res.string.reg_col_team), Modifier.weight(0.2f))
            HeaderText(stringResource(Res.string.reg_col_food), Modifier.width(FoodColumnWidth), TextAlign.Center, maxLines = 1)
            HeaderText(stringResource(Res.string.reg_paid), Modifier.width(PaidColumnWidth), TextAlign.Center)
            Spacer(Modifier.width(ActionsColumnWidth))
        }
        DataTableDivider()
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier, textAlign: TextAlign = TextAlign.Start, maxLines: Int = 2) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The title of one list: a colored mark, its name and how many people it has. */
@Composable
private fun SectionTitle(status: RegistrationStatus, title: StringResource, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MtsTheme.spacing.lg, bottom = MtsTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
    ) {
        Box(Modifier.width(4.dp).height(20.dp).clip(MtsTheme.shapes.pill).background(statusColor(status)))
        Text(stringResource(title, count), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * One person. A wide screen shows a row of the table; a phone shows the name over the EMA number, the team and the
 * email, with Paid and the menu at the end.
 */
@Composable
private fun RegistrationRow(
    registration: AdminRegistration,
    displayName: String,
    photoUrl: String?,
    teamName: String?,
    isTeams: Boolean,
    enabled: Boolean,
    onPaidChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (RegistrationStatus) -> Unit,
    onRemove: () -> Unit,
) {
    val compact = LocalCompactLayout.current
    val team = if (isTeams) teamName ?: stringResource(Res.string.reg_without_team) else null
    DataTableRow(
        modifier = Modifier.testTag("registration-${registration.emaId}"),
        onClick = if (enabled) onEdit else null,
        contentPadding = PaddingValues(horizontal = if (compact) 0.dp else 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) MtsTheme.spacing.sm else MtsTheme.spacing.md),
    ) {
        Text(
            if (registration.status == RegistrationStatus.PENDING) "—" else registration.position.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(if (compact) 24.dp else PositionColumnWidth),
        )
        PlayerPhoto(photoUrl = photoUrl, name = displayName, size = PhotoSize)
        if (compact) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameLine(displayName, registration.country)
                Text(
                    listOfNotNull("EMA ${registration.emaId}", team).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (registration.email.isNotBlank()) {
                    Text(registration.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RowNotes(registration, showFood = true)
            }
            PaidBox(registration, enabled, onPaidChange, compact = true)
        } else {
            Column(Modifier.weight(0.34f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameLine(displayName, registration.country)
                Text("EMA ${registration.emaId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(0.3f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(registration.email.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RowNotes(registration, showFood = false)
            }
            if (team != null) {
                Text(
                    team,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (teamName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.2f),
                )
            }
            Box(Modifier.width(FoodColumnWidth), contentAlignment = Alignment.Center) {
                if (registration.foodRestrictions.isNotBlank()) {
                    FoodIcon(registration.foodRestrictions)
                } else {
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PaidBox(registration, enabled, onPaidChange, compact = false)
        }
        RegistrationMenu(registration, displayName, enabled, onEdit, onMove, onRemove)
    }
}

@Composable
private fun NameLine(name: String, country: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        CountryFlag(code = country, width = 20.dp)
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** No account, an email that another person shares and, on a phone, the food restrictions. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RowNotes(registration: AdminRegistration, showFood: Boolean) {
    val food = showFood && registration.foodRestrictions.isNotBlank()
    if (registration.hasAccount && !registration.sharedEmail && !food) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
        if (!registration.hasAccount) {
            Text(stringResource(Res.string.reg_no_account), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (food) FoodIcon(registration.foodRestrictions, size = 18.dp)
        if (registration.sharedEmail) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.reg_shared_email), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The food restrictions are private: an icon with a tooltip, so the list stays short. */
@Composable
private fun FoodIcon(text: String, size: Dp = 22.dp) {
    com.etologic.mahjongtournamentsuite.presentation.components.AppTooltipBox(stringResource(Res.string.reg_food_of, text)) {
        FocusedIconButton(onClick = { toggleTooltip() }, buttonModifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Restaurant, contentDescription = stringResource(Res.string.reg_food), tint = MtsTheme.colors.warning, modifier = Modifier.size(size))
        }
    }
}

@Composable
private fun PaidBox(registration: AdminRegistration, enabled: Boolean, onPaidChange: (Boolean) -> Unit, compact: Boolean) {
    Column(
        Modifier.width(if (compact) 48.dp else PaidColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (registration.status != RegistrationStatus.PENDING) {
            Checkbox(checked = registration.paid, onCheckedChange = onPaidChange, enabled = enabled, modifier = Modifier.testTag("registration-paid-${registration.emaId}"))
            if (compact) Text(stringResource(Res.string.reg_paid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RegistrationMenu(
    registration: AdminRegistration,
    displayName: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onMove: (RegistrationStatus) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.width(ActionsColumnWidth), contentAlignment = Alignment.Center) {
        FocusedIconButton(onClick = { menu = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.reg_more_actions, displayName))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.reg_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    menu = false
                    onEdit()
                },
            )
            if (registration.status != RegistrationStatus.CONFIRMED) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.reg_give_place)) },
                    onClick = {
                        menu = false
                        onMove(RegistrationStatus.CONFIRMED)
                    },
                )
            }
            if (registration.status == RegistrationStatus.CONFIRMED) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.reg_move_to_waiting)) },
                    onClick = {
                        menu = false
                        onMove(RegistrationStatus.WAITING)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.reg_remove), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}

/** Adds a person ([registration] null) or changes one. */
@Composable
private fun RegistrationEditDialog(
    title: String,
    registration: AdminRegistration?,
    registry: List<Player>,
    isTeams: Boolean,
    teamName: String,
    onSave: (emaId: String, email: String, teamName: String, food: String, paid: Boolean, notify: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var person by remember { mutableStateOf(registration?.let { Player(it.emaId, it.name, "", it.country) }) }
    var picking by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(registration?.email.orEmpty()) }
    var team by remember { mutableStateOf(teamName) }
    var food by remember { mutableStateOf(registration?.foodRestrictions.orEmpty()) }
    var paid by remember { mutableStateOf(registration?.paid == true) }
    var notify by remember { mutableStateOf(true) }
    if (picking) {
        EmaPlayerPickerDialog(
            players = registry,
            owners = emptyMap(),
            selectedEmaId = person?.emaId,
            onSelect = {
                person = it
                picking = false
            },
            onDismiss = { picking = false },
        )
        return
    }
    TrackedDialog(if (registration == null) "AddRegistration" else "EditRegistration") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(title) },
            text = {
                Column(Modifier.verticalScrollWithIndicators(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (registration == null) {
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(person?.let { "${it.displayName.ifBlank { it.emaId }} · ${it.emaId}" } ?: stringResource(Res.string.reg_pick_person))
                        }
                    } else {
                        Text("${registration.name} · ${registration.emaId}", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text(stringResource(if (registration == null) Res.string.reg_email_optional else Res.string.reg_email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isTeams) {
                        OutlinedTextField(
                            value = team,
                            onValueChange = { team = it.take(MAX_REGISTRATION_TEAM_NAME_LENGTH) },
                            label = { Text(stringResource(Res.string.reg_team_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = food,
                        onValueChange = { food = it.take(MAX_FOOD_RESTRICTIONS_LENGTH) },
                        label = { Text(stringResource(Res.string.reg_food)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = paid, onCheckedChange = { paid = it })
                        Text(stringResource(Res.string.reg_paid))
                    }
                    if (registration == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = notify, onCheckedChange = { notify = it })
                            Text(stringResource(Res.string.reg_notify))
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        enabled = person != null,
                        onClick = { person?.let { onSave(it.emaId, email, team, food, paid, notify) } },
                    ) { Text(stringResource(Res.string.reg_save)) }
                    FocusedTextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** Reads the CSV or Excel file of the old Google Form, shows what it will do, and imports it. */
@Composable
private fun RegistrationImportDialog(
    preview: suspend (String, ByteArray) -> AppResult<RegistrationImport>,
    apply: suspend (String, ByteArray) -> AppResult<RegistrationImport>,
    onDone: (imported: Int?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var result by remember { mutableStateOf<RegistrationImport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val picker = rememberSpreadsheetPicker(
        onSelected = { selected ->
            file = selected.fileName to selected.bytes
            busy = true
            scope.launch {
                when (val read = preview(selected.fileName, selected.bytes)) {
                    is AppResult.Success -> result = read.value
                    is AppResult.Failure -> error = read.error.toUiText()
                }
                busy = false
            }
        },
        onError = { error = it },
    )
    error?.let { AppErrorDialog(message = it, onDismiss = { error = null }) }
    TrackedDialog("ImportRegistrations") {
        AlertDialog(
            onDismissRequest = { if (!busy) onDone(null) },
            title = { MtsDialogTitle(stringResource(Res.string.reg_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MtsDialogText(stringResource(Res.string.reg_import_info))
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    result?.let { read ->
                        val ready = read.rows.count { it.problems.isEmpty() }
                        Text(stringResource(Res.string.reg_import_ready, ready, read.rows.size), fontWeight = FontWeight.SemiBold)
                        val state = rememberLazyListState()
                        LazyColumnWithScrollbar(state = state, modifier = Modifier.heightIn(max = 320.dp)) {
                            items(read.rows, key = { it.line }) { row ->
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "${row.line}. ${row.registryName ?: "${row.firstName} ${row.lastName}".trim()} · ${row.emaId}" +
                                            (row.team?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    row.problems.forEach { problem ->
                                        Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    if (row.sharedEmail) {
                                        Text(stringResource(Res.string.reg_shared_email), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    val chosen = file
                    val ready = result?.rows?.count { it.problems.isEmpty() } ?: 0
                    if (chosen != null && ready > 0) {
                        FocusedButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    when (val done = apply(chosen.first, chosen.second)) {
                                        is AppResult.Success -> onDone(done.value.imported)
                                        is AppResult.Failure -> error = done.error.toUiText()
                                    }
                                    busy = false
                                }
                            },
                        ) { Text(stringResource(Res.string.reg_import_apply, ready)) }
                    }
                    OutlinedButton(enabled = !busy, onClick = { picker.launch() }) { Text(stringResource(Res.string.reg_import_choose)) }
                    FocusedTextButton(enabled = !busy, onClick = { onDone(null) }) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun problemText(code: String): String = when (code) {
    "INVALID_EMA" -> stringResource(Res.string.reg_problem_invalid_ema)
    "DUPLICATE_EMA" -> stringResource(Res.string.reg_problem_duplicate_ema)
    "INVALID_EMAIL" -> stringResource(Res.string.reg_problem_invalid_email)
    "EMA_NOT_FOUND" -> stringResource(Res.string.reg_problem_ema_not_found)
    "ALREADY_REGISTERED" -> stringResource(Res.string.reg_problem_already_registered)
    else -> code
}
