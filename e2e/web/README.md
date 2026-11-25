# Web E2E tests

The Player and admin web apps (Kotlin/Wasm) run in Chromium through [Playwright](https://playwright.dev), against the local test
backend (Firebase Auth and Firestore emulators with the e2e seed, see [docs/test-environment.md](../../docs/test-environment.md)).
Nothing touches production or staging.

The app draws on a canvas. With `mts.a11y` = `on` in `localStorage` it also writes its accessibility tree in the DOM
(`main.kt` of each app). The tests find buttons and texts in that tree and click on their place on the
canvas. Users never get the tree: it is off unless the key is set.

## Run on your computer

```bash
# 1. The test backend (port 5055), in another terminal
cd firebase/functions && npm run e2e:backend

# 2. The web apps
./gradlew :playerApp:wasmJsBrowserDistribution :adminApp:wasmJsBrowserDistribution

# 3. The tests
cd e2e/web && npm ci && npx playwright install chromium && npm test
```

`MTS_E2E_HEADED=1 npm test` shows the browser. `MTS_E2E_API` and `MTS_E2E_WEB_ROOT` change the backend address and
the folder of the built app.

Every account of the seed has the password `Passw0rd!-e2e`. A test that changes a tournament puts it back with
`resetTournaments(...)` (`POST /__e2e/reset?only=<id>`).

## In GitHub

The workflow **Test · Web E2E** (`.github/workflows/web-e2e.yml`) runs every night at 03:30 UTC on `main`, and by hand
from the Actions tab.

## Known limits of Compose for the web (1.12)

- After a dialog or a menu closes, the accessibility tree keeps the dialog. `freshTree(page)` reloads the page; the
  app keeps the screen of the address and the session. In the admin it can show an old screen after a few screens:
  `openAdmin` opens the screen under test directly, through the screens the app restores after a reload.
- The text nodes of the tree have no size, so `seeText` reads the tree (`ariaSnapshot`) instead of waiting for a
  visible element.

## Tests

- `tests/player.test.mjs`: a guest opens a public tournament from the list and from its link; a player signs in,
  goes to the own table and saves a draw.
- `tests/registrations.test.mjs`: creates a tournament with 4 places and open registrations. A person without an
  account registers from its public link and confirms with the link of the email, the organization fills the places,
  the next person goes to the waiting list, the first one cancels, and the free place goes by itself to the one who
  waited, who gets an email. The emails come from `GET /__e2e/mail` of the test backend.
- `tests/admin-player-count.test.mjs`: the admin changes the number of players of a drawn team tournament from 28 to
  24 before it starts (team 1 leaves). The draw runs in the browser; the test checks the players, the 6 tables of
  every round and the emails.
- `tests/admin-roles.test.mjs`: `club@e2e.test` is the Club Admin of the club of `test-last-table`. The server and the
  admin app give it only that tournament and refuse the rest; after a super admin takes away "Create tournaments"
  from this person, the New tournament button goes and the server refuses the call.
