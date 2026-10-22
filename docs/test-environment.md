# Test environment

The test environment runs the web apps on a local backend with fake data. Use it to try the apps without touching the production data.

## What it is

- The backend is the real API. It runs on the Firebase Auth and Firestore emulators.
- The script `firebase/functions/src/scripts/startE2eBackend.ts` starts it. The instrumented UI tests use the same script.
- The API listens at `http://localhost:5055/api`.
- The data lives in memory. Each start deletes it and writes it again.

## Data at start

- The EMA player registry (160 players).
- The `test-*` tournaments, including the live tournament `test-live-ongoing`.
- Test accounts. The password of all accounts is `Passw0rd!-e2e`.

| Account | Role |
| --- | --- |
| `admin@e2e.test` | Admin |
| `editor@e2e.test` | Editor of the live tournament and three more |
| `outsider@e2e.test` | Editor with no tournament |
| `disabled@e2e.test` | Disabled editor |
| `player@e2e.test` | Player with a table that has 11 hands |
| `seated@e2e.test` | Player with a table that has seats and no hands |
| `waiting@e2e.test` | Player with no seat in the running round |
| `done@e2e.test` | Player with a finished table |
| `stranger@e2e.test` | Player who plays in no tournament |

## How to use it

1. Start the backend: `cd firebase/functions && npm run test-env:backend`.
   In Android Studio, run the configuration **Test env - Backend**.
2. Start a web app with the variable `MTS_API_TARGET=http://localhost:5055`.
   - Android Studio: run **Test env - Player Web** or **Test env - Admin Web**.
   - Terminal: `MTS_API_TARGET=http://localhost:5055 ./gradlew :playerApp:wasmJsBrowserDevelopmentRun`.
3. Sign in with a test account.

The configurations **Player - Web** and **Admin - Web** do not set the variable. They use the production backend.

## How it works

- The web apps call `/api` on the dev server (`localhost:8080`).
- `webpack.config.d/proxy.js` in `adminApp` and `playerApp` sends `/api` to the target.
- The target is `MTS_API_TARGET`. If the variable is not set, it is the production Cloud Functions.

## Needs

- JDK 21 for the emulators. The script uses the Zulu 21 in `/Library/Java/JavaVirtualMachines` by default. Set `E2E_JAVA_HOME` to use another one.
- Free ports: 5055 (API), 9199 (Auth) and 8181 (Firestore). A second start fails if the first is still running.

## Reset

- Stop and start the backend to get the original data.
- A test can call `POST /__e2e/reset?only=test-a,test-b` to restore some `test-*` tournaments.
