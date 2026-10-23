package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiIsoDateTimeOrDash
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.best_hands
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.chicken_hands
import mahjongtournamentsuite.adminapp.generated.resources.choose_end_date
import mahjongtournamentsuite.adminapp.generated.resources.choose_start_date
import mahjongtournamentsuite.adminapp.generated.resources.completed
import mahjongtournamentsuite.adminapp.generated.resources.count_the_best_hands_and_show
import mahjongtournamentsuite.adminapp.generated.resources.count_the_chicken_hands_and_show
import mahjongtournamentsuite.adminapp.generated.resources.created_at
import mahjongtournamentsuite.adminapp.generated.resources.created_by_name
import mahjongtournamentsuite.adminapp.generated.resources.created_by_uid
import mahjongtournamentsuite.adminapp.generated.resources.date_to
import mahjongtournamentsuite.adminapp.generated.resources.dd_mm_yyyy
import mahjongtournamentsuite.adminapp.generated.resources.delete
import mahjongtournamentsuite.adminapp.generated.resources.delete_tournament
import mahjongtournamentsuite.adminapp.generated.resources.delete_tournament_message
import mahjongtournamentsuite.adminapp.generated.resources.edit_tournament
import mahjongtournamentsuite.adminapp.generated.resources.field_error_color
import mahjongtournamentsuite.adminapp.generated.resources.field_error_country
import mahjongtournamentsuite.adminapp.generated.resources.field_error_host_city
import mahjongtournamentsuite.adminapp.generated.resources.from
import mahjongtournamentsuite.adminapp.generated.resources.id
import mahjongtournamentsuite.adminapp.generated.resources.logo
import mahjongtournamentsuite.adminapp.generated.resources.logo_choose
import mahjongtournamentsuite.adminapp.generated.resources.logo_current
import mahjongtournamentsuite.adminapp.generated.resources.logo_current_info
import mahjongtournamentsuite.adminapp.generated.resources.logo_from
import mahjongtournamentsuite.adminapp.generated.resources.logo_must_be_2mb
import mahjongtournamentsuite.adminapp.generated.resources.logo_must_be_jpeg_png
import mahjongtournamentsuite.adminapp.generated.resources.logo_none
import mahjongtournamentsuite.adminapp.generated.resources.logo_upload
import mahjongtournamentsuite.adminapp.generated.resources.logo_url
import mahjongtournamentsuite.adminapp.generated.resources.logo_will_be_removed
import mahjongtournamentsuite.adminapp.generated.resources.match_duration_invalid
import mahjongtournamentsuite.adminapp.generated.resources.match_extra_invalid
import mahjongtournamentsuite.adminapp.generated.resources.max_10_characters
import mahjongtournamentsuite.adminapp.generated.resources.no
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.remove
import mahjongtournamentsuite.adminapp.generated.resources.reuse
import mahjongtournamentsuite.adminapp.generated.resources.rounds
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.short_name
import mahjongtournamentsuite.adminapp.generated.resources.tab_branding
import mahjongtournamentsuite.adminapp.generated.resources.tab_data
import mahjongtournamentsuite.adminapp.generated.resources.tab_general
import mahjongtournamentsuite.adminapp.generated.resources.tab_schedule
import mahjongtournamentsuite.adminapp.generated.resources.tab_venue
import mahjongtournamentsuite.adminapp.generated.resources.teams
import mahjongtournamentsuite.adminapp.generated.resources.time_zone_invalid
import mahjongtournamentsuite.adminapp.generated.resources.tournament_data
import mahjongtournamentsuite.adminapp.generated.resources.tournament_name
import mahjongtournamentsuite.adminapp.generated.resources.tournament_name_is_required
import mahjongtournamentsuite.adminapp.generated.resources.tries
import mahjongtournamentsuite.adminapp.generated.resources.updated_at
import mahjongtournamentsuite.adminapp.generated.resources.use_1_to_10_characters
import mahjongtournamentsuite.adminapp.generated.resources.use_a_valid_date_on_or
import mahjongtournamentsuite.adminapp.generated.resources.use_dd_mm_yyyy
import mahjongtournamentsuite.adminapp.generated.resources.yes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private enum class TournamentEditField {
    NAME,
    SHORT_NAME,
    HOST_COUNTRY,
    HOST_CITY,
    VENUE,
    CONTACT_EMAIL,
    PRIMARY_COLOR,
    START_DATE,
    END_DATE,
}

