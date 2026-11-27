package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.OwnRegistration
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.player.MainTab
import com.etologic.mahjongtournamentsuite.player.PlayerTab
import com.etologic.mahjongtournamentsuite.player.TableScreen
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerRoute
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerScreen
import com.etologic.mahjongtournamentsuite.player.TournamentScreen
import com.etologic.mahjongtournamentsuite.player.TournamentsScreen
import com.etologic.mahjongtournamentsuite.presentation.theme.GangOfThreeFontFamily
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.UPCOMING
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotRegistrationRepository
import com.etologic.mahjongtournamentsuite.screenshots.otherLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.module.Module
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The screenshots of the Google Play listing (docs/play-store/README.md): each screen inside a frame with a caption,
 * in every language, for phones (1080 × 1920) and tablets (1920 × 1080). They are written straight into
 * `docs/play-store/<phone|tablet>/<language>/`, replacing the ones there. It runs only when asked:
 *
 * `MTS_STORE_SCREENSHOTS=1 ./gradlew :playerApp:jvmTest --tests '*StoreScreenshotsTest*' --rerun --no-configuration-cache`
 *
 * (`--rerun`: Gradle does not see the variables, so without it a test run that passed counts as up to date.
 * `--no-configuration-cache`: a reused configuration cache keeps the variables of the run that stored it.)
 *
 * `MTS_STORE_LANGUAGES=en,es` and `MTS_STORE_FORMS=phone` make only those. The App Store ones (iPhone 6.9"
 * 1320 × 2868 and iPad 13" 2064 × 2752, in `docs/app-store/<iphone|ipad>/<language>/`) are made only when asked:
 * `MTS_STORE_FORMS=iphone,ipad`.
 *
 * Record on Linux, like the other screenshots: macOS draws the texts a little differently.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class StoreScreenshotsTest {
    /** A store device: the image size in pixels, the density of the screen inside, and where the frame puts things. */
    private enum class Form(
        val dir: String,
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val captionHeight: Dp,
        val captionSize: TextUnit,
        val cardWidth: Dp,
        val cardHeight: Dp,
        val store: String = "play-store",
        val wide: Boolean = false,
    ) {
        // 432 × 768 dp: a phone screen of 376 × 624 dp under the caption.
        Phone("phone", 1080, 1920, 2.5f, 128.dp, 26.sp, 376.dp, 624.dp),

        // 1280 × 720 dp: a big tablet or Chromebook screen of 1030 × 580 dp under the caption.
        Tablet("tablet", 1920, 1080, 1.5f, 112.dp, 36.sp, 1030.dp, 580.dp, wide = true),

        // App Store, iPhone 6.9": 440 × 956 dp, a phone screen of 400 × 790 dp under the caption.
        IPhone("iphone", 1320, 2868, 3f, 140.dp, 28.sp, 400.dp, 790.dp, store = "app-store"),

        // App Store, iPad 13": 1032 × 1376 dp, a tablet screen of 940 × 1200 dp under the caption.
        IPad("ipad", 2064, 2752, 2f, 150.dp, 40.sp, 940.dp, 1200.dp, store = "app-store", wide = true),
    }

    /** One screenshot: its number and name in the file name, the caption of each language, the screen and its wait. */
    private class Shot(
        val number: Int,
        val name: String,
        val captions: Map<String, String>,
        val tablet: Boolean = true,
        val modules: () -> List<Module> = { playerModules(tournaments = StoreTournamentRepository()) },
        val ready: ComposeUiTest.() -> Unit,
        val content: @Composable (NavHostController) -> Unit,
    )

    /** Every language, or only those of `MTS_STORE_LANGUAGES` (for example `en,es`). */
    private val languages = (listOf("en") + otherLanguages).filter { language ->
        System.getenv("MTS_STORE_LANGUAGES")?.split(',')?.map(String::trim)?.contains(language) ?: true
    }

    /** The forms of Google Play, or those of `MTS_STORE_FORMS` (`phone`, `tablet`, `iphone`, `ipad`). */
    private val forms = Form.entries.filter { form ->
        System.getenv("MTS_STORE_FORMS")?.split(',')?.contains(form.dir) ?: (form.store == "play-store")
    }

    private val shots = listOf(
        Shot(
            1,
            "tournaments",
            captions(
                en = "Follow every tournament", es = "Sigue todos los torneos", fr = "Suivez tous les tournois",
                it = "Segui tutti i tornei", de = "Verfolge jedes Turnier", sv = "Följ alla turneringar",
                nl = "Volg elk toernooi", ru = "Следите за всеми турнирами",
            ),
            ready = { openStartTab("tournaments_tab_all") },
        ) { nav -> TournamentsScreen(nav) },
        Shot(
            2,
            "my-tables",
            captions(
                en = "Your tables, round by round", es = "Tus mesas, ronda a ronda", fr = "Vos tables, manche par manche",
                it = "I tuoi tavoli, turno per turno", de = "Deine Tische, Runde für Runde", sv = "Dina bord, rond för rond",
                nl = "Je tafels, ronde per ronde", ru = "Ваши столы по раундам",
            ),
            ready = {
                openTab(MainTab.TABLES)
                val me = playerText("tab_tables_me")
                waitForText(me)
                onAllNodesWithText(me).onFirst().performClick()
                waitForIdle()
                waitForText("LUCÍA MARTÍN", substring = true)
            },
        ) { nav -> TournamentScreen(nav, ONGOING) },
        Shot(
            3,
            "table-in-play",
            captions(
                en = "Enter the hands at your table", es = "Anota las manos en tu mesa", fr = "Notez les mains à votre table",
                it = "Segna le mani al tuo tavolo", de = "Trage die Hände am Tisch ein", sv = "För in händerna vid bordet",
                nl = "Noteer de handen aan je tafel", ru = "Записывайте раздачи за столом",
            ),
            ready = {
                waitForText("LUCÍA MARTÍN", substring = true)
                val addWin = playerText("entry_add_win")
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(addWin, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { nav -> TableScreen(nav, ONGOING, roundId = 3, tableId = 1) },
        Shot(
            4,
            "tournament-ranking",
            captions(
                en = "Live rankings", es = "Clasificaciones en directo", fr = "Classements en direct",
                it = "Classifiche in diretta", de = "Live-Ranglisten", sv = "Ranking live",
                nl = "Live klassementen", ru = "Рейтинги в реальном времени",
            ),
            ready = { openTab(MainTab.RANKING) },
        ) { nav -> TournamentScreen(nav, ONGOING) },
        Shot(
            5,
            "my-results",
            captions(
                en = "Share your results", es = "Comparte tus resultados", fr = "Partagez vos résultats",
                it = "Condividi i tuoi risultati", de = "Teile deine Ergebnisse", sv = "Dela dina resultat",
                nl = "Deel je resultaten", ru = "Делитесь результатами",
            ),
            ready = {
                waitForText("EMA 04010001", substring = true)
                waitForText(playerText("tab_my_results"))
            },
        ) { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1, tab = PlayerTab.SUMMARY)) },
        Shot(
            6,
            "statistics",
            captions(
                en = "Your skills and your hands", es = "Tus habilidades y tus manos", fr = "Vos compétences et vos mains",
                it = "Le tue abilità e le tue mani", de = "Deine Fähigkeiten und Hände", sv = "Dina färdigheter och händer",
                nl = "Je vaardigheden en handen", ru = "Ваши навыки и раздачи",
            ),
            ready = {
                openStartTab(null)
                val title = playerText("stats_title")
                onAllNodesWithText(title).onFirst().performClick()
                waitForText(uiText("stats_skills"), substring = true)
            },
        ) { nav -> TournamentsScreen(nav) },
        Shot(
            7,
            "registrations",
            captions(
                en = "Register for tournaments", es = "Inscríbete en los torneos", fr = "Inscrivez-vous aux tournois",
                it = "Iscriviti ai tornei", de = "Melde dich für Turniere an", sv = "Anmäl dig till turneringar",
                nl = "Schrijf je in voor toernooien", ru = "Регистрируйтесь на турниры",
            ),
            modules = {
                playerModules(
                    tournaments = StoreTournamentRepository(),
                    registrations = ScreenshotRegistrationRepository(
                        own = OwnRegistration("04010001", "LUCÍA MARTÍN", "ES", RegistrationStatus.CONFIRMED, 1, teamId = "t-red"),
                    ),
                )
            },
            // The status card and the own place push the list under the fold: wait for the places line, which is on screen.
            ready = { waitForText("14", substring = true) },
        ) { nav -> TournamentScreen(nav, UPCOMING) },
        Shot(
            8,
            "tournament-info",
            captions(
                en = "Schedule, venue and directions", es = "Horario, sede y cómo llegar", fr = "Horaires, lieu et itinéraire",
                it = "Orari, sede e indicazioni", de = "Zeitplan, Ort und Anfahrt", sv = "Schema, plats och väg dit",
                nl = "Schema, locatie en route", ru = "Расписание, место и маршрут",
            ),
            ready = {
                openTab(MainTab.INFO)
                // Scrolls down to the schedule, so the venue card above it is whole on screen.
                val schedule = playerText("info_schedule")
                waitForText(schedule)
                onAllNodesWithText(schedule).onFirst().performScrollTo()
                waitForIdle()
            },
        ) { nav -> TournamentScreen(nav, ONGOING) },
    )

    @Test
    fun storeScreenshots() {
        if (System.getenv("MTS_STORE_SCREENSHOTS") != "1") return
        val failures = mutableListOf<String>()
        Dispatchers.setMain(Dispatchers.Unconfined)
        val locale = Locale.getDefault()
        for (form in forms) {
            for (language in languages) {
                val dir = File("../docs/${form.store}/${form.dir}/$language").apply { mkdirs() }
                dir.listFiles { f -> f.extension == "png" }?.forEach(File::delete)
                Locale.setDefault(Locale.forLanguageTag(language))
                for (shot in shots.filter { !form.wide || it.tablet }) {
                    val file = File(dir, "${shot.number}-${shot.name}.png")
                    try {
                        runDesktopComposeUiTest(form.widthPx, form.heightPx) {
                            setContent { StoreFrame(form, shot.captions.getValue(language)) { PlayerFrame(shot.modules, dark = false, content = shot.content) } }
                            waitForIdle()
                            val notReady = runCatching { shot.ready(this) }.exceptionOrNull()
                            waitForIdle()
                            // A screen that never got ready goes to build/, so the image shows why.
                            val target = if (notReady == null) file else File("build/outputs/store-not-ready/${form.dir}-$language-${file.name}").apply { parentFile.mkdirs() }
                            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", target)
                            if (notReady != null) throw AssertionError("Not ready, see playerApp/${target.path}: ${notReady.message}", notReady)
                        }
                    } catch (e: Throwable) {
                        failures += "${form.dir}/$language/${file.name}: ${e.message}"
                    }
                }
            }
        }
        Locale.setDefault(locale)
        Dispatchers.resetMain()
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} store screenshot(s) failed:\n" + failures.joinToString("\n"))
    }

    /** The green frame: the caption on top and the screen in a rounded card under it. */
    @Composable
    private fun StoreFrame(form: Form, caption: String, screen: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(form.density)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(StoreGreen, StoreGreenDark))),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(form.captionHeight).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            caption.uppercase(),
                            color = Color.White,
                            fontFamily = GangOfThreeFontFamily(),
                            fontWeight = FontWeight.Normal,
                            fontSize = form.captionSize,
                            lineHeight = form.captionSize * 1.1f,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                    val shape = RoundedCornerShape(24.dp)
                    Box(
                        Modifier
                            .size(form.cardWidth, form.cardHeight)
                            .shadow(16.dp, shape)
                            .clip(shape),
                    ) { screen() }
                }
            }
        }
    }

    /** Opens a main tab of the tournament ([MainTab]) by its title in the language of the screenshot. */
    private fun ComposeUiTest.openTab(tab: Int) {
        waitForText("Madrid Open 2026", substring = true)
        val title = playerText(MainTab.title(tab).key)
        waitForText(title)
        onAllNodesWithText(title).onFirst().performClick()
        waitForIdle()
    }

    /** Waits for the start screen with the marks of the own tournaments, then opens the tab [key] (none when null). */
    private fun ComposeUiTest.openStartTab(key: String?) {
        waitForText(playerText("tournament_mark_plays").uppercase())
        if (key == null) return
        val title = playerText(key)
        waitForText(title)
        onAllNodesWithText(title).onFirst().performClick()
        waitForIdle()
    }

    private companion object {
        val StoreGreen = Color(0xFF00934A)
        val StoreGreenDark = Color(0xFF00783C)

        fun captions(en: String, es: String, fr: String, it: String, de: String, sv: String, nl: String, ru: String) =
            mapOf("en" to en, "es" to es, "fr" to fr, "it" to it, "de" to de, "sv" to sv, "nl" to nl, "ru" to ru)
    }
}

