package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import coil3.compose.AsyncImage

/** Shared player base. All signed-in users can read it. Only global admins can change it. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerBaseScreen(navController: NavHostController) {
    val presenter = koinInject<PlayerBasePresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val players by store.players.collectAsState()
    val admin by store.adminStatus.collectAsState()
    val canEdit = admin?.canEditPlayers == true
    val canEditEmaNumber = admin?.isSuperadmin == true
    var selectedEmaId by remember { mutableStateOf<String?>(null) }
    var emaId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<SelectedImage?>(null) }
    var showNewPlayerDialog by remember { mutableStateOf(false) }
    var newEmaId by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newCountry by remember { mutableStateOf("") }
    var newCountryQuery by remember { mutableStateOf("") }
    var newCountryMenuExpanded by remember { mutableStateOf(false) }
    var newPhoto by remember { mutableStateOf<SelectedImage?>(null) }
    var newPlayerError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = players.firstOrNull { it.emaId == selectedEmaId }
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
    val filteredPlayers = remember(players, countries, searchQuery) {
        val query = normalizeSearchText(searchQuery.trim())
        if (query.isEmpty()) players else players.filter {
            val countryName = countries.firstOrNull { country -> country.code == it.country }?.name.orEmpty()
            normalizeSearchText(it.emaId).contains(query) ||
                normalizeSearchText(it.name).contains(query) ||
                normalizeSearchText(it.country).contains(query) ||
                normalizeSearchText(countryName).contains(query)
        }
    }

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
    LaunchedEffect(Unit) {
        when (val result = presenter.loadCountries()) {
            is AppResult.Success -> countries = result.value
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
    }

    if (showNewPlayerDialog) {
        AlertDialog(
            onDismissRequest = { if (!saving) showNewPlayerDialog = false },
            title = { Text("New player") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
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
                        modifier = Modifier.fillMaxWidth(),
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
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onRefresh = ::refresh,
                onNewPlayer = if (canEdit) ::openNewPlayerDialog else null,
            )
        },
    ) {
        ScreenColumn(maxWidth = 1200.dp, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { AppErrorMessage(it) }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by name, country, or EMA number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(
                    modifier = Modifier.weight(if (selected == null) 1f else 1.7f).fillMaxHeight(),
                    title = "Player base",
                    subtitle = "${filteredPlayers.size} of ${players.size} EMA players",
                ) {
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        DataTableHeaderRow {
                            PlayerTableHeader("Photo", Modifier.width(PlayerPhotoColumnWidth), TextAlign.Center)
                            PlayerTableHeader("Country", Modifier.width(PlayerCountryColumnWidth), TextAlign.Center)
                            PlayerTableHeader("Name", Modifier.weight(1f))
                            PlayerTableHeader("EMA number", Modifier.width(PlayerEmaColumnWidth))
                        }
                        DataTableDivider()
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredPlayers, key = { it.emaId }) { player ->
                                DataTableRow(onClick = { select(player) }) {
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
                if (selected != null) {
                    SectionCard(
                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                        title = "Player details",
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
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
                            )
                            if (canEdit) {
                                Button(enabled = !saving, onClick = imagePicker::launch) {
                                    Text(if (photoModel == null) "Choose photo" else "Choose new photo")
                                }
                                pendingPhoto?.let {
                                    Text(
                                        text = it.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (canEdit) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !saving, onClick = ::save) { Text("Save") }
                                    TextButton(enabled = !saving, onClick = { select(null) }) { Text("Close") }
                                }
                            } else {
                                Text("Only admins and superadmins can edit the player base.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
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
                if (selectedCountry == null) selectedCode else "${countryFlag(selectedCountry.code)} ${selectedCountry.name}"
            },
            onValueChange = {
                onQueryChange(it)
                onExpandedChange(true)
            },
            label = { Text("Country") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            filteredCountries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${countryFlag(option.code)} ${option.name}") },
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
        Text(countryFlag(code))
    }
}

private fun countryFlag(code: String): String {
    val normalized = code.trim().uppercase()
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return "🌐"
    return normalized.map { regionalIndicator(it) }.joinToString("")
}

private fun regionalIndicator(letter: Char): String {
    val codePoint = 0x1F1E6 + (letter.code - 'A'.code)
    val offset = codePoint - 0x10000
    val high = ((offset / 0x400) + 0xD800).toChar()
    val low = ((offset % 0x400) + 0xDC00).toChar()
    return charArrayOf(high, low).concatToString()
}
