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
Deploy · Staging). The app of the test and the backend it talks to are always the same commit.

## Who does what

- **Organizer (tablet emulator, admin app, `ScenarioOrganizerTest`).** Signs in as the bot admin, creates the tournament
  in the form (so the app computes the draw; its main organizer is the federation "Game day club", which the first
  game day creates in Organizations and the next ones reuse), creates some accounts by hand in Users, imports the players from an Excel
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

## Who plays

A player of a real tournament usually played others before. In a plan (`slotKind` in `scenario.mjs`):

- 1 in 4 players is new: an EMA player without an account, who gets an invitation and creates the account.
- 1 in 20 has an account made by hand in Users before the tournament.
- The others played an earlier game day and come back with that account (`returning` in the plan): the email of then,
  a reminder instead of an invitation. `invitations.mjs` signs them in with the password of the tests; when it does not
  work (that game day stopped before its players chose one), the player uses Forgot password (again, up to 3 times, after
  a network or server failure) and opens that email.
- Without enough players of earlier game days, new players take their slots. Without enough free EMA players, players
  of earlier game days also take slots of new players, from the last slot up, while 1 in 20 (at least 2) stay new. With
  fewer free EMA players than that, only those are new, and with none every player comes back.
- Before that, the plan frees EMA players: when the registry has fewer free ones than the new players and the accounts
  made in Users need, the bot admin deletes in Users the accounts of the oldest game days (the date in the tag of their
  email). Their EMA players and the tournaments they played stay. So every game day invites new accounts again.

Each new player uses up an EMA player of the registry of staging (a few hundred), as an EMA number has one account. With
players who come back, a large scenario uses about 70 of them instead of 240.

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

  Done (plan.cornerCases, round 1, table 1). The scorer and the second player of the table save the first hand at the
  same moment. The checks want one answer 200, the other 409 (the table changed), and no extra hand at the table.
- A wrong hand: the editor deletes it in the admin app and the phone writes it again. Both the last hand of the table
  and a hand of several hands before.
- A hand edited by the editor (the last one and an older one), and the phone of the table shows the correction.
  The editor turns Done on by the state of its box (not a tap at the centre of the cell) and checks, before Save, that
  the hand shows the new score and is done. After Save the hand on the server must be done with the new score: a hand
  left not done no longer counts, and the phones see it deleted. The organizer also logs any done hand of another table
  of the round that changed with its Save, because Save sends every table with changes.

  Done (plan.cornerCases, round 2, tables 2 to 5). The organizer waits until the table has played a few hands, opens
  it in the table editor and turns Done off and clears the hand, or types another score. The bots of the table see the
  change: the scorer writes a deleted hand again at once, and the others note it. The checks want each case seen by
  the scorer and by another phone, at the right hand (the newest or an older one), and no change at any other table.
  A phone whose next look comes after the scorer wrote the hand again sees the hand change instead of go: for a
  deleted hand, that counts as seen too. When the newest hand is the one to change, the table waits for the editor
  (up to 4 minutes), as players who called the organization do, so it is still the newest when the editor gets there.
- Penalties and a draw.

  Done (plan.cornerCases, round 1, table 1). After 2 hands the scorer gives -10 to East and then -5 to South, in two
  calls (the second one adds a row), and writes a draw. The checks want both penalties on the draw, and the totals of
  the table equal to the independent count. Besides, the bots give a random penalty in about 1 of 20 hands, and about
  1 hand in 10 is a draw.
- Extra time after the gong: a slow table finishes its hand.

  Done (plan.cornerCases, last round, table 1). The scorer stops after 3 hands and saves the next one 20 s after the
  end of the match time. The checks want that hand saved in the extra time, as the last hand of the table, and the
  table completed by it.
- A table that stays open and the admin closes it.

  Done (plan.cornerCases, last round, table 2). The players leave after 3 hands. When the time is over, the organizer
  closes the open tables of the ended rounds in the review of Round control. The checks want the table completed with
  those 3 hands.
