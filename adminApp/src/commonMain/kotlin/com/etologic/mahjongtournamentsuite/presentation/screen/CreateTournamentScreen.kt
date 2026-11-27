package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_EXTRA_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_ORGANIZATION_COLOR
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RepeatFreeRounds
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentPlayerCountValidator
import com.etologic.mahjongtournamentsuite.presentation.components.AgendaItemEditorRow
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.CreateProgressDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DateField
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationButton
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.MAX_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.presentation.components.MAX_MATCH_EXTRA_MINUTES
import com.etologic.mahjongtournamentsuite.presentation.components.MatchTimeFields
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsSwitchRow
import com.etologic.mahjongtournamentsuite.presentation.components.RequiredLabel
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDateRangePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentIdCardPreviewButton
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentOptionSwitch
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentOrganizersField
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentPlaceFields
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentScheduleEditor
import com.etologic.mahjongtournamentsuite.presentation.components.VenueTimeZoneField
import com.etologic.mahjongtournamentsuite.presentation.components.adjustedEndDate
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.compactDateTextStyle
import com.etologic.mahjongtournamentsuite.presentation.components.contactEmailRequiredError
import com.etologic.mahjongtournamentsuite.presentation.components.deviceTimeZoneId
import com.etologic.mahjongtournamentsuite.presentation.components.isTimeZoneIdShape
import com.etologic.mahjongtournamentsuite.presentation.components.matchDurationOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.matchExtraMinutesOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.synchronizeRoundScheduleRows
import com.etologic.mahjongtournamentsuite.presentation.components.toDateInput
import com.etologic.mahjongtournamentsuite.presentation.components.toDigitsInput
import com.etologic.mahjongtournamentsuite.presentation.components.toDisplayTournamentDate
import com.etologic.mahjongtournamentsuite.presentation.components.toIsoTournamentDateOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentAgendaItems
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentRoundSchedules
import com.etologic.mahjongtournamentsuite.presentation.components.tournamentScheduleEditorError
import com.etologic.mahjongtournamentsuite.presentation.components.venueRequiredError
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedTab
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.best_hands
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.chicken_hands
import mahjongtournamentsuite.adminapp.generated.resources.choose_end_date
import mahjongtournamentsuite.adminapp.generated.resources.choose_start_date
import mahjongtournamentsuite.adminapp.generated.resources.count_the_best_hands_and_show
import mahjongtournamentsuite.adminapp.generated.resources.count_the_chicken_hands_and_show
import mahjongtournamentsuite.adminapp.generated.resources.create
import mahjongtournamentsuite.adminapp.generated.resources.create_heavy_info
import mahjongtournamentsuite.adminapp.generated.resources.create_intro
import mahjongtournamentsuite.adminapp.generated.resources.create_players_placeholder
import mahjongtournamentsuite.adminapp.generated.resources.create_required_note
import mahjongtournamentsuite.adminapp.generated.resources.create_rounds_blocks
import mahjongtournamentsuite.adminapp.generated.resources.create_rounds_maybe_blocks
import mahjongtournamentsuite.adminapp.generated.resources.create_teams_info
import mahjongtournamentsuite.adminapp.generated.resources.date_to
import mahjongtournamentsuite.adminapp.generated.resources.dd_mm_yyyy
import mahjongtournamentsuite.adminapp.generated.resources.error_star_prefix
import mahjongtournamentsuite.adminapp.generated.resources.field_error_city
import mahjongtournamentsuite.adminapp.generated.resources.field_error_country
import mahjongtournamentsuite.adminapp.generated.resources.field_error_date
import mahjongtournamentsuite.adminapp.generated.resources.field_error_end_before_start
import mahjongtournamentsuite.adminapp.generated.resources.field_error_minutes
import mahjongtournamentsuite.adminapp.generated.resources.field_error_name_required
import mahjongtournamentsuite.adminapp.generated.resources.field_error_players
import mahjongtournamentsuite.adminapp.generated.resources.field_error_rounds
import mahjongtournamentsuite.adminapp.generated.resources.field_error_short_name
import mahjongtournamentsuite.adminapp.generated.resources.field_error_time_zone
import mahjongtournamentsuite.adminapp.generated.resources.from
import mahjongtournamentsuite.adminapp.generated.resources.heavy_computing
import mahjongtournamentsuite.adminapp.generated.resources.max_10_characters
import mahjongtournamentsuite.adminapp.generated.resources.name
import mahjongtournamentsuite.adminapp.generated.resources.new_tournament
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.registration_closes_on_optional
import mahjongtournamentsuite.adminapp.generated.resources.registration_open_switch
import mahjongtournamentsuite.adminapp.generated.resources.registration_open_switch_info
import mahjongtournamentsuite.adminapp.generated.resources.registration_places_error
import mahjongtournamentsuite.adminapp.generated.resources.registration_places_optional
import mahjongtournamentsuite.adminapp.generated.resources.registration_places_placeholder
import mahjongtournamentsuite.adminapp.generated.resources.rounds
import mahjongtournamentsuite.adminapp.generated.resources.short_name
import mahjongtournamentsuite.adminapp.generated.resources.tab_general
import mahjongtournamentsuite.adminapp.generated.resources.tab_organizers
import mahjongtournamentsuite.adminapp.generated.resources.tab_schedule
import mahjongtournamentsuite.adminapp.generated.resources.tab_venue
import mahjongtournamentsuite.adminapp.generated.resources.teams
import mahjongtournamentsuite.adminapp.generated.resources.tournament_main_organizer_needed
import mahjongtournamentsuite.adminapp.generated.resources.tournament_organizers_need_yours
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTextTab as Tab

