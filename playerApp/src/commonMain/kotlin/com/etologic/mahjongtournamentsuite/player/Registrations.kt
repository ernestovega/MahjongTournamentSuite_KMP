package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MAX_FOOD_RESTRICTIONS_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.MAX_REGISTRATION_TEAM_NAME_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.NoEmaNumberRequest
import com.etologic.mahjongtournamentsuite.domain.model.OwnRegistration
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PublicRegistration
import com.etologic.mahjongtournamentsuite.domain.model.REGISTRATION_TEAM_SIZE
import com.etologic.mahjongtournamentsuite.domain.model.RegisterRequest
import com.etologic.mahjongtournamentsuite.domain.model.RegisterResult
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.TABLE_PATH_WORD
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryRegistrationAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.action_cancel
import mahjongtournamentsuite.playerapp.generated.resources.preg_add_member
import mahjongtournamentsuite.playerapp.generated.resources.preg_add_member_hint
import mahjongtournamentsuite.playerapp.generated.resources.preg_cancel
import mahjongtournamentsuite.playerapp.generated.resources.preg_cancel_confirm
import mahjongtournamentsuite.playerapp.generated.resources.preg_cancel_message
import mahjongtournamentsuite.playerapp.generated.resources.preg_cancel_title
import mahjongtournamentsuite.playerapp.generated.resources.preg_captain
import mahjongtournamentsuite.playerapp.generated.resources.preg_close
import mahjongtournamentsuite.playerapp.generated.resources.preg_closed
import mahjongtournamentsuite.playerapp.generated.resources.preg_closes_on
import mahjongtournamentsuite.playerapp.generated.resources.preg_consent
import mahjongtournamentsuite.playerapp.generated.resources.preg_create_team
import mahjongtournamentsuite.playerapp.generated.resources.preg_done_pending
import mahjongtournamentsuite.playerapp.generated.resources.preg_done_place
import mahjongtournamentsuite.playerapp.generated.resources.preg_done_waiting
import mahjongtournamentsuite.playerapp.generated.resources.preg_email
import mahjongtournamentsuite.playerapp.generated.resources.preg_email_hint
import mahjongtournamentsuite.playerapp.generated.resources.preg_empty
import mahjongtournamentsuite.playerapp.generated.resources.preg_food
import mahjongtournamentsuite.playerapp.generated.resources.preg_food_hint
import mahjongtournamentsuite.playerapp.generated.resources.preg_leave_team
import mahjongtournamentsuite.playerapp.generated.resources.preg_manage
import mahjongtournamentsuite.playerapp.generated.resources.preg_manage_title
import mahjongtournamentsuite.playerapp.generated.resources.preg_me
import mahjongtournamentsuite.playerapp.generated.resources.preg_message
import mahjongtournamentsuite.playerapp.generated.resources.preg_name
import mahjongtournamentsuite.playerapp.generated.resources.preg_no_ema
import mahjongtournamentsuite.playerapp.generated.resources.preg_no_ema_country
import mahjongtournamentsuite.playerapp.generated.resources.preg_no_ema_info
import mahjongtournamentsuite.playerapp.generated.resources.preg_no_ema_sent
import mahjongtournamentsuite.playerapp.generated.resources.preg_no_ema_title
import mahjongtournamentsuite.playerapp.generated.resources.preg_no_matches
import mahjongtournamentsuite.playerapp.generated.resources.preg_open
import mahjongtournamentsuite.playerapp.generated.resources.preg_pick_hint
import mahjongtournamentsuite.playerapp.generated.resources.preg_places
import mahjongtournamentsuite.playerapp.generated.resources.preg_register
import mahjongtournamentsuite.playerapp.generated.resources.preg_register_as
import mahjongtournamentsuite.playerapp.generated.resources.preg_register_info
import mahjongtournamentsuite.playerapp.generated.resources.preg_register_title
import mahjongtournamentsuite.playerapp.generated.resources.preg_remove_member
import mahjongtournamentsuite.playerapp.generated.resources.preg_rename_team
import mahjongtournamentsuite.playerapp.generated.resources.preg_save
import mahjongtournamentsuite.playerapp.generated.resources.preg_search_ema
import mahjongtournamentsuite.playerapp.generated.resources.preg_send
import mahjongtournamentsuite.playerapp.generated.resources.preg_status_pending
import mahjongtournamentsuite.playerapp.generated.resources.preg_status_place
import mahjongtournamentsuite.playerapp.generated.resources.preg_status_waiting
import mahjongtournamentsuite.playerapp.generated.resources.preg_team
import mahjongtournamentsuite.playerapp.generated.resources.preg_team_info
import mahjongtournamentsuite.playerapp.generated.resources.preg_team_name
import mahjongtournamentsuite.playerapp.generated.resources.preg_team_reminder
import mahjongtournamentsuite.playerapp.generated.resources.preg_title_confirmed
import mahjongtournamentsuite.playerapp.generated.resources.preg_title_waiting
import mahjongtournamentsuite.playerapp.generated.resources.preg_without_team
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/**
 * A link of the web that opens a tournament: `/<slug>` (the public link, the registrations before the draw) or
 * `/registration?t=<tournament>&k=<token>` (the link of the emails, which also confirms the email).
 * The old links `/inscripcion/<slug>` and `/inscripcion/gestionar?t=&k=` still work.
 * The QR of a physical table adds `/table/<n>`: `/<slug>/table/5`, or `/t/<id>/table/5` for a tournament without a
 * public link. Its [tableId] opens that table in the round in play. See tableQrLink and docs/table-qr.md.
 */
