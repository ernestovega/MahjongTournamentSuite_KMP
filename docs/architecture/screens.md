# Map of the screens (wireframe)

How to move around the apps and what each role sees. The pictures are the ones of the screenshot tests (`*/screenshots/*/phone-light.png`, sample data), so they update themselves when they are recorded again. There are desktop, tablet, dark and other language versions in the same folders. The full list of features is in [features.md](../features.md).

## How the apps fit together

```mermaid
flowchart LR
  subgraph Android["📱 Android app «MTS» (one app)"]
    AP["Player part<br/>(playerApp)"] -- "«Management» button<br/>(staff only)" --> AA["Management part<br/>(adminApp)"]
    AA -- "«Player view» button / back" --> AP
  end
  subgraph Web["🌐 mts.mahjongmadrid.com"]
    WP["/ → player web"] -- "after signing in,<br/>staff chooses" --> WA["/admin/ → management web"]
    WA -- "«Player view»" --> WP
  end
  D["🖥️ Desktop"] --> DA["management only"]
```

## Player app

It always starts at the list of tournaments. No account is needed to see anything; an account only marks «me» and lets you enter hands at your own table.

```mermaid
flowchart TD
  S([Start]) --> T["Tournaments<br/>In play · All · Statistics"]
  T -- "open a tournament / link /t/id" --> TT
  T -- "tournament with registrations /<br/>short link /slug" --> TP
  T -- "QR of the tournament<br/>/slug/my-table" --> M
  subgraph Tournament["Tournament (tabs)"]
    TT["Tables<br/>Me · Team · All"]
    TR["Ranking<br/>now or after a round · Share"]
    TP["Players"]
    TE["Teams<br/>(team tournaments)"]
    TS["Statistics"]
    TI["Info"]
  end
  TT -- "table" --> M["Table<br/>🌐 view · 🀄 enter hands"]
  TP -- "player" --> J["Player page<br/>Tables · Results · Statistics"]
  TR -- "player" --> J
  J -- "table" --> M
  T -- "Statistics tab" --> E["Statistics<br/>row of filters"]
  T -- "⋮ menu" --> R["Rules"] & SUP["Contact / Privacy"]
  R --> C["Combinations"] & P["Penalties"] & GB["Green Book (PDF)"]
  T -- "«Sign in»<br/>or «Sign in to enter hands»" --> L["Sign in"]
  L --> T
  M -. "«your table» reminder" .-> M
```

| Screen | Who | What it does | Picture |
|---|---|---|---|
| **Tournaments (no account)** | Everyone | Tabs In play · All · Statistics, with filters (status, country, year). Sign-in button. | <img src="../../playerApp/screenshots/tournaments-guest/phone-light.png" width="140" alt="Tournaments (no account)"> |
| **Tournaments (with an account)** | Player | The same, with the tournaments where you play at the top and the marks Playing / Played / Registered / Waiting list. | <img src="../../playerApp/screenshots/tournaments/phone-light.png" width="140" alt="Tournaments (with an account)"> |
| **Tournament › Tables** | Everyone | Me / Team / All. Tables per round with live results. | <img src="../../playerApp/screenshots/tournament-tables/phone-light.png" width="140" alt="Tournament › Tables"> |
| **Tournament › Ranking** | Everyone | Players, teams, chicken hands and best hand. A round button shows the ranking after a past round, and Share makes an image. | <img src="../../playerApp/screenshots/tournament-ranking/phone-light.png" width="140" alt="Tournament › Ranking"> |
| **Tournament › Players** | Everyone | Search; opens the player page. | <img src="../../playerApp/screenshots/tournament-players/phone-light.png" width="140" alt="Tournament › Players"> |
| **Tournament › Players (registrations)** | Everyone sees · anyone registers | Before the draw there are only Players and Info. Places, waiting list, Register, My registration (food, team, cancel) and «I have no EMA number». Short link `/<slug>`, for example `/6thmmc` (docs/registrations.md). | <img src="../../playerApp/screenshots/tournament-registrations/phone-light.png" width="140" alt="Tournament › Players (registrations)"> |
| **Tournament › Teams** | Everyone | Team tournaments only. | <img src="../../playerApp/screenshots/tournament-teams/phone-light.png" width="140" alt="Tournament › Teams"> |
| **Tournament › Statistics** | Everyone | Totals and hands, per round. | <img src="../../playerApp/screenshots/tournament-statistics/phone-light.png" width="140" alt="Tournament › Statistics"> |
| **Tournament › Info** | Everyone | Progress, round times, venue, agenda, contact and organizers. | <img src="../../playerApp/screenshots/tournament-info/phone-light.png" width="140" alt="Tournament › Info"> |
| **Player page › Tables** | Everyone | The tables of the player in the tournament. | <img src="../../playerApp/screenshots/tournament-player-tables/phone-light.png" width="140" alt="Player page › Tables"> |
| **Player page › Results** | Everyone | Every round in one table, with a chart of the place after each round, shareable as an image. | <img src="../../playerApp/screenshots/tournament-player-summary/phone-light.png" width="140" alt="Player page › Results"> |
| **Player page › Statistics** | Everyone | Standing (with the chart of the place per round), skills and totals. | <img src="../../playerApp/screenshots/tournament-player-statistics/phone-light.png" width="140" alt="Player page › Statistics"> |
| **Table (in play)** | Everyone sees · the players of the table enter | Seats, points and hands. The players of the table enter hands, seats and penalties while the round is open. | <img src="../../playerApp/screenshots/table-in-play/phone-light.png" width="140" alt="Table (in play)"> |
| **Table (completed)** | Everyone | Final result and every hand. | <img src="../../playerApp/screenshots/table-completed/phone-light.png" width="140" alt="Table (completed)"> |
| **Statistics** | Everyone | One screen with a row of filters: player, rival, tournament, countries and years. | <img src="../../playerApp/screenshots/statistics-player/phone-light.png" width="140" alt="Statistics"> |
| **Combinations** | Everyone | The 81 MCR combinations with tile examples. | <img src="../../playerApp/screenshots/combinations/phone-light.png" width="140" alt="Combinations"> |
| **Penalties** | Everyone | Table of penalties. | <img src="../../playerApp/screenshots/penalties/phone-light.png" width="140" alt="Penalties"> |

