package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryLink
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.language.LocalLanguageController
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.telemetry.rememberOpenLink
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.serialization.Serializable
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.apps_android_detail
import mahjongtournamentsuite.playerapp.generated.resources.apps_download
import mahjongtournamentsuite.playerapp.generated.resources.apps_intro
import mahjongtournamentsuite.playerapp.generated.resources.apps_ios
import mahjongtournamentsuite.playerapp.generated.resources.apps_ios_detail
import mahjongtournamentsuite.playerapp.generated.resources.apps_linux_detail
import mahjongtournamentsuite.playerapp.generated.resources.apps_mac_detail
import mahjongtournamentsuite.playerapp.generated.resources.apps_notice_close
import mahjongtournamentsuite.playerapp.generated.resources.apps_notice_message
import mahjongtournamentsuite.playerapp.generated.resources.apps_notice_never
import mahjongtournamentsuite.playerapp.generated.resources.apps_notice_title
import mahjongtournamentsuite.playerapp.generated.resources.apps_soon
import mahjongtournamentsuite.playerapp.generated.resources.apps_this_device
import mahjongtournamentsuite.playerapp.generated.resources.apps_title
import mahjongtournamentsuite.playerapp.generated.resources.apps_windows_detail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The page of the apps, `/download` on the web. */
@Serializable
data object AppsRoute

/** The systems that have an app of MTS. */
enum class AppPlatform {
    ANDROID,
    IOS,
    MACOS,
    WINDOWS,
    LINUX,
}

/**
 * Where each app is downloaded. A null address shows "Coming soon": set it when the app is out (docs/desktop-release.md).
 * The desktop installers keep the same name in every GitHub release, so the link of the latest release finds them.
 */
object AppDownloads {
    private const val LATEST_RELEASE = "https://github.com/ernestovega/MahjongTournamentSuite_KMP/releases/latest/download/"

    /** Google Play. Null until the app is in production. */
    val android: String? = null

    /** The App Store. Null until Apple publishes the app. */
    val ios: String? = null

    /** The desktop installers. Null until the first `desktop-v*` release of GitHub. */
    val desktop: Boolean = false

    fun url(platform: AppPlatform): String? = when (platform) {
        AppPlatform.ANDROID -> android
        AppPlatform.IOS -> ios
        AppPlatform.MACOS -> LATEST_RELEASE.takeIf { desktop }?.plus(MAC_FILE)
        AppPlatform.WINDOWS -> LATEST_RELEASE.takeIf { desktop }?.plus(WINDOWS_FILE)
        AppPlatform.LINUX -> LATEST_RELEASE.takeIf { desktop }?.plus(LINUX_FILE)
    }

    /** True when at least one app can be downloaded. Until then the web does not show the notice of the apps. */
    val anyAvailable: Boolean get() = AppPlatform.entries.any { url(it) != null }

    // The names of the files in the release. .github/workflows/desktop.yml gives them.
    const val MAC_FILE = "MTS-macos-arm64.dmg"
    const val WINDOWS_FILE = "MTS-windows-x64.msi"
    const val LINUX_FILE = "mts_amd64.deb"
}

/**
 * The web tells the player app about the apps: the system of the browser, and whether the person hid the notice of the
 * apps. Only the web has it ([LocalWebApps]): the apps themselves do not offer the apps.
 */
class WebApps(
    /** The system of the browser, which the page of the apps shows first. Null when it is not known. */
    val device: AppPlatform?,
    private val noticeHidden: () -> Boolean,
    private val hideNotice: () -> Unit,
) {
    /** True after the notice closed in this page load: it shows again on the next visit. */
    private var closedThisLoad by mutableStateOf(false)

    val showsNotice: Boolean get() = !closedThisLoad && AppDownloads.anyAvailable && !noticeHidden()

    fun closeNotice(forever: Boolean) {
        closedThisLoad = true
        if (forever) hideNotice()
    }
}

/** The apps of the web. Null in the apps. */
val LocalWebApps = staticCompositionLocalOf<WebApps?> { null }

/** The order of the page: the system of the browser first. */
internal fun appsInOrder(device: AppPlatform?): List<AppPlatform> = AppPlatform.entries.sortedBy { if (it == device) -1 else it.ordinal }

/**
 * The system of a browser from its user agent. iPadOS says it is a Mac, so a Mac with a touch screen is an iPad.
 */
fun appPlatformOfUserAgent(userAgent: String, touchPoints: Int = 0): AppPlatform? {
    val agent = userAgent.lowercase()
    return when {
        "android" in agent -> AppPlatform.ANDROID
        "iphone" in agent || "ipad" in agent || "ipod" in agent -> AppPlatform.IOS
        "macintosh" in agent || "mac os x" in agent -> if (touchPoints > 1) AppPlatform.IOS else AppPlatform.MACOS
        "windows" in agent -> AppPlatform.WINDOWS
        "cros" in agent -> null
        "linux" in agent -> AppPlatform.LINUX
        else -> null
    }
}

