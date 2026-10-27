# Statistics

Both apps show the same statistics. One shared use case computes them: `CalculateStatisticsUseCase` in `:domain`.
`LoadStatisticsUseCase` loads one tournament through the existing repositories and revision cache.
`LoadOverviewStatisticsUseCase` shows many tournaments from the sums that the server keeps (see Server statistics store).

## Rules
- A table counts when it is completed. It counts with hands or with manual totals.
- Hand statistics use only completed tables that have entered hands. Totals-only tables have no hand data.
- A hand counts when it is done (or its table is completed) and has a valid result. Rules match `handScoring.ts`.
- The place at a table comes from the score. Equal scores share the place.
- Rates need at least 8 hands before a leaderboard shows them.
- "Me" and "My tournaments" link a person to slots with the EMA id of the account.

## What the screens show
The tournament and table views use plain rows with a title. The player view uses three big cards (`PlayerStatisticsPanel`).
The two apps use a wider viewport (1100 dp) for the Player, Me and My tournaments scopes.
- **Player**: cards in this order: Skills, Places, Totals, Records, Best Rivals (scope Me). The Skills and Places cards have no title. On wide screens the header and Skills are on the left, Places on the right, and Totals use the full width below.
  - **Totals**: tournaments, tables, hands, a ring chart of the hands won, lost and drawn (the outer ring shows the way of each win or loss, and the chicken hands as a part of the wins by discard).
  - **Records**: highest table score, highest hand score, hands won on a table, highest penalty got (the biggest single penalty, `TableResult.maxPenalty`), tables won in a row, hands won in a row, hands without a deal-in. Runs of tables and hands join across all tables of the player in play order, also from one tournament to the next. A table without entered hands does not break a run of hands. Data: `TableResult.winRun` and `noDealInRun` (`HandRun`: best, start, end).
  - **Best Rivals**: the top 3 opponents (the name of each rival with five big numbers: shared tables, finished above you, your discards to him/her, his/her self-draws, his/her wins on a discard of another player) with at least 2 shared tables. The score is `10 * tables behind + 5 * discards + 3 * self-draws + 1 * wins on another player's discard`, divided by the shared tables. The weights are in `RivalWeights`. Every number counts only the tables that you and the rival played together. The tooltip of the card says this and lists the four criteria and their weights, without reasons. An opponent is the EMA number, or `tournamentId#slotId` without one (`RivalTally.opponent`).
  - **Skills**: a radar chart with four axes. Win rate (wins per hand, full at 40%), self-draw rate (self-draws per win, full at 50%), deal-in rate (deal-ins per hand, full at 25%) and hand value (average winning hand, from the 8 point minimum to 32). The full values are in `skillsOf`.
  - **Places**: a ring chart with the count and share of each place, the average place, and a chart of the place in the last 10, 20, 50 or all tables. Each dot has the date and time of the round under it.
  - **By tournament** (scope My tournaments in the player app, Player tournaments in the admin app): one card for each tournament, with the tables, hands, best table score, rank, a skills chart and a places ring chart. The newest tournament comes first and the five newest are open. Only five show at the start. "Show 5 more" and "Show all" show more. A dropdown of the tournaments (typing filters it) scrolls to one tournament. A "From" and a "To" date show only the tournaments in that range. The ranks load for the tournaments on screen only.
- **Tournament**: summary (tables completed, hands played, draw rate, average winning hand, highest hand), top lists (points, win rate, deal-in rate, self-draws).
- **Table**: result table (place, score, points, wins, deal-ins), hands, score after each hand.
- The player header shows photo, name, country and EMA number. The tournament header shows logo, name and dates.

## Where the buttons are
- **Statistics icon**: `StatisticsIcon` in `:ui` (Insights). Every statistics button, tab and screen uses it. The ranking icon is `Leaderboard`.
- **Player app**: the second tab "Statistics" of the tournaments screen has two tabs inside, "Me" and "My tournaments". A fourth tab "Statistics" on the Tournament screen shows that tournament. The My tournaments, Table and Player screens have a top bar button.
- **Admin app**: the statistics button is on the right of the top bar of the Tournaments, Tournament, Players, Teams and Tables screens.
  The App usage button (administrators only) is on the left of the Tournaments top bar, after the theme button.

## Admin app scopes
- **Tournaments**: all tournaments together by default (administrators only). A search field picks one tournament.
- **Table**: opens from the Tournament screen when a table is selected.
- **Player in tournament**: one slot of the picked tournament.
- **Player overall**: one EMA player over all tournaments.

## Server statistics store
The server keeps the statistics up to date. A request never builds them.
When a table that is or was complete changes (save, reset, edit), the same Firestore transaction updates the store (`statisticsStore.ts`, called from `saveTableStateInTransaction` and `resetTable`). Other saves do not touch it.
The update uses the table and hands that the transaction already holds. The memory use is the size of one table, whatever the number of tournaments.

| Collection | Content |
| --- | --- |
| `statsTables/{tournamentId}_{round}_{table}` | The digest of one completed table: the table totals and one result for each seat (`buildTableDigest` in `statisticsDigest.ts`). `emaIds` lets a query find the tables of a player. |
| `statsPlayers/{emaId}` | The sums of one player over all tournaments. `leaderWinRate` and `leaderDealInRate` exist only with at least 8 hands, so the leaderboard queries skip other players. |
| `statsTournaments/{tournamentId}` | Points and score of every slot, the team of every slot, and the chicken hands and best hand of every slot at every table. The standings read only this document. |
| `statsGlobal/overview-{0..7}` | The sums of all tournaments, in 8 shards. A request adds the shards. |

