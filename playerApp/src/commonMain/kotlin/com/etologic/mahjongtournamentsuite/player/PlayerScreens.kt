package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import com.etologic.mahjongtournamentsuite.presentation.components.SearchableDropdownField
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentInt
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.etologic.mahjongtournamentsuite.presentation.components.SignInForm
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.platform.loadBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBrowserCredentials
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.saveable.rememberSaveable
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Directions
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Groups
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.domain.model.seatScores
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.HandLabel
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.LastPlaceIcon
import com.etologic.mahjongtournamentsuite.presentation.components.PositionTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.positionColor
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SeatDisplay
import com.etologic.mahjongtournamentsuite.presentation.components.SeatLevelMedal
import com.etologic.mahjongtournamentsuite.presentation.components.TableSeatsView
import kotlin.time.Clock
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDateRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The interval of the checks for new results while a tournament screen is open. */
private const val REFRESH_INTERVAL_MS = 15_000L

@Composable
fun SplashScreen(
    navController: NavHostController,
    inviteToken: String?,
    openTournamentId: String?,
    /** The screens that were open before a page refresh, from the first to the last. */
    restoredRoutes: List<Any> = emptyList(),
) {
    val presenter = koinInject<PlayerSessionPresenter>()
    LaunchedEffect(Unit) {
        val next: Any = when {
            inviteToken != null -> InviteRoute(inviteToken)
            presenter.hasSession() -> TournamentsRoute
            else -> SignInRoute(openTournamentId = openTournamentId)
        }
        navController.navigate(next) { popUpTo(SplashRoute) { inclusive = true } }
        if (next == TournamentsRoute) {
            val restored = restoredRoutes.filterNot { it == TournamentsRoute }
            if (restored.isNotEmpty()) {
                // A page refresh returns to the screen that was open.
                restored.forEach { navController.navigate(it) }
            } else if (openTournamentId != null) {
                // The link of a registration email goes on to the tournament. Back returns to the list.
                navController.navigate(TournamentRoute(openTournamentId))
            }
        }
    }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
}

