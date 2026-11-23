package com.etologic.mahjongtournamentsuite.presentation.screenshots

import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountBlocker
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountData
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountSlot
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountWaiting
import com.etologic.mahjongtournamentsuite.presentation.screen.ChooseContent
import com.etologic.mahjongtournamentsuite.presentation.screen.DoneContent
import com.etologic.mahjongtournamentsuite.presentation.screen.LockedContent
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerCountForm
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerCountFrame
import com.etologic.mahjongtournamentsuite.presentation.screen.ReviewContent
import com.etologic.mahjongtournamentsuite.presentation.screen.SureDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.TableQrContent
import com.etologic.mahjongtournamentsuite.presentation.screen.WorkingContent
import androidx.compose.foundation.layout.ColumnScope
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerCountSummary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.CreateProgressDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.EditTournamentDialog
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.presentation.components.IdCardsExportDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDatePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDateRangePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentTimePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.screen.AccountOutcome
import com.etologic.mahjongtournamentsuite.presentation.screen.AccountOutcomeDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.AccountPermissionsDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.CreateTournamentDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.DeleteOrganizationDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.DeleteRoleDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.EmaPlayerDeletesConfirmDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.EmaPlayerDeletesResultDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.EmaPlayerPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.HandNotDone
import com.etologic.mahjongtournamentsuite.presentation.screen.HandsNotDoneDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.MakeDrawDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.MissingEmailsDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.NewRoleNameDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.OpenTablesReviewDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.OrganizationEditorDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PasswordResetsConfirmDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PasswordResetsResultDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerAccountsBulkDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerSubstitutionDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerWithData
import com.etologic.mahjongtournamentsuite.presentation.screen.RemoveFromOrganizationsDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.ResetTableDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.RoleDraft
import com.etologic.mahjongtournamentsuite.presentation.screen.RoleEditorDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.RoundControlDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.RoundControlDialogContent
import com.etologic.mahjongtournamentsuite.presentation.screen.RoundTimerController
import com.etologic.mahjongtournamentsuite.presentation.screen.SendSummaryDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.SendingDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.SlotEmailDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.TableUpdateNotice
import com.etologic.mahjongtournamentsuite.presentation.screen.TableUpdatesDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.UserDeletesConfirmDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.UserDeletesResultDialog
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.screenshots.dialogVariants
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import org.koin.compose.koinInject
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_calculating
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test

/** Every dialog of the admin app, on a phone and on a desktop, light and dark. */
private const val QR_SITE = "https://mts.mahjongmadrid.com"

@OptIn(ExperimentalTestApi::class)
class AdminDialogsScreenshotTest {
    private fun dialog(name: String, content: @Composable () -> Unit) = adminScreenshots("dialogs/$name", variants = dialogVariants) { content() }

    private val players = ScreenshotData.tournamentPlayers()
    private fun nameOf(id: Int) = ScreenshotData.players[id - 1].displayName

    @Test
    fun editTournament() = dialog("edit-tournament") {
        // The user is an administrator, so the dialog shows the Delete button. The app loads the role on the tournaments screen.
        val store = koinInject<AppMemoryStore>()
        remember { store.adminStatus.value = AdminStatus("admin-1", GlobalUserRole.SUPER_ADMIN) }
        EditTournamentDialog(ScreenshotData.tournament(ONGOING), onDismiss = {})
    }

    @Test
    fun createTournament() = dialog("create-tournament") { CreateTournamentDialog(onDismiss = {}, onCreated = {}) }

    @Test
    fun drawExplanation() = dialog("draw-explanation") { DrawExplanationDialog(onDismiss = {}) }

    /** The same dialog scrolled to its end: the second way to draw, with its diagram and its time limits. */
    @Test
    fun drawExplanationSecondWay() = adminScreenshots(
        "dialogs/draw-explanation-second-way",
        variants = dialogVariants,
        ready = { onNodeWithTag("drawExplanationEnd").performScrollTo() },
    ) { DrawExplanationDialog(onDismiss = {}) }

    /** Registrations closed: confirm the draw, with "How does the draw work?" above the buttons as in Create tournament. */
    @Test
    fun registrationsDraw() = dialog("registrations-draw") {
        MakeDrawDialog(confirmed = 16, rounds = 7, tries = null, onConfirm = {}, onDismiss = {})
    }