- A table with manual totals.

  Done (plan.cornerCases, last round, table 3). The scorer saves the seats and the players leave: the table plays on
  paper. The editor opens it in the table editor, turns Manual scores on, types the four totals (31 / 9 / -12 / -28, or
  0 for all when a player of the tie sits there), turns Completed on and saves. The checks want the table manual and
  completed, totals that add up to 0, and its table points in the independent count of the ranking.
- A seat change after the seats are saved.

  Done (plan.cornerCases, round 1, table 4). The players swap East and South before the first hand (answer 200) and
  try again after it (answer 409). The checks want both answers and the new seats on the table.
- A phone without network for a minute, and an app sent to the background.

  Done (plan.cornerCases, last round, table 4). After 2 hands the scorer saves a hand and its answer never arrives (the
  network is gone). A minute later it sends the same hand again, with the version of then, as the app does after a
  failed send. The highest slot of the table goes to the background for that minute (no polls) and then looks again.
  The checks want the second send refused (409, the table changed), the hand saved once, and the phone back from the
  background showing at least the hands it had.
- Ties in the ranking.

  Done (plan.cornerCases, corners only, round 1, table 7: corners has 28 players for it). The table plays only draws
  with no penalty, and so does every table of the next rounds with one of its players. The 4 players finish with the
  same table points and score. The checks want them level in the independent count, and the check of the places wants
  the server to give them the same place.
- `small` only: a private championship, a disabled user, editors and admins added, changed and removed in the middle,
  a change of branding and of venue in the middle.

## The plan and the registry

`scenario.mjs plan` takes free EMA players of the staging registry: not disabled, with no account, with a first and a
last name, no "test" in the name, not from 00 (the Google Play reviewers have 00000000) nor from 99980000 (the
calibration), and only numbers of 8 digits (the registry keeps a few that lost their leading zero, such as 8010027: the
import pads the file to 8 digits, does not find them and refuses the file). Two EMA guests play when the registry has
them.

The rounds are planned an hour later, in Madrid. The form of a new tournament keeps its dates on today, so after 23:00
the plan keeps them on today at 23:59 (the rounds start when the organizer presses Play anyway). The tablet of the
organizer runs in the Madrid time zone too (`-timezone Europe/Madrid` in scenario.yml and `cmd alarm set-timezone` in
emulator-organizer.sh), so the day of the form is the day of the plan.

## The organizer on the emulator

`ScenarioOrganizerTest` works around what a tablet emulator in Actions does, found in the runs of 18 and 19 November:

- The Timer opens in its own task. Its back key is not reliable, so the test closes the Timer window itself, and brings
  the running app back to the front with `am start -f 0x10020000` (a launcher intent would open a second MainActivity
  with its own Koin graph).
- The launcher can keep the window focus, and then a touch is refused. The Timer buttons and the Back of the top bar are
  pressed with their click action (`performSemanticsAction(OnClick)`), and a refused tap is tried again. The Back is
  found by its label, as a tablet shows it as text.
- Substitute appears in the menu of a slot once its table has a hand, so the substitution waits for the first hand of
  the round and for the Players screen to load. When the EMA search shows no single row, the test searches the email.
- The poll can draw the table again while the test types a score: the test types it again.
- On a failure, the job log shows the last steps of the organizer, the last lines of the app (at info level) and the
  crashes, with every address hidden (`hide_emails`).

The mailbox is tried for about 12 minutes when it does not open (it was once down for more than 3 minutes).

## The checks

`report.mjs` scores every table again with its own count (teams, chicken hands, best hands, penalties) and compares it
with the server, together with the hands of the bots, the times and the errors (docs/rehearsal.md, The checks).

It also asks the server the places of every player, as the list of tournaments shows them (podium, team, chicken
hands and best hand), and compares each one with its own count. The bots mark about a third of the hands won on a
discard with 8 to 12 points as chicken hands, so every scenario has some.

## The cost

After the checks, the job **Cost** reads from Cloud Monitoring of staging what the game day used (Firestore, the API,
Storage), prices it in euros and projects it to a real tournament of 60 players. Only a report: it never fails the
run. See docs/benchmark.md, "Cost".

## Corners: the corner cases in minutes

