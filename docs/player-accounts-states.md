# Player accounts: states and actions

This document lists every case for the player import, accounts, EMA ids, tournament links and password resets.
Each row names the test that covers it. Change a rule and its test together.

Code: `firebase/functions/src/services/playerAccountsService.ts`, `playerImport.ts`, `playerImportService.ts`, `tournamentContentService.ts` (`overwriteTournamentPlayers`), and the routes in `api/routes/tournaments.routes.ts` and `api/routes/admin.routes.ts`.
Tests: `firebase/functions/src/api/playerAccounts.integration.test.ts`, `firebase/functions/src/services/playerImport.integration.test.ts` and `playerImport.test.ts` (run the integration tests with `npm run test:integration`, JDK 21).

## Base rules

- One account has at most one EMA id. One EMA id has at most one account.
- **The player capability exists only with an EMA id.** Giving an EMA id to an account adds the capability. Taking the EMA id away removes it. A player account cannot exist without an EMA id. `npm run audit:ema-ids` checks it, and `npm run cleanup:player-claims` fixes accounts that broke the rule.
- **The slot holds a pending email.** The import writes it. Only admins read it. When an account owns the EMA id of the slot, the email of the account wins and the pending email is ignored.
- **An email never links an account to an EMA id.** One email belongs to one account. The import refuses a file where two rows share an email, or where an account of another EMA id owns the email.
- A slot holds a registered EMA player or nothing. There are no non-member players and no provisional EMA ids.
- Roles add up. One account can be editor and player at the same time.
- **The EMA id is the link.** The account of a slot is the account that owns the EMA id of the slot. The server computes it on each read, so no stored link can go wrong.
- An account sees a tournament as a player when a slot of that tournament holds the EMA id of the account.
- The import and the assignment of a player never create an account and never send an email.
- **A reminder is not a password reset.** The Invitations dialog sends a reminder to accounts and an invitation (new account and password reset email) to slots with no account. Password resets for many accounts live in App users.
- Giving an account an EMA id gives it access to **all** tournaments that hold this EMA id. Taking the EMA id away removes that access in all of them.
- The app shows only "Has account" or "No account" for each player. There are no other states.
- A new player account has **no password and no alias**. The person chooses a password with the password reset email and an alias in the profile.

## Who can do what with accounts

| Action | Admin | Editor | Test |
|---|---|---|---|
| Create an admin account | Yes | No | "an editor cannot create an admin account" in `rolePermissions.integration.test.ts` |
| Create an editor account | Yes | Yes | "an editor can edit another editor account" in `rolePermissions.integration.test.ts` (no test creates it, see the note below) |
| Create a player account (EMA id required) | Yes | Yes | "needs an EMA number for a player account…" |
| Create a player account with "Invite" (from the pending email of the slot) | Yes | Yes | "creates the account with no password and no alias, links it, and sends the password reset email" |
| Set an EMA id on a new editor or admin account | Yes | No | "refuses an unknown EMA number and an editor who sets one" |
| Change the EMA id of a player account | Yes | Yes | "lets an editor change the EMA number of a player account…" |
| Change the EMA id of an editor or admin account | Yes | No | "lets an editor change the EMA number of a player account…" |
| Promote a player account to editor | Yes | No | "lets an admin promote a player-only account…" |

An account that an admin or an editor creates in the accounts screen gets a password-reset email at once.
No test creates an account through that route and reads the result. That route calls the Google password API, which the emulator does not replace.
The tests replace the call to the password API (`setPasswordResetSenderForTests`) and read the emails that the code sends.

## State table

