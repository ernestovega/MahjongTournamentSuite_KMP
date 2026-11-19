# Application users

## Scope

This application is for tournament management. The public viewer application is outside this repository.

The management application has three staff roles, and the player application one:

- `EDITOR`: the tournaments assigned to it.
- `CLUB_ADMIN`: every tournament of its organizations (clubs or federations).
- `SUPER_ADMIN`: everything.
- `PLAYER`: the player app.

An account can have several roles. What each role may do is in [the permissions matrix](user-roles-matrix.md).

The roles restrict only the management app. The player app (and the player part of the Android app) reads the public
list of tournaments without the account (`/public/tournaments`), so every role sees every tournament there.

## Editor

An editor works in the tournaments assigned to it: players, tables, hands, results, the Timer and the round control. It
creates player accounts and sees every player account, but the email of a player only when the player plays in one of
its tournaments. It sees the editors and admins of the organizations of its tournaments.

An editor always has at least one tournament. When it loses the last one (a club admin takes it away, or the
tournament is deleted), the account becomes a player, or it is disabled when it has no EMA ID (`syncRoleClaims`).
That account keeps `users/{uid}.disabledReason: "NO_ROLE"`, and the account dialog says so. A country admin sees it
like a player account and brings it back: it gives it the editor role with a tournament of its countries, and saving
enables the account again. An account that a super admin disabled stays disabled: only a super admin enables it (any
enable or disable of a super admin clears `disabledReason`).

## Organizations

Clubs and federations own the tournaments (`organizations/{id}`: name, type `CLUB` or `FEDERATION`, country, color,
logo and, for a club, its premises). A federation works like a club. A tournament has one or more organizers in `tournaments/{id}.organizerIds`: the
first one is the main organizer, the others are co-organizers. The tournament has no logo or color of its own: it shows
the ones of its main organizer (ID cards, apps). Its country is only the place and the statistics.

Only a super admin creates and deletes organizations, and an organization that still organizes tournaments cannot be
deleted. A club admin edits the details of its own organizations.

**Who changes the organizers of a tournament that exists** (`tournaments.changeOrganizers`, `canChangeOrganizers` in
`accessService.ts`, `AdminStatus.canChangeOrganizersOf` in the app):

- A super admin, always.
- A club admin of the **main** organizer. It adds and removes co-organizers and can hand the main role to another
  organizer, but the new organizers must keep one of its organizations, so it never gives the tournament away by mistake.
- Not the club admins of a co-organizer, and not the editors: they see the organizers but do not change them.

**Premises of a club** (`organizations/{id}.place`: `mapUrl`, `name`, `address`, `city`). Optional: some clubs have
their own room, which is not always the venue of their tournaments. The form reads it from a Google Maps link with the
same tool as the venue of a tournament (`POST /venues/resolve-link`, Google Places): the link fills the name, the
address and the city. Without a link the user types them. A place with some field needs a link or an address. The link
never changes the country of the club. A federation has no premises. Players do not see the premises yet.

Screen: Tournaments → Organizations. API: `GET/POST /admin/organizations`,
`PUT/DELETE /admin/organizations/{id}`.

## Club Admin

A club admin manages every tournament that one of its organizations organizes, without an assignment: it creates them
(one of its organizations must be an organizer), configures, deletes (a past tournament with results only a super admin
deletes) and assigns their editors. It creates and changes editors and club admins of its organizations, and it never
takes away what an account has in other organizations: a club admin that is also a club admin elsewhere cannot be
demoted by it, only its own organizations change. It creates EMA players of any country.

