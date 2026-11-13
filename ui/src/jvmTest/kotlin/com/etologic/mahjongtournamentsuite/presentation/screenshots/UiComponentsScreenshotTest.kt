package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppSuccessDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.BigNumber
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.DateField
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedExtendedFloatingActionButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.HandLabel
import com.etologic.mahjongtournamentsuite.presentation.components.LastPlaceIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LevelChangeTag
import com.etologic.mahjongtournamentsuite.presentation.components.LevelMedal
import com.etologic.mahjongtournamentsuite.presentation.components.LevelMedalTag
import com.etologic.mahjongtournamentsuite.presentation.components.LevelRibbonBadge
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStandingCard
import com.etologic.mahjongtournamentsuite.presentation.components.PositionTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.ProfileDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ProvideCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.RankTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.SeatDisplay
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.SignInForm
import com.etologic.mahjongtournamentsuite.presentation.components.SignOutConfirmDialog
import com.etologic.mahjongtournamentsuite.presentation.components.StatCard
import com.etologic.mahjongtournamentsuite.presentation.components.StatGrid
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.components.SuiteAppChooserDialog
import com.etologic.mahjongtournamentsuite.presentation.components.SupportForm
import com.etologic.mahjongtournamentsuite.presentation.components.TableSeatsView
import com.etologic.mahjongtournamentsuite.presentation.components.WindIcon
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsSpacing
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.screenshots.componentVariants
import com.etologic.mahjongtournamentsuite.screenshots.screenshots
import kotlin.test.Test