private enum class EditTournamentTab(val title: StringResource) {
    GENERAL(Res.string.tab_general),
    VENUE(Res.string.tab_venue),
    BRANDING(Res.string.tab_branding),
    SCHEDULE(Res.string.tab_schedule),
    DATA(Res.string.tab_data),
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
    var errorMessage by remember { mutableStateOf<UiText?>(null) }
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
    var settingsVenue by remember(tournament.id) { mutableStateOf(tournament.venue) }
    var settingsCountBestHands by remember(tournament.id) { mutableStateOf(tournament.countBestHands) }
    var settingsCountChickenHands by remember(tournament.id) { mutableStateOf(tournament.countChickenHands) }
    var settingsMatchDurationText by remember(tournament.id) { mutableStateOf(tournament.matchDurationMinutes.toString()) }
    var settingsMatchExtraText by remember(tournament.id) { mutableStateOf(tournament.matchExtraMinutes.toString()) }
    // An old tournament has no time zone. The form suggests the zone of this device, so the server can check the time.
    var settingsTimeZone by remember(tournament.id) {
        mutableStateOf(tournament.timeZone ?: TimeZone.currentSystemDefault().id)
    }
    var settingsFinishHand by remember(tournament.id) { mutableStateOf(tournament.matchEndMode == MatchEndMode.FINISH_HAND) }
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
    var selectedTab by remember { mutableStateOf(EditTournamentTab.GENERAL) }
    var pendingFocusField by remember { mutableStateOf<TournamentEditField?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var settingsLogo by remember { mutableStateOf<SelectedImage?>(null) }
    var settingsLogoToCrop by remember { mutableStateOf<SelectedImage?>(null) }
    var reusedSettingsLogoTournament by remember { mutableStateOf<Tournament?>(null) }
    var showSettingsLogoLibrary by remember { mutableStateOf(false) }
    var removeSettingsLogo by remember { mutableStateOf(false) }
    var logoImageInfo by remember { mutableStateOf<String?>(null) }
    var renameError by remember { mutableStateOf<UiText?>(null) }
    var invalidEditFields by remember { mutableStateOf(emptySet<TournamentEditField>()) }
    val renameFieldFocusRequester = remember { FocusRequester() }
    val renameShortNameFocusRequester = remember { FocusRequester() }
    val settingsHostCountryFocusRequester = remember { FocusRequester() }
    val settingsHostCityFocusRequester = remember { FocusRequester() }
    val settingsMapUrlFocusRequester = remember { FocusRequester() }
    val settingsContactEmailFocusRequester = remember { FocusRequester() }
    val settingsStartDateFocusRequester = remember { FocusRequester() }
    val settingsEndDateFocusRequester = remember { FocusRequester() }
    val settingsColorFocusRequester = remember { FocusRequester() }
    val renameConfirmFocusRequester = remember { FocusRequester() }
    val renameCancelFocusRequester = remember { FocusRequester() }
    val settingsLogoPicker = rememberImagePicker(
        onImageSelected = { image ->
            invalidEditFields = emptySet()
            if (image.contentType !in setOf("image/jpeg", "image/png")) {
                renameError = UiText.of(Res.string.logo_must_be_jpeg_png)
            } else if (image.bytes.size > 2 * 1024 * 1024) {
                renameError = UiText.of(Res.string.logo_must_be_2mb)
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
            is AppResult.Failure -> errorMessage = result.error.toUiText()
        }
    }

    val editDeleteFocusRequester = remember(tournament.id) { FocusRequester() }
    val dialogScrollState = rememberScrollState()

    LaunchedEffect(tournament.id) {
        renameFieldFocusRequester.requestFocus()
    }

    LaunchedEffect(selectedTab) {
        dialogScrollState.scrollTo(0)
    }

    // A field on another tab is not composed yet. Focus it after the tab changes.
    LaunchedEffect(pendingFocusField, selectedTab) {
        val field = pendingFocusField ?: return@LaunchedEffect
        when (field) {
            TournamentEditField.NAME -> renameFieldFocusRequester.requestFocus()
            TournamentEditField.SHORT_NAME -> renameShortNameFocusRequester.requestFocus()
            TournamentEditField.HOST_COUNTRY -> settingsHostCountryFocusRequester.requestFocus()
            TournamentEditField.HOST_CITY -> settingsHostCityFocusRequester.requestFocus()
            TournamentEditField.VENUE -> settingsMapUrlFocusRequester.requestFocus()
            TournamentEditField.CONTACT_EMAIL -> settingsContactEmailFocusRequester.requestFocus()
            TournamentEditField.START_DATE -> settingsStartDateFocusRequester.requestFocus()
            TournamentEditField.END_DATE -> settingsEndDateFocusRequester.requestFocus()
            TournamentEditField.PRIMARY_COLOR -> settingsColorFocusRequester.requestFocus()
        }
        pendingFocusField = null
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup().widthIn(max = 720.dp).fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = {
            if (!isLoading) {
                showDateRangePicker = false
                showColorPickerDialog = false
                onDismiss()
            }
        },
        title = { Text(stringResource(Res.string.edit_tournament)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    edgePadding = 0.dp,
                ) {
                    EditTournamentTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            text = { Text(stringResource(tab.title)) },
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
                        when (selectedTab) {
                            EditTournamentTab.GENERAL -> {
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
                                    label = { RequiredLabel(stringResource(Res.string.tournament_name)) },
                                    singleLine = true,
                                    isError = TournamentEditField.NAME in invalidEditFields,
                                    supportingText = if (TournamentEditField.NAME in invalidEditFields) {
                                        { Text(stringResource(Res.string.tournament_name_is_required)) }
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
                                    label = { RequiredLabel(stringResource(Res.string.short_name)) },
                                    placeholder = { Text(stringResource(Res.string.max_10_characters)) },
                                    singleLine = true,
                                    isError = TournamentEditField.SHORT_NAME in invalidEditFields,
                                    supportingText = if (TournamentEditField.SHORT_NAME in invalidEditFields) {
                                        { Text(stringResource(Res.string.use_1_to_10_characters)) }
                                    } else {
                                        null
                                    },
                                )

                                MatchTimeFields(
                                    durationText = settingsMatchDurationText,
                                    onDurationChange = { settingsMatchDurationText = it },
                                    finishHand = settingsFinishHand,
                                    onFinishHandChange = { settingsFinishHand = it },
                                    extraMinutesText = settingsMatchExtraText,
                                    onExtraMinutesChange = { settingsMatchExtraText = it },
                                    enabled = !isLoading,
                                )

                                TournamentOptionSwitch(
                                    label = stringResource(Res.string.best_hands),
                                    description = stringResource(Res.string.count_the_best_hands_and_show),
                                    checked = settingsCountBestHands,
                                    enabled = !isLoading,
                                    onCheckedChange = { settingsCountBestHands = it },
                                )
                                TournamentOptionSwitch(
                                    label = stringResource(Res.string.chicken_hands),
                                    description = stringResource(Res.string.count_the_chicken_hands_and_show),
                                    checked = settingsCountChickenHands,
                                    enabled = !isLoading,
                                    onCheckedChange = { settingsCountChickenHands = it },
                                )
                            }
                            EditTournamentTab.VENUE -> {
                                TournamentPlaceFields(
                                    venue = settingsVenue,
                                    onVenueChange = {
                                        settingsVenue = it
                                        invalidEditFields = invalidEditFields -
                                            setOf(TournamentEditField.VENUE, TournamentEditField.CONTACT_EMAIL)
                                        renameError = null
                                    },
                                    hostCountry = settingsHostCountry,
                                    onHostCountryChange = {
                                        settingsHostCountry = it
                                        invalidEditFields = invalidEditFields - TournamentEditField.HOST_COUNTRY
                                        renameError = null
                                    },
                                    hostCity = settingsHostCity,
                                    onHostCityChange = {
                                        settingsHostCity = it
                                        invalidEditFields = invalidEditFields - TournamentEditField.HOST_CITY
                                        renameError = null
                                    },
                                    countries = countries,
                                    resolveLink = presenter::resolveVenueLink,
                                    enabled = !isLoading,
                                    countryError = if (TournamentEditField.HOST_COUNTRY in invalidEditFields) {
                                        stringResource(Res.string.field_error_country)
                                    } else {
                                        null
                                    },
                                    cityError = if (TournamentEditField.HOST_CITY in invalidEditFields) {
                                        stringResource(Res.string.field_error_host_city)
                                    } else {
                                        null
                                    },
                                    countryFocusRequester = settingsHostCountryFocusRequester,
                                    cityFocusRequester = settingsHostCityFocusRequester,
                                    venueError = if (TournamentEditField.VENUE in invalidEditFields) {
                                        venueRequiredError(settingsVenue)?.asString()
                                    } else {
                                        null
                                    },
                                    contactEmailError = if (TournamentEditField.CONTACT_EMAIL in invalidEditFields) {
                                        contactEmailRequiredError(settingsVenue.contactEmail)?.asString()
                                    } else {
                                        null
                                    },
                                    mapUrlFocusRequester = settingsMapUrlFocusRequester,
                                    contactEmailFocusRequester = settingsContactEmailFocusRequester,
                                )
                                VenueTimeZoneField(
                                    timeZoneText = settingsTimeZone,
                                    onTimeZoneChange = { settingsTimeZone = it },
                                    enabled = !isLoading,
                                )
                            }
                            EditTournamentTab.BRANDING -> {
                                TournamentColorField(
                                    value = settingsPrimaryColor,
                                    enabled = !isLoading,
                                    isError = TournamentEditField.PRIMARY_COLOR in invalidEditFields,
                                    errorMessage = if (TournamentEditField.PRIMARY_COLOR in invalidEditFields) {
                                        stringResource(Res.string.field_error_color)
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

                                Text(stringResource(Res.string.logo), style = MaterialTheme.typography.titleSmall)
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
                                            removeSettingsLogo -> stringResource(Res.string.logo_will_be_removed)
                                            settingsLogo != null -> buildString {
                                                append(settingsLogo?.fileName.orEmpty())
                                                append(" · ")
                                                append(formatByteSize(settingsLogo?.bytes?.size?.toLong() ?: 0L))
                                                logoImageInfo?.let { append(" · ").append(it) }
                                            }
                                            reusedSettingsLogoTournament != null ->
                                                stringResource(Res.string.logo_from, reusedSettingsLogoTournament?.name.orEmpty())
                                            tournament.associationLogoUrl != null ->
                                                logoImageInfo?.let { stringResource(Res.string.logo_current_info, it) } ?: stringResource(Res.string.logo_current)
                                            else -> stringResource(Res.string.logo_none)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(onClick = settingsLogoPicker::launch) {
                                            Text(stringResource(if (logoModel == null) Res.string.logo_choose else Res.string.logo_upload))
                                        }
                                        if (tournaments.any {
                                                it.id != tournament.id && !it.associationLogoUrl.isNullOrBlank()
                                            }) {
                                            Button(onClick = { showSettingsLogoLibrary = true }) {
                                                Text(stringResource(Res.string.reuse))
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
                                            ) { Text(stringResource(Res.string.remove)) }
                                        }
                                    }
                                }
                            }

                            TournamentReadOnlyField(
                                label = stringResource(Res.string.logo_url),
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
                            }
                            EditTournamentTab.SCHEDULE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = settingsEventStartDate,
                                    onValueChange = {
                                        settingsEventStartDate = it.toDateInput()
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
                                    label = { RequiredLabel(stringResource(Res.string.from)) },
                                    placeholder = { Text(stringResource(Res.string.dd_mm_yyyy)) },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = stringResource(Res.string.choose_start_date),
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    isError = TournamentEditField.START_DATE in invalidEditFields,
                                    supportingText = if (TournamentEditField.START_DATE in invalidEditFields) {
                                        { Text(stringResource(Res.string.use_dd_mm_yyyy)) }
                                    } else {
                                        null
                                    },
                                )
                                OutlinedTextField(
                                    value = settingsEventEndDate,
                                    onValueChange = {
                                        val newValue = it.toDateInput()
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
                                    label = { RequiredLabel(stringResource(Res.string.date_to)) },
                                    placeholder = { Text(stringResource(Res.string.dd_mm_yyyy)) },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = stringResource(Res.string.choose_end_date),
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    isError = TournamentEditField.END_DATE in invalidEditFields,
                                    supportingText = if (TournamentEditField.END_DATE in invalidEditFields) {
                                        { Text(stringResource(Res.string.use_a_valid_date_on_or)) }
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
                            }
                            EditTournamentTab.DATA -> {
                                Text(stringResource(Res.string.tournament_data), style = MaterialTheme.typography.titleSmall)
                                TournamentReadOnlyField(label = stringResource(Res.string.id), value = tournament.id)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TournamentReadOnlyField(
                                        label = stringResource(Res.string.teams),
                                        value = stringResource(if (tournament.isTeams) Res.string.yes else Res.string.no),
                                        modifier = Modifier.weight(1f),
                                    )
                                    TournamentReadOnlyField(
                                        label = stringResource(Res.string.completed),
                                        value = stringResource(if (tournament.isCompleted) Res.string.yes else Res.string.no),
                                        modifier = Modifier.weight(1f),
                                    )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TournamentReadOnlyField(
                                    label = stringResource(Res.string.players),
                                    value = tournament.numPlayers.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                TournamentReadOnlyField(
                                    label = stringResource(Res.string.rounds),
                                    value = tournament.numRounds.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                TournamentReadOnlyField(
                                    label = stringResource(Res.string.tries),
                                    value = tournament.numTries.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            TournamentReadOnlyField(
                                label = stringResource(Res.string.created_by_uid),
                                value = tournament.createdByUid ?: "—",
                            )
                            TournamentReadOnlyField(
                                label = stringResource(Res.string.created_by_name),
                                value = tournament.createdByName ?: "—",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TournamentReadOnlyField(
                                    label = stringResource(Res.string.created_at),
                                    value = tournament.createdAt.toUiIsoDateTimeOrDash(),
                                    modifier = Modifier.weight(1f),
                                )
                                TournamentReadOnlyField(
                                    label = stringResource(Res.string.updated_at),
                                    value = tournament.updatedAt.toUiIsoDateTimeOrDash(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            }
                        }
                }
                }
                        renameError?.let { message ->
                            Text(
                                text = message.asString(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
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
                        Text(stringResource(Res.string.delete))
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
                    Text(stringResource(Res.string.cancel))
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
                            if (venueRequiredError(settingsVenue) != null) {
                                add(TournamentEditField.VENUE)
                            }
                            if (contactEmailRequiredError(settingsVenue.contactEmail) != null) {
                                add(TournamentEditField.CONTACT_EMAIL)
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
                            val firstInvalid = newInvalidFields.first()
                            selectedTab = when (firstInvalid) {
                                TournamentEditField.PRIMARY_COLOR -> EditTournamentTab.BRANDING
                                TournamentEditField.HOST_COUNTRY,
                                TournamentEditField.HOST_CITY,
                                TournamentEditField.VENUE,
                                TournamentEditField.CONTACT_EMAIL,
                                -> EditTournamentTab.VENUE
                                TournamentEditField.START_DATE,
                                TournamentEditField.END_DATE,
                                -> EditTournamentTab.SCHEDULE
                                else -> EditTournamentTab.GENERAL
                            }
                            pendingFocusField = firstInvalid
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
                            selectedTab = EditTournamentTab.SCHEDULE
                            renameError = scheduleError
                            return@Button
                        }
                        val validMatchDuration = matchDurationOrNull(settingsMatchDurationText)
                        if (validMatchDuration == null) {
                            selectedTab = EditTournamentTab.GENERAL
                            renameError = UiText.of(Res.string.match_duration_invalid, MAX_MATCH_DURATION_MINUTES)
                            return@Button
                        }
                        if (!isTimeZoneIdShape(settingsTimeZone)) {
                            selectedTab = EditTournamentTab.VENUE
                            renameError = UiText.of(Res.string.time_zone_invalid)
                            return@Button
                        }
                        val validMatchExtra = matchExtraMinutesOrNull(settingsMatchExtraText)
                        if (settingsFinishHand && validMatchExtra == null) {
                            selectedTab = EditTournamentTab.GENERAL
                            renameError = UiText.of(Res.string.match_extra_invalid, MAX_MATCH_EXTRA_MINUTES)
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
                                venue = settingsVenue,
                                associationLogoContentType = settingsLogo?.contentType,
                                associationLogoBytes = settingsLogo?.bytes,
                                associationLogoSourceTournamentId = reusedSettingsLogoTournament?.id,
                                removeAssociationLogo = removeSettingsLogo,
                                roundSchedules = settingsRoundScheduleRows.toTournamentRoundSchedules(),
                                agendaItems = settingsAgendaRows.toTournamentAgendaItems(),
                                countBestHands = settingsCountBestHands,
                                countChickenHands = settingsCountChickenHands,
                                matchDurationMinutes = validMatchDuration,
                                matchEndMode = if (settingsFinishHand) MatchEndMode.FINISH_HAND else MatchEndMode.STOP,
                                matchExtraMinutes = validMatchExtra ?: tournament.matchExtraMinutes,
                                timeZone = settingsTimeZone.trim(),
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
                                    errorMessage = result.error.toUiText()
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
                    Text(stringResource(Res.string.save))
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
            title = { Text(stringResource(Res.string.delete_tournament)) },
            text = { Text(stringResource(Res.string.delete_tournament_message, deleting.name, deleting.id)) },
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
                                    errorMessage = result.error.toUiText()
                                }
                            }

                            isLoading = false
                        }
                    },
                ) {
                    Text(stringResource(Res.string.delete))
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
                    Text(stringResource(Res.string.cancel))
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
