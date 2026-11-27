package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HowToReg
import com.etologic.mahjongtournamentsuite.presentation.components.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.Player
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
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DrawBackupRunningNote
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationButton
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.screenFullBleed
import com.etologic.mahjongtournamentsuite.presentation.components.screenListPadding
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberSpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.platform.saveTextFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.RegistrationsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.reg_add_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirm_remove_message
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirm_remove_next
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirm_remove_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_confirmed_section
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_calculating
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_confirm
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_message
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_edit_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_empty_message
import mahjongtournamentsuite.adminapp.generated.resources.reg_empty_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_export
import mahjongtournamentsuite.adminapp.generated.resources.reg_import
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_result
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_copied
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_matches
import mahjongtournamentsuite.adminapp.generated.resources.reg_notify
import mahjongtournamentsuite.adminapp.generated.resources.reg_pending_section
import mahjongtournamentsuite.adminapp.generated.resources.reg_remove
import mahjongtournamentsuite.adminapp.generated.resources.reg_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_waiting_section
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The setting of the registrations that is open for a change on the screen, if any. */
internal enum class RegistrationSettingField { PLACES, CLOSES_ON, LINK }

/** The lists of the registrations, in the order of the screen and of the export, with their titles and colors. */
internal val RegistrationSections = listOf(
    RegistrationStatus.CONFIRMED to Res.string.reg_confirmed_section,
    RegistrationStatus.WAITING to Res.string.reg_waiting_section,
    RegistrationStatus.PENDING to Res.string.reg_pending_section,
)

