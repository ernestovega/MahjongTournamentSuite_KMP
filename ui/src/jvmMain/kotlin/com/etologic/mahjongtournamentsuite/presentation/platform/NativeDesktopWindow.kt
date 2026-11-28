package com.etologic.mahjongtournamentsuite.presentation.platform

/**
 * Code that only opens a window or a dialog of the desktop system (a save or open dialog, a window of its own). The
 * tests run without a screen, where those windows cannot open, so Kover leaves this code out of the coverage. The
 * code around it, which tests replace the dialog for, is still measured.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class NativeDesktopWindow
