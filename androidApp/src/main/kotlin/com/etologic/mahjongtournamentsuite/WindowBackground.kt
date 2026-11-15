package com.etologic.mahjongtournamentsuite

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.presentation.theme.storedThemePreference

/**
 * Paints the window in the background of the theme that the app uses, also when the user chose a theme in the app
 * that is not the one of the system. Until the app draws, the window shows this color and not a white screen.
 * Call it after [initAndroidApp], which the saved theme needs.
 */
internal fun Activity.useAppThemeWindowBackground() {
    val dark = when (storedThemePreference()) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.System, null ->
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    val color = getColor(if (dark) R.color.mts_background_dark else R.color.mts_background_light)
    window.setBackgroundDrawable(ColorDrawable(color))
}
