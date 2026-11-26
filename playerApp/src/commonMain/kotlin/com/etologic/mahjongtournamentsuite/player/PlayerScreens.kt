package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactSupport
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.components.AppLoading
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DrawExplanationTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalTopBarMenu
import com.etologic.mahjongtournamentsuite.presentation.components.PasswordResetConfirmDialog
import com.etologic.mahjongtournamentsuite.presentation.components.SignInForm
import com.etologic.mahjongtournamentsuite.presentation.components.SuiteAppChooserDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TopBarMenuScope
import com.etologic.mahjongtournamentsuite.presentation.components.passwordResetEmailError
import com.etologic.mahjongtournamentsuite.presentation.components.rememberOpenPrivacyPolicy
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.platform.appSwitchLeavesPage
import com.etologic.mahjongtournamentsuite.presentation.platform.currentAppSwitcher
import com.etologic.mahjongtournamentsuite.presentation.platform.loadBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.menu_contact
import mahjongtournamentsuite.playerapp.generated.resources.menu_more
import mahjongtournamentsuite.playerapp.generated.resources.menu_privacy
import mahjongtournamentsuite.playerapp.generated.resources.sign_in_enter_email
import mahjongtournamentsuite.playerapp.generated.resources.sign_in_reset_sent
import mahjongtournamentsuite.playerapp.generated.resources.tab_my_results
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedExtendedFloatingActionButton as ExtendedFloatingActionButton

@Composable
fun SplashScreen(
    navController: NavHostController,
    openTournamentId: String?,
    /** The screens that were open before a page refresh, from the first to the last. */
    restoredRoutes: List<Any> = emptyList(),
    /** True when the admin app sent the user here to sign in. The site has one sign-in, the one of this app. */
    openSignIn: Boolean = false,
    /** True when the address was `/support`, the link of the privacy policy. */
    openSupport: Boolean = false,
    /** A link of the web to a tournament, `/<slug>` or `/registration?...` (also the old `/inscripcion/...`). */
    openRegistration: RegistrationLink? = null,
    /** True when the address was `/download`, the page of the apps. */
    openApps: Boolean = false,
) {
    val session = koinInject<PlayerSessionPresenter>()
    val openRegistrationLink = rememberRegistrationLinkOpener(navController)
    LaunchedEffect(Unit) {
        val signInFirst = openSignIn && !session.hasSession()
        // Everything is public, so the app opens on the tournaments with or without an account.
        navController.navigate(TournamentsRoute) { popUpTo(SplashRoute) { inclusive = true } }
        val restored = restoredRoutes.filterNot { it == TournamentsRoute }
        if (openSupport) {
            // The support form over the tournaments, so Back returns to the list.
            navController.navigate(SupportRoute())
        } else if (openApps) {
            // The page of the apps over the tournaments, like the support form.
            navController.navigate(AppsRoute)
        } else if (openRegistration != null) {
            // The registrations of the tournament. The link of the emails also confirms the email.
            // The splash leaves the screen as soon as the tournaments open, which would cancel this effect half way:
            // the QR link then opened My tables but never the table of the player (found by the web E2E tests).
            withContext(NonCancellable) { openRegistrationLink(openRegistration) }
        } else if (signInFirst) {
            navController.navigate(SignInRoute())
        } else if (restored.isNotEmpty()) {
            // A page refresh returns to the screen that was open.
            restored.forEach { navController.navigate(it) }
        } else if (openTournamentId != null) {
            // A shared link or the link of a registration email goes on to the tournament. Back returns to the list.
            navController.navigate(TournamentRoute(openTournamentId))
        }
    }
    AppLoading()
}

