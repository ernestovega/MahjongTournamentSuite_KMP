package com.etologic.mahjongtournamentsuite.domain.model

/** A message to the support of the apps, from the "Need help?" screen. [app] is the app that sends it. */
data class SupportMessage(
    val name: String,
    val email: String,
    val message: String,
    val app: String,
    val appVersion: String,
)
