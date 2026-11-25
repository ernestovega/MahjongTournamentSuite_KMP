package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_ORGANIZATION_COLOR
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationPlace
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsRadioRow
import com.etologic.mahjongtournamentsuite.presentation.components.OrganizationLogo
import com.etologic.mahjongtournamentsuite.presentation.components.RequiredLabel
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.TopBarButtonsLayout
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorField
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoCropDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoPreview
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentPlaceFields
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.rememberCountryNames
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.OrganizationsPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.delete
import mahjongtournamentsuite.adminapp.generated.resources.error_star_prefix
import mahjongtournamentsuite.adminapp.generated.resources.field_error_color
import mahjongtournamentsuite.adminapp.generated.resources.filter_by_code
import mahjongtournamentsuite.adminapp.generated.resources.loading
import mahjongtournamentsuite.adminapp.generated.resources.logo
import mahjongtournamentsuite.adminapp.generated.resources.logo_choose
import mahjongtournamentsuite.adminapp.generated.resources.logo_must_be_2mb
import mahjongtournamentsuite.adminapp.generated.resources.logo_must_be_jpeg_png
import mahjongtournamentsuite.adminapp.generated.resources.logo_upload
import mahjongtournamentsuite.adminapp.generated.resources.no_matches
import mahjongtournamentsuite.adminapp.generated.resources.organization_country_needed
import mahjongtournamentsuite.adminapp.generated.resources.organization_delete_text
import mahjongtournamentsuite.adminapp.generated.resources.organization_delete_title
import mahjongtournamentsuite.adminapp.generated.resources.organization_edit
import mahjongtournamentsuite.adminapp.generated.resources.organization_name
import mahjongtournamentsuite.adminapp.generated.resources.organization_name_needed
import mahjongtournamentsuite.adminapp.generated.resources.organization_new
import mahjongtournamentsuite.adminapp.generated.resources.organization_place
import mahjongtournamentsuite.adminapp.generated.resources.organization_place_info
import mahjongtournamentsuite.adminapp.generated.resources.organization_place_link_hint
import mahjongtournamentsuite.adminapp.generated.resources.organization_place_name
import mahjongtournamentsuite.adminapp.generated.resources.organization_place_needed
import mahjongtournamentsuite.adminapp.generated.resources.organization_type
import mahjongtournamentsuite.adminapp.generated.resources.organization_type_club
import mahjongtournamentsuite.adminapp.generated.resources.organization_type_federation
import mahjongtournamentsuite.adminapp.generated.resources.organizations_empty
import mahjongtournamentsuite.adminapp.generated.resources.organizations_title
import mahjongtournamentsuite.adminapp.generated.resources.remove
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.search
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The longest name of an organization that the server keeps. */
private const val MAX_ORGANIZATION_NAME_LENGTH = 80

/** The largest logo that the server takes. */
private const val MAX_LOGO_BYTES = 2 * 1024 * 1024

/** What the editor dialog of an organization edits. A null [id] is a new organization. */
internal data class OrganizationEditing(val id: String?, val organization: Organization?)

internal fun OrganizationType.labelRes(): StringResource = when (this) {
    OrganizationType.CLUB -> Res.string.organization_type_club
    OrganizationType.FEDERATION -> Res.string.organization_type_federation
}

/**
 * The clubs and federations that organize the tournaments. Every staff account sees them. A super admin creates,
 * edits and deletes them; a club admin edits its own ones.
 */