data class RegistrationLink(
    val slug: String? = null,
    val tournamentId: String? = null,
    val token: String? = null,
    val tableId: Int? = null,
)

/**
 * The first segments of the addresses of the site that are not the link of a tournament. The backend refuses them as
 * link names too (RESERVED_SLUGS of registrationSettings.ts).
 */
private val SitePaths = setOf("admin", "api", "api-docs", "version", "privacy", "support", "green-book", "t")

private val SlugPattern = Regex("^[a-z0-9-]{3,40}$")

/** The word before the number of a table in its QR link. `mesa` also works, for a link typed by hand. */
private val TablePathWords = setOf(TABLE_PATH_WORD, "mesa")

/** The link of a QR of a physical table, from the segments of its path: `<slug>/table/<n>` or `t/<id>/table/<n>`. */
private fun tableLinkOf(parts: List<String>): RegistrationLink? {
    val tableId = parts.last().toIntOrNull()?.takeIf { it in 1..MAX_TABLE_NUMBER } ?: return null
    return when {
        parts.size == 4 && parts[0] == "t" && parts[1].isNotBlank() -> RegistrationLink(tournamentId = parts[1], tableId = tableId)
        parts.size == 3 && parts[0] !in SitePaths -> parts[0].lowercase().takeIf { SlugPattern.matches(it) }
            ?.let { RegistrationLink(slug = it, tableId = tableId) }
        else -> null
    }
}

private const val MAX_TABLE_NUMBER = 999

/**
 * The registration link of an address of the web, or null. [path] is the path without the query, such as `/6thmmc`,
 * and [query] the decoded parameters of the address.
 */
fun registrationLinkOf(path: String, query: Map<String, String>): RegistrationLink? {
    val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
    if (parts.size >= 3 && parts[parts.size - 2].lowercase() in TablePathWords) return tableLinkOf(parts)
    val manage = (parts.size == 1 && parts[0] == "registration") || (parts.size == 2 && parts[0] == "inscripcion" && parts[1] == "gestionar")
    if (manage) {
        val tournamentId = query["t"]?.takeIf { it.isNotBlank() } ?: return null
        return RegistrationLink(tournamentId = tournamentId, token = query["k"]?.takeIf { it.isNotBlank() })
    }
    val slug = when {
        parts.size == 2 && parts[0] == "inscripcion" -> parts[1]
        parts.size == 1 && parts[0] !in SitePaths -> parts[0]
        else -> return null
    }.lowercase()
    return if (SlugPattern.matches(slug)) RegistrationLink(slug = slug) else null
}

