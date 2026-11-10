# Public data

Everyone can read every public tournament without an account: the information, players, teams, rounds, tables with
their hands, rankings and statistics, and the statistics of every person. An account is needed only to write hands at
a table where the player sits, and to know which player, team and tables are the own ones.

The management app does not change. It keeps its routes and its sign-in.

## Routes

The routes under `/public` never read the token. Every caller gets the same answer, so Firebase Hosting can keep it
in its CDN and every viewer of a tournament shares one call to the function.

| Route | Answer | CDN cache |
| --- | --- | --- |
| `GET /public/tournaments` | All public tournaments, without the creator | 60 s |
| `GET /public/tournaments/:id` | One tournament | 15 s in play, 300 s completed |
| `GET /public/tournaments/:id/sync/manifest` | The revisions of the tournament | 15 s in play, 300 s completed |
| `GET /public/tournaments/:id/players`, `teams`, `rounds`, `tables`, `tables-with-hands`, `rounds/:r/tables-with-hands`, `tables/:r/:t` | The content of the tournament. Players never carry an email or an account | 15 s in play, 300 s completed |
| `GET /public/statistics/overview` | Sums and leaderboards of all tournaments | 60 s |
| `GET /public/statistics/persons/:emaId` and `.../standings` | The results of one person in the public tournaments | 60 s |
| `GET /public/players` | The EMA registry: number, name and country. No photos | 1 h |
| `GET /public/countries` | The countries | 1 h |
| `GET /public/sync/manifest` | The global revisions | 15 s |
| `GET /me` (token) | The EMA number of the account and its slot and team in each tournament | none |

A browser keeps an answer for 5 seconds at most (`max-age`), so polling sees new data soon after the CDN does.
A hidden tournament answers 404 on every public route.

## Hidden tournaments

`isPublic` on the tournament document. A missing value means public. An admin sets it to `false` through
`PUT /tournaments/:id/settings` to hide a test tournament.

## Limits

- `/public` allows 600 requests a minute for one address on one function instance.
- The `api` function runs at most 20 instances, so a flood of requests cannot grow the bill without end.

## Tests

- `publicReads.integration.test.ts`: no token needed, hidden tournaments answer 404, no email or account in the
  players even for an admin token, longer cache for a completed tournament, the registry has no photos, `/me`.
- The public routes are listed as `PUBLIC` in the `MATRIX` of `rolePermissions.integration.test.ts`.
