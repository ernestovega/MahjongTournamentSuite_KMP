# Google Play reviewer account

Google Play reviews the app with an account that must reach every feature (Play Console > App content > App access).
The account is an admin, so it reaches everything without an EMA player. It must always exist in production.

## The data

| Field | Value |
| --- | --- |
| Email | `mtsgoogleplaytester@mahjongmadrid.com` |
| Alias | Google Play Testers |
| EMA number | none |
| Global role | Admin |
| Photo | `firebase/functions/assets/google-play-tester.png` (the Play Console icon) |

The password is only in Play Console (App access). Never write it in the repository.

The same data is in code: `GOOGLE_PLAY_TESTER` in `firebase/functions/src/services/googlePlayTester.ts`. Change both
together.

## Restore it

The account exists in production since 2026-11-15 (created by hand). Run this when production is cleaned of test data
before the launch, or after any reset that deletes it (`resetAccess` deletes every account but one admin).

`firebase/functions`, with production credentials and the owner's OK (it writes to production):

```bash
npm run seed:google-play-tester                    # preview
npm run seed:google-play-tester:apply              # writes what differs
GOOGLE_PLAY_TESTER_PASSWORD='…' npm run seed:google-play-tester:apply   # also creates a deleted account
```

It fixes the alias and the role, removes an EMA number, and puts the photo when the account has none (a photo that
someone chose stays). An existing account keeps its password.

## App Store reviewer account

Apple reviews the iOS app with an account of its own, made the same way: `APP_STORE_TESTER` in
`firebase/functions/src/services/googlePlayTester.ts` (`mtsappstoretester@mahjongmadrid.com`, alias App Store Testers,
admin, no EMA number, the app icon as photo, `firebase/functions/assets/app-store-tester.png`).

The workflow Actions > Publish · App Store listing makes or fixes it in production on each run
(`npm run seed:app-store-tester:apply -- --set-password`). Its password comes from the App Store Connect API key, so
it is the same on every run, and the workflow writes it only into App Store Connect (App Review Information). Nobody
needs to know it; to see it, open App Store Connect.

### Account for the deletion test

App Review checks the account deletion (Guideline 5.1.1(v)). Deleting the demo account would leave the next review
without one, so the review notes (`docs/app-store/review-notes.txt`) ask Apple not to delete it and offer a second
account instead: `APP_STORE_DELETE_TESTER` (`mtsappstoredelete@mahjongmadrid.com`, alias App Store Delete Test,
player, no EMA number). The same workflow creates it again on each run when a review deleted it, with a password that
also comes from the API key, and writes its email and password into the review notes, never into the repository.
The notes also explain how the reviewer can create an own account from ADMIN > Users and delete it.

The link to the screen recording of the notes comes from the input `review_video_url` of the workflow, or from the
repository variable `APP_REVIEW_VIDEO_URL`. The workflow stops when it has none.