/** The registrations of the player app. Every change counts the `registration` event. See docs/registrations.md. */
class PlayerRegistrationPresenter(
    private val repository: RegistrationRepository,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    private fun <T> AppResult<T>.counted(action: String) = track(telemetry, TelemetryEvent.REGISTRATION, mapOf(TelemetryParam.ACTION to action))

    suspend fun loadList(tournamentId: String): AppResult<TournamentRegistrations> = repository.loadPublicList(tournamentId)

    suspend fun loadOwn(tournamentId: String): AppResult<OwnRegistration?> = repository.loadOwn(tournamentId)

    /** The EMA player of the signed-in account, or null without an account or without an EMA number. */
    suspend fun loadMe(registry: List<Player>): Player? {
        if (authRepository.currentSession() == null) return null
        val emaId = (authRepository.getMe(RefreshMode.IF_CHANGED) as? AppResult.Success)?.value?.emaId?.takeIf { it.isNotBlank() } ?: return null
        return registry.firstOrNull { it.emaId == emaId } ?: Player(emaId, "", "")
    }

    suspend fun loadRegistry(): AppResult<List<Player>> = playerRepository.listPlayers(RefreshMode.IF_CHANGED)

    suspend fun resolveLink(link: RegistrationLink): AppResult<String> {
        val tournamentId = link.tournamentId
        val token = link.token
        if (tournamentId != null && token != null) {
            return when (val result = repository.confirm(tournamentId, token).counted(TelemetryRegistrationAction.CONFIRMED)) {
                is AppResult.Success -> AppResult.Success(tournamentId)
                is AppResult.Failure -> result
            }
        }
        // The QR of a table of a tournament without a public link names the tournament.
        if (tournamentId != null && link.slug == null) return AppResult.Success(tournamentId)
        return repository.resolveLink(link.slug.orEmpty())
    }

    suspend fun register(tournamentId: String, request: RegisterRequest): AppResult<RegisterResult> = repository.register(tournamentId, request).counted(TelemetryRegistrationAction.REGISTERED)

    suspend fun saveFood(tournamentId: String, food: String, consent: Boolean): AppResult<OwnRegistration> = repository.updateOwn(tournamentId, food, consent).counted(TelemetryRegistrationAction.FOOD_UPDATED)

    suspend fun cancel(tournamentId: String): AppResult<Unit> = repository.cancelOwn(tournamentId).counted(TelemetryRegistrationAction.CANCELLED)

    suspend fun createTeam(tournamentId: String, name: String): AppResult<OwnRegistration> = repository.createTeam(tournamentId, name).counted(TelemetryRegistrationAction.TEAM_CREATED)

    suspend fun renameTeam(tournamentId: String, name: String): AppResult<OwnRegistration> = repository.renameTeam(tournamentId, name).counted(TelemetryRegistrationAction.TEAM_RENAMED)

    suspend fun leaveTeam(tournamentId: String): AppResult<OwnRegistration> = repository.leaveTeam(tournamentId).counted(TelemetryRegistrationAction.TEAM_LEFT)

    suspend fun addMember(tournamentId: String, emaId: String): AppResult<OwnRegistration> = repository.addTeamMember(tournamentId, emaId).counted(TelemetryRegistrationAction.TEAM_MEMBER_ADDED)

    suspend fun removeMember(tournamentId: String, emaId: String): AppResult<OwnRegistration> = repository.removeTeamMember(tournamentId, emaId).counted(TelemetryRegistrationAction.TEAM_MEMBER_REMOVED)

    suspend fun writeWithoutEma(tournamentId: String, request: NoEmaNumberRequest): AppResult<Unit> = repository.requestWithoutEmaNumber(tournamentId, request).counted(TelemetryRegistrationAction.NO_EMA_MESSAGE)
}

/** How often the list of registrations is read again while it is on screen. */
private const val REGISTRATIONS_REFRESH_MS = 60_000L