    /** Change of the number of players before the first hand, on its own screen: 3 marked, which do not fit in a table yet. */
    @Test
    fun playerCountRemove() = playerCountScreen("player-count-remove") {
        val tournament = ScreenshotData.tournament(ONGOING).copy(isTeams = false, numPlayers = 20)
        ChooseContent(
            tournament = tournament,
            data = playerCountData(20),
            form = PlayerCountForm(chosen = setOf(5, 11, 17)),
            onFormChange = {},
            onContinue = {},
            onCancel = {},
        )
    }

    /** A search: only the people who match show, and the marked ones stay marked; 4 marked fit in one table. */
    @Test
    fun playerCountSearch() = playerCountScreen("player-count-search") {
        val tournament = ScreenshotData.tournament(ONGOING).copy(isTeams = false, numPlayers = 20)
        ChooseContent(
            tournament = tournament,
            data = playerCountData(20),
            form = PlayerCountForm(chosen = setOf(5, 6, 11, 12), query = "ma"),
            onFormChange = {},
            onContinue = {},
            onCancel = {},
        )
    }

    /** One table added in a tournament with registrations: the new places can take people of the waiting list. */
    @Test
    fun playerCountAdd() = playerCountScreen("player-count-add") {
        val tournament = ScreenshotData.tournament(ONGOING).copy(isTeams = false, numPlayers = 16, registration = ScreenshotData.registrationSettings)
        ChooseContent(
            tournament = tournament,
            data = playerCountData(16).copy(
                waiting = listOf(
                    PlayerCountWaiting("10990136", nameOf(17), 1),
                    PlayerCountWaiting("10990137", nameOf(18), 2),
                ),
            ),
            form = PlayerCountForm(addedTables = 1, waiting = listOf("10990136")),
            onFormChange = {},
            onContinue = {},
            onCancel = {},
        )
    }

    /** The review: what the new draw deletes, adds and keeps, with "How does the draw work?". */
    @Test
    fun playerCountConfirm() = playerCountScreen("player-count-confirm", step = 1) {
        ReviewContent(
            summary = PlayerCountSummary(
                from = 20, to = 16, rounds = 7, isTeams = false, hasRegistrations = true,
                leaving = listOf("#5 · " + nameOf(5), "#6 · " + nameOf(6), "#11 · " + nameOf(11), "#12 · " + nameOf(12)),
                newSlots = 0, fromWaiting = emptyList(),
            ),
            onConfirm = {},
            onBack = {},
        )
    }

    /** The last word before the draw, in red: it cannot be undone. */
    @Test
    fun playerCountSure() = dialog("player-count-sure") { SureDialog(onConfirm = {}, onDismiss = {}) }

    /** The draw running, after the first way took too long. */
    @Test
    fun playerCountWorking() = playerCountScreen("player-count-working", step = 1) {
        WorkingContent(text = stringResource(Res.string.reg_draw_calculating, 1_234_567), backup = true)
    }

    /** The result: how many people got an email, and who the organizer must tell by hand. */
    @Test
    fun playerCountDone() = playerCountScreen("player-count-done", step = 2) {
        DoneContent(
            previous = 20,
            now = 16,
            emailsSent = 17,
            noEmail = listOf("#6 · " + nameOf(6), "#12 · " + nameOf(12)),
            emailFailed = listOf("#14 · " + nameOf(14)),
            onClose = {},
        )
    }

    @Test
    fun playerCountLocked() = playerCountScreen("player-count-locked", step = null) { LockedContent(PlayerCountBlocker.STARTED, onClose = {}) }

    /** A step of the screen of the change of the number of players, with its top bar and its steps. */
    /** The QR codes of the tables: the grid of all the tables. */
    @Test
    fun tableQr() = adminScreenshots("table-qr") { TableQrContent(qrTournament(), QR_SITE, onBack = {}, onDownload = {}) }

    /** One code in big, to show it on a screen. */
    @Test
    fun tableQrBig() = adminScreenshots("table-qr-big") { TableQrContent(qrTournament(), QR_SITE, onBack = {}, onDownload = {}, initialTable = 3) }

    private fun qrTournament() = ScreenshotData.tournament(ONGOING).copy(numPlayers = 24, registration = RegistrationSettings(slug = "madridopen"))

    private fun playerCountScreen(name: String, step: Int? = 0, content: @Composable ColumnScope.() -> Unit) =
        adminScreenshots(name) { PlayerCountFrame(subtitle = "Madrid Open 2026", step = step, onBack = {}, content = content) }

