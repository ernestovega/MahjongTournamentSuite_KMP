package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.language.AppLanguage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The public privacy policy (https://mts.mahjongmadrid.com/privacy/, linked from Google Play) has one section for each
 * language of the apps. A new [AppLanguage] fails this test until the policy has its translation.
 */
class PrivacyPolicyLanguagesTest {
    private val html = File("src/wasmJsMain/resources/privacy/index.html").readText()

    @Test
    fun thePolicyHasASectionAndALinkForEachLanguage() {
        for (language in AppLanguage.entries) {
            val tag = language.tag
            assertTrue("<section id=\"$tag\" lang=\"$tag\">" in html, "The privacy policy has no section for ${language.name} ($tag)")
            assertTrue("<a href=\"#$tag\">${language.endonym}</a>" in html, "The privacy policy has no link to ${language.name} ($tag)")
        }
    }

    @Test
    fun thePolicyHasNoSectionForALanguageTheAppsDoNotHave() {
        val sections = Regex("<section id=\"([a-z]+)\" lang=").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(AppLanguage.entries.map { it.tag }.sorted(), sections.sorted())
    }
}
