# Player accounts: states and actions

This document lists every case for accounts, EMA ids, tournament links, emails and reminders.
Each row names the test that covers it. Change a rule and its test together.

Code: `firebase/functions/src/services/playerAccountsService.ts`, `playerImport.ts`, `playerImportService.ts` and `api/routes/admin.routes.ts`.
Tests: `firebase/functions/src/api/playerAccounts.integration.test.ts`, `firebase/functions/src/services/playerImport.integration.test.ts` and `playerImport.test.ts` (run the integration tests with `npm run test:integration`, JDK 21).

## Base rules

- One account has at most one EMA id. One EMA id has at most one account.
- **The email lives only in the account.** A slot, an import file row or an invitation holds no email of its own.
- **An email never links an account to an EMA id.** Several people can share one email. A player gets an account only from an email that no account owns.
- **The email of an account always wins.** A different email in an import or in a send is refused and the account email stays.
- A slot holds a registered EMA player or nothing. There are no non-member players and no provisional EMA ids.
- Roles add up. One account can be editor and player at the same time.
- An account sees a tournament as a player when a slot of that tournament links to the account.
- Linking never sends an email. An editor sends every email by hand.
- Giving an account an EMA id links it to **all** tournaments that hold this EMA id.
- Taking the EMA id away unlinks all of them.
- A player account made from an email has **no password**. The person chooses one with the password reset email that "Invite" sends.

## Who can do what with accounts

| Action | Admin | Editor | Test |
|---|---|---|---|
| Create an admin account | Yes | No | "an editor cannot create an admin account" in `rolePermissions.integration.test.ts` |
| Create an editor account | Yes | Yes | "an editor can edit another editor account" in `rolePermissions.integration.test.ts` (no test creates it, see the note below) |
| Create a player account (EMA id required) | Yes | Yes | "needs an EMA number for a player account…" |
| Create a player account from an email (invite, save email, import) | Yes | Yes | "creates the account without a password, links it, and sends the password reset email" |
| Set an EMA id on a new editor or admin account | Yes | No | "refuses an unknown EMA number and an editor who sets one" |
| Change the EMA id of a player account | Yes | Yes | "lets an editor change the EMA number of a player account…" |
| Change the EMA id of an editor or admin account | Yes | No | "lets an editor change the EMA number of a player account…" |
| Promote a player account to editor | Yes | No | "lets an admin promote a player-only account…" |

An account that an admin or an editor creates in the accounts screen gets a password-reset email at once. This is the only email that creating an account there sends.
No test creates an account through that route and reads the result. That route calls the Google password API, which the emulator does not replace.
The tests replace the call to the password API (`setPasswordResetSenderForTests`) and read the emails that the code sends.

## State table