An update subtracts the stored digest of the table and adds the new one, so an edit does not count twice.
`TableResult` has `rivals`, `winRun` and `noDealInRun` since digest version 3, and `maxPenalty` since version 4. Change `DIGEST_VERSION` when the rules change, then run the backfill.

Requests (they read a fixed number of documents, or the tables of one player):
- `GET /tournaments/statistics/overview` (administrators): the sums, the highest hand, the number of tournaments, and the players of three leaderboards (top 5 each).
- `GET /tournaments/statistics/me` (any account): the results of the player of the account. The account sees only the tournaments that it can see in the tournament list.
- `GET /tournaments/statistics/persons/:emaId` (every signed-in account): the same for another player. The results come only from the tournaments that the account can see. A player picks the other player from `GET /players`, which lists only the people that the player plays with.
- `GET /tournaments/statistics/me/standings?tournaments=a,b` and `.../persons/:emaId/standings?tournaments=a,b`: the final place of the player in up to 25 tournaments, with the place of the team, the place by chicken hands and the place of the best hand. The apps ask only for the tournaments on screen.

The Tournaments scope without a chosen tournament is for administrators.
The tournament scope with one tournament loads the hands of that tournament. The person scopes use the server store in both apps and load no hands.

Other events that call the store:
- Assigning an EMA number to a slot after tables started builds the statistics of the tournament again (`refreshTournamentStats`).
- Deleting a tournament takes its numbers out of the sums first (`removeTournamentStats`).

### Backfill
Run once after the first deploy, and again after a change of `DIGEST_VERSION`. It works one table at a time and is safe to repeat.
```
cd firebase/functions
npm run backfill:statistics          # preview
npm run backfill:statistics:apply    # write
```
It also deletes the old `tournaments/{id}/cache/statistics` documents.

## App usage (administrators only)
`GET /admin/usage?days=7|30|90` builds the report. The admin app has one chip for each period.
Every number comes in two periods: the current one and the one of the same length before it. The screen shows the change.

Sources:
- The change log (`tournaments/{id}/changes`): table saves, resets and refused saves (`conflict`). Entries have `source` (`player` or `editor`), `uid`, `at` and `handChangeCount`. Entries of the server (`system`) do not count as work. Entries without a source count as editor saves.
- The activity log (`activity`): setup work like settings, players, teams, invitations and accounts.
- Firebase Authentication: role, creation date and last sign-in of each account.
- The tournaments, tables and player slots: settings, data entry modes and invitation status.

Sections of the screen:
- **KPIs**: active users, table saves, tournaments with activity, player share, setup work and conflict rate.
- **What stands out**: facts from the data, like a fall of saves, accounts that never signed in, invitation acceptance and unused features. The rules are in `UsageInsights.kt`.
- **Table saves by day** and **When tables are saved**: a heatmap of weekday and hour. The hour is local to the time zone of each tournament.
- **Who uses the apps**: the status of each account and a filter. Active did work in the period. Signed in only has a sign-in and no work. Dormant signed in before the period. Never signed in has no sign-in.
- **What is used and what is not**: features with uses, users, adoption and last use. A feature with no use shows in red. Uses of actions count the period. Setup features and data entry modes count all time.
- **How far tournaments go**: tournaments created, with players, with invitations, with player accounts, with match data and completed.
- **Where tournaments are**: tournaments, players and saves for each country.
- **Tournaments**: status, completion, last activity and details. A tournament that started and has no saves shows "No match data".

The last sign-in is the last token refresh when it exists. It is the best estimate of when a person used the apps.

## Player level

- The **skill score** is the mean of the four skill axes of the skills chart, from 0 to 100. `SkillScale` in `Statistics.kt` has the axis limits.
- The **level** comes from the score: Beginner (under 30), Intermediate (30-44), Advanced (45-59), Expert (60-74), Master (75 or more).
- A player needs 8 hands for a level. The level is provisional under 100 hands.
- The limits are a first estimate on an absolute scale. Calibrate them with real data before the team uses them to compare or seed players.

### Feature flag

- The Firebase Remote Config key `levels_enabled` (boolean) controls all level UI. The default is off.
- The server reads the key and serves it on the public route `GET /config`. The server caches the value for 60 seconds.
- Both apps load the flags at start. The flags go to the UI through `LocalFeatureFlags`.
- When the flag is off, the apps hide the medals, the level badge, the skill score and the score change. The skills chart stays.

### Where the level shows

- **Statistics screens**: a large medal with a ribbon (`LevelRibbonBadge`) above the skills chart, with the skill score and an info icon.
- **Player app, table lists**: a small medal (`LevelMedalTag`) before the name of each player. A tooltip gives the level and the score.
- **Player app, table screen**: the medal shows the level after the round of the table. Under the name, an arrow shows how much the table changed the skill score. The change needs a score before and after the round.
- **Scope**: in the player app the level uses only the completed tables of the open tournament. It is provisional until 100 hands. A level over all tournaments needs the person results of the server store, which only the admin app loads.
- **Icons**: sprout (Beginner), tree (Intermediate), flame (Advanced), star (Expert), black hole (Master). The ring has one more lit segment for each level.
