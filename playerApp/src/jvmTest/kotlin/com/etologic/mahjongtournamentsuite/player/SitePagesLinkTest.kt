package com.etologic.mahjongtournamentsuite.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Android app takes every address of the site since 24-nov (App Links, docs/table-qr.md) and sends the pages that
 * are not the app to the browser. A new page or folder of the site must never open as the public link of a
 * tournament, `/<slug>`, in the app, and the backend must refuse its name as a link name (RESERVED_SLUGS).
 * This test reads the files that the web hosting serves, so a new folder of the site fails here first.
 */
class SitePagesLinkTest {
    /** Gradle runs the tests with the module folder as the working directory. */
    private val resources = File("src/wasmJsMain/resources")

    /** The folders that scripts/assemble-web-hosting.sh adds next to the files of the player web. */
    private val assembledFolders = listOf("api-docs")

    private val folders: List<String>
        get() = resources.listFiles().orEmpty().filter { it.isDirectory }.map { it.name } + assembledFolders

    private val files: List<String>
        get() = resources.listFiles().orEmpty().filter { it.isFile }.map { it.name } + listOf("version.json")

    private val reservedSlugs: Set<String> by lazy {
        val source = File("../firebase/functions/src/services/registrationSettings.ts").readText()
        val list = Regex("""RESERVED_SLUGS[^=]*=\s*new Set\(\[(.*?)]\)""", RegexOption.DOT_MATCHES_ALL).find(source)
            ?: error("RESERVED_SLUGS not found in registrationSettings.ts")
        Regex(""""([^"]+)"""").findAll(list.groupValues[1]).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun theTestFindsThePagesOfTheSite() {
        assertTrue("privacy" in folders && "green-book" in folders, "Folders found: $folders in ${resources.absolutePath}")
        assertTrue("index.html" in files, "Files found: $files")
        assertTrue(reservedSlugs.size > 10, "Reserved slugs: $reservedSlugs")
    }

    @Test
    fun everyFolderOfTheSiteGoesToTheBrowser() {
        for (folder in folders) {
            assertEquals(SiteLink.Browser, siteLinkOf("/$folder", emptyMap()), "/$folder")
            assertEquals(SiteLink.Browser, siteLinkOf("/$folder/", emptyMap()), "/$folder/")
            assertEquals(SiteLink.Browser, siteLinkOf("/$folder/page.html", emptyMap()), "/$folder/page.html")
        }
    }

    @Test
    fun everyFileOfTheSiteGoesToTheBrowser() {
        for (file in files) {
            assertEquals(SiteLink.Browser, siteLinkOf("/$file", emptyMap()), "/$file")
        }
    }

    @Test
    fun theBackendRefusesTheNameOfEveryFolderAndEntryAddressAsALinkName() {
        val names = folders + listOf("admin", "api", "support", "version", "t", "registration", "inscripcion")
        val free = names.filter { it !in reservedSlugs }
        assertTrue(free.isEmpty(), "Add these to RESERVED_SLUGS (registrationSettings.ts): a tournament could take them as its link: $free")
    }
}
