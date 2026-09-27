# Application users

## Scope

This application is for tournament management. The public viewer application is outside this repository.

The management application has two global roles:

- `EDITOR`
- `ADMIN`

There are no reader accounts and no tournament-specific roles.

## Editor

An editor can use only assigned tournaments.

An assigned editor can:

- view tournament data;
- assign EMA players to tournament slots;
- edit tables, hands, scores, and results;
- view EMA reports;
- list the tournament's assigned accounts;
- assign or remove other editors;
- create and edit `EDITOR` accounts;
- assign editor accounts only to tournaments that the editor can manage.

An editor cannot:

- create, delete, rename, or configure tournaments;
- create or modify an `ADMIN` account;
- disable an account;
- edit the shared EMA player registry.

## Admin

An admin has implicit access to every tournament.

An admin can:

- perform every editor operation;
- create, delete, rename, and configure tournaments;
- create and modify admins;
- disable and enable accounts;
- edit the shared EMA player registry.

The last enabled admin cannot be demoted or disabled. An admin cannot disable their own account.

## Firebase Authentication

Firebase Authentication stores the sign-in email and disabled state.

The `admin: true` custom claim identifies an admin. An account without this claim is an editor.

The application has no public sign-up endpoint. An authorized editor or admin creates each account.

The bootstrap endpoint promotes an existing Firebase Authentication account to the first admin. The account must have an email address.

```text
POST /admin/bootstrapAdmin
X-Bootstrap-Key: <value>

{ "uid": "<firebase-auth-uid>" }
```

The endpoint rejects the request when an enabled admin already exists.

## Firestore assignments

Editor assignments use this path:

```text
tournaments/{tournamentId}/members/{uid}
```

The document contains `uid`, `createdAt`, and `updatedAt`. It does not contain a role or permission.

Admin access is implicit. Admins do not need membership documents.

## User management API

Signed-in editors and admins can use these routes:

```text
GET /admin/users
POST /admin/users
PUT /admin/users/{uid}
GET /admin/users/lookup?identifier={email}
```

Only admins can disable or enable accounts:

```text
PUT /admin/users/{uid}/disabled
```

An editor receives only assignments for tournaments that the editor can manage. The backend preserves hidden assignments.

## Tournament member API

An assigned editor or any admin can use these routes:

```text
GET /tournaments/{tournamentId}/members
GET /tournaments/{tournamentId}/users/lookup?email={email}
PUT /tournaments/{tournamentId}/members/{uid}
DELETE /tournaments/{tournamentId}/members/{uid}
```

The member list includes assigned editors and all enabled admins. Admin rows are implicit and cannot be removed.

## Account removal policy

The application does not delete managed accounts. An admin disables an account and revokes its refresh tokens.

The profile, tournament history, and audit data remain available.

## Full access reset

The maintenance script `npm run reset:access` resets accounts and tournament assignments before the new role model starts.

Deploy the new backend before this reset. The new client requires the `role` field returned by the new `whoami` endpoint.

The script uses a dry run by default. Apply mode requires the project ID twice and creates a local metadata inventory before remote changes.

The reset performs these actions:

- keeps or creates one selected Firebase Authentication account;
- deletes every other Firebase Authentication account;
- deletes all user profiles and tournament membership documents;
- creates one clean profile with the global `ADMIN` role;
- leaves all tournament content and EMA registry data unchanged;
- verifies that one enabled admin and no tournament assignments remain.

Use [Firebase access reset](firebase-access-reset.md) for the commands, credential requirements, and inventory location.

Use [Firebase role rollout](firebase-role-rollout.md) for the deployment order and the `EDITOR` and `ADMIN` checks.

This reset is an exceptional maintenance task. Normal account removal still uses account disabling.
