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
| `small` | 24 (6 tables) | 4 | each combination on and off | 16 | 2 h |

The round lasts 25 minutes with phone emulators: the emulator that writes the hands takes about 70 seconds per hand.
The workflow has a checkbox for the phone emulators. Without them only bots play, the rounds last about 12 minutes
(240 / 64 / 24 players: 2 h 30 / 1 h 30 / 50 min), and the tablet still prepares and runs the tournament. The
checkbox says it: "Phone emulators: on about 5 h / 3 h 15 / 2 h, off about 2 h 30 / 1 h 30 / 50 min (240 / 64 / 24
players)".
A job of GitHub lasts at most 6 hours, so `large` with phone emulators plays in two parts (rounds 1-6 and 7-11); between
them every phone and bot signs in again, as after the lunch break.

Each run starts with a short preflight (about 10 minutes): the tablet creates a tournament of 16, imports and invites, and
one phone writes two hands. When a screen has changed, the run stops there.

## Staging first

Each run deploys main to staging before anything else (the job "Deploy main to staging", the same deploy as Actions >
Deploy staging). The app of the test and the backend it talks to are always the same commit.

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

  Done (plan.cornerCases, round 1, table 3). The scorer of the table arrives 4 minutes after the gong. A minute after
  the gong the organizer substitutes the highest slot of the table in Players (the player came late, from round 1).
  The checks want the table played to the end, and the substituted slot with no result in the ranking (the
  independent count leaves out the rounds of the substitute, as the server does).
- Two phones send the same hand at once (race).
- A wrong hand: the editor deletes it in the admin app and the phone writes it again. Both the last hand of the table
  and a hand of several hands before.
- A hand edited by the editor (the last one and an older one), and the phone of the table shows the correction.

  Done (plan.cornerCases, round 2, tables 2 to 5). The organizer waits until the table has played a few hands, opens
  it in the table editor and turns Done off and clears the hand, or types another score. The bots of the table see the
  change: the scorer writes a deleted hand again at once, and the others note it. The checks want each case seen by
  the scorer and by another phone, at the right hand (the newest or an older one), and no change at any other table.
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

It also asks the server the places of every player, as the list of tournaments shows them (podium, team, chicken
hands and best hand), and compares each one with its own count. The bots mark about a third of the hands won on a
discard with 8 to 12 points as chicken hands, so every scenario has some.

## Corners: the corner cases in minutes

`corners` is a small tournament of 24 players with 2 rounds of 8 minutes, and every corner case in round 1. The late
table and its substitution are at table 3, and the deleted and edited hands at tables 2, 4, 5 and 6. It takes about
45 minutes. Turn off "Deploy main to staging first" when only the tests changed, to save about 9 more minutes. When the
organizer fails, the whole run stops, so the bots do not play on for nothing. The full scenarios stay as they are.