/**
 * The Players tab of a tournament before its draw: the people with a place, the waiting list, the button to register
 * and the own registration. Nothing private shows here: no email, no food restrictions and no registration date.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RegistrationsTab(tournament: Tournament) {
    val presenter = koinInject<PlayerRegistrationPresenter>()
    val scope = rememberCoroutineScope()
    var list by remember(tournament.id) { mutableStateOf<TournamentRegistrations?>(null) }
    var own by remember(tournament.id) { mutableStateOf<OwnRegistration?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var registering by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var noEma by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<UiText?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(tournament.id, reload) {
        while (true) {
            when (val result = presenter.loadList(tournament.id)) {
                is AppResult.Success -> list = result.value
                is AppResult.Failure -> if (list == null) error = result.error.toUiText()
            }
            (presenter.loadOwn(tournament.id) as? AppResult.Success)?.let { own = it.value }
            delay(REGISTRATIONS_REFRESH_MS)
        }
    }

    val current = list
    val settings = current?.settings ?: tournament.registration
    val normalized = normalizeSearchText(query.trim())
    val shown = current?.registrations.orEmpty().filter { row ->
        normalized.isEmpty() || normalizeSearchText("${row.name} ${row.emaId} ${row.country} ${current?.teamOf(row)?.name.orEmpty()}").contains(normalized)
    }
    error?.let { AppErrorDialog(it, onDismiss = { error = null }) }
    done?.let { message ->
        TrackedDialog("RegistrationDone") {
            AlertDialog(
                onDismissRequest = { done = null },
                text = { MtsDialogText(message.asString()) },
                confirmButton = { MtsDialogButtons { TextButton(onClick = { done = null }) { Text(stringResource(Res.string.preg_close)) } } },
            )
        }
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().scrollIndicators(listState, scrollbar = MtsScrollbar.Overlay),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "summary") {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val open = settings?.isOpen == true
                    Text(
                        stringResource(if (open) Res.string.preg_open else Res.string.preg_closed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (settings != null) {
                        Text(registrationPlacesText(settings.confirmed, settings.maxPlayers, settings.waiting))
                        settings.closesOn?.let { Text(stringResource(Res.string.preg_closes_on, formatLocalizedDate(it))) }
                    }
                    val mine = own
                    if (mine != null) {
                        Text(
                            when (mine.status) {
                                RegistrationStatus.CONFIRMED -> stringResource(Res.string.preg_status_place, mine.position)
                                RegistrationStatus.WAITING -> stringResource(Res.string.preg_status_waiting, mine.position)
                                RegistrationStatus.PENDING -> stringResource(Res.string.preg_status_pending)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (current?.isTeams == true && mine.teamId == null && mine.status != RegistrationStatus.PENDING) {
                            Text(stringResource(Res.string.preg_team_reminder), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (mine != null && mine.status != RegistrationStatus.PENDING) {
                            Button(onClick = { managing = true }, modifier = Modifier.testTag("registration-manage")) {
                                Text(stringResource(Res.string.preg_manage))
                            }
                        } else if (open && mine == null) {
                            Button(onClick = { registering = true }, modifier = Modifier.testTag("registration-register")) {
                                Icon(Icons.Default.HowToReg, contentDescription = null, Modifier.size(18.dp))
                                Text(stringResource(Res.string.preg_register), Modifier.padding(start = 8.dp))
                            }
                            TextButton(onClick = { noEma = true }) { Text(stringResource(Res.string.preg_no_ema)) }
                        }
                    }
                }
            }
        }
        if (current != null && current.registrations.isNotEmpty()) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.preg_search_ema)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (current != null && current.registrations.isEmpty()) {
            item(key = "empty") { Text(stringResource(Res.string.preg_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        } else if (current != null && shown.isEmpty()) {
            item(key = "no-match") { Text(stringResource(Res.string.preg_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }
        listOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITING).forEach { status ->
            val rows = shown.filter { it.status == status }
            if (rows.isNotEmpty()) {
                item(key = "title-$status") {
                    Text(
                        stringResource(if (status == RegistrationStatus.CONFIRMED) Res.string.preg_title_confirmed else Res.string.preg_title_waiting, rows.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(rows, key = { "$status-${it.emaId}" }) { row ->
                    RegistrationLine(row, current?.teamOf(row)?.name, isTeams = current?.isTeams == true, isMe = row.emaId == own?.emaId)
                }
            }
        }
    }
    if (registering) {
        RegisterDialog(
            tournament = tournament,
            onDone = { result ->
                registering = false
                done = when (result.status) {
                    RegistrationStatus.PENDING -> UiText.of(Res.string.preg_done_pending, result.emailHint)
                    RegistrationStatus.WAITING -> UiText.of(Res.string.preg_done_waiting, result.position ?: 0)
                    RegistrationStatus.CONFIRMED -> UiText.of(Res.string.preg_done_place, result.position ?: 0)
                }
                reload++
            },
            onDismiss = { registering = false },
        )
    }
    if (managing) {
        own?.let { mine ->
            ManageRegistrationDialog(
                tournamentId = tournament.id,
                own = mine,
                list = current,
                onChanged = { changed ->
                    own = changed
                    reload++
                },
                onCancelled = {
                    managing = false
                    own = null
                    reload++
                },
                onDismiss = { managing = false },
            )
        }
    }
    if (noEma) {
        NoEmaNumberDialog(
            onSend = { request ->
                scope.launch {
                    when (val result = presenter.writeWithoutEma(tournament.id, request)) {
                        is AppResult.Success -> {
                            noEma = false
                            done = UiText.of(Res.string.preg_no_ema_sent)
                        }
                        is AppResult.Failure -> error = result.error.toUiText()
                    }
                }
            },
            onDismiss = { noEma = false },
        )
    }
}

/** "12 with a place of 60 · 3 on the waiting list". */
@Composable
internal fun registrationPlacesText(confirmed: Int, maxPlayers: Int?, waiting: Int): String = stringResource(Res.string.preg_places, confirmed, maxPlayers?.toString() ?: "∞", waiting)