@Composable
fun SignInScreen(navController: NavHostController, openTournamentId: String?) {
    val presenter = koinInject<PlayerSessionPresenter>()
    val scope = rememberCoroutineScope()
    val emailRequester = remember { FocusRequester() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<UiText?>(null) }
    var infoMessage by remember { mutableStateOf<UiText?>(null) }
    // Admins and editors choose between this app and the admin app after the sign-in.
    val appSwitcher = currentAppSwitcher()
    var choosingApp by remember { mutableStateOf(false) }
    // True when the browser gave the saved email and password: the sign-in then starts by itself.
    var signInWithSavedCredentials by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Chrome mediates this request through its password manager UI.
        loadBrowserCredentials { savedEmail, savedPassword ->
            email = savedEmail
            password = savedPassword
            signInWithSavedCredentials = true
        }
        emailRequester.requestFocus()
    }

    fun continueInPlayerApp() {
        // Back to the screens that were open, loaded again for the account.
        val routes = navController.openRoutes()
        navController.reopen(if (openTournamentId != null) routes + TournamentRoute(openTournamentId) else routes)
    }

    fun openAdminApp(switcher: (SuiteApp) -> Unit) {
        switcher(SuiteApp.ADMIN)
        // On Android the admin app opens over this one. Back returns here, signed in. On the web the admin app is
        // another page: closing the sign-in here goes back in the browser history, which would stop that page loading.
        if (!appSwitchLeavesPage) continueInPlayerApp()
    }

    fun submit() {
        errorMessage = null
        infoMessage = null
        busy = true
        scope.launch {
            val result = presenter.signIn(email, password)
            val canManage = result is AppResult.Success && appSwitcher != null && presenter.canManage(RefreshMode.FORCE)
            // The sign in suspends, and what follows must run on the main thread, also in the UI tests: there the
            // coroutine comes back on a worker thread, and the error dialog that it opens cannot be made off it.
            withContext(Dispatchers.Main) {
                when (result) {
                    is AppResult.Success -> {
                        saveBrowserCredentials(email.trim(), password)
                        if (canManage) choosingApp = true else continueInPlayerApp()
                    }
                    is AppResult.Failure -> errorMessage = result.error.toUiText()
                }
                busy = false
            }
        }
    }

    LaunchedEffect(signInWithSavedCredentials) {
        if (signInWithSavedCredentials) {
            signInWithSavedCredentials = false
            if (!busy) submit()
        }
    }

    var confirmingReset by remember { mutableStateOf(false) }

    fun requestPasswordReset() {
        errorMessage = null
        infoMessage = null
        if (email.trim().isEmpty()) {
            errorMessage = UiText.of(Res.string.sign_in_enter_email)
            emailRequester.requestFocus()
            return
        }
        passwordResetEmailError(email)?.let {
            errorMessage = it
            emailRequester.requestFocus()
            return
        }
        confirmingReset = true
    }

    fun sendPasswordReset() {
        confirmingReset = false
        busy = true
        scope.launch {
            val result = presenter.resetPassword(email.trim())
            withContext(Dispatchers.Main) {
                when {
                    // A well formed email never gets an error: only a lost connection is worth telling.
                    result is AppResult.Failure && result.error == AppError.Network -> errorMessage = result.error.toUiText()
                    else -> infoMessage = UiText.of(Res.string.sign_in_reset_sent)
                }
                busy = false
            }
        }
    }

    if (choosingApp && appSwitcher != null) {
        SuiteAppChooserDialog { app ->
            choosingApp = false
            if (app == SuiteApp.ADMIN) openAdminApp(appSwitcher) else continueInPlayerApp()
        }
    }

    if (confirmingReset) {
        PasswordResetConfirmDialog(
            email = email.trim(),
            onConfirm = ::sendPasswordReset,
            onDismiss = {
                confirmingReset = false
                emailRequester.requestFocus()
            },
        )
    }

    SignInForm(
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        isLoading = busy,
        errorMessage = errorMessage,
        infoMessage = infoMessage,
        emailRequester = emailRequester,
        onSubmit = ::submit,
        onForgotPassword = ::requestPasswordReset,
        onErrorDismiss = { errorMessage = null },
        onNeedHelp = { navController.navigate(SupportRoute(email = email.trim())) },
        onGetTheApp = if (LocalWebApps.current != null) {
            { navController.navigate(AppsRoute) }
        } else {
            null
        },
        onBack = { if (!navController.popBackStack()) navController.reopen(emptyList()) },
    )
}

