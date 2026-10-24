package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.rememberCountryNames
import org.jetbrains.compose.resources.pluralStringResource
import mahjongtournamentsuite.adminapp.generated.resources.country_players_count
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppPlainTooltip
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.FullBleedRow
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformScrollbarThickness
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformVerticalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.toDigitsInput
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.add_player
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.choose_a_player_in_the_list
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.ema_number
import mahjongtournamentsuite.adminapp.generated.resources.filter_by_code
import mahjongtournamentsuite.adminapp.generated.resources.first_name
import mahjongtournamentsuite.adminapp.generated.resources.last_name
import mahjongtournamentsuite.adminapp.generated.resources.name
import mahjongtournamentsuite.adminapp.generated.resources.new_player
import mahjongtournamentsuite.adminapp.generated.resources.no_player_photo
import mahjongtournamentsuite.adminapp.generated.resources.no_player_selected
import mahjongtournamentsuite.adminapp.generated.resources.only_admins_can_edit_the_player
import mahjongtournamentsuite.adminapp.generated.resources.photo
import mahjongtournamentsuite.adminapp.generated.resources.photo_choose
import mahjongtournamentsuite.adminapp.generated.resources.photo_choose_new
import mahjongtournamentsuite.adminapp.generated.resources.photo_error_size
import mahjongtournamentsuite.adminapp.generated.resources.photo_error_type
import mahjongtournamentsuite.adminapp.generated.resources.photo_of
import mahjongtournamentsuite.adminapp.generated.resources.player_base
import mahjongtournamentsuite.adminapp.generated.resources.player_base_summary
import mahjongtournamentsuite.adminapp.generated.resources.player_details
import mahjongtournamentsuite.adminapp.generated.resources.player_error_country
import mahjongtournamentsuite.adminapp.generated.resources.player_error_ema_digits
import mahjongtournamentsuite.adminapp.generated.resources.player_error_ema_exists
import mahjongtournamentsuite.adminapp.generated.resources.player_error_ema_required
import mahjongtournamentsuite.adminapp.generated.resources.player_error_first_name
import mahjongtournamentsuite.adminapp.generated.resources.player_error_last_name
import mahjongtournamentsuite.adminapp.generated.resources.player_error_required_fields
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.remove_country_filter
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.save_changes_to
import mahjongtournamentsuite.adminapp.generated.resources.save_player_changes
import mahjongtournamentsuite.adminapp.generated.resources.search_by_name_or_ema_number
import mahjongtournamentsuite.adminapp.generated.resources.this_player
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Shared player base. All signed-in users can read it. Only admins can change it. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerBaseScreen(navController: NavHostController) {
    val countryNameOf = rememberCountryNames()
    val presenter = koinInject<PlayerBasePresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val players by store.players.collectAsState()
    val admin by store.adminStatus.collectAsState()
    val canEdit = admin?.canEditPlayers == true
    val canEditEmaNumber = admin?.isAdmin == true
    var selectedEmaId by remember { mutableStateOf<String?>(null) }
    var emaId by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<SelectedImage?>(null) }
    var showNewPlayerDialog by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var restoreSaveFocus by remember { mutableStateOf(false) }
    var newEmaId by remember { mutableStateOf("") }
    var newFirstName by remember { mutableStateOf("") }
    var newLastName by remember { mutableStateOf("") }
    var newCountry by remember { mutableStateOf("") }
    var newPhoto by remember { mutableStateOf<SelectedImage?>(null) }
    var newPlayerError by remember { mutableStateOf<UiText?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    val searchFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val selected = players.firstOrNull { it.emaId == selectedEmaId }
    val hasPlayerChanges = selected?.let { player ->
        emaId.trim() != player.emaId ||
            firstName.trim() != player.firstName ||
            lastName.trim() != player.lastName ||
            country.trim() != player.country ||
            pendingPhoto != null
    } == true
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    val existingPlayerCountryCodes = remember(players, countries) {
        players
            .map { it.country.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { code ->
                normalizeSearchText(
                    countries.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name ?: code,
                )
            }
    }
    val filteredPlayers = remember(players, searchQuery, selectedCountryCodes) {
        val query = normalizeSearchText(searchQuery.trim())
        players.filter {
            val matchesSearch = query.isEmpty() ||
                normalizeSearchText(it.emaId).contains(query) ||
                normalizeSearchText(it.displayName).contains(query)
            val matchesCountry = selectedCountryCodes.isEmpty() ||
                it.country.trim().uppercase() in selectedCountryCodes
            matchesSearch && matchesCountry
        }
    }
    val filteredCountryCount = remember(filteredPlayers) {
        filteredPlayers
            .map { it.country.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .size
    }
    val playerFocusRequesters = remember(filteredPlayers.map { it.emaId }) {
        filteredPlayers.map { FocusRequester() }
    }
    var playerFocusApplied by remember { mutableStateOf(false) }
    var lastFocusedPlayerEmaId by rememberSaveable { mutableStateOf<String?>(null) }

    fun select(player: Player?) {
        selectedEmaId = player?.emaId
        emaId = player?.emaId.orEmpty()
        firstName = player?.firstName.orEmpty()
        lastName = player?.lastName.orEmpty()
        country = player?.country.orEmpty()
        photoUrl = player?.photoUrl.orEmpty()
        pendingPhoto = null
    }
    fun upsertPlayerLocally(previousEmaId: String, player: Player) {
        store.upsertBasePlayers(
            (players.filterNot { it.emaId == previousEmaId || it.emaId == player.emaId } + player)
                .sortedWith(compareBy(Player::lastName, Player::firstName, Player::emaId)),
        )
    }
    val imagePicker = rememberImagePicker(
        onImageSelected = { image ->
            val validationError = validatePlayerPhoto(image)
            if (validationError == null) {
                pendingPhoto = image
                error = null
            } else {
                error = validationError
            }
        },
        onError = { error = it },
    )
    val newPlayerImagePicker = rememberImagePicker(
        onImageSelected = { image ->
            val validationError = validatePlayerPhoto(image)
            if (validationError == null) {
                newPhoto = image
                newPlayerError = null
            } else {
                newPlayerError = validationError
            }
        },
        onError = { newPlayerError = it },
    )
    fun openNewPlayerDialog() {
        newEmaId = ""
        newFirstName = ""
        newLastName = ""
        newCountry = ""
        newPhoto = null
        newPlayerError = null
        showNewPlayerDialog = true
    }
    fun refresh(force: Boolean = false) = scope.launch {
        loading = true
        when (val result = presenter.loadPlayers(force)) {
            is AppResult.Success -> store.upsertBasePlayers(result.value)
            is AppResult.Failure -> error = result.error.toUiText()
        }
        loading = false
    }
    fun save() = scope.launch {
        val currentPlayer = selected ?: return@launch
        if (firstName.isBlank() || lastName.isBlank() || !country.trim().matches(Regex("^[A-Za-z]{3}$"))) {
            error = UiText.of(Res.string.player_error_required_fields)
            return@launch
        }
        val rawEmaId = emaId.trim()
        if (!rawEmaId.matches(Regex("\\d{1,8}"))) {
            error = UiText.of(Res.string.player_error_ema_digits)
            return@launch
        }
        val normalizedEmaId = rawEmaId.padStart(8, '0')
        if (players.any { it.emaId == normalizedEmaId && it.emaId != currentPlayer.emaId }) {
            error = UiText.of(Res.string.player_error_ema_exists)
            return@launch
        }
        saving = true
        error = null
        val player = Player(
            emaId = normalizedEmaId,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            country = country.trim().uppercase(),
            photoUrl = currentPlayer.photoUrl,
        )
        val savedPlayer = when (val result = presenter.updatePlayer(currentPlayer.emaId, player)) {
            is AppResult.Success -> player
            is AppResult.Failure -> {
                error = result.error.toUiText()
                saving = false
                return@launch
            }
        }
        val finalPlayer = pendingPhoto?.let { photo ->
            when (val result = presenter.updatePlayerPhoto(savedPlayer.emaId, photo.contentType, photo.bytes)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    upsertPlayerLocally(currentPlayer.emaId, savedPlayer)
                    select(savedPlayer)
                    pendingPhoto = photo
                    error = result.error.toUiText()
                    saving = false
                    return@launch
                }
            }
        } ?: savedPlayer
        upsertPlayerLocally(currentPlayer.emaId, finalPlayer)
        pendingPhoto = null
        select(finalPlayer)
        when (val result = presenter.loadPlayers(force = true)) {
            is AppResult.Success -> store.upsertBasePlayers(result.value)
            is AppResult.Failure -> error = result.error.toUiText()
        }
        saving = false
    }
    fun createPlayer() = scope.launch {
        val rawEmaId = newEmaId.trim()
        val normalizedEmaId = rawEmaId.padStart(8, '0')
        val normalizedFirstName = newFirstName.trim()
        val normalizedLastName = newLastName.trim()
        val normalizedCountry = newCountry.trim().uppercase()
        newPlayerError = when {
            rawEmaId.isEmpty() -> UiText.of(Res.string.player_error_ema_required)
            !rawEmaId.matches(Regex("\\d{1,8}")) -> UiText.of(Res.string.player_error_ema_digits)
            players.any { it.emaId == normalizedEmaId } -> UiText.of(Res.string.player_error_ema_exists)
            normalizedFirstName.isEmpty() -> UiText.of(Res.string.player_error_first_name)
            normalizedLastName.isEmpty() -> UiText.of(Res.string.player_error_last_name)
            !normalizedCountry.matches(Regex("^[A-Za-z]{3}$")) -> UiText.of(Res.string.player_error_country)
            else -> null
        }
        if (newPlayerError != null) return@launch

        saving = true
        val createdPlayer = when (
            val result = presenter.createPlayer(
                Player(
                    emaId = normalizedEmaId,
                    firstName = normalizedFirstName,
                    lastName = normalizedLastName,
                    country = normalizedCountry,
                ),
            )
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                newPlayerError = result.error.toUiText()
                saving = false
                return@launch
            }
        }
        val finalPlayer = newPhoto?.let { photo ->
            when (val result = presenter.updatePlayerPhoto(createdPlayer.emaId, photo.contentType, photo.bytes)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    upsertPlayerLocally(createdPlayer.emaId, createdPlayer)
                    showNewPlayerDialog = false
                    select(createdPlayer)
                    pendingPhoto = photo
                    error = result.error.toUiText()
                    saving = false
                    return@launch
                }
            }
        } ?: createdPlayer
        upsertPlayerLocally(finalPlayer.emaId, finalPlayer)
        showNewPlayerDialog = false
        select(finalPlayer)
        saving = false
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(existingPlayerCountryCodes) {
        val availableCountryCodes = existingPlayerCountryCodes.toSet()
        val availableSelection = selectedCountryCodes.intersect(availableCountryCodes)
        if (availableSelection != selectedCountryCodes) {
            selectedCountryCodes = availableSelection
        }
    }
    LaunchedEffect(loading, filteredPlayers.map { it.emaId }) {
        if (!playerFocusApplied && !loading) {
            val index = filteredPlayers.indexOfFirst { it.emaId == lastFocusedPlayerEmaId }
                .takeIf { it >= 0 }
                ?: 0
            if (index in playerFocusRequesters.indices) {
                if (selected == null) select(filteredPlayers[index])
                playerFocusRequesters[index].requestFocus()
            } else {
                if (selected == null && players.isNotEmpty()) select(players.first())
                searchFocusRequester.requestFocus()
            }
            playerFocusApplied = true
        }
    }
    LaunchedEffect(Unit) {
        when (val result = presenter.loadCountries()) {
            is AppResult.Success -> countries = result.value
            is AppResult.Failure -> error = result.error.toUiText()
        }
    }
    LaunchedEffect(showSaveConfirmation, restoreSaveFocus) {
        if (!showSaveConfirmation && restoreSaveFocus && hasPlayerChanges) {
            saveFocusRequester.requestFocus()
            restoreSaveFocus = false
        }
    }

    if (showSaveConfirmation) {
        val confirmFocusRequester = remember { FocusRequester() }
        val cancelFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            confirmFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = {
                if (!saving) {
                    showSaveConfirmation = false
                    restoreSaveFocus = true
                }
            },
            title = { Text(stringResource(Res.string.save_player_changes)) },
            text = {
                Text(stringResource(Res.string.save_changes_to, selected?.displayName ?: stringResource(Res.string.this_player)))
            },
            confirmButton = {
                Button(
                    enabled = !saving,
                    onClick = {
                        showSaveConfirmation = false
                        save()
                    },
                    focusRequester = confirmFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = cancelFocusRequester,
                        next = cancelFocusRequester,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Text(stringResource(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        showSaveConfirmation = false
                        restoreSaveFocus = true
                    },
                    focusRequester = cancelFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = confirmFocusRequester,
                        next = confirmFocusRequester,
                    ),
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showNewPlayerDialog) {
        val newEmaFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            newEmaFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!saving) showNewPlayerDialog = false },
            title = { Text(stringResource(Res.string.new_player)) },
            text = {
                val newPlayerScrollState = rememberScrollState()
                Box(modifier = Modifier.heightIn(max = 620.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(newPlayerScrollState)
                            .padding(end = PlatformScrollbarThickness),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    newPlayerError?.let { AppErrorDialog(it, onDismiss = { newPlayerError = null }) }
                    OutlinedTextField(
                        value = newEmaId,
                        onValueChange = { newEmaId = it.toDigitsInput(8); newPlayerError = null },
                        label = { Text(stringResource(Res.string.ema_number)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(newEmaFocusRequester),
                    )
                    OutlinedTextField(
                        value = newFirstName,
                        onValueChange = { newFirstName = it; newPlayerError = null },
                        label = { Text(stringResource(Res.string.first_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newLastName,
                        onValueChange = { newLastName = it; newPlayerError = null },
                        label = { Text(stringResource(Res.string.last_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmaCountryDropdown(
                        selectedCode = newCountry,
                        onCountrySelected = { newCountry = it; newPlayerError = null },
                        label = stringResource(Res.string.country),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PlayerPhotoPreview(
                        model = newPhoto?.dataUrl,
                        playerName = "$newFirstName $newLastName".trim(),
                        maxWidth = 200.dp,
                        maxHeight = 220.dp,
                    )
                    Button(enabled = !saving, onClick = newPlayerImagePicker::launch) {
                        Text(stringResource(if (newPhoto == null) Res.string.photo_choose else Res.string.photo_choose_new))
                    }
                    newPhoto?.let {
                        Text(
                            text = it.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    }
                    PlatformVerticalScrollbar(
                        scrollState = newPlayerScrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(PlatformScrollbarThickness),
                    )
                }
            },
            confirmButton = {
                Button(enabled = !saving, onClick = ::createPlayer) { Text(stringResource(Res.string.add_player)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showNewPlayerDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    AppScaffold(
        title = stringResource(Res.string.players),
        isLoading = loading || saving,
        showBackgroundLogo = loading && players.isEmpty(),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onNewPlayer = if (canEdit) ::openNewPlayerDialog else null,
            )
        },
    ) {
        ScreenColumn(maxWidth = 1200.dp, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let {
                AppErrorDialog(
                    message = it,
                    onDismiss = { error = null },
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(Res.string.search_by_name_or_ema_number)) },
                singleLine = true,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                searchFocusRequester.requestFocus()
                            },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search))
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
            )
            if (existingPlayerCountryCodes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FullBleedRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        existingPlayerCountryCodes.forEach { code ->
                            val countryName = countryNameOf(
                                code,
                                countries.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name ?: code,
                            )
                            val countryPlayerCount = players.count {
                                it.country.trim().equals(code, ignoreCase = true)
                            }
                            val isSelected = code in selectedCountryCodes
                            val filterDescription = stringResource(
                                if (isSelected) Res.string.remove_country_filter else Res.string.filter_by_code,
                                countryName,
                            )
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above,
                                ),
                                tooltip = {
                                    AppPlainTooltip {
                                        Text(pluralStringResource(Res.plurals.country_players_count, countryPlayerCount, countryName, countryPlayerCount))
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                TextButton(
                                    onClick = {
                                        selectedCountryCodes = if (isSelected) {
                                            selectedCountryCodes - code
                                        } else {
                                            selectedCountryCodes + code
                                        }
                                    },
                                    buttonModifier = Modifier.semantics {
                                        contentDescription = filterDescription
                                    },
                                    shape = CircleShape,
                                    colors = if (isSelected) {
                                        ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    CountryFlag(code = code)
                                    Spacer(Modifier.width(4.dp))
                                    Text("$code ($countryPlayerCount)")
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(
                    modifier = Modifier.weight(1.7f).fillMaxHeight(),
                    title = stringResource(Res.string.player_base),
                    subtitle = stringResource(Res.string.player_base_summary, filteredPlayers.size, filteredCountryCount),
                ) {
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        DataTableHeaderRow {
                            PlayerTableHeader(stringResource(Res.string.photo), Modifier.width(PlayerPhotoColumnWidth), TextAlign.Center)
                            PlayerTableHeader(stringResource(Res.string.country), Modifier.width(PlayerCountryColumnWidth), TextAlign.Center)
                            PlayerTableHeader(stringResource(Res.string.name), Modifier.weight(1f))
                            PlayerTableHeader(stringResource(Res.string.ema_number), Modifier.width(PlayerEmaColumnWidth))
                        }
                        DataTableDivider()
                        LazyColumnWithScrollbar(
                            state = rememberPersistentLazyListState("players"),
                            modifier = Modifier.weight(1f),
                        ) {
                            itemsIndexed(filteredPlayers, key = { _, player -> player.emaId }) { index, player ->
                                DataTableRow(
                                    modifier = Modifier
                                        .focusRequester(playerFocusRequesters[index])
                                        .onFocusChanged {
                                            if (it.isFocused) lastFocusedPlayerEmaId = player.emaId
                                        },
                                    onClick = { select(player) },
                                    highlighted = selectedEmaId == player.emaId,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(PlayerPhotoColumnWidth)
                                            .height(PlayerPhotoSize),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (player.photoUrl.isNullOrBlank()) {
                                            PlayerPhotoPlaceholder(PlayerPhotoSize)
                                        } else {
                                            AsyncImage(
                                                model = player.photoUrl,
                                                contentDescription = stringResource(Res.string.photo_of, player.displayName),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.width(PlayerPhotoSize).height(PlayerPhotoSize),
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.width(PlayerCountryColumnWidth),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CountryFlagWithTooltip(player.country, countries)
                                    }
                                    Text(player.displayName, modifier = Modifier.weight(1f))
                                    Text(
                                        player.emaId,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(PlayerEmaColumnWidth),
                                    )
                                }
                                DataTableDivider()
                            }
                        }
                    }
                }
                SectionCard(
                    modifier = Modifier.weight(0.8f).fillMaxHeight(),
                    title = stringResource(Res.string.player_details),
                ) {
                    if (selected == null) {
                        AppEmptyState(
                            icon = Icons.Default.PersonSearch,
                            title = stringResource(Res.string.no_player_selected),
                            message = stringResource(Res.string.choose_a_player_in_the_list),
                            compact = true,
                        )
                    } else {
                        val detailsScrollState = rememberScrollState()
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(detailsScrollState)
                                    .padding(end = PlatformScrollbarThickness),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                            OutlinedTextField(
                                value = emaId,
                                onValueChange = { emaId = it.toDigitsInput(8) },
                                label = { Text(stringResource(Res.string.ema_number)) },
                                enabled = canEditEmaNumber && !saving,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(firstName, { firstName = it }, label = { Text(stringResource(Res.string.first_name)) }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(lastName, { lastName = it }, label = { Text(stringResource(Res.string.last_name)) }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                            EmaCountryDropdown(
                                selectedCode = country,
                                onCountrySelected = { country = it },
                                enabled = canEdit,
                                label = stringResource(Res.string.country),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val photoModel = pendingPhoto?.dataUrl ?: photoUrl.takeIf { it.isNotBlank() }
                            PlayerPhotoPreview(
                                model = photoModel,
                                playerName = "$firstName $lastName".trim(),
                                maxWidth = 280.dp,
                                maxHeight = 340.dp,
                                enabled = canEdit && !saving,
                                onClick = imagePicker::launch,
                            )
                            if (canEdit) {
                                pendingPhoto?.let {
                                    Text(
                                        text = it.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (canEdit && hasPlayerChanges) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                ) {
                                    Button(
                                        enabled = !saving,
                                        onClick = { showSaveConfirmation = true },
                                        focusRequester = saveFocusRequester,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary,
                                        ),
                                    ) {
                                        Text(stringResource(Res.string.save))
                                    }
                                }
                            } else if (!canEdit) {
                                Text(stringResource(Res.string.only_admins_can_edit_the_player), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            }
                            PlatformVerticalScrollbar(
                                scrollState = detailsScrollState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(PlatformScrollbarThickness),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PlayerPhotoSize = 40.dp
private val PlayerPhotoColumnWidth = 48.dp
private val PlayerCountryColumnWidth = 64.dp
private val PlayerEmaColumnWidth = 96.dp
private const val MaxPlayerPhotoBytes = 5 * 1024 * 1024
private val SupportedPlayerPhotoTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

@Composable
private fun PlayerTableHeader(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}

@Composable
private fun PlayerPhotoPlaceholder(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = stringResource(Res.string.no_player_photo),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

@Composable
private fun PlayerPhotoPreview(
    model: Any?,
    playerName: String,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val photo: @Composable () -> Unit = {
        if (model == null) {
            PlayerPhotoPlaceholder(160.dp)
        } else {
            AsyncImage(
                model = model,
                contentDescription = stringResource(Res.string.photo_of, playerName),
                contentScale = ContentScale.Fit,
                modifier = Modifier.sizeIn(
                    minWidth = 120.dp,
                    minHeight = 120.dp,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                ),
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (onClick == null) {
            photo()
        } else {
            FocusHighlightContainer(
                modifier = Modifier,
                interactionSource = interactionSource,
            ) {
                Box(
                    modifier = Modifier
                        .clickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .activateOnEnter(enabled = enabled, onClick = onClick),
                ) {
                    photo()
                }
            }
        }
    }
}

private fun validatePlayerPhoto(image: SelectedImage): UiText? = when {
    image.contentType !in SupportedPlayerPhotoTypes -> UiText.of(Res.string.photo_error_type)
    image.bytes.size > MaxPlayerPhotoBytes -> UiText.of(Res.string.photo_error_size)
    else -> null
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CountryFlagWithTooltip(code: String, countries: List<Country>) {
    val country = countries.firstOrNull { it.code == code }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { AppPlainTooltip { Text(country?.name ?: code) } },
        state = rememberTooltipState(),
    ) {
        CountryFlag(code = code, contentDescription = country?.name ?: code)
    }
}
