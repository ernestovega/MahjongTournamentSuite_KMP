# Screenshot tests

Every screen of both apps is drawn with fake data and compared with a reference image. A change that moves, recolors or
resizes something on a screen fails the test and shows where.

## How they work

- Tool: [Roborazzi](https://github.com/takahirom/roborazzi) on the JVM (Compose Desktop). It needs no emulator and uses
  the same desktop Compose UI tests as the rest of `jvmTest`, with the fake repositories in Koin.
- Each screen is drawn in six variants: phone (412×915 dp, compact), tablet (800×1280 dp, medium) and desktop
  (1440×900 dp, expanded), each one light and dark. Density 1. A component gallery is drawn on the phone only
  (`componentVariants`), and a dialog on the phone and the desktop (`dialogVariants`), light and dark.
- The images are in `<module>/screenshots/<name>/<size>-<theme>.png`:
  - `designsystem`: the catalog and the galleries of its components (`components/...`).
  - `ui`: the shared components (`components/...`) and the shared dialogs (`dialog-...`).
  - `adminApp`: every screen, including the Timer and the standalone ranking, and its dialogs (`dialogs/...`).
  - `playerApp`: every screen and tab, the table, and the statistics of the tournament, a player and the account.
- The helpers are in the `:screenshotTesting` module: `screenshots()` and `screenshotsInMtsTheme()`. Each app has its
  own frame: `adminScreenshots()` (adminApp) and `playerScreenshots()` (playerApp).
- The data must be the same on every run: fixed dates (never `Clock.System.now()`), fixed ids and no network images.
  The fake data is `ScreenshotData`: every tournament is in the past except the upcoming one, in 2099, because the
  screens compare dates with today. A screen that shows the time takes a `Clock` (see `RankingStandaloneScreen`), and
  the tests run in UTC.
- A screen that loads in the background needs a `ready` step, usually `waitForText(...)`. When it is not ready in
  time, the test saves what it drew to `build/outputs/roborazzi/not-ready/`.

## Fonts

The apps draw their text with the system font, which is a different font on macOS and on each Linux. On Linux, the
build points fontconfig to `screenshotTesting/fonts/fonts.conf`, so the tests see only the bundled Inter (for the text)
and the font of the titles. This way the images are the same in the cloud, in GitHub Actions and on any Linux machine.

macOS draws text in its own way, so **the reference images are recorded on Linux only** (a cloud session or the
workflow). On a Mac, `recordRoborazziJvm` is fine to look at the screens, but do not commit those images, and
`verifyRoborazziJvm` will fail on the text.

## Commands

```bash
./gradlew verifyRoborazziJvm   # compare with the reference images (every module)
./gradlew recordRoborazziJvm   # write the reference images again
./gradlew :adminApp:recordRoborazziJvm --tests '*TournamentsScreenshotTest'   # one test
```

A plain `jvmTest` draws the screens but compares nothing. When a verify fails, `build/outputs/roborazzi/` of the module
has the actual image (`*_actual.png`) and the comparison (`*_compare.png`).

## GitHub Actions

`.github/workflows/screenshots.yml` runs by hand (Actions > Screenshot tests > Run workflow), with `verify` or
`record`. It uploads the images as an artifact. The trigger on the pull requests to main is commented out until the
release.

## Git LFS

The reference images are in Git LFS (`.gitattributes`). Install it once on each machine: `brew install git-lfs` and
`git lfs install`. Without it, the images are small text pointers and `verifyRoborazziJvm` fails.
