package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformVerticalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformScrollbarThickness
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import coil3.compose.AsyncImage

/** Shared player base. All signed-in users can read it. Only admins can change it. */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun PlayerBaseScreen(navController: NavHostController) {
    val presenter = koinInject<PlayerBasePresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val players by store.players.collectAsState()
    val admin by store.adminStatus.collectAsState()
    val canEdit = admin?.canEditPlayers == true
    val canEditEmaNumber = admin?.isAdmin == true
    var selectedEmaId by remember { mutableStateOf<String?>(null) }
    var emaId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<SelectedImage?>(null) }
    var showNewPlayerDialog by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var restoreSaveFocus by remember { mutableStateOf(false) }
    var newEmaId by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newCountry by remember { mutableStateOf("") }
    var newCountryQuery by remember { mutableStateOf("") }
    var newCountryMenuExpanded by remember { mutableStateOf(false) }
    var newPhoto by remember { mutableStateOf<SelectedImage?>(null) }
    var newPlayerError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    val searchFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = players.firstOrNull { it.emaId == selectedEmaId }
    val hasPlayerChanges = selected?.let { player ->
        emaId.trim() != player.emaId ||
            name.trim() != player.name ||
            country.trim() != player.country ||
            pendingPhoto != null
    } == true
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
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
                normalizeSearchText(it.name).contains(query)
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
        name = player?.name.orEmpty()
        country = player?.country.orEmpty()
        photoUrl = player?.photoUrl.orEmpty()
        pendingPhoto = null
    }
    fun upsertPlayerLocally(previousEmaId: String, player: Player) {
        store.upsertBasePlayers(
            (players.filterNot { it.emaId == previousEmaId || it.emaId == player.emaId } + player)
                .sortedWith(compareBy(Player::name, Player::emaId)),
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
        newName = ""
        newCountry = ""
        newCountryQuery = ""
        newCountryMenuExpanded = false
        newPhoto = null
        newPlayerError = null
        showNewPlayerDialog = true
    }
    fun refresh() = scope.launch {
        loading = true
        when (val result = presenter.loadPlayers()) {
            is AppResult.Success -> store.upsertBasePlayers(result.value)
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        loading = false
    }
    fun save() = scope.launch {
        val currentPlayer = selected ?: return@launch
        if (name.isBlank() || country.isBlank()) {
            error = "Name and country are required."
            return@launch
        }
        val normalizedEmaId = emaId.trim()
        if (!normalizedEmaId.matches(Regex("\\d+"))) {
            error = "EMA number must contain only digits."
            return@launch
        }
        if (players.any { it.emaId == normalizedEmaId && it.emaId != currentPlayer.emaId }) {
            error = "This EMA number already exists."
            return@launch
        }
        saving = true
        error = null
        val player = Player(
            emaId = normalizedEmaId,
            name = name.trim(),
            country = country.trim(),
            photoUrl = currentPlayer.photoUrl,
        )
        val savedPlayer = when (val result = presenter.updatePlayer(currentPlayer.emaId, player)) {
            is AppResult.Success -> player
            is AppResult.Failure -> {
                error = result.error.toUiMessage()
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
                    error = result.error.toUiMessage()
                    saving = false
                    return@launch
                }
            }
        } ?: savedPlayer
        upsertPlayerLocally(currentPlayer.emaId, finalPlayer)
        pendingPhoto = null
        select(finalPlayer)
        when (val result = presenter.loadPlayers()) {
            is AppResult.Success -> store.upsertBasePlayers(result.value)
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        saving = false
    }
    fun createPlayer() = scope.launch {
        val normalizedEmaId = newEmaId.trim()
        val normalizedName = newName.trim()
        val normalizedCountry = newCountry.trim()
        newPlayerError = when {
            normalizedEmaId.isEmpty() -> "EMA number is required."
            normalizedEmaId.any { !it.isDigit() } -> "EMA number must contain only digits."
            players.any { it.emaId == normalizedEmaId } -> "This EMA number already exists."
            normalizedName.isEmpty() -> "Name is required."
            normalizedCountry.isEmpty() -> "Country is required."
            else -> null
        }
        if (newPlayerError != null) return@launch

        saving = true
        val createdPlayer = when (
            val result = presenter.createPlayer(
                Player(
                    emaId = normalizedEmaId,
                    name = normalizedName,
                    country = normalizedCountry,
                ),
            )
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                newPlayerError = result.error.toUiMessage()
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
                    error = result.error.toUiMessage()
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
            is AppResult.Failure -> error = result.error.toUiMessage()
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
            title = { Text("Save player changes") },
            text = {
                Text("Save the changes to ${selected?.name ?: "this player"}?")
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
                    Text("Save")
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
                    Text("Cancel")
                }
            },
        )
    }

    if (showNewPlayerDialog) {
        val newEmaFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            newEmaFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!saving) showNewPlayerDialog = false },
            title = { Text("New player") },
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
                    newPlayerError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = newEmaId,
                        onValueChange = { newEmaId = it; newPlayerError = null },
                        label = { Text("EMA number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(newEmaFocusRequester),
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; newPlayerError = null },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CountrySelector(
                        selectedCode = newCountry,
                        countries = countries,
                        query = newCountryQuery,
                        expanded = newCountryMenuExpanded,
                        enabled = !saving,
                        onQueryChange = { newCountryQuery = it },
                        onExpandedChange = { newCountryMenuExpanded = it },
                        onCountrySelected = {
                            newCountry = it
                            newCountryQuery = ""
                            newCountryMenuExpanded = false
                            newPlayerError = null
                        },
                    )
                    PlayerPhotoPreview(
                        model = newPhoto?.dataUrl,
                        playerName = newName,
                        maxWidth = 200.dp,
                        maxHeight = 220.dp,
                    )
                    Button(enabled = !saving, onClick = newPlayerImagePicker::launch) {
                        Text(if (newPhoto == null) "Choose photo" else "Choose new photo")
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
                Button(enabled = !saving, onClick = ::createPlayer) { Text("Add player") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showNewPlayerDialog = false }) { Text("Cancel") }
            },
        )
    }

    AppScaffold(
        title = "Players",
        isLoading = loading || saving,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onRefresh = ::refresh,
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
                label = { Text("Search by name or EMA number") },
                singleLine = true,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                searchFocusRequester.requestFocus()
                            },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
            )
            if (existingPlayerCountryCodes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Filter by country",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        existingPlayerCountryCodes.forEach { code ->
                            val countryName = countries
                                .firstOrNull { it.code.equals(code, ignoreCase = true) }
                                ?.name
                                ?: code
                            val countryPlayerCount = players.count {
                                it.country.trim().equals(code, ignoreCase = true)
                            }
                            val isSelected = code in selectedCountryCodes
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above,
                                ),
                                tooltip = {
                                    PlainTooltip {
                                        Text(
                                            "$countryName · $countryPlayerCount " +
                                                if (countryPlayerCount == 1) "player" else "players",
                                        )
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
                                        contentDescription = if (isSelected) {
                                            "Remove $countryName country filter"
                                        } else {
                                            "Filter by $countryName"
                                        }
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
                    title = "Player base",
                    subtitle = "${filteredPlayers.size} players · $filteredCountryCount countries",
                ) {
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        DataTableHeaderRow {
                            PlayerTableHeader("Photo", Modifier.width(PlayerPhotoColumnWidth), TextAlign.Center)
                            PlayerTableHeader("Country", Modifier.width(PlayerCountryColumnWidth), TextAlign.Center)
                            PlayerTableHeader("Name", Modifier.weight(1f))
                            PlayerTableHeader("EMA number", Modifier.width(PlayerEmaColumnWidth))
                        }
                        DataTableDivider()
                        LazyColumnWithScrollbar(
                            state = rememberLazyListState(),
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
                                                contentDescription = "Photo of ${player.name}",
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
                                    Text(player.name, modifier = Modifier.weight(1f))
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
                    title = "Player details",
                ) {
                    if (selected == null) {
                        Text(
                            text = "No player selected.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                onValueChange = { emaId = it },
                                label = { Text("EMA number") },
                                enabled = canEditEmaNumber && !saving,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(name, { name = it }, label = { Text("Name") }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                            CountrySelector(
                                selectedCode = country,
                                countries = countries,
                                query = countryQuery,
                                expanded = countryMenuExpanded,
                                enabled = canEdit,
                                onQueryChange = { countryQuery = it },
                                onExpandedChange = { if (canEdit) countryMenuExpanded = it },
                                onCountrySelected = {
                                    country = it
                                    countryQuery = ""
                                    countryMenuExpanded = false
                                },
                            )
                            val photoModel = pendingPhoto?.dataUrl ?: photoUrl.takeIf { it.isNotBlank() }
                            PlayerPhotoPreview(
                                model = photoModel,
                                playerName = name,
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
                                        Text("Save")
                                    }
                                }
                            } else if (!canEdit) {
                                Text("Only admins can edit the player base.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            contentDescription = "No player photo",
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
                contentDescription = "Photo of $playerName",
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CountrySelector(
    selectedCode: String,
    countries: List<Country>,
    query: String,
    expanded: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onCountrySelected: (String) -> Unit,
) {
    val filteredCountries = remember(countries, query) {
        val normalizedQuery = query.trim().lowercase()
        countries.filter {
            normalizedQuery.isEmpty() ||
                it.name.lowercase().contains(normalizedQuery) ||
                it.code.lowercase().contains(normalizedQuery)
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (expanded) query else {
                val selectedCountry = countries.firstOrNull { it.code == selectedCode }
                selectedCountry?.name ?: selectedCode
            },
            onValueChange = {
                onQueryChange(it)
                onExpandedChange(true)
            },
            label = { Text("Country") },
            leadingIcon = if (!expanded && selectedCode.isNotBlank()) {
                {
                    CountryFlag(
                        code = selectedCode,
                    )
                }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            filteredCountries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CountryFlag(code = option.code)
                            Text(option.name)
                        }
                    },
                    onClick = { onCountrySelected(option.code) },
                )
            }
        }
    }
}

private fun validatePlayerPhoto(image: SelectedImage): String? = when {
    image.contentType !in SupportedPlayerPhotoTypes -> "Choose a JPEG, PNG, WebP, or GIF image."
    image.bytes.size > MaxPlayerPhotoBytes -> "Choose a photo that is 5 MB or smaller."
    else -> null
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CountryFlagWithTooltip(code: String, countries: List<Country>) {
    val country = countries.firstOrNull { it.code == code }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(country?.name ?: code) } },
        state = rememberTooltipState(),
    ) {
        CountryFlag(code = code, contentDescription = country?.name ?: code)
    }
}
