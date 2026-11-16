package com.etologic.mahjongtournamentsuite.player

import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.allDrawableResources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The combinations and the penalties of the table screen point at texts and tiles that exist. */
class ReferenceListsTest {
    @Test
    fun theRulesHave81Combinations() {
        assertEquals(81, Combinations.size)
        assertEquals(81, Combinations.map { it.id }.toSet().size)
        assertEquals(Combinations.sortedBy { it.points }, Combinations, "The list goes from the cheapest to the most valuable")
    }

    @Test
    fun everyCombinationHasANameAndTilesOrADescription() {
        Combinations.forEach { combination ->
            combination.name
            if (combination.tiles == null) {
                assertTrue(combination.description != null, "${combination.id} has no description")
            } else {
                assertTrue(combination.groups.isNotEmpty(), "${combination.id} has no tiles")
                assertTrue(combination.groups.sumOf { it.size } <= 18, "${combination.id} has too many tiles")
            }
        }
    }

    @Test
    fun everyTileOfEveryCombinationHasAPicture() {
        Combinations.flatMap { it.groups.flatten() }.toSet().forEach { tile ->
            assertTrue(tile in Res.allDrawableResources, "No picture for $tile")
        }
    }

    @Test
    fun theNotationReadsSuitsHonorsAndBacks() {
        assertEquals(
            listOf(listOf("tile_s1", "tile_s9", "tile_p1"), listOf("tile_we", "tile_dr", "tile_dg", "tile_dw"), listOf("tile_back", "tile_back")),
            TileNotation.parse("19s1p ECFP XX"),
        )
        assertEquals(listOf(listOf("tile_f2")), TileNotation.parse("2f"))
    }

    @Test
    fun everyPenaltyHasAText() {
        assertEquals(26, PenaltyKind.entries.sumOf { it.items.size })
    }

    @Test
    fun theSearchLooksAtTheNameTheDescriptionAndThePoints() {
        val chicken = Combinations.first { it.id == "chicken_hand" }
        assertTrue(chicken.matches("", "Chicken hand", null))
        assertTrue(chicken.matches("chick", "Chicken hand", null))
        assertTrue(chicken.matches(" 8 ", "Chicken hand", null))
        assertFalse(chicken.matches("88", "Chicken hand", null))
        val edge = Combinations.first { it.id == "edge_wait" }
        assertTrue(edge.matches("1–2", "Edge wait", "Win with 3 or 7 having 1–2 or 8–9"))
    }

    @Test
    fun thePenaltyNameIsTheTextBeforeTheColon() {
        val text = penaltyText("Late by 10 minutes or less: 10 points deducted.")
        assertEquals("Late by 10 minutes or less: 10 points deducted.", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals(0 until 27, text.spanStyles.single().let { it.start until it.end })
        assertEquals("Too few or too many tiles.".length, penaltyText("Too few or too many tiles.").spanStyles.single().end)
    }
}
