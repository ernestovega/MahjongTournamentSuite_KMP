package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import org.junit.Test

class DiagFilterTest : AdminE2eTest() {
    private fun tree() = c.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().joinToString("\n") { it.toDebugString() }.take(2500)
    private fun counts(): String {
        val l = hasLabel("Filter by SUI")
        return "label=${c.onAllNodes(l).fetchSemanticsNodes().size} click=${c.onAllNodes(l and hasClickAction()).fetchSemanticsNodes().size} " +
            "enabled=${c.onAllNodes(l and hasClickAction() and isEnabled()).fetchSemanticsNodes().size} unmerged=${c.onAllNodes(l, useUnmergedTree = true).fetchSemanticsNodes().size} " +
            "bounds=${c.onAllNodes(l and hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }} jorge=${c.onAllNodes(hasLabel("Jorge MARTIN")).fetchSemanticsNodes().size}"
    }

    @Test
    fun diag() {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament("Test - Live ongoing tournament")
        c.waitForAnyText("Players & results", "Tournament players")
        if (c.isOnScreen("Players & results")) c.clickMoreMenuButton("Players")
        c.waitForText("Tournament players")
        c.waitForText("SUI (6)")
        val log = StringBuilder()
        log.append("A ").append(counts()).append('\n')
        val node = c.onAllNodes(hasLabel("Filter by SUI") and hasClickAction())[0]
        val r = runCatching { node.performScrollTo() }
        log.append("scroll ").append(r.exceptionOrNull()?.message?.take(300)).append('\n')
        log.append("B ").append(counts()).append('\n')
        c.waitForIdle()
        log.append("C ").append(counts()).append('\n')
        Thread.sleep(1500)
        c.waitForIdle()
        log.append("D ").append(counts()).append('\n')
        val r2 = runCatching { c.onAllNodes(hasLabel("Filter by SUI") and hasClickAction())[0].performClick() }
        log.append("click ").append(r2.exceptionOrNull()?.message?.take(300)).append('\n')
        Thread.sleep(2000)
        c.waitForIdle()
        log.append("E ").append(counts()).append('\n')
        throw AssertionError("DIAG\n$log\n--tree--\n${tree()}")
    }
}
