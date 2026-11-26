package com.etologic.mahjongtournamentsuite.data.platform

/** What the desktop app of the suite tells the data layer. */
object DesktopPlatform {
    /** The part on the screen (`player` or `admin`), for the telemetry of that part. Null runs the admin app alone. */
    @Volatile
    var currentApp: String? = null
}
