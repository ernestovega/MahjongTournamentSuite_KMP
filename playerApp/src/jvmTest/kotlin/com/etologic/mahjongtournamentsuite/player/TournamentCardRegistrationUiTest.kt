package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The card of a tournament with open registrations in the list of Player (20-21 nov): the Register button, the places,
 * and no button for an account already registered or on the waiting list (the stamp of the card says it).
 */
@OptIn(ExperimentalTestApi::class)
class TournamentCardRegistrationUiTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previous)

    private fun tournament(status: RegistrationPhase = RegistrationPhase.OPEN) = Tournament(
        id = "bcn",
        name = "Barcelona Open 2027",
        isTeams = false,
        numPlayers = 64,
        numRounds = 7,
        eventStartDate = "2099-03-01",
        drawPending = true,
        registration = RegistrationSettings(status = status, maxPlayers = 64, confirmed = 40, waiting = 3, slug = "bcn27"),
    )

    private var opened = 0

    private fun ComposeUiTest.card(tournament: Tournament, mark: MyTournamentMark?) {
        setContent { MtsTheme(useDarkTheme = false) { TournamentCard(tournament, standing = null, mark = mark, onClick = { opened++ }) } }
        waitForIdle()
    }

    @Test
    fun anOpenTournamentShowsItsPlacesAndTheButtonToRegister() = runComposeUiTest {
        card(tournament(), mark = null)

        onNodeWithText("40 with a place of 64", substring = true).assertExists()
        onNodeWithText("3 on the waiting list", substring = true).assertExists()
        onNodeWithTag("tournament-register").performClick()
        waitForIdle()
        assertEquals(1, opened, "Register opens the tournament, where the person registers")
    }

    @Test
    fun anAccountAlreadyRegisteredOrWaitingHasNoButtonToRegister() = runComposeUiTest {
        card(tournament(), mark = MyTournamentMark.REGISTERED)
        onNodeWithTag("tournament-register").assertDoesNotExist()
    }

    @Test
    fun anAccountOnTheWaitingListHasNoButtonToRegister() = runComposeUiTest {
        card(tournament(), mark = MyTournamentMark.WAITING)
        onNodeWithTag("tournament-register").assertDoesNotExist()
    }

    @Test
    fun closedRegistrationsHaveNoButton() = runComposeUiTest {
        card(tournament(RegistrationPhase.CLOSED), mark = null)
        onNodeWithTag("tournament-register").assertDoesNotExist()
        onNodeWithText("40 with a place of 64", substring = true).assertExists()
    }

    @Test
    fun withoutALimitThePlacesHaveNoEnd() = runComposeUiTest {
        card(tournament().copy(registration = RegistrationSettings(maxPlayers = null, confirmed = 12)), mark = null)
        onNodeWithText("12 with a place of ∞", substring = true).assertExists()
    }

    @Test
    fun afterTheDrawTheCardHasNoRegistrations() = runComposeUiTest {
        card(tournament().copy(drawPending = false), mark = null)
        onNodeWithTag("tournament-register").assertDoesNotExist()
        onNodeWithText("with a place of", substring = true).assertDoesNotExist()
    }
}