## Management app

```mermaid
flowchart TD
  L["Sign in"] --> T["Tournaments"]
  T -- "tournament" --> E["Tournament<br/>round › table editor"]
  T -- "tournament before the draw" --> REG["Registrations"]
  E -- "Timer · Ranking · QR" --> W["Windows to project"]
  E -- "Round control" --> RC["Round control dialog"]
  E -- "More" --> MORE["Players · Tables IDs · Stats ·<br/>EMA report · Activity"]
  MORE --> PL["Players"]
  PL -- "top bar" --> TE["Teams · ID cards · ID list ·<br/>Invitations · Number of players"]
  T -- "top bar" --> U["Accounts · EMA registry ·<br/>Organizations · DEV menu"]
```

| Screen | Who | What it does | Picture |
|---|---|---|---|
| **Sign in** | Staff (Super Admin, Club Admin, editor) | On the web, a staff account chooses «Player view» or «Management». | <img src="../../adminApp/screenshots/sign-in/phone-light.png" width="140" alt="Sign in"> |
| **Tournaments** | Super Admin: all · Club Admin: those of its organizations · Editor: assigned | Create (Super Admin and Club Admin), edit, open. Top bar: accounts, EMA registry, organizations, clock; DEV menu (Super Admin). | <img src="../../adminApp/screenshots/tournaments/phone-light.png" width="140" alt="Tournaments"> |
| **Tournament (round editor)** | Staff of the tournament | Round › table: players, seats, points and the 16 hands. Top bar: Timer, Ranking, QR and Round control; Players, Tables IDs, Stats, EMA report and Activity in the More menu. | <img src="../../adminApp/screenshots/tournament/phone-light.png" width="140" alt="Tournament (round editor)"> |
| **Registrations** | Staff of the tournament | Before the draw: public link, places, waiting list, paid, food, the import of the Google Form (it replaces the list and marks the rows with problems), settings and Make the draw. | <img src="../../adminApp/screenshots/registrations/phone-light.png" width="140" alt="Registrations"> |
| **Tournament players** | Staff of the tournament | Slots, EMA numbers, import and export, substitutions, the tables per round of each player (folded). Top bar: Teams, ID cards, ID list, Invitations and Number of players. | <img src="../../adminApp/screenshots/players/phone-light.png" width="140" alt="Tournament players"> |
| **Number of players** | Who configures the tournament | Before the first hand: mark the people who leave or add tables, complete the teams, review and a new draw; those who stay keep their number (docs/player-count-change.md). | <img src="../../adminApp/screenshots/player-count-remove/phone-light.png" width="140" alt="Number of players"> |
| **QR and Tables IDs** | Admin or editor of the tournament | "QR" button of the top bar: a separate window with the QR of the tournament to project, with the organizer's logo or initial (it opens My tables in the Player, with the table in play or the next one). More › "Tables IDs": a dialog with a PDF of one card per table (big number + QR), 1, 2, 4, 6 or 8 per A4, with a preview (docs/table-qr.md). | <img src="../../adminApp/screenshots/dialogs/tables-ids/phone-light.png" width="140" alt="Tables IDs"> |
| **Teams** | Staff of the tournament | Name and members of each team. Opens from Players. | <img src="../../adminApp/screenshots/teams/phone-light.png" width="140" alt="Teams"> |
| **Ranking (big screen)** | Staff of the tournament | To project in the venue; it pages by itself. | <img src="../../adminApp/screenshots/ranking-standalone/phone-light.png" width="140" alt="Ranking (big screen)"> |
| **Round clock** | Staff of the tournament | Starts, pauses and sets the time of the round; opens the hands to the players. | <img src="../../adminApp/screenshots/timer/phone-light.png" width="140" alt="Round clock"> |
| **Statistics** | Staff | The same screen as the Player, with the same filters. | <img src="../../adminApp/screenshots/statistics/phone-light.png" width="140" alt="Statistics"> |
| **Accounts** | Staff: every player and the staff of its organizations · Super Admin: all | Create, edit, deactivate and delete (Super Admin), password email. The **Permissions** button of a staff account: custom role (also the Club Admin, in its organizations) and single boxes (Super Admin). | <img src="../../adminApp/screenshots/users/phone-light.png" width="140" alt="Accounts"> |
| **Roles** | Super Admin | Standard roles (read only, can be duplicated) and custom roles that start from Editor or Club Admin: name, permission boxes, how many accounts use it. Opens from Accounts. | <img src="../../adminApp/screenshots/roles/phone-light.png" width="140" alt="Roles"> |
| **Organizations** | Super Admin and Club Admin | Clubs and federations in a grid of cards, with logo, color and an optional club venue. | <img src="../../adminApp/screenshots/organizations/phone-light.png" width="140" alt="Organizations"> |
| **EMA registry** | Super Admin edits · Club Admin creates · Editor looks | EMA players, photos, hide from the public, delete EMA players. | <img src="../../adminApp/screenshots/player-base/phone-light.png" width="140" alt="EMA registry"> |
| **Activity** | Super Admin (the whole app) · Club Admin (its tournaments) | Who changed what and when, with filters. | <img src="../../adminApp/screenshots/activity/phone-light.png" width="140" alt="Activity"> |
| **App errors** | Super Admin | Errors of every app, grouped. | <img src="../../adminApp/screenshots/errors/phone-light.png" width="140" alt="App errors"> |
| **Design system** | Super Admin | Catalogue of colors and components (DEV menu). | <img src="../../adminApp/screenshots/design-system/phone-light.png" width="140" alt="Design system"> |

