# Benchmark

The benchmark measures production as a guest sees it, so a slower deploy shows up the same day.

- **Backend:** the response time of each public read of the player app (`/public/...` and `/config`).
- **Screens:** how long each screen of the player web app takes to show its content, on a desktop and on a phone.

## When it runs

**Since 2026-11-24 it runs every morning** (until the tournament of February 2027) and by hand. The run after each
deploy stays off, because main deploys many times a day: to turn it on, uncomment `workflow_run` in the workflow.

The workflow **Check · Production benchmark** (`.github/workflows/benchmark.yml`) runs:

- after each successful run of **Deploy · Production** on `main` (off for now, see above);
- every day at 05:00 UTC;
- by hand: Actions › Benchmark › Run workflow. You can choose the web app, the tournament, and the number of calls
  and visits.

A time has a problem when it is over its budget, or clearly slower than the median of the last 5 runs (passed or
failed, so the baseline is not only the luckiest runs). The times of production vary a lot from one run to the next
(shared GitHub runners, Cloud Functions that start cold right after a deploy), so a problem seen once is only a
warning. The run fails when the previous run had a problem on the same line too, or when a call or a screen fails.
A real regression stays, so it fails on the second run after the deploy that caused it.

Its summary page has a table with every time, and its artifact `benchmark-results` keeps the results for 90 days.
The next runs compare with them.

## Backend

`node scripts/benchmark/backend.mjs` calls each endpoint 15 times (`--runs`). It picks the finished public
tournament with the most players (or `--tournament <id>`) and one of its EMA players. Each endpoint runs in two
modes:

- `cdn`: the call of the apps. Firebase Hosting can answer from its cache (see the `Cache-Control` of the public
  routes in docs/public-data.md).
- `origin`: the same call with a unique `bench` parameter, so the cache misses and the Cloud Function answers.

It also measures the clocks of the rounds, one table, the statistics of a group (the filters of the Statistics screen,
here the tournament), and, when a public tournament takes registrations, its public list and its public link.

One call before the measured ones keeps a cold start of the function out of the times. The result has p50, p95,
the maximum and the size of each answer.

If App Check is set to `enforce`, add a debug token of App Check as the secret `BENCH_APP_CHECK_TOKEN` and pass it
to the step. The script sends it in `X-Firebase-AppCheck`.

## Screens

The apps measure their own screens. `ReportScreenLoading` (ui module, called by `AppScaffold`) times each load:

- `open`: from the moment the screen opens until it stops loading;
- `tab`: a later load of the same screen, for example after a tab change.

Each load sends the Analytics event `screen_load` (`screen`, `tab`, `kind`, `ms`; see docs/telemetry.md), so the
real load times of the users are in GA4 too. On the web the load is also kept in `window.mtsScreenLoads`.

`node scripts/benchmark/screens.mjs` opens the player web app in Chromium (Playwright) and reads those times:

| Visit | What it measures |
|---|---|
| Tournaments (cold) | The first visit of a new user, with an empty cache. The time since navigation includes the download and start of the app. |
| Tournaments (warm) | A page refresh: the files of the app are in the cache. |
| Tournaments › General statistics | The Statistics tab of the start screen (tabs In play, All and Statistics). |
| Tournament › Tables, Ranking, Players, Statistics, Info | Each tab of a tournament, opened with a page refresh. |

The script opens a tab by changing the saved tab of the screen in `sessionStorage` and reloading, like a page refresh
on that tab. Each visit runs 3 times (`--samples`) on two profiles: a desktop, and a Pixel 7 with the CPU four times
slower and a fast 4G network. The report gives the median.

The page gets a stub of `telemetry.js`, so the benchmark sends nothing to Analytics and needs no consent.

To run it on a computer:

```bash
npm install --no-save --prefix /tmp/pw playwright@1.63.0 && /tmp/pw/node_modules/.bin/playwright install chromium
cp scripts/benchmark/screens.mjs /tmp/pw/ && node /tmp/pw/screens.mjs --samples 1
```

## Budgets

`benchmarks/budgets.json` has the budgets:

- `backend.p95Ms`: the p95 of each endpoint and mode. `default` applies to the endpoints without their own line.
- `screens.budgetMs`: the screen time of a warm visit (`default`), and the time since navigation of a cold start
  (`coldStart`), for each profile. A screen can have its own line by its name in the report.
- `regression`: a time is "slower" when it is more than `factor` times the median of the recent runs and at least
  `minMs` more.

Change a budget only on purpose, for example when a screen starts to load more data.

`node scripts/benchmark/compare.mjs --current <results> --history <folder>` writes the report
(`benchmark-report.md`) and fails on a problem. It runs at the end of the workflow.

## Cost

The benchmark and the game day also say what the app costs, in euros. Only as a report: nothing fails on a cost.

### Firestore reads of each call

The API counts the Firestore operations of each request (`firebase/functions/src/api/middleware/firestoreUsage.ts`):
a read per document returned (at least one per query), one per 1000 index entries of a count, a write per document
written, a delete per document deleted, as Firestore bills them. A request with the header `X-MTS-Usage: 1` gets them
back in `X-MTS-Firestore: reads=12; writes=0; deletes=0`. The apps never ask, so nothing changes for them.

- The **benchmark** asks in `origin` mode, and its table shows the reads of one call of each endpoint next to the
  previous run (⬆️ when they grew). A screen that starts to read more costs more on every poll of every phone.
- The **bots of the game day** ask on every call. Their report (`bots.json`) keeps the reads, writes and bytes of
  each kind of call.

### Cost of a game day and of a real tournament

`scripts/rehearsal/cost.mjs` reads from Cloud Monitoring of staging what the project used while the bots played:
Firestore reads, writes and deletes, requests, CPU and memory of the API (Cloud Run functions), bytes that the API and
Storage sent. It prices them with `benchmarks/prices.json` (list prices in USD and the euro rate; update them when
Google changes them) and projects them to a real tournament:

- the screens (GET calls) grow with the players and the minutes on screen: a real round lasts 2 hours, a round of
  the game day 12 to 25 minutes, and the bots poll as the app does (2 s the own table, 5 s the player in play, 15 s the
  rest);
- the actions (saves of hands, sign-ins) grow with the players and the rounds;
- the bots tell how much of the reads, calls and bytes are screens and how much actions;
- the web app download (15 MB, twice a day for each player and spectator) is an estimate.

The defaults are the tournament of February 2027: 60 players and 20 spectators, 7 rounds of 120 minutes with 15
minutes between rounds, over 2 days, one tournament a month (`--target-players`, `--target-spectators`,
`--target-rounds`, `--round-minutes`, `--break-minutes`, `--days`, `--tournaments-per-month`, `--web-mb`,
`--web-loads`). The report gives the cost of the game day, of a tournament day, of the whole tournament and of a month,
with and without the free tiers (the free tiers count as if the project used nothing else).

It runs:

- at the end of each game day (Actions › Test · Game day · Auto, job **Cost**, artifact `cost`);
- by hand for any window of staging: Actions › Test · Game day · Participating, action `cost`, with `since`, `until`
  and the name of the tournament of that window in `tournament` (for its players and rounds).

The service account of the workflows needs to read Cloud Monitoring of staging, once:

```bash
gcloud projects add-iam-policy-binding mts---staging \
  --member="serviceAccount:github-deployer@mts---staging.iam.gserviceaccount.com" --role="roles/monitoring.viewer"
```

The real bill is in the Cloud Console, Billing › Reports. The report is an estimate to compare runs and to see what a
change costs.
