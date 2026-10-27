package com.etologic.mahjongtournamentsuite.presentation.language

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.language_label
import org.jetbrains.compose.resources.stringResource

/**
 * The choice of the language of the app: one of the languages of the app.
 * It is a dropdown that the user cannot type in. Each language has a flag and its own name.
 */
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val controller = LocalLanguageController.current
    val options = remember {
        AppLanguage.entries.map { language -> SelectOption(language, language.endonym, leadingIcon = { LanguageFlag(language) }) }
    }
    SelectField(
        label = stringResource(Res.string.language_label),
        options = options,
        selected = controller.language,
        onSelect = controller.onChange,
        modifier = modifier,
    )
}

@Composable
private fun LanguageFlag(language: AppLanguage) {
    Box(Modifier.size(width = 24.dp, height = 18.dp), contentAlignment = Alignment.Center) {
        CountryFlag(code = language.flagCode, width = 24.dp)
    }
}
