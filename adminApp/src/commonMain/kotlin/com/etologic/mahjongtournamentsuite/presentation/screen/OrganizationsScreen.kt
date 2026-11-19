package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_ORGANIZATION_COLOR
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
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
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
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
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.delete
import mahjongtournamentsuite.adminapp.generated.resources.error_star_prefix
import mahjongtournamentsuite.adminapp.generated.resources.field_error_color
import mahjongtournamentsuite.adminapp.generated.resources.loading
import mahjongtournamentsuite.adminapp.generated.resources.logo
import mahjongtournamentsuite.adminapp.generated.resources.logo_choose
import mahjongtournamentsuite.adminapp.generated.resources.logo_must_be_2mb
import mahjongtournamentsuite.adminapp.generated.resources.logo_must_be_jpeg_png
import mahjongtournamentsuite.adminapp.generated.resources.logo_upload
import mahjongtournamentsuite.adminapp.generated.resources.organization_country_needed
import mahjongtournamentsuite.adminapp.generated.resources.organization_delete_text
import mahjongtournamentsuite.adminapp.generated.resources.organization_delete_title
import mahjongtournamentsuite.adminapp.generated.resources.organization_edit
import mahjongtournamentsuite.adminapp.generated.resources.organization_name
import mahjongtournamentsuite.adminapp.generated.resources.organization_name_needed
import mahjongtournamentsuite.adminapp.generated.resources.organization_new
import mahjongtournamentsuite.adminapp.generated.resources.organization_type
import mahjongtournamentsuite.adminapp.generated.resources.organization_type_club
import mahjongtournamentsuite.adminapp.generated.resources.organization_type_federation
import mahjongtournamentsuite.adminapp.generated.resources.organizations_empty
import mahjongtournamentsuite.adminapp.generated.resources.organizations_info
import mahjongtournamentsuite.adminapp.generated.resources.organizations_title
import mahjongtournamentsuite.adminapp.generated.resources.remove
import mahjongtournamentsuite.adminapp.generated.resources.save
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
        ScreenColumn(maxWidth = 900.dp, scrollable = true, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { AppErrorDialog(message = it, onDismiss = { error = null }) }
            val countryNameOf = rememberCountryNames()
            SectionCard(title = stringResource(Res.string.organizations_title), subtitle = stringResource(Res.string.organizations_info)) {
                val list = organizations
                when {
                    list == null -> Text(stringResource(Res.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.isEmpty() -> Text(stringResource(Res.string.organizations_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> list.forEachIndexed { index, organization ->
                        if (index > 0) HorizontalDivider()
                        OrganizationRow(
                            organization = organization,
                            details = stringResource(organization.type.labelRes()) + " · " + countryNameOf(organization.country, organization.country),
                            enabled = !saving && organization.canEdit,
                            onClick = { editing = OrganizationEditing(organization.id, organization) },
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
        )
    }
    deleting?.let { organization ->
        DeleteOrganizationDialog(organization = organization, saving = saving, onDismiss = { deleting = null }, onConfirm = { delete(organization) })
    }
}

@Composable
private fun OrganizationRow(organization: Organization, details: String, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(modifier = Modifier.fillMaxWidth(), interactionSource = interactionSource) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .activateOnEnter(enabled, onClick)
                .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OrganizationLogo(organization, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(organization.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (organization.country.isNotBlank()) CountryFlag(code = organization.country, width = 16.dp)
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (enabled) Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Creates an organization, or edits one: name, type, country, color and logo. The tournaments of the organization show
 * this logo and this color when it is their main organizer.
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
) {
    var name by remember(organization) { mutableStateOf(organization?.name.orEmpty()) }
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
                                Row(
                                    Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .selectable(selected = type == option, enabled = !saving, role = Role.RadioButton) { type = option }
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = type == option, onClick = null, enabled = !saving, modifier = Modifier.padding(8.dp))
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
                        Text(stringResource(Res.string.logo), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            TournamentLogoPreview(model = logoModel, onImageInfo = {}, modifier = Modifier.size(120.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FocusedButton(onClick = logoPicker::launch, enabled = !saving) {
                                    Text(stringResource(if (logoModel == null) Res.string.logo_choose else Res.string.logo_upload))
                                }
                                if (logoModel != null) {
                                    FocusedTextButton(
                                        onClick = {
                                            newLogo = null
                                            removeLogo = organization?.logoUrl != null
                                        },
                                        enabled = !saving,
                                    ) { Text(stringResource(Res.string.remove)) }
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
                            if (!nameError && !countryError && !colorError) {
                                onSave(
                                    OrganizationDraft(
                                        name = trimmedName,
                                        type = type,
                                        country = country,
                                        primaryColor = normalizedColor,
                                        logoContentType = newLogo?.contentType,
                                        logoBytes = newLogo?.bytes,
                                        removeLogo = removeLogo && newLogo == null,
                                    ),
                                )
                            }
                        },
                        enabled = !saving,
                    ) { Text(stringResource(Res.string.save)) }
                    if (onDelete != null) {
                        FocusedOutlinedButton(
                            onClick = onDelete,
                            enabled = !saving,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(!saving),
                        ) { Text(stringResource(Res.string.delete)) }
                    }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(Res.string.cancel)) }
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
                        Text(stringResource(Res.string.delete))
                    }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}