`corners` is a small tournament of 28 players with 2 rounds of 8 minutes, and every corner case in round 1. The race,
the penalties and the draw are at table 1, the late table and its substitution at table 3, the seat change at table 4,
the deleted and edited hands at tables 2, 4, 5 and 6, and the tie at table 7. The slow table of the extra time, the
abandoned table, the manual totals and the phone without network are at tables 1 to 4 of round 2, the last one. It takes about
45 minutes. Turn off "Deploy main to staging first" when only the tests changed, to save about 9 more minutes. When the
organizer fails, the whole run stops, so the bots do not play on for nothing. The full scenarios stay as they are.

## The registrations

The job **Registrations before the tournament** (`scripts/rehearsal/registrations.mjs`) runs beside the tournament of
the scenario, in a second tournament that opens registrations (docs/registrations.md). With the people of the plan:

- The bot admin creates it with 16 places and a public link, checks the link, previews and imports a Google Form of 4
  people, and adds by hand those who do not fit in the 20 registrations that one address can send in 15 minutes.
- Players of earlier game days register with their account. New players register with their email and open the link
  of the registration email in the mailbox of the tests. One person never opens it: it stays pending and out of the
  public list. One person writes "I have no EMA number".
- The public list has 16 places, the waiting list and no email. A person with a place cancels (the organization gets the
  email), and the bot admin gives the place to the first of the waiting list.
- With teams, four captains create their teams and add three people each.
- The bot admin closes the registrations and makes the draw (3 rounds, nobody meets twice, four teams at each table),
  and every person with a place gets a slot.

Each step prints ✓ or ✗ in the summary of the job, and `registrations.json` keeps them. A ✗ fails the job.

### By hand on the tablet (corners)

The bots go through the API. In `corners` the tablet also goes through the screens (plan.byHand, before the tournament
of the scenario, in the phase setup of `ScenarioOrganizerTest`):

- The bot admin creates the organization "Game day co-club" in Organizations (once), and in Users makes the account
  "Bot club admin" (`+mts-botclub`, made by `rehearsalAccounts.ts --calibration-setup` as a plain account) the Club
  Admin of "Game day club".
- The club admin signs in and creates a tournament with Open registrations: 16 places, 3 rounds, its club as main
  organizer (the form puts it) and "Game day co-club" as co-organizer. The tournament opens on its registrations.
- Import Google Form with `registrations-form.csv` (the first 16 people of the plan): 16 with a place. Add a person
  (the 17th) from the EMA registry: the waiting list.
- A player (`+mts-cal01`) signs in to the player app, opens the tournament, registers (number 2 of the waiting list)
  and cancels in My registration.
- The club admin removes the first person, gives the place to the person of the waiting list, closes the
  registrations in Registration settings and makes the draw: the tournament opens on its players.

A step that fails stops the organizer, as any other step of the tablet.

## Tests of the scripts

`node --test scripts/rehearsal/test/*.test.mjs` (about 6 s, no network, no account) runs:

- `bots.test.mjs`: the corners plan of `scenario.mjs`, played by `bots.mjs` against a staging in memory
  (`fakeStaging.mjs`: the routes that the bots and the report call, an organizer that starts the rounds and closes the
  abandoned table, an editor that deletes and corrects hands), and checked by `report.mjs`. Every corner case and every
  general verdict must pass.
- `report.test.mjs`: each verdict of a corner case with a run that passes it and runs that fail it, the details of a
  failed case, ties at a table, a substituted slot, and the slowest of a long list.
- `scenario.test.mjs`: the players of the registry, the planned start near midnight, the corner cases of each
  scenario, the slots and the import files.
- `registrations.test.mjs`: who registers how within the calls of one address, the draw (every seed seats everybody,
  nobody meets twice, four teams at a table), the token of the email link, and the whole registration with and without
  teams against a server in memory.
- `api.test.mjs` and `mail.test.mjs`: the metrics of long runs, the retry of a read, and the retries of the mailbox.
- `scripts.test.mjs`: the shell scripts, the hidden addresses of the job log, the Madrid time zone, and one major
  version of each action in the workflows.

They run locally, not in CI (no new checks until production). Run them after a change in `scripts/rehearsal/`.
The backend side of the medium game day (the saves of 16 tables at once) is in `playerHand.integration.test.ts`.
