# Users system

## Purpose

The users system separates account control from tournament access.

- A global role controls account administration.
- A tournament role controls access to one tournament.
- Disabling an account blocks sign-in without deleting its records.

## Global roles

The app has two global roles.

| Role | Permissions |
| --- | --- |
| User | Access assigned tournaments. |
| Superadmin | Manage accounts, tournaments, the EMA player base, and all tournament assignments. |

Firebase Authentication stores the `superadmin` custom claim. The old global `admin` claim has no effect.

The last enabled superadmin cannot lose the role or become disabled. A user cannot disable their own account.

## Tournament roles

Each tournament stores one role for each assigned user.

| Role | Permissions |
| --- | --- |
| Reader | Read tournament data. |
| Editor | Read and edit tournament data. |
| Tournament Admin | Manage tournament data and tournament users. |

A Tournament Admin can:

- add a user as Reader or Editor;
- change another assignment to Reader or Editor;
- remove users and other Tournament Admins from the tournament.

Only a superadmin can grant the Tournament Admin role.

## Email and account creation

The add-user dialog requires the email twice. The app enables Save only when both values match.

Firebase Authentication uses the same email for sign-in and contact. After creation, Firebase sends a password-reset email.

## Main Users screen

Only superadmins can open the main Users screen.

- Select a user row to open the edit dialog.
- Set the global role to User or Superadmin.
- Set access for each tournament.
- Disable or enable the account from the edit dialog.

The list shows the email, global role, assigned tournaments, and account status.

## Tournament Users screen

Superadmins and the tournament's Tournament Admins can open this screen.

- Look up an account by email.
- Assign Reader or Editor.
- Change or remove existing tournament assignments.
- Grant Tournament Admin when the signed-in user is a superadmin.

## Firebase data

Firebase Authentication stores:

- the sign-in email;
- the disabled state;
- the `superadmin` custom claim.

Firestore stores the account profile at:

```text
users/{uid}
```

Tournament assignments use:

```text
tournaments/{tournamentId}/members/{uid}
```

Each membership document contains `uid`, `role`, `createdAt`, and `updatedAt`.

## API routes

Global user routes require a superadmin.

```text
GET /admin/users
POST /admin/users
PUT /admin/users/{uid}
PUT /admin/users/{uid}/disabled
GET /admin/users/lookup?identifier={email}
```

Tournament user routes require a superadmin or the tournament's Tournament Admin.

```text
GET /tournaments/{tournamentId}/members
GET /tournaments/{tournamentId}/users/lookup?email={email}
PUT /tournaments/{tournamentId}/members/{uid}
DELETE /tournaments/{tournamentId}/members/{uid}
```

The server rejects Tournament Admin grants from non-superadmins.

## Account removal policy

The app does not delete managed accounts. It disables Firebase Authentication and revokes refresh tokens.

The profile, tournament history, and audit data remain available.