@Composable
fun OrganizationsScreen(navController: NavHostController) {
    val presenter = koinInject<OrganizationsPresenter>()
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<AdminStatus?>(null) }
    var organizations by remember { mutableStateOf<List<Organization>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var editing by remember { mutableStateOf<OrganizationEditing?>(null) }
    var deleting by remember { mutableStateOf<Organization?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryCodes by remember { mutableStateOf(emptySet<String>()) }
    val searchFocusRequester = remember { FocusRequester() }

    fun reload() {
        scope.launch {
            loading = true
            when (val status = presenter.loadAdminStatus()) {
                is AppResult.Success -> access = status.value
                is AppResult.Failure -> error = status.error.toUiText()
            }
            when (val result = presenter.loadOrganizations()) {
                is AppResult.Success -> organizations = result.value
                is AppResult.Failure -> if (error == null) error = result.error.toUiText()
            }
            loading = false
        }
    }
    LaunchedEffect(presenter) { reload() }

    fun save(target: OrganizationEditing, draft: OrganizationDraft) {
        scope.launch {
            saving = true
            val result = if (target.id == null) presenter.createOrganization(draft) else presenter.updateOrganization(target.id, draft)
            when (result) {
                is AppResult.Success -> {
                    editing = null
                    when (val list = presenter.loadOrganizations()) {
                        is AppResult.Success -> organizations = list.value
                        is AppResult.Failure -> error = list.error.toUiText()
                    }
                }
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    fun delete(organization: Organization) {
        scope.launch {
            saving = true
            when (val result = presenter.deleteOrganization(organization.id)) {
                is AppResult.Success -> {
                    deleting = null
                    editing = null
                    organizations = organizations?.filterNot { it.id == organization.id }
                }
                is AppResult.Failure -> {
                    deleting = null
                    error = result.error.toUiText()
                }
            }
            saving = false
        }
    }

    AppScaffold(
        title = stringResource(Res.string.organizations_title),
        isLoading = loading || saving,
        onBack = { navController.popBackStack() },
        actions = if (access?.canCreateOrganizations == true) {
            {
                TopBarButtonsLayout {
                    AppTopBarButton(stringResource(Res.string.organization_new), Icons.Default.Add, onClick = {
                        editing = OrganizationEditing(id = null, organization = null)
                    })
                }
            }
        } else {
            null
        },
    ) {
        ScreenColumn(maxWidth = 1200.dp, scrollable = true, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { AppErrorDialog(message = it, onDismiss = { error = null }) }
            val countryNameOf = rememberCountryNames()
            SectionCard(container = false) {
                val all = organizations
                val countryCodes = all.orEmpty().map { it.country.uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
                val query = searchQuery.trim()
                val list = all?.filter { organization ->
                    (selectedCountryCodes.isEmpty() || organization.country.uppercase() in selectedCountryCodes) &&
                        (query.isEmpty() || organization.name.contains(query, ignoreCase = true))
                }
                if (!all.isNullOrEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(Res.string.search)) },
                        singleLine = true,
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchFocusRequester.requestFocus()
                                }) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search)) }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                    )
                    if (countryCodes.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScrollWithIndicators(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            countryCodes.forEach { code ->
                                val isSelected = code in selectedCountryCodes
                                val count = all.count { it.country.uppercase() == code }
                                TextButton(
                                    onClick = { selectedCountryCodes = if (isSelected) selectedCountryCodes - code else selectedCountryCodes + code },
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
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    CountryFlag(code = code, contentDescription = stringResource(Res.string.filter_by_code, code))
                                    Spacer(Modifier.width(4.dp))
                                    ButtonText("$code ($count)")
                                }
                            }
                        }
                    }
                }
                when {
                    all != null && all.isNotEmpty() && list.isNullOrEmpty() -> Text(stringResource(Res.string.no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list == null -> Text(stringResource(Res.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.isEmpty() -> Text(stringResource(Res.string.organizations_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> OrganizationsGrid(list) { organization ->
                        OrganizationCard(
                            organization = organization,
                            details = stringResource(organization.type.labelRes()) + " · " + countryNameOf(organization.country, organization.country),
                            enabled = !saving && organization.canEdit,
                            onClick = { editing = OrganizationEditing(organization.id, organization) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    editing?.let { current ->
        OrganizationEditorDialog(
            organization = current.organization,
            saving = saving,
            onDismiss = { editing = null },
            onSave = { draft -> save(current, draft) },
            onDelete = current.organization?.takeIf { it.canDelete }?.let { organization -> { deleting = organization } },
            onError = { error = it },
            resolvePlaceLink = presenter::resolvePlaceLink,
        )
    }
    deleting?.let { organization ->
        DeleteOrganizationDialog(organization = organization, saving = saving, onDismiss = { deleting = null }, onConfirm = { delete(organization) })
    }
}

/** The organizations in a grid of cards: as many columns as fit [OrganizationCardMinWidth], and two on a phone. */
@Composable
private fun OrganizationsGrid(organizations: List<Organization>, card: @Composable (Organization) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / OrganizationCardMinWidth).toInt().coerceAtLeast(2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            organizations.chunked(columns).forEach { row ->
                // The cards of a row take the height of the tallest one.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { organization -> Box(Modifier.weight(1f).fillMaxHeight()) { card(organization) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun OrganizationCard(organization: Organization, details: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(modifier = modifier, interactionSource = interactionSource) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .activateOnEnter(enabled, onClick)
                .clickable(enabled = enabled, interactionSource = interactionSource, onClick = onClick),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OrganizationLogo(organization, size = 64.dp)
                    Text(
                        organization.name,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (organization.country.isNotBlank()) CountryFlag(code = organization.country, width = 16.dp)
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (enabled) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
            }
        }
    }
}

private val OrganizationCardMinWidth = 220.dp

/**
 * Creates an organization, or edits one: name, type, country, color, logo, and the optional premises of a club. The
 * tournaments of the organization show this logo and this color when it is their main organizer. The premises are read
 * from a Google Maps link with [resolvePlaceLink], like the venue of a tournament.
 */
@Composable
internal fun OrganizationEditorDialog(
    organization: Organization?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (OrganizationDraft) -> Unit,
    /** Null when the viewer may not delete it. */
    onDelete: (() -> Unit)?,
    onError: (UiText) -> Unit,
    resolvePlaceLink: suspend (String) -> AppResult<ResolvedVenueLink> = { AppResult.Failure(AppError.Unexpected("No place reader")) },
) {
    var name by remember(organization) { mutableStateOf(organization?.name.orEmpty()) }
    val savedPlace = organization?.place ?: OrganizationPlace()
    var placeVenue by remember(organization) {
        mutableStateOf(TournamentVenue(name = savedPlace.name, address = savedPlace.address, mapUrl = savedPlace.mapUrl))
    }
    var placeCity by remember(organization) { mutableStateOf(savedPlace.city) }
    var type by remember(organization) { mutableStateOf(organization?.type ?: OrganizationType.CLUB) }
    var country by remember(organization) { mutableStateOf(organization?.country.orEmpty()) }
    var color by remember(organization) { mutableStateOf(organization?.primaryColor ?: DEFAULT_ORGANIZATION_COLOR) }
    var newLogo by remember(organization) { mutableStateOf<SelectedImage?>(null) }
    var removeLogo by remember(organization) { mutableStateOf(false) }
    var logoToCrop by remember { mutableStateOf<SelectedImage?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var triedSave by remember(organization) { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val logoPicker = rememberImagePicker(
        onImageSelected = { image ->
            when {
                image.contentType !in setOf("image/jpeg", "image/png") -> onError(UiText.of(Res.string.error_star_prefix, UiText.of(Res.string.logo_must_be_jpeg_png)))
                image.bytes.size > MAX_LOGO_BYTES -> onError(UiText.of(Res.string.error_star_prefix, UiText.of(Res.string.logo_must_be_2mb)))
                else -> logoToCrop = image
            }
        },
        onError = { message -> onError(UiText.of(Res.string.error_star_prefix, message)) },
    )
    val trimmedName = name.trim()
    val normalizedColor = color.trim().uppercase()
    val nameError = trimmedName.isEmpty()
    val countryError = country.isBlank()
    val colorError = !Regex("^#[0-9A-F]{6}$").matches(normalizedColor)
    // Only a club has premises. A federation saves none.
    val place = if (type == OrganizationType.CLUB) {
        OrganizationPlace(mapUrl = placeVenue.mapUrl.trim(), name = placeVenue.name.trim(), address = placeVenue.address.trim(), city = placeCity.trim())
    } else {
        OrganizationPlace()
    }
    val placeError = !place.isComplete
    val logoModel: Any? = newLogo?.dataUrl ?: organization?.logoUrl?.takeUnless { removeLogo }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    TrackedDialog(if (organization == null) "NewOrganization" else "EditOrganization") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = {
                MtsDialogTitle(
                    if (organization == null) stringResource(Res.string.organization_new) else stringResource(Res.string.organization_edit),
                    subtitle = organization?.name,
                )
            },
            text = {
                ScrollableColumnWithScrollbar(state = rememberScrollState(), modifier = Modifier.heightIn(max = 560.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(MAX_ORGANIZATION_NAME_LENGTH) },
                            label = { RequiredLabel(stringResource(Res.string.organization_name)) },
                            isError = triedSave && nameError,
                            supportingText = if (triedSave && nameError) ({ Text(stringResource(Res.string.organization_name_needed)) }) else null,
                            singleLine = true,
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                        )
                        Text(stringResource(Res.string.organization_type), style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OrganizationType.entries.forEach { option ->
                                MtsRadioRow(
                                    selected = type == option,
                                    onClick = { type = option },
                                    enabled = !saving,
                                    contentPadding = PaddingValues(end = 8.dp),
                                    controlPadding = 8.dp,
                                ) {
                                    Text(stringResource(option.labelRes()), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        EmaCountryDropdown(
                            selectedCode = country.takeIf { it.isNotBlank() },
                            onCountrySelected = { code -> country = code.uppercase() },
                            enabled = !saving,
                            label = stringResource(Res.string.country),
                            required = true,
                            twoLetterCodes = true,
                            isError = triedSave && countryError,
                            errorMessage = if (triedSave && countryError) stringResource(Res.string.organization_country_needed) else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TournamentColorField(
                            value = color,
                            enabled = !saving,
                            isError = triedSave && colorError,
                            errorMessage = if (triedSave && colorError) stringResource(Res.string.field_error_color) else null,
                            onValueChange = { color = it },
                            onPreviewClick = { showColorPicker = true },
                        )
                        if (type == OrganizationType.CLUB) {
                            Text(stringResource(Res.string.organization_place), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(Res.string.organization_place_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TournamentPlaceFields(
                                venue = placeVenue,
                                onVenueChange = { placeVenue = it },
                                hostCountry = country,
                                onHostCountryChange = {},
                                hostCity = placeCity,
                                onHostCityChange = { placeCity = it },
                                countries = emptyList(),
                                resolveLink = resolvePlaceLink,
                                enabled = !saving,
                                venueError = if (triedSave && placeError) stringResource(Res.string.organization_place_needed) else null,
                                // The club keeps its country above, so the link leaves it.
                                countryLocked = true,
                                placeRequired = false,
                                showCountry = false,
                                showContactEmail = false,
                                nameLabel = stringResource(Res.string.organization_place_name),
                                linkHint = stringResource(Res.string.organization_place_link_hint),
                            )
                        }
                        Text(stringResource(Res.string.logo), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            TournamentLogoPreview(model = logoModel, onImageInfo = {}, modifier = Modifier.size(120.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FocusedButton(onClick = logoPicker::launch, enabled = !saving) {
                                    ButtonText(stringResource(if (logoModel == null) Res.string.logo_choose else Res.string.logo_upload))
                                }
                                if (logoModel != null) {
                                    FocusedTextButton(
                                        onClick = {
                                            newLogo = null
                                            removeLogo = organization?.logoUrl != null
                                        },
                                        enabled = !saving,
                                    ) { ButtonText(stringResource(Res.string.remove)) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = {
                            triedSave = true
                            if (!nameError && !countryError && !colorError && !placeError) {
                                onSave(
                                    OrganizationDraft(
                                        name = trimmedName,
                                        type = type,
                                        country = country,
                                        primaryColor = normalizedColor,
                                        place = place,
                                        logoContentType = newLogo?.contentType,
                                        logoBytes = newLogo?.bytes,
                                        removeLogo = removeLogo && newLogo == null,
                                    ),
                                )
                            }
                        },
                        enabled = !saving,
                    ) { ButtonText(stringResource(Res.string.save)) }
                    if (onDelete != null) {
                        FocusedOutlinedButton(
                            onClick = onDelete,
                            enabled = !saving,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(!saving),
                        ) { ButtonText(stringResource(Res.string.delete)) }
                    }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }

    if (showColorPicker) {
        TournamentColorPickerDialog(
            initialColor = color,
            onDismiss = { showColorPicker = false },
            onColorSelected = {
                color = it
                showColorPicker = false
            },
        )
    }
    logoToCrop?.let { image ->
        TournamentLogoCropDialog(
            image = image,
            onDismiss = { logoToCrop = null },
            onCropped = { cropped ->
                newLogo = cropped
                removeLogo = false
                logoToCrop = null
            },
            onError = { message -> onError(UiText.of(Res.string.error_star_prefix, message)) },
        )
    }
}

/** Asks before an organization is deleted for good. The server refuses it while it still organizes tournaments. */
@Composable
internal fun DeleteOrganizationDialog(organization: Organization, saving: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    TrackedDialog("DeleteOrganization") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.organization_delete_title, organization.name)) },
            text = { MtsDialogText(stringResource(Res.string.organization_delete_text)) },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = onConfirm, enabled = !saving, colors = MtsButtons.destructiveColors()) {
                        ButtonText(stringResource(Res.string.delete))
                    }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}
