# Permissions matrix

The management application has three staff roles: `EDITOR`, `COUNTRY_ADMIN` and `SUPER_ADMIN`. The player application
uses `PLAYER`. An account can have several roles. For example, an editor who also plays has `EDITOR` and `PLAYER`, and a
country admin of Spain who is an editor of one tournament in Sweden has `COUNTRY_ADMIN` and `EDITOR`.

- An **editor** works in the tournaments assigned to it (a membership document `tournaments/{id}/members/{uid}`). An
  editor always has at least one tournament: one that loses its last tournament becomes a player, or is disabled when it
  has no EMA ID.
- A **country admin** manages every tournament of its countries without an assignment. The countries are two-letter
  codes in `users/{uid}.adminCountries`, set by a super admin or by another country admin of the same countries.
- A **super admin** manages everything.

The server keeps the rules in one table of named permissions, `firebase/functions/src/models/permissions.ts`. Each route
asks for one permission (`requirePermission`) or for one permission inside the tournament (`requireTournamentPermission`).
The apps read the permissions from `GET /admin/whoami` and from the `permissions` of each tournament of the list, and the
enum `Permission` of `:domain` mirrors the names.

| Feature or action | Editor (its tournaments) | Country Admin (its countries) | Super Admin |
| --- | ---: | ---: | ---: |
| Sign in and reset password | Use | Use | Use |
| View tournaments | Assigned | Its countries + assigned | All |
| Assign EMA players, edit tables, hands, scores and results | Edit, until completed | Edit | Edit |
| Round control and the Timer | Use | Use | Use |
| Edit a completed tournament, reset a table | — | Use | Use |
| Tournament activity | — | View | View |
| Create a tournament | — | In its countries | Any country |
| Rename, configure or delete a tournament | — | Use (a past one with results: only a super admin) | Use |
| Change the country of a tournament | — | — | Use |
| See all player accounts (name, EMA ID) | View | View | View |
| See the email of a player | Plays in its tournaments | Plays in its tournaments | All |
| See editor and admin accounts | Of its countries | Of its countries | All |
| Create a player account | Use | Use | Use |
| Create or change editors and country admins | — | Of its countries, never below what they have elsewhere | Use |
| Create or change super admins | — | — | Use |
| Change the email or the EMA ID of an account | — | — | Use |
| Send password resets, change photos | — | Resets | Use |
| Remove an account from its countries, ask to disable or delete it | — | Use | — |
| Disable or delete an account, approve the requests | — | — | Use |
| Create EMA players (any country) | — | Use | Use |
| Edit, disable or delete EMA players | — | — | Use |
| Statistics of all tournaments, venues, ID cards | Use | Use | Use |
| DEV menu: activity of the app, errors, design system | — | — | Use |

A country admin that takes an account out of its countries removes its countries and its tournaments there. With a
request, the super admins get an email and see the request in Accounts, where they approve (the account is disabled or
deleted at once) or reject it. See `services/accountRequests.ts`.

## Custom roles and the permissions of one person

The four roles above are the standard ones and never change. On top of them (`services/customPermissions.ts`):

- A super admin creates **custom roles** in Accounts → Roles (`roles/{id}` in Firestore). A custom role builds on
  Editor or Country Admin. The base gives the scope (assigned tournaments or countries) and never changes. A standard
  role can be duplicated as the start of a custom one.
- An editor or a country admin can use a custom role instead of the standard one (`users/{uid}.customRoles`, one per
  level). A country admin chooses the role of the staff of its countries, only among roles that do not hold more than
  it holds, and only for accounts with nothing outside its countries.
- A super admin can also tick or untick **single permissions** for one person (`users/{uid}.permissionChanges`). Only
  the difference with the role is saved, so a later change of the role still reaches the person. "Save as a new role"
  turns those changes into a reusable role.
- Deleting a custom role puts its accounts back on the standard role.
- Some permissions never leave the super admins (`SUPER_ADMIN_ONLY`): changing the country of a tournament, deleting a
  finished tournament with results, super admin accounts, emails, EMA IDs, photos, disabling and deleting accounts,
  approving requests, the DEV menu and the roles themselves. An editor-based role never gets permissions that need a
  country, nor assigning editors (`GRANTABLE`). Every role keeps `tournament.view` (`REQUIRED`).
- What reaches the server is always cut to `GRANTABLE` and the API reads it at each request (30 s cache per server
  instance), so a change works without signing in again. Every change goes to the activity log.
- Tests: `firebase/functions/src/api/customPermissions.integration.test.ts`.

## Everyone, without an account

Anyone can read the public tournaments: players, teams, rounds, tables, hands, rankings and statistics, and the statistics of every person. Emails, accounts and photos stay private. See [public-data.md](public-data.md).

## Player

A player account has no access to the management routes. It can do the following in the tournaments where it is registered:

| Action | Player |
| --- | ---: |
| List the tournaments where the player is registered | View |
| View players, teams, rounds, tables, hands and rankings of those tournaments | View |
| See emails or accounts of other players | — |
| Write down the hands of a table where the player sits (hands mode), once the Timer started the round | Use |
| Set the seats of a table where the player sits, before its first hand or penalty | Use |
| Edit tables, results, penalties or players directly | — |
| Use any other tournament | — |


## Tests

- `firebase/functions/src/api/rolePermissions.integration.test.ts` runs the real API against the Auth and Firestore emulators. It checks every route for these callers: no token, an editor who is not assigned, an editor who is assigned, a country admin of the country of the tournament, a country admin of another country, a super admin, a player who is registered in the tournament and a player who is not. It also checks the account rules (only country admins of the same country change editors and country admins, nobody changes their own role, the last super admin stays, disabled accounts are refused) and the country rules.
- The table `MATRIX` in that file is written by hand with the permission of each route. A test compares it with the guards of the real routes, so a new route fails until its permission is listed.
- `adminApp/src/jvmTest/.../RoleGatingUiTest.kt` checks what each role sees on the tournament list, the EMA players screen and the accounts screen. `TournamentScreenUiTest` checks the reset button.
- Run them with `npm run test:integration` (needs JDK 21) and `./gradlew :adminApp:jvmTest`.

One behavior to know: the API remembers a successful token check and a successful membership check for 30 seconds per user. A user who is disabled or removed from a tournament can keep working for up to 30 seconds. A first call is always checked.
