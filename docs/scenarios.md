# Game day scenarios

Repeatable whole tournaments in staging, played without anybody: an admin app on a tablet emulator prepares and runs
the tournament as an organizer, the player app on phone emulators writes and follows hands, bots play the rest of the
tables through the API, and the phones of one venue wifi and the public read from one address. Everything a person does
in a real tournament is done in the apps (the draw too). The only code of our own is the scripts that drive the bots and
the emulators, and the independent count of the checks. Nothing runs on a Mac: GitHub Actions only.

The tournaments stay in staging after the run, with the time of the run in their name.

## The scenarios

| | Players | Rounds | Teams, chicken, best hands | Hands per table | Time (about) |
| --- | --- | --- | --- | --- | --- |
| `large` | 240 (60 tables) | 11 | on | 16 (14-15 at a few tables) | 5 h |
| `medium` | 64 (16 tables) | 7 | on | 16 (14-15 at a few tables) | 3 h 15 |
| `small` | 16-24 | 3-4 | each combination on and off | 16 | 2 h |

The round lasts 25 minutes: the emulator that writes the hands takes about 70 seconds per hand.

## Who does what

- **Organizer (tablet emulator, admin app, `ScenarioOrganizerTest`).** Signs in as the bot admin, creates the tournament
  in the form (so the app computes the draw), creates some accounts by hand in Users, imports the players from an Excel
  file (first a file with errors, then the right one), assigns a few slots by hand, and sends the invitations to every
  player. Then it presses Play of each round at its time, and does the corner cases of the plan (below). Its screen is
  recorded.
- **Editor (the same tablet, or a second one).** Deletes and edits hands, substitutes a player, closes open tables.
- **Phones (phone emulators, player app, `RehearsalPlayerTest`).** Each one plays a slot: opens the invitation link
  from its email, chooses its password, signs in, and writes or follows the hands of its tables. Two of them are
  recorded: the one with the happy path and the one with the most corner cases.
- **Bots (`bots.mjs`).** The other slots, through the API, as the app calls it. They also open their invitation links.
- **Venue and public.** Phones of one venue wifi and spectators, from one address.

## The emails

The players use addresses `<local>+<scenario>-<slot>@<domain>` of one mailbox for tests (the secret
`REHEARSAL_IMAP_URI`). The scripts read the invitation of each slot by IMAP and open its link, as a person would.

## The plan of corner cases

The orchestrator writes a plan before the run: which table does what, and when. The organizer, the phones and the bots
read it, so each one knows its part.

- A table starts late; its scorer arrives minutes after the gong. A substitution of a player at that table, minutes
  after the first gong of round 1.
- Two phones send the same hand at once (race).
- A wrong hand: the editor deletes it in the admin app and the phone writes it again.
- A hand edited by the editor, and the phone of the table shows the correction.
- Penalties, a draw, extra time after the gong (finish the hand), a table that stays open and the admin closes it.
- A table with manual totals.
- A seat change after the seats are saved.
- A phone without network for a minute, and an app sent to the background.
- Ties in the ranking.
- `small` only: a private championship, a disabled user, editors and admins added, changed and removed in the middle,
  a change of branding and of venue in the middle.

## The checks

`report.mjs` scores every table again with its own count (teams, chicken hands, best hands, penalties) and compares it
with the server, together with the hands of the bots, the times and the errors (docs/rehearsal.md, The checks).