The dialogs (create or edit a tournament, invitations, import of players, round control, profile…) have their pictures in `adminApp/screenshots/dialogs/` and `playerApp/screenshots/dialogs/`.

## Which role sees what (summary)

| | No account | Player | Editor | Club Admin | Super Admin |
|---|:-:|:-:|:-:|:-:|:-:|
| See public tournaments, tables, rankings, statistics | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mark «me», «my tournaments» | | ✅ | ✅* | ✅* | ✅* |
| Enter hands and seats at your table (open round) | | ✅ | ✅* | ✅* | ✅* |
| Manage tournaments (players, tables, hands, clock, round control, exports) | | | Assigned | Its organizations | All |
| Create, delete and configure tournaments, reset a table, activity of the tournament | | | | Its organizations | All |
| Accounts: create players | | | ✅ | ✅ | ✅ |
| Accounts: editors and Club Admins | | | | Its organizations | ✅ |
| Deactivate or delete accounts, the email or EMA number of an account | | | | | ✅ |
| EMA registry: create / edit and delete | | | | Create | ✅ |
| DEV menu: activity of the app, errors, design system | | | | | ✅ |

\* when the account also has an EMA number. Custom roles change these boxes one by one. The detailed matrix is in [user-roles-matrix.md](../user-roles-matrix.md).