| Id | Situation | Result | Email | Options after | Test |
|---|---|---|---|---|---|
| A | Create a player account (EMA id required) | Account with the player role. Linked to all tournaments with this EMA id. | Password reset only | Remind with `send`. Unlink with `revoke`. | "links the slots of an EMA number when an admin sets the EMA number of an account" |
| B | Create an editor or admin account with an EMA id (admin only) | Same as A, and the account also has the editor or admin role. | Password reset only | Same as A | same as A |
| C | Create an editor or admin account without an EMA id | No player role. Sees no tournament as a player. | Password reset only | An EMA id can be set later (case D). | "C: an account with no EMA number sees no tournament" |
| D | Set, change or clear the EMA id of an account | New EMA id: links all its tournaments. Old EMA id: unlinks all its tournaments and frees the id. | None | Same as A | "D: changing the EMA number of an account releases…" |
| E | Assign an EMA id to a slot, and the account exists | Linked at once. | None | `send` reminds. `revoke` unlinks. | "E: assigning an EMA number to a slot links the existing account…" |
| F | Assign an EMA id to a slot, and no account exists | Slot stays unlinked. Nobody sees it. | None | `send` with an email makes the account (case G). | "F: assigning an EMA number with no account…" |
| G | `send` with an email to a slot with no account | The server creates the account **without a password**, with the EMA name as alias, and links all tournaments of the EMA id. | Password reset email | `resend` sends it again. `revoke` unlinks this tournament. | "creates the account without a password, links it, and sends the password reset email", "links every tournament of the EMA number when the account is created" |
| H | `send` with an email that an account owns already (another EMA id, or none) | Refused (`FAILED`). The slot stays unlinked and the other account does not change. Several people can share one email, so the email never links an account. | None | Use another email, or leave the player without an account. | "never gives an account an EMA number because of an email…", "never gives an EMA number to an editor account because of an email", "two players cannot share one email…" |
| H2 | `send` with an email that differs from the email of the linked account | Refused (`FAILED`). The account email stays. | None | Send without an email. | "refuses a different email for a linked slot and changes nothing" |
| I | `send` to a linked slot of an account **with a password** | First time: reminder. Later: skipped as already reminded. | One reminder | `resend` sends the reminder again. | "I: a linked slot of an account with a password gets one reminder with send…" |
| I2 | `send` to a linked slot of an account **without a password** | The password reset email, so the person can choose a password. | Password reset email | `resend` sends it again. | "a linked slot of an account with no password gets the password reset email, not the reminder" |
| J | The person chooses a password and signs in | `invitationStatus` becomes `ACCEPTED`. | None | Same as A | "after the person chooses a password, the slot shows the player as accepted and send is skipped" |
| K | The email cannot be sent | `FAILED` for that player. The account and the link stay. | None | `resend` | "keeps the account when the reset email fails, and resend sends it again", "keeps the link and lets the editor repeat when the reminder cannot be sent" |
| L | `revoke` | Only this tournament: the slot loses the account link. The account and its EMA id stay. | None | `send` or a new assignment links it again. To keep the player out, remove the EMA id from the slot. | "L: revoke unlinks only that tournament…" |
| M | Change the EMA id of a slot | The old account link of the slot is removed. | None | New EMA id follows case E or F. | "M: clears the account link when the EMA number of the slot changes" |
| N | The EMA id belongs to another account | Error 409. Nothing changes. | None | Choose another EMA id. | "refuses an EMA number that belongs to another account", "an account cannot use two EMA numbers", "refuses an EMA number and an email that belong to another account" |
| O | `send` to a slot with no EMA id | Refused. | None | Assign an EMA id first. | "refuses a slot without an EMA id" |
| P | Save emails without sending (`PUT …/emails`) | A player with no account gets an account without a password (`CREATED`). An account with the same email: `UNCHANGED`. An account with another email: `KEPT_ACCOUNT_EMAIL`. A refused email: `FAILED`. | None | `send` | "creates an account for a player with none, keeps the email of an account, and refuses an email that others own" |

## Import and export of emails

| Situation | Result | Test |
|---|---|---|
| A new email for a player with no account | The import creates the account without a password. It sends no message. | "assigns players, creates the accounts of the emails and names the teams" |
| The email differs from the email of the account | The import keeps the account email, applies the rest of the row and warns (`EMAIL_KEPT_ACCOUNT`). | "keeps the email of an account that has one, warns, and applies the rest of the row" |
| The email belongs to another account | The import does not save it and warns (`EMAIL_IN_USE`). It never links the account. | "never links an account to an EMA number because of an email…" |
| Two rows of the file use the same email | The first row gets the account. The second warns (`EMAIL_IN_USE`). | "gives a shared email to the first player of the file only" |
| A row without an email | The account and its email stay. | "a row without an email leaves the account and its email as they are" |
| A second import of the same file | Nothing changes. | "a second import of the same file changes nothing…" |
| Export | The email column holds the email of the linked account, also when the account was linked outside the import. | "writes the email of an account that was linked outside the import…" |
| Import of an exported file | Every assigned row is `UNCHANGED` and has no warning. | "works as a template: importing the exported file changes nothing and warns about nothing" |

## Names in the app

| Server action | Button in the app | Used for |
|---|---|---|
| `send` | "Send invitation" | Slots with no account (needs an email) |
| `send` | "Send reminder" | Linked slots, first email |
| `resend` | "Send again" | Linked slots of an account that has no password |
| `resend` | "Send reminder again" | Linked slots that got a reminder |
| `send` or `resend` on many players | "Invite or remind (n)", "Resend (n)" | Invitations and reminders dialog |
| `revoke` | "Remove account link" | Unlinks the tournament |
