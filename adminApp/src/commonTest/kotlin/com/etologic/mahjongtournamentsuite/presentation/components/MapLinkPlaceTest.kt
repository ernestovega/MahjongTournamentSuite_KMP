package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.domain.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapLinkPlaceTest {
    private val countries = listOf(
        Country("ES", "Spain"),
        Country("GB", "United Kingdom"),
        Country("DE", "Germany"),
    )

    @Test
    fun readsNameAddressCityAndCountryFromAPlaceLink() {
        val place = parseMapLink(
            "https://www.google.com/maps/place/Hotel+Arts,+Carrer+de+la+Marina+19,+08005+Barcelona,+Spain/@41.38,2.19,17z",
            countries,
        )

        assertEquals(
            MapLinkPlace("Hotel Arts", "Carrer de la Marina 19, 08005 Barcelona", "Barcelona", "ES"),
            place,
        )
    }

    @Test
    fun readsASearchLinkWithoutCountry() {
        val place = parseMapLink(
            "https://www.google.com/maps/search/?api=1&query=Eskalerria%2C%20Calle%20Cipriano%20Sancho%2036%2C%20Madrid",
            countries,
        )

        assertEquals(MapLinkPlace("Eskalerria", "Calle Cipriano Sancho 36, Madrid", "Madrid", null), place)
    }

    @Test
    fun decodesUtf8AndReadsACountryWrittenInItsOwnLanguage() {
        val place = parseMapLink(
            "https://www.google.com/maps/place/Caf%C3%A9+Z%C3%BCrich,+Hauptstra%C3%9Fe+5,+10115+Berlin,+Deutschland",
            countries,
        )

        assertEquals(MapLinkPlace("Café Zürich", "Hauptstraße 5, 10115 Berlin", "Berlin", "DE"), place)
    }

    @Test
    fun aLinkOfAPlainAddressHasNoName() {
        val place = parseMapLink("https://www.google.com/maps/place/Calle+Mayor+3,+28013+Madrid,+Spain", countries)

        assertEquals(MapLinkPlace(null, "Calle Mayor 3, 28013 Madrid", "Madrid", "ES"), place)
    }

    @Test
    fun dropsThePostcodeOfAnUkCity() {
        val place = parseMapLink("https://www.google.com/maps/place/The+Venue,+1+High+St,+London+SW1A+1AA,+UK", countries)

        assertEquals("London", place?.city)
        assertEquals("GB", place?.countryCode)
    }

    @Test
    fun aSingleTextIsTheName() {
        val place = parseMapLink("https://www.google.com/maps/place/Santiago+de+Compostela", countries)

        assertEquals(MapLinkPlace("Santiago de Compostela", null, null, null), place)
    }

    @Test
    fun givesNothingForCoordinatesOrALinkWithoutAPlace() {
        assertNull(parseMapLink("https://www.google.com/maps/search/?api=1&query=40.4168,-3.7038", countries))
        assertNull(parseMapLink("https://www.google.com/maps/@40.4,-3.7,15z", countries))
        assertNull(parseMapLink("https://example.com/maps/place/Hotel", countries))
    }

    @Test
    fun recognizesShortAndFullLinks() {
        assertTrue(isShortMapLink("https://maps.app.goo.gl/abc123"))
        assertTrue(isGoogleMapsLink("https://maps.app.goo.gl/abc123"))
        assertTrue(isGoogleMapsLink("https://www.google.es/maps/place/Hotel"))
        assertTrue(isGoogleMapsLink("https://maps.google.com/?q=Hotel"))
        assertFalse(isGoogleMapsLink("http://www.google.com/maps/place/Hotel"))
        assertFalse(isGoogleMapsLink("https://www.google.com/search?q=Hotel"))
        assertFalse(isGoogleMapsLink("https://evil.example/maps/place/Hotel"))
        assertFalse(isShortMapLink("https://www.google.com/maps/place/Hotel"))
    }
}