    private fun playerCountData(count: Int) = PlayerCountData(
        slots = (1..count).map { id -> PlayerCountSlot(id, nameOf(id), "1099${id.toString().padStart(4, '0')}", 0) },
        teams = emptyList(),
        waiting = emptyList(),
    )

    @Test
    fun createProgress() = dialog("create-progress") {
        CreateProgressDialog(CreateTournamentPresenter.Progress(CreateTournamentPresenter.Phase.CALCULATING, 4, 1200, 8), startMark = null, onCancel = {})
    }

    /** The first way took too long: the second way to draw is running. */
    @Test
    fun createProgressSecondWay() = dialog("create-progress-second-way") {
        CreateProgressDialog(
            CreateTournamentPresenter.Progress(CreateTournamentPresenter.Phase.CALCULATING, 4, 35_000, 4, backup = true),
            startMark = null,
            onCancel = {},
        )
    }

    @Test
    fun colorPicker() = dialog("color-picker") { TournamentColorPickerDialog("#02B16B", onDismiss = {}, onColorSelected = {}) }

    @Test
    fun datePicker() = dialog("date-picker") { TournamentDatePickerDialog("16/05/2026", onDismiss = {}, onDateSelected = {}) }

    @Test
    fun dateRangePicker() = dialog("date-range-picker") {
        TournamentDateRangePickerDialog("16/05/2026", "17/05/2026", onDismiss = {}, onDatesSelected = { _, _ -> })
    }

    @Test
    fun timePicker() = dialog("time-picker") { TournamentTimePickerDialog("10:00", onDismiss = {}, onTimeSelected = {}) }

    @Test
    fun idCardsExport() = dialog("id-cards-export") { IdCardsExportDialog(onExport = {}, onDismiss = {}) }

    /** Without backs, with sheets for substitutes and for the staff, and more staff names than fit on one sheet. */
    @Test
    fun idCardsExportExtras() = dialog("id-cards-export-extras") {
        IdCardsExportDialog(
            onExport = {},
            onDismiss = {},
            initialLayout = IdCardSheetLayout(
                cardsPerSheet = 2,
                backs = false,
                substituteSheets = 2,
                staffSheets = 1,
                staffNames = listOf("Lucía Gómez", "Pedro Martín", "Ana Ruiz"),
            ),
        )
    }

    @Test
    fun unsavedChanges() = dialog("unsaved-changes") { UnsavedChangesDialog(isSaving = false, onSave = {}, onDiscard = {}, onCancel = {}) }

    @Test
    fun tableUpdates() = dialog("table-updates") {
        TableUpdatesDialog(listOf(TableUpdateNotice(TableKey(3, 2), emptyList(), saveRejected = true)), onDismiss = {})
    }

    @Test
    fun roundControl() = adminScreenshots("dialogs/round-control", variants = dialogVariants, ready = { waitForText("2:00:00") }) {
        RoundControlDialog(ONGOING, initialRound = 3, onDismiss = {})
    }

    @Test
    fun roundControlOpenTablesOfFinishedRounds() = adminScreenshots(
        "dialogs/round-control-open-tables",
        variants = dialogVariants,
        ready = { waitForText("2:00:00") },
    ) {
        // Round 2 is over (round 3 is in play) but two of its tables stayed open: badge on its chip and the close button.
        val repository = koinInject<TournamentRepository>()
        val controller = remember { RoundTimerController(repository, ONGOING, initialRound = 3) }
        val tables = remember {
            ScreenshotData.tables(ONGOING).map { if (it.roundId == 2 && it.tableId in setOf(1, 3)) it.copy(isCompleted = false) else it }
        }
        RoundControlDialogContent(
            controller = controller,
            loadTables = { AppResult.Success(tables) },
            loadRoundHands = { roundId -> openRoundHands(roundId) },
            onDismiss = {},
        )
    }

    @Test
    fun roundControlOpenTablesReview() = dialog("round-control-open-tables-review") {
        OpenTablesReviewDialog(
            tables = listOf(TableKey(2, 1), TableKey(2, 3), TableKey(4, 2)),
            busy = false,
            loadRoundHands = { roundId -> openRoundHands(roundId) },
            onGoTo = {},
            onCloseSelected = {},
            onDismiss = {},
        )
    }

