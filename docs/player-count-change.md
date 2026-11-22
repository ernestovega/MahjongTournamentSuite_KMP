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
with no hand does not stop it. When it is not possible, the button looks off and its dialog explains why:

| Reason | Code of the server |
| --- | --- |
| A table has data (the tournament started) | `TOURNAMENT_STARTED` (409) |
| The tournament is finished | `TOURNAMENT_COMPLETED` (409) |
| The registrations are closed and the draw is not made yet | `DRAW_PENDING` (409) |

## The steps

1. **Choose.** The dialog opens on the current number. The − and + buttons move it by whole tables of 4, from 16 up to
   400.
   - A search field above each list (people, teams, waiting list) finds them by name, slot number or EMA number. It
     only hides lines: the people chosen stay chosen.
   - One table less or more: in an individual tournament the organizer chooses who leaves, 4 people for each table.
     In a team tournament, whole teams leave or come.
   - More tables: the new slots are empty, to assign afterwards in Players (or Teams). In a tournament with
     registrations, the new places can go to people of the waiting list, in its order, up to the number of new places.
2. **Confirm.** A red dialog says what is deleted (the tables of every round, the people who leave, their
   registrations), what is added and what is kept (the other players with their EMA player, account and email, the
   rounds with their times and clocks). The button **How does the draw work?** explains the draw, as when a tournament
   is created.
3. **Draw.** The app computes the new draw of every round, as at the creation (nobody meets twice; with teams, four
   teams at each table), and sends it. The server checks it again and saves it in one go.

## What the server does

`POST /tournaments/{tournamentId}/player-count` (`firebase/functions/src/services/playerCountChange.ts`):

- The people who stay keep their data and take the lowest slot numbers, in their order. Teams that stay take the
  lowest team numbers. Default names ("Player N", "Team N") follow the new number; real names stay.
- The removed slots and teams are deleted, and so are the old tables. The new tables are written with a higher version,
  so every phone and screen loads the new draw.
- With registrations: the people who leave lose their registration, with no email. The people of the waiting list who
  get a place are confirmed. A maximum of places, when there was one, becomes the new number.
- The activity log records `tournament.playerCount` with the old and new number, who left and who came. Nobody gets an
  email: the organizer tells the people.
- Any failure while writing puts the old draw back.

Other codes: `BAD_PLAYER_COUNT` (not a multiple of 4, under 16 or over 400), `SAME_PLAYER_COUNT`, `BAD_REMOVED_SLOTS`,
`PARTIAL_TEAM`, `BAD_WAITING`, `NOT_WAITING`, `NO_REGISTRATIONS` and `BAD_DRAW`. The app shows each one with its own
text.

## Tests

- Backend: `playerCountChange.test.ts` (the plan of the change), `playerCountChange.integration.test.ts` (Firestore
  emulator: data kept, teams, registrations, rollback, blocked states), `api/playerCount.integration.test.ts` (route,
  permissions, activity) and the matrix of `rolePermissions.integration.test.ts`.
- App: `PlayerCountRulesTest` (domain), `PlayerCountPresenterTest`, `PlayerCountUiTest` (the whole dialog, back, teams,
  waiting list, server errors, locked states, both entry points and permissions), `AdminActionTrackingTest`.
- Screenshots: `dialogs/player-count-remove`, `player-count-search`, `player-count-add`, `player-count-confirm` and `player-count-locked`.
- Game day: `corners` creates its tournament with 32 players and takes a table out before the first hand
  (docs/scenarios.md).
