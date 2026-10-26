package com.etologic.mahjongtournamentsuite.presentation.language

import com.etologic.mahjongtournamentsuite.presentation.components.rememberMenuScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.language_label
import org.jetbrains.compose.resources.stringResource

/**
 * The choice of the language of the app: one of the languages of the app.
 * It is a dropdown that the user cannot type in. Each language has a flag and its own name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val controller = LocalLanguageController.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = controller.language.endonym,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(Res.string.language_label)) },
            leadingIcon = { LanguageFlag(controller.language) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            scrollState = rememberMenuScrollState(expanded, AppLanguage.entries.indexOf(controller.language)),
        ) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.endonym) },
                    leadingIcon = { LanguageFlag(option) },
                    onClick = {
                        expanded = false
                        controller.onChange(option)
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