| Id | Situation | Result | Email | Test |
|---|---|---|---|---|
| A | Create a player account in the accounts screen (EMA id required) | Account with the player role, linked to all tournaments with this EMA id. | Password reset | "links the slots of an EMA number when an admin sets the EMA number of an account" |
| B | Create an editor or admin account with an EMA id (admin only) | Same as A, plus the editor or admin role. | Password reset | same as A |
| C | Create an editor or admin account without an EMA id | No player role. Sees no tournament as a player. | Password reset | "C: an account with no EMA number sees no tournament" |
| D | Set, change or clear the EMA id of an account | New EMA id: links all its tournaments. Old EMA id: unlinks all its tournaments and frees the id. | None | "D: changing the EMA number of an account releases…" |
| E | Assign an EMA id to a slot, and the account exists | The slot shows the account at once. | None | "E: assigning an EMA number to a slot links the existing account…" |
| F | Assign an EMA id to a slot, and no account exists | The slot has no account. | None | "F: assigning an EMA number with no account…" |
| G | `send` to a slot with no account and a pending email | The server creates the account with no password and no alias, links all tournaments of the EMA id, and sends the password reset email. Result `INVITED`. | Password reset | "creates the account with no password and no alias…", "links every tournament of the EMA number when the account is created" |
| H | `send` to a slot with no account and no pending email | `FAILED` with `NO_EMAIL`. Nothing is created. The app asks for the email before it sends, so this is a safety net. | None | "fails for a slot with no pending email and creates nothing" |
| H2 | Set the pending email of a slot (`PUT …/player-emails`) | `SAVED` in lower case, or `CLEARED` for an empty text. Allowed after the tournament started. | None | "saves a valid email in lower case, so Invite can use it", "replaces the email of a slot and clears it with an empty text", "works after the tournament started…" |
| H3 | Set an email that is not valid, that an account of another EMA id owns, or that another slot holds | `FAILED` (`INVALID_EMAIL` or `EMAIL_IN_USE`). The old email stays. | None | "refuses an invalid email and keeps the old one", "refuses an email that an account of another EMA number owns", "refuses an email that another slot of the tournament holds…" |
| H4 | Set an email for a slot with an account, with no EMA id, or that does not exist | `FAILED` (`HAS_ACCOUNT`, `NO_EMA_ID`, `PLAYER_NOT_FOUND`). | None | "refuses a slot with an account…", "refuses a slot with no EMA number and a player that does not exist" |
| I | `send` to a slot whose pending email an account owns already | `FAILED` with `EMAIL_IN_USE`. The other account does not change. | None | "fails with EMAIL_IN_USE when an account owns the email…", "never gives an EMA number to an editor account because of an email" |
| J | `send` to a slot with an account | The server sends the reminder (an email with a link to the tournament) to the account email. Result `REMINDED`. The pending email is ignored. It never sends a password reset, also when the person has no password yet. | Reminder | "sends the reminder with a link to the tournament…", "sends a reminder every time…", "writes the reminder in Spanish…" |
| K | The email cannot be sent | `FAILED` with `EMAIL_FAILED`. The account stays. The next `send` is a reminder. For a password, use the mass reset in App users. | None | "keeps the account when the reset email fails…", "fails with EMAIL_FAILED when the reminder cannot be sent…" |
| L | Change the EMA id of a slot | The slot shows the account of the new EMA id, or none. The pending email goes away with the old person. | None | "M: a slot with another EMA number has no account…" |
| M | The EMA id belongs to another account | Error 409. Nothing changes. | None | "refuses an EMA number that belongs to another account", "refuses an EMA number and an email that belong to another account" |
| N | `send` to a slot with no EMA id | `FAILED` with `NO_EMA_ID`. | None | "fails for a slot without an EMA number" |
| O | `send` to many players with both states | One result for each player. The server picks the action for each. Up to 200 players. | Password reset (invitations), reminder (accounts) | "handles each player by its state in one request" |
| Q | Password reset for many accounts (`POST /admin/users/password-resets`, App users, admins only) | One result per account. A disabled, unknown or email-less account fails alone. Up to 200 accounts per request. | Password reset | "sends the password reset email to every account of the list", "gives a failure to an unknown, a disabled and an account without email…", "is for admins only" |

## Import

| Situation | Result | Test |
|---|---|---|
| A valid file | The import overwrites all players, pending emails and (with a team column) team names, in one request. It creates no account and sends no email. | "writes the players, the pending emails and the teams, and creates no account and sends no email" |
| Slots that the file does not list, and rows with an id only | They become empty slots. | "overwrites every slot: moved players, omitted players, old emails and old substitutions" |
| A new person in a slot | The substitution of the old person goes away. The `playerLog` records the change. | same as above |
| The same file twice | The second import changes no slot. | "changes nothing when the same file is applied again" |
| A file with any error | The whole file is refused. The preview lists every error with its line. Nothing is written. | "writes nothing when one row has an error, and the preview lists the error" |
| Two rows with the same email, in any letter case | Error `DUPLICATE_EMAIL` on both rows. | "refuses two rows with the same email" |
| An email that an account of another EMA number, or with none, owns | Error `EMAIL_OWNED_BY_OTHER_ACCOUNT`. | "refuses an email that an account of another EMA number owns, and accepts the account of the same number" |
| An email that the account of the same EMA number owns | Accepted. The account email wins. | same as above |
| An empty email | Accepted. The slot has no pending email. | "an empty email is allowed and a valid email is saved in lower case" |
| A tournament with table data | The preview shows `tournamentStarted`. The apply step refuses with `TOURNAMENT_STARTED`. | "refuses a tournament with table data and says so in the preview" |
| A team column | Every team gets the name of the file, or `Team N` when the file does not name it. Without the column, the names stay. | "keeps the team names when the file has no team column…" |
| A file with up to 1000 rows | The server writes in batches of 400 documents. | "writes a big file in several batches" |
| Export | The email column holds the account email, or the pending email. Only admins get it. | "writes the account email, or the pending email when the slot has no account, for admins", "leaves the email column empty for the callers that are not admins" |
| Import of an exported file | The file is valid and changes the same data. | "writes a file that the import accepts again" |

## Names in the app

| Server action | Button in the app | Used for |
|---|---|---|
| `send` | "Invite" in the menu of a row | A slot with an EMA id and no account. If the slot has no email, a dialog asks for it first, saves it and then invites. |
| `PUT …/player-emails` | "Edit email" in the menu of a row | Sets or removes the pending email. Admins see the saved email. Editors see an empty field and a note. |
| `send` | "Send reminder" in the menu of a row | A slot with an account. Sends the reminder. |
| (none) | "Edit account" in the menu of a row (admins) | Opens the edit dialog of the account. It has "Send password reset email". |
| `send` for many players | "Send (n)" in the Invitations dialog | Invites the selected players with no account and sends a reminder to the selected players with an account. Selected players with no account and no email get a dialog that asks for their emails. A player left blank is skipped. |

| `POST /admin/users/password-resets` | "Send password reset (n)" in App users (admins) | Select accounts with the checkboxes, confirm, and read the result. |

The list of the Invitations dialog has sticky column titles. Each player shows a check (has an account) or a cross (no account) instead of text.
