package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
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
import com.etologic.mahjongtournamentsuite.presentation.components.CreateProgressDialog
import com.etologic.mahjongtournamentsuite.presentation.components.EditTournamentDialog
import com.etologic.mahjongtournamentsuite.presentation.components.IdCardsExportDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDatePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDateRangePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoLibraryDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentTimePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.screen.AccountOutcome
import com.etologic.mahjongtournamentsuite.presentation.screen.AccountOutcomeDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.CreateTournamentDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.EmaPlayerPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.MissingEmailsDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PasswordResetsConfirmDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PasswordResetsResultDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerAccountsBulkDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerSubstitutionDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.ResetTableDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.SendSummaryDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.SendingDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.SlotEmailDialog
import com.etologic.mahjongtournamentsuite.presentation.screen.TableUpdateNotice
import com.etologic.mahjongtournamentsuite.presentation.screen.TableUpdatesDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.screenshots.dialogVariants
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import kotlin.test.Test

/** Every dialog of the admin app, on a phone and on a desktop, light and dark. */
@OptIn(ExperimentalTestApi::class)
class AdminDialogsScreenshotTest {
    private fun dialog(name: String, content: @Composable () -> Unit) = adminScreenshots("dialogs/$name", variants = dialogVariants) { content() }

    private val players = ScreenshotData.tournamentPlayers()
    private fun nameOf(id: Int) = ScreenshotData.players[id - 1].displayName

    @Test
    fun editTournament() = dialog("edit-tournament") { EditTournamentDialog(ScreenshotData.tournament(ONGOING), onDismiss = {}) }

    @Test
    fun createTournament() = dialog("create-tournament") { CreateTournamentDialog(onDismiss = {}, onCreated = {}) }

    @Test
    fun createProgress() = dialog("create-progress") {
        CreateProgressDialog(CreateTournamentPresenter.Progress(CreateTournamentPresenter.Phase.CALCULATING, 4, 1200, 8), startMark = null, onCancel = {})
    }

    @Test
    fun colorPicker() = dialog("color-picker") { TournamentColorPickerDialog("#02B16B", onDismiss = {}, onColorSelected = {}) }

    @Test
    fun logoLibrary() = dialog("logo-library") { TournamentLogoLibraryDialog(ScreenshotData.tournaments, onDismiss = {}, onSelect = {}) }

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

    @Test
    fun unsavedChanges() = dialog("unsaved-changes") { UnsavedChangesDialog(isSaving = false, onSave = {}, onDiscard = {}, onCancel = {}) }

    @Test
    fun tableUpdates() = dialog("table-updates") {
        TableUpdatesDialog(listOf(TableUpdateNotice(TableKey(3, 2), emptyList(), saveRejected = true)), onDismiss = {})
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
}
