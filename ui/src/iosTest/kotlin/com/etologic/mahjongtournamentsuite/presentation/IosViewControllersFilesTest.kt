package com.etologic.mahjongtournamentsuite.presentation

import com.etologic.mahjongtournamentsuite.presentation.platform.IosViewControllers
import com.etologic.mahjongtournamentsuite.presentation.platform.toByteArray
import com.etologic.mahjongtournamentsuite.presentation.platform.toNSData
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The files that the share sheet and the "Save to Files" picker of iOS take: written whole, with a valid name. */
class IosViewControllersFilesTest {
    @Test
    fun bytesGoToFoundationAndComeBackWhole() {
        val bytes = ByteArray(300) { (it - 128).toByte() }
        assertContentEquals(bytes, bytes.toNSData().toByteArray())
    }

    @Test
    fun noBytesAreAnEmptyFile() {
        assertEquals(0uL, ByteArray(0).toNSData().length)
        assertContentEquals(ByteArray(0), ByteArray(0).toNSData().toByteArray())
    }

    @Test
    fun theFileToShareHoldsTheBytesUnderAValidName() {
        val bytes = "Ranking Madrid Open 2026 · Ñ".encodeToByteArray()
        val url = IosViewControllers.temporaryFile("Ranking 2026/11/28.xlsx", bytes)
        assertEquals("Ranking 2026-11-28.xlsx", url.lastPathComponent)
        val written = assertNotNull(NSData.dataWithContentsOfURL(url))
        assertContentEquals(bytes, written.toByteArray())
    }

    @Test
    fun theSameNameAgainReplacesTheFile() {
        IosViewControllers.temporaryFile("players.csv", "old,longer content".encodeToByteArray())
        val url = IosViewControllers.temporaryFile("players.csv", "new".encodeToByteArray())
        assertEquals("new", NSData.dataWithContentsOfURL(url)!!.toByteArray().decodeToString())
    }
}
