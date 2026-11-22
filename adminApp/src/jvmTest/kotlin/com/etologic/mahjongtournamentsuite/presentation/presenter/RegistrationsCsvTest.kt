package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationTeam
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The export of the registrations. testdata/registrations-export.csv is the file of [SAMPLE]: the backend test
 * (registrationImport.test.ts) reads the same file with the import, so the export always goes back in.
 * Run with `-DwriteRegistrationsExport=true` to write the file again after a change of the format.
 */
class RegistrationsCsvTest {
    private val sampleFile: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "testdata") }
        .first { it.isDirectory }
        .resolve("registrations-export.csv")

    @Test
    fun theSampleIsTheSharedFileThatTheImportReads() {
        val text = RegistrationsCsv.build(SAMPLE, REGISTRY)
        if (System.getProperty("writeRegistrationsExport") == "true") sampleFile.writeText(text)
        assertEquals(sampleFile.readText(), text)
    }

    @Test
    fun theHeaderHasTheTitlesThatTheImportLooksFor() {
        val text = RegistrationsCsv.build(SAMPLE, REGISTRY)
        assertTrue(text.startsWith(RegistrationsCsv.BOM))
        val header = text.removePrefix(RegistrationsCsv.BOM).lineSequence().first()
        assertEquals("Timestamp,Email,First name,Last name,EMA number,Country,Team,Food restrictions,Status,Paid", header)
    }

    @Test
    fun rowsGoByPlacesThenWaitingListThenPending() {
        val lines = RegistrationsCsv.build(SAMPLE, REGISTRY).trimEnd().split("\r\n").drop(1)
        assertEquals(listOf("04010001", "04010002", "04010003", "04010004"), lines.map { it.split(",")[4] })
    }

    @Test
    fun namesComeFromTheRegistryOrFromTheRegistration() {
        val lines = RegistrationsCsv.build(SAMPLE, REGISTRY).trimEnd().split("\r\n").drop(1)
        assertTrue(lines[0].contains(",Lucía,Martín,"), lines[0])
        // Not in the registry: the whole name goes in First name.
        assertTrue(lines[3].contains(",Somebody Else,,"), lines[3])
    }

    @Test
    fun cellsWithCommasQuotesAndLineBreaksAreQuoted() {
        assertEquals("plain", RegistrationsCsv.escape("plain"))
        assertEquals("\"a, b\"", RegistrationsCsv.escape("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", RegistrationsCsv.escape("say \"hi\""))
        assertEquals("\"one\ntwo\"", RegistrationsCsv.escape("one\ntwo"))
        assertEquals("\"a;b\"", RegistrationsCsv.escape("a;b"))
    }

    @Test
    fun withoutTeamsTheTeamColumnIsEmpty() {
        val lines = RegistrationsCsv.build(SAMPLE.copy(isTeams = false), REGISTRY).trimEnd().split("\r\n").drop(1)
        assertTrue(lines.all { it.split(",")[6].isEmpty() }, lines.joinToString("\n"))
        assertTrue(lines[0].contains(",ES,,"), lines[0])
    }

    @Test
    fun theFileNameComesFromTheTournament() {
        assertEquals("registrations-berlin-spring-2099.csv", RegistrationsCsv.fileName("Berlin Spring 2099"))
        assertEquals("registrations-mcr-open.csv", RegistrationsCsv.fileName("  MCR / Open!! "))
        assertEquals("registrations.csv", RegistrationsCsv.fileName("ÑÑ"))
    }

    private companion object {
        val REGISTRY = listOf(
            Player("04010001", "Lucía", "Martín", "ES"),
            Player("04010002", "Hugo", "García", "ES"),
            Player("04010003", "Claire", "Dubois", "FR"),
        )

        val SAMPLE = AdminRegistrations(
            settings = RegistrationSettings(maxPlayers = 2, confirmed = 2, waiting = 1),
            isTeams = true,
            drawPending = true,
            registrations = listOf(
                // Out of order on purpose: the file sorts them.
                AdminRegistration(
                    emaId = "04010004",
                    name = "Somebody Else",
                    country = "",
                    status = RegistrationStatus.PENDING,
                    position = 1,
                    email = "else@example.test",
                    registeredAt = "2099-01-04T10:00:00Z",
                ),
                AdminRegistration(
                    emaId = "04010003",
                    name = "CLAIRE DUBOIS",
                    country = "FR",
                    status = RegistrationStatus.WAITING,
                    position = 1,
                    teamId = "t2",
                    email = "claire@example.test",
                    foodRestrictions = "Vegetarian, no \"spicy\" food",
                    registeredAt = "2099-01-03T10:00:00Z",
                ),
                AdminRegistration(
                    emaId = "04010002",
                    name = "HUGO GARCÍA",
                    country = "ES",
                    status = RegistrationStatus.CONFIRMED,
                    position = 2,
                    teamId = "t1",
                    email = "hugo@example.test",
                    registeredAt = "2099-01-02T10:00:00Z",
                ),
                AdminRegistration(
                    emaId = "04010001",
                    name = "LUCÍA MARTÍN",
                    country = "ES",
                    status = RegistrationStatus.CONFIRMED,
                    position = 1,
                    teamId = "t1",
                    email = "lucia@example.test",
                    paid = true,
                    registeredAt = "2099-01-01T10:00:00Z",
                ),
            ),
            teams = listOf(
                RegistrationTeam("t1", "Red Dragons", "04010001", listOf("04010001", "04010002")),
                RegistrationTeam("t2", "East Wind", "04010003", listOf("04010003")),
            ),
        )
    }
}
