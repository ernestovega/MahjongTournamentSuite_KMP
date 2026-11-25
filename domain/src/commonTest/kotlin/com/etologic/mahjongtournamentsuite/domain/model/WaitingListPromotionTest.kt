package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Who takes a free place, as the server gives it (docs/registrations.md, "Waiting list"). */
class WaitingListPromotionTest {
    private fun person(emaId: String, status: RegistrationStatus, position: Int) = AdminRegistration(emaId, "P$emaId", "ES", status, position)

    private val placed = (1..4).map { person("1$it", RegistrationStatus.CONFIRMED, it) }
    private val waiting = listOf(person("22", RegistrationStatus.WAITING, 2), person("21", RegistrationStatus.WAITING, 1))
    private val pending = person("31", RegistrationStatus.PENDING, 0)

    private fun list(maxPlayers: Int?, people: List<AdminRegistration> = placed + waiting + pending) = AdminRegistrations(
        settings = RegistrationSettings(maxPlayers = maxPlayers),
        isTeams = false,
        drawPending = true,
        registrations = people,
        teams = emptyList(),
    )

    @Test
    fun thePlaceOfAPersonWithAPlaceGoesToTheFirstOfTheWaitingList() {
        assertEquals("21", list(4).takesThePlaceOf(placed[2])?.emaId)
    }

    @Test
    fun nobodyTakesThePlaceOfAPersonWithoutAPlace() {
        assertNull(list(4).takesThePlaceOf(waiting[0]))
        assertNull(list(4).takesThePlaceOf(pending))
    }

    @Test
    fun nobodyMovesWithoutANumberOfPlacesOrWithoutAWaitingList() {
        assertNull(list(null).takesThePlaceOf(placed[0]))
        assertNull(list(4, placed).takesThePlaceOf(placed[0]))
    }

    @Test
    fun nobodyMovesWhileThePlacesAreStillFullAfterTheRemoval() {
        // The organizer gave places by hand above the limit: one fewer still fills it.
        val above = placed + person("15", RegistrationStatus.CONFIRMED, 5) + waiting
        assertNull(list(4, above).takesThePlaceOf(placed[0]))
    }
}
