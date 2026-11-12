# Web hosting

Both web apps live on one site, `https://mts.mahjongmadrid.com` (Firebase Hosting site `mahjong-tournament-suite`, also
reachable at `mahjong-tournament-suite.web.app`):

| Path | App |
| --- | --- |
| `/` | Player app. Everyone lands here. Shared links are `/t/<id>` and `/?tournament=<id>`. |
| `/admin/` | Admin app, for admins and editors. |
| `/api/**` | The `api` function. |

`scripts/assemble-web-hosting.sh` builds both apps and copies them to `build/hosting`, the player app at the root and
the admin app in `admin/`. `firebase.json` runs it before every hosting deploy. The admin `index.html` uses relative
paths only, so it works under `/admin/`.

## One sign-in

The two apps are on the same origin, so they share the session in `localStorage`
(`mahjongTournamentSuite.authSession`). The site has one sign-in, the one of the player app:

- After the sign-in, an admin or an editor chooses between the player app and the admin app (`SuiteAppChooserDialog`).
  With "Remember my choice on this device", the choice goes to `mahjongTournamentSuite.preferredApp` and the question
  does not come back. With `ADMIN` remembered, the player `index.html` sends the bare address `/` to `/admin/` before
  the player app loads. Shared links stay in the player app.
- The player app shows a "Management" button to admins and editors. The admin app shows a "Player view" button. A
  switch also updates a remembered choice, so the site never sends the user back.
- The admin app on the site has no sign-in screen. Without a session it sends the user to `/?signin=1`, which opens
  the player sign-in. A player account that opens `/admin/` goes to `/` and keeps the session. Signing out in the admin
  app goes to `/`.
- The local dev server serves the admin app at the root. There the admin app keeps its own sign-in screen.

The code is in `ui/.../platform/SuiteApps.kt` and its platform files.

## Android and desktop

The two Android apps stay separate apps with their own sessions. The same buttons open the other app with its launch
intent, or its Google Play page when it is not installed (`market://`, then the web page). Each manifest lists the other
package in `<queries>`. The desktop apps show no switch buttons.

