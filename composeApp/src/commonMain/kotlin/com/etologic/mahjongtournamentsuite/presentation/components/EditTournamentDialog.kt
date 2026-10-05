package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
import com.etologic.mahjongtournamentsuite.presentation.PlayerBaseRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.VersionPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiIsoDateTimeOrDash
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

private enum class TournamentEditField {
    NAME,
    SHORT_NAME,
    HOST_COUNTRY,
    HOST_CITY,
    PRIMARY_COLOR,
    START_DATE,
    END_DATE,
}

@Composable
fun EditTournamentDialog(
    tournament: Tournament,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = onDismiss,
) {
    val presenter = koinInject<TournamentsPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()

    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()
    val canDeleteTournaments = adminStatus?.canDeleteTournaments == true
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deleteDialogTournament by remember { mutableStateOf<Tournament?>(null) }
    var renameValue by remember(tournament.id) {
        mutableStateOf(TextFieldValue(text = tournament.name, selection = TextRange(tournament.name.length)))
    }
    var settingsShortName by remember(tournament.id) {
        mutableStateOf(tournament.shortName.ifBlank { tournament.name.take(10) })
    }
    var settingsPrimaryColor by remember(tournament.id) { mutableStateOf(tournament.primaryColor) }
    var settingsEventStartDate by remember(tournament.id) {
        mutableStateOf(tournament.eventStartDate.toDisplayTournamentDate())
    }
    var settingsEventEndDate by remember(tournament.id) {
        mutableStateOf(tournament.eventEndDate.toDisplayTournamentDate())
    }
    var settingsHostCountry by remember(tournament.id) { mutableStateOf(tournament.hostCountry) }
    var settingsHostCity by remember(tournament.id) { mutableStateOf(tournament.hostCity) }
    var settingsRoundScheduleRows by remember(tournament.id) {
        mutableStateOf(
            synchronizeRoundScheduleRows(
                rows = tournament.roundSchedules.map { it.toEditorRow() },
                roundCount = tournament.numRounds,
            ),
        )
    }
    var settingsAgendaRows by remember(tournament.id) {
        mutableStateOf(tournament.agendaItems.map { it.toEditorRow() })
    }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var settingsLogo by remember { mutableStateOf<SelectedImage?>(null) }
    var settingsLogoToCrop by remember { mutableStateOf<SelectedImage?>(null) }
    var reusedSettingsLogoTournament by remember { mutableStateOf<Tournament?>(null) }
    var showSettingsLogoLibrary by remember { mutableStateOf(false) }
    var removeSettingsLogo by remember { mutableStateOf(false) }
    var logoImageInfo by remember { mutableStateOf<String?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var invalidEditFields by remember { mutableStateOf(emptySet<TournamentEditField>()) }
    val renameFieldFocusRequester = remember { FocusRequester() }
    val renameShortNameFocusRequester = remember { FocusRequester() }
    val settingsHostCountryFocusRequester = remember { FocusRequester() }
    val settingsHostCityFocusRequester = remember { FocusRequester() }
    val settingsStartDateFocusRequester = remember { FocusRequester() }
    val settingsEndDateFocusRequester = remember { FocusRequester() }
    val settingsColorFocusRequester = remember { FocusRequester() }
    val renameConfirmFocusRequester = remember { FocusRequester() }
    val renameCancelFocusRequester = remember { FocusRequester() }
    val settingsLogoPicker = rememberImagePicker(
        onImageSelected = { image ->
            invalidEditFields = emptySet()
            if (image.contentType !in setOf("image/jpeg", "image/png")) {
                renameError = "Logo must be a JPEG or PNG image."
            } else if (image.bytes.size > 2 * 1024 * 1024) {
                renameError = "Logo must be 2 MB or smaller."
            } else {
                settingsLogoToCrop = image
            }
        },
        onError = { message ->
            invalidEditFields = emptySet()
            renameError = message
        },
    )

    suspend fun refreshTournaments() {
        when (val result = presenter.loadTournaments(force = true)) {
            is AppResult.Success -> store.upsertTournaments(result.value)
            is AppResult.Failure -> Unit
        }
    }

    LaunchedEffect(presenter) {
        when (val result = presenter.loadCountries()) {
            is AppResult.Success -> countries = result.value
            is AppResult.Failure -> errorMessage = result.error.toUiMessage()
        }
    }

    val editDeleteFocusRequester = remember(tournament.id) { FocusRequester() }
    val dialogScrollState = rememberScrollState()

    LaunchedEffect(tournament.id) {
        renameFieldFocusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = {
            if (!isLoading) {
                showDateRangePicker = false
                showColorPickerDialog = false
                onDismiss()
            }
        },
        title = { Text("Edit tournament") },
        text = {
            ScrollableColumnWithScrollbar(
                state = dialogScrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = {
                            renameValue = it
                            invalidEditFields = invalidEditFields - TournamentEditField.NAME
                            renameError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameFieldFocusRequester)
                            .textFieldFocusLoop(
                                previous = renameConfirmFocusRequester,
                                next = renameShortNameFocusRequester,
                                value = { renameValue },
                            ),
                        label = { Text("Tournament name") },
                        singleLine = true,
                        isError = TournamentEditField.NAME in invalidEditFields,
                        supportingText = if (TournamentEditField.NAME in invalidEditFields) {
                            { Text("Tournament name is required.") }
                        } else {
                            null
                        },
                    )
                    OutlinedTextField(
                        value = settingsShortName,
                        onValueChange = {
                            settingsShortName = it.take(10)
                            invalidEditFields = invalidEditFields - TournamentEditField.SHORT_NAME
                            renameError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameShortNameFocusRequester),
                        label = { Text("Short name") },
                        placeholder = { Text("Max. 10 characters") },
                        singleLine = true,
                        isError = TournamentEditField.SHORT_NAME in invalidEditFields,
                        supportingText = if (TournamentEditField.SHORT_NAME in invalidEditFields) {
                            { Text("Use 1 to 10 characters.") }
                        } else {
                            null
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EmaCountryDropdown(
                            selectedCode = settingsHostCountry,
                            countries = countries,
                            onCountrySelected = {
                                settingsHostCountry = it
                                invalidEditFields = invalidEditFields - TournamentEditField.HOST_COUNTRY
                                renameError = null
                            },
                            modifier = Modifier.weight(1f),
                            focusRequester = settingsHostCountryFocusRequester,
                            isError = TournamentEditField.HOST_COUNTRY in invalidEditFields,
                            label = "Country",
                            errorMessage = if (TournamentEditField.HOST_COUNTRY in invalidEditFields) {
                                "Select a host country."
                            } else {
                                null
                            },
                        )
                        OutlinedTextField(
                            value = settingsHostCity,
                            onValueChange = {
                                settingsHostCity = it
                                invalidEditFields = invalidEditFields - TournamentEditField.HOST_CITY
                                renameError = null
                            },
                            modifier = Modifier.weight(2f).focusRequester(settingsHostCityFocusRequester),
                            label = { Text("City") },
                            singleLine = true,
                            isError = TournamentEditField.HOST_CITY in invalidEditFields,
                            supportingText = if (TournamentEditField.HOST_CITY in invalidEditFields) {
                                { Text("Host city is required.") }
                            } else {
                                null
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = settingsEventStartDate,
                            onValueChange = {
                                settingsEventStartDate = it.take(10)
                                settingsEventEndDate = adjustedEndDate(
                                    startDisplayDate = settingsEventStartDate,
                                    endDisplayDate = settingsEventEndDate,
                                )
                                invalidEditFields = invalidEditFields - setOf(
                                    TournamentEditField.START_DATE,
                                    TournamentEditField.END_DATE,
                                )
                                renameError = null
                            },
                            modifier = Modifier.weight(1f).focusRequester(settingsStartDateFocusRequester),
                            label = { Text("From") },
                            placeholder = { Text("DD/MM/YYYY") },
                            trailingIcon = {
                                IconButton(onClick = { showDateRangePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Choose start date",
                                    )
                                }
                            },
                            singleLine = true,
                            isError = TournamentEditField.START_DATE in invalidEditFields,
                            supportingText = if (TournamentEditField.START_DATE in invalidEditFields) {
                                { Text("Use DD/MM/YYYY.") }
                            } else {
                                null
                            },
                        )
                        OutlinedTextField(
                            value = settingsEventEndDate,
                            onValueChange = {
                                val newValue = it.take(10)
                                settingsEventEndDate = when {
                                    newValue.isEmpty() && settingsEventStartDate.toIsoTournamentDateOrNull() != null ->
                                        settingsEventStartDate
                                    else -> adjustedEndDate(settingsEventStartDate, newValue)
                                }
                                invalidEditFields = invalidEditFields - setOf(
                                    TournamentEditField.START_DATE,
                                    TournamentEditField.END_DATE,
                                )
                                renameError = null
                            },
                            modifier = Modifier.weight(1f).focusRequester(settingsEndDateFocusRequester),
                            label = { Text("To") },
                            placeholder = { Text("DD/MM/YYYY") },
                            trailingIcon = {
                                IconButton(onClick = { showDateRangePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Choose end date",
                                    )
                                }
                            },
                            singleLine = true,
                            isError = TournamentEditField.END_DATE in invalidEditFields,
                            supportingText = if (TournamentEditField.END_DATE in invalidEditFields) {
                                { Text("Use a valid date on or after From.") }
                            } else {
                                null
                            },
                        )
                    }

                    TournamentScheduleEditor(
                        roundRows = settingsRoundScheduleRows,
                        onRoundRowsChange = { settingsRoundScheduleRows = it },
                        agendaRows = settingsAgendaRows,
                        onAgendaRowsChange = { settingsAgendaRows = it },
                        enabled = !isLoading,
                        minimumDisplayDate = settingsEventStartDate,
                        maximumDisplayDate = settingsEventEndDate,
                    )

                    TournamentColorField(
                        value = settingsPrimaryColor,
                        enabled = !isLoading,
                        isError = TournamentEditField.PRIMARY_COLOR in invalidEditFields,
                        errorMessage = if (TournamentEditField.PRIMARY_COLOR in invalidEditFields) {
                            "Use #RRGGBB format."
                        } else {
                            null
                        },
                        onValueChange = {
                            settingsPrimaryColor = it
                            invalidEditFields = invalidEditFields - TournamentEditField.PRIMARY_COLOR
                            renameError = null
                        },
                        onPreviewClick = {
                            showDateRangePicker = false
                            showColorPickerDialog = true
                        },
                        fieldModifier = Modifier.focusRequester(settingsColorFocusRequester),
                    )

                    Text("Logo", style = MaterialTheme.typography.titleSmall)
                    val logoModel = when {
                        removeSettingsLogo -> null
                        settingsLogo != null -> settingsLogo?.dataUrl
                        reusedSettingsLogoTournament != null -> reusedSettingsLogoTournament?.associationLogoUrl
                        else -> tournament.associationLogoUrl
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        TournamentLogoPreview(
                            model = logoModel,
                            onImageInfo = { logoImageInfo = it },
                            modifier = Modifier.size(120.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = when {
                                    removeSettingsLogo -> "Logo will be removed"
                                    settingsLogo != null -> buildString {
                                        append(settingsLogo?.fileName.orEmpty())
                                        append(" · ")
                                        append(formatByteSize(settingsLogo?.bytes?.size?.toLong() ?: 0L))
                                        logoImageInfo?.let { append(" · ").append(it) }
                                    }
                                    reusedSettingsLogoTournament != null ->
                                        "Logo from ${reusedSettingsLogoTournament?.name}"
                                    tournament.associationLogoUrl != null ->
                                        logoImageInfo?.let { "Current logo · $it" } ?: "Current logo"
                                    else -> "No logo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(onClick = settingsLogoPicker::launch) {
                                    Text(if (logoModel == null) "Choose logo" else "Upload logo")
                                }
                                if (tournaments.any {
                                        it.id != tournament.id && !it.associationLogoUrl.isNullOrBlank()
                                    }) {
                                    Button(onClick = { showSettingsLogoLibrary = true }) {
                                        Text("Reuse")
                                    }
                                }
                                if (logoModel != null) {
                                    FocusedTextButton(
                                        onClick = {
                                            settingsLogo = null
                                            reusedSettingsLogoTournament = null
                                            removeSettingsLogo = true
                                            logoImageInfo = null
                                        },
                                    ) { Text("Remove") }
                                }
                            }
                        }
                    }
                    renameError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TournamentReadOnlyField(
                        label = "Logo URL",
                        value = tournament.associationLogoUrl ?: "—",
                    )

                    TournamentIdCardPreviewButton(
                        shortName = settingsShortName,
                        primaryColor = settingsPrimaryColor,
                        year = settingsEventStartDate.toIsoTournamentDateOrNull()?.take(4).orEmpty(),
                        associationLogoContentType = settingsLogo?.contentType,
                        associationLogoBytes = settingsLogo?.bytes,
                        associationLogoUrl = reusedSettingsLogoTournament?.associationLogoUrl
                            ?: if (settingsLogo == null && !removeSettingsLogo) tournament.associationLogoUrl else null,
                        roundSchedules = settingsRoundScheduleRows.toTournamentRoundSchedules(),
                        onError = { renameError = it },
                    )

                    Text("Tournament data", style = MaterialTheme.typography.titleSmall)
                    TournamentReadOnlyField(label = "ID", value = tournament.id)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TournamentReadOnlyField(
                            label = "Teams",
                            value = if (tournament.isTeams) "Yes" else "No",
                            modifier = Modifier.weight(1f),
                        )
                        TournamentReadOnlyField(
                            label = "Completed",
                            value = if (tournament.isCompleted) "Yes" else "No",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TournamentReadOnlyField(
                            label = "Players",
                            value = tournament.numPlayers.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        TournamentReadOnlyField(
                            label = "Rounds",
                            value = tournament.numRounds.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        TournamentReadOnlyField(
                            label = "Tries",
                            value = tournament.numTries.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TournamentReadOnlyField(
                        label = "Created by UID",
                        value = tournament.createdByUid ?: "—",
                    )
                    TournamentReadOnlyField(
                        label = "Created by name",
                        value = tournament.createdByName ?: "—",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TournamentReadOnlyField(
                            label = "Created at",
                            value = tournament.createdAt.toUiIsoDateTimeOrDash(),
                            modifier = Modifier.weight(1f),
                        )
                        TournamentReadOnlyField(
                            label = "Updated at",
                            value = tournament.updatedAt.toUiIsoDateTimeOrDash(),
                            modifier = Modifier.weight(1f),
                        )
                    }

                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canDeleteTournaments) {
                    FocusedTextButton(
                        enabled = !isLoading,
                        onClick = { deleteDialogTournament = tournament },
                        focusRequester = editDeleteFocusRequester,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete")
                    }
                }
                Spacer(Modifier.weight(1f))
                FocusedTextButton(
                    enabled = !isLoading,
                    onClick = {
                        showDateRangePicker = false
                        showColorPickerDialog = false
                        onDismiss()
                    },
                    focusRequester = renameCancelFocusRequester,
                ) {
                    Text("Cancel")
                }
                Button(
                    enabled = !isLoading,
                    onClick = {
                        val newName = renameValue.text.trim()
                        val newShortName = settingsShortName.trim()
                        val newPrimaryColor = settingsPrimaryColor.trim().uppercase()
                        val newEventStartDate = settingsEventStartDate.toIsoTournamentDateOrNull()
                        val newEventEndDate = settingsEventEndDate.toIsoTournamentDateOrNull()
                        val newHostCountry = settingsHostCountry.trim().uppercase()
                        val newHostCity = settingsHostCity.trim()
                        val newInvalidFields = buildSet {
                            if (newName.isEmpty()) {
                                add(TournamentEditField.NAME)
                            }
                            if (newShortName.isEmpty() || newShortName.length > 10) {
                                add(TournamentEditField.SHORT_NAME)
                            }
                            if (!Regex("^[A-Z]{2,3}$").matches(newHostCountry)) {
                                add(TournamentEditField.HOST_COUNTRY)
                            }
                            if (newHostCity.isEmpty()) {
                                add(TournamentEditField.HOST_CITY)
                            }
                            if (!Regex("^#[0-9A-F]{6}$").matches(newPrimaryColor)) {
                                add(TournamentEditField.PRIMARY_COLOR)
                            }
                            if (newEventStartDate == null) {
                                add(TournamentEditField.START_DATE)
                            }
                            if (newEventEndDate == null) {
                                add(TournamentEditField.END_DATE)
                            } else if (
                                newEventStartDate != null &&
                                !TournamentDateRangeValidator.isValidRange(
                                    startDate = newEventStartDate,
                                    endDate = newEventEndDate,
                                )
                            ) {
                                add(TournamentEditField.END_DATE)
                            }
                        }
                        if (newInvalidFields.isNotEmpty()) {
                            invalidEditFields = newInvalidFields
                            when (newInvalidFields.first()) {
                                TournamentEditField.NAME -> renameFieldFocusRequester.requestFocus()
                                TournamentEditField.SHORT_NAME -> renameShortNameFocusRequester.requestFocus()
                                TournamentEditField.HOST_COUNTRY -> settingsHostCountryFocusRequester.requestFocus()
                                TournamentEditField.HOST_CITY -> settingsHostCityFocusRequester.requestFocus()
                                TournamentEditField.START_DATE -> settingsStartDateFocusRequester.requestFocus()
                                TournamentEditField.END_DATE -> settingsEndDateFocusRequester.requestFocus()
                                TournamentEditField.PRIMARY_COLOR -> settingsColorFocusRequester.requestFocus()
                            }
                            return@Button
                        }
                        val validEventStartDate = newEventStartDate ?: return@Button
                        val validEventEndDate = newEventEndDate ?: return@Button
                        val scheduleError = tournamentScheduleEditorError(
                            roundRows = settingsRoundScheduleRows,
                            agendaRows = settingsAgendaRows,
                            eventStartDate = validEventStartDate,
                            eventEndDate = validEventEndDate,
                        )
                        if (scheduleError != null) {
                            renameError = scheduleError
                            return@Button
                        }
                        coroutineScope.launch {
                            isLoading = true
                            invalidEditFields = emptySet()
                            renameError = null
                            when (val result = presenter.updateTournamentSettings(
                                tournamentId = tournament.id,
                                name = newName,
                                shortName = newShortName,
                                primaryColor = newPrimaryColor,
                                eventStartDate = validEventStartDate,
                                eventEndDate = validEventEndDate,
                                hostCountry = newHostCountry,
                                hostCity = newHostCity,
                                associationLogoContentType = settingsLogo?.contentType,
                                associationLogoBytes = settingsLogo?.bytes,
                                associationLogoSourceTournamentId = reusedSettingsLogoTournament?.id,
                                removeAssociationLogo = removeSettingsLogo,
                                roundSchedules = settingsRoundScheduleRows.toTournamentRoundSchedules(),
                                agendaItems = settingsAgendaRows.toTournamentAgendaItems(),
                            )) {
                                is AppResult.Success -> {
                                    showDateRangePicker = false
                                    showColorPickerDialog = false
                                    store.updateTournament(result.value)
                                    // A non-forced refresh serves the stale cached list while it revalidates
                                    // in the background, which would overwrite the saved schedule.
                                    refreshTournaments()
                                    onDismiss()
                                }

                                is AppResult.Failure -> {
                                    invalidEditFields = emptySet()
                                    errorMessage = result.error.toUiMessage()
                                }
                            }
                            isLoading = false
                        }
                    },
                    focusRequester = renameConfirmFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = renameCancelFocusRequester,
                        next = renameFieldFocusRequester,
                    ),
                ) {
                    Text("Save")
                }
            }
        },
    )

    if (showDateRangePicker) {
        TournamentDateRangePickerDialog(
            selectedStartDisplayDate = settingsEventStartDate,
            selectedEndDisplayDate = settingsEventEndDate,
            onDismiss = { showDateRangePicker = false },
            onDatesSelected = { selectedStartDate, selectedEndDate ->
                settingsEventStartDate = selectedStartDate
                settingsEventEndDate = selectedEndDate
                invalidEditFields = invalidEditFields - setOf(
                    TournamentEditField.START_DATE,
                    TournamentEditField.END_DATE,
                )
                renameError = null
                showDateRangePicker = false
            },
        )
    }

    if (showColorPickerDialog) {
        TournamentColorPickerDialog(
            initialColor = settingsPrimaryColor,
            onDismiss = { showColorPickerDialog = false },
            onColorSelected = { selectedColor ->
                settingsPrimaryColor = selectedColor
                invalidEditFields = invalidEditFields - TournamentEditField.PRIMARY_COLOR
                renameError = null
                showColorPickerDialog = false
            },
        )
    }

    settingsLogoToCrop?.let { image ->
        TournamentLogoCropDialog(
            image = image,
            onDismiss = { settingsLogoToCrop = null },
            onCropped = { croppedImage ->
                settingsLogo = croppedImage
                reusedSettingsLogoTournament = null
                removeSettingsLogo = false
                logoImageInfo = null
                renameError = null
                settingsLogoToCrop = null
            },
            onError = { message -> renameError = message },
        )
    }

    if (showSettingsLogoLibrary) {
        TournamentLogoLibraryDialog(
            tournaments = tournaments.filter { it.id != tournament.id },
            onDismiss = { showSettingsLogoLibrary = false },
            onSelect = { sourceTournament ->
                settingsLogo = null
                reusedSettingsLogoTournament = sourceTournament
                removeSettingsLogo = false
                logoImageInfo = null
                renameError = null
                showSettingsLogoLibrary = false
            },
        )
    }

    deleteDialogTournament?.let { deleting ->
        val deleteFocusRequester = remember(deleting.id) { FocusRequester() }
        val cancelFocusRequester = remember(deleting.id) { FocusRequester() }

        LaunchedEffect(deleting.id) {
            deleteFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!isLoading) deleteDialogTournament = null },
            title = { Text("Delete tournament") },
            text = { Text("This will permanently delete \"${deleting.name}\" (ID: ${deleting.id}).") },
            confirmButton = {
                Button(
                    enabled = !isLoading,
                    focusRequester = deleteFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = cancelFocusRequester,
                        next = cancelFocusRequester,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        coroutineScope.launch {
                            errorMessage = null
                            isLoading = true

                            when (val result = presenter.deleteTournament(deleting.id)) {
                                is AppResult.Success -> {
                                    deleteDialogTournament = null
                                    store.removeTournament(deleting.id)
                                    refreshTournaments()
                                    onDeleted()
                                }

                                is AppResult.Failure -> {
                                    errorMessage = result.error.toUiMessage()
                                }
                            }

                            isLoading = false
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(
                    enabled = !isLoading,
                    onClick = { deleteDialogTournament = null },
                    focusRequester = cancelFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = deleteFocusRequester,
                        next = deleteFocusRequester,
                    ),
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    errorMessage?.let { message ->
        AppErrorDialog(
            message = message,
            onDismiss = { errorMessage = null },
        )
    }
}

@Composable
private fun TournamentReadOnlyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        modifier = modifier,
        label = { Text(label) },
        enabled = false,
        singleLine = true,
    )
}
