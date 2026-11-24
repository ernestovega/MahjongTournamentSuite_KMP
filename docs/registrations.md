# Registrations

The registrations of a tournament replace the Google Form and its spreadsheet. A tournament with registrations is
created without players and without a draw. People register from the player app or from the public link. When the
registrations close, the organizer makes the draw with the confirmed people, with the same algorithm as the creation of
a tournament.

Decisions of the owner (19-nov-2026): the creation without players, the waiting list with promotions by hand, no
accounts from the registrations, private food restrictions with consent, optional teams that must be complete before
the draw, a public list without hidden people, a button to cancel, the public link of the tournament, the Players tab of
the player app, and the import of the old Google Form.

## Life of a tournament with registrations

1. **Creation** (admin, Create tournament > "Open registrations"). The organizer can set the number of places and the
   last day to register. The tournament has `drawPending: true` and `registration` (its settings), and no players,
   tables or rounds. The public link is the short name of the tournament at the root of the site, in English and as short as
   possible, for example `https://mts.mahjongmadrid.com/6thmmc` (owner, 22-nov-2026). It opens the registrations while
   the tournament waits for its draw, and the tournament itself after the draw. The old links
   `/inscripcion/<slug>` still work.
2. **Open.** People register. Each one gets a place (`CONFIRMED`) while there are places, and then goes to the
   numbered waiting list (`WAITING`). Without a limit of places everyone gets a place.
3. **Closed.** The organizer closes the registrations (Close registrations on the Registrations screen), or the hourly job closes them the day
   after the last day, in the time zone of the tournament. They can open again: nothing is lost.
4. **Draw** (admin, Registrations > Make the draw, only when closed). The app checks the people with a place: a
   multiple of 4, at least 16, and in a tournament with teams every team with 4 people. Then it computes the rounds
   and the tables on the device, puts the people in the slots in a random order (a registration team takes one team of
   the draw), and the server writes everything (`POST /tournaments/{id}/draw`). The tournament then works as any other.

## The person who registers

- **With an account that has an EMA number:** Register, and the place or the waiting list comes at once.
- **Without an account:** the person finds itself in the EMA registry, writes an email, and gets a link to confirm.
  Until it opens the link the registration is `PENDING`: nobody else sees it, and it is deleted after 48 hours.
  The link of the emails (`/registration?t=<tournament>&k=<token>`, the old `/inscripcion/gestionar?...` still works) confirms the email and keeps a token in
  the app (the web leaves it out of the address at once). With that token the person manages its registration without
  an account.
- **No EMA number:** "I have no EMA number" sends a message to the organization, which creates the EMA player in the
  admin app and registers the person. The registrations never create accounts or EMA players.
- **Food restrictions** (allergies, intolerances, diets) are private: only the organizers and the person see them.
  They can be health data, so the app asks for an explicit consent. They are stored in `personPrivate/{emaId}`, so they
  belong to the person and its account, and not to one tournament.
- **Cancel:** "Cancel my registration", with a confirmation. The organization gets an email.

## Teams

Only in a tournament with teams. A team has 4 people and a name. The team is optional when a person registers, but it
must be complete before the draw.

- A captain creates the team and adds up to 3 registered people without a team, by their EMA number. One person can
  thus register the whole team: the others only register.
- The captain renames the team or removes a member. Anyone can leave its team.
- One week before the last day (at 10:00 of the tournament), the people without a complete team get a reminder. If
  they do nothing, the organization chooses their team (admin, Change > Team).

## Public list

`GET /public/tournaments/{id}/registrations`: order, name, flag, EMA number, status, position, team and photo. No emails,
no food restrictions and no registration date. There are no hidden people in this list.

The photo (`photoUrl`) is the one of the account of the person, or else the one of its EMA player (both are public),
the same order as the profile of an account. `photosOf` (registrationsService.ts) keeps them 5 minutes in memory,
because every viewer reads the list every minute. The organizers' list has the same photo.

The Players tab of the player app (screenshots `tournament-registrations` and `tournament-registrations-team-missing`):

- A card with the rows Status, Places and Last day, each with a line that says what its value means, as in the
  Registrations screen of the organizers.
- A card with the own registration (place, waiting list, email to confirm) and its button, or "Do you want to play?"
  with Register and I have no EMA number.
- In a tournament with teams, a person with a place and no team sees a big red warning on top, with Set up my team.
- Each person has its photo after the position, and "EMA <number> · team" under the name.

## Organizers

Admins, club admins of the organizers, and editors assigned to the tournament (permission `tournament.playerEmails`)
see the screen and manage the people. Only who configures the tournament (`tournament.configure`: super admins and
club admins of the organizers, or a custom permission) changes the settings and makes the draw: an editor sees the
settings without Change or Close registrations, and no Make the draw (screenshot `registrations-editor`).

Admin app, Registrations (opens from the tournament while `drawPending`):