private val AppPlatform.icon: ImageVector
    get() = when (this) {
        AppPlatform.ANDROID -> Icons.Default.Android
        AppPlatform.IOS -> Icons.Default.PhoneIphone
        AppPlatform.MACOS -> Icons.Default.LaptopMac
        AppPlatform.WINDOWS -> Icons.Default.DesktopWindows
        AppPlatform.LINUX -> Icons.Default.Computer
    }

/** The names of the systems are brands: the same in every language, except iPhone and iPad. */
@Composable
private fun AppPlatform.title(): String = when (this) {
    AppPlatform.ANDROID -> "Android"
    AppPlatform.IOS -> stringResource(Res.string.apps_ios)
    AppPlatform.MACOS -> "Mac"
    AppPlatform.WINDOWS -> "Windows"
    AppPlatform.LINUX -> "Linux"
}

private val AppPlatform.detail: StringResource
    get() = when (this) {
        AppPlatform.ANDROID -> Res.string.apps_android_detail
        AppPlatform.IOS -> Res.string.apps_ios_detail
        AppPlatform.MACOS -> Res.string.apps_mac_detail
        AppPlatform.WINDOWS -> Res.string.apps_windows_detail
        AppPlatform.LINUX -> Res.string.apps_linux_detail
    }

/** One app: its icon, the system, where it comes from, and the button that downloads it (or "Coming soon"). */
@Composable
private fun AppRow(platform: AppPlatform, thisDevice: Boolean, onOpen: (String) -> Unit) {
    val url = AppDownloads.url(platform)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MtsTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
    ) {
        Icon(platform.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(platform.title(), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(platform.detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (thisDevice) {
                Text(
                    stringResource(Res.string.apps_this_device),
                    style = MaterialTheme.typography.labelMedium,
                    color = MtsTheme.colors.link,
                )
            }
        }
        when {
            url == null -> Text(
                stringResource(Res.string.apps_soon),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            thisDevice -> DebouncedButton(onClick = { onOpen(url) }) { ButtonText(stringResource(Res.string.apps_download)) }
            else -> DebouncedOutlinedButton(onClick = { onOpen(url) }) { ButtonText(stringResource(Res.string.apps_download)) }
        }
    }
}

/** The apps, one per row, the system of the browser first. */
@Composable
internal fun AppList(device: AppPlatform?) {
    val open = rememberOpenLink(TelemetryLink.APP_DOWNLOAD)
    Column {
        appsInOrder(device).forEachIndexed { index, platform ->
            if (index > 0) HorizontalDivider()
            AppRow(platform, thisDevice = platform == device, onOpen = open)
        }
    }
}

/** The page of the apps: `/download`, the More menu and the sign-in open it. */
@Composable
fun AppsScreen(navController: NavHostController) {
    val device = LocalWebApps.current?.device
    AppScaffold(
        title = stringResource(Res.string.apps_title),
        autoFocusFirst = false,
        onBack = { if (!navController.popBackStack()) navController.reopen(emptyList()) },
    ) {
        ScreenColumn(
            maxWidth = 640.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            scrollable = true,
            paddingInsideScroll = true,
        ) {
            SectionCard {
                Text(stringResource(Res.string.apps_intro), style = MaterialTheme.typography.bodyMedium)
                AppList(device)
            }
        }
    }
}

/** The option of the More menu that opens the page of the apps. Only the web shows it. */
@Composable
fun AppsTopBarButton(navController: NavHostController) {
    if (LocalWebApps.current == null) return
    AppTopBarButton(
        text = stringResource(Res.string.apps_title),
        icon = Icons.Default.PhoneIphone,
        onClick = { navController.navigate(AppsRoute) },
    )
}

/**
 * The notice of the apps on the list of tournaments of the web: the apps, their links, and where to find them later.
 * Close hides it until the next visit, "Don't show again" for good. It waits for the notice of the AI translations.
 */
@Composable
fun AppsNotice() {
    val apps = LocalWebApps.current ?: return
    if (LocalLanguageController.current.showAiNotice || !apps.showsNotice) return
    AppsNoticeDialog(device = apps.device, onClose = apps::closeNotice)
}

/** The dialog of [AppsNotice]. [onClose] gets true for "Don't show again". */
@Composable
internal fun AppsNoticeDialog(device: AppPlatform?, onClose: (forever: Boolean) -> Unit) {
    TrackedDialog("AppsNotice") {
        AlertDialog(
            onDismissRequest = { onClose(false) },
            title = { MtsDialogTitle(stringResource(Res.string.apps_notice_title)) },
            text = {
                ScrollableColumnWithScrollbar(
                    state = rememberScrollState(),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
                ) {
                    MtsDialogText(stringResource(Res.string.apps_notice_message))
                    AppList(device)
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    DebouncedButton(onClick = { onClose(false) }) { ButtonText(stringResource(Res.string.apps_notice_close)) }
                    AppTextButton(onClick = { onClose(true) }) { ButtonText(stringResource(Res.string.apps_notice_never)) }
                }
            },
        )
    }
}
