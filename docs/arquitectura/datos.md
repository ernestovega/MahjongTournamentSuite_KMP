# Datos: Firestore y Storage

Solo la función `api` (y los scripts de mantenimiento de `firebase/functions/src/scripts`) leen y escriben aquí. Las reglas de Firestore y Storage niegan todo a las apps.

## Mapa de colecciones

```mermaid
flowchart TB
  subgraph raiz["Colecciones raíz"]
    users["users/{uid}<br/>alias, emaId, photoUrl,<br/>adminOrganizations"]
    organizations["organizations/{id}<br/>name, type (CLUB/FEDERATION),<br/>country, primaryColor, logoUrl"]
    emaIdIndex["emaIdIndex/{emaId}<br/>uid (quién es dueño del EMA id)"]
    registry["emaPlayerRegistry/{emaId}<br/>firstName, lastName, country,<br/>photoUrl, hiddenFromPublic"]
    countries["countries/{code}<br/>code, name"]
    tournaments["tournaments/{tournamentId}<br/>organizerIds (principal primero)"]
    stats["statsTables · statsPlayers ·<br/>statsTournaments · statsGlobal"]
    activity["activity/{id}<br/>acciones de gestión"]
    errors["errorGroups/{id}<br/>└ reports/{id}"]
    meta["_meta/dataVersions<br/>revisiones globales"]
    sync["emaPlayerRegistrySyncReports · …SyncState ·<br/>emaRankingTournamentResultIndex"]
  end

  subgraph torneo["Dentro de cada torneo"]
    members["members/{uid}<br/>editores asignados"]
    players["players/{slotId}<br/>hueco: assignedEmaId, team,<br/>email pendiente, substitution"]
    teams["teams/{teamId}<br/>name"]
    rounds["rounds/{roundId}"]
    tables["tables/{roundId}_{tableId}<br/>playerIds, asientos, puntos,<br/>isCompleted, version, handsCopy"]
    hands["hands/{1..16}<br/>ganador, perdedor, puntos,<br/>isChickenHand, penalizaciones"]
    changes["changes/{id}<br/>registro de cada guardado de mesa"]
    playerLog["playerLog/{id}<br/>cambios de persona en un hueco"]
    assignments["emaPlayerAssignments/{emaId}<br/>EMA id → hueco"]
  end

  tournaments --> members & players & teams & rounds & tables & changes & playerLog & assignments
  tables --> hands
  users -. emaId .-> registry
  emaIdIndex -. uid .-> users
  players -. assignedEmaId .-> registry
  members -. uid .-> users
  tournaments -. organizerIds .-> organizations
  users -. adminOrganizations .-> organizations
```

## Relaciones (modelo entidad-relación)

```mermaid
erDiagram
  AUTH_ACCOUNT ||--o| USER : "users/{uid}"
  USER |o--o| EMA_PLAYER : "emaId (único, vía emaIdIndex)"
  EMA_PLAYER ||--o{ SLOT : "assignedEmaId"
  TOURNAMENT ||--|{ SLOT : "players/{slotId}"
  TOURNAMENT ||--o{ TEAM : "teams/{teamId}"
  ORGANIZATION ||--o{ TOURNAMENT : "organizerIds (el primero es el principal)"
  ORGANIZATION ||--o{ USER : "adminOrganizations (Club Admin)"
  TEAM ||--|{ SLOT : "slot.team"
  TOURNAMENT ||--o{ MEMBER : "members/{uid}"
  USER ||--o{ MEMBER : "editor asignado"
  TOURNAMENT ||--|{ TABLE : "tables/{round}_{table}"
  TABLE }|--|{ SLOT : "playerIds (4)"
  TABLE ||--|{ HAND : "hands/1..16"
  TABLE ||--o{ CHANGE : "changes"
  TABLE ||--o| STATS_TABLE : "statsTables (si está completa)"
  EMA_PLAYER ||--o| STATS_PLAYER : "statsPlayers/{emaId}"

  AUTH_ACCOUNT {
    string uid
    string email
    bool claim_admin
    bool claim_editor
    bool claim_player
    bool disabled
  }
  USER {
    string alias
    string emaId
    string photoUrl
  }
  EMA_PLAYER {
    string emaId PK
    string firstName
    string lastName
    string country
    bool hiddenFromPublic
  }
  TOURNAMENT {
    string id PK
    string name
    bool isTeams
    int numPlayers
    int numRounds
    date eventStartDate
    date eventEndDate
    string venue
    bool isPublic
    bool isCompleted
    list roundSchedules
    list roundClocks
    list roundControls
  }
  SLOT {
    int id PK
    string assignedEmaId
    int team
    string email
    object substitution
  }
  TABLE {
    int roundId
    int tableId
    list playerIds
    string playerEastId
    bool isCompleted
    bool useTotalsOnly
    long version
    object handsCopy
  }
  HAND {
    int handId
    string playerWinnerId
    string playerLooserId
    string handScore
    bool isChickenHand
    bool isDone
  }
```

