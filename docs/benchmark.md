# Benchmark

The benchmark measures production as a guest sees it, so a slower deploy shows up the same day.

- **Backend:** the response time of each public read of the player app (`/public/...` and `/config`).
- **Screens:** how long each screen of the player web app takes to show its content, on a desktop and on a phone.

## When it runs

The workflow **Benchmark** (`.github/workflows/benchmark.yml`) runs:

- after each successful run of **Tests and deploy** on `main`;
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
| Tournaments › General statistics | The statistics tab of the start screen. |
| Tournament › Tables, Ranking, Statistics, Info | Each tab of a tournament, opened with a page refresh. |
| Tournament › Statistics › Players | The statistics of one player in the tournament. |

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
