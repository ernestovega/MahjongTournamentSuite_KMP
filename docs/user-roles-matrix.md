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
