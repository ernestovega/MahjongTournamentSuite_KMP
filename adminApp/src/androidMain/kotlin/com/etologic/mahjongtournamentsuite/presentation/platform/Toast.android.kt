package com.etologic.mahjongtournamentsuite.presentation.platform

import android.widget.Toast
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

actual fun showWarningToast(message: String) {
    Toast.makeText(
        AndroidPlatformContext.requireContext(),
        message,
        Toast.LENGTH_SHORT,
    ).show()
}
