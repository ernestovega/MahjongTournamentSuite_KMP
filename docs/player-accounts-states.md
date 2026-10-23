# Player accounts: states and actions

This document lists every case for accounts, EMA ids, tournament links, invitations and reminders.
Each row names the test that covers it. Change a rule and its test together.

Code: `firebase/functions/src/services/playerAccountsService.ts` and `api/routes/admin.routes.ts`.
Tests: `firebase/functions/src/api/playerInvitations.integration.test.ts` (run with `npm run test:integration`, JDK 21).

## Base rules

- One account has at most one EMA id. One EMA id has at most one account.
- Roles add up. One account can be editor and player at the same time.
- An account sees a tournament as a player when a slot of that tournament links to the account.
- Linking never sends an email. An editor sends every email by hand.
- Giving an account an EMA id links it to **all** tournaments that hold this EMA id.
- Taking the EMA id away unlinks all of them.

## Who can do what with accounts

| Action | Admin | Editor | Test |
|---|---|---|---|
| Create an admin account | Yes | No | "an editor cannot create an admin account" in `rolePermissions.integration.test.ts` |
| Create an editor account | Yes | Yes | "an editor can edit another editor account" in `rolePermissions.integration.test.ts` (no test creates it, see the note below) |
| Create a player account (EMA id required) | Yes | Yes | "needs an EMA id for a player account…" |
| Set an EMA id on a new editor or admin account | Yes | No | "refuses an unknown EMA id and an editor who sets one" |
| Change the EMA id of a player account | Yes | Yes | "lets an editor change the EMA id of a player account…" |
| Change the EMA id of an editor or admin account | Yes | No | "lets an editor change the EMA id of a player account…" |
| Promote a player account to editor | Yes | No | "lets an admin promote a player-only account…" |

A new account always gets a password-reset email. This is the only email that creating an account sends.
No test creates an account through the route and reads the result. That route calls the Google password API, which the emulator does not replace.
Case A and case B below are tested through `setAccountEmaId`, which the route calls.

## State table

| Id | Situation | Result | Email | Options after | Test |
|---|---|---|---|---|---|
| A | Create a player account (EMA id required) | Account with the player role. Linked to all tournaments with this EMA id. | Password reset only | Remind with `send`. Unlink with `revoke`. | "links the slots of an EMA id when an admin sets the EMA id of an account" |
| B | Create an editor or admin account with an EMA id (admin only) | Same as A, and the account also has the editor or admin role. | Password reset only | Same as A | same as A |
| C | Create an editor or admin account without an EMA id | No player role. Sees no tournament as a player. | Password reset only | An EMA id can be set later (case D). | "C: an account with no EMA id sees no tournament" |
| D | Set, change or clear the EMA id of an account | New EMA id: links all its tournaments. Old EMA id: unlinks all its tournaments and frees the id. | None | Same as A | "D: changing the EMA id of an account releases…" and "links the slots of an EMA id…" |
| E | Assign an EMA id to a slot, and the account exists | Linked at once (`LINKED`). | None | `send` reminds. `revoke` unlinks. | "E: assigning an EMA id to a slot links the existing account…" |
| F | Assign an EMA id to a slot, and no account exists | Slot stays unlinked. Nobody sees it. | None | `send` with an email makes an invitation (case G). | "F: assigning an EMA id with no account…" |
| G | `send` to a slot with no account | Invitation `PENDING`. Valid for 14 days. | Invitation with link | `resend` makes a new link. The old link stops working. `revoke` cancels. | "creates a new account with the invitation link…", "G: resend gives a new link…", "keeps the invitation when the email cannot be sent…" |
| H | `send` with an email that already has an account | The account gets the EMA id and links at once. No invitation. | Reminder | Same as E | "sends to the email of an existing account without an invitation…" |
| I | `send` to a linked slot | First time: reminder. Later: skipped as already reminded. | One reminder | `resend` sends the reminder again. | "I: a linked slot gets one reminder with send…" |
| J | The player accepts an invitation | Account is created with the player role and the chosen password. Linked to **all** tournaments of the EMA id. | None | Same as A | "creates a new account with the invitation link…", "links every tournament of the EMA id when the player accepts one invitation" |
| K | The invitation expired, was revoked or was used | Accept fails (409). The invitation is replaced by `resend` and the old link then gives 404. The slot stays unlinked. | None | `resend` or `send` again | "K: an expired invitation cannot be accepted…", "revokes a pending invitation" |
| L | `revoke` | Only this tournament: slot returns to `NONE`, invitations become `REVOKED`, the player loses this tournament. The account and its EMA id stay. | None | `send` or a new assignment links it again. To keep the player out, remove the EMA id from the slot. | "L: revoke unlinks only that tournament…", "revokes a pending invitation" |
| M | Change the EMA id of a slot | The old account link of the slot is removed. | None | New EMA id follows case E or F. | "clears the account link when the EMA id of the slot changes" |
| N | The EMA id belongs to another account | Error 409. Nothing changes. | None | Choose another EMA id. | "refuses an EMA id that belongs to another account", "an account cannot use two EMA ids", "refuses an EMA id and an email that belong to another account" |
| P | An invitation is pending, and the same EMA id sits in other tournaments | Every slot of this person without an account shows `INVITED`, in all tournaments. The server adds this when it reads the players. `send` there is skipped. `revoke` and `resend` work from any of these tournaments. They end the pending invitation of the person, and `resend` makes a new one in the tournament of the action. | None | The "Invited" view in App users lists all pending invitations, with `Resend` and `Revoke`. | "P: an invitation marks every tournament of the person…" |
| O | `send` to a slot with no EMA id | Refused. | None | Assign an EMA id first. | "refuses a slot without an EMA id" |

## Names in the app

| Server action | Button in the app | Used for |
|---|---|---|
| `send` | "Send invitation" | Slots with no account |
| `send` | "Send reminder" | Linked slots, first reminder |
| `resend` | "Resend with new link" | Slots with a pending invitation |
| `resend` | "Send reminder again" | Linked slots that got a reminder |
| `send` or `resend` on many players | "Invite or remind (n)", "Resend (n)" | Invitations and reminders dialog |
| `revoke` | "Revoke invitation", "Remove account link" | Cancels the invitation, or unlinks the tournament |
