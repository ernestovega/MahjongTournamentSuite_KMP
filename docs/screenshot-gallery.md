# Screenshot gallery

A web page with the reference images of the screenshot tests (docs/screenshot-tests.md) and the videos of the last
game days (docs/scenarios.md): `https://<GALLERY_SITE>.web.app`, by default `https://mts-staging-screenshots.web.app`.
It is a second Hosting site of the staging project, so it never touches the staging web apps.

## What it shows

- **Screens**: every image, grouped by module and screen, with filters by device and theme and a search. The version
  menu shows the images as they were in an older version, and `changed` keeps only what changed in it.
- **Changes**: the last 30 commits of main that changed images, newest first, each with its images before and after.
- **Viewer** (click an image): the image in full size, every version where it changed, and a comparison with any of
  them: side by side, a slider, or the difference (black where both are equal).
- **Game day videos**: the screen of the organizer tablet in the last 3 runs of Actions > Game day.
- At the top, whether the images are up to date:
  - the last run of Actions > Screenshot tests, and how many commits of main came after it;
  - for each module, whether its main code (`src/*Main`, plus `ui`, `designsystem` and `screenshotTesting` where
    they apply) changed after its images. That is only a hint: running the tests is the real check.

## How it stays small

- Each image is stored once, named by its Git LFS id, in `img/` (the original PNG, opened only in the viewer) and
  `thumb/` (a WebP 440 px high, about 8 KB, loaded lazily in the lists). An image that did not change in a version
  costs nothing more.
- Only the last 30 versions are kept (`DEFAULT_VERSIONS` in `scripts/gallery/gallery.py`). About 40 MB in total.
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