## Lo importante en pocas líneas

- **Cuenta ≠ jugador EMA.** Una cuenta de Firebase Auth (`users/{uid}`) puede tener un EMA id. El jugador EMA vive en `emaPlayerRegistry/{emaId}`, compartido por todos los torneos. `emaIdIndex/{emaId}` garantiza que un EMA id tiene como mucho una cuenta.
- **Los roles no están en Firestore**: son *custom claims* de la cuenta en Firebase Auth (`superAdmin`, `clubAdmin`, `editor`, `player`).
- **Las organizaciones (clubs y federaciones) son las dueñas de los torneos.** Un torneo tiene uno o varios organizadores (`organizerIds`); el primero es el principal y el torneo usa su logo y su color. Los Club Admin de cada organizador gestionan el torneo.
- **Un torneo tiene huecos (slots)** numerados del 1 al N (`players/{slotId}`). Cada hueco tiene un EMA id o está vacío. Una cuenta «juega» un torneo si su EMA id está en algún hueco: no se guarda ningún enlace cuenta↔torneo, se calcula.
- **Rondas y mesas las genera la app** (sorteo) al crear el torneo; el backend solo valida y guarda.
- **Cada mesa tiene 16 manos** (`hands/1..16`) y una copia de las 16 en el propio documento (`handsCopy`) para leer una mesa con una sola lectura.
- **`version` de la mesa** sube con cada guardado. Quien guarda envía la versión que tenía (`expectedVersion`); si no coincide, 409. Así dos dispositivos no se pisan.
- **Las estadísticas se calculan al guardar**, en la misma transacción: `statsTables` (resumen de una mesa completa), `statsPlayers` (sumas por persona), `statsTournaments` (clasificaciones), `statsGlobal/overview-{n}` (totales repartidos en trozos).
- **Auditoría:** `tournaments/{id}/changes` guarda cada guardado/reset/conflicto de mesa con quién y qué cambió; `activity` guarda el resto de acciones de gestión. Ver [activity-log.md](../activity-log.md).
- **Errores de las apps:** `errorGroups/{id}` agrupa errores iguales; `reports` guarda los últimos informes. Ver [telemetry.md](../telemetry.md).

## Storage

| Carpeta | Contenido | Quién la escribe |
|---|---|---|
| `playerPhotos/{emaId}.jpg` | Foto del jugador EMA | Admin, o el script de sincronización con la EMA |
| `userPhotos/{uid}.{ext}` | Foto propia de una cuenta | El usuario (`PUT /auth/me/photo`) o un admin |
| `tournamentLogos/{tournamentId}.{ext}` | Logo de la asociación (acreditaciones) | Admin, al crear o editar el torneo |

Las URLs públicas llevan un token de descarga; las fotos nunca salen en las rutas `/public`.

## Índices

`firebase/firestore.indexes.json`: índices de grupo de colecciones (`members.uid`, `players.assignedEmaId`) y los compuestos del registro de actividad. El emulador no los necesita, así que una consulta sin índice solo falla en producción.
