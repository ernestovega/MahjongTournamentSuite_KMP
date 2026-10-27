# Test tournament scenarios

The script `firebase/functions/src/scripts/seedTestTournaments.ts` writes 13 tournaments with fixed ids that start with `test-`.
This page lists each scenario and the tournament that covers it. A scenario without a tournament is listed as a gap.

## Tournaments

| Id | Players | Mode | Purpose |
| --- | ---: | --- | --- |
| `test-min-16` | 16 | Individual | The smallest valid tournament. One round, finished. |
| `test-draws-penalties` | 16 | Individual | Draws, all draws and penalties. Finished. |
| `test-ties` | 16 | Individual | Ties inside tables and in the ranking. Finished. |
| `test-special-names` | 16 | Individual | EMA players whose names have accents or other alphabets. |
| `test-teams-partial` | 24 | Teams | Teams with 0 to 3 open slots, long and unicode team names. |
| `test-totals-only` | 16 | Teams | Tables with manual totals only, and a total that does not add up to zero. |
| `test-no-extras` | 20 | Individual | No best hands, no chicken hands, no venue, no schedule, no agenda. |
| `test-incomplete-states` | 16 | Teams | Every incomplete table state, and invalid hands. |
| `test-registration-open` | 24 | Teams | Registered, not started. All tables empty. Future dates. |
| `test-extremes` | 32 | Individual | Extreme scores and penalties, a very long name, long venue and agenda. |
| `test-large` | 64 | Teams | 16 tables per round. Last round mixes finished, running, seated and empty tables. |
| `test-last-table` | 24 | Individual | One table has 15 hands. Writing the last hand finishes the tournament. Broken logo address. Chicken hands off. |
| `test-live-ongoing` | 40 | Teams | Running tournament. Round 4 has every table state. Used for tables, conflicts, players and the player app. |

## Scenario matrix

| Scenario | Tournament |
| --- | --- |
| Individual tournament | `test-min-16` and others |
| Team tournament | `test-live-ongoing`, `test-large` |
| Minimum size (16 players) | `test-min-16` |
| Large size (64 players, 48 tables) | `test-large` |
| One round only | `test-min-16` |
| Many rounds (8) | `test-extremes` |
| Finished tournament | `test-min-16`, `test-draws-penalties`, `test-ties` |
| Running tournament | `test-live-ongoing` |
| Not started tournament | `test-registration-open` |
| One table left to finish | `test-last-table` |
| EMA players and open slots together | `test-teams-partial` |
| Unicode and very long names | `test-special-names`, `test-extremes` |
| Teams with missing players | `test-teams-partial` |
| Draw hands, all draws | `test-draws-penalties` |
| Penalties | `test-draws-penalties`, `test-extremes` |
| Ties in table and ranking | `test-ties` |
| Totals only, valid and invalid sum | `test-totals-only` |
| Empty, seated, partial, pending and invalid hands | `test-incomplete-states` |
| Best hands off and chicken hands off | `test-no-extras` |
| Best hands on and chicken hands off | `test-last-table` |
| No venue, no schedule, no agenda | `test-no-extras` |
| Logo address that does not load | `test-last-table` |
| Editor member of some tournaments | accounts `editor@e2e.test` and `outsider@e2e.test` |
| Player at a running table, seated, waiting, finished | accounts `player@`, `seated@`, `waiting@`, `done@` |
| Disabled account | `disabled@e2e.test` |
| Account that plays nowhere | `stranger@e2e.test` |

## Error cases that show a message

| Case | Message | Covered by |
| --- | --- | --- |
| Wrong password or unknown email | The email or password is incorrect. | `SignInTest` |
| Empty email or password | Enter your email and password. | `SignInTest` |
| Disabled account | This account is disabled. Ask an administrator for help. | `SignInTest` |
| Backend not reachable | Network request failed. | `SignInTest` |
| Reset password without email | Enter your email address first. | `SignInTest` |
| Player opens the account list | You do not have permission to perform this action. | `TournamentListTest` |
| Account with no EMA number opens statistics | Your account is not linked to a player yet. Ask the organizers to link your EMA number. | `PlayerStatisticsTest` |
| Another editor saved the same table | Table updated, with the lost values | `TableEditingTest` |

## Gaps

These cases have no emulator test yet.

- Entering a hand and then switching "Completed" on in the same edit. The switch stays off after a hand was typed. This needs a closer look. Switching "Completed" on without typing works and is tested.
- Photo upload for EMA players, ID card export and EMA report export. They need a file picker or a share window.
- Choosing a password with the password reset email. The local backend keeps the email in memory and sends no real message.
- Warnings that the table editor shows for invalid hands (chicken hand above the limit, loser without winner).
