# Google Play reviewer account

Google Play reviews the app with an account that must reach every feature (Play Console > App content > App access).
That account and its EMA player live in production and must always exist.

## The data

EMA player (Players > EMA players):

| Field | Value |
| --- | --- |
| EMA number | `00000000` |
| First name | Google Play |
| Last name | Testers |
| Country | USA |
| Hide from the public | yes |
| Photo | `firebase/functions/assets/google-play-tester.png` (the Play Console icon) |

Account (Users):

| Field | Value |
| --- | --- |
| Email | `mtsgoogleplaytester@mahjongmadrid.com` |
| Alias | Google Play Testers |
| EMA number | `00000000` |
| Global role | Admin |
| Photo | none of its own: it shows the photo of its EMA player |

The password is only in Play Console (App access). Never write it in the repository.

The same data is in code: `GOOGLE_PLAY_TESTER` in `firebase/functions/src/services/googlePlayTester.ts`. Change both
together.

## Restore it

Both exist in production since 2026-11-15 (created by hand). Run this when production is cleaned of test data
before the launch, or after any reset that deletes them.

`firebase/functions`, with production credentials and the owner's OK (it writes to production):

```bash
npm run seed:google-play-tester                    # preview
npm run seed:google-play-tester:apply              # writes what differs
GOOGLE_PLAY_TESTER_PASSWORD='…' npm run seed:google-play-tester:apply   # also creates a deleted account
```

It creates or fixes the EMA player (with the photo when it has none), and fixes the alias, the role and the EMA number of the account. A photo that someone chose stays. An existing account
keeps its password. Run it after `resetAccess` (which deletes every account but one admin) or any data reset.

## Notes

- An account can only take an EMA number that already exists among the EMA players (otherwise the server answers 404,
  "EMA player not found"). Create the EMA player first.
- The EMA sync (`seed:players`, weekly sync) never deletes players, so `00000000` stays.
- To play hands in a tournament, the account needs a slot in an ongoing tournament: give `00000000` a slot, or add the
  account to `TESTER_ACCOUNTS` in `seedTestTournaments.ts` (it needs the account uid).
