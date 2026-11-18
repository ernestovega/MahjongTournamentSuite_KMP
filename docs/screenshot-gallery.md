# Screenshot gallery

A web page with the reference images of the screenshot tests (docs/screenshot-tests.md) and the videos of the last
game days (docs/scenarios.md): `https://<GALLERY_SITE>.web.app`, by default `https://mts-staging-screenshots.web.app`.
It is a second Hosting site of the staging project, so it never touches the staging web apps.

## What it shows

The admin app opens it with **DEV > Screenshots** in the top bar of the tournament list (administrators only).

- **Screens**: every image, grouped by module and screen, with filters by device, theme and language (EN, DE, ES, FR, IT, NL, RU, SV), a search and four sizes
  (S, M, L, Fit). **One screen** shows a single screen at a time, as big as the window (J and K, or the buttons, move
  to the next one). The version menu shows the images as they were on an older date, and **Changed only** keeps what
  changed then. Versions are named by date and web version.
- **Changes**: the last 30 changes of the images on main, newest first, each image with BEFORE (red) and AFTER
  (green). **Review** opens the viewer on them.
- **Viewer** (click an image): the image in full size, the arrows at the sides (or ← and →, or a swipe on a phone)
  go to the next image, and the versions of the image are at the bottom. Compare BEFORE (red) and AFTER (green) side
  by side, with a slider, flipping between them (Space) or as a difference (black where both are equal). Z switches
  between fit and real pixels.
- **Copy reference** (the floating button, or C): copies a text with the screen, the variant, the file in the
  repository, the version and a link that opens the same image. Paste it in the chat to ask about that screen.
- **Game day videos**: the screen of the organizer tablet in the last 3 runs of Actions > Game day.
- **Status** (top right): whether the images are up to date:
  - the last run of Actions > Screenshot tests, and how many commits of main came after it;
  - for each module, whether its main code (`src/*Main`, plus `ui`, `designsystem` and `screenshotTesting` where
    they apply) changed after its images. That is only a hint: running the tests is the real check.

## How it stays small

- Each image is stored once, named by its Git LFS id, in `img/` (the original PNG, opened only in the viewer) and
  `thumb2/` (a WebP up to 900 px high, loaded lazily in the lists). An image that did not change in a version
  costs nothing more.
- Only the last 30 versions are kept (`DEFAULT_VERSIONS` in `scripts/gallery/gallery.py`). About 60 MB in total.
- The videos keep only the frames that change (variable frame rate, 960 px wide), so a still screen costs almost
  nothing and the times stay real. Only the last 3 game days are kept (`KEEP_GAME_DAYS`).
- Publishing uses the Hosting REST API: files the site already has are never uploaded again, and the runner downloads
  from Git LFS only the images the site does not have. Hosting keeps 2 versions of the site.

## How it runs

`.github/workflows/screenshot-gallery.yml` runs after every push to main that changes an image or the gallery, after
every run of Screenshot tests and Game day, and by hand (Actions > Screenshot gallery). It uses the variables of
staging (`STAGING_FIREBASE_PROJECT` and its deploy account) and creates the site the first time. The optional
variable `GALLERY_SITE` changes its name.

Locally, without publishing: `python3 scripts/gallery/gallery.py build` (needs Pillow; without `--previous` it
builds every image), then serve `build/screenshot-gallery/` with `python3 -m http.server`.
