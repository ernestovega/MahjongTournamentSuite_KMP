package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.toDateInput
import com.etologic.mahjongtournamentsuite.presentation.components.toDigitsInput
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_EXTRA_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentOptionSwitch
import com.etologic.mahjongtournamentsuite.presentation.components.MAX_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.presentation.components.MAX_MATCH_EXTRA_MINUTES
import com.etologic.mahjongtournamentsuite.presentation.components.isTimeZoneIdShape
import com.etologic.mahjongtournamentsuite.presentation.components.matchExtraMinutesOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.MatchTimeFields
import com.etologic.mahjongtournamentsuite.presentation.components.VenueTimeZoneField
import com.etologic.mahjongtournamentsuite.presentation.components.matchDurationOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.RequiredLabel
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorField
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDateRangePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoPreview
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoCropDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentIdCardPreviewButton
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoLibraryDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AgendaItemEditorRow
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentScheduleEditor
import com.etologic.mahjongtournamentsuite.presentation.components.adjustedEndDate
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.formatByteSize
import com.etologic.mahjongtournamentsuite.presentation.components.toDisplayTournamentDate
import com.etologic.mahjongtournamentsuite.presentation.components.toIsoTournamentDateOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.synchronizeRoundScheduleRows
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentPlaceFields
import com.etologic.mahjongtournamentsuite.presentation.components.contactEmailRequiredError
import com.etologic.mahjongtournamentsuite.presentation.components.venueRequiredError
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentAgendaItems
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentRoundSchedules
import com.etologic.mahjongtournamentsuite.presentation.components.tournamentScheduleEditorError
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentPlayerCountValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.koin.compose.koinInject

/** The fields of the form that can be wrong. A wrong field gets a red mark and a short text. */
private enum class CreateField {
    NAME, SHORT_NAME, PLAYERS, ROUNDS, COUNTRY, CITY, VENUE, CONTACT_EMAIL, START_DATE, END_DATE, MATCH_DURATION,
    TIME_ZONE, EXTRA_TIME, COLOR,
}

private enum class CreateTab(val title: String) {
    GENERAL("General"),
    VENUE("Venue"),
    BRANDING("Branding"),
    SCHEDULE("Schedule"),
}