    /** The tables of a round with part of their hands, like tables that stayed open: 11, 0, 7 and 14 hands. */
    private fun openRoundHands(roundId: Int) = AppResult.Success(
        (1..4).map { tableId ->
            val (table, hands) = ScreenshotData.table(ONGOING, roundId, tableId)
            val done = listOf(11, 0, 7, 14)[tableId - 1]
            table to hands.mapIndexed { index, hand -> hand.copy(isDone = index < done) }
        },
    )

    @Test
    fun handsNotDone() = dialog("hands-not-done") {
        HandsNotDoneDialog(hands = listOf(HandNotDone(1, 5, 4), HandNotDone(1, 6, 3)), onSave = {}, onDismiss = {})
    }

    @Test
    fun resetTable() = dialog("reset-table") { ResetTableDialog(roundId = 3, tableId = 2, busy = false, onConfirm = {}, onDismiss = {}) }

    @Test
    fun sending() = dialog("sending") { SendingDialog() }

    @Test
    fun sendSummary() = dialog("send-summary") {
        SendSummaryDialog(
            results = listOf(
                PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED, sentAt = "2026-05-10T09:00:00Z"),
                PlayerAccountActionResult(2, PlayerAccountOutcome.REMINDED, sentAt = "2026-05-10T09:00:00Z"),
                PlayerAccountActionResult(4, PlayerAccountOutcome.FAILED, PlayerAccountFailure.NO_EMAIL),
            ),
            nameOf = ::nameOf,
            onDismiss = {},
        )
    }

    @Test
    fun accountOutcome() = dialog("account-outcome") {
        AccountOutcomeDialog(AccountOutcome(UiText.Plain("The invitation went to lucia@example.test."), isError = false), onDismiss = {})
    }

    @Test
    fun accountsBulk() = dialog("accounts-bulk") {
        PlayerAccountsBulkDialog(
            players = players,
            nameOf = { nameOf(it.id) },
            countryOf = { it.country },
            results = emptyList(),
            busy = false,
            errorMessage = null,
            onSend = { _, _ -> },
            onDismiss = {},
        )
    }

    @Test
    fun substitution() = dialog("substitution") {
        PlayerSubstitutionDialog(
            playerName = nameOf(3),
            current = PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 3),
            rounds = (1..4).toList(),
            defaultRound = 3,
            busy = false,
            onSave = {},
            onRemove = {},
            onDismiss = {},
        )
    }

    @Test
    fun slotEmail() = dialog("slot-email") {
        SlotEmailDialog(nameOf(1), "lucia@example.test", hasEmail = true, confirmLabel = "Save", busy = false, onSave = {}, onRemove = {}, onDismiss = {})
    }

    @Test
    fun missingEmails() = dialog("missing-emails") {
        MissingEmailsDialog(players.filter { !it.hasEmail }, { nameOf(it.id) }, busy = false, confirmLabel = "Send", onConfirm = {}, onDismiss = {})
    }

    @Test
    fun emaPlayerPicker() = dialog("ema-player-picker") {
        EmaPlayerPickerDialog(ScreenshotData.players, owners = mapOf("04010002" to "Player 2"), selectedEmaId = "04010001", onSelect = {}, onDismiss = {})
    }

    @Test
    fun passwordResetsConfirm() = dialog("password-resets-confirm") { PasswordResetsConfirmDialog(count = 3, busy = false, onConfirm = {}, onDismiss = {}) }

    @Test
    fun passwordResetsResult() = dialog("password-resets-result") {
        PasswordResetsResultDialog(
            results = listOf(
                PasswordResetResult("player-1", PasswordResetOutcome.SENT),
                PasswordResetResult("editor-2", PasswordResetOutcome.FAILED, PasswordResetFailure.DISABLED),
            ),
            users = ScreenshotData.users,
            onDismiss = {},
        )
    }

    @Test
    fun accountPermissions() = dialog("account-permissions") {
        AccountPermissionsDialog(
            user = ScreenshotData.customizedEditor,
            catalog = ScreenshotData.rolesCatalog,
            access = AdminStatus("admin-1", GlobalUserRole.SUPER_ADMIN),
            saving = false,
            onDismiss = {},
            onSave = {},
            onSaveAsNewRole = { _, _ -> },
        )
    }

    @Test
    fun accountPermissionsClubAdmin() = dialog("account-permissions-club-admin") {
        AccountPermissionsDialog(
            user = ScreenshotData.customizedEditor,
            catalog = ScreenshotData.rolesCatalog,
            access = AdminStatus("admin-1", GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-madrid")),
            saving = false,
            onDismiss = {},
            onSave = {},
            onSaveAsNewRole = { _, _ -> },
        )
    }

    @Test
    fun roleEditor() = dialog("role-editor") {
        val role = ScreenshotData.rolesCatalog.custom.first()
        RoleEditorDialog(
            draft = RoleDraft(role.id, role.name, role.base, role.permissions, accounts = role.accounts),
            catalog = ScreenshotData.rolesCatalog,
            saving = false,
            onDismiss = {},
            onSave = { _, _, _ -> },
            onDuplicate = {},
            onDelete = {},
        )
    }

    @Test
    fun roleStandard() = dialog("role-standard") {
        RoleEditorDialog(
            draft = RoleDraft(null, "", GlobalUserRole.CLUB_ADMIN, ScreenshotData.rolesCatalog.standard.getValue(GlobalUserRole.CLUB_ADMIN), readOnly = true),
            catalog = ScreenshotData.rolesCatalog,
            saving = false,
            onDismiss = {},
            onSave = { _, _, _ -> },
            onDuplicate = {},
            onDelete = {},
        )
    }

    @Test
    fun roleDelete() = dialog("role-delete") {
        DeleteRoleDialog(ScreenshotData.rolesCatalog.custom.first(), saving = false, onDismiss = {}, onConfirm = {})
    }

    @Test
    fun organizationNew() = dialog("organization-new") {
        OrganizationEditorDialog(organization = null, saving = false, onDismiss = {}, onSave = {}, onDelete = null, onError = {})
    }

    @Test
    fun organizationEdit() = dialog("organization-edit") {
        OrganizationEditorDialog(organization = ScreenshotData.organizations.first(), saving = false, onDismiss = {}, onSave = {}, onDelete = {}, onError = {})
    }

    @Test
    fun organizationDelete() = dialog("organization-delete") {
        DeleteOrganizationDialog(ScreenshotData.organizations.first(), saving = false, onDismiss = {}, onConfirm = {})
    }

    @Test
    fun newRoleName() = dialog("new-role-name") {
        NewRoleNameDialog(ScreenshotData.rolesCatalog, initialName = "Bruno", saving = false, onDismiss = {}, onConfirm = {})
    }

    @Test
    fun removeFromOrganizations() = dialog("remove-from-organizations") {
        RemoveFromOrganizationsDialog(user = ScreenshotData.users[1], saving = false, onDismiss = {}, onConfirm = { _, _ -> })
    }

    @Test
    fun userDeletesConfirm() = dialog("user-deletes-confirm") { UserDeletesConfirmDialog(count = 3, busy = false, onConfirm = {}, onDismiss = {}) }

    @Test
    fun userDeletesResult() = dialog("user-deletes-result") {
        UserDeletesResultDialog(
            results = listOf(
                com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult("player-1", com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome.DELETED),
                com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult(
                    "editor-2",
                    com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome.FAILED,
                    com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure.LAST_SUPER_ADMIN,
                ),
            ),
            users = ScreenshotData.users,
            onDismiss = {},
        )
    }

    @Test
    fun emaPlayerDeletesConfirm() = dialog("ema-player-deletes-confirm") {
        EmaPlayerDeletesConfirmDialog(
            count = 3,
            busy = false,
            onConfirm = {},
            onDismiss = {},
            withData = listOf(PlayerWithData("ALAN TURING (00010003)", listOf("Open de Madrid 2026", "Riichi Cup 2025"))),
        )
    }

    @Test
    fun emaPlayerDeletesResult() = dialog("ema-player-deletes-result") {
        EmaPlayerDeletesResultDialog(
            results = ScreenshotData.players.take(3).mapIndexed { index, player ->
                when (index) {
                    0 -> com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult(player.emaId, com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteOutcome.DELETED)
                    1 -> com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult(player.emaId, com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteOutcome.DISABLED)
                    else -> com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult(
                        player.emaId,
                        com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteOutcome.FAILED,
                        com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteFailure.NOT_FOUND,
                    )
                }
            },
            players = ScreenshotData.players,
            onDismiss = {},
        )
    }
}
