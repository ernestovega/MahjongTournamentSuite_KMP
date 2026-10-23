package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.presentation.util.rememberTemplate
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.substitution_disqualified
import mahjongtournamentsuite.ui.generated.resources.substitution_ill
import mahjongtournamentsuite.ui.generated.resources.substitution_ill_excluded
import mahjongtournamentsuite.ui.generated.resources.substitution_late
import org.jetbrains.compose.resources.stringResource

/** A short text for lists and rankings, in the language of the app. */
@Composable
fun PlayerSubstitution.label(): String = when (reason) {
    SubstitutionReason.LATE -> stringResource(Res.string.substitution_late, fromRound)
    SubstitutionReason.ILLNESS ->
        stringResource(if (excludedFromEma) Res.string.substitution_ill_excluded else Res.string.substitution_ill, fromRound)
    SubstitutionReason.DISQUALIFIED -> stringResource(Res.string.substitution_disqualified, fromRound)
}

/** The same text as [label], as a function for code that is not a composable. */
@Composable
fun rememberSubstitutionLabel(): (PlayerSubstitution) -> String {
    val late = rememberTemplate(Res.string.substitution_late)
    val ill = rememberTemplate(Res.string.substitution_ill)
    val illExcluded = rememberTemplate(Res.string.substitution_ill_excluded)
    val disqualified = rememberTemplate(Res.string.substitution_disqualified)
    return remember(late, ill, illExcluded, disqualified) {
        { substitution ->
            when (substitution.reason) {
                SubstitutionReason.LATE -> late(substitution.fromRound)
                SubstitutionReason.ILLNESS -> (if (substitution.excludedFromEma) illExcluded else ill)(substitution.fromRound)
                SubstitutionReason.DISQUALIFIED -> disqualified(substitution.fromRound)
            }
        }
    }
}