internal val PositionColumnWidth = 36.dp
internal val PhotoSize = 36.dp
internal val FoodColumnWidth = 48.dp
internal val PaidColumnWidth = 72.dp
internal val ActionsColumnWidth = 48.dp

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
    val adminStatus by store.adminStatus.collectAsState()
    // Only who configures the tournament (super admins and club admins of its organizers, or a custom permission)
    // changes the settings and makes the draw. An editor manages the people. The API checks the same permission.
    val canConfigure = tournament != null && adminStatus?.can(Permission.TOURNAMENT_CONFIGURE, tournament.permissions) == true
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
    var problemsOnly by remember { mutableStateOf(false) }
    var editingSetting by remember { mutableStateOf<RegistrationSettingField?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminRegistration?>(null) }
    var removing by remember { mutableStateOf<AdminRegistration?>(null) }
    var importFile by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    val importPicker = rememberSpreadsheetPicker(
        onSelected = { selected -> importFile = selected.fileName to selected.bytes },
        onError = { error = it },
    )
    var drawConfirm by remember { mutableStateOf(false) }
    var drawTries by remember { mutableStateOf<Long?>(null) }
    var drawBackup by remember { mutableStateOf(false) }
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
            normalizeSearchText("${registration.name} ${registration.shownEmaId} ${registration.email} ${registration.country} ${current?.teamOf(registration)?.name.orEmpty()}")
                .contains(normalizedQuery)
    }
    val shown = matching.filter { (statusFilter == null || it.status == statusFilter) && (!problemsOnly || it.problems.isNotEmpty()) }

    AppScaffold(
        title = stringResource(Res.string.reg_title),
        subtitle = tournament?.name,
        shortSubtitle = tournament?.shortName,
        isLoading = loading || saving,
        showBackgroundLogo = loading && current == null,
        contentLoading = loading && current == null,
        onBack = { navController.popBackStack() },
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            AppTopBarButton(
                text = stringResource(Res.string.reg_export),
                icon = Icons.Default.FileUpload,
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
                icon = Icons.Default.FileDownload,
                onClick = { importPicker.launch() },
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
                        confirmButton = { MtsDialogButtons { FocusedButton(onClick = { info = null }) { ButtonText(stringResource(Res.string.ok)) } } },
                    )
                }
            }
            if (current != null) {
                val busy = saving || drawTries != null
                // The list reaches the edges of the window, so its shadows take the whole width; the margins go inside.
                LazyColumnWithScrollbar(
                    state = rememberLazyListState(),
                    modifier = Modifier.weight(1f).fillMaxWidth().screenFullBleed().testTag("registrations-list"),
                    contentPadding = screenListPadding(top = MtsTheme.layout.screenGutter, bottom = MtsTheme.layout.screenGutter),
                    scrollbar = MtsScrollbar.Overlay,
                ) {
                    item(key = "settings") {
                        RegistrationSettingsPanel(
                            data = current,
                            editing = editingSetting,
                            busy = busy,
                            canConfigure = canConfigure,
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
                            onDraw = { drawConfirm = true },
                        )
                    }
                    if (current.registrations.isEmpty()) {
                        item(key = "add") {
                            AddPersonButton(busy = busy, onAdd = { adding = true }, modifier = Modifier.padding(top = MtsTheme.spacing.lg))
                        }
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
                                onSelect = {
                                    statusFilter = it
                                    problemsOnly = false
                                },
                                problemsOnly = problemsOnly,
                                onProblemsOnly = {
                                    problemsOnly = it
                                    if (it) statusFilter = null
                                },
                                paid = current.registrations.count { it.status == RegistrationStatus.CONFIRMED && it.paid },
                                confirmed = current.registrations.count { it.status == RegistrationStatus.CONFIRMED },
                                busy = busy,
                                onAdd = { adding = true },
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
                                            photoUrl = registration.photoUrl ?: player?.photoUrl,
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
            onSave = { emaId, email, teamName, food, paid, _ ->
                editing = null
                change {
                    presenter.update(
                        tournamentId,
                        registration.emaId,
                        RegistrationUpdate(
                            newEmaId = emaId.takeIf { it != registration.emaId },
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
                        // The place goes by itself to the first of the waiting list: the organizer reads who gets it.
                        current?.takesThePlaceOf(registration)?.let { next ->
                            MtsDialogText(
                                stringResource(Res.string.reg_confirm_remove_next, next.name.ifBlank { next.emaId }),
                                modifier = Modifier.testTag("registration-remove-next"),
                            )
                        }
                        if (registration.email.isNotBlank()) {
                            MtsCheckboxRow(text = stringResource(Res.string.reg_notify), checked = notify, onCheckedChange = { notify = it })
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
                        ) { ButtonText(stringResource(Res.string.reg_remove)) }
                        FocusedTextButton(onClick = { removing = null }) { ButtonText(stringResource(Res.string.cancel)) }
                    }
                },
            )
        }
    }
    importFile?.let { chosen ->
        RegistrationImportDialog(
            file = chosen,
            preview = { name, bytes -> presenter.previewImport(tournamentId, name, bytes) },
            apply = { name, bytes -> presenter.applyImport(tournamentId, name, bytes) },
            onDone = { imported ->
                importFile = null
                if (imported != null) {
                    refresh()
                    info = UiText.Plural(Res.plurals.reg_import_result, imported, listOf(imported))
                }
            },
        )
    }
    if (drawConfirm && current != null && tournament != null) {
        MakeDrawDialog(
            confirmed = current.settings.confirmed,
            rounds = tournament.numRounds,
            tries = drawTries,
            backup = drawBackup,
            onConfirm = {
                drawTries = 0
                drawBackup = false
                scope.launch {
                    val result = presenter.makeDraw(tournament, current) { progress ->
                        scope.launch(Dispatchers.Main) {
                            if (drawTries != null) {
                                drawTries = progress.tried
                                drawBackup = progress.backup
                            }
                        }
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
    backup: Boolean = false,
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
                        if (backup) DrawBackupRunningNote()
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
                        FocusedButton(enabled = tries == null, onClick = onConfirm) { ButtonText(stringResource(Res.string.reg_draw_confirm)) }
                        if (tries == null) FocusedTextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.cancel)) }
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
internal fun LabeledIconButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    if (LocalCompactLayout.current) {
        FocusedIconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = text)
        }
    } else {
        FocusedTextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            ButtonText(text, modifier = Modifier.padding(start = MtsTheme.spacing.sm))
        }
    }
}
