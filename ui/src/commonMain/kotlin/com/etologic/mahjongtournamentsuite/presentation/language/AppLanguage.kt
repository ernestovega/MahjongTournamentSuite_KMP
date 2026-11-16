package com.etologic.mahjongtournamentsuite.presentation.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames
import com.etologic.mahjongtournamentsuite.presentation.util.rememberTemplate
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.rival_player

/**
 * The languages of the apps. Without a choice of the user, the app uses the language of the device or the browser
 * when it is one of these, and [English] when it is not.
 * [endonym] is the name of the language in the language itself, so a user finds it in any language.
 * [flagCode] is the country whose flag marks the language.
 */
enum class AppLanguage(val tag: String, val endonym: String, val flagCode: String) {
    English("en", "English", "GB"),
    Spanish("es", "Español", "ES"),
    French("fr", "Français", "FR"),
    Italian("it", "Italiano", "IT"),
    German("de", "Deutsch", "DE"),
    Swedish("sv", "Svenska", "SE"),
    Dutch("nl", "Nederlands", "NL"),
    Russian("ru", "Русский", "RU"),
    ;

    companion object {
        /** The language with this tag, or [English] when the tag is empty or unknown. */
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: English

        /**
         * The first language of [tags] (the languages of the device, in order of preference) that the app has, or [English].
         * A tag matches a language by the whole tag ("de-CH") or by its first part ("es-MX" gives Spanish).
         */
        fun fromSystemTags(tags: List<String>): AppLanguage {
            for (raw in tags) {
                val tag = raw.trim().replace('_', '-').lowercase()
                if (tag.isEmpty()) continue
                entries.firstOrNull { it.tag.lowercase() == tag }?.let { return it }
                val primary = tag.substringBefore('-')
                entries.firstOrNull { it.tag.lowercase().substringBefore('-') == primary }?.let { return it }
            }
            return English
        }
    }
}

/**
 * The language that the user chose and the function that changes it.
 * [showAiNotice] is true while the app shows a translation by AI and the user has not seen the notice about it yet.
 */
@Immutable
class LanguageController(
    val language: AppLanguage,
    val showAiNotice: Boolean = false,
    val onAiNoticeSeen: () -> Unit = {},
    val onChange: (AppLanguage) -> Unit,
)

val LocalLanguageController = staticCompositionLocalOf { LanguageController(AppLanguage.English) {} }

/**
 * Applies the saved language before [content] draws, and draws [content] again when the user changes the language.
 * The texts of the screens come from the language of the platform, so the content starts new after a change.
 */
@Composable
fun ProvideAppLanguage(
    store: LanguageStore = PlatformLanguageStore,
    content: @Composable () -> Unit,
) {
    // Without a saved choice the app follows the device. The choice is not saved, so the app follows a later change of the device.
    var chosen by remember(store) {
        mutableStateOf(store.load()?.let { AppLanguage.fromTag(it) } ?: AppLanguage.fromSystemTags(store.systemTags()))
    }
    var aiNoticeSeen by remember(store) { mutableStateOf(store.aiNoticeSeen()) }
    remember(store, chosen) { store.apply(chosen.tag) }
    val playerName = rememberTemplate(Res.string.rival_player)
    remember(playerName) {
        PlaceholderNames.playerTemplate = { number -> playerName(number) }
    }
    val controller = remember(store, chosen, aiNoticeSeen) {
        LanguageController(
            language = chosen,
            showAiNotice = chosen != AppLanguage.English && !aiNoticeSeen,
            onAiNoticeSeen = {
                store.saveAiNoticeSeen()
                aiNoticeSeen = true
            },
        ) { next ->
            store.save(next.tag)
            chosen = next
        }
    }
    CompositionLocalProvider(LocalLanguageController provides controller) {
        key(chosen) { content() }
    }
}

/** Where the choice of the user is kept, and how the language reaches the platform. */
interface LanguageStore {
    fun load(): String?
    fun save(tag: String)

    /** Makes the platform use [tag]. */
    fun apply(tag: String)

    /** The languages of the device or the browser, in order of preference, as tags like "es-ES". */
    fun systemTags(): List<String> = emptyList()

    /** True when the user has seen the notice about the translations by AI. */
    fun aiNoticeSeen(): Boolean = true
    fun saveAiNoticeSeen() {}
}

internal object PlatformLanguageStore : LanguageStore {
    override fun load(): String? = LanguagePreferenceStorage.load()?.takeIf { it.isNotBlank() }
    override fun save(tag: String) = LanguagePreferenceStorage.save(tag)
    override fun apply(tag: String) = applyPlatformLanguage(tag)
    override fun systemTags(): List<String> = systemLanguageTags()
    override fun aiNoticeSeen(): Boolean = LanguagePreferenceStorage.loadAiNoticeSeen()
    override fun saveAiNoticeSeen() = LanguagePreferenceStorage.saveAiNoticeSeen()
}

internal expect object LanguagePreferenceStorage {
    fun load(): String?
    fun save(value: String)
    fun loadAiNoticeSeen(): Boolean
    fun saveAiNoticeSeen()
}

internal expect fun applyPlatformLanguage(tag: String)

/** The languages of the device or the browser, in order of preference. It does not change when the app applies its language. */
internal expect fun systemLanguageTags(): List<String>
