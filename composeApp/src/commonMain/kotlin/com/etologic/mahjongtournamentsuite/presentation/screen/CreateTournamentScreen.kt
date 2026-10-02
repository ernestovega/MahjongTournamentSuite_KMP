package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
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
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentAgendaItems
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentRoundSchedules
import com.etologic.mahjongtournamentsuite.presentation.components.tournamentScheduleEditorError
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
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

    var name by remember { mutableStateOf("") }
    var shortName by remember { mutableStateOf("") }
    var primaryColor by remember { mutableStateOf("#02B16B") }
    var hostCountry by remember { mutableStateOf("") }
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var hostCity by remember { mutableStateOf("") }
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
    var computeMode by remember { mutableStateOf(CreateTournamentPresenter.ComputeMode.LIGHT) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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

        if (trimmedName.isBlank()) {
            errorMessage = "* Name is required."
            nameFocusRequester.requestFocus()
            return
        }
        if (trimmedShortName.isBlank() || trimmedShortName.length > 10) {
            errorMessage = "* Short name must contain 1 to 10 characters."
            shortNameFocusRequester.requestFocus()
            return
        }
        if (!Regex("^#[0-9A-F]{6}$").matches(normalizedPrimaryColor)) {
            errorMessage = "* Primary color must use #RRGGBB format."
            primaryColorFocusRequester.requestFocus()
            return
        }
        if (!Regex("^[A-Z]{2,3}$").matches(normalizedHostCountry)) {
            errorMessage = "* Select a host country."
            hostCountryFocusRequester.requestFocus()
            return
        }
        if (trimmedHostCity.isBlank()) {
            errorMessage = "* Host city is required."
            hostCityFocusRequester.requestFocus()
            return
        }
        if (
            trimmedStartDate == null ||
            trimmedEndDate == null ||
            !TournamentDateRangeValidator.isValidRange(trimmedStartDate, trimmedEndDate)
        ) {
            errorMessage = "* Enter valid DD/MM/YYYY dates. The end date must not be before the start date."
            return
        }
        if (numPlayers == null || numPlayers <= 0 || numPlayers % 4 != 0) {
            errorMessage = "* Must be a positive multiple of 4."
            playersFocusRequester.requestFocus()
            return
        }
        if (numRounds == null || numRounds <= 0) {
            errorMessage = "* Must be a positive integer."
            roundsFocusRequester.requestFocus()
            return
        }
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
                    isTeams = isTeams,
                    numPlayers = numPlayers,
                    numRounds = numRounds,
                    roundSchedules = roundScheduleRows.toTournamentRoundSchedules(),
                    agendaItems = agendaRows.toTournamentAgendaItems(),
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
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = {
            if (!isLoading) {
                cancelCreation()
                onDismiss()
            }
        },
        title = { Text("New tournament") },
        text = {
            ScrollableColumnWithScrollbar(
                state = rememberScrollState(),
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { shortNameFocusRequester.requestFocus() }),
                    )
                    OutlinedTextField(
                        value = shortName,
                        onValueChange = { shortName = it.take(10) },
                        label = { Text("Short name") },
                        placeholder = { Text("Max. 10 characters") },
                        singleLine = true,
                        enabled = !isLoading,
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
                            onValueChange = { numPlayersText = it },
                            label = { Text("Players") },
                            placeholder = { Text("Multiple of 4") },
                            modifier = Modifier.weight(1f).focusRequester(playersFocusRequester),
                            singleLine = true,
                            enabled = !isLoading,
                        )
                        OutlinedTextField(
                            value = numRoundsText,
                            onValueChange = { value ->
                                numRoundsText = value
                                roundScheduleRows = synchronizeRoundScheduleRows(
                                    rows = roundScheduleRows,
                                    roundCount = value.trim().toIntOrNull() ?: 0,
                                )
                            },
                            label = { Text("Rounds") },
                            modifier = Modifier.weight(1f).focusRequester(roundsFocusRequester),
                            singleLine = true,
                            enabled = !isLoading,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EmaCountryDropdown(
                            selectedCode = hostCountry,
                            onCountrySelected = { hostCountry = it },
                            countries = countries,
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            focusRequester = hostCountryFocusRequester,
                            label = "Country",
                        )
                        OutlinedTextField(
                            value = hostCity,
                            onValueChange = { hostCity = it },
                            label = { Text("City") },
                            singleLine = true,
                            enabled = !isLoading,
                            modifier = Modifier.weight(2f).focusRequester(hostCityFocusRequester),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = eventStartDate,
                            onValueChange = {
                                eventStartDate = it.take(10)
                                eventEndDate = adjustedEndDate(eventStartDate, eventEndDate)
                            },
                            label = { Text("From") },
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
                        )
                        OutlinedTextField(
                            value = eventEndDate,
                            onValueChange = {
                                val newValue = it.take(10)
                                eventEndDate = when {
                                    newValue.isEmpty() && eventStartDate.toIsoTournamentDateOrNull() != null -> eventStartDate
                                    else -> adjustedEndDate(eventStartDate, newValue)
                                }
                            },
                            label = { Text("To") },
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
                    TournamentColorField(
                        value = primaryColor,
                        enabled = !isLoading,
                        isError = errorMessage?.contains("color", ignoreCase = true) == true,
                        onValueChange = { primaryColor = it },
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
                            SwitchInfoTooltip(
                                "Group players into teams of four. Team members will not play together.",
                            )
                        }
                    }

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
                            SwitchInfoTooltip("Use more CPU to calculate the schedule faster.")
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SwitchInfoTooltip(description: String) {
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = tooltipState,
    ) {
        IconButton(
            onClick = {
                coroutineScope.launch { tooltipState.show() }
            },
            modifier = Modifier.semantics {
                contentDescription = "Show information: $description"
            },
        ) {
            Text(
                text = "ⓘ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatWithDots(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val reversed = s.reversed().chunked(3).joinToString(".")
    return reversed.reversed()
}

private fun formatAvgPerTry(
    tried: Long,
    startMark: TimeMark?,
): String? {
    if (startMark == null) return null
    if (tried <= 0) return null

    val elapsedNs = startMark.elapsedNow().inWholeNanoseconds
    if (elapsedNs <= 0) return null

    val nsPerTry = elapsedNs.toDouble() / tried.toDouble()

    return when {
        nsPerTry >= 1_000_000_000.0 -> {
            val s = nsPerTry / 1_000_000_000.0
            "(${formatDecimal(s, 2)} s/try)"
        }

        nsPerTry >= 1_000_000.0 -> {
            val ms = nsPerTry / 1_000_000.0
            "(${formatDecimal(ms, if (ms >= 10) 1 else 2)} ms/try)"
        }

        nsPerTry >= 1_000.0 -> {
            val us = nsPerTry / 1_000.0
            "(${formatDecimal(us, if (us >= 10) 1 else 2)} µs/try)"
        }

        else -> "(${formatDecimal(nsPerTry, 0)} ns/try)"
    }
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