@Composable
fun SignInScreen(navController: NavHostController, inviteToken: String?, openTournamentId: String?) {
    val presenter = koinInject<PlayerSessionPresenter>()
    val scope = rememberCoroutineScope()
    val emailRequester = remember { FocusRequester() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var backendVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        backendVersion = presenter.backendVersion()
        // Chrome mediates this request through its password manager UI.
        loadBrowserCredentials { savedEmail, savedPassword ->
            email = savedEmail
            password = savedPassword
        }
        emailRequester.requestFocus()
    }

    fun submit() {
        errorMessage = null
        infoMessage = null
        busy = true
        scope.launch {
            when (val result = presenter.signIn(email, password)) {
                is AppResult.Success -> {
                    saveBrowserCredentials(email.trim(), password)
                    val next: Any = if (inviteToken != null) InviteRoute(inviteToken) else TournamentsRoute
                    navController.navigate(next) { popUpTo(0) }
                    if (inviteToken == null && openTournamentId != null) {
                        navController.navigate(TournamentRoute(openTournamentId))
                    }
                }
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
            busy = false
        }
    }

    fun requestPasswordReset() {
        errorMessage = null
        infoMessage = null
        if (email.trim().isEmpty()) {
            errorMessage = "Enter your email address first."
            emailRequester.requestFocus()
            return
        }
        busy = true
        scope.launch {
            when (val result = presenter.resetPassword(email)) {
                is AppResult.Success ->
                    infoMessage = "If an account exists, we sent password reset instructions to your email."
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
            busy = false
        }
    }

    SignInForm(
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        isLoading = busy,
        errorMessage = errorMessage,
        infoMessage = infoMessage,
        backendVersion = backendVersion,
        emailRequester = emailRequester,
        onSubmit = ::submit,
        onForgotPassword = ::requestPasswordReset,
        onErrorDismiss = { errorMessage = null },
        description = "Use the email of your invitation to sign in.",
    )
}

@Composable
fun InviteScreen(navController: NavHostController, token: String) {
    val presenter = koinInject<PlayerSessionPresenter>()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasSession by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { hasSession = presenter.hasSession() }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Accept your invitation", style = MaterialTheme.typography.headlineMedium)
        if (hasSession == true) {
            Text("You are signed in. Accept the invitation with this account.")
        } else {
            Text("Choose a password for your new account. If you already have an account, sign in first.")
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it }, label = { Text("Repeat password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val passwordsOk = hasSession == true || (password.length >= 8 && password == confirm)
        Button(
            enabled = !busy && hasSession != null && passwordsOk,
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    val sent = if (hasSession == true) null else password
                    when (val result = presenter.acceptInvitation(token, sent)) {
                        is AppResult.Success -> {
                            if (hasSession == true) {
                                navController.navigate(TournamentsRoute) { popUpTo(0) }
                            } else {
                                // The account exists now. The player signs in with the new password.
                                when (presenter.signIn(result.value, password)) {
                                    is AppResult.Success -> navController.navigate(TournamentsRoute) { popUpTo(0) }
                                    is AppResult.Failure -> navController.navigate(SignInRoute()) { popUpTo(0) }
                                }
                            }
                        }
                        is AppResult.Failure -> message = result.error.toUiMessage()
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Accept invitation") }
        if (hasSession == false) {
            TextButton(onClick = { navController.navigate(SignInRoute(inviteToken = token)) }) {
                Text("I already have an account")
            }
        }
    }
}

@Composable
fun TournamentsScreen(navController: NavHostController) {
    val tournaments = koinInject<PlayerTournamentPresenter>()
    val session = koinInject<PlayerSessionPresenter>()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Tournament>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // The place of the player in each tournament that started. It fills in while the list is on screen.
    var standings by remember { mutableStateOf<Map<String, TournamentStanding>>(emptyMap()) }
    var tab by rememberPersistentInt("tournaments-tab", 0)
    // The statistics tab tells this screen when it loads, so the progress bar under the top bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val result = tournaments.listTournaments(RefreshMode.FORCE)) {
            is AppResult.Success -> items = result.value
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
    }
    LaunchedEffect(items) {
        items.orEmpty().filter { statusOf(it).let { status -> status != null && status != TournamentStatus.UPCOMING } }.forEach { tournament ->
            val view = (tournaments.load(tournament.id) as? AppResult.Success)?.value ?: return@forEach
            val ranking = view.rankings.players.firstOrNull { it.playerId == view.mySlotId } ?: return@forEach
            val finished = statusOf(tournament) == TournamentStatus.FINISHED
            val rankings = view.rankings
            // The extra icons show only when the tournament is over and the player came first.
            val standing = TournamentStanding(
                position = ranking.position,
                wonTeams = finished && tournament.isTeams &&
                    rankings.teams.any { it.position == 1 && it.teamId == ranking.teamId },
                wonChickenHands = finished && tournament.countChickenHands &&
                    rankings.chickenHands.any { it.position == 1 && it.playerId == ranking.playerId },
                wonBestHands = finished && tournament.countBestHands &&
                    rankings.bestHands.any { it.position == 1 && it.playerId == ranking.playerId },
            )
            standings = standings + (tournament.id to standing)
        }
    }

    AppScaffold(
        title = "My tournaments",
        isLoading = (items == null && message == null) || (tab == 1 && statisticsLoading),
        autoFocusFirst = false,
        navigationIcon = {
            SignOutAction {
                scope.launch {
                    session.signOut()
                    navController.navigate(SignInRoute()) { popUpTo(0) }
                }
            }
        },
        actions = { ThemeAction() },
    ) {
        // The tab row touches the top bar, so the screen has no padding. The content under the tabs has its own.
        // The screen uses the full width. The tab row and the tournament list keep a narrow column. The statistics use all the width.
        ScreenColumn(maxWidth = Dp.Infinity, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // The tabs use the color of the top bar.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = tabRowColor(), contentColor = TabContentColor,
                indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), color = TabContentColor) },
                modifier = Modifier.widthIn(max = 720.dp),
            ) {
                listOf("My tournaments", "Statistics").forEachIndexed { index, title ->
                    CompactTab(
                        selected = tab == index,
                        onClick = { tab = index },
                        title = title,
                        icon = {
                            Icon(
                                if (index == 0) Icons.Default.EmojiEvents else StatisticsIcon,
                                contentDescription = null,
                                Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
            }
            Column(
                Modifier.weight(1f).align(Alignment.CenterHorizontally)
                    .then(if (tab == 1) Modifier.fillMaxWidth() else Modifier.widthIn(max = 720.dp).fillMaxWidth())
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 1) {
                    MyStatisticsTab { statisticsLoading = it }
                    return@Column
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val list = items
                when {
                    list == null -> Unit
                    list.isEmpty() -> EmptyState("You are not registered in any tournament yet.")
                    else -> LazyColumn(
                        state = rememberPersistentLazyListState("tournaments"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list, key = { it.id }) { tournament ->
                            TournamentCard(tournament, standings[tournament.id]) { navController.navigate(TournamentRoute(tournament.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
}

private enum class TournamentStatus(val label: String) { UPCOMING("Upcoming"), IN_PROGRESS("In progress"), FINISHED("Finished") }

/** The place of the player in a tournament, and the rankings that the player won. */
private data class TournamentStanding(
    val position: Int,
    val wonTeams: Boolean = false,
    val wonChickenHands: Boolean = false,
    val wonBestHands: Boolean = false,
)

/** The status comes from the dates. It is null when the tournament has no dates. */
private fun statusOf(tournament: Tournament): TournamentStatus? {
    if (tournament.isCompleted) return TournamentStatus.FINISHED
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val start = tournament.eventStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = tournament.eventEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
    return when {
        start == null -> null
        today < start -> TournamentStatus.UPCOMING
        end != null && today > end -> TournamentStatus.FINISHED
        else -> TournamentStatus.IN_PROGRESS
    }
}

/** A small outlined label with square corners, so it does not look like a button. */
@Composable
private fun OutlinedTag(color: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .border(BorderStroke(1.dp, color), RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun StatusChip(status: TournamentStatus) {
    val color = when (status) {
        TournamentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        TournamentStatus.UPCOMING -> MaterialTheme.colorScheme.primary
        TournamentStatus.FINISHED -> MaterialTheme.colorScheme.secondary
    }
    OutlinedTag(color) {
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** The place of the player: a trophy or a leaderboard icon and the number. */
@Composable
private fun StandingTag(standing: TournamentStanding) {
    val color = MaterialTheme.colorScheme.onSurface
    OutlinedTag(color) {
        if (standing.position <= 3) {
            PositionTrophy(standing.position, size = 18.dp)
        } else {
            Icon(Icons.Default.Leaderboard, contentDescription = "Place", Modifier.size(18.dp), tint = color)
        }
        Text("${standing.position}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/** The logo of the tournament. A neutral box with a trophy shows while the tournament has no logo. */
@Composable
private fun TournamentLogo(tournament: Tournament) {
    val shape = RoundedCornerShape(10.dp)
    val modifier = Modifier.size(56.dp).clip(shape)
    if (tournament.associationLogoUrl.isNullOrBlank()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = tournament.associationLogoUrl,
            contentDescription = "Logo of ${tournament.name}",
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

@Composable
private fun TournamentCard(tournament: Tournament, standing: TournamentStanding?, onClick: () -> Unit) {
    var expanded by remember(tournament.id) { mutableStateOf(false) }
    val status = statusOf(tournament)
    val uriHandler = LocalUriHandler.current
    val venueText = venueSummary(tournament)
    val mapLink = venueMapLink(tournament)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TournamentLogo(tournament)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tournament.name, style = MaterialTheme.typography.titleMedium)
                val dates = datesOf(tournament)
                if (dates != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(dates, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (tournament.hostCity.isNotBlank() || tournament.hostCountry.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Place, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        if (tournament.hostCity.isNotBlank()) {
                            Text(tournament.hostCity, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (tournament.hostCountry.isNotBlank()) {
                            CountryFlag(code = tournament.hostCountry, contentDescription = tournament.hostCountry)
                        }
                    }
                }
                if (venueText.isNotBlank() && mapLink != null) {
                    // The venue opens Google Maps in another tab. The icon shows that it is a link.
                    Row(
                        Modifier.clickable { uriHandler.openUri(mapLink) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = "Open directions in Google Maps",
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            venueText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                val email = tournament.venue.contactEmail
                if (email.isNotBlank()) {
                    Row(
                        Modifier.clickable { uriHandler.openUri("mailto:$email") },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(email, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (hasSchedule(tournament)) {
                    Row(
                        Modifier.clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (expanded) "Hide schedule" else "Show schedule",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (expanded) TournamentSchedule(tournament)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (status != null) StatusChip(status)
                if (standing != null) {
                    StandingTag(standing)
                    if (standing.wonTeams || standing.wonChickenHands || standing.wonBestHands) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (standing.wonTeams) {
                                Icon(Icons.Default.Groups, contentDescription = "Won the teams ranking", Modifier.size(22.dp))
                            }
                            if (standing.wonChickenHands) ChickenIcon(size = 22.dp)
                            if (standing.wonBestHands) PositionTrophy(1, size = 22.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Loads the tournament and checks for changes at a fixed interval while the screen is open. */
@Composable
private fun rememberTournamentView(tournamentId: String, reloadKey: Int = 0): Pair<PlayerTournamentView?, String?> {
    val presenter = koinInject<PlayerTournamentPresenter>()
    var view by remember(tournamentId) { mutableStateOf<PlayerTournamentView?>(null) }
    var message by remember(tournamentId) { mutableStateOf<String?>(null) }
    LaunchedEffect(tournamentId, reloadKey) {
        while (true) {
            when (val result = presenter.load(tournamentId, if (reloadKey > 0) RefreshMode.FORCE else RefreshMode.IF_CHANGED)) {
                is AppResult.Success -> {
                    view = result.value
                    message = null
                }
                is AppResult.Failure -> message = result.error.toUiMessage()
            }
            delay(REFRESH_INTERVAL_MS)
        }
    }
    return view to message
}

@Composable
fun TournamentScreen(navController: NavHostController, tournamentId: String) {
    val (view, message) = rememberTournamentView(tournamentId)
    // An account that is not linked to a player has no own tables. It starts on All tables.
    // A negative value means that the player did not choose a tab.
    var chosenTab by rememberPersistentInt("tab", -1)
    val tab = chosenTab.takeIf { it >= 0 } ?: if (view?.mySlotId == null) 1 else 0
    // The statistics tab tells this screen when it loads, so the progress bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }

    AppScaffold(
        title = view?.tournament?.name ?: "Tournament",
        subtitle = view?.let(::progressOf),
        isLoading = (view == null && message == null) || (tab == 3 && statisticsLoading),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = { ThemeAction() },
    ) {
        // The tab row touches the top bar, so the screen has no padding. The content under the tabs has its own.
        ScreenColumn(maxWidth = 720.dp, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (view == null) {
                message?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                return@ScreenColumn
            }
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = tabRowColor(), contentColor = TabContentColor,
                indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), color = TabContentColor) },
            ) {
                listOf("My tables", "All tables", "Ranking", "Statistics", "Info").forEachIndexed { index, title ->
                    CompactTab(
                        selected = tab == index,
                        onClick = { chosenTab = index },
                        title = title,
                        icon = {
                            val icon = when (index) {
                                0 -> Icons.Default.Person
                                1 -> Icons.Default.People
                                2 -> Icons.Default.Leaderboard
                                3 -> StatisticsIcon
                                else -> Icons.Default.Info
                            }
                            Icon(icon, contentDescription = null, Modifier.size(20.dp))
                        },
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val open: (PlayerTable) -> Unit = { navController.navigate(TableRoute(tournamentId, it.roundId, it.tableId)) }
                when (tab) {
                    0 -> TableList(
                        view,
                        view.myTables,
                        if (view.mySlotId == null) {
                            "Your account is not linked to a player of this tournament. " +
                                "Ask the organizers to send you an invitation. All tables are in the next tab."
                        } else {
                            "You have no table yet."
                        },
                        open,
                    )
                    1 -> TableList(view, view.tables, "No tables yet.", open)
                    2 -> RankingTab(view) { navController.navigate(PlayerTablesRoute(tournamentId, it)) }
                    3 -> TournamentStatisticsTab(navController, tournamentId, view.mySlotId) { statisticsLoading = it }
                    else -> TournamentInfoTab(view.tournament)
                }
            }
        }
    }
}

private fun datesOf(tournament: Tournament): String? =
    formatLocalizedDateRange(tournament.eventStartDate, tournament.eventEndDate)

/** How far the tournament is, for the subtitle of the top bar. Example: "2 of 5 rounds - 8 of 20 tables - 3 ongoing". */
private fun progressOf(view: PlayerTournamentView): String {
    val rounds = view.tournament.numRounds
    val tablesPerRound = view.tournament.numPlayers / 4
    val completedTables = view.tables.count { it.isComplete }
    val ongoingTables = view.tables.count { !it.isComplete && it.doneHands > 0 }
    val completedRounds = (1..rounds).count { round ->
        tablesPerRound > 0 && view.tables.count { it.roundId == round && it.isComplete } >= tablesPerRound
    }
    return listOf(
        "$completedRounds of $rounds rounds",
        "$completedTables of ${rounds * tablesPerRound} tables",
        "$ongoingTables ongoing",
    ).joinToString(" - ")
}

/** The four players of a table in the order of hand 1 (East, South, West, North). */
private fun PlayerTable.seatsFor(view: PlayerTournamentView, withLevelChange: Boolean = false): List<SeatDisplay> {
    val t = table
    fun penalty(select: (TableHand) -> String) = hands.sumOf { select(it).trim().toIntOrNull() ?: 0 }
    fun seat(id: String, score: String, points: String, penalty: Int): SeatDisplay {
        val slot = id.toIntOrNull()
        val after = if (withLevelChange) view.statisticsAfter(slot, roundId) else null
        return SeatDisplay(
            name = view.nameOf(slot),
            country = slot?.let { view.players[it]?.country }.orEmpty(),
            score = score,
            points = points,
            penalty = penalty,
            isMine = slot != null && slot == view.mySlotId,
            slotId = slot,
            // The table screen shows the level after this round. The table list shows the level now.
            level = after?.level ?: if (withLevelChange) null else view.levelOf(slot),
            skillScore = after?.skillScore ?: if (withLevelChange) null else view.skillScoreOf(slot),
            levelProvisional = after?.isLevelProvisional ?: view.isLevelProvisional(slot),
            levelChange = if (withLevelChange && table.isCompleted) view.levelChangeAt(slot, roundId) else null,
        )
    }
    // The table points come only from the scores of the table.
    val scores = t.seatScores
    val points = t.seatPoints
    return listOf(
        seat(t.playerEastId, scores[0], points[0], penalty { it.playerEastPenalty }),
        seat(t.playerSouthId, scores[1], points[1], penalty { it.playerSouthPenalty }),
        seat(t.playerWestId, scores[2], points[2], penalty { it.playerWestPenalty }),
        seat(t.playerNorthId, scores[3], points[3], penalty { it.playerNorthPenalty }),
    )
}

@Composable
private fun TableList(
    view: PlayerTournamentView,
    tables: List<PlayerTable>,
    emptyText: String,
    onOpen: (PlayerTable) -> Unit,
) {
    if (tables.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    val rounds = tables.sortedBy { it.roundId }.groupBy { it.roundId }
    LazyColumn(
        state = rememberPersistentLazyListState("tables-" + emptyText),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        rounds.forEach { (roundId, roundTables) ->
            // The title of a round stays at the top while its tables scroll under it.
            stickyHeader(key = "round_$roundId") { RoundHeader(roundId, view.roundStartOf(roundId)) }
            items(roundTables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                TableSummaryCard(view, table, highlightSlot = view.mySlotId, onClick = { onOpen(table) })
            }
        }
    }
}

/** The title of a group of tables. It has a solid background, so the tables do not show through it. */
@Composable
private fun RoundHeader(roundId: Int, start: String?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.background).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Round $roundId",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        if (start != null) {
            Icon(Icons.Default.Schedule, contentDescription = null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            Text(start, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** A table with its four players. The points of the table show only for a complete table. */
@Composable
private fun TableSummaryCard(view: PlayerTournamentView, table: PlayerTable, highlightSlot: Int?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val complete = table.isComplete
    val ongoing = !complete && table.doneHands > 0
    // The header color shows the state of the table: finished, ongoing or not started.
    val headerColor = when {
        complete -> colors.primaryContainer
        ongoing -> colors.tertiaryContainer
        else -> colors.surfaceContainerHighest
    }
    val headerContent = when {
        complete -> colors.onPrimaryContainer
        ongoing -> colors.onTertiaryContainer
        else -> colors.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().background(headerColor).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Table ${table.tableId}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = headerContent,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val statusIcon = when {
                    complete -> Icons.Default.CheckCircle
                    ongoing -> Icons.Default.PlayArrow
                    else -> Icons.Default.HourglassEmpty
                }
                Icon(statusIcon, contentDescription = null, Modifier.size(20.dp), tint = headerContent)
                Text(
                    when {
                        complete -> "Finished"
                        else -> "${table.doneHands}/${table.hands.size} hands"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = headerContent,
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Seat order (east to north) while the table is open. Winner to last when it is complete.
            val seats = table.seatsFor(view).let { all ->
                if (complete) {
                    // The table points come from the scores and use a decimal comma ("1,5"), so sort by score.
                    all.sortedByDescending { it.score.trim().toIntOrNull() ?: Int.MIN_VALUE }
                } else {
                    all
                }
            }
            seats.forEachIndexed { position, seat ->
                val highlighted = seat.slotId != null && seat.slotId == highlightSlot
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (highlighted) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (complete) {
                        // Seats are sorted by result here. The fourth place has a sad face.
                        if (position < 3) PositionTrophy(position + 1, size = 24.dp) else LastPlaceIcon(size = 24.dp)
                    }
                    PlayerFlag(seat.country)
                    SeatLevelMedal(seat, size = 24.dp)
                    Text(
                        seat.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (highlighted) FontWeight.Bold else null,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (complete && seat.points.isNotBlank()) {
                        // The table points have the same outlined style as in the ranking.
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, colors.onSurface),
                        ) {
                            Text(
                                formatPoints(seat.points),
                                Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        seat.score.ifBlank { "-" },
                        Modifier.width(56.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** [short] is the label of the button of the ranking. It is short because the buttons share a row with the search. */
private enum class RankingKind(val title: String, val short: String) {
    PLAYERS("Players", "Players"),
    TEAMS("Teams", "Teams"),
    CHICKEN("Chicken hands", "Chicken"),
    BEST("Best hands", "Best"),
}

/** One line of a ranking list. The chip is the main number, outlined. The score is the plain number next to it. */
private data class RankingRow(
    val key: String,
    val position: Int,
    val title: String,
    val subtitle: String?,
    /** The country code of the player. The line shows its flag next to the name. */
    val country: String? = null,
    val chip: String,
    val chipChicken: Boolean = false,
    val score: String? = null,
    val mine: Boolean = false,
    val playerId: Int? = null,
    /** The players of a team, by score. Not empty only for a team. The row can be folded. */
    val members: List<RankingRow> = emptyList(),
)

/** The ranking tab. A second row of tabs shows the rankings that the tournament has. */
@Composable
private fun RankingTab(view: PlayerTournamentView, onPlayerClick: (Int) -> Unit) {
    val rankings = view.rankings
    val kinds = buildList {
        add(RankingKind.PLAYERS)
        if (view.tournament.isTeams && rankings.teams.isNotEmpty()) add(RankingKind.TEAMS)
        if (view.tournament.countChickenHands && rankings.chickenHands.isNotEmpty()) add(RankingKind.CHICKEN)
        if (view.tournament.countBestHands && rankings.bestHands.isNotEmpty()) add(RankingKind.BEST)
    }
    var chosen by remember { mutableStateOf(RankingKind.PLAYERS) }
    val kind = chosen.takeIf { it in kinds } ?: RankingKind.PLAYERS
    val myTeam = rankings.players.firstOrNull { it.playerId == view.mySlotId }?.teamId

    val rows = when (kind) {
        RankingKind.PLAYERS -> rankings.players.map { row ->
            RankingRow(
                key = "p${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = null,
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
        RankingKind.TEAMS -> rankings.teams.map { row ->
            val members = rankings.players
                .filter { it.teamId == row.teamId }
                .sortedWith(compareByDescending<PlayerRanking> { it.score }.thenByDescending { it.points })
                .map { member ->
                    RankingRow(
                        key = "t${row.teamId}_p${member.playerId}",
                        position = member.position,
                        title = view.nameOf(member.playerId),
                        subtitle = null,
                        country = view.players[member.playerId]?.country?.takeIf { it.isNotBlank() },
                        chip = formatPoints(member.points),
                        score = "${member.score}",
                        mine = member.playerId == view.mySlotId,
                        playerId = member.playerId,
                    )
                }
            RankingRow(
                key = "t${row.teamId}",
                position = row.position,
                title = view.teamNames[row.teamId] ?: "Team ${row.teamId}",
                subtitle = null,
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.teamId == myTeam,
                members = members,
            )
        }
        RankingKind.CHICKEN -> rankings.chickenHands.map { row ->
            RankingRow(
                key = "c${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = "${formatPoints(row.points)} points - ${row.score} score",
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                chip = "${row.chickenHands}",
                chipChicken = true,
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
        RankingKind.BEST -> rankings.bestHands.mapIndexed { index, row ->
            RankingRow(
                key = "b${index}_${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = "${formatPoints(row.points)} points - ${row.score} score",
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                chip = "${row.handScore}",
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
    }

    RankingRows(
        rows,
        emptyText = "No results yet.",
        onPlayerClick = onPlayerClick,
        controls = {
            // One low button for each ranking: the icon over its label.
            if (kinds.size > 1) {
                kinds.forEach { item ->
                    val selected = item == kind
                    val contentColor = if (selected) TabContentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) tabRowColor() else Color.Transparent)
                            .clickable { chosen = item },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when (item) {
                            RankingKind.PLAYERS -> Icon(Icons.Default.People, contentDescription = null, Modifier.size(20.dp), tint = contentColor)
                            RankingKind.TEAMS -> Icon(Icons.Default.Groups, contentDescription = null, Modifier.size(20.dp), tint = contentColor)
                            RankingKind.CHICKEN -> ChickenIcon(size = 20.dp)
                            RankingKind.BEST -> PositionTrophy(1, size = 20.dp)
                        }
                        Text(item.short, style = MaterialTheme.typography.labelSmall, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    )
}

/** A dropdown of the lines that the user can type in, with the list under it, and a button that scrolls to the own line. */
@Composable
private fun RankingRows(
    rows: List<RankingRow>,
    emptyText: String,
    onPlayerClick: (Int) -> Unit,
    controls: @Composable RowScope.() -> Unit,
) {
    if (rows.isEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), content = controls)
        EmptyState(emptyText)
        return
    }
    val visible = rows
    val options = remember(rows) { rows.map { it.key to it.title } }
    val listState = rememberPersistentLazyListState("ranking")
    val scope = rememberCoroutineScope()
    val myIndex = visible.indexOfFirst { it.mine }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // One row: the ranking buttons, then "Jump to me", then the small dropdown at the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            controls()
            if (myIndex >= 0) {
                Column(
                    Modifier.width(44.dp).height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { scope.launch { listState.animateScrollToItem(myIndex) } }
                        .semantics { contentDescription = "Jump to me" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Me", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            // The dropdown does not filter the list. Choosing a line scrolls the list to it.
            SearchableDropdownField<String>(
                label = "Search",
                options = options,
                selected = null,
                onChoose = { key ->
                    val index = visible.indexOfFirst { it.key == key }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
                modifier = Modifier.weight(2f),
                compact = true,
            )
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.key }) { row ->
                RankingCard(row, onPlayerClick)
            }
        }
    }
}

/** One ranking line. A team line folds out to show its players. */
@Composable
private fun RankingCard(row: RankingRow, onPlayerClick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val foldable = row.members.isNotEmpty()
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    val playerId = row.playerId
    val onClick: (() -> Unit)? = when {
        foldable -> ({ expanded = !expanded })
        playerId != null -> ({ onPlayerClick(playerId) })
        else -> null
    }
    Card(
        if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth(),
        border = if (row.mine) BorderStroke(2.dp, colors.primary) else null,
        colors = CardDefaults.cardColors(
            // Gold, silver and bronze for the first three places, softened so the text stays readable.
            containerColor = if (row.position in 1..3) {
                // The first place uses a bright, light yellow so it is easy to tell from bronze.
                val medal = if (row.position == 1) Color(0xFFFFE259) else positionColor(row.position)
                medal.copy(alpha = if (row.position == 1) 0.55f else 0.35f).compositeOver(colors.surfaceContainerHigh)
            } else {
                colors.surfaceContainerHigh
            },
        ),
    ) {
        Column {
            RankingLine(row, trailing = if (foldable) expanded else null)
            if (foldable && expanded) {
                row.members.forEach { member ->
                    val memberId = member.playerId
                    Box(
                        if (memberId != null) Modifier.fillMaxWidth().clickable { onPlayerClick(memberId) } else Modifier.fillMaxWidth(),
                    ) { RankingLine(member, compact = true) }
                }
            }
        }
    }
}

/** The content of a ranking line. [trailing] is null for a line that cannot be folded, else it is the folded state. */
@Composable
private fun RankingLine(row: RankingRow, compact: Boolean = false, trailing: Boolean? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = if (compact) 24.dp else 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!compact) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                if (row.position <= 3) {
                    // A circle behind the trophy keeps it visible on the colored card.
                    Box(
                        Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { PositionTrophy(row.position, size = 24.dp) }
                } else Text("${row.position}", fontWeight = FontWeight.Bold)
            }
        }
        // The flag comes first and has a fixed width, so the names are aligned. A team has no flag.
        if (row.playerId != null) PlayerFlag(row.country)
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                fontWeight = if (row.mine) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, colors.onSurface),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (row.chipChicken) ChickenIcon()
                Text(
                    row.chip,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
        }
        row.score?.let {
            Text(
                it,
                Modifier.widthIn(min = 56.dp),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.End,
            )
        }
        if (trailing != null) {
            Icon(
                if (trailing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (trailing) "Hide players" else "Show players",
                Modifier.size(24.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TableScreen(navController: NavHostController, tournamentId: String, roundId: Int, tableId: Int) {
    var reloadKey by remember { mutableStateOf(0) }
    val (view, message) = rememberTournamentView(tournamentId, reloadKey)
    val table = view?.tables?.firstOrNull { it.roundId == roundId && it.tableId == tableId }

    AppScaffold(
        title = view?.tournament?.name ?: "Tournament",
        subtitle = view?.let(::progressOf),
        isLoading = view == null && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = {
            StatisticsAction {
                navController.navigate(
                    StatisticsRoute(StatisticsScope.TABLE.name, tournamentId = tournamentId, roundId = roundId, tableId = tableId),
                )
            }
            ThemeAction()
        },
    ) {
        ScreenColumn(
            maxWidth = 720.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            scrollable = true,
        ) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (view == null || table == null) return@ScreenColumn
            val complete = table.isComplete
            val handNumber = minOf(table.doneHands + 1, 16)
            // The round and the table are under the top bar. The top bar keeps the tournament name.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Round $roundId - Table $tableId", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${table.doneHands} of ${table.hands.size} hands" + if (complete) " - finished" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TableSeatsView(
                seats = table.seatsFor(view, withLevelChange = true),
                handNumber = handNumber,
                centerLabel = if (complete) "Done" else "Hand\n$handNumber/${table.hands.size}",
                showPoints = complete,
                initialRanking = complete,
                onPlayerClick = { navController.navigate(PlayerTablesRoute(tournamentId, it)) },
            )
            HandEntryPanel(
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
                view = view,
                table = table,
                onSaved = { reloadKey++ },
            )
            HandHistory(view, table)
        }
    }
}

@Composable
private fun HandHistory(view: PlayerTournamentView, table: PlayerTable) {
    val done = table.hands.filter { it.isDone }.sortedByDescending { it.handId }
    Text("Hands", style = MaterialTheme.typography.titleMedium)
    if (done.isEmpty()) {
        EmptyState("No hand saved yet.")
        return
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    done.forEach { hand ->
        val winnerSlot = hand.playerWinnerId.toIntOrNull()
        val loserSlot = hand.playerLooserId.toIntOrNull()
        val kind = hand.kind()
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${hand.handId}",
                    Modifier.width(20.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                )
                HandLabel(hand.handId, Modifier.width(44.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (kind != HandKind.DRAW) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlayerFlag(view.players[winnerSlot]?.country, width = 24.dp)
                            Text(
                                view.nameOf(winnerSlot),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WinTypePill(kind, kind.accent(dark, MaterialTheme.colorScheme.error))
                        if (kind == HandKind.DISCARD) {
                            PlayerFlag(view.players[loserSlot]?.country, width = 20.dp)
                            Text(
                                view.nameOf(loserSlot),
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (hand.isChickenHand) ChickenIcon()
                if (kind != HandKind.DRAW) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text(
                            hand.handScore,
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** The way the hand was won. Only this pill has color, so the type reads at once without coloring the whole item. */
@Composable
private fun WinTypePill(kind: HandKind, accent: Color) {
    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.18f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(kind.icon(), contentDescription = null, Modifier.size(16.dp), tint = accent)
            Text(
                when (kind) {
                    HandKind.SELF_PICK -> "Self-pick"
                    HandKind.DISCARD -> "Discard"
                    HandKind.DRAW -> "Draw"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

/** Green for a self-pick, the error red for a discard and grey for a draw. The dark theme gets lighter shades. */
private fun HandKind.accent(dark: Boolean, error: Color): Color = when (this) {
    HandKind.SELF_PICK -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    HandKind.DISCARD -> error
    HandKind.DRAW -> if (dark) Color(0xFFBDBDBD) else Color(0xFF757575)
}

private fun HandKind.icon(): ImageVector = when (this) {
    HandKind.SELF_PICK -> Icons.Default.Bolt
    HandKind.DISCARD -> Icons.Default.SwapHoriz
    HandKind.DRAW -> Icons.Default.HorizontalRule
}

/** Every table of one player of the tournament, with the results. */
@Composable
fun PlayerTablesScreen(navController: NavHostController, tournamentId: String, slotId: Int) {
    val (view, message) = rememberTournamentView(tournamentId)
    val ranking = view?.rankings?.players?.firstOrNull { it.playerId == slotId }
    val tables = view?.tables.orEmpty().filter { slotId in it.seatIds() }.sortedBy { it.roundId }

    AppScaffold(
        title = view?.nameOf(slotId) ?: "Player",
        subtitle = ranking?.let { "Place ${it.position} - ${it.points} points - ${it.score} score" },
        isLoading = view == null && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = {
            StatisticsAction {
                navController.navigate(
                    StatisticsRoute(StatisticsScope.PLAYER.name, tournamentId = tournamentId, slotId = slotId),
                )
            }
            ThemeAction()
        },
    ) {
        ScreenColumn(maxWidth = 720.dp, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (view == null) return@ScreenColumn
            if (tables.isEmpty()) {
                EmptyState("This player has no table yet.")
            } else {
                LazyColumn(
                    state = rememberPersistentLazyListState("tables"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                        TableSummaryCard(view, table, highlightSlot = slotId) {
                            navController.navigate(TableRoute(tournamentId, table.roundId, table.tableId))
                        }
                    }
                }
            }
        }
    }
}
