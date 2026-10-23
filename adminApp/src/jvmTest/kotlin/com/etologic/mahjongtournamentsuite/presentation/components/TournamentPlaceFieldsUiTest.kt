package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.VenueLinkPlace
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TournamentPlaceFieldsUiTest {
    private val countries = listOf(Country("ES", "Spain"))
    private val placeLink =
        "https://www.google.com/maps/place/Hotel+Arts,+Carrer+de+la+Marina+19,+08005+Barcelona,+Spain/@41.38,2.19,17z"

    private class Form(country: String = "", city: String = "") {
        var country by mutableStateOf(country)
        var city by mutableStateOf(city)
        var currentVenue by mutableStateOf(TournamentVenue())
        val resolved = mutableListOf<String>()
    }

    private fun ComposeUiTest.show(form: Form, resolve: suspend (String) -> AppResult<ResolvedVenueLink> = { AppResult.Success(ResolvedVenueLink(it, null)) }) {
        setContent {
            MtsTheme(useDarkTheme = false) {
                TournamentPlaceFields(
                    venue = form.currentVenue,
                    onVenueChange = { form.currentVenue = it },
                    hostCountry = form.country,
                    onHostCountryChange = { form.country = it },
                    hostCity = form.city,
                    onHostCityChange = { form.city = it },
                    countries = countries,
                    resolveLink = { url ->
                        form.resolved += url
                        resolve(url)
                    },
                )
            }
        }
    }

    @Test
    fun everyFieldIsOpenWithoutALink() = runComposeUiTest {
        show(Form())

        onNodeWithText("Venue name").assertIsEnabled()
        onNodeWithText("Address").assertIsEnabled()
        onNodeWithText("City").assertIsEnabled()
        onNodeWithText("Country").assertIsEnabled()
    }

    @Test
    fun aFullLinkFillsAndLocksTheFields() = runComposeUiTest {
        val form = Form()
        show(form)

        onNodeWithText("Google Maps link").performTextInput(placeLink)
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals("Hotel Arts", form.currentVenue.name)
        assertEquals("Carrer de la Marina 19, 08005 Barcelona", form.currentVenue.address)
        assertEquals("Barcelona", form.city)
        assertEquals("ES", form.country)
        assertEquals(placeLink, form.currentVenue.mapUrl)
        onNodeWithText("Venue name").assertIsNotEnabled()
        onNodeWithText("Address").assertIsNotEnabled()
        onNodeWithText("City").assertIsNotEnabled()
        onNodeWithText("Country").assertIsNotEnabled()
        onNodeWithText("Google Maps link").assertIsEnabled()
        assertEquals(listOf(placeLink), form.resolved)
    }

    @Test
    fun aShortLinkIsOpenedByTheServerFirst() = runComposeUiTest {
        val form = Form()
        show(form, resolve = { AppResult.Success(ResolvedVenueLink(placeLink, null)) })

        onNodeWithText("Google Maps link").performTextInput("https://maps.app.goo.gl/abc123")
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(listOf("https://maps.app.goo.gl/abc123"), form.resolved)
        assertEquals("Hotel Arts", form.currentVenue.name)
        onNodeWithText("Venue name").assertIsNotEnabled()
    }

    @Test
    fun thePlaceFromTheServerWinsOverTheTextOfTheLink() = runComposeUiTest {
        val form = Form()
        val place = VenueLinkPlace("Hotel Arts Barcelona", "Carrer de la Marina 19-21, 08005 Barcelona", "Barcelona", "ES")
        show(form, resolve = { AppResult.Success(ResolvedVenueLink(it, place)) })

        onNodeWithText("Google Maps link").performTextInput(placeLink)
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals("Hotel Arts Barcelona", form.currentVenue.name)
        assertEquals("Carrer de la Marina 19-21, 08005 Barcelona", form.currentVenue.address)
        assertEquals("Barcelona", form.city)
        assertEquals("ES", form.country)
        onNodeWithText("Venue name").assertIsNotEnabled()
    }

    @Test
    fun clearingTheLinkOpensTheFieldsAndKeepsNoValueFromTheLink() = runComposeUiTest {
        val form = Form(country = "FR", city = "Paris")
        show(form)
        onNodeWithText("Google Maps link").performTextInput(placeLink)
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        form.currentVenue = form.currentVenue.copy(mapUrl = "")
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("Venue name").assertIsEnabled()
        onNodeWithText("City").assertIsEnabled()
        assertEquals("", form.currentVenue.name)
        assertEquals("", form.city)
        assertEquals("", form.country)
    }

    @Test
    fun aLinkThatTheServerCannotOpenLeavesTheFieldsOpen() = runComposeUiTest {
        val form = Form()
        show(form, resolve = { AppResult.Failure(AppError.Network) })

        onNodeWithText("Google Maps link").performTextInput("https://maps.app.goo.gl/abc123")
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("The link could not be opened. Check it or type the place.").assertExists()
        onNodeWithText("Venue name").assertIsEnabled()
        onNodeWithText("City").assertIsEnabled()
    }

    @Test
    fun aLinkThatIsNotGoogleMapsGivesAnError() = runComposeUiTest {
        val form = Form()
        show(form)

        onNodeWithText("Google Maps link").performTextInput("https://example.com/place")
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("Use an https link from Google Maps. Or clear the link and type the place.").assertExists()
        onNodeWithText("Venue name").assertIsEnabled()
    }

    @Test
    fun aLinkThatHoldsOnlyTheNameLocksOnlyTheName() = runComposeUiTest {
        val form = Form()
        show(form)

        onNodeWithText("Google Maps link").performTextInput("https://www.google.com/maps/place/Santiago+de+Compostela")
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("Venue name").assertIsNotEnabled()
        onNodeWithText("Address").assertIsEnabled()
        onNodeWithText("City").assertIsEnabled()
        onNodeWithText("Country").assertIsEnabled()
    }
}
