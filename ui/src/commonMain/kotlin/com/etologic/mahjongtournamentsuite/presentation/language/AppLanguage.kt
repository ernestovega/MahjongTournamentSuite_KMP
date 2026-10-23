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
 * The languages of the apps. [System] follows the language of the device.
 * [endonym] is the name of the language in the language itself, so a user finds it in any language.
 */
enum class AppLanguage(val tag: String?, val endonym: String?) {
    System(null, null),
    English("en", "English"),
    Spanish("es", "Español"),
    ;

    companion object {
        /** The language with this tag, or [System] when the tag is empty or unknown. */
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag != null && it.tag == tag } ?: System
    }
}

/** The language that the user chose and the function that changes it. */
@Immutable
class LanguageController(
    val language: AppLanguage,
    val onChange: (AppLanguage) -> Unit,
)

val LocalLanguageController = staticCompositionLocalOf { LanguageController(AppLanguage.System) {} }

/**
 * Applies the saved language before [content] draws, and draws [content] again when the user changes the language.
 * The texts of the screens come from the language of the platform, so the content starts new after a change.
 */
@Composable
fun ProvideAppLanguage(
    store: LanguageStore = PlatformLanguageStore,
    content: @Composable () -> Unit,
) {
    var chosen by remember(store) { mutableStateOf(AppLanguage.fromTag(store.load())) }
    remember(store, chosen) { store.apply(chosen.tag) }
    val playerName = rememberTemplate(Res.string.rival_player)
    remember(playerName) {
        PlaceholderNames.playerTemplate = { number -> playerName(number) }
    }
    val controller = remember(store, chosen) {
        LanguageController(chosen) { next ->
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
    fun save(tag: String?)

    /** Makes the platform use [tag]. A null tag brings back the language of the device. */
    fun apply(tag: String?)
}

internal object PlatformLanguageStore : LanguageStore {
    override fun load(): String? = LanguagePreferenceStorage.load()?.takeIf { it.isNotBlank() }
    override fun save(tag: String?) = LanguagePreferenceStorage.save(tag.orEmpty())
    override fun apply(tag: String?) = applyPlatformLanguage(tag)
}

internal expect object LanguagePreferenceStorage {
    fun load(): String?
    fun save(value: String)
}

internal expect fun applyPlatformLanguage(tag: String?)
