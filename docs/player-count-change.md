# Change the number of players of a tournament

Last minute withdrawals can leave a table short, and late people can fill one more. Before the first hand, the admin
app takes whole tables out of a tournament that has its draw, or adds them, and makes the draw of every round again.

## Where

- **Players** of the tournament: the top bar button **Number of players**.
- **Edit tournament > Data**: the button **Change the number of players**, below Players, Rounds and Tries. It is hidden
  while the registrations are open (the number of places is still free there).

Both need the right to configure the tournament (`tournament.configure`): a super admin, an admin, the club admin of an
organizer, or an editor of the tournament with that right. An account without it does not see the button.

## When

The change is possible while no table has data: no hand, no manual total, no completed table. A round clock that runs
with no hand does not stop it. When it is not possible, the button looks off and its screen explains why:

| Reason | Code of the server |
| --- | --- |
| A table has data (the tournament started) | `TOURNAMENT_STARTED` (409) |
| The tournament is finished | `TOURNAMENT_COMPLETED` (409) |
| The registrations are closed and the draw is not made yet | `DRAW_PENDING` (409) |

## The steps

The change has its own screen (`PlayerCountRoute`, `PlayerCountScreen.kt`), opened from Players and from Edit
tournament > Data. It survives a reload of the web, and a row of steps at the top says where the organizer is:
**Who leaves or comes**, **Review** and **Done**. The ⓘ next to the numbers explains the change in short.

1. **Who leaves or comes.** The top says the number now and after the change ("After" only shows when it fits in
   tables of 4).
   - **Who comes?** − and + add whole tables of 4, before the first hand, up to 400 players. The new slots are empty,
     numbered after the highest one, to assign afterwards in Players (or Teams). In a tournament with registrations,
     the new places can go to people of the waiting list, in its order, up to the number of new places.
   - **Who leaves?** The organizer marks the people who leave, and the screen counts them: "Marked: 7. Mark 1 more or
     unmark 3." In a team tournament, whole teams are marked. At least 16 players stay, so the list stops there.
   - Adding and removing never go together: tables can only be added with nobody marked, and people can only be
     marked with no table added. The screen says so where it applies.
   - A search field above each list (people, teams, waiting list) finds them by name, slot number or EMA number. It
     only hides lines: the people marked stay marked.
   - **Continue** only works when the choice fits in whole tables.
2. **Review.** The screen says what is deleted (the tables of every round, the people who leave, their
   registrations), what is added and what is kept (the other players with their EMA player, account and email, the
   rounds with their times and clocks). The button **How does the draw work?** explains the draw, as when a tournament
   is created. The red **Make the new draw** asks one last time, in a short red dialog, because it cannot be undone.
3. **Draw.** The app computes the new draw of every round, as at the creation (docs/draw.md: the first way, and the
   second one when the first takes too long; nobody meets twice; with teams, four
   teams at each table), and sends it. The server checks it again and saves it in one go. While it runs, the screen
   shows the tries and cannot be left. **Done** then says how many people got an email and who must be told by hand.

## What the server does

`POST /tournaments/{tournamentId}/player-count` (`firebase/functions/src/services/playerCountChange.ts`):

- The people who stay keep their data and their slot number, and teams that stay keep their team number and their
  slots (Ernesto, 22-nov): after a removal the numbers have gaps, and the new slots and teams take the numbers after
  the highest ones, never a gap. The app draws on 1..N as always and puts that draw on the real numbers
  (`onSlots` in `DrawTournamentScheduleUseCase.kt`: without history any renaming keeps every rule). A draw of an older
  app, on 1..N, is still accepted: then the people who stay take the lowest numbers in their order, as before.
- The removed slots and teams are deleted, and so are the old tables. The new tables are written with a higher version,
  so every phone and screen loads the new draw.
- With registrations: the people who leave lose their registration. The people of the waiting list who get a place
  are confirmed. A maximum of places, when there was one, becomes the new number.
- Every person affected gets an email (`playerCountEmails.ts`, in the language of the registration or of the
  organization, with the contact email as reply-to): the players who stay (new tables, and their player number when it
  changes), the people who leave, and the people of the waiting list who get a place. The answer of the server lists
  who got none (no address, or the send failed), and the app shows them by name so the organizer tells them.
- The activity log records `tournament.playerCount` with the old and new number, who left and who came, and how many
  people were told.
- Any failure while writing puts the old draw back.

Other codes: `BAD_PLAYER_COUNT` (not a multiple of 4, under 16 or over 400), `SAME_PLAYER_COUNT`, `BAD_REMOVED_SLOTS`,
`PARTIAL_TEAM`, `BAD_WAITING`, `NOT_WAITING`, `NO_REGISTRATIONS` and `BAD_DRAW`. The app shows each one with its own
text.

## Tests

- Backend: `playerCountChange.test.ts` (the plan of the change, with kept numbers and with the 1..N draws of older apps), `playerCountChange.integration.test.ts` (Firestore
  emulator: data kept, teams, registrations, rollback, blocked states), `api/playerCount.integration.test.ts` (route,
  permissions, activity) and the matrix of `rolePermissions.integration.test.ts`.
- App: `PlayerCountRulesTest` and `DrawOnSlotsTest` (domain), `PlayerCountPresenterTest`, `PlayerCountUiTest` (the whole screen, the count of the marked people, added tables, back, teams,
  waiting list, server errors, locked states, both entry points and permissions), `AdminActionTrackingTest`.
- Screenshots: `player-count-remove`, `player-count-search`, `player-count-add`, `player-count-confirm`,
  `player-count-working`, `player-count-done`, `player-count-locked` and `dialogs/player-count-sure`.
- Game day: `corners` creates its tournament with 32 players and takes a table out before the first hand
  (docs/scenarios.md).