- Settings on the screen itself, no settings dialog: one card with a row per setting (title, value in words, buttons).
  Status says what it means ("Anyone with the link can register. Close the registrations to make the draw." / "Nobody
  can register now. The draw can be made.") and has Close or Open registrations; Make the draw shows in that row once
  they are closed. Places ("14 of 16 places taken", waiting list and emails not confirmed), Last day ("Until ...,
  included") and Link to register have Change, which turns the value into its field with Cancel and Save. The link
  also has Copy (a short message at the bottom, no dialog). On a phone each row has the title and icon buttons in one
  line and the value under it; wider screens show the text of every button.
- Add a person is beside the search (an icon button on a phone; alone when nobody has registered yet).
- One list, without a big card around it: search, a chip per list (one line that scrolls sideways on a phone) with its count (All, With a place, Waiting list,
  Email not confirmed) and how many with a place have paid. A wide screen shows a table (#, photo, player with flag
  and EMA number, email, team, food, paid, menu); a phone shows each person in three lines. Names and photos come from
  the EMA registry. Food restrictions are an icon with a tooltip, and a warning shows when two people share an email
  (one account cannot share an email).
- Each person: Change (email, team, food, paid), Give a place, Move to the waiting list, Remove (with or without an
  email to the person). The waiting list moves only by hand.
- Export CSV (top bar): the list as a CSV file with the columns of the import (Timestamp, Email, First name, Last
  name, EMA number, Country, Team, Food restrictions), plus Status and Paid, which the import leaves out. The app makes
  it from the list it shows (`RegistrationsCsv`), in the order places, waiting list, pending. It is the example of the
  import and goes back in as it is (it replaces the list of the tournament where it is imported). UTF-8
  with BOM and CRLF, so Excel opens it well.
- Import (top bar), of the Google Form or of an exported file (CSV or Excel, up to 500 rows): opens the file picker at
  once, then a review of the rows. The file REPLACES the list: the review warns how many registrations are there now,
  and the import removes them all, and their teams, without emails. Every row is imported, also the rows with
  problems (EMA number not valid, twice in the file or not in the EMA registry, email not valid): they keep their
  problems and the list marks them, with a "With problems" filter, so the organizer fixes them there. A row without a
  valid EMA number gets the id `row-<line>` and keeps the text of the file (`fileEmaId`); "Choose the right person" in
  its Edit gives it the EMA number, with the name and country of the registry. A new email clears INVALID_EMAIL. The
  draw refuses while a person with a place has a problem with the EMA number (IMPORT_PROBLEMS). The name and the
  country come from the EMA registry, not from the file. The import never sends emails.
- Emails to the organization (the contact email of the tournament): each cancellation, and a weekly report on Sunday at
  21:00 of the tournament, until the draw.

## Data

| Where | What |
| --- | --- |
| `tournaments/{id}` | `drawPending`, `registration` (status, maxPlayers, closesOn, slug, counters) |
| `tournaments/{id}/registrations/{emaId}` | status, position, email, team, paid, source, the token of its links, dates |
| `tournaments/{id}/registrationTeams/{teamId}` | name, captain |
| `tournaments/{id}/registrationLog` | the changes for the weekly report |
| `registrationSlugs/{slug}` | the tournament of a public link |

A new tournament from the app has no link name yet (the app sends an empty one). The API then makes the shortest free one:
the short name without spaces (`6thmmc`), then with the year of its start (`6thmmc2027`), then with `-2`, `-3`...
A link name cannot be a page of the site (`admin`, `api`, `api-docs`, `privacy`, `support`, `green-book`, `t`,
`registration`, `inscripcion`...): `RESERVED_SLUGS` in `registrationSettings.ts` (the player web has the same list in
`Registrations.kt`). A new top-level page of the site must go in both lists. The organizer can change
it later in the Public link tile of the Registrations screen.
| `personPrivate/{emaId}` | the food restrictions and their consent |

The scheduled function `registrationsHourly` deletes the expired pending registrations, closes on the last day, sends
the team reminders and the weekly report. Cloud Scheduler does not exist in Madrid, so the schedule runs in Belgium
(`europe-west1`) and only calls the function `registrationsHourRun` in Madrid, which does the work there because the
SMTP server accepts only some countries. That function checks the Google ID token of the call: only the service
account of the functions can run it (`registrationHourRelay.ts`).

## Code

- Backend: `firebase/functions/src/services/registration*.ts`, `api/routes/registrations.routes.ts` (the person),
  `tournaments.routes.ts` (organizers and draw), `public.routes.ts` (public list and links).
- Domain: `model/Registration.kt`, `RegistrationRepository`, `assignRegistrationsToDraw`.
- Admin: `RegistrationsScreen`, `RegistrationsPresenter`, the switch in `CreateTournamentScreen`.
- Player: `Registrations.kt` (Players tab, dialogs, links), `main.kt` of the web (`/<slug>`, `/registration`, the old `/inscripcion/...`).
- Analytics: the `registration` event with its actions, and the admin actions (docs/telemetry.md).

## Tests

- Export and import share `testdata/registrations-export.csv`: `RegistrationsCsvTest` checks that the export writes it,
  and `registrationImport.test.ts` that the import reads it.
- Backend: `registrationSettings.test.ts`, `registrationImport.test.ts`, `registrationEmails.test.ts`,
  `registrations.integration.test.ts` (the import that replaces the list, rows with problems and their fix, the draw
  refused with IMPORT_PROBLEMS)
  (emulators), and the routes in `rolePermissions.integration.test.ts`.
- Kotlin: `AssignRegistrationsToDrawTest` (domain), `DefaultRegistrationRepositoryTest` (data: problems, fileEmaId,
  replaced, newEmaId), `RegistrationsTabUiTest` (player), `RegistrationsCsvTest`, `RegistrationsPresenterTest` (the draw
  refuses by name: count, EMA problems of the import, incomplete teams) and `RegistrationsProblemsUiTest` (the rows with
  problems and the With problems filter) (admin)
  and the screenshots `registrations`, `registrations-list` and `registrations-editor` (admin), `tournament-registrations` and `tournaments-registration-guest` (player).
- Game day: the job "Registrations before the tournament" of every scenario runs the whole process in staging
  (`scripts/rehearsal/registrations.mjs`, docs/scenarios.md).
