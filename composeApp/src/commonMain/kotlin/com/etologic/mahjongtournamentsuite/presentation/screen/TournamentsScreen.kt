package com.etologic.mahjongtournamentsuite.presentation.screen

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
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppVersionLabel
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformHorizontalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformVerticalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorField
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDateRangePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentIdCardPreviewButton
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoCropDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoLibraryDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoPreview
import com.etologic.mahjongtournamentsuite.presentation.components.adjustedEndDate
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.emaCountryFlagCode
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.formatByteSize
import com.etologic.mahjongtournamentsuite.presentation.components.textFieldFocusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.toDisplayTournamentDate
import com.etologic.mahjongtournamentsuite.presentation.components.toIsoTournamentDateOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentColorOrNull
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
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
fun TournamentsScreen(
    navController: NavHostController,
) {
    val presenter = koinInject<TournamentsPresenter>()
    val versionPresenter = koinInject<VersionPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()

    val profile by store.profile.collectAsState()
    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }

    var isRefreshing by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var backendVersion by remember { mutableStateOf<String?>(null) }
    var deleteDialogTournament by remember { mutableStateOf<Tournament?>(null) }
    var renameDialogTournament by remember { mutableStateOf<Tournament?>(null) }
    var showCreateTournamentDialog by remember { mutableStateOf(false) }
    var restoreNewTournamentFocus by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(TextFieldValue()) }
    var settingsShortName by remember { mutableStateOf("") }
    var settingsPrimaryColor by remember { mutableStateOf("#02B16B") }
    var settingsEventStartDate by remember { mutableStateOf("") }
    var settingsEventEndDate by remember { mutableStateOf("") }
    var settingsHostCountry by remember { mutableStateOf("") }
    var settingsHostCity by remember { mutableStateOf("") }
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
    var tournamentFocusApplied by remember { mutableStateOf(false) }
    var lastFocusedTournamentId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedControl by rememberSaveable { mutableStateOf<String?>(null) }
    val playerBaseFocusRequester = remember { FocusRequester() }
    val usersFocusRequester = remember { FocusRequester() }
    val newTournamentFocusRequester = remember { FocusRequester() }
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

    fun refresh(force: Boolean = false) {
        coroutineScope.launch {
            isRefreshing = true
            errorMessage = null

            when (val profileResult = presenter.loadProfile(force)) {
                is AppResult.Success -> store.profile.value = profileResult.value
                is AppResult.Failure -> errorMessage = profileResult.error.toUiMessage()
            }

            when (val adminResult = presenter.loadAdminStatus(force)) {
                is AppResult.Success -> store.adminStatus.value = adminResult.value
                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    adminResult.error.toUiMessage()
            }

            when (val tournamentsResult = presenter.loadTournaments(force)) {
                is AppResult.Success -> {
                    store.upsertTournaments(tournamentsResult.value)
                }

                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    tournamentsResult.error.toUiMessage()
            }

            when (val countriesResult = presenter.loadCountries(force)) {
                is AppResult.Success -> countries = countriesResult.value
                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    countriesResult.error.toUiMessage()
            }

            isRefreshing = false
        }
    }

    fun openTournament(tournament: Tournament) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val result = presenter.loadPlayers(tournament.id)) {
                is AppResult.Success -> {
                    store.upsertPlayers(tournament.id, result.value)
                    if (result.value.any { !it.isAssigned }) {
                        navController.navigate(
                            TournamentRoute(
                                tournamentId = tournament.id,
                                tournamentName = tournament.name,
                            ),
                        )
                        navController.navigate(PlayersRoute(tournamentId = tournament.id))
                    } else {
                        navController.navigate(
                            TournamentRoute(
                                tournamentId = tournament.id,
                                tournamentName = tournament.name,
                            ),
                        )
                    }
                }

                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }

            isLoading = false
        }
    }

    LaunchedEffect(presenter) {
        refresh()
    }

    LaunchedEffect(versionPresenter) {
        backendVersion = versionPresenter.loadBackendVersion()
    }

    val roleLabel = adminStatus?.let {
        when (it.role) {
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.ADMIN -> "Admin"
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.EDITOR -> "Editor"
        }
    }
    val canConfigureTournaments = adminStatus?.canConfigureTournaments == true
    val canDeleteTournaments = adminStatus?.canDeleteTournaments == true
    val canEditTournament = canConfigureTournaments || canDeleteTournaments
    val tournamentFocusRequesters = remember(tournaments.map { it.id }) {
        tournaments.map { FocusRequester() }
    }

    LaunchedEffect(isRefreshing, tournaments.map { it.id }) {
        if (!tournamentFocusApplied && !isRefreshing) {
            withFrameNanos { }
            when (lastFocusedControl) {
                "player-base" -> playerBaseFocusRequester.requestFocus()
                "users" -> if (adminStatus?.canManageUsers == true) {
                    usersFocusRequester.requestFocus()
                } else {
                    newTournamentFocusRequester.requestFocus()
                }
                "new-tournament" -> if (adminStatus?.canCreateTournaments == true) {
                    newTournamentFocusRequester.requestFocus()
                } else {
                    usersFocusRequester.requestFocus()
                }
                else -> {
                    if (tournamentFocusRequesters.isNotEmpty()) {
                        val index = tournaments.indexOfFirst { it.id == lastFocusedTournamentId }.coerceAtLeast(0)
                        tournamentFocusRequesters[index].requestFocus()
                    } else {
                        usersFocusRequester.requestFocus()
                    }
                }
            }
            tournamentFocusApplied = true
        }
    }

    LaunchedEffect(showCreateTournamentDialog, restoreNewTournamentFocus) {
        if (!showCreateTournamentDialog && restoreNewTournamentFocus) {
            restoreNewTournamentFocus = false
            newTournamentFocusRequester.requestFocus()
        }
    }

    if (showCreateTournamentDialog) {
        CreateTournamentDialog(
            onDismiss = {
                showCreateTournamentDialog = false
                restoreNewTournamentFocus = true
            },
            onCreated = { tournament ->
                showCreateTournamentDialog = false
                navController.navigate(
                    TournamentRoute(
                        tournamentId = tournament.id,
                        tournamentName = tournament.name,
                    ),
                )
                navController.navigate(PlayersRoute(tournamentId = tournament.id))
            },
        )
    }

    renameDialogTournament?.let { tournament ->
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
                    renameDialogTournament = null
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
                            renameDialogTournament = null
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
                                )) {
                                    is AppResult.Success -> {
                                        showDateRangePicker = false
                                        showColorPickerDialog = false
                                        renameDialogTournament = null
                                        store.updateTournament(result.value)
                                        refresh()
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
    }

    deleteDialogTournament?.let { tournament ->
        val deleteFocusRequester = remember(tournament.id) { FocusRequester() }
        val cancelFocusRequester = remember(tournament.id) { FocusRequester() }

        LaunchedEffect(tournament.id) {
            deleteFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!isLoading) deleteDialogTournament = null },
            title = { Text("Delete tournament") },
            text = { Text("This will permanently delete \"${tournament.name}\" (ID: ${tournament.id}).") },
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

                            when (val result = presenter.deleteTournament(tournament.id)) {
                                is AppResult.Success -> {
                                    deleteDialogTournament = null
                                    renameDialogTournament = null
                                    store.removeTournament(tournament.id)
                                    refresh()
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

    AppScaffold(
        title = "Tournaments",
        isLoading = isRefreshing || isLoading,
        navigationIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoutButton(coroutineScope, presenter, navController)
                ProfileInfo(profile, roleLabel)
            }
        },
        leadingActions = {
            AppTopBarLeadingActions(
                showThemeToggle = true,
                onAppUsers = if (adminStatus?.canManageUsers == true) {
                    {
                        lastFocusedControl = "users"
                        navController.navigate(UsersRoute)
                    }
                } else {
                    null
                },
                onTimer = { openTimer(navController) },
                usersFocusRequester = usersFocusRequester,
            )
        },
        actions = {
            AppTopBarActions(
                onPlayerBase = {
                    lastFocusedControl = "player-base"
                    navController.navigate(PlayerBaseRoute)
                },
                onNewTournament = if (adminStatus?.canCreateTournaments == true) {
                    {
                        lastFocusedControl = "new-tournament"
                        showCreateTournamentDialog = true
                    }
                } else {
                    null
                },
                playerBaseFocusRequester = playerBaseFocusRequester,
                newTournamentFocusRequester = newTournamentFocusRequester,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(horizontal = 24.dp, vertical = 24.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            SectionCard(
                content = {
                    when {
                        !isRefreshing && tournaments.isEmpty() -> {
                            Text(
                                text = "No tournaments yet.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        else -> {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val scrollState = rememberScrollState()
                                val listState = rememberLazyListState()
                                val cellMinWidth = 56.dp
                                val numColumns = 10
                                val actionCellMinWidth = 56.dp
                                val columnsMinWidth = (cellMinWidth * (numColumns - 1)) + actionCellMinWidth
                                val columnSpacing = 12.dp
                                val baseMinWidth = columnsMinWidth + (columnSpacing * (numColumns - 1))
                                val needsHorizontalScroll = baseMinWidth > maxWidth

                                val tableContent: @Composable (Modifier) -> Unit = { modifier ->
                                    LazyColumn(
                                        state = listState,
                                        modifier = modifier,
                                    ) {
                                        item {
                                            TournamentTableHeader(
                                                cellMinWidth = cellMinWidth,
                                                actionCellMinWidth = actionCellMinWidth,
                                            )
                                            DataTableDivider()
                                        }
                                        itemsIndexed(tournaments, key = { _, tournament -> tournament.id }) { index, tournament ->
                                            TournamentTableRow(
                                                modifier = Modifier
                                                    .focusRequester(tournamentFocusRequesters[index])
                                                    .onFocusChanged {
                                                        if (it.isFocused) lastFocusedTournamentId = tournament.id
                                                        if (it.isFocused) lastFocusedControl = "tournament"
                                                    },
                                                tournament = tournament,
                                                enabled = !isLoading,
                                                cellMinWidth = cellMinWidth,
                                                actionCellMinWidth = actionCellMinWidth,
                                                showEdit = canEditTournament,
                                                onClick = {
                                                    lastFocusedTournamentId = tournament.id
                                                    lastFocusedControl = "tournament"
                                                    openTournament(tournament)
                                                },
                                                onEdit = {
                                                    lastFocusedTournamentId = tournament.id
                                                    lastFocusedControl = "tournament"
                                                    renameValue = TextFieldValue(
                                                        text = tournament.name,
                                                        selection = TextRange(tournament.name.length),
                                                    )
                                                    renameError = null
                                                    invalidEditFields = emptySet()
                                                    settingsShortName = tournament.shortName.ifBlank {
                                                        tournament.name.take(10)
                                                    }
                                                    settingsPrimaryColor = tournament.primaryColor
                                                    settingsEventStartDate = tournament.eventStartDate.toDisplayTournamentDate()
                                                    settingsEventEndDate = tournament.eventEndDate.toDisplayTournamentDate()
                                                    settingsHostCountry = tournament.hostCountry
                                                    settingsHostCity = tournament.hostCity
                                                    settingsLogo = null
                                                    reusedSettingsLogoTournament = null
                                                    removeSettingsLogo = false
                                                    logoImageInfo = null
                                                    showDateRangePicker = false
                                                    showColorPickerDialog = false
                                                    showSettingsLogoLibrary = false
                                                    renameDialogTournament = tournament
                                                },
                                            )
                                            DataTableDivider()
                                        }
                                    }
                                }

                                if (needsHorizontalScroll) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(scrollState)
                                                .pointerInput(scrollState) {
                                                    detectHorizontalDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        scrollState.dispatchRawDelta(-dragAmount)
                                                    }
                                                },
                                        ) {
                                            tableContent(Modifier.width(baseMinWidth))
                                        }

                                        PlatformVerticalScrollbar(
                                            listState = listState,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(12.dp),
                                        )

                                        PlatformHorizontalScrollbar(
                                            scrollState = scrollState,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .height(12.dp),
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        tableContent(Modifier.fillMaxWidth())

                                        PlatformVerticalScrollbar(
                                            listState = listState,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )

            errorMessage?.let { message ->
                AppErrorDialog(
                    message = message,
                    onDismiss = { errorMessage = null },
                )
            }
            }

            AppVersionLabel(
                backendVersion = backendVersion,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
fun ProfileInfo(
    profile: UserProfile?,
    roleLabel: String?
) {
    profile?.let { user ->
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = user.alias.trim().ifBlank { user.toUiName() },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            roleLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LogoutButton(
    coroutineScope: CoroutineScope,
    presenter: TournamentsPresenter,
    navController: NavHostController
) {
    AppTopBarButton(
        text = "Logout",
        icon = Icons.AutoMirrored.Filled.Logout,
        onClick = {
            coroutineScope.launch {
                presenter.signOut()
                navController.navigate(SignInRoute) {
                    popUpTo(TournamentsRoute) { inclusive = true }
                }
            }
        },
    )
}

@Composable
private fun TournamentTableHeader(
    cellMinWidth: Dp,
    actionCellMinWidth: Dp,
) {
    DataTableHeaderRow {
        HeaderCell(text = "Logo", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "Color", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "Name", minWidth = cellMinWidth, weight = 2.0f)
        HeaderCell(text = "Short name", minWidth = cellMinWidth, weight = 1.0f)
        HeaderCell(text = "Country", minWidth = cellMinWidth, weight = .7f, textAlign = TextAlign.Center)
        HeaderCell(text = "City", minWidth = cellMinWidth, weight = 1.0f)
        HeaderCell(text = "MERS", minWidth = cellMinWidth, weight = .6f, textAlign = TextAlign.Center)
        HeaderCell(text = "Teams", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "Players", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "Rounds", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "From", minWidth = cellMinWidth, weight = 1.0f, textAlign = TextAlign.Center)
        HeaderCell(text = "To", minWidth = cellMinWidth, weight = 1.0f, textAlign = TextAlign.Center)
        Text(
            text = "",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(actionCellMinWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TournamentTableRow(
    modifier: Modifier = Modifier,
    tournament: Tournament,
    enabled: Boolean,
    cellMinWidth: Dp,
    actionCellMinWidth: Dp,
    showEdit: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    DataTableRow(
        modifier = modifier,
        onClick = if (enabled) onClick else null,
    ) {
        TournamentLogoCell(
            logoUrl = tournament.associationLogoUrl,
            tournamentName = tournament.name,
            minWidth = cellMinWidth,
        )
        TournamentColorCell(
            colorValue = tournament.primaryColor,
            minWidth = cellMinWidth,
        )
        BodyCell(text = tournament.name, minWidth = cellMinWidth, weight = 2.0f)
        BodyCell(
            text = tournament.shortName.ifBlank { "—" },
            minWidth = cellMinWidth,
            weight = 1.0f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TournamentCountryCell(
            countryCode = tournament.hostCountry,
            minWidth = cellMinWidth,
        )
        BodyCell(text = tournament.hostCity.ifBlank { "—" }, minWidth = cellMinWidth, weight = 1.0f)
        BodyCell(
            text = tournament.mers.toString().removeSuffix(".0"),
            minWidth = cellMinWidth,
            weight = .6f,
            textAlign = TextAlign.Center,
        )
        TeamModeCell(
            isTeams = tournament.isTeams,
            minWidth = cellMinWidth,
            weight = .5f,
        )
        BodyCell(
            text = tournament.numPlayers.toString(),
            minWidth = cellMinWidth,
            weight = .5f,
            textAlign = TextAlign.Center,
        )
        BodyCell(
            text = tournament.numRounds.toString(),
            minWidth = cellMinWidth,
            weight = .5f,
            textAlign = TextAlign.Center,
        )
        BodyCell(
            text = tournament.eventStartDate.toDisplayTournamentDate().ifBlank { "—" },
            minWidth = cellMinWidth,
            weight = 1.0f,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.eventEndDate.toDisplayTournamentDate().ifBlank { "—" },
            minWidth = cellMinWidth,
            weight = 1.0f,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier.width(actionCellMinWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (showEdit) {
                IconButton(
                    enabled = enabled,
                    onClick = onEdit,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ${tournament.name}",
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TournamentCountryCell(
    countryCode: String,
    minWidth: Dp,
) {
    Box(
        modifier = Modifier
            .weight(.7f)
            .widthIn(min = minWidth),
        contentAlignment = Alignment.Center,
    ) {
        CountryFlag(
            code = emaCountryFlagCode(countryCode).orEmpty(),
            contentDescription = countryCode.ifBlank { "No host country" },
        )
    }
}

@Composable
private fun RowScope.TournamentLogoCell(
    logoUrl: String?,
    tournamentName: String,
    minWidth: Dp,
) {
    Box(
        modifier = Modifier
            .weight(.5f)
            .widthIn(min = minWidth)
            .height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = MaterialTheme.shapes.small
        if (logoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .semantics { contentDescription = "No logo for $tournamentName" },
            )
        } else {
            AsyncImage(
                model = logoUrl,
                contentDescription = "Logo for $tournamentName",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(shape),
            )
        }
    }
}

@Composable
private fun RowScope.TournamentColorCell(
    colorValue: String,
    minWidth: Dp,
) {
    Box(
        modifier = Modifier
            .weight(.5f)
            .widthIn(min = minWidth)
            .height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = colorValue.toTournamentColorOrNull()
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.small,
                )
                .semantics { contentDescription = "Tournament color $colorValue" },
        )
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    minWidth: Dp,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .weight(weight)
            .widthIn(min = minWidth),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

@Composable
private fun RowScope.BodyCell(
    text: String,
    minWidth: Dp,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = Modifier
            .weight(weight)
            .widthIn(min = minWidth),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

@Composable
private fun RowScope.TeamModeCell(
    isTeams: Boolean,
    minWidth: Dp,
    weight: Float,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .widthIn(min = minWidth),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isTeams) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = if (isTeams) "Team tournament" else "Individual tournament",
            tint = if (isTeams) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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

private fun UserProfile.toUiName(): String {
    val rawName = email
        .substringBefore("@")
        .replace('.', ' ')
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercaseChar().toString() }
        }

    return rawName.ifBlank { email }
}
