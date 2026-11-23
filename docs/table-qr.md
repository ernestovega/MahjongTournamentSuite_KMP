# QR codes of the tables

Each physical table of a tournament has a QR code. A player who scans it with the phone camera opens that table in
the player app, in the round in play, with its hands and its results. Nobody needs an account: the tables are public.

## The link

- A tournament with a public link (`registration.slug`, see registrations.md): `https://mts.mahjongmadrid.com/<slug>/table/<n>`,
  for example `/6thmmc/table/5`.
- Any other tournament: `https://mts.mahjongmadrid.com/t/<tournament id>/table/<n>`.
- `mesa` works in place of `table`, for a link typed by hand.

The admin app builds the link with `Tournament.tableQrLink` (domain) and the web of the players `PublicSite.url`
(data): the address of the page on the web, production or the Firebase address of the project on Android. The backend
builds the same link with `tableQrLink` of `tableQrService.ts` and `PLAYER_APP_URL`, so staging prints staging links.

## Which round the player app opens

`registrationLinkOf` reads the link and `TableQrLinkPresenter` finds the round (`Tournament.tableQrRound`):

1. the round in play, or the round that starts within the seating time (the same round as the reminder of the own table);
2. else the last round that started (between rounds: the round that just ended);
3. else the last round with hands (rounds played without the Timer);
4. else round 1.

The app opens the tournament and the table over it, so Back shows the tournament. A tournament before its draw, or a
table number above the last table, opens only the tournament.

The Android app does not open these links yet: the camera opens them in the browser. App Links would need
`/.well-known/assetlinks.json` on the domain with the signing certificate of Google Play.

## Admin

Tournament screen > "Table QR" (top bar) opens `TableQrScreen`:

- a grid with the code of every table; a touch shows one code as big as the screen, with the previous and next table,
  to show it on a screen or a projector;
- "Download PDF to print" makes `GET /tournaments/{id}/table-qr-codes` (permission `tournament.view`): an A4 PDF with
  four cards per page (an A6 each, with dashed lines to cut). Each card has the name of the tournament on the color of
  the main organizer, the number of the table, the code, one sentence in the language of the app (Accept-Language)
  and the link. The export counts `table_qr_exported`.
