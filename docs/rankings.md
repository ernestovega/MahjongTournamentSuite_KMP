# Rankings screen

## Purpose

The Rankings screen shows tournament results on a separate display.

Its behavior follows the old Windows tournament application. The design uses the current Compose components and keyboard controls.

## Ranking views

The screen cycles through these views:

1. Player ranking.
2. Team ranking, for team tournaments only.
3. Chicken hands, when chicken hands exist.
4. Best hands, when scored hands exist.

Each view is one scrollable list. Empty special-hand views are not included in the cycle.

When automatic paging is active, the screen scrolls the current list by the selected row count. After the last subgroup, it changes to the next view and returns to Players after Best hands.

## Player ranking rules

The calculation adds each player's saved points and score from every tournament table.

Players are sorted by:

1. Total points, from highest to lowest.
2. Total score, from highest to lowest.

A blank or invalid saved value counts as zero. Points accept both a decimal point and a decimal comma.

The calculation uses all saved tables.

## Team ranking rules

Team ranking is available only when the tournament has team mode enabled.

The calculation adds the points and scores of all players in each team. Teams use the same sort order as players.

The current data model stores a numeric team ID. The screen displays it as `Team <ID>`.

## Chicken-hand ranking rules

The calculation counts hands marked as chicken hands for each recorded winner.

Players are sorted by:

1. Chicken-hand count, from highest to lowest.
2. Total tournament points, from highest to lowest.
3. Total tournament score, from highest to lowest.

Players without a chicken hand do not appear in this view.

## Best-hand ranking rules

The calculation uses hands with a valid winner and numeric hand score.

It sorts hands by score and shows the ten highest hands. A player can appear more than once.

## Tournament best hands

The tournament has up to three best hands. Hands tied with the third hand also count, so the tournament can have more than three.

- A table can have more than one best hand of the tournament.
- Each table stores its own best hand scores (`bestHandScores`): its three highest done hand scores and their ties.
- The Tournament screen shows a trophy for each round and table that has a best hand of the tournament. The trophy shows a count when there is more than one.
- Each tournament has two switches, **Best hands** and **Chicken hands** (`countBestHands`, `countChickenHands`). Both are on by default and for old tournaments. The admin sets them when creating the tournament and can change them in the tournament settings.
- When a switch is off, the Rankings screen does not show that list. The app also hides that statistic in the table header and the tournament badges. When Chicken hands is off, the hands table has no Chicken column.
- Only done hands with a numeric score count. The server and the app calculate the chicken hand count and the best hand scores from the hands. Users cannot type these values.

## Player identity rules

Tournament player records are schedule slots. The Rankings screen does not use their stored name or country.

- It resolves the name and country from the assigned EMA player.
- It shows `Player <ID>` for an unassigned slot.
- It shows `🌐` when the EMA player has no country.
- It treats the EMA pseudo-country `EU` as no country.

## Display controls

The screen opens paused. The play button starts automatic paging.

The player list columns are rank, country flag, player, points, score, and team. The team column is last. Team labels use the form `Team <ID>` because the current data model has no custom team-name field.

The best-hand list shows rank, country flag, player, and hand score. It does not show points or score.

The table has a fixed centered width, alternating row backgrounds, no row separators, and no ranking scrollbar. Names keep uppercase first names and use capitalized last names. The column headings are compact. The ranking countdown appears as seconds in the top-right corner while paging runs.

| Control | Behavior |
| --- | --- |
| Play or Pause | Starts or stops automatic paging. |
| Previous or Next | Changes the ranking view immediately. |
| Row controls | Set the visible row target. The default is 20. The minimum is 16. The maximum uses the smallest readable font size, 11 sp. |
| Second controls | Set the page interval. The default is 7 seconds. The minimum is 1 second. |
| Refresh | Loads current tournament data and returns to the first page. |
| Light or dark icon | Changes only this Rankings screen between light and dark themes. |

The controls use the shared focus components. They support Tab, Shift+Tab, arrow keys, Enter, and Numpad Enter.

## Data loading

The screen loads a snapshot when it opens or when the user selects Refresh.

It loads:

- the tournament settings;
- tournament player slots;
- the shared EMA player base;
- all table summaries;
- each table and its hands.

Table detail requests use at most eight concurrent requests. This limit reduces load time without sending all requests at once.

The ranking does not update in real time between refreshes.

Automatic paging uses the selected row target to change the font size. More rows use a smaller font. Fewer rows use a larger font.

The Rankings screen starts with the saved app theme. Its local theme button does not change the app or another standalone window.

## Independent launch behavior

Each platform opens Rankings outside the main navigation flow.

| Platform | Behavior |
| --- | --- |
| Desktop | Opens a separate Compose window with its own dependency context. Closing the main window does not close Rankings. |
| Android | Opens `RankingsActivity` in a separate Android task. Removing the main task does not close the Rankings task. |
| Web | Opens a separate browser tab with the tournament ID in the query string. Closing the main tab does not close Rankings. |

The screen needs the saved authentication session to load data. An operating-system force stop or browser shutdown still closes it.

## Main source files

- Ranking models: `domain/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/domain/model/TournamentRanking.kt`
- Ranking rules: `domain/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/domain/usecase/CalculateTournamentRankingsUseCase.kt`
- Data loading: `composeApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/presenter/RankingPresenter.kt`
- Screen: `composeApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/screen/RankingStandaloneScreen.kt`
- Desktop windows: `composeApp/src/jvmMain/kotlin/com/etologic/mahjongtournamentsuite/StandaloneWindows.kt`
- Android task: `androidApp/src/main/kotlin/com/etologic/mahjongtournamentsuite/RankingsActivity.kt`
- Web entry point: `composeApp/src/webMain/kotlin/com/etologic/mahjongtournamentsuite/main.kt`

## Tests

Ranking rule tests are in:

`domain/src/commonTest/kotlin/com/etologic/mahjongtournamentsuite/domain/usecase/CalculateTournamentRankingsUseCaseTest.kt`

Run all domain and data tests with:

```bash
./gradlew :domain:allTests :data:allTests
```

The project has no UI test harness. Validate the screen manually on each target after changing layout behavior. The JVM and Wasm Compose compilation checks are:

```bash
./gradlew :composeApp:compileKotlinJvm
./gradlew :composeApp:compileKotlinWasmJs
```
