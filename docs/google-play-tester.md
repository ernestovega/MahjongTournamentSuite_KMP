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