/**
 * The tournaments of an account that plays now in [ONGOING]: round 3 started 37 minutes ago on the clock, so its
 * table is in play with the time left and the buttons to enter hands.
 */
private class StoreTournamentRepository : MineTournamentRepository() {
    private fun clocks(): RoundClocks {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        return RoundClocks(
            clocks = listOf(
                RoundClock(1, startedAtMs = now - 6 * hour),
                RoundClock(2, startedAtMs = now - 4 * hour),
                RoundClock(3, startedAtMs = now - 37 * 60_000L),
            ),
            serverNowMs = now,
        )
    }

    // The first read of a screen comes from the cache, without the fresh clocks: the listed tournament has them too.
    override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> =
        AppResult.Success(tournaments.map { if (it.id == ONGOING) it.copy(roundClocks = clocks().clocks) else it })

    override suspend fun getRoundClocks(tournamentId: String): AppResult<RoundClocks> = AppResult.Success(clocks())

    // A table in play has its 16 hands, the ones not played yet empty and not done.
    private fun inPlay(table: Pair<TableState, List<TableHand>>): Pair<TableState, List<TableHand>> {
        val (state, hands) = table
        if (state.isCompleted) return table
        val empty = (hands.size + 1..16).map { TableHand(it, "", "", "", false, false, "", "", "", "") }
        return state to hands + empty
    }

    override suspend fun getTableWithHands(tournamentId: String, roundId: Int, tableId: Int, refreshMode: RefreshMode) =
        AppResult.Success(inPlay(ScreenshotData.table(tournamentId, roundId, tableId)))

    override suspend fun getRoundTablesWithHands(tournamentId: String, roundId: Int, refreshMode: RefreshMode) =
        AppResult.Success((1..4).map { inPlay(ScreenshotData.table(tournamentId, roundId, it)) })

    override suspend fun getAllTablesWithHands(tournamentId: String, refreshMode: RefreshMode) =
        AppResult.Success((1..4).flatMap { round -> (1..4).map { inPlay(ScreenshotData.table(tournamentId, round, it)) } })
}
