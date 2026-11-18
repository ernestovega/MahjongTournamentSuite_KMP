package com.etologic.mahjongtournamentsuite.presentation.language

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.AppTooltipBox
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.theme.TitleFontFamily
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
    // Each name in a font that has its letters: Gang of Three has no Cyrillic, so "Русский" would be empty.
    val fonts = AppLanguage.entries.associateWith { TitleFontFamily(it.tag) }
    val options = remember(fonts) {
        AppLanguage.entries.map { language ->
            SelectOption(language, language.endonym, leadingIcon = { LanguageFlag(language) }, fontFamily = fonts.getValue(language))
        }
    }
    SelectField(
        label = stringResource(Res.string.language_label),
        options = options,
        selected = controller.language,
        onSelect = controller.onChange,
        modifier = modifier,
    )
}

/**
 * A compact choice of the language for a corner: only the flag of the current language and an arrow. The menu lists
 * every language with its flag and its own name. The name of the button is read by screen readers and shown as a tooltip.
 */
@Composable
fun CompactLanguageSelector(modifier: Modifier = Modifier) {
    val controller = LocalLanguageController.current
    val label = stringResource(Res.string.language_label)
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AppTooltipBox(label) {
            FocusedTextButton(
                onClick = { open = true },
                contentPadding = PaddingValues(horizontal = 8.dp),
                buttonModifier = Modifier.semantics { contentDescription = "$label: ${controller.language.endonym}" },
            ) {
                LanguageFlag(controller.language)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.endonym, fontFamily = TitleFontFamily(language.tag)) },
                    leadingIcon = { LanguageFlag(language) },
                    onClick = {
                        open = false
                        controller.onChange(language)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageFlag(language: AppLanguage) {
    Box(Modifier.size(width = 24.dp, height = 18.dp), contentAlignment = Alignment.Center) {
        CountryFlag(code = language.flagCode, width = 24.dp)
    }
}
