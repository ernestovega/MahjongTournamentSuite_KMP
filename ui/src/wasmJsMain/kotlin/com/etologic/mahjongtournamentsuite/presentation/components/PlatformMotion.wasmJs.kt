package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun prefersReducedMotion(): Boolean = js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

@Composable
actual fun rememberReduceMotion(): Boolean = remember { prefersReducedMotion() }
