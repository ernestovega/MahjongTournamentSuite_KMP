# Rehearsal of a tournament in staging

Before the first real tournament, a full team tournament of 60 players is played in staging (docs/staging.md):
Ernesto as the organizer and as one player, emulators that play with the real app, bots that play through the API, and
spectators that only read. The plan of the day and its targets are in the rehearsal plan artifact.

## Who plays each slot

The account of slot N is the local part of `STAGING_ADMIN_EMAIL` plus `+mts-jNN` (slot 7 is `<local>+mts-j07@<domain>`).
Ernesto creates these accounts with the invitations of the tournament. Then `Rehearsal > passwords` gives them the
rehearsal password, so the bots and emulators can sign in.

| Slots | Who | Where |
| --- | --- | --- |
| 1 | Ernesto, with his phone | |
| 2-5 | Emulators with the app | MacBook Pro |
| 6-9 | Emulators with the app | GitHub Actions |
| 10-48 | Bots | GitHub Actions, three jobs (three addresses) |
| 49-60 | Bots | MacBook Pro |
| - | 99 spectators | GitHub Actions, with the bots |

At each table the lowest slot writes the hands, as the scorer of a real table, and the others only read. So a table
with Ernesto is written by Ernesto, and a table with an emulator by the emulator.

## The password

It lives in the Secret Manager of staging as `REHEARSAL_PASSWORD`. The first run of the Rehearsal workflow creates it.
To read it on the Mac:

```bash
gcloud secrets versions access latest --secret=REHEARSAL_PASSWORD --project=mts---staging
```

## The workflow (Actions > Rehearsal)

- `calibrate`: creates the 16 calibration accounts (`+mts-cal01..16`, EMA 99980001-16) and the bot admin
  (`+mts-botadmin`), plays a tournament of 16 bots in 2 short rounds and checks it. The tournament stays in staging, with the time of the run in its name. About 25 minutes.
  It tests the bots, a race between two phones on the same hand, a table that starts late, penalties, extra time and
  the admin closing open tables. With `emulators` on, slot 1 plays with the real app on an emulator.
- `passwords`: the rehearsal password for the accounts j02-j60. Run it after the invitations.
- `bots`: the bots j10-j48 and the spectators, in three jobs, and the app on four emulators (j06-j09), until `until`.
  Then the checks.
- `check`: the checks alone, for example after the bots of the Mac.
- `calibration-cleanup`: removes the calibration accounts and EMA players.

## The Mac part

Start four emulators, then from the project folder, with Node 22:

```bash
export STAGING_ADMIN_EMAIL=<the staging admin email>
export REHEARSAL_PASSWORD="$(gcloud secrets versions access latest --secret=REHEARSAL_PASSWORD --project=mts---staging)"
scripts/rehearsal/mac.sh 2026-11-19T19:45:00+01:00
```

It builds the staging app with its automatic player, puts slots 2-5 on the emulators
(`scripts/rehearsal/emulator-player.sh`), and runs the bots 49-60. The logs and reports go to `build/rehearsal`.
The bots alone:

```bash
node scripts/rehearsal/bots.mjs --tournament="Ensayo General MTS 2026" --slots=49-60 \
  --until=<end, as 2026-11-19T19:45:00+01:00> --report=build/rehearsal/bots-mac.json --title="Bots 49-60 (Mac)"
```

The automatic player of an emulator (`RehearsalPlayerTest`) uses the screens as a person: the reminder of the round,
the seats, Add win or Add draw, the numpad, Review and Confirm. It writes only when its slot is the lowest of its
table, and follows the table otherwise. `adb logcat -s REHEARSAL` shows what it does.

Options: `--venue-phones=60` (phones of one venue wifi, from one address), `--hand-seconds=60-90` (time between hands), `--penalty-rate=0.05`, `--late-tables=2_7` (the scorer starts
2 minutes late), `--race-tables=1_3` (two phones send the same hand at once), `--spectators=N`, `--verbose`.

## The checks

`scripts/rehearsal/report.mjs` reads the tournament as a spectator and scores every table again with its own code. It
compares that with the totals and table points of the server, and the hands of the server with the hands that the bots
saved. It prints one line per target:

- scores and table points equal to the independent count;
- no hand lost, changed or saved twice;
- no answer 5xx, no network failure, no answer 429;
- saving a hand under 1.5 s (p95), and a hand on the other phones of the table in under 20 s (p95);
- every table closed, and the integrity check without errors (calibration).

```bash
node scripts/rehearsal/report.mjs --tournament="Ensayo General MTS 2026" build/rehearsal/bots-*.json
```

## Limits to keep in mind

The public reads take 600 calls a minute from one address (per server instance). Phones on the wifi of a venue share one
address, and each one reads the manifest every few seconds. That is why the bots of GitHub run in three jobs.