/** The tab that holds the field. */
private fun CreateField.tab(): CreateTab = when (this) {
    CreateField.NAME, CreateField.SHORT_NAME, CreateField.PLAYERS, CreateField.ROUNDS,
    CreateField.MATCH_DURATION, CreateField.EXTRA_TIME,
    -> CreateTab.GENERAL
    CreateField.COUNTRY, CreateField.CITY, CreateField.VENUE, CreateField.CONTACT_EMAIL,
    CreateField.TIME_ZONE,
    -> CreateTab.VENUE
    CreateField.COLOR -> CreateTab.BRANDING
    CreateField.START_DATE, CreateField.END_DATE -> CreateTab.SCHEDULE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onCreated: (Tournament) -> Unit,
) {
    val presenter = koinInject<CreateTournamentPresenter>()
    val store = koinInject<AppMemoryStore>()
    val tournaments by store.tournaments.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val nameFocusRequester = remember { FocusRequester() }
    val shortNameFocusRequester = remember { FocusRequester() }
    val primaryColorFocusRequester = remember { FocusRequester() }
    val hostCountryFocusRequester = remember { FocusRequester() }
    val hostCityFocusRequester = remember { FocusRequester() }
    val eventStartDateFocusRequester = remember { FocusRequester() }
    val eventEndDateFocusRequester = remember { FocusRequester() }
    val playersFocusRequester = remember { FocusRequester() }
    val roundsFocusRequester = remember { FocusRequester() }
    val matchDurationFocusRequester = remember { FocusRequester() }
    val mapUrlFocusRequester = remember { FocusRequester() }
    val contactEmailFocusRequester = remember { FocusRequester() }
    val dialogScrollState = rememberScrollState()
    var selectedTab by remember { mutableStateOf(CreateTab.GENERAL) }
    var pendingFocusField by remember { mutableStateOf<CreateField?>(null) }

    var name by remember { mutableStateOf("") }
    var shortName by remember { mutableStateOf("") }
    var primaryColor by remember { mutableStateOf("#02B16B") }
    var hostCountry by remember { mutableStateOf("") }
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var hostCity by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf(TournamentVenue()) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var associationLogo by remember { mutableStateOf<SelectedImage?>(null) }
    var logoToCrop by remember { mutableStateOf<SelectedImage?>(null) }
    var reusedLogoTournament by remember { mutableStateOf<Tournament?>(null) }
    var showLogoLibrary by remember { mutableStateOf(false) }
    var logoImageInfo by remember { mutableStateOf<String?>(null) }
    val today = remember {
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
            .toDisplayTournamentDate()
    }
    var eventStartDate by remember { mutableStateOf(today) }
    var eventEndDate by remember { mutableStateOf(today) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var numPlayersText by remember { mutableStateOf("60") }
    var numRoundsText by remember { mutableStateOf("7") }
    var roundScheduleRows by remember {
        mutableStateOf(synchronizeRoundScheduleRows(emptyList(), 7))
    }
    var agendaRows by remember { mutableStateOf<List<AgendaItemEditorRow>>(emptyList()) }
    var isTeams by remember { mutableStateOf(true) }
    var countBestHands by remember { mutableStateOf(true) }
    var countChickenHands by remember { mutableStateOf(true) }
    var matchDurationText by remember { mutableStateOf(DEFAULT_MATCH_DURATION_MINUTES.toString()) }
    var finishHandInPlay by remember { mutableStateOf(false) }
    var matchExtraText by remember { mutableStateOf(DEFAULT_MATCH_EXTRA_MINUTES.toString()) }
    var timeZoneText by remember { mutableStateOf(TimeZone.currentSystemDefault().id) }
    var computeMode by remember { mutableStateOf(CreateTournamentPresenter.ComputeMode.LIGHT) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<CreateField, String>>(emptyMap()) }
    var progress by remember { mutableStateOf<CreateTournamentPresenter.Progress?>(null) }
    var createJob by remember { mutableStateOf<Job?>(null) }
    var calcStartMark by remember { mutableStateOf<TimeMark?>(null) }
    val logoPicker = rememberImagePicker(
        onImageSelected = { image ->
            if (image.contentType !in setOf("image/jpeg", "image/png")) {
                errorMessage = "* Logo must be a JPEG or PNG image."
            } else if (image.bytes.size > 2 * 1024 * 1024) {
                errorMessage = "* Logo must be 2 MB or smaller."
            } else {
                logoToCrop = image
            }
        },
        onError = { message -> errorMessage = "* $message" },
    )
    val teamsToggleInteractionSource = remember { MutableInteractionSource() }
    val computeToggleInteractionSource = remember { MutableInteractionSource() }

    fun setTeamsChecked(checked: Boolean) {
        if (isLoading) return
        isTeams = checked
    }

    fun setHeavyCompute(checked: Boolean) {
        if (isLoading) return
        computeMode = if (checked) {
            CreateTournamentPresenter.ComputeMode.HEAVY
        } else {
            CreateTournamentPresenter.ComputeMode.LIGHT
        }
    }

    fun clearError(vararg fields: CreateField) {
        fieldErrors = fieldErrors - fields.toSet()
    }

    fun cancelCreation() {
        createJob?.cancel()
        createJob = null
        isLoading = false
        progress = null
        calcStartMark = null
    }

    DisposableEffect(Unit) {
        onDispose { createJob?.cancel() }
    }

    LaunchedEffect(selectedTab) {
        dialogScrollState.scrollTo(0)
    }

    // A field on another tab is not composed yet. Focus it after the tab changes.
    LaunchedEffect(pendingFocusField, selectedTab) {
        val field = pendingFocusField ?: return@LaunchedEffect
        when (field) {
            CreateField.NAME -> nameFocusRequester
            CreateField.SHORT_NAME -> shortNameFocusRequester
            CreateField.PLAYERS -> playersFocusRequester
            CreateField.ROUNDS -> roundsFocusRequester
            CreateField.COUNTRY -> hostCountryFocusRequester
            CreateField.CITY -> hostCityFocusRequester
            CreateField.VENUE -> mapUrlFocusRequester
            CreateField.CONTACT_EMAIL -> contactEmailFocusRequester
            CreateField.START_DATE -> eventStartDateFocusRequester
            CreateField.END_DATE -> eventEndDateFocusRequester
            CreateField.MATCH_DURATION -> matchDurationFocusRequester
            CreateField.COLOR -> primaryColorFocusRequester
            CreateField.TIME_ZONE, CreateField.EXTRA_TIME -> null
        }?.requestFocus()
        pendingFocusField = null
    }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
        when (val result = presenter.loadCountries()) {
            is AppResult.Success -> countries = result.value
            is AppResult.Failure -> errorMessage = result.error.toUiMessage()
        }
    }

    fun startCreate() {
        if (isLoading) return

        val trimmedName = name.trim()
        val trimmedShortName = shortName.trim()
        val normalizedPrimaryColor = primaryColor.trim().uppercase()
        val normalizedHostCountry = hostCountry.trim().uppercase()
        val trimmedHostCity = hostCity.trim()
        val trimmedStartDate = eventStartDate.toIsoTournamentDateOrNull()
        val trimmedEndDate = eventEndDate.toIsoTournamentDateOrNull()
        val numPlayers = numPlayersText.trim().toIntOrNull()
        val numRounds = numRoundsText.trim().toIntOrNull()

        val matchDurationMinutes = matchDurationOrNull(matchDurationText)
        val matchExtraMinutes = matchExtraMinutesOrNull(matchExtraText)
        val errors = linkedMapOf<CreateField, String>()
        if (trimmedName.isBlank()) errors[CreateField.NAME] = "Name is required."
        if (trimmedShortName.isBlank() || trimmedShortName.length > 10) {
            errors[CreateField.SHORT_NAME] = "Use 1 to 10 characters."
        }
        if (numPlayers == null || !TournamentPlayerCountValidator.isValid(numPlayers)) {
            errors[CreateField.PLAYERS] =
                "Must be a multiple of 4 and at least ${TournamentPlayerCountValidator.MIN_PLAYERS}."
        }
        if (numRounds == null || numRounds <= 0) errors[CreateField.ROUNDS] = "Must be a positive whole number."
        if (!Regex("^[A-Z]{2,3}$").matches(normalizedHostCountry)) errors[CreateField.COUNTRY] = "Select a host country."
        if (trimmedHostCity.isBlank()) errors[CreateField.CITY] = "City is required."
        venueRequiredError(venue)?.let { errors[CreateField.VENUE] = it }
        contactEmailRequiredError(venue.contactEmail)?.let { errors[CreateField.CONTACT_EMAIL] = it }
        if (trimmedStartDate == null) errors[CreateField.START_DATE] = "Use DD/MM/YYYY."
        if (trimmedEndDate == null) {
            errors[CreateField.END_DATE] = "Use DD/MM/YYYY."
        } else if (trimmedStartDate != null &&
            !TournamentDateRangeValidator.isValidRange(trimmedStartDate, trimmedEndDate)
        ) {
            errors[CreateField.END_DATE] = "The end date must not be before the start date."
        }
        if (matchDurationMinutes == null) {
            errors[CreateField.MATCH_DURATION] = "Use a whole number of minutes from 1 to $MAX_MATCH_DURATION_MINUTES."
        }
        if (!isTimeZoneIdShape(timeZoneText)) errors[CreateField.TIME_ZONE] = "Use a name like Europe/Madrid."
        if (finishHandInPlay && matchExtraMinutes == null) {
            errors[CreateField.EXTRA_TIME] = "Use a whole number of minutes from 1 to $MAX_MATCH_EXTRA_MINUTES."
        }
        if (!Regex("^#[0-9A-F]{6}$").matches(normalizedPrimaryColor)) errors[CreateField.COLOR] = "Use #RRGGBB format."
        if (errors.isNotEmpty()) {
            fieldErrors = errors
            val firstField = errors.keys.first()
            selectedTab = firstField.tab()
            pendingFocusField = firstField
            return
        }
        if (trimmedStartDate == null || trimmedEndDate == null || numPlayers == null || numRounds == null ||
            matchDurationMinutes == null
        ) {
            return
        }
        fieldErrors = emptyMap()
        val scheduleError = tournamentScheduleEditorError(
            roundRows = roundScheduleRows,
            agendaRows = agendaRows,
            eventStartDate = trimmedStartDate,
            eventEndDate = trimmedEndDate,
        )
        if (scheduleError != null) {
            errorMessage = "* $scheduleError"
            return
        }

        errorMessage = null
        isLoading = true
        progress = null
        calcStartMark = TimeSource.Monotonic.markNow()

        val progressSink: (CreateTournamentPresenter.Progress) -> Unit = { p ->
            coroutineScope.launch { progress = p }
        }

        createJob = coroutineScope.launch {
            try {
                when (val result = presenter.createTournament(
                    name = trimmedName,
                    shortName = trimmedShortName,
                    primaryColor = normalizedPrimaryColor,
                    associationLogoContentType = associationLogo?.contentType,
                    associationLogoBytes = associationLogo?.bytes,
                    associationLogoSourceTournamentId = reusedLogoTournament?.id,
                    eventStartDate = trimmedStartDate,
                    eventEndDate = trimmedEndDate,
                    hostCountry = normalizedHostCountry,
                    hostCity = trimmedHostCity,
                    venue = venue,
                    isTeams = isTeams,
                    countBestHands = countBestHands,
                    countChickenHands = countChickenHands,
                    numPlayers = numPlayers,
                    numRounds = numRounds,
                    roundSchedules = roundScheduleRows.toTournamentRoundSchedules(),
                    agendaItems = agendaRows.toTournamentAgendaItems(),
                    matchDurationMinutes = matchDurationMinutes,
                    matchEndMode = if (finishHandInPlay) MatchEndMode.FINISH_HAND else MatchEndMode.STOP,
                    matchExtraMinutes = matchExtraMinutes ?: DEFAULT_MATCH_EXTRA_MINUTES,
                    timeZone = timeZoneText.trim(),
                    computeMode = computeMode,
                    onProgress = progressSink,
                )) {
                    is AppResult.Success -> {
                        store.addTournament(result.value)
                        onCreated(result.value)
                    }

                    is AppResult.Failure -> {
                        errorMessage = result.error.toUiMessage()
                    }
                }
            } catch (_: CancellationException) {
                // User cancelled or navigated away.
            } finally {
                isLoading = false
                createJob = null
                progress = null
                calcStartMark = null
            }
        }
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup().widthIn(max = 720.dp).fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = {
            if (!isLoading) {
                cancelCreation()
                onDismiss()
            }
        },
        title = { Text("New tournament") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    edgePadding = 0.dp,
                ) {
                    CreateTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.title) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                        Text(
                            "Creates rounds, tables, and players automatically.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Fields marked with * are required.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        when (selectedTab) {
                        CreateTab.GENERAL -> {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    clearError(CreateField.NAME)
                                },
                                label = { RequiredLabel("Name") },
                                singleLine = true,
                                enabled = !isLoading,
                                isError = CreateField.NAME in fieldErrors,
                                supportingText = fieldErrors[CreateField.NAME]?.let { message -> { Text(message) } },
                                modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { shortNameFocusRequester.requestFocus() }),
                            )
                            OutlinedTextField(
                                value = shortName,
                                onValueChange = {
                                    shortName = it.take(10)
                                    clearError(CreateField.SHORT_NAME)
                                },
                                label = { RequiredLabel("Short name") },
                                placeholder = { Text("Max. 10 characters") },
                                singleLine = true,
                                enabled = !isLoading,
                                isError = CreateField.SHORT_NAME in fieldErrors,
                                supportingText = fieldErrors[CreateField.SHORT_NAME]?.let { message -> { Text(message) } },
                                modifier = Modifier.fillMaxWidth().focusRequester(shortNameFocusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { playersFocusRequester.requestFocus() }),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = numPlayersText,
                                    onValueChange = {
                                        numPlayersText = it.toDigitsInput()
                                        clearError(CreateField.PLAYERS)
                                    },
                                    label = { RequiredLabel("Players") },
                                    placeholder = { Text("Multiple of 4, minimum ${TournamentPlayerCountValidator.MIN_PLAYERS}") },
                                    modifier = Modifier.weight(1f).focusRequester(playersFocusRequester),
                                    singleLine = true,
                                    enabled = !isLoading,
                                    isError = CreateField.PLAYERS in fieldErrors,
                                    supportingText = fieldErrors[CreateField.PLAYERS]?.let { message -> { Text(message) } },
                                )
                                OutlinedTextField(
                                    value = numRoundsText,
                                    onValueChange = { typed ->
                                        val value = typed.toDigitsInput()
                                        numRoundsText = value
                                        roundScheduleRows = synchronizeRoundScheduleRows(
                                            rows = roundScheduleRows,
                                            roundCount = value.trim().toIntOrNull() ?: 0,
                                        )
                                        clearError(CreateField.ROUNDS)
                                    },
                                    label = { RequiredLabel("Rounds") },
                                    modifier = Modifier.weight(1f).focusRequester(roundsFocusRequester),
                                    singleLine = true,
                                    enabled = !isLoading,
                                    isError = CreateField.ROUNDS in fieldErrors,
                                    supportingText = fieldErrors[CreateField.ROUNDS]?.let { message -> { Text(message) } },
                                )
                            }

                            MatchTimeFields(
                                durationText = matchDurationText,
                                onDurationChange = {
                                    matchDurationText = it
                                    clearError(CreateField.MATCH_DURATION)
                                },
                                finishHand = finishHandInPlay,
                                onFinishHandChange = {
                                    finishHandInPlay = it
                                    clearError(CreateField.EXTRA_TIME)
                                },
                                extraMinutesText = matchExtraText,
                                onExtraMinutesChange = {
                                    matchExtraText = it
                                    clearError(CreateField.EXTRA_TIME)
                                },
                                enabled = !isLoading,
                                durationFocusRequester = matchDurationFocusRequester,
                                durationError = fieldErrors[CreateField.MATCH_DURATION],
                                extraMinutesError = fieldErrors[CreateField.EXTRA_TIME],
                            )

                            FocusHighlightContainer(
                                modifier = Modifier.fillMaxWidth(),
                                interactionSource = teamsToggleInteractionSource,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Switch(
                                        checked = isTeams,
                                        onCheckedChange = ::setTeamsChecked,
                                        enabled = !isLoading,
                                        interactionSource = teamsToggleInteractionSource,
                                    )
                                    Text(
                                        text = "Teams",
                                        modifier = Modifier.toggleable(
                                            value = isTeams,
                                            enabled = !isLoading,
                                            role = Role.Switch,
                                            interactionSource = teamsToggleInteractionSource,
                                            indication = null,
                                            onValueChange = ::setTeamsChecked,
                                        ),
                                    )
                                    InfoTooltipIcon(
                                        "Group players into teams of four. Team members will not play together.",
                                    )
                                }
                            }

                            TournamentOptionSwitch(
                                label = "Best hands",
                                description = "Count the best hands and show them in the ranking and the tournament screens.",
                                checked = countBestHands,
                                enabled = !isLoading,
                                onCheckedChange = { countBestHands = it },
                            )

                            TournamentOptionSwitch(
                                label = "Chicken hands",
                                description = "Count the chicken hands and show them in the ranking and the tournament screens.",
                                checked = countChickenHands,
                                enabled = !isLoading,
                                onCheckedChange = { countChickenHands = it },
                            )

                            FocusHighlightContainer(
                                modifier = Modifier.fillMaxWidth(),
                                interactionSource = computeToggleInteractionSource,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val isHeavy = computeMode == CreateTournamentPresenter.ComputeMode.HEAVY
                                    Switch(
                                        checked = isHeavy,
                                        onCheckedChange = ::setHeavyCompute,
                                        enabled = !isLoading,
                                        interactionSource = computeToggleInteractionSource,
                                    )
                                    Text(
                                        text = "Heavy computing",
                                        modifier = Modifier.toggleable(
                                            value = isHeavy,
                                            enabled = !isLoading,
                                            role = Role.Switch,
                                            interactionSource = computeToggleInteractionSource,
                                            indication = null,
                                            onValueChange = ::setHeavyCompute,
                                        ),
                                    )
                                    InfoTooltipIcon("Use more CPU to calculate the schedule faster.")
                                }
                            }
                        }
                        CreateTab.VENUE -> {
                            TournamentPlaceFields(
                                venue = venue,
                                onVenueChange = {
                            venue = it
                            clearError(CreateField.VENUE, CreateField.CONTACT_EMAIL)
                        },
                                hostCountry = hostCountry,
                                onHostCountryChange = {
                                    hostCountry = it
                                    clearError(CreateField.COUNTRY)
                                },
                                hostCity = hostCity,
                                onHostCityChange = {
                                    hostCity = it
                                    clearError(CreateField.CITY)
                                },
                                countries = countries,
                                resolveLink = presenter::resolveVenueLink,
                                enabled = !isLoading,
                                countryError = fieldErrors[CreateField.COUNTRY],
                                cityError = fieldErrors[CreateField.CITY],
                                countryFocusRequester = hostCountryFocusRequester,
                                cityFocusRequester = hostCityFocusRequester,
                                venueError = fieldErrors[CreateField.VENUE],
                                contactEmailError = fieldErrors[CreateField.CONTACT_EMAIL],
                                mapUrlFocusRequester = mapUrlFocusRequester,
                                contactEmailFocusRequester = contactEmailFocusRequester,
                            )
                            VenueTimeZoneField(
                                timeZoneText = timeZoneText,
                                onTimeZoneChange = {
                                    timeZoneText = it
                                    clearError(CreateField.TIME_ZONE)
                                },
                                enabled = !isLoading,
                                timeZoneError = fieldErrors[CreateField.TIME_ZONE],
                            )
                        }
                        CreateTab.BRANDING -> {
                            TournamentColorField(
                                value = primaryColor,
                                enabled = !isLoading,
                                isError = CreateField.COLOR in fieldErrors,
                                errorMessage = fieldErrors[CreateField.COLOR],
                                onValueChange = {
                                    primaryColor = it
                                    clearError(CreateField.COLOR)
                                },
                                onPreviewClick = {
                                    showDateRangePicker = false
                                    showColorPickerDialog = true
                                },
                                fieldModifier = Modifier.focusRequester(primaryColorFocusRequester),
                            )

                            Text("Logo", style = MaterialTheme.typography.titleSmall)
                            val logoModel = associationLogo?.dataUrl ?: reusedLogoTournament?.associationLogoUrl
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
                                            associationLogo != null -> buildString {
                                                append(associationLogo?.fileName.orEmpty())
                                                append(" · ")
                                                append(formatByteSize(associationLogo?.bytes?.size?.toLong() ?: 0L))
                                                logoImageInfo?.let { append(" · ").append(it) }
                                            }
                                            reusedLogoTournament != null -> "Logo from ${reusedLogoTournament?.name}"
                                            else -> "No logo"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(enabled = !isLoading, onClick = logoPicker::launch) {
                                            Text(if (logoModel == null) "Choose logo" else "Upload logo")
                                        }
                                        if (tournaments.any { !it.associationLogoUrl.isNullOrBlank() }) {
                                            Button(
                                                enabled = !isLoading,
                                                onClick = { showLogoLibrary = true },
                                            ) {
                                                Text("Reuse existing")
                                            }
                                        }
                                        if (logoModel != null) {
                                            AppTextButton(
                                                enabled = !isLoading,
                                                onClick = {
                                                    associationLogo = null
                                                    reusedLogoTournament = null
                                                    logoImageInfo = null
                                                },
                                            ) { Text("Remove") }
                                        }
                                    }
                                }
                            }
                            TournamentIdCardPreviewButton(
                                shortName = shortName,
                                primaryColor = primaryColor,
                                year = eventStartDate.toIsoTournamentDateOrNull()?.take(4).orEmpty(),
                                associationLogoContentType = associationLogo?.contentType,
                                associationLogoBytes = associationLogo?.bytes,
                                associationLogoUrl = reusedLogoTournament?.associationLogoUrl,
                                roundSchedules = roundScheduleRows.toTournamentRoundSchedules(),
                                onError = { errorMessage = it },
                            )
                        }
                        CreateTab.SCHEDULE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = eventStartDate,
                                    onValueChange = {
                                        eventStartDate = it.toDateInput()
                                        eventEndDate = adjustedEndDate(eventStartDate, eventEndDate)
                                        clearError(CreateField.START_DATE, CreateField.END_DATE)
                                    },
                                    label = { RequiredLabel("From") },
                                    placeholder = { Text("DD/MM/YYYY") },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            androidx.compose.material3.Icon(
                                                Icons.Default.DateRange,
                                                contentDescription = "Choose start date",
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).focusRequester(eventStartDateFocusRequester),
                                    singleLine = true,
                                    enabled = !isLoading,
                                    isError = CreateField.START_DATE in fieldErrors,
                                    supportingText = fieldErrors[CreateField.START_DATE]?.let { message -> { Text(message) } },
                                )
                                OutlinedTextField(
                                    value = eventEndDate,
                                    onValueChange = {
                                        val newValue = it.toDateInput()
                                        eventEndDate = when {
                                            newValue.isEmpty() && eventStartDate.toIsoTournamentDateOrNull() != null -> eventStartDate
                                            else -> adjustedEndDate(eventStartDate, newValue)
                                        }
                                        clearError(CreateField.START_DATE, CreateField.END_DATE)
                                    },
                                    label = { RequiredLabel("To") },
                                    placeholder = { Text("DD/MM/YYYY") },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            androidx.compose.material3.Icon(
                                                Icons.Default.DateRange,
                                                contentDescription = "Choose end date",
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).focusRequester(eventEndDateFocusRequester),
                                    singleLine = true,
                                    enabled = !isLoading,
                                    isError = CreateField.END_DATE in fieldErrors,
                                    supportingText = fieldErrors[CreateField.END_DATE]?.let { message -> { Text(message) } },
                                )
                            }
                            TournamentScheduleEditor(
                                roundRows = roundScheduleRows,
                                onRoundRowsChange = { roundScheduleRows = it },
                                agendaRows = agendaRows,
                                onAgendaRowsChange = { agendaRows = it },
                                enabled = !isLoading,
                                minimumDisplayDate = eventStartDate,
                                maximumDisplayDate = eventEndDate,
                            )
                        }
                        }
                    errorMessage?.let {
                        AppErrorDialog(
                            message = it,
                            onDismiss = { errorMessage = null },
                        )
                    }

                    if (isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(
                                when (progress?.phase) {
                                    CreateTournamentPresenter.Phase.CREATING -> "Saving tournament…"
                                    else -> "Calculating schedule…"
                                },
                            )
                        }
                        progress?.let { currentProgress ->
                            if (currentProgress.phase == CreateTournamentPresenter.Phase.CALCULATING) {
                                Text("Tried: ${formatWithDots(currentProgress.tried)}")
                                RunningTriesSlots(
                                    runningTries = currentProgress.runningTries,
                                    maxConcurrency = currentProgress.maxConcurrency,
                                )
                            }
                        }
                    }
                }
            }
            }
        },
        confirmButton = {
            Button(
                onClick = if (isLoading) ::cancelCreation else ::startCreate,
            ) {
                Text(if (isLoading) "Cancel calculation" else "Create")
            }
        },
        dismissButton = if (isLoading) {
            null
        } else {
            {
                AppTextButton(
                    onClick = {
                        cancelCreation()
                        onDismiss()
                    },
                ) {
                    Text("Cancel")
                }
            }
        },
    )

    if (showDateRangePicker) {
        TournamentDateRangePickerDialog(
            selectedStartDisplayDate = eventStartDate,
            selectedEndDisplayDate = eventEndDate,
            onDismiss = { showDateRangePicker = false },
            onDatesSelected = { selectedStartDate, selectedEndDate ->
                eventStartDate = selectedStartDate
                eventEndDate = selectedEndDate
                showDateRangePicker = false
            },
        )
    }

    if (showColorPickerDialog) {
        TournamentColorPickerDialog(
            initialColor = primaryColor,
            onDismiss = { showColorPickerDialog = false },
            onColorSelected = { selectedColor ->
                primaryColor = selectedColor
                showColorPickerDialog = false
            },
        )
    }

    logoToCrop?.let { image ->
        TournamentLogoCropDialog(
            image = image,
            onDismiss = { logoToCrop = null },
            onCropped = { croppedImage ->
                associationLogo = croppedImage
                reusedLogoTournament = null
                logoImageInfo = null
                errorMessage = null
                logoToCrop = null
            },
            onError = { message -> errorMessage = "* $message" },
        )
    }

    if (showLogoLibrary) {
        TournamentLogoLibraryDialog(
            tournaments = tournaments,
            onDismiss = { showLogoLibrary = false },
            onSelect = { tournament ->
                associationLogo = null
                reusedLogoTournament = tournament
                logoImageInfo = null
                errorMessage = null
                showLogoLibrary = false
            },
        )
    }
}

