package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.etologic.mahjongtournamentsuite.presentation.components.DateField
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberSpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.RegistrationsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
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
import mahjongtournamentsuite.adminapp.generated.resources.reg_closes_on
import mahjongtournamentsuite.adminapp.generated.resources.reg_closes_on_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirm_remove_message
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirm_remove_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirmed_section
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
import mahjongtournamentsuite.adminapp.generated.resources.reg_link
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_copied
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_name
import mahjongtournamentsuite.adminapp.generated.resources.reg_more_actions
import mahjongtournamentsuite.adminapp.generated.resources.reg_move_to_waiting
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_account
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_limit
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_matches
import mahjongtournamentsuite.adminapp.generated.resources.reg_notify
import mahjongtournamentsuite.adminapp.generated.resources.reg_paid
import mahjongtournamentsuite.adminapp.generated.resources.reg_pending_section
import mahjongtournamentsuite.adminapp.generated.resources.reg_pick_person
import mahjongtournamentsuite.adminapp.generated.resources.reg_places
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_already_registered
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_duplicate_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_ema_not_found
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_invalid_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_invalid_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_remove
import mahjongtournamentsuite.adminapp.generated.resources.reg_save
import mahjongtournamentsuite.adminapp.generated.resources.reg_settings
import mahjongtournamentsuite.adminapp.generated.resources.reg_settings_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_shared_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_state_closed
import mahjongtournamentsuite.adminapp.generated.resources.reg_state_open
import mahjongtournamentsuite.adminapp.generated.resources.reg_status_open_switch
import mahjongtournamentsuite.adminapp.generated.resources.reg_team_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_waiting_section
import mahjongtournamentsuite.adminapp.generated.resources.reg_without_team
import mahjongtournamentsuite.adminapp.generated.resources.search
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The registrations of a tournament before its draw: places, waiting list and people who did not confirm the email
 * yet. The organizers add, change and remove people, open or close the registrations, import the old Google Form
 * and make the draw with the confirmed people. See docs/registrations.md.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
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
    var data by remember { mutableStateOf<AdminRegistrations?>(null) }
    var registry by remember { mutableStateOf<List<Player>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var info by remember { mutableStateOf<UiText?>(null) }
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminRegistration?>(null) }
    var removing by remember { mutableStateOf<AdminRegistration?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var drawConfirm by remember { mutableStateOf(false) }
    var drawTries by remember { mutableStateOf<Long?>(null) }
    val linkCopied = stringResource(Res.string.reg_link_copied)

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

    LaunchedEffect(tournamentId) {
        refresh()
        (presenter.loadRegistry() as? AppResult.Success)?.let { registry = it.value }
    }

    val current = data
    val normalizedQuery = normalizeSearchText(query.trim())
    val shown = current?.registrations.orEmpty().filter { registration ->
        normalizedQuery.isEmpty() ||
            normalizeSearchText("${registration.name} ${registration.emaId} ${registration.email} ${registration.country} ${current?.teamOf(registration)?.name.orEmpty()}")
                .contains(normalizedQuery)
    }

    AppScaffold(
        title = stringResource(Res.string.reg_title),
        subtitle = tournament?.name,
        isLoading = loading || saving,
        showBackgroundLogo = loading && current == null,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarButton(
                text = stringResource(Res.string.reg_import),
                icon = Icons.Default.UploadFile,
                onClick = { importOpen = true },
                enabled = current != null,
                showLabel = false,
            )
            AppTopBarButton(
                text = stringResource(Res.string.reg_settings),
                icon = Icons.Default.Settings,
                onClick = { settingsOpen = true },
                enabled = current != null,
                showLabel = false,
            )
        },
    ) {
        ScreenColumn(
            maxWidth = 1100.dp,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                SummaryCard(
                    data = current,
                    onCopyLink = {
                        clipboard.setText(AnnotatedString(current.publicLink))
                        info = UiText.Plain(linkCopied)
                    },
                    onAdd = { adding = true },
                    onDraw = { drawConfirm = true },
                    busy = saving || drawTries != null,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.search)) },
                    singleLine = true,
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            FocusedIconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search))
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("registrations-search"),
                )
                if (current.registrations.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Default.HowToReg,
                        title = stringResource(Res.string.reg_empty_title),
                        message = stringResource(Res.string.reg_empty_message),
                    )
                } else if (shown.isEmpty()) {
                    Text(stringResource(Res.string.reg_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val listState = rememberLazyListState()
                    val sections = listOf(
                        RegistrationStatus.CONFIRMED to Res.string.reg_confirmed_section,
                        RegistrationStatus.WAITING to Res.string.reg_waiting_section,
                        RegistrationStatus.PENDING to Res.string.reg_pending_section,
                    )
                    val sectionTitles = sections.associate { (status, title) -> status to stringResource(title, shown.count { it.status == status }) }
                    LazyColumnWithScrollbar(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        scrollbar = MtsScrollbar.Margin,
                    ) {
                        sections.forEach { (status, _) ->
                            val rows = shown.filter { it.status == status }
                            if (rows.isNotEmpty()) {
                                item(key = "title-$status") {
                                    Text(
                                        sectionTitles.getValue(status),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                items(rows, key = { "${it.status}-${it.emaId}" }) { registration ->
                                    RegistrationRow(
                                        registration = registration,
                                        teamName = current.teamOf(registration)?.name,
                                        isTeams = current.isTeams,
                                        enabled = !saving,
                                        onPaidChange = { paid ->
                                            change { presenter.update(tournamentId, registration.emaId, RegistrationUpdate(paid = paid)) }
                                        },
                                        onEdit = { editing = registration },
                                        onMove = { status ->
                                            change { presenter.update(tournamentId, registration.emaId, RegistrationUpdate(status = status)) }
                                        },
                                        onRemove = { removing = registration },
                                    )
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
    if (settingsOpen && current != null) {
        RegistrationSettingsDialog(
            settings = current.settings,
            onSave = { settings ->
                settingsOpen = false
                change { presenter.saveSettings(tournamentId, settings) }
            },
            onDismiss = { settingsOpen = false },
        )
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
        TrackedDialog("MakeDraw") {
            AlertDialog(
                onDismissRequest = { if (drawTries == null) drawConfirm = false },
                title = { MtsDialogTitle(stringResource(Res.string.reg_draw_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val tries = drawTries
                        if (tries == null) {
                            MtsDialogText(stringResource(Res.string.reg_draw_message, current.settings.confirmed, tournament.numRounds))
                        } else {
                            MtsDialogText(stringResource(Res.string.reg_draw_calculating, tries))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    MtsDialogButtons {
                        FocusedButton(
                            enabled = drawTries == null,
                            onClick = {
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
                        ) { Text(stringResource(Res.string.reg_draw_confirm)) }
                        if (drawTries == null) FocusedTextButton(onClick = { drawConfirm = false }) { Text(stringResource(Res.string.cancel)) }
                    }
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SummaryCard(
    data: AdminRegistrations,
    onCopyLink: () -> Unit,
    onAdd: () -> Unit,
    onDraw: () -> Unit,
    busy: Boolean,
) {
    val settings = data.settings
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (settings.isOpen) Res.string.reg_state_open else Res.string.reg_state_closed),
                style = MaterialTheme.typography.titleMedium,
                color = if (settings.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    Res.string.reg_places,
                    settings.confirmed,
                    settings.maxPlayers?.toString() ?: stringResource(Res.string.reg_no_limit),
                    settings.waiting,
                ),
            )
            settings.closesOn?.let { Text(stringResource(Res.string.reg_closes_on, formatLocalizedDate(it))) }
            if (data.publicLink.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.reg_link, data.publicLink),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FocusedIconButton(onClick = onCopyLink) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(Res.string.reg_copy_link))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAdd, enabled = !busy) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.reg_add), modifier = Modifier.padding(start = 6.dp))
                }
                FocusedButton(onClick = onDraw, enabled = !busy && settings.status == RegistrationPhase.CLOSED) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.reg_draw), modifier = Modifier.padding(start = 6.dp))
                }
                if (settings.isOpen) InfoTooltipIcon(stringResource(Res.string.reg_draw_needs_close))
            }
        }
    }
}

@Composable
private fun RegistrationRow(
    registration: AdminRegistration,
    teamName: String?,
    isTeams: Boolean,
    enabled: Boolean,
    onPaidChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (RegistrationStatus) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("registration-${registration.emaId}")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (registration.status == RegistrationStatus.PENDING) "·" else registration.position.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.widthIn(min = 28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountryFlag(code = registration.country, width = 20.dp)
                    Text(registration.name.ifBlank { registration.emaId }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(registration.emaId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val details = listOfNotNull(
                    registration.email.ifBlank { null },
                    if (isTeams) teamName ?: stringResource(Res.string.reg_without_team) else null,
                    if (!registration.hasAccount) stringResource(Res.string.reg_no_account) else null,
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (registration.sharedEmail) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.reg_shared_email), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (registration.foodRestrictions.isNotBlank()) {
                InfoTooltipIcon(
                    description = stringResource(Res.string.reg_food_of, registration.foodRestrictions),
                    contentDescription = stringResource(Res.string.reg_food),
                )
            }
            if (registration.status != RegistrationStatus.PENDING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = registration.paid, onCheckedChange = onPaidChange, enabled = enabled)
                    Text(stringResource(Res.string.reg_paid), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column {
                FocusedIconButton(onClick = { menu = true }, enabled = enabled) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.reg_more_actions, registration.name))
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
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun RegistrationSettingsDialog(
    settings: RegistrationSettings,
    onSave: (RegistrationSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var open by remember { mutableStateOf(settings.isOpen) }
    var places by remember { mutableStateOf(settings.maxPlayers?.toString().orEmpty()) }
    var closesOn by remember { mutableStateOf(settings.closesOn.orEmpty()) }
    var slug by remember { mutableStateOf(settings.slug) }
    TrackedDialog("RegistrationSettings") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.reg_settings_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = open, onCheckedChange = { open = it })
                        Text(stringResource(Res.string.reg_status_open_switch))
                    }
                    OutlinedTextField(
                        value = places,
                        onValueChange = { typed -> places = typed.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(Res.string.reg_places_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateField(
                        label = stringResource(Res.string.reg_closes_on_optional),
                        value = closesOn,
                        onChange = { closesOn = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = slug,
                        onValueChange = { typed -> slug = typed.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(40) },
                        label = { Text(stringResource(Res.string.reg_link_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        enabled = slug.isNotBlank(),
                        onClick = {
                            onSave(
                                settings.copy(
                                    status = if (open) RegistrationPhase.OPEN else RegistrationPhase.CLOSED,
                                    maxPlayers = places.toIntOrNull(),
                                    closesOn = closesOn.ifBlank { null },
                                    slug = slug,
                                ),
                            )
                        },
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
