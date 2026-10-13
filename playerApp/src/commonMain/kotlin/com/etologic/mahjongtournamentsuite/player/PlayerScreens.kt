package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.clickable
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
import com.etologic.mahjongtournamentsuite.presentation.platform.loadBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBrowserCredentials
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Bolt
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
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.WindIcon
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.HandLabel
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.PositionTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SeatDisplay
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
fun SplashScreen(navController: NavHostController, inviteToken: String?, openTournamentId: String?) {
    val presenter = koinInject<PlayerSessionPresenter>()
    LaunchedEffect(Unit) {
        val next: Any = when {
            inviteToken != null -> InviteRoute(inviteToken)
            presenter.hasSession() -> TournamentsRoute
            else -> SignInRoute(openTournamentId = openTournamentId)
        }
        navController.navigate(next) { popUpTo(SplashRoute) { inclusive = true } }
        // The link of a registration email goes on to the tournament. Back returns to the list.
        if (next == TournamentsRoute && openTournamentId != null) {
            navController.navigate(TournamentRoute(openTournamentId))
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

    LaunchedEffect(Unit) {
        when (val result = tournaments.listTournaments(RefreshMode.FORCE)) {
            is AppResult.Success -> items = result.value
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
    }

    AppScaffold(
        centerTitle = true,
        title = "My tournaments",
        isLoading = items == null && message == null,
        autoFocusFirst = false,
        actions = {
            PlayerOverflowMenu(onSignOut = {
                scope.launch {
                    session.signOut()
                    navController.navigate(SignInRoute()) { popUpTo(0) }
                }
            })
        },
    ) {
        ScreenColumn(maxWidth = 720.dp, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState("You are not registered in any tournament yet.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { tournament ->
                        TournamentCard(tournament) { navController.navigate(TournamentRoute(tournament.id)) }
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

private fun statusOf(tournament: Tournament): TournamentStatus {
    if (tournament.isCompleted) return TournamentStatus.FINISHED
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val start = tournament.eventStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return if (start != null && today < start) TournamentStatus.UPCOMING else TournamentStatus.IN_PROGRESS
}

@Composable
private fun StatusChip(status: TournamentStatus) {
    val (container, content) = when (status) {
        TournamentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        TournamentStatus.UPCOMING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TournamentStatus.FINISHED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(status.label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TournamentCard(tournament: Tournament, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tournament.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                StatusChip(statusOf(tournament))
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
            val dates = datesOf(tournament)
            if (dates != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(dates, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var chosenTab by remember { mutableStateOf<Int?>(null) }
    val tab = chosenTab ?: if (view?.mySlotId == null) 1 else 0

    AppScaffold(
        centerTitle = true,
        title = view?.tournament?.name ?: "Tournament",
        subtitle = view?.tournament?.let(::datesOf),
        isLoading = view == null && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = { PlayerOverflowMenu() },
    ) {
        ScreenColumn(maxWidth = 720.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (view == null) return@ScreenColumn
            ProgressLine(view)
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("My tables", "All tables", "Ranking").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { chosenTab = index }, text = { Text(title) })
                }
            }
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
                else -> RankingTab(view) { navController.navigate(PlayerTablesRoute(tournamentId, it)) }
            }
        }
    }
}

private fun datesOf(tournament: Tournament): String? =
    formatLocalizedDateRange(tournament.eventStartDate, tournament.eventEndDate)

/** How far the tournament is. It is the same on every tab, so it sits above the tabs. */
@Composable
private fun ProgressLine(view: PlayerTournamentView) {
    val rounds = view.tournament.numRounds
    val tablesPerRound = view.tournament.numPlayers / 4
    val completedTables = view.tables.count { it.table.isCompleted }
    val ongoingTables = view.tables.count { !it.table.isCompleted && it.doneHands > 0 }
    val completedRounds = (1..rounds).count { round ->
        tablesPerRound > 0 && view.tables.count { it.roundId == round && it.table.isCompleted } >= tablesPerRound
    }
    val text = listOf(
        "$completedRounds of $rounds rounds",
        "$completedTables of ${rounds * tablesPerRound} tables",
        "$ongoingTables ongoing",
    ).joinToString(" · ")
    Text(
        text,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The four players of a table in the order of hand 1 (East, South, West, North). */
private fun PlayerTable.seatsFor(view: PlayerTournamentView): List<SeatDisplay> {
    val t = table
    fun penalty(select: (TableHand) -> String) = hands.sumOf { select(it).trim().toIntOrNull() ?: 0 }
    fun seat(id: String, score: String, points: String, penalty: Int): SeatDisplay {
        val slot = id.toIntOrNull()
        return SeatDisplay(
            name = view.nameOf(slot),
            score = score,
            points = points,
            penalty = penalty,
            isMine = slot != null && slot == view.mySlotId,
            slotId = slot,
        )
    }
    return listOf(
        seat(t.playerEastId, t.playerEastScore, t.playerEastPoints, penalty { it.playerEastPenalty }),
        seat(t.playerSouthId, t.playerSouthScore, t.playerSouthPoints, penalty { it.playerSouthPenalty }),
        seat(t.playerWestId, t.playerWestScore, t.playerWestPoints, penalty { it.playerWestPenalty }),
        seat(t.playerNorthId, t.playerNorthScore, t.playerNorthPoints, penalty { it.playerNorthPenalty }),
    )
}

/** A table is complete when its totals were entered by hand or all its hands are done. */
private val PlayerTable.isComplete: Boolean
    get() = table.isCompleted || (hands.isNotEmpty() && doneHands >= hands.size)

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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tables, key = { "${it.roundId}_${it.tableId}" }) { table ->
            TableSummaryCard(view, table, highlightSlot = view.mySlotId, onClick = { onOpen(table) })
        }
    }
}

/** A table with its four players. The points of the table show only for a complete table. */
@Composable
private fun TableSummaryCard(view: PlayerTournamentView, table: PlayerTable, highlightSlot: Int?, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val title = listOfNotNull("Round ${table.roundId}", "Table ${table.tableId}", view.roundStartOf(table.roundId))
                Text(title.joinToString(" · "), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (table.isComplete) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Finished", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        "${table.doneHands}/${table.hands.size} hands",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // East to North while the table is open. Winner to last when it is complete.
            val seats = table.seatsFor(view).withIndex().toList().let { all ->
                if (table.isComplete) {
                    all.sortedWith(
                        compareByDescending<IndexedValue<SeatDisplay>> { it.value.points.trim().toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
                            .thenByDescending { it.value.score.trim().toIntOrNull() ?: Int.MIN_VALUE },
                    )
                } else {
                    all
                }
            }
            seats.forEachIndexed { position, (index, seat) ->
                val highlighted = seat.slotId != null && seat.slotId == highlightSlot
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (table.isComplete) {
                        // Seats are sorted by result here. The empty box keeps the names aligned.
                        if (position < 3) PositionTrophy(position + 1, size = 22.dp) else Spacer(Modifier.size(22.dp))
                    }
                    WindIcon(SeatWind.entries[index], size = 22.dp)
                    Text(
                        seat.name,
                        Modifier.weight(1f),
                        fontWeight = if (highlighted) FontWeight.Bold else null,
                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(seat.score.ifBlank { "—" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (table.isComplete && seat.points.isNotBlank()) {
                        Text(formatPoints(seat.points), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private enum class RankingKind(val title: String) {
    PLAYERS("Players"),
    TEAMS("Teams"),
    CHICKEN("Chicken hands"),
    BEST("Best hands"),
}

/** One line of a ranking list. The chip is the main number, outlined. The score is the plain number next to it. */
private data class RankingRow(
    val key: String,
    val position: Int,
    val title: String,
    val subtitle: String?,
    val chip: String,
    val chipChicken: Boolean = false,
    val score: String? = null,
    val mine: Boolean = false,
    val playerId: Int? = null,
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
                subtitle = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
        RankingKind.TEAMS -> rankings.teams.map { row ->
            RankingRow(
                key = "t${row.teamId}",
                position = row.position,
                title = view.teamNames[row.teamId] ?: "Team ${row.teamId}",
                subtitle = null,
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.teamId == myTeam,
            )
        }
        RankingKind.CHICKEN -> rankings.chickenHands.map { row ->
            RankingRow(
                key = "c${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = "${formatPoints(row.points)} points · ${row.score} score",
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
                subtitle = "${formatPoints(row.points)} points · ${row.score} score",
                chip = "${row.handScore}",
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (kinds.size > 1) {
            SecondaryTabRow(selectedTabIndex = kinds.indexOf(kind)) {
                kinds.forEach { item ->
                    Tab(selected = item == kind, onClick = { chosen = item }, text = { Text(item.title, maxLines = 1) })
                }
            }
        }
        RankingRows(rows, emptyText = "No results yet.", onPlayerClick = onPlayerClick)
    }
}

/** A search field with the list under it, and a button that scrolls to the own line. */
@Composable
private fun RankingRows(rows: List<RankingRow>, emptyText: String, onPlayerClick: (Int) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    val visible = rows.filter { it.title.contains(query.trim(), ignoreCase = true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val myIndex = visible.indexOfFirst { it.mine }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            if (myIndex >= 0) {
                TextButton(onClick = { scope.launch { listState.animateScrollToItem(myIndex) } }) { Text("Jump to me") }
            }
        }
        if (visible.isEmpty()) {
            EmptyState("Nobody matches the search.")
            return@Column
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.key }) { row ->
                val playerId = row.playerId
                Card(
                    if (playerId != null) Modifier.fillMaxWidth().clickable { onPlayerClick(playerId) } else Modifier.fillMaxWidth(),
                    border = if (row.mine) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (row.position <= 3) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            if (row.position <= 3) PositionTrophy(row.position) else Text("${row.position}", fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(row.title, fontWeight = if (row.mine) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            row.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        row.score?.let {
                            Text(
                                it,
                                Modifier.widthIn(min = 56.dp),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (it.startsWith("-")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableScreen(navController: NavHostController, tournamentId: String, roundId: Int, tableId: Int) {
    var reloadKey by remember { mutableStateOf(0) }
    val (view, message) = rememberTournamentView(tournamentId, reloadKey)
    val table = view?.tables?.firstOrNull { it.roundId == roundId && it.tableId == tableId }

    AppScaffold(
        centerTitle = true,
        title = "Round $roundId · Table $tableId",
        subtitle = table?.let { "${it.doneHands} of ${it.hands.size} hands" + if (it.isComplete) " · finished" else "" },
        isLoading = view == null && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = { PlayerOverflowMenu() },
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
            TableSeatsView(
                seats = table.seatsFor(view),
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
                        Text(
                            view.nameOf(winnerSlot),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WinTypePill(kind, kind.accent(dark, MaterialTheme.colorScheme.error))
                        if (kind == HandKind.DISCARD) {
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
        centerTitle = true,
        title = view?.nameOf(slotId) ?: "Player",
        subtitle = ranking?.let { "Place ${it.position} · ${it.points} points · ${it.score} score" },
        isLoading = view == null && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = { PlayerOverflowMenu() },
    ) {
        ScreenColumn(maxWidth = 720.dp, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (view == null) return@ScreenColumn
            if (tables.isEmpty()) {
                EmptyState("This player has no table yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
