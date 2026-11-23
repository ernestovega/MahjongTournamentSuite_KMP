# QR code of a tournament

A tournament has one QR code. A player who scans it with the phone camera opens the tournament in the player app, on
its tables tab, and over it their own table: the table in play, or the next one. The same code goes on every physical
table, on a card with the number of the table in big, so the card also tells the players which table is which.

## The link

- A tournament with a public link (`registration.slug`, see registrations.md): `https://mts.mahjongmadrid.com/<slug>/my-table`,
  for example `/6thmmc/my-table`.
- Any other tournament: `https://mts.mahjongmadrid.com/t/<tournament id>/my-table`.
- `mesa` works in place of `my-table`, for a link typed by hand. The old links of the cards of one table
  (`/<slug>/table/<n>`) open the same as `my-table`.

The admin app builds the link with `Tournament.myTableQrLink` (domain) and the web of the players `PublicSite.url`
(data): the address of the page on the web, production or the Firebase address of the project on Android. The backend
builds the same link with `myTableQrLink` of `tableQrService.ts` and `PLAYER_APP_URL`, so staging prints staging links.

## What the player app opens

`registrationLinkOf` reads the link (`RegistrationLink.myTable`). The app opens the tournament on the tables tab
(`TournamentRoute.myTables`, "Me"), and `TableQrLinkPresenter` looks for the table of the signed-in account
(`Tournament.myQrTable`):

1. its table in the round in play, or in the round that starts within the seating time (the same round as the reminder
   of the own table), when that table is not closed yet;
2. else its table in the next round that has not started, the first one not closed.

Without an account in the tournament, before the draw, in a finished tournament, or when the player has played all
their tables, the app shows only the tables tab. Back from the table shows the tournament.

The Android app opens these links when it is installed (App Links): `MainActivity` has an `autoVerify` intent filter
for `https://mts.mahjongmadrid.com/.*/my-table` (and the old `/table/` and `/mesa/`), and passes the link to the player
part (`SuiteStart.link`), which starts again and opens it like the web. The site serves `/.well-known/assetlinks.json`
(a rewrite of firebase.json to `playerApp/src/wasmJsMain/resources/assetlinks.json`) with the SHA-256 fingerprints of the
signing keys of the app. Without the app, or before Android verifies the site, the link opens in the
browser. The staging app does not take the links.

## Admin

Tournament screen > "QR & Tables IDs" (top bar) opens `TableQrScreen`:

- "Show in a separate window": a window of its own (a browser tab on the web, a window on the desktop, an activity on
  Android) with the code as big as it fits, black on white, the logo of the main organizer over the name of the
  tournament and one sentence, all centred, to project it (`TableQrProjectionScreen`, screen `TableQrProjection`). The
  window gets the name, the link and the logo address, so it loads nothing and needs no session;
- "Print the cards of the tables": 1, 2, 4, 6 or 8 cards per A4 sheet, the size of each card, a preview of the first
  sheet, and "Download PDF to print", which makes `GET /tournaments/{id}/table-qr-codes?perSheet=1|2|4|6|8` (permission `tournament.view`). Each card
  (the table ID) has the logo of the main organizer in the top left corner, the name of the tournament, the word
  "TABLE" and its number as big as the card allows on the left, and on the right one sentence and the link over the
  code; dashed lines show where to cut. Without a logo the corner shows a circle of the color of the organizer with its
  first letter, like the app. Every card is landscape: 1 = 297 x 210 mm, 2 = 210 x 148.5 mm, 4 = 148.5 x 105 mm,
  6 = 148.5 x 70 mm, 8 = 105 x 74.25 mm; the sheet is landscape for 1, 4 and 6 cards and portrait for 2 and 8. The cards are always in English, whatever the language of the app. Before the draw the tournament has no
  tables and the PDF is not offered. The export counts `table_qr_exported`.
