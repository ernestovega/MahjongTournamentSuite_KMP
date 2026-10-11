# Permissions matrix

The management application has two global roles: `EDITOR` and `ADMIN`.

An editor needs a membership document for each tournament. An admin has implicit access to every tournament.

| Feature or action | Assigned editor | Admin |
| --- | ---: | ---: |
| Sign in and reset password | Use | Use |
| View assigned tournaments | View | View all |
| View tournament content | View | View |
| Assign EMA players to tournament slots | Edit | Edit |
| Edit tables, hands, scores, and results | Edit | Edit |
| View and export EMA reports | Use | Use |
| List tournament accounts | Manage | Manage |
| Assign or remove tournament editors | Manage | Manage |
| Create an editor account | Manage | Manage |
| Edit an editor account | Manage | Manage |
| Assign an editor to accessible tournaments | Manage | Manage |
| Create or edit an admin account | — | Manage |
| Disable or enable an account | — | Manage |
| Create a tournament | — | Manage |
| Rename or configure a tournament | — | Manage |
| Delete a tournament | — | Manage |
| Edit the shared EMA player registry | — | Manage |
| Bootstrap the first admin | Setup only | Setup only |

There are no reader accounts. The public viewer application is outside this repository.

## Tests

- `firebase/functions/src/api/rolePermissions.integration.test.ts` runs the real API against the Auth and Firestore emulators. It checks every route for four callers: no token, an editor who is not assigned, an editor who is assigned, and an admin. It also checks the account rules (editors cannot create or change admins, nobody changes their own role, the last admin stays, disabled accounts are refused) and the bootstrap key.
- The table `MATRIX` in that file is written by hand. A test compares it with the guards of the real routes, so a new route fails until its access is listed.
- `composeApp/src/jvmTest/.../RoleGatingUiTest.kt` checks what each role sees on the tournament list, the EMA players screen and the accounts screen. `TournamentScreenUiTest` checks the reset button.
- Run them with `npm run test:integration` (needs JDK 21) and `./gradlew :composeApp:jvmTest`.

One behavior to know: the API remembers a successful token check and a successful membership check for 30 seconds per user. A user who is disabled or removed from a tournament can keep working for up to 30 seconds. A first call is always checked.
