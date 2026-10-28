# Player client

The player client is a separate app in this repository. It uses the same backend, `domain` and `data` modules as the management app.

## Status

Phases 1 and 2 are built and tested. Phase 3 (schedules and notifications) is not started.

| Part | State |
| --- | --- |
| Editor routes closed for player accounts (`requireEditor`) | Done |
| Roles `ADMIN`, `EDITOR`, `PLAYER` as claims, `emaId` on the account | Done |
| Account creation from an email without a password, password reset email, slot link | Done |
| Management app: account state and invitation actions on the players screen | Done |
| Player read access with `requireTournamentReader`, scoped lists | Done |
| Modules `:ui`, `:playerApp`, `:playerAndroidApp` (Android and web) | Done |
| Hand entry with a review step (preview, then confirm) | Done |
| Schedules, time zone, FCM and Web Push | Phase 3 |

## Decisions

- **Platforms:** Android and web. No desktop app and no iOS app. On iOS, players use the web app.
- **Visibility:** a player sees all tables and rankings of the tournaments where the player is registered. A player never sees emails or accounts of other players.
- **Hand entry:** one player who sits at the table writes the hands down. There is no start step and no confirmation by the other players. A table is ready when the editor assigned its four seats. An editor can still enter hands directly.
- **Review step:** before saving, the app shows the winner, the loser, the hand score, the score change of every seat and the new table totals. The player shows this to the table and then confirms.
- **Hands mode is the default** for new tables, in both apps. An editor can still switch a table to totals-only.
- **Seats:** a player of the table assigns the four seats, until the first hand is saved.
- **Table changed:** the save carries the table version of the review. If the table changed in between (for example a staff member saved the hand), the server answers `409`. The app keeps the entered hand and opens a window with the server data and the hand of the player. The player closes it to go on, or calls a staff member first.
- **Corrections, penalties and totals-only tables:** editor-only. Hand entry needs `useTotalsOnly = false`.
- **No legacy handling:** an account with no claim has no capability. The app is not in production, so old accounts are fixed by saving them once in the accounts screen.

## Modules

- `:ui` has the theme, keyboard focus helpers, dialogs and tables that both apps use. It keeps the package names of the code that moved.
- `:playerApp` is a Kotlin Multiplatform library with the player screens. It has a web entry point (`playerApp.js`). It targets Android, JVM (tests only) and wasmJs.
- `:playerAndroidApp` is the thin Android application. Its id is `com.etologic.mahjongtournamentsuite.player`.
- The web app has its own Firebase Hosting site `mahjong-tournament-suite-player`.

No new libraries were added.

## Setup before the first invitation

1. Create the hosting site: `firebase hosting:sites:create mahjong-tournament-suite-player`.
2. Connect the domain `mtsplayer.mahjongmadrid.com` to that site (Firebase console, Hosting, Add custom domain) and add the DNS records in cPanel.
3. Create a mailbox for the sender and store its SMTP address as the secret `MTS_SMTP_URI`:
   `npx -y firebase-tools@latest functions:secrets:set MTS_SMTP_URI --project mahjong-tournament-suite`.
4. `PLAYER_APP_URL` and `MAIL_FROM` are set in `firebase/functions/.env`. Deploy the functions. Then deploy hosting for both sites.
5. Save each existing editor account once in the accounts screen so it gets the `editor` claim.
6. Run `npm run verify:roles` (see `firebase-role-rollout.md`).

## Phase 3: schedules and notifications

Not started. Open points:

- `Tournament` has no time zone. The round schedule has a date and a start time but no end time. Agenda events have no end time.
- The notification channels (FCM on Android, Web Push on the web) need new dependencies.

## Known gaps

- The player app does not pause polling when the app is in the background.
- There is no UI test for the player app screens. The view model code has unit tests.

## Player app design

The player screens use shared components from the `:ui` module. The design follows MahjongScoring2.

- **Sign-in:** `SignInForm` is the same screen as in the editor app. Only the logo differs (`LocalAppLogo`).
- **Table seats:** `TableSeatsView` shows the four seats around a center label. The seat of the player is at the bottom. A switch shows a ranked list. Another switch shows the point differences between seats.
- **Table screen:** seats, one button that opens a bottom sheet for hand entry, and a card per saved hand.
- **Tournament screen:** a header with the place and points of the player, and the tabs My tables, All tables and Ranking. Ranking marks the first three places with trophies.
- **Tournament list:** a card per tournament with a status chip (Upcoming, In progress, Finished).

## Invitations and registration emails

- Assigning a player never sends an email. If an account owns the EMA ID, the slot shows that account and the player sees the tournament. No link is stored: the EMA ID is the link.
- The email of a player lives only in the account. A player with no account gets an account without a password when an editor saves or imports the email.
- An editor sends, resends or marks as not notified by hand, for one player or for many (button "Invitations" on the players screen).
- The list tracks who was contacted. Editors filter by "Not invited yet", "Invited, waiting", "Contacted" or "No EMA ID" and run the action on the selected players.
- "Invite" sends the password reset email to an account that has no password yet. The person chooses a password and signs in on the player app.
- A player with a password gets a "you are registered" email with a link to the tournament (`/?tournament=<id>`). The player app opens that tournament after sign-in.
- An account with an EMA ID (set by an admin in the accounts screen, or created from an email) sees every tournament where that EMA ID is assigned. The email is a notice and is optional for such players.

## Table screen rules

- The player of the viewer stays at the bottom. The chairs change after every four hands and the winds turn after every hand, as in MahjongScoring2.
- The points of the table show only when the table is complete (all hands done or totals entered by hand). A complete table opens in the ranking view.
- The differences switch shows the hand score that each player needs to reach the places above by self-pick, by a discard of that player, or by a discard of another player.
- A hand shows as the wind of its round and its place in the round (E1 to N4).
- Tapping a player opens the tables and results of that player.
