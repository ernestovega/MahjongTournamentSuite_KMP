package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TournamentIdCardPreviewButton(
    shortName: String,
    primaryColor: String,
    year: String,
    associationLogoContentType: String?,
    associationLogoBytes: ByteArray?,
    associationLogoUrl: String?,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presenter = koinInject<IdCardProofPresenter>()
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }

    FocusedButton(
        enabled = !isGenerating,
        onClick = {
            scope.launch {
                isGenerating = true
                when (val result = presenter.generate(
                    IdCardProofRequest(
                        shortName = shortName,
                        year = year,
                        primaryColor = primaryColor,
                        associationLogoContentType = associationLogoContentType,
                        associationLogoBytes = associationLogoBytes,
                        associationLogoUrl = associationLogoUrl,
                    ),
                )) {
                    is AppResult.Success -> {
                        val safeName = shortName
                            .replace(Regex("[^A-Za-z0-9_-]+"), "-")
                            .trim('-')
                            .ifBlank { "tournament" }
                        saveBinaryFile(
                            fileName = "$safeName-${year.ifBlank { "proof" }}-id-card-proof.pdf",
                            content = result.value,
                            mimeType = "application/pdf",
                        )
                    }
                    is AppResult.Failure -> onError(result.error.toUiMessage())
                }
                isGenerating = false
            }
        },
        modifier = modifier,
    ) {
        if (isGenerating) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
            Text("Generate ID card preview")
        }
    }
}
