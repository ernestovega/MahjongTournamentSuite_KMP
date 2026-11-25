# Google Play store listing

Everything that goes into the Play Console store listing of the «MTS» app, in its 8 languages (en, es, fr, it, de, sv, nl, ru).

| Path | What | Size |
|---|---|---|
| [listing.md](listing.md) | Name, short and full description of each language | |
| `phone/<lang>/` | Phone screenshots, 8 per language | 1080 × 1920 |
| `tablet/<lang>/` | Tablet and Chromebook screenshots, 8 per language | 1920 × 1080 |
| `feature-graphic/<lang>.png` | Feature graphic (made by hand on 16 November 2026) | 1024 × 500 |

The images live in Git LFS (see `.gitattributes`).

## Screenshots

The screenshots are made by `StoreScreenshotsTest` (playerApp, jvmTest): the real screens of the Player over the data of
the screenshot tests (`ScreenshotData`), in a green frame with a caption, written straight into the folders above. Record
them on Linux, like the screenshot tests:

```bash
MTS_STORE_SCREENSHOTS=1 ./gradlew :playerApp:jvmTest --tests '*StoreScreenshotsTest*' --rerun --no-configuration-cache
# only some languages or one form:
MTS_STORE_SCREENSHOTS=1 MTS_STORE_LANGUAGES=en,es MTS_STORE_FORMS=phone ./gradlew :playerApp:jvmTest --tests '*StoreScreenshotsTest*' --rerun --no-configuration-cache
```

| # | Screen | Phone | Tablet |
|---|---|:-:|:-:|
| 1 | Tournaments, All tab with the own marks | ✅ | ✅ |
| 2 | Own tables of the tournament (Tables > Me), round by round | ✅ | ✅ |
| 3 | Own table in play, with the round clock and the hand buttons | ✅ | ✅ |
| 4 | Tournament ranking | ✅ | ✅ |
| 5 | My results | ✅ | ✅ |
| 6 | Statistics of the own player | ✅ | ✅ |
| 7 | Registrations of an upcoming tournament | ✅ | ✅ |
| 8 | Tournament info (progress, organizers, venue) | ✅ | ✅ |

Without a Linux machine at hand, run Actions > Store · Screenshots > Run workflow and take the images from its artifact.

The captions of each language are in the test (`Shot.captions`). To change a shot, its caption or the frame, edit the test
and run it again; then upload the listing (below).

## Uploading to Play

Actions > Publish · Play store listing > Run workflow (`.github/workflows/play-listing.yml`, `scripts/play_listing.py`)
uploads everything of the 8 languages in one Play edit: short and full description from `listing.md`, phone screenshots,
tablet screenshots (as 7-inch and 10-inch) and feature graphic. It replaces the images on Play; the title of a language
already on Play stays. Run it first with `check`, which changes nothing and lists what Play has now, then with `upload`.

- The service account of the deploy needs the Play Console permission **Manage store presence** on the app (the
  upload of the app only needs the testing tracks).
- The Play API has no Chromebook screenshots: upload `tablet/<lang>/` there by hand if you want them.
- `python3 scripts/play_listing.py check --local` checks the lengths and image sizes without Play.