/** The shared components of both apps (module ui), each one in its states, light and dark. */
@OptIn(ExperimentalTestApi::class)
class UiComponentsScreenshotTest {
    /** A component in the theme of the apps. [scroll] false is for content that fills the window itself. */
    private fun component(name: String, scroll: Boolean = true, content: @Composable () -> Unit) = screenshots("components/$name", componentVariants) { variant ->
        val preference = if (variant.dark) ThemePreference.Dark else ThemePreference.Light
        CompositionLocalProvider(LocalThemeController provides ThemeController(preference, variant.dark, {})) {
            MtsTheme(useDarkTheme = variant.dark) {
                ProvideCompactLayout {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (scroll) {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()).padding(MtsTheme.layout.screenGutter),
                                verticalArrangement = Arrangement.spacedBy(MtsSpacing.lg),
                            ) { content() }
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun buttons() = component("buttons") {
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
            DebouncedButton(onClick = {}) { Text("Save") }
            DebouncedButton(onClick = {}, enabled = false) { Text("Disabled") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
            DebouncedOutlinedButton(onClick = {}) { Text("Cancel") }
            DebouncedOutlinedButton(onClick = {}, enabled = false) { Text("Disabled") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
            DebouncedTextButton(onClick = {}) { Text("Text button") }
            AppTextButton(onClick = {}) { Text("App text button") }
        }
        DebouncedExtendedFloatingActionButton(onClick = {}) {
            Icon(Icons.Default.Add, null)
            Text("New tournament")
        }
    }

    @Test
    fun topBar() = component("top-bar", scroll = false) {
        AppScaffold(
            title = "Madrid Open 2026",
            subtitle = "Round 3",
            onBack = {},
            actions = {
                AppTopBarButton(text = "Players", icon = Icons.Default.Person, onClick = {})
                AppTopBarButton(text = "Teams", icon = Icons.Default.Groups, onClick = {})
                AppTopBarButton(text = "Statistics", icon = Icons.Default.QueryStats, onClick = {})
            },
        ) {
            Column(Modifier.padding(MtsTheme.layout.screenGutter), verticalArrangement = Arrangement.spacedBy(MtsSpacing.lg)) {
                SectionCard(title = "Section card", subtitle = "With a subtitle") { Text("The content of the card.") }
                SectionCard(title = "Centered title", centerTitle = true) { Text("Another card.") }
            }
        }
    }

    @Test
    fun fields() = component("fields") {
        val options = listOf(SelectOption("ES", "Spain"), SelectOption("FR", "France"), SelectOption("DE", "Germany"))
        SelectField(label = "Country", options = options, selected = "ES", onSelect = {})
        SelectField(label = "Country", options = options, selected = null, onSelect = {}, required = true)
        SelectField(label = "Country", options = options, selected = null, onSelect = {}, isError = true, supportingText = "Choose a country")
        SelectField(label = "Search", options = options, selected = null, onSelect = {}, compact = true)
        SelectField(label = "Disabled", options = options, selected = "FR", onSelect = {}, enabled = false)
        DateField(label = "First day", value = "2026-05-16", onChange = {})
        DateField(label = "Last day", value = "", onChange = {})
    }

    @Test
    fun dataTable() = component("data-table") {
        SectionCard(title = "Players") {
            DataTableHeaderRow {
                Text("ID", Modifier.weight(0.2f))
                Text("Name", Modifier.weight(1f))
                Text("Points", Modifier.weight(0.4f))
            }
            DataTableDivider()
            listOf(Triple(1, "Lucía Martín", "12"), Triple(2, "Hugo García", "9"), Triple(3, "Claire Dubois", "7")).forEachIndexed { index, (id, name, points) ->
                DataTableRow(onClick = {}, highlighted = index == 0) {
                    Text("$id", Modifier.weight(0.2f))
                    Text(name, Modifier.weight(1f))
                    Text(points, Modifier.weight(0.4f))
                }
                DataTableDivider()
            }
        }
    }

    @Test
    fun levels() = component("levels") {
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md)) {
            PlayerLevel.entries.forEach { LevelMedal(it, size = MtsSpacing.xxxl) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md)) {
            PlayerLevel.entries.forEach { LevelMedal(it, size = MtsSpacing.xxxl, laurel = true) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md)) {
            LevelRibbonBadge(PlayerLevel.EXPERT)
            LevelRibbonBadge(PlayerLevel.MASTER)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md)) {
            LevelMedalTag(PlayerLevel.ADVANCED, skillScore = 52.4, provisional = false)
            LevelMedalTag(PlayerLevel.BEGINNER, skillScore = 12.0, provisional = true)
            LevelChangeTag(1.8)
            LevelChangeTag(-0.6)
        }
    }

    @Test
    fun tableSeats() = component("table-seats") {
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md)) {
            SeatWind.entries.forEach { WindIcon(it) }
            ChickenIcon()
            HandLabel(7)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md)) {
            (1..4).forEach { PositionTrophy(it) }
            (1..3).forEach { RankTrophy(it) }
            LastPlaceIcon()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
            listOf("ES", "FR", "DE", "IT", "NL", "PT", "EU", "").forEach { CountryFlag(it) }
        }
        TableSeatsView(
            seats = listOf(
                SeatDisplay("LUCÍA MARTÍN", "37", "2", isMine = true, slotId = 1, country = "ES", level = PlayerLevel.ADVANCED),
                SeatDisplay("HUGO GARCÍA", "93", "4", slotId = 2, country = "ES", level = PlayerLevel.EXPERT),
                SeatDisplay("CLAIRE DUBOIS", "-266", "0", penalty = -10, slotId = 3, country = "FR"),
                SeatDisplay("JONAS BECKER", "136", "1", slotId = 4, country = "DE", level = PlayerLevel.BEGINNER),
            ),
            handNumber = 9,
            centerLabel = "Hand 9",
            showPoints = true,
        )
    }

    @Test
    fun statistics() = component("statistics") {
        StatGrid {
            BigNumber("Tables", "8 of 16")
            BigNumber("Players", "16")
            BigNumber("Hands", "128", info = "Hands with a result")
            BigNumber("Win rate", "24", suffix = "%")
        }
        PlayerStandingCard(position = 2, points = 11.5, score = 214)
        StatCard(title = "Hands", info = "Every hand of the tournament") {
            StatRow("Won by discard", "62")
            StatRow("Won by self-draw", "46")
            StatRow("Draws", "20")
        }
        StatSection("Section") { StatRow("Best hand", "88") }
        AppEmptyState(Icons.Default.QueryStats, "No statistics yet", "The statistics show when the first hand is saved.")
    }

    @Test
    fun signInForm() = component("sign-in-form", scroll = false) {
        SignInForm(
            email = "ada@example.test",
            onEmailChange = {},
            password = "secret",
            onPasswordChange = {},
            isLoading = false,
            errorMessage = null,
            infoMessage = null,
            emailRequester = remember { FocusRequester() },
            onSubmit = {},
            onForgotPassword = {},
            onErrorDismiss = {},
            onNeedHelp = {},
        )
    }

    @Test
    fun supportForm() = component("support-form", scroll = false) {
        SupportForm(app = "player", initialEmail = "lucia@example.test", onSend = { AppResult.Success(Unit) }, onBack = {})
    }

    @Test
    fun errorDialog() = component("dialog-error") { AppErrorDialog(message = "The server did not answer. Try again in a moment.", onDismiss = {}) }

    @Test
    fun successDialog() = component("dialog-success") { AppSuccessDialog(message = "The invitations are sent.", onDismiss = {}) }

    @Test
    fun signOutDialog() = component("dialog-sign-out") { SignOutConfirmDialog(onConfirm = {}, onDismiss = {}) }

    @Test
    fun appChooserDialog() = component("dialog-app-chooser") { SuiteAppChooserDialog(onChoose = {}) }

    @Test
    fun profileDialog() = component("dialog-profile") {
        ProfileDialog(
            profile = UserProfile(uid = "player-1", email = "lucia@example.test", alias = "Lucía", emaId = "04010001"),
            onSaveAlias = { AppResult.Success(it.let { alias -> UserProfile("player-1", "lucia@example.test", alias) }) },
            onSavePhoto = { _, _ -> error("unused") },
            onRemovePhoto = { error("unused") },
            onSaved = {},
            onDismiss = {},
            onSignOut = {},
            onOpenDesignSystem = {},
        )
    }
}