A club admin does not disable or delete accounts. It removes an account from its organizations ("Remove from my
organizations"), and it can ask the super admins to disable or delete the whole account with a reason
(`POST /admin/users/{uid}/removal`). The super admins get an email and see the request in Accounts.

## Super Admin

A super admin has every permission: every tournament and account, the organizations and the organizers of a tournament,
the email and the EMA ID of an account, the photos, the EMA players, the requests of the club admins and the DEV menu.

The last enabled super admin cannot be demoted, disabled or deleted. A super admin cannot disable or delete their own
account in App users.

## Deleting an account

The privacy policy (`https://mts.mahjongmadrid.com/privacy/`, file `playerApp/src/wasmJsMain/resources/privacy/index.html`)
promises that a person can delete the own account. Deleting removes the sign-in, the profile (email, alias), the own
photo, the editor assignments and the link to the EMA id. The EMA player and the tournament results stay, and the slots
show "No account" again. It cannot be undone.

- Any account deletes itself with "Delete my account" in the profile dialog: `DELETE /auth/me`.
- A super admin deletes another account with "Delete account" in the account dialog of App users: `DELETE /admin/users/{uid}`.

## Firebase Authentication

Firebase Authentication stores the sign-in email and disabled state.

The custom claims `superAdmin`, `clubAdmin`, `editor` and `player` hold the roles. The API reads the roles and the
organizations from the account itself on each request (cached 30 seconds), not from the token, so a change works at once.
The organizations of a club admin are in `users/{uid}.adminOrganizations`. `npm run migrate:organizations` (dry run;
`migrate:organizations:apply` writes) gives each old tournament a placeholder organizer of its country and takes the old
`countryAdmin` claim away.

The application has no public sign-up endpoint. The API has no route to create the first super admin: set the
`superAdmin: true` custom claim with the Firebase Admin SDK. `npm run migrate:role-claims` turns the old `admin` claim
into `superAdmin` (dry run; `migrate:role-claims:apply` writes).

## Firestore assignments

Editor assignments use this path:

```text
tournaments/{tournamentId}/members/{uid}
```

The document contains `uid`, `createdAt`, and `updatedAt`. It does not contain a role or permission.

Club admins and super admins do not need membership documents.

## User management API

```text
GET /admin/users                       accounts.view
POST /admin/users                      accounts.createPlayer (staff roles need accounts.manageStaff)
PUT /admin/users/{uid}                 accounts.view (then the account rules)
POST /admin/users/{uid}/removal        accounts.requestRemoval (club admins)
GET /admin/account-requests            accounts.reviewRequests (super admins)
POST /admin/account-requests/{id}      accounts.reviewRequests
PUT /admin/users/{uid}/disabled        accounts.disable (super admins)
DELETE /admin/users/{uid}              accounts.delete (super admins)
PUT /admin/users/{uid}/permissions     accounts.manageStaff (single permissions need permissions.manage)
GET /admin/roles                       accounts.view
POST /admin/roles                      permissions.manage (super admins)
PUT /admin/roles/{roleId}              permissions.manage
DELETE /admin/roles/{roleId}           permissions.manage
```

Custom roles and the permissions of one person are described in
[user-roles-matrix.md](user-roles-matrix.md#custom-roles-and-the-permissions-of-one-person).

A viewer receives only the assignments of the tournaments that it manages. The backend keeps the hidden assignments and
the organizations that the viewer does not manage.

## Tournament member API

```text
GET /tournaments/{tournamentId}/members
PUT /tournaments/{tournamentId}/members/{uid}      tournament.members
DELETE /tournaments/{tournamentId}/members/{uid}   tournament.members
```

The member list includes the assigned editors, the club admins of the organizers of the tournament and all enabled super
admins. The last two are implicit and cannot be removed.

## Full access reset

The maintenance script `npm run reset:access` resets accounts and tournament assignments before the new role model starts.

Deploy the new backend before this reset. The new client requires the `role` field returned by the new `whoami` endpoint.

The script uses a dry run by default. Apply mode requires the project ID twice and creates a local metadata inventory before remote changes.

The reset performs these actions:

- keeps or creates one selected Firebase Authentication account;
- deletes every other Firebase Authentication account;
- deletes all user profiles and tournament membership documents;
- creates one clean profile with the global `SUPER_ADMIN` role;
- leaves all tournament content and EMA registry data unchanged;
- verifies that one enabled super admin and no tournament assignments remain.

Use [Firebase access reset](firebase-access-reset.md) for the commands, credential requirements, and inventory location.

Use [Firebase role rollout](firebase-role-rollout.md) for the deployment order and the role checks.

This reset is an exceptional maintenance task. Normal account removal still uses account disabling.
