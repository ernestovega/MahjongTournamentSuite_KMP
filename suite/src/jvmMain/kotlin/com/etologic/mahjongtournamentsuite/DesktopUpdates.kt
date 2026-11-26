package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.presentation.components.UpdateAvailableDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A version of the desktop app published on GitHub, and the page where it is downloaded. */
internal data class DesktopRelease(val version: String, val page: String)

/** The releases of the desktop app are tagged `desktop-v<version>` (docs/desktop-release.md). */
internal const val DESKTOP_TAG_PREFIX = "desktop-v"

private const val RELEASES_API = "https://api.github.com/repos/ernestovega/mahjongtournamentsuite_kmp/releases?per_page=30"

/**
 * Shows [UpdateAvailableDialog] when GitHub has a newer desktop release than this one. It looks once per start, and
 * only in an installed app: a run from Gradle or the IDE is not a release.
 */
@Composable
internal fun DesktopUpdateCheck() {
    var release by remember { mutableStateOf<DesktopRelease?>(null) }
    LaunchedEffect(Unit) {
        if (System.getProperty("jpackage.app-path") == null) return@LaunchedEffect
        release = withContext(Dispatchers.IO) { runCatching { fetchReleasesJson() }.getOrNull() }
            ?.let { newestDesktopRelease(it) }
            ?.takeIf { isNewer(it.version, AppVersion.admin) }
    }
    val shown = release ?: return
    UpdateAvailableDialog(
        version = shown.version,
        onDownload = {
            runCatching { Desktop.getDesktop().browse(URI(shown.page)) }
            release = null
        },
        onDismiss = { release = null },
    )
}

private fun fetchReleasesJson(): String {
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    val request = HttpRequest.newBuilder(URI(RELEASES_API))
        .timeout(Duration.ofSeconds(10))
        .header("Accept", "application/vnd.github+json")
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "GitHub answered ${response.statusCode()}" }
    return response.body()
}

/** The newest published desktop release in the answer of the GitHub API, or null. Drafts and prereleases do not count. */
internal fun newestDesktopRelease(json: String): DesktopRelease? = runCatching { Json.parseToJsonElement(json).jsonArray }
    .getOrNull()
    .orEmpty()
    .mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        if (release.flag("draft") || release.flag("prerelease")) return@mapNotNull null
        val tag = release.text("tag_name") ?: return@mapNotNull null
        val page = release.text("html_url") ?: return@mapNotNull null
        if (!tag.startsWith(DESKTOP_TAG_PREFIX)) return@mapNotNull null
        DesktopRelease(tag.removePrefix(DESKTOP_TAG_PREFIX), page)
    }
    .filter { versionParts(it.version) != null }
    .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.flag(name: String): Boolean = (this[name] as? JsonPrimitive)?.booleanOrNull == true

/** True when [candidate] (such as `1.0.415`) is above [current]. */
internal fun isNewer(candidate: String, current: String): Boolean = compareVersions(candidate, current) > 0

private fun compareVersions(a: String, b: String): Int {
    val left = versionParts(a) ?: return -1
    val right = versionParts(b) ?: return 1
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}

private fun versionParts(version: String): List<Int>? = version.split('.').map { it.toIntOrNull() ?: return null }
