# Web E2E tests

The Player web app (Kotlin/Wasm) runs in Chromium through [Playwright](https://playwright.dev), against the local test
backend (Firebase Auth and Firestore emulators with the e2e seed, see [docs/test-environment.md](../../docs/test-environment.md)).
Nothing touches production or staging.

The app draws on a canvas. With `mts.a11y` = `on` in `localStorage` it also writes its accessibility tree in the DOM
(`playerApp/src/wasmJsMain/.../main.kt`). The tests find buttons and texts in that tree and click on their place on the
canvas. Users never get the tree: it is off unless the key is set.

## Run on your computer

```bash
# 1. The test backend (port 5055), in another terminal
cd firebase/functions && npm run e2e:backend

# 2. The web app
./gradlew :playerApp:wasmJsBrowserDistribution

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
  app keeps the screen of the address and the session.
- The text nodes of the tree have no size, so `seeText` reads the tree (`ariaSnapshot`) instead of waiting for a
  visible element.

## Tests

- `tests/player.test.mjs`: a guest opens a public tournament from the list and from its link; a player signs in,
  goes to the own table and saves a draw.
- `tests/registrations.test.mjs`: creates a tournament with 4 places and open registrations. A person without an
  account registers from its public link and confirms with the link of the email, the organization fills the places,
  the next person goes to the waiting list, the first one cancels, and the organization gives the place to the one who
  waited (the waiting list moves only by hand). The emails come from `GET /__e2e/mail` of the test backend.