/**
 * The Rules button and the three-dots menu with How does the draw work?, Contact and Privacy, in the top bar.
 * The list of tournaments and the screen of a tournament show them.
 *
 * On a phone the buttons are already inside the three-dots menu of the top bar, and a menu inside a menu is awkward.
 * There the draw, Contact and Privacy show directly in that menu, under the Rules group.
 */
@Composable
fun InfoTopBarButtons(
    navController: NavHostController,
    drawExplanation: DrawExplanationState,
    /** Opens the results of the signed-in player in this tournament. Null when the player does not play it. */
    onMyResults: (() -> Unit)? = null,
) {
    // Out of the menus, so it lives on while a menu closes.
    val openPrivacyPolicy = rememberOpenPrivacyPolicy()
    val options: @Composable () -> Unit = {
        onMyResults?.let { MyResultsTopBarButton(it) }
        // The same explanation of the draw as in the admin app, with its flowchart: public for every player.
        DrawExplanationTopBarButton(onClick = drawExplanation::open)
        ContactTopBarButton(navController)
        AppsTopBarButton(navController)
        AppTopBarButton(
            text = stringResource(Res.string.menu_privacy),
            icon = Icons.Default.Policy,
            onClick = openPrivacyPolicy,
        )
    }
    RulesTopBarButton(navController, dividerBelow = true)
    if (LocalTopBarMenu.current != null) {
        options()
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        AppTopBarButton(
            text = stringResource(Res.string.menu_more),
            icon = Icons.Default.MoreVert,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // The options draw as menu items and close this menu when they are chosen.
            CompositionLocalProvider(LocalTopBarMenu provides TopBarMenuScope { open = false }) { options() }
        }
    }
}

/**
 * Whether "How does the draw work?" is open. It lives in the screen, out of the top bar: on a phone the button is in the
 * three-dots menu, which leaves the screen when it closes. The screen shows the dialog with [Dialog].
 */
class DrawExplanationState {
    private var visible by mutableStateOf(false)

    fun open() {
        visible = true
    }

    @Composable
    fun Dialog() {
        if (visible) DrawExplanationDialog(onDismiss = { visible = false })
    }
}

@Composable
fun rememberDrawExplanationState() = remember { DrawExplanationState() }

/** Opens the contact form. The landing page has the contact button. The privacy policy also links the form at /support. */
@Composable
fun ContactTopBarButton(navController: NavHostController) {
    AppTopBarButton(
        text = stringResource(Res.string.menu_contact),
        icon = Icons.Default.ContactSupport,
        onClick = { navController.navigate(SupportRoute()) },
    )
}

/** The option of the menu of a tournament that opens the results of the signed-in player. */
@Composable
private fun MyResultsTopBarButton(onClick: () -> Unit) {
    AppTopBarButton(text = stringResource(Res.string.tab_my_results), icon = Icons.Default.TableChart, onClick = onClick)
}

/**
 * The floating button of Tables and Ranking that opens the results of the signed-in player. While the button
 * "Go to my table" shows, it goes above it.
 */
@Composable
internal fun MyResultsButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .padding(bottom = if (MyTableButtonState.shown) MyTableButtonClearance else 0.dp)
            .testTag(MY_RESULTS_BUTTON_TAG),
    ) {
        Icon(Icons.Default.TableChart, contentDescription = null)
        Spacer(Modifier.width(MtsTheme.spacing.sm))
        ButtonText(stringResource(Res.string.tab_my_results))
    }
}

/** The bottom space that a list leaves for the floating buttons over it. */
internal val LocalFabClearance = compositionLocalOf { 0.dp }

/** The space of the button "My results" over the bottom of a list. */
internal val FabClearance = 80.dp

/** The height of the button "Go to my table" and a gap above it. */
internal val MyTableButtonClearance = 72.dp

internal const val MY_RESULTS_BUTTON_TAG = "myResultsButton"
