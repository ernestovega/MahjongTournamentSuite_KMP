# Google Play store listing

Everything that goes into the Play Console store listing of the «MTS» app, in its 8 languages (en, es, fr, it, de, sv, nl, ru).

| Path | What | Size |
|---|---|---|
| [listing.md](listing.md) | Name, short and full description of each language | |
| `phone/<lang>/` | Phone screenshots, 8 per language | 1080 × 1920 |
| `tablet/<lang>/` | Tablet and Chromebook screenshots, 6 per language (shots 1 to 6) | 1920 × 1080 |
| `feature-graphic/<lang>.png` | Feature graphic (made by hand on 16 November 2026) | 1024 × 500 |

The images live in Git LFS (see `.gitattributes`).

## Screenshots

The screenshots are made by `StoreScreenshotsTest` (playerApp, jvmTest): the real screens of the Player over the data of
the screenshot tests (`ScreenshotData`), in a green frame with a caption, written straight into the folders above. Record
them on Linux, like the screenshot tests:

```bash
MTS_STORE_SCREENSHOTS=1 ./gradlew :playerApp:jvmTest --tests '*StoreScreenshotsTest*' --rerun
# only some languages or one form:
MTS_STORE_SCREENSHOTS=1 MTS_STORE_LANGUAGES=en,es MTS_STORE_FORMS=phone ./gradlew :playerApp:jvmTest --tests '*StoreScreenshotsTest*' --rerun
```

| # | Screen | Phone | Tablet |
|---|---|:-:|:-:|
| 1 | Tournaments, All tab with the own marks | ✅ | ✅ |
| 2 | Own table in play, with the round clock and the hand buttons | ✅ | ✅ |
| 3 | Tournament ranking | ✅ | ✅ |
| 4 | My results | ✅ | ✅ |
| 5 | Statistics of the own player | ✅ | ✅ |
| 6 | Registrations of an upcoming tournament | ✅ | ✅ |
| 7 | Tournament info (progress, organizers, venue) | ✅ | |
| 8 | The 81 MCR combinations | ✅ | |

Without a Linux machine at hand, run Actions > Store · Screenshots > Run workflow and take the images from its artifact.

The captions of each language are in the test (`Shot.captions`). To change a shot, its caption or the frame, edit the test
and run it again; then upload the new images in Play Console.