@Composable
private fun RegistrationLine(row: PublicRegistration, teamName: String?, isTeams: Boolean, isMe: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(row.position.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 28.dp))
            CountryFlag(code = row.country, width = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (isMe) stringResource(Res.string.preg_me, row.name) else row.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(row.emaId, if (isTeams) teamName ?: stringResource(Res.string.preg_without_team) else null)
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The registration. With an account that has an EMA number the person registers itself at once. Without one, the
 * person picks its EMA number and writes an email, which gets a link to confirm.
 */
@Composable
private fun RegisterDialog(tournament: Tournament, onDone: (RegisterResult) -> Unit, onDismiss: () -> Unit) {
    val presenter = koinInject<PlayerRegistrationPresenter>()
    val scope = rememberCoroutineScope()
    var registry by remember { mutableStateOf<List<Player>>(emptyList()) }
    var me by remember { mutableStateOf<Player?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Player?>(null) }
    var query by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var food by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    LaunchedEffect(Unit) {
        val players = (presenter.loadRegistry() as? AppResult.Success)?.value.orEmpty()
        registry = players
        me = presenter.loadMe(players)
        loaded = true
    }
    error?.let { AppErrorDialog(it, onDismiss = { error = null }) }
    val person = me ?: picked
    TrackedDialog("Register") {
        AlertDialog(
            onDismissRequest = { if (!busy) onDismiss() },
            title = { MtsDialogTitle(stringResource(Res.string.preg_register_title), subtitle = tournament.name) },
            text = {
                Column(Modifier.verticalScrollWithIndicators(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!loaded || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val signedInPerson = me
                    if (signedInPerson != null) {
                        MtsDialogText(stringResource(Res.string.preg_register_as, signedInPerson.displayName.ifBlank { signedInPerson.emaId }, signedInPerson.emaId))
                    } else if (loaded) {
                        Text(stringResource(Res.string.preg_register_info), style = MaterialTheme.typography.bodySmall)
                        val chosen = picked
                        if (chosen != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CountryFlag(code = chosen.country, width = 20.dp)
                                Text("${chosen.displayName} · ${chosen.emaId}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { picked = null }) { Text(stringResource(Res.string.action_cancel)) }
                            }
                        } else {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text(stringResource(Res.string.preg_search_ema)) },
                                placeholder = { Text(stringResource(Res.string.preg_pick_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("registration-ema-search"),
                            )
                            val terms = normalizeSearchText(query.trim()).split(' ').filter { it.isNotEmpty() }
                            if (terms.isNotEmpty()) {
                                val matches = registry.filter { player ->
                                    val text = normalizeSearchText("${player.displayName} ${player.emaId}")
                                    terms.all { it in text }
                                }.take(8)
                                Column(Modifier.heightIn(max = 280.dp)) {
                                    matches.forEach { player ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable { picked = player }.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CountryFlag(code = player.country, width = 20.dp)
                                            Text(player.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(player.emaId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        HorizontalDivider()
                                    }
                                    if (matches.isEmpty()) Text(stringResource(Res.string.preg_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trim() },
                            label = { Text(stringResource(Res.string.preg_email)) },
                            supportingText = { Text(stringResource(Res.string.preg_email_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (loaded) FoodFields(food, { food = it }, consent, { consent = it })
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(
                        enabled = !busy && person != null && (me != null || email.contains('@')) && (food.isBlank() || consent),
                        onClick = {
                            val chosen = person ?: return@Button
                            busy = true
                            scope.launch {
                                when (val result = presenter.register(tournament.id, RegisterRequest(chosen.emaId, email, food, consent))) {
                                    is AppResult.Success -> onDone(result.value)
                                    is AppResult.Failure -> error = result.error.toUiText()
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.testTag("registration-submit"),
                    ) { Text(stringResource(Res.string.preg_register)) }
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}

/** The private field of the food restrictions, with the consent that it needs. Only the organizers see it. */
@Composable
private fun FoodFields(food: String, onFood: (String) -> Unit, consent: Boolean, onConsent: (Boolean) -> Unit) {
    OutlinedTextField(
        value = food,
        onValueChange = { onFood(it.take(MAX_FOOD_RESTRICTIONS_LENGTH)) },
        label = { Text(stringResource(Res.string.preg_food)) },
        supportingText = { Text(stringResource(Res.string.preg_food_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (food.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = onConsent, modifier = Modifier.testTag("registration-consent"))
            Text(stringResource(Res.string.preg_consent), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The own registration: the food restrictions, the team, and the cancellation. */
@Composable
private fun ManageRegistrationDialog(
    tournamentId: String,
    own: OwnRegistration,
    list: TournamentRegistrations?,
    onChanged: (OwnRegistration) -> Unit,
    onCancelled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val presenter = koinInject<PlayerRegistrationPresenter>()
    val scope = rememberCoroutineScope()
    var food by remember(own.emaId) { mutableStateOf(own.foodRestrictions) }
    var consent by remember(own.emaId) { mutableStateOf(own.foodRestrictions.isNotBlank()) }
    var teamName by remember(own.teamId) { mutableStateOf(list?.teams?.firstOrNull { it.id == own.teamId }?.name.orEmpty()) }
    var member by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    val team = list?.teams?.firstOrNull { it.id == own.teamId }
    val isCaptain = team?.captainEmaId == own.emaId

    fun run(block: suspend () -> AppResult<OwnRegistration>) = scope.launch {
        busy = true
        when (val result = block()) {
            is AppResult.Success -> onChanged(result.value)
            is AppResult.Failure -> error = result.error.toUiText()
        }
        busy = false
    }
    error?.let { AppErrorDialog(it, onDismiss = { error = null }) }
    if (confirmCancel) {
        TrackedDialog("CancelRegistration") {
            AlertDialog(
                onDismissRequest = { confirmCancel = false },
                title = { MtsDialogTitle(stringResource(Res.string.preg_cancel_title)) },
                text = { MtsDialogText(stringResource(Res.string.preg_cancel_message)) },
                confirmButton = {
                    MtsDialogButtons {
                        Button(
                            colors = MtsButtons.destructiveColors(),
                            onClick = {
                                confirmCancel = false
                                scope.launch {
                                    when (val result = presenter.cancel(tournamentId)) {
                                        is AppResult.Success -> onCancelled()
                                        is AppResult.Failure -> error = result.error.toUiText()
                                    }
                                }
                            },
                        ) { Text(stringResource(Res.string.preg_cancel_confirm)) }
                        TextButton(onClick = { confirmCancel = false }) { Text(stringResource(Res.string.action_cancel)) }
                    }
                },
            )
        }
        return
    }
    TrackedDialog("ManageRegistration") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.preg_manage_title), subtitle = "${own.name} · ${own.emaId}") },
            text = {
                Column(Modifier.verticalScrollWithIndicators(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    FoodFields(food, { food = it }, consent, { consent = it })
                    TextButton(
                        enabled = !busy && (food.isBlank() || consent) && food != own.foodRestrictions,
                        onClick = { run { presenter.saveFood(tournamentId, food, consent) } },
                    ) { Text(stringResource(Res.string.preg_save)) }
                    if (list?.isTeams == true) {
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Groups, contentDescription = null)
                            Text(stringResource(Res.string.preg_team), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(stringResource(Res.string.preg_team_info, REGISTRATION_TEAM_SIZE), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = teamName,
                            onValueChange = { teamName = it.take(MAX_REGISTRATION_TEAM_NAME_LENGTH) },
                            label = { Text(stringResource(Res.string.preg_team_name)) },
                            singleLine = true,
                            enabled = team == null || isCaptain,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (team == null) {
                            TextButton(enabled = !busy && teamName.isNotBlank(), onClick = { run { presenter.createTeam(tournamentId, teamName) } }) {
                                Text(stringResource(Res.string.preg_create_team))
                            }
                        } else {
                            if (isCaptain && teamName.isNotBlank() && teamName != team.name) {
                                TextButton(enabled = !busy, onClick = { run { presenter.renameTeam(tournamentId, teamName) } }) { Text(stringResource(Res.string.preg_rename_team)) }
                            }
                            team.members.forEach { emaId ->
                                val name = list.registrations.firstOrNull { it.emaId == emaId }?.name ?: emaId
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (emaId == team.captainEmaId) stringResource(Res.string.preg_captain, name) else name,
                                        Modifier.weight(1f),
                                    )
                                    if (isCaptain && emaId != own.emaId) {
                                        TextButton(enabled = !busy, onClick = { run { presenter.removeMember(tournamentId, emaId) } }) {
                                            Text(stringResource(Res.string.preg_remove_member))
                                        }
                                    }
                                }
                            }
                            if (isCaptain && team.members.size < REGISTRATION_TEAM_SIZE) {
                                OutlinedTextField(
                                    value = member,
                                    onValueChange = { member = it.filter(Char::isDigit).take(8) },
                                    label = { Text(stringResource(Res.string.preg_add_member)) },
                                    supportingText = { Text(stringResource(Res.string.preg_add_member_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(enabled = !busy && member.length == 8, onClick = {
                                    val emaId = member
                                    member = ""
                                    run { presenter.addMember(tournamentId, emaId) }
                                }) { Text(stringResource(Res.string.preg_add_member)) }
                            }
                            TextButton(enabled = !busy, onClick = { run { presenter.leaveTeam(tournamentId) } }) { Text(stringResource(Res.string.preg_leave_team)) }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.preg_close)) }
                    OutlinedButton(
                        onClick = { confirmCancel = true },
                        colors = MtsButtons.destructiveOutlinedColors(),
                        border = MtsButtons.destructiveBorder(),
                        modifier = Modifier.testTag("registration-cancel"),
                    ) { Text(stringResource(Res.string.preg_cancel)) }
                }
            },
        )
    }
}

/** A person without an EMA number writes to the organization, which creates the EMA player and registers it. */
@Composable
private fun NoEmaNumberDialog(onSend: (NoEmaNumberRequest) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    TrackedDialog("NoEmaNumber") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.preg_no_ema_title)) },
            text = {
                Column(Modifier.verticalScrollWithIndicators(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.preg_no_ema_info), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(Res.string.preg_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(country, { country = it.take(60) }, label = { Text(stringResource(Res.string.preg_no_ema_country)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it.trim() }, label = { Text(stringResource(Res.string.preg_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(message, { message = it.take(1000) }, label = { Text(stringResource(Res.string.preg_message)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(
                        enabled = name.trim().length >= 2 && email.contains('@'),
                        onClick = { onSend(NoEmaNumberRequest(name, country, email, message)) },
                    ) { Text(stringResource(Res.string.preg_send)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}

/** Opens a registration link of the web after the splash: it confirms the email of a link of the emails. */
@Composable
internal fun rememberRegistrationLinkOpener(navController: NavHostController): suspend (RegistrationLink) -> Unit {
    val presenter = koinInject<PlayerRegistrationPresenter>()
    val tables = koinInject<TableQrLinkPresenter>()
    return { link ->
        val tournamentId = when (val result = presenter.resolveLink(link)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> link.tournamentId
        }
        if (tournamentId != null) {
            navController.navigate(TournamentRoute(tournamentId))
            // The QR of a physical table opens that table over the tournament, so Back shows the tournament.
            link.tableId?.let { tables.find(tournamentId, it) }?.let { navController.navigate(it) }
        }
    }
}
