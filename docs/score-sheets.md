# Score sheets

The sheets that the players fill in by hand at the tables, to print on A4. They follow the model the organizers used in
a spreadsheet (`HojaPuntuacionMCR.xlsx`), with the icons and logos of the app.

## In the admin app

"Score sheets" in the "More" menu of a tournament (after "Tables IDs") opens `ScoreSheetsDialog`. It opens over the
table, also with changes not saved. The user chooses:

- **Blank**: one sheet, to copy as many times as needed.
- **With round and table**: one sheet for each table of each round, with the round and the table written in its boxes,
  ordered by round (every table of round 1, then round 2...). `numRounds × numPlayers / 4` sheets. Not available
  while the tournament has no tables.
- **EMA logo** (on by default): the logo of the EMA beside the logo of the organizer.

The dialog says whether the sheet has the chicken hand column. "Create PDF" makes the PDF in the background, like the
other exports, and saves it as `<short name>-<year>-score-sheets[-tables]-<date>.pdf`.

## The sheet

Always A4 portrait and always in English, as the cards of the tables (`scoreSheetsService.ts`, pdfkit):

- Top left: the short name of the tournament (the name when it has none), in up to two lines. Beside it the gong of the
  Timer (the round) and the table of the Tables tab (the table), each with a box.
- The four seats with the wind icons of the app (East, South, West, North) and a Name and an Id line each.
- A row for each of the 16 hands (`HANDS_PER_TABLE`): #, Winner Id, Loser Id, Hand Points, the chicken hand box (only
  when the tournament counts chicken hands, `countChickenHands`), Score and Pty. of each seat, and under them the running
  total of each seat (not on hand 1). The last column, "Sum 0", has a box to tick once the hand adds up to 0. A
  thicker line every four hands, when the prevailing wind changes.
- Table points and Signatures under the seats.
- Bottom left: the logo of the main organizer in a circle (or its initial on its color, as the app shows it), and the
  logo of the EMA when chosen.

Without the chicken hand column its room goes to the Id and Points columns, so the seats do not move.

## API

`GET /tournaments/{id}/score-sheets?mode=blank|tables&ema=true|false` (permission `tournament.view`) returns the PDF.
`mode=tables` answers 409 `NO_TABLES` when the tournament has no tables or rounds.

## Assets

The PDF uses the font, the icons and the logos of the app. The build of the backend (`scripts/syncIdCardAssets.cjs`)
copies them to `lib/assets` from:

- `ui/src/commonMain/composeResources/drawable/logo_ema.png`: the logo of the EMA with a transparent background (800 px,
  cut out of the white background of the original; the white inside the circle stays).
- `icon_chicken.png` and `icon_wind_*.png` of the same folder.
- `designsystem/.../font/go3v2.ttf`, for names that Helvetica cannot write (Cyrillic, for example).

The gong (`GongIcon.kt`) and the table (Material `table_restaurant`) are drawn as vector paths.