private fun formatWithDots(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val reversed = s.reversed().chunked(3).joinToString(".")
    return reversed.reversed()
}

private fun formatDecimal(value: Double, decimals: Int): String {
    val safeDecimals = decimals.coerceIn(0, 6)
    val factor = (0 until safeDecimals).fold(1.0) { acc, _ -> acc * 10.0 }
    val rounded = (value * factor).roundToLong().toDouble() / factor
    // Avoid "-0.0"
    val safe = if (abs(rounded) < 0.000_000_1) 0.0 else rounded
    return if (decimals == 0) safe.toLong().toString() else safe.toString()
}

@Composable
private fun RunningTriesSlots(
    runningTries: Int,
    maxConcurrency: Int,
) {
    val safeMax = maxConcurrency.coerceAtLeast(1)
    val filled = runningTries.coerceIn(0, safeMax)
    val shape = RoundedCornerShape(3.dp)
    val columns = minOf(25, safeMax)
    val slotSize = 10.dp
    val spacing = 2.dp
    val rows = (safeMax + columns - 1) / columns
    val gridWidth =
        (slotSize * columns.toFloat()) + (spacing * (columns - 1).coerceAtLeast(0).toFloat())
    val gridHeight = (slotSize * rows.toFloat()) + (spacing * (rows - 1).coerceAtLeast(0).toFloat())
    val maxGridHeight = 420.dp

    @OptIn(ExperimentalFoundationApi::class)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier
            .widthIn(min = gridWidth, max = gridWidth)
            .heightIn(max = gridHeight.coerceAtMost(maxGridHeight)),
    ) {
        items(count = safeMax) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(slotSize)
                    .clip(shape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .background(
                        color = if (isFilled) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = shape,
                    ),
            )
        }
    }
}