/** The fields of the form that can be wrong. A wrong field gets a red mark and a short text. */
private enum class CreateField {
    NAME,
    SHORT_NAME,
    PLAYERS,
    ROUNDS,
    COUNTRY,
    CITY,
    VENUE,
    CONTACT_EMAIL,
    START_DATE,
    END_DATE,
    MATCH_DURATION,
    TIME_ZONE,
    EXTRA_TIME,
    ORGANIZERS,
}

private enum class CreateTab(val title: StringResource) {
    GENERAL(Res.string.tab_general),
    VENUE(Res.string.tab_venue),
    ORGANIZERS(Res.string.tab_organizers),
    SCHEDULE(Res.string.tab_schedule),
}

/** The tab that holds the field. */
private fun CreateField.tab(): CreateTab = when (this) {
    CreateField.NAME, CreateField.SHORT_NAME, CreateField.PLAYERS, CreateField.ROUNDS,
    CreateField.MATCH_DURATION, CreateField.EXTRA_TIME,
    -> CreateTab.GENERAL
    CreateField.COUNTRY, CreateField.CITY, CreateField.VENUE, CreateField.CONTACT_EMAIL,
    CreateField.TIME_ZONE,
    -> CreateTab.VENUE
    CreateField.ORGANIZERS -> CreateTab.ORGANIZERS
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
    val coroutineScope = rememberCoroutineScope()
    val nameFocusRequester = remember { FocusRequester() }
    val shortNameFocusRequester = remember { FocusRequester() }
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
    val adminStatus by store.adminStatus.collectAsState()
    // A club admin creates tournaments of its organizations. A super admin creates them for any organization.
    val ownOrganizations = adminStatus?.takeUnless { it.isSuperAdmin }?.adminOrganizations
    var organizations by remember { mutableStateOf<List<Organization>>(emptyList()) }
    var organizerIds by remember { mutableStateOf(ownOrganizations?.singleOrNull()?.let(::listOf).orEmpty()) }
    val mainOrganizer = organizations.firstOrNull { it.id == organizerIds.firstOrNull() }
    var hostCountry by remember { mutableStateOf("") }
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var hostCity by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf(TournamentVenue()) }
    var showDrawExplanation by remember { mutableStateOf(false) }
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
    var timeZoneText by remember { mutableStateOf(deviceTimeZoneId()) }
    var computeMode by remember { mutableStateOf(CreateTournamentPresenter.ComputeMode.LIGHT) }
    // A tournament with registrations has no players yet: the draw comes when the registrations close.
    var withRegistration by remember { mutableStateOf(false) }
    var maxPlayersText by remember { mutableStateOf("") }
    var closesOn by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<UiText?>(null) }
    var fieldErrorTexts by remember { mutableStateOf<Map<CreateField, UiText>>(emptyMap()) }
    val fieldErrors = fieldErrorTexts.mapValues { it.value.asString() }
    var progress by remember { mutableStateOf<CreateTournamentPresenter.Progress?>(null) }
    var createJob by remember { mutableStateOf<Job?>(null) }
    var calcStartMark by remember { mutableStateOf<TimeMark?>(null) }
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
        fieldErrorTexts = fieldErrorTexts - fields.toSet()
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
            CreateField.TIME_ZONE, CreateField.EXTRA_TIME, CreateField.ORGANIZERS -> null
        }?.requestFocus()
        pendingFocusField = null
    }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
        when (val result = presenter.loadCountries()) {
            is AppResult.Success -> countries = result.value
            is AppResult.Failure -> errorMessage = result.error.toUiText()
        }
        when (val result = presenter.loadOrganizations()) {
            is AppResult.Success -> {
                organizations = result.value
                // The country of the main organizer is a good first guess for the host country.
                if (hostCountry.isBlank()) hostCountry = result.value.firstOrNull { it.id == organizerIds.firstOrNull() }?.country.orEmpty()
            }
            is AppResult.Failure -> errorMessage = result.error.toUiText()
        }
    }

    fun startCreate() {
        if (isLoading) return

        val trimmedName = name.trim()
        val trimmedShortName = shortName.trim()
        val normalizedHostCountry = hostCountry.trim().uppercase()
        val trimmedHostCity = hostCity.trim()
        val trimmedStartDate = eventStartDate.toIsoTournamentDateOrNull()
        val trimmedEndDate = eventEndDate.toIsoTournamentDateOrNull()
        val numPlayers = numPlayersText.trim().toIntOrNull()
        val numRounds = numRoundsText.trim().toIntOrNull()

        val matchDurationMinutes = matchDurationOrNull(matchDurationText)
        val matchExtraMinutes = matchExtraMinutesOrNull(matchExtraText)
        val errors = linkedMapOf<CreateField, UiText>()
        if (trimmedName.isBlank()) errors[CreateField.NAME] = UiText.of(Res.string.field_error_name_required)
        if (trimmedShortName.isBlank() || trimmedShortName.length > 10) {
            errors[CreateField.SHORT_NAME] = UiText.of(Res.string.field_error_short_name)
        }
        val maxPlayers = maxPlayersText.trim().toIntOrNull()
        if (withRegistration) {
            if (maxPlayersText.isNotBlank() && (maxPlayers == null || maxPlayers !in 4..1000)) {
                errors[CreateField.PLAYERS] = UiText.of(Res.string.registration_places_error)
            }
        } else if (numPlayers == null || !TournamentPlayerCountValidator.isValid(numPlayers)) {
            errors[CreateField.PLAYERS] =
                UiText.of(Res.string.field_error_players, TournamentPlayerCountValidator.MIN_PLAYERS)
        }
        if (numRounds == null || numRounds <= 0) errors[CreateField.ROUNDS] = UiText.of(Res.string.field_error_rounds)
        if (!Regex("^[A-Z]{2,3}$").matches(normalizedHostCountry)) errors[CreateField.COUNTRY] = UiText.of(Res.string.field_error_country)
        if (trimmedHostCity.isBlank()) errors[CreateField.CITY] = UiText.of(Res.string.field_error_city)
        venueRequiredError(venue)?.let { errors[CreateField.VENUE] = it }
        contactEmailRequiredError(venue.contactEmail)?.let { errors[CreateField.CONTACT_EMAIL] = it }
        if (trimmedStartDate == null) errors[CreateField.START_DATE] = UiText.of(Res.string.field_error_date)
        if (trimmedEndDate == null) {
            errors[CreateField.END_DATE] = UiText.of(Res.string.field_error_date)
        } else if (trimmedStartDate != null &&
            !TournamentDateRangeValidator.isValidRange(trimmedStartDate, trimmedEndDate)
        ) {
            errors[CreateField.END_DATE] = UiText.of(Res.string.field_error_end_before_start)
        }
        if (matchDurationMinutes == null) {
            errors[CreateField.MATCH_DURATION] = UiText.of(Res.string.field_error_minutes, MAX_MATCH_DURATION_MINUTES)
        }
        if (!isTimeZoneIdShape(timeZoneText)) errors[CreateField.TIME_ZONE] = UiText.of(Res.string.field_error_time_zone)
        if (finishHandInPlay && matchExtraMinutes == null) {
            errors[CreateField.EXTRA_TIME] = UiText.of(Res.string.field_error_minutes, MAX_MATCH_EXTRA_MINUTES)
        }
        if (organizerIds.isEmpty()) {
            errors[CreateField.ORGANIZERS] = UiText.of(Res.string.tournament_main_organizer_needed)
        } else if (ownOrganizations != null && organizerIds.none { it in ownOrganizations }) {
            errors[CreateField.ORGANIZERS] = UiText.of(Res.string.tournament_organizers_need_yours)
        }
        if (errors.isNotEmpty()) {
            fieldErrorTexts = errors
            val firstField = errors.keys.first()
            selectedTab = firstField.tab()
            pendingFocusField = firstField
            return
        }
        if (trimmedStartDate == null ||
            trimmedEndDate == null ||
            (numPlayers == null && !withRegistration) ||
            numRounds == null ||
            matchDurationMinutes == null
        ) {
            return
        }
        fieldErrorTexts = emptyMap()
        val scheduleError = tournamentScheduleEditorError(
            roundRows = roundScheduleRows,
            agendaRows = agendaRows,
            eventStartDate = trimmedStartDate,
            eventEndDate = trimmedEndDate,
        )
        if (scheduleError != null) {
            errorMessage = UiText.of(Res.string.error_star_prefix, scheduleError)
            return
        }

        errorMessage = null
        isLoading = true
        progress = null
        calcStartMark = TimeSource.Monotonic.markNow()

        // The draw reports its progress from the worker threads: the state of the dialog changes on the main thread only.
        val progressSink: (CreateTournamentPresenter.Progress) -> Unit = { p ->
            coroutineScope.launch(Dispatchers.Main) { progress = p }
        }

        createJob = coroutineScope.launch(Dispatchers.Main) {
            try {
                when (
                    val result = presenter.createTournament(
                        name = trimmedName,
                        shortName = trimmedShortName,
                        organizerIds = organizerIds,
                        eventStartDate = trimmedStartDate,
                        eventEndDate = trimmedEndDate,
                        hostCountry = normalizedHostCountry,
                        hostCity = trimmedHostCity,
                        venue = venue,
                        isTeams = isTeams,
                        countBestHands = countBestHands,
                        countChickenHands = countChickenHands,
                        numPlayers = numPlayers ?: 0,
                        numRounds = numRounds,
                        roundSchedules = roundScheduleRows.toTournamentRoundSchedules(),
                        agendaItems = agendaRows.toTournamentAgendaItems(),
                        matchDurationMinutes = matchDurationMinutes,
                        matchEndMode = if (finishHandInPlay) MatchEndMode.FINISH_HAND else MatchEndMode.STOP,
                        matchExtraMinutes = matchExtraMinutes ?: DEFAULT_MATCH_EXTRA_MINUTES,
                        timeZone = timeZoneText.trim(),
                        computeMode = computeMode,
                        onProgress = progressSink,
                        registration = if (withRegistration) {
                            RegistrationSettings(maxPlayers = maxPlayers, closesOn = closesOn.ifBlank { null })
                        } else {
                            null
                        },
                    )
                ) {
                    is AppResult.Success -> {
                        store.addTournament(result.value)
                        // The creation suspends. The navigation of onCreated must run on the main thread.
                        withContext(Dispatchers.Main) { onCreated(result.value) }
                    }

                    is AppResult.Failure -> {
                        errorMessage = result.error.toUiText()
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

    TrackedDialog("NewTournament") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().widthIn(max = 720.dp).fillMaxWidth(0.92f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = {
                if (!isLoading) {
                    cancelCreation()
                    onDismiss()
                }
            },
            title = { MtsDialogTitle(stringResource(Res.string.new_tournament)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        edgePadding = 0.dp,
                    ) {
                        CreateTab.entries.forEach { tab ->
                            if (tab == selectedTab) TrackedTab(tab.name)
                            Tab(
                                selected = tab == selectedTab,
                                onClick = { selectedTab = tab },
                                text = { ButtonText(stringResource(tab.title), fontWeight = null) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ScrollableColumnWithScrollbar(
                        state = dialogScrollState,
                        inDialog = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(Res.string.create_intro),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(Res.string.create_required_note),
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
                                        label = { RequiredLabel(stringResource(Res.string.name)) },
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
                                        label = { RequiredLabel(stringResource(Res.string.short_name)) },
                                        placeholder = { Text(stringResource(Res.string.max_10_characters)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        isError = CreateField.SHORT_NAME in fieldErrors,
                                        supportingText = fieldErrors[CreateField.SHORT_NAME]?.let { message -> { Text(message) } },
                                        modifier = Modifier.fillMaxWidth().focusRequester(shortNameFocusRequester),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { playersFocusRequester.requestFocus() }),
                                    )
                                    TournamentOptionSwitch(
                                        label = stringResource(Res.string.registration_open_switch),
                                        description = stringResource(Res.string.registration_open_switch_info),
                                        checked = withRegistration,
                                        enabled = !isLoading,
                                        onCheckedChange = {
                                            withRegistration = it
                                            clearError(CreateField.PLAYERS)
                                        },
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (withRegistration) {
                                            OutlinedTextField(
                                                value = maxPlayersText,
                                                onValueChange = {
                                                    maxPlayersText = it.toDigitsInput()
                                                    clearError(CreateField.PLAYERS)
                                                },
                                                label = { Text(stringResource(Res.string.registration_places_optional)) },
                                                placeholder = { Text(stringResource(Res.string.registration_places_placeholder)) },
                                                modifier = Modifier.weight(1f).focusRequester(playersFocusRequester),
                                                singleLine = true,
                                                enabled = !isLoading,
                                                isError = CreateField.PLAYERS in fieldErrors,
                                                supportingText = fieldErrors[CreateField.PLAYERS]?.let { message -> { Text(message) } },
                                            )
                                        } else {
                                            OutlinedTextField(
                                                value = numPlayersText,
                                                onValueChange = {
                                                    numPlayersText = it.toDigitsInput()
                                                    clearError(CreateField.PLAYERS)
                                                },
                                                label = { RequiredLabel(stringResource(Res.string.players)) },
                                                placeholder = { Text(stringResource(Res.string.create_players_placeholder, TournamentPlayerCountValidator.MIN_PLAYERS)) },
                                                modifier = Modifier.weight(1f).focusRequester(playersFocusRequester),
                                                singleLine = true,
                                                enabled = !isLoading,
                                                isError = CreateField.PLAYERS in fieldErrors,
                                                supportingText = fieldErrors[CreateField.PLAYERS]?.let { message -> { Text(message) } },
                                            )
                                        }
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
                                            label = { RequiredLabel(stringResource(Res.string.rounds)) },
                                            modifier = Modifier.weight(1f).focusRequester(roundsFocusRequester),
                                            singleLine = true,
                                            enabled = !isLoading,
                                            isError = CreateField.ROUNDS in fieldErrors,
                                            supportingText = fieldErrors[CreateField.ROUNDS]?.let { message -> { Text(message) } },
                                        )
                                    }

                                    if (!withRegistration) {
                                        RepeatRoundsNotice(
                                            players = numPlayersText.trim().toIntOrNull(),
                                            rounds = numRoundsText.trim().toIntOrNull(),
                                            isTeams = isTeams,
                                        )
                                    }
                                    if (withRegistration) {
                                        DateField(
                                            label = stringResource(Res.string.registration_closes_on_optional),
                                            value = closesOn,
                                            onChange = { closesOn = it },
                                            modifier = Modifier.fillMaxWidth(),
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MtsSwitchRow(
                                                checked = isTeams,
                                                onCheckedChange = ::setTeamsChecked,
                                                enabled = !isLoading,
                                                switchFirst = true,
                                                gap = 12.dp,
                                                interactionSource = teamsToggleInteractionSource,
                                                modifier = Modifier.weight(1f, fill = false),
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.teams),
                                                    modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
                                                )
                                            }
                                            InfoTooltipIcon(
                                                stringResource(Res.string.create_teams_info),
                                            )
                                        }
                                    }

                                    TournamentOptionSwitch(
                                        label = stringResource(Res.string.best_hands),
                                        description = stringResource(Res.string.count_the_best_hands_and_show),
                                        checked = countBestHands,
                                        enabled = !isLoading,
                                        onCheckedChange = { countBestHands = it },
                                    )

                                    TournamentOptionSwitch(
                                        label = stringResource(Res.string.chicken_hands),
                                        description = stringResource(Res.string.count_the_chicken_hands_and_show),
                                        checked = countChickenHands,
                                        enabled = !isLoading,
                                        onCheckedChange = { countChickenHands = it },
                                    )

                                    if (!withRegistration) {
                                        FocusHighlightContainer(
                                            modifier = Modifier.fillMaxWidth(),
                                            interactionSource = computeToggleInteractionSource,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isHeavy = computeMode == CreateTournamentPresenter.ComputeMode.HEAVY
                                                MtsSwitchRow(
                                                    checked = isHeavy,
                                                    onCheckedChange = ::setHeavyCompute,
                                                    enabled = !isLoading,
                                                    switchFirst = true,
                                                    gap = 12.dp,
                                                    interactionSource = computeToggleInteractionSource,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                ) {
                                                    Text(
                                                        text = stringResource(Res.string.heavy_computing),
                                                        modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
                                                    )
                                                }
                                                InfoTooltipIcon(stringResource(Res.string.create_heavy_info))
                                            }
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
                                        onTimeZoneFound = {
                                            timeZoneText = it
                                            clearError(CreateField.TIME_ZONE)
                                        },
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
                                CreateTab.ORGANIZERS -> {
                                    TournamentOrganizersField(
                                        organizations = organizations,
                                        selected = organizerIds,
                                        onChange = {
                                            organizerIds = it
                                            clearError(CreateField.ORGANIZERS)
                                        },
                                        enabled = !isLoading,
                                        mainChoices = ownOrganizations,
                                        error = fieldErrors[CreateField.ORGANIZERS],
                                    )
                                    TournamentIdCardPreviewButton(
                                        shortName = shortName,
                                        primaryColor = mainOrganizer?.primaryColor ?: DEFAULT_ORGANIZATION_COLOR,
                                        year = eventStartDate.toIsoTournamentDateOrNull()?.take(4).orEmpty(),
                                        associationLogoContentType = null,
                                        associationLogoBytes = null,
                                        associationLogoUrl = mainOrganizer?.logoUrl,
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
                                            textStyle = compactDateTextStyle(),
                                            label = { RequiredLabel(stringResource(Res.string.from)) },
                                            placeholder = { Text(stringResource(Res.string.dd_mm_yyyy)) },
                                            trailingIcon = {
                                                IconButton(onClick = { showDateRangePicker = true }) {
                                                    androidx.compose.material3.Icon(
                                                        Icons.Default.DateRange,
                                                        contentDescription = stringResource(Res.string.choose_start_date),
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
                                            textStyle = compactDateTextStyle(),
                                            label = { RequiredLabel(stringResource(Res.string.date_to)) },
                                            placeholder = { Text(stringResource(Res.string.dd_mm_yyyy)) },
                                            trailingIcon = {
                                                IconButton(onClick = { showDateRangePicker = true }) {
                                                    androidx.compose.material3.Icon(
                                                        Icons.Default.DateRange,
                                                        contentDescription = stringResource(Res.string.choose_end_date),
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
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // How the tables of the rounds are drawn, with a flowchart. Centered just above Cancel and Create.
                    // With registrations open there is no draw now: the button shows in the draw dialog of Registrations.
                    if (!withRegistration) {
                        DrawExplanationButton(onClick = { showDrawExplanation = true })
                        Spacer(Modifier.height(8.dp))
                    }
                    MtsDialogButtons {
                        Button(onClick = ::startCreate, enabled = !isLoading) {
                            ButtonText(stringResource(Res.string.create))
                        }
                        if (!isLoading) {
                            AppTextButton(
                                onClick = {
                                    cancelCreation()
                                    onDismiss()
                                },
                            ) {
                                ButtonText(stringResource(Res.string.cancel))
                            }
                        }
                    }
                }
            },
        )
    }

    if (isLoading) {
        CreateProgressDialog(progress = progress, startMark = calcStartMark, onCancel = ::cancelCreation)
    }

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

    if (showDrawExplanation) {
        DrawExplanationDialog(onDismiss = { showDrawExplanation = false })
    }
}

/**
 * Under the players and rounds: with more rounds than the draw finds without repeats, says how many fit and that some
 * players will meet a rival again, at most how many times (docs/draw.md). Nothing when they fit.
 */
@Composable
internal fun RepeatRoundsNotice(players: Int?, rounds: Int?, isTeams: Boolean) {
    if (players == null || rounds == null || !TournamentPlayerCountValidator.isValid(players) || rounds <= 0) return
    val max = RepeatFreeRounds.max(players, isTeams)
    val sure = RepeatFreeRounds.sure(players, isTeams)
    if (rounds <= sure || sure < 1) return
    val times = RepeatFreeRounds.timesAtMost(players, rounds, isTeams)
    val text = if (rounds > max) {
        stringResource(Res.string.create_rounds_blocks, players, max, rounds, times)
    } else {
        stringResource(Res.string.create_rounds_maybe_blocks, players, sure, rounds, times)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MtsTheme.colors.link,
        modifier = Modifier.fillMaxWidth().testTag("createRoundsNotice"),
    )
}
