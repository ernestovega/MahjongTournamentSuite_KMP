#!/usr/bin/env python3
"""Writes docs/api/openapi.json, the OpenAPI description of the backend API (function `api`).

- The schemas come from the Kotlin DTOs of the apps (data/.../backend/dto/*.kt), so they are the shapes the apps
  really send and read. Run this script again after a DTO changes.
- The routes are listed by hand below (ROUTES). Add a route here when you add one in firebase/functions/src/api.
- Only the Python standard library is needed: python3 scripts/generate-openapi.py

See docs/architecture/api.md.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DTO_DIR = ROOT / "data/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/data/backend/dto"
OUT = ROOT / "docs/api/openapi.json"
VERSIONS = ROOT / "gradle/libs.versions.toml"

# --------------------------------------------------------------------------------------------------------------------
# Schemas from the Kotlin DTOs
# --------------------------------------------------------------------------------------------------------------------

KOTLIN_TYPES = {
    "String": {"type": "string"},
    "Int": {"type": "integer", "format": "int32"},
    "Long": {"type": "integer", "format": "int64"},
    "Double": {"type": "number", "format": "double"},
    "Boolean": {"type": "boolean"},
}


def schema_name(kotlin: str) -> str:
    return kotlin[:-3] if kotlin.endswith("Dto") else kotlin


def split_top_level(text: str) -> list[str]:
    parts, depth, current = [], 0, ""
    for ch in text:
        if ch in "(<[":
            depth += 1
        elif ch in ")>]":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += ch
    if current.strip():
        parts.append(current)
    return parts


def type_schema(kotlin_type: str) -> dict:
    kotlin_type = kotlin_type.strip()
    nullable = kotlin_type.endswith("?")
    base = kotlin_type.rstrip("?")
    if base.startswith("List<"):
        schema = {"type": "array", "items": type_schema(base[5:-1])}
    elif base.startswith("Map<"):
        _, value = split_top_level(base[4:-1])
        schema = {"type": "object", "additionalProperties": type_schema(value)}
    elif base in KOTLIN_TYPES:
        schema = dict(KOTLIN_TYPES[base])
    else:
        schema = {"$ref": f"#/components/schemas/{schema_name(base)}"}
    if nullable:
        if "$ref" in schema:
            schema = {"anyOf": [schema, {"type": "null"}]}
        else:
            schema["type"] = [schema["type"], "null"]
    return schema


def read_dtos() -> dict:
    schemas: dict = {}
    for path in sorted(DTO_DIR.glob("*.kt")):
        text = path.read_text()
        # Remove comments, but keep the KDoc of a property as its description.
        for match in re.finditer(r"enum class (\w+)\s*\{([^}]*)\}", text):
            values = [v.strip() for v in match.group(2).replace("\n", ",").split(",") if v.strip()]
            schemas[schema_name(match.group(1))] = {"type": "string", "enum": values}
        for match in re.finditer(r"data class (\w+)\(\s*(.*?)\n\)", text, re.S):
            name, body = match.group(1), match.group(2)
            properties, required = {}, []
            doc = None
            for raw in body.split("\n"):
                line = raw.strip()
                if line.startswith("/**"):
                    doc = line.strip("/* ").rstrip("*/ ").strip()
                    continue
                if line.startswith("*") or line.startswith("//") or not line:
                    continue
                prop = re.match(r"val (\w+): ([^=]+?)(?:\s*=\s*(.+?))?,?$", line)
                if not prop:
                    continue
                prop_name, prop_type, default = prop.group(1), prop.group(2), prop.group(3)
                schema = type_schema(prop_type)
                if doc:
                    schema = {**schema, "description": doc}
                    doc = None
                properties[prop_name] = schema
                if default is None:
                    required.append(prop_name)
            schema = {"type": "object", "properties": properties}
            if required:
                schema["required"] = required
            schemas[schema_name(name)] = schema
    return schemas


# --------------------------------------------------------------------------------------------------------------------
# Routes
# --------------------------------------------------------------------------------------------------------------------

# Who may call a route. The text goes into the description of each route.
ACCESS = {
    "public": "🌐 Public: no account.",
    "auth": "🔑 Any signed-in account (Firebase token).",
    "player": "🀄 Player: a signed-in account whose EMA number is in a slot of the tournament.",
    "editor": "✏️ Staff: editor, Club Admin or Super Admin, with the named permission of the route.",
    "tEditor": "✏️ Admin, or an editor assigned to this tournament (`tournaments/{id}/members/{uid}`).",
    "admin": "🛡️ Super Admin, or the named permission of the route.",
}
SECURITY = {"public": [], "auth": [{"bearer": []}], "player": [{"bearer": []}], "editor": [{"bearer": []}],
            "tEditor": [{"bearer": []}], "admin": [{"bearer": []}]}

T = {"name": "tournamentId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Id of the tournament."}
R = {"name": "roundId", "in": "path", "required": True, "schema": {"type": "integer"}, "description": "Number of the round (from 1)."}
TB = {"name": "tableId", "in": "path", "required": True, "schema": {"type": "integer"}, "description": "Number of the table in the round (from 1)."}
P = {"name": "playerId", "in": "path", "required": True, "schema": {"type": "integer"}, "description": "Number of the slot of the player in the tournament."}
UID = {"name": "uid", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Firebase Auth uid of the account."}
EMA = {"name": "emaId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "EMA number (8 digits)."}
REG = {"name": "emaId", "in": "path", "required": True, "schema": {"type": "string"},
       "description": "EMA number (8 digits), or `row-<line>` for a row of an import without a valid EMA number."}
ORG = {"name": "organizationId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Id of the organization."}
ROLE = {"name": "roleId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Id of the custom role."}
GROUP = [
    {"name": "tournaments", "in": "query", "required": False, "schema": {"type": "string"}, "description": "Ids of tournaments, comma separated."},
    {"name": "tournamentCountry", "in": "query", "required": False, "schema": {"type": "string"}, "description": "Two-letter ISO code of the country of the tournaments."},
    {"name": "playerCountry", "in": "query", "required": False, "schema": {"type": "string"}, "description": "EMA country code of the players, or `none` for players without a country."},
    {"name": "from", "in": "query", "required": False, "schema": {"type": "string"}, "description": "ISO date: tournaments that start on or after it."},
    {"name": "to", "in": "query", "required": False, "schema": {"type": "string"}, "description": "ISO date: tournaments that start on or before it."},
]


def q(name, description, schema=None, required=False):
    return {"name": name, "in": "query", "required": required, "schema": schema or {"type": "string"}, "description": description}


PDF = {"application/pdf": {"schema": {"type": "string", "format": "binary"}}}
XLSX = {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": {"schema": {"type": "string", "format": "binary"}}}
OK = "OkResponse"

# (method, path, tag, access, summary, description, params, request schema, response schema or content, status)
ROUTES = [
    # ---- System
    ("get", "/health", "System", "public", "Status and version of the backend",
     "Answers `{ ok: true, version }`. `python3 scripts/versions.py status` uses it. It skips App Check.", [], None,
     {"type": "object", "properties": {"ok": {"type": "boolean"}, "version": {"type": "string", "example": "1.0.12"}}}),
    ("get", "/version", "System", "public", "HTML page of the versions",
     "A table with the version of the web, the backend and the Android app: the one of `main` next to the published one. "
     "The site also serves it at `https://mts.mahjongmadrid.com/version`.", [], None,
     {"text/html": {"schema": {"type": "string"}}}),
    ("get", "/config", "System", "public", "Feature flags", "", [], None, "FeatureFlags"),
    ("post", "/telemetry/errors", "System", "public", "Send an error report of an app",
     "Public because a failure can happen before signing in. Limit: 30 per minute and address. "
     "The server removes emails, long numbers and tokens from the text. Answers 204 without a body.", [],
     {"type": "object", "required": ["app", "platform", "kind", "errorType"], "properties": {
         "app": {"type": "string", "enum": ["admin", "player"]},
         "platform": {"type": "string", "enum": ["android", "web", "desktop"]},
         "kind": {"type": "string", "enum": ["crash", "handled"]},
         "env": {"type": "string", "enum": ["prod", "dev", "debug"]},
         "role": {"type": "string", "enum": ["admin", "editor", "player"]},
         "appVersion": {"type": "string"}, "errorType": {"type": "string"}, "area": {"type": "string"},
         "message": {"type": "string"}, "stackTrace": {"type": "string"}, "reason": {"type": "string"},
         "httpStatus": {"type": "integer"}, "language": {"type": "string"}, "reportId": {"type": "string"},
         "sessionId": {"type": "string"}}},
     None, "204"),
    ("post", "/support/message", "System", "public", "Contact form",
     "Sends an email to the support mailbox. Limit: 3 messages every 15 minutes per address.", [], "SupportMessageRequest", OK),

    # ---- Auth
    ("post", "/auth/signIn", "Session", "public", "Sign in with email and password",
     "The backend calls Firebase Auth and returns the ID token (1 h) and the refresh token. Limit: 30 per minute.", [],
     "SignInRequest", "SignInResponse"),
    ("post", "/auth/refresh", "Session", "public", "Refresh the ID token",
     "The apps call it when they get `401 unauthenticated`.", [], "RefreshRequest", "RefreshResponse"),
    ("post", "/auth/passwordReset", "Session", "public", "Ask for the password email",
     "Firebase sends the email. It always answers `ok`, so it never reveals which emails exist.", [], "PasswordResetRequest", OK),
    ("get", "/auth/me", "Session", "auth", "My profile", "Alias, EMA number, photo and roles of the account.", [], None, "UserProfile"),
    ("put", "/auth/me", "Session", "auth", "Change my alias", "1 to 60 characters.", [], "UpdateOwnAliasRequest", "UserProfile"),
    ("put", "/auth/me/photo", "Session", "auth", "Upload my photo", "JPEG or PNG in Base64. It is saved in Storage `userPhotos/{uid}`.", [],
     "UpdatePlayerPhotoRequest", "UserProfile"),
    ("delete", "/auth/me/photo", "Session", "auth", "Remove my photo", "", [], None, "UserProfile"),
    ("delete", "/auth/me", "Session", "auth", "Delete my account",
     "Deletes the account and its profile. The EMA player and the results of the tournaments stay.", [], None, OK),
    ("get", "/me", "Session", "auth", "My slots in the tournaments and my registrations",
     "EMA number of the account and, for each tournament where it is, its slot and its team. The player app uses it to mark its own player. "
     "It also gives the registrations of the person (with a place or on the waiting list) in the tournaments that wait for their draw.",
     [], None, "MyTournamentsResponse"),

    # ---- Public
    ("get", "/public/tournaments", "Public", "public", "List of public tournaments",
     "Tournaments with `isPublic != false`. CDN cache of 60 s.", [], None, "TournamentsResponse"),
    ("get", "/public/tournaments/{tournamentId}", "Public", "public", "One public tournament",
     "404 when it does not exist or is not public. Cache 15 s in play, 300 s finished.", [T], None, "Tournament"),
    ("get", "/public/tournaments/{tournamentId}/sync/manifest", "Public", "public", "Revisions of the data of a tournament",
     "The apps compare revisions to ask only for what changed.", [T], None, "DataVersionsManifest"),
    ("get", "/public/tournaments/{tournamentId}/players", "Public", "public", "Players of a tournament",
     "No emails and no accounts. A player with `hiddenFromPublic` shows as «#N».", [T], None, "TournamentPlayersResponse"),
    ("get", "/public/tournaments/{tournamentId}/teams", "Public", "public", "Teams of a tournament", "", [T], None, "TournamentTeamsResponse"),
    ("get", "/public/tournaments/{tournamentId}/round-clocks", "Public", "public", "Clocks and control of the rounds",
     "Includes `serverNowMs`, so the app corrects its clock.", [T], None, "RoundClocksResponse"),
    ("get", "/public/tournaments/{tournamentId}/rounds", "Public", "public", "Rounds", "", [T], None, "TournamentRoundsResponse"),
    ("get", "/public/tournaments/{tournamentId}/tables", "Public", "public", "Tables (summary)", "",
     [T, q("roundId", "Only the tables of this round.", {"type": "integer"})], None, "TournamentTablesResponse"),
    ("get", "/public/tournaments/{tournamentId}/tables-with-hands", "Public", "public", "Every table with its 16 hands", "",
     [T], None, "RoundTablesWithHandsResponse"),
    ("get", "/public/tournaments/{tournamentId}/rounds/{roundId}/tables-with-hands", "Public", "public",
     "Tables of a round with their hands", "", [T, R], None, "RoundTablesWithHandsResponse"),
    ("get", "/public/tournaments/{tournamentId}/tables/{roundId}/{tableId}", "Public", "public", "One table with its hands", "",
     [T, R, TB], None, "TableWithHandsResponse"),
    ("get", "/public/statistics/overview", "Public", "public", "Global statistics", "Sums of every public tournament.", [], None,
     "StatisticsOverview"),
    ("get", "/public/statistics/groups", "Public", "public", "Statistics of a group of tournaments and players",
     "The filters of the Statistics screen. Only public tournaments; the people hidden from the public are left out.",
     GROUP, None, "StatisticsGroup"),
    ("get", "/public/statistics/persons/{emaId}", "Public", "public", "Statistics of a person", "", [EMA], None, "PersonStatistics"),
    ("get", "/public/statistics/persons/{emaId}/standings", "Public", "public", "Places of a person in several tournaments", "",
     [EMA, q("tournaments", "Ids of tournaments, comma separated.")], None, "StandingsResponse"),
    ("get", "/public/players", "Public", "public", "Public EMA registry", "Number, name, country and photo. Cache 1 h.", [], None, "PlayersResponse"),
    ("get", "/public/countries", "Public", "public", "Countries", "", [], None, "CountriesResponse"),
    ("get", "/public/sync/manifest", "Public", "public", "Global revisions (list of tournaments, registry…)", "", [], None,
     "DataVersionsManifest"),

    # ---- Player (hands entered by players at the table)
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/seats", "Player at the table", "player", "Seat the players",
     "Sets East/South/West/North. `expectedVersion` avoids overwriting changes of another device (409 when the table changed).",
     [T, R, TB], "SeatsRequest", "SeatsResponse"),
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/hands/preview", "Player at the table", "player",
     "Compute a hand without saving it", "Returns how the table would be.", [T, R, TB], "HandRequest", "HandPreview"),
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/hands", "Player at the table", "player", "Enter a hand",
     "Only while the round is open for hands (clock started or control `open`). 409 when the table changed.",
     [T, R, TB], "HandRequest", "HandPreview"),
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/penalties", "Player at the table", "player",
     "Enter penalties", "", [T, R, TB], "PenaltiesRequest", OK),

    # ---- Tournaments (management)
    ("get", "/tournaments", "Tournaments", "auth", "Tournaments I manage",
     "Super Admin: all. Club Admin: those of its organizations. Editor: the assigned ones. A player account: an empty list (it uses `/public`).",
     [], None, "TournamentsResponse"),
    ("post", "/tournaments", "Tournaments", "admin", "Create a tournament",
     "The app makes the rounds and the tables (the draw) and sends them here; the backend checks and saves them. Venue and contact email are required. "
     "With open registrations the tournament starts without players and the draw comes at the close.",
     [], "CreateTournamentRequest", "Tournament"),
    ("put", "/tournaments/{tournamentId}", "Tournaments", "admin", "Rename a tournament", "", [T], "RenameTournamentRequest", OK),
    ("put", "/tournaments/{tournamentId}/settings", "Tournaments", "admin", "Edit the settings of the tournament",
     "Name, dates, venue, colors, logo, organizers, round times, agenda, public or private, match length…", [T],
     "UpdateTournamentSettingsRequest", "Tournament"),
    ("post", "/tournaments/{tournamentId}/player-count", "Tournaments", "tEditor", "Change the number of players",
     "Before the first hand: remove or add whole tables of 4. The app computes the new draw of every round; "
     "the server keeps the people who stay with their numbers, removes the chosen slots and can give the new slots to the waiting list. "
     "In a team tournament every team must end with 4 people (`PARTIAL_TEAM`), and people who stay can move to another team. "
     "It sends an email to each person affected and answers who did not get it. "
     "409 TOURNAMENT_STARTED when a table has data. "
     "See docs/player-count-change.md.", [T], "PlayerCountRequest", "PlayerCountResponse"),
    ("delete", "/tournaments/{tournamentId}", "Tournaments", "admin", "Delete a tournament", "Deletes the tournament and all its subcollections.", [T], None, OK),
    ("get", "/tournaments/{tournamentId}/sync/manifest", "Tournaments", "tEditor", "Revisions of the data of the tournament", "", [T], None,
     "DataVersionsManifest"),
    ("get", "/sync/manifest", "Tournaments", "auth", "Global revisions", "", [], None, "DataVersionsManifest"),
    ("get", "/tournaments/{tournamentId}/integrity", "Tournaments", "tEditor", "Check the integrity of the tournament",
     "Looks for inconsistencies (slots, copies of hands, times…). It only reads.", [T], None, {"type": "object"}),
    ("post", "/tournaments/{tournamentId}/complete-open-tables", "Tournaments", "admin", "Complete open tables", "", [T],
     "CompleteTablesRequest", "CompleteTablesResponse"),

    # ---- Players & teams of a tournament
    ("get", "/tournaments/{tournamentId}/players", "Tournament players", "tEditor", "Slots of the tournament",
     "The email only goes to admins; editors get `hasAccount` and `hasEmail`.", [T], None, "TournamentPlayersResponse"),
    ("put", "/tournaments/{tournamentId}/players/{playerId}", "Tournament players", "tEditor", "Give an EMA number to a slot",
     "An empty or null `emaId` leaves the slot free.", [T, P], "AssignTournamentPlayerRequest", OK),
    ("put", "/tournaments/{tournamentId}/players/{playerId}/substitution", "Tournament players", "tEditor", "Mark a substitution",
     "`reason`: LATE, ILLNESS or DISQUALIFIED.", [T, P], "PlayerSubstitution", OK),
    ("delete", "/tournaments/{tournamentId}/players/{playerId}/substitution", "Tournament players", "tEditor", "Remove the substitution",
     "", [T, P], None, OK),
    ("get", "/tournaments/{tournamentId}/teams", "Tournament players", "tEditor", "Teams", "", [T], None, "TournamentTeamsResponse"),
    ("put", "/tournaments/{tournamentId}/teams/{teamId}", "Tournament players", "tEditor", "Edit a team",
     "Name and EMA numbers of its members.",
     [T, {"name": "teamId", "in": "path", "required": True, "schema": {"type": "integer"}}], "UpdateTournamentTeamRequest", OK),
    ("post", "/tournaments/{tournamentId}/player-accounts/send", "Tournament players", "tEditor", "Invite or remind",
     "For each player: without an account → creates the account and sends the password email (`INVITED`); with an account → a "
     "reminder email (`REMINDED`). Up to 200.", [T], "PlayerAccountActionRequest", "PlayerAccountActionResponse"),
    ("put", "/tournaments/{tournamentId}/player-emails", "Tournament players", "tEditor", "Save pending emails of slots",
     "Up to 200. Result per player: SAVED, CLEARED or FAILED.", [T], "PlayerEmailsRequest", "PlayerEmailsResponse"),
    ("get", "/tournaments/{tournamentId}/player-import/export", "Tournament players", "tEditor", "Export the players to Excel",
     "The Excel file goes in Base64 inside the JSON.", [T], None, "PlayerExport"),
    ("post", "/tournaments/{tournamentId}/player-import/preview", "Tournament players", "tEditor", "Preview an import",
     "Reads a CSV or Excel file and says what would change. It writes nothing.", [T], "PlayerImportRequest", "PlayerImportPreview"),
    ("post", "/tournaments/{tournamentId}/player-import/apply", "Tournament players", "tEditor", "Apply an import",
     "Replaces every player and team. 409 `FILE_HAS_ERRORS` or `TOURNAMENT_STARTED`.", [T], "PlayerImportRequest",
     "PlayerImportResult"),

    # ---- Members (editors of a tournament)
    ("get", "/tournaments/{tournamentId}/members", "Tournament editors", "tEditor", "Assigned editors", "", [T], None, "MembersResponse"),
    ("put", "/tournaments/{tournamentId}/members/{uid}", "Tournament editors", "admin", "Assign an editor", "", [T, UID],
     "UpsertMemberRequest", OK),
    ("delete", "/tournaments/{tournamentId}/members/{uid}", "Tournament editors", "admin", "Remove an editor", "", [T, UID], None, OK),
    ("get", "/tournaments/{tournamentId}/users/lookup", "Tournament editors", "tEditor", "Find an account by email", "",
     [T, q("email", "Email of the account.", required=True)], None, "UserProfile"),

    # ---- Rounds & tables
    ("get", "/tournaments/{tournamentId}/rounds", "Rounds and tables", "tEditor", "Rounds", "", [T], None, "TournamentRoundsResponse"),
    ("get", "/tournaments/{tournamentId}/round-clocks", "Rounds and tables", "tEditor", "Clocks of the rounds", "", [T], None,
     "RoundClocksResponse"),
    ("post", "/tournaments/{tournamentId}/rounds/{roundId}/clock", "Rounds and tables", "tEditor", "Drive the clock of a round",
     "`action`: start, pause, resume, reset, setRemaining or stop. `agoSeconds` allows actions made without network (up to 10 min).",
     [T, R], "RoundClockActionRequest", "RoundClocksResponse"),
    ("post", "/tournaments/{tournamentId}/rounds/{roundId}/control", "Rounds and tables", "admin", "Manual control of the round",
     "Opens or closes hands and seats (`auto`, `open`, `closed`), per round or per table; reopens or completes tables. Permission `tournament.roundControl`.",
     [T, R], "RoundControlRequest", "RoundClocksResponse"),
    ("get", "/tournaments/{tournamentId}/tables", "Rounds and tables", "tEditor", "Tables (summary)", "",
     [T, q("roundId", "Only the tables of this round.", {"type": "integer"})], None, "TournamentTablesResponse"),
    ("get", "/tournaments/{tournamentId}/tables-with-hands", "Rounds and tables", "tEditor", "Every table with its hands", "", [T], None,
     "RoundTablesWithHandsResponse"),
    ("get", "/tournaments/{tournamentId}/rounds/{roundId}/tables-with-hands", "Rounds and tables", "tEditor", "Tables of a round with their hands",
     "", [T, R], None, "RoundTablesWithHandsResponse"),
    ("get", "/tournaments/{tournamentId}/tables/{roundId}/{tableId}", "Rounds and tables", "tEditor", "One table with its hands", "",
     [T, R, TB], None, "TableWithHandsResponse"),
    ("put", "/tournaments/{tournamentId}/tables/{roundId}/{tableId}/state", "Rounds and tables", "tEditor", "Save a table (editor)",
     "Patch of the table and its hands in one transaction. `expectedVersion` must be the current version: if someone else saved first, 409 "
     "with the data of the server. It updates the statistics and the change log in the same transaction.",
     [T, R, TB], "SaveTableStateRequest", "TableWithHandsResponse"),
    ("post", "/tournaments/{tournamentId}/tables/{roundId}/{tableId}/reset", "Rounds and tables", "admin", "Reset a table", "",
     [T, R, TB], None, "TableWithHandsResponse"),

    # ---- Exports
    ("get", "/tournaments/{tournamentId}/id-cards", "Exports", "tEditor", "PDF of the ID cards",
     "Refuses tournaments with slots without a person.",
     [T, q("layout", "`sheet` for several per A4 sheet; without a value, one per page."), q("cardsPerSheet", "1 to 40 (10 by default).", {"type": "integer"}),
      q("scalePercent", "Size of the card.", {"type": "integer"}), q("landscape", "true/false"), q("doubleSided", "true/false"),
      q("cutMarks", "true/false"), q("backs", "true/false. With false the PDF has only the front sides."),
      q("backSameAsFront", "true/false. The back of players and staff repeats the front; the substitutes keep the table."),
      q("substituteSheets", "Sheets of cards without name or number, for substitutes (0-20).", {"type": "integer"}),
      q("staffSheets", "Sheets of cards with STAFF in place of the number (0-20).", {"type": "integer"}),
      q("staffName", "Name of a staff card. Repeated once per name, in order (max. 200).")], None, PDF),
    ("get", "/tournaments/{tournamentId}/id-cards/staff-names", "Exports", "tEditor", "Saved staff names for the ID cards",
     "", [T], None, "IdCardStaffNames"),
    ("get", "/tournaments/{tournamentId}/id-cards/staff-candidates", "Exports", "tEditor",
     "Accounts of the app to choose the staff of the ID cards",
     "Only the name (EMA player or alias) and the role, never the email. First the staff of the tournament, then editors and admins, then players.",
     [T], None, "IdCardStaffCandidates"),
    ("put", "/tournaments/{tournamentId}/id-cards/staff-names", "Exports", "tEditor", "Save the staff names for the ID cards",
     "Removes the empty names. At most 200 names of 60 characters.", [T], "IdCardStaffNames", "IdCardStaffNames"),
    ("post", "/tournaments/id-card-proof", "Exports", "editor", "Proof PDF of one ID card", "", [], "IdCardProofRequest", PDF),
    ("post", "/tournaments/id-card-preview", "Exports", "editor", "Preview of one ID card", "The same as id-card-proof.", [],
     "IdCardProofRequest", PDF),
    ("get", "/tournaments/{tournamentId}/id-list", "Exports", "tEditor", "Excel file with the list of players", "", [T], None, XLSX),
    ("get", "/tournaments/{tournamentId}/table-qr-codes", "Exports", "tEditor", "PDF with the cards of the tables (Tables IDs)",
     "One card per table with its number in big and the QR code of the tournament, the logo of the main organizer (or its initial) and "
     "«Or just open the app:» with the main site. `?perSheet=1|2|4|6|8` cards per A4 sheet (4 by default), always in English. "
     "Permission `tournament.view`. See docs/table-qr.md.", [T, q("perSheet", "1, 2, 4, 6 or 8.", {"type": "integer"})], None, PDF),
    ("post", "/tournaments/{tournamentId}/ema-report", "Exports", "tEditor", "Excel file of results for the EMA",
     "The app sends the final ranking.", [T], "EmaReportRequest", XLSX),

    # ---- Statistics (signed in)
    ("get", "/tournaments/statistics/overview", "Statistics", "admin", "Global statistics (all)", "", [], None, "StatisticsOverview"),
    ("get", "/tournaments/statistics/groups", "Statistics", "auth", "Statistics of a group of tournaments and players",
     "Like `/public/statistics/groups`, over the tournaments that the account can see.", GROUP, None, "StatisticsGroup"),
    ("get", "/tournaments/statistics/me", "Statistics", "auth", "My statistics", "", [], None, "PersonStatistics"),
    ("get", "/tournaments/statistics/me/standings", "Statistics", "auth", "My places",
     "", [q("tournaments", "Ids of tournaments, comma separated.")], None, "StandingsResponse"),
    ("get", "/tournaments/statistics/persons/{emaId}", "Statistics", "auth", "Statistics of a person", "", [EMA], None,
     "PersonStatistics"),
    ("get", "/tournaments/statistics/persons/{emaId}/standings", "Statistics", "auth", "Places of a person", "",
     [EMA, q("tournaments", "Ids of tournaments, comma separated.")], None, "StandingsResponse"),

    # ---- EMA registry
    ("get", "/ema-player-registry", "EMA registry", "auth", "Full EMA registry", "", [], None, "PlayersResponse"),
    ("post", "/ema-player-registry", "EMA registry", "admin", "Create an EMA player", "Super Admin or Club Admin.", [], "CreatePlayerRequest", "Player"),
    ("put", "/ema-player-registry/{emaId}", "EMA registry", "admin", "Edit an EMA player",
     "Includes `hiddenFromPublic` (in public it shows as «#N»).", [EMA], "UpdatePlayerRequest", OK),
    ("put", "/ema-player-registry/{emaId}/photo", "EMA registry", "admin", "Photo of an EMA player", "Storage `playerPhotos/{emaId}`.",
     [EMA], "UpdatePlayerPhotoRequest", "Player"),
    ("post", "/ema-player-registry/delete/preview", "EMA registry", "admin", "Before deleting EMA players",
     "Permission `ema.delete`. The tournaments where each chosen player has data: those players are deactivated, not deleted. It writes nothing.",
     [], "EmaPlayerDeletesRequest", "EmaPlayerDeletesPreviewResponse"),
    ("post", "/ema-player-registry/delete", "EMA registry", "admin", "Delete EMA players",
     "Permission `ema.delete`. Deletes the chosen EMA players, or deactivates those with data in a tournament. Their accounts stay, without the EMA number.",
     [], "EmaPlayerDeletesRequest", "EmaPlayerDeletesResponse"),
    ("get", "/countries", "EMA registry", "auth", "Countries", "", [], None, "CountriesResponse"),
    ("post", "/venues/resolve-link", "EMA registry", "editor", "Resolve a short Google Maps link",
     "Follows up to 5 redirects of Google and returns the full link and, when it can, name, address, city, country and time zone.",
     [], "ResolveVenueLinkRequest", "ResolveVenueLinkResponse"),

    # ---- Admin
    ("get", "/admin/whoami", "Admin", "auth", "Who am I", "`role` is the highest capacity; `roles` all of them.", [], None, "WhoAmIResponse"),
    ("get", "/admin/users", "Admin", "editor", "Accounts", "Super Admin: all. Club Admin: the players and the staff of its organizations. Editor: its own.",
     [], None, "ManagedUsersResponse"),
    ("post", "/admin/users", "Admin", "editor", "Create an account", "Answers 201.", [], "SaveManagedUserRequest", "ManagedUser", "201"),
    ("put", "/admin/users/{uid}", "Admin", "editor", "Edit an account",
     "An editor only edits its own. An EMA number of another account gives 409.", [UID], "SaveManagedUserRequest", "ManagedUser"),
    ("put", "/admin/users/{uid}/photo", "Admin", "admin", "Photo of an account", "", [UID], "UpdatePlayerPhotoRequest", "ManagedUser"),
    ("delete", "/admin/users/{uid}/photo", "Admin", "admin", "Remove the photo of an account", "", [UID], None, "ManagedUser"),
    ("put", "/admin/users/{uid}/disabled", "Admin", "admin", "Disable or enable an account", "", [UID], "SetUserDisabledRequest",
     "ManagedUser"),
    ("delete", "/admin/users/{uid}", "Admin", "admin", "Delete an account", "", [UID], None, OK),
    ("post", "/admin/users/delete", "Admin", "admin", "Delete several accounts", "", [], "UserDeletesRequest", "UserDeletesResponse"),
    ("post", "/admin/users/password-resets", "Admin", "admin", "Send password emails", "Up to 200 uids.", [],
     "PasswordResetsRequest", "PasswordResetsResponse"),
    ("post", "/admin/users/{uid}/removal", "Admin", "editor", "Take an account out of my organizations",
     "Permission `accounts.requestRemoval` (Club Admin). Removes the account from the organizations and tournaments in the scope of the caller. "
     "With `kind` DISABLE or DELETE it also asks the Super Admins to disable or delete the whole account. Never the own account or a Super Admin.",
     [UID], "AccountRemovalRequest", "AccountRemovalResponse"),
    ("get", "/admin/account-requests", "Admin", "admin", "Requests to disable or delete accounts",
     "Permission `accounts.reviewRequests`. The open requests of the Club Admins.", [], None, "AccountRequestsResponse"),
    ("post", "/admin/account-requests/{requestId}", "Admin", "admin", "Approve or reject a request",
     "Permission `accounts.reviewRequests`. `approve: true` disables or deletes the account at once.",
     [{"name": "requestId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Id of the request."}],
     "ResolveAccountRequest", "ResolveAccountRequestResponse"),
    ("put", "/admin/users/{uid}/permissions", "Admin", "editor", "Custom role and permissions of an account",
     "Permission `accounts.manageStaff`. `levels.EDITOR` and `levels.CLUB_ADMIN` carry `roleId` (null: standard role) and "
     "`permissions` (the whole list; null goes back to the role; without it the changes stay). Only with `permissions.manage` "
     "can single permissions be sent; a Club Admin only chooses roles with no more permissions than its own, for accounts of its "
     "organizations. Never the own account or a Super Admin.", [UID], "SetUserPermissionsRequest", "ManagedUser"),
    ("get", "/admin/roles", "Admin", "editor", "Standard and custom roles",
     "Permission `accounts.view`. Includes what each base can have (`grantable`), what every role keeps (`required`) "
     "and how many accounts use each custom role.", [], None, "RolesResponse"),
    ("post", "/admin/roles", "Admin", "admin", "Create a custom role",
     "Permission `permissions.manage`. Base EDITOR or CLUB_ADMIN; a unique name of 1 to 40 characters. Answers 201.",
     [], "SaveRoleRequest", "CustomRole", "201"),
    ("put", "/admin/roles/{roleId}", "Admin", "admin", "Change a custom role",
     "Permission `permissions.manage`. The base does not change. The change reaches the accounts that use it.",
     [ROLE], "SaveRoleRequest", "CustomRole"),
    ("delete", "/admin/roles/{roleId}", "Admin", "admin", "Delete a custom role",
     "Permission `permissions.manage`. Its accounts go back to the standard role; `accounts` says how many.",
     [ROLE], None, "DeleteRoleResponse"),
    ("get", "/admin/users/lookup", "Admin", "editor", "Find an account", "",
     [q("identifier", "Email, uid or EMA number.", required=True)], None, "UserProfile"),
    ("get", "/admin/organizations", "Organizations", "editor", "Organizations",
     "Clubs and federations that own the tournaments, with `canEdit` and `canDelete` for the caller.", [], None, "OrganizationsResponse"),
    ("post", "/admin/organizations", "Organizations", "admin", "Create an organization", "Super Admin only. Answers 201.", [],
     "SaveOrganizationRequest", "Organization", "201"),
    ("put", "/admin/organizations/{organizationId}", "Organizations", "admin", "Edit an organization",
     "Super Admin, or a Club Admin of that organization. Logo JPEG or PNG of up to 2 MB.", [ORG], "SaveOrganizationRequest",
     "Organization"),
    ("delete", "/admin/organizations/{organizationId}", "Organizations", "admin", "Delete an organization",
     "Super Admin only, and only when it organizes no tournament (409).", [ORG], None, OK),
    ("get", "/admin/activity", "Admin", "admin", "Activity log",
     "Changes of tables and management actions, newest first.",
     [q("limit", "Page size (max. 50).", {"type": "integer"}), q("source", "all, tables or other."),
      q("tournamentId", "Filter by tournament."), q("uid", "Filter by account."), q("origin", "admin, player or system."),
      q("from", "From (ms epoch).", {"type": "integer"}), q("to", "To (ms epoch).", {"type": "integer"}),
      q("q", "Free text (no capitals or accents)."), q("cursor", "`nextCursor` of the previous page.")],
     None, "ActivityPage"),
    ("get", "/admin/error-groups", "Admin", "admin", "Errors of the apps, grouped", "",
     [q("limit", "Max. 200.", {"type": "integer"}), q("since", "From (ms epoch).", {"type": "integer"}),
      q("cursorAt", "From `nextCursor`.", {"type": "integer"}), q("cursorId", "From `nextCursor`.")], None, "ErrorGroupsResponse"),
    ("get", "/admin/error-groups/{id}", "Admin", "admin", "One group of errors with its reports", "",
     [{"name": "id", "in": "path", "required": True, "schema": {"type": "string"}}, q("limit", "Max. 50.", {"type": "integer"}),
      q("cursorAt", "", {"type": "integer"}), q("cursorId", "")], None, "ErrorGroupDetail"),
    # ---- Registrations (docs/registrations.md). The person without an account sends the token of its emails in the
    # header x-registration-token; with an account, the session is enough.
    ("get", "/public/tournaments/{tournamentId}/registrations", "Registrations", "public", "Public list of the registered people",
     "Places and waiting list, without emails, food restrictions or registration dates.", [T], None, "PublicRegistrations"),
    ("get", "/public/registration-links/{slug}", "Registrations", "public", "Tournament of a public link",
     "The short link `/<slug>` (and the old `/inscripcion/<slug>`) opens this tournament.",
     [{"name": "slug", "in": "path", "required": True, "schema": {"type": "string"}}], None, "RegistrationLink"),
    ("post", "/registrations/{tournamentId}", "Registrations", "public", "Register",
     "Without an account: an email arrives with the link to confirm (status PENDING). With an account and an EMA number: a place or the waiting list at once.",
     [T], "RegisterRequest", "RegisterResult"),
    ("post", "/registrations/{tournamentId}/confirm", "Registrations", "public", "Confirm the email",
     "The link of the emails. It gives a place or the waiting list.", [T], "ConfirmRegistrationRequest", "OwnRegistration"),
    ("get", "/registrations/{tournamentId}/me", "Registrations", "public", "My registration", "404 when there is none.", [T], None, "OwnRegistration"),
    ("put", "/registrations/{tournamentId}/me", "Registrations", "public", "Change my food restrictions",
     "They need the consent.", [T], "UpdateOwnRegistrationRequest", "OwnRegistration"),
    ("delete", "/registrations/{tournamentId}/me", "Registrations", "public", "Cancel my registration",
     "The organization gets an email.", [T], None, OK),
    ("post", "/registrations/{tournamentId}/me/team", "Registrations", "public", "Create a team (captain)", "", [T],
     "RegistrationTeamNameRequest", "OwnRegistration"),
    ("put", "/registrations/{tournamentId}/me/team", "Registrations", "public", "Rename the team (captain)", "", [T],
     "RegistrationTeamNameRequest", "OwnRegistration"),
    ("delete", "/registrations/{tournamentId}/me/team", "Registrations", "public", "Leave the team", "", [T], None, "OwnRegistration"),
    ("post", "/registrations/{tournamentId}/me/team/members", "Registrations", "public", "Add a member (captain)",
     "A registered person without a team.", [T], "RegistrationTeamMemberRequest", "OwnRegistration"),
    ("delete", "/registrations/{tournamentId}/me/team/members/{emaId}", "Registrations", "public", "Remove a member (captain)", "",
     [T, EMA], None, "OwnRegistration"),
    ("post", "/registrations/{tournamentId}/no-ema", "Registrations", "public", "«I have no EMA number»",
     "Sends a message to the organization. Limit per address.", [T], "NoEmaNumberRequest", OK),
    ("get", "/tournaments/{tournamentId}/registrations", "Registrations", "tEditor", "Registrations (organization)",
     "With emails, payments and food restrictions, and the `problems` and `fileEmaId` of the rows of an import. Permission `tournament.playerEmails`.",
     [T], None, "AdminRegistrations"),
    ("post", "/tournaments/{tournamentId}/registrations", "Registrations", "tEditor", "Add a person", "", [T],
     "AdminAddRegistrationRequest", "AdminRegistration"),
    ("post", "/tournaments/{tournamentId}/registrations/import", "Registrations", "tEditor", "Import the Google Form",
     "CSV or Excel file of the answers. `apply: false` only checks the rows and says in `replaced` how many registrations the list has. "
     "`apply: true` REPLACES the list (registrations and teams, without emails); rows with problems (INVALID_EMA, DUPLICATE_EMA, "
     "EMA_NOT_FOUND, INVALID_EMAIL) are imported too, marked, and a row without a valid EMA number gets the id `row-<line>`.",
     [T], "RegistrationImportRequest", "RegistrationImport"),
    ("put", "/tournaments/{tournamentId}/registrations/{emaId}", "Registrations", "tEditor", "Change a registration",
     "Email (a new one clears INVALID_EMAIL), status (give a place or move to the waiting list), paid, team and restrictions. "
     "`newEmaId` moves a row of an import to the right EMA number and clears its EMA problems (409 ALREADY_REGISTERED when that number is in the list).",
     [T, REG], "AdminUpdateRegistrationRequest", "AdminRegistration"),
    ("delete", "/tournaments/{tournamentId}/registrations/{emaId}", "Registrations", "tEditor", "Remove a registration",
     "`notify=false` sends no email.", [T, REG, q("notify", "true or false.")], None, OK),
    ("put", "/tournaments/{tournamentId}/registration-settings", "Registrations", "tEditor", "Settings of the registrations",
     "Open or close, places, last day and link.", [T], "RegistrationSettingsRequest", "RegistrationSettings"),
    ("post", "/tournaments/{tournamentId}/draw", "Registrations", "tEditor", "Make the draw",
     "With the registrations closed: the app computes the rounds and the tables and places the confirmed people in the slots. "
     "409 with `details.code`: REGISTRATION_OPEN, BAD_PLAYER_COUNT, IMPORT_PROBLEMS (a person with a place has no valid EMA number yet; "
     "`details.emaIds`), INCOMPLETE_TEAMS, BAD_ASSIGNMENTS or DRAW_MADE.",
     [T], "MakeDrawRequest", "Tournament"),
]

ERRORS = {
    "400": "Request not valid (`bad_request`). `details` says which field fails.",
    "401": "No token, an expired token (`unauthenticated`) or App Check refused (`app_check_failed`, only in enforce mode).",
    "403": "The account has no permission (`forbidden`).",
    "404": "It does not exist (`not_found`).",
    "409": "Conflict (`conflict`): the table changed on the server, the EMA number has an owner, the file has errors, the draw is not ready… `details.code` says which.",
    "429": "Too many requests (`too_many_requests`).",
}


def content_of(spec) -> dict:
    if isinstance(spec, str):
        return {"application/json": {"schema": {"$ref": f"#/components/schemas/{spec}"}}}
    if isinstance(spec, dict) and any("/" in key for key in spec):
        return spec
    return {"application/json": {"schema": spec}}


def build() -> dict:
    schemas = read_dtos()
    schemas["Error"] = {
        "type": "object",
        "required": ["error", "message"],
        "properties": {
            "error": {"type": "string", "example": "conflict", "description": "Stable code of the error."},
            "message": {"type": "string", "description": "Text for people (in English)."},
            "details": {"description": "Extra data, such as `{ code: \"FILE_HAS_ERRORS\" }` or the current state of the table."},
        },
    }
    paths: dict = {}
    used_tags: list[str] = []
    for route in ROUTES:
        method, path, tag, access, summary, description, params, request, response = route[:9]
        status = route[9] if len(route) > 9 else "200"
        if tag not in used_tags:
            used_tags.append(tag)
        operation = {
            "tags": [tag],
            "summary": summary,
            "description": "\n\n".join(text for text in [ACCESS[access], description] if text),
            "security": SECURITY[access],
            "responses": {},
        }
        if params:
            operation["parameters"] = params
        if request is not None:
            operation["requestBody"] = {"required": True, "content": content_of(request)}
        if status == "204":
            operation["responses"]["204"] = {"description": "Done, without a body."}
        else:
            operation["responses"][status] = {"description": "OK", "content": content_of(response)}
        codes = ["400", "429"] if access == "public" else ["400", "401", "403", "404", "409", "429"]
        if path.startswith("/public/tournaments/{"):
            codes = ["404", "429"]
        for code in codes:
            operation["responses"][code] = {"$ref": f"#/components/responses/E{code}"}
        paths.setdefault(path, {})[method] = operation

    referenced = set(re.findall(r'#/components/schemas/(\w+)', json.dumps(paths)))
    missing = referenced - set(schemas)
    if missing:
        sys.exit(f"Schemas not found in the DTOs: {sorted(missing)}")

    backend_version = re.search(r'backend-versionName\s*=\s*"([^"]+)"', VERSIONS.read_text())
    return {
        "openapi": "3.1.0",
        "info": {
            "title": "Mahjong Tournament Suite API",
            "version": backend_version.group(1) if backend_version else "0",
            "description": (
                "The API of the backend: one HTTP Cloud Function (`api`, Express, Node 22, europe-southwest1).\n\n"
                "- The apps never read or write Firestore directly (the rules deny everything): everything goes through here.\n"
                "- **Authentication:** header `Authorization: Bearer <Firebase ID token>`, from `POST /auth/signIn`.\n"
                "- **App Check:** the apps send `X-Firebase-AppCheck`. Today in *monitor* mode (it is only logged).\n"
                "- **Errors:** always `{ error, message, details }` (see the `Error` schema).\n"
                "- **Limits:** 600 writes/min per address, 120 per account; 600 reads/min per address on `/public`.\n\n"
                "Documentation with diagrams: `docs/architecture/` in the repository.\n\n"
                "On this page «Try it out» only works for GET: changes are made from the apps."
            ),
        },
        "servers": [
            {"url": "https://mts.mahjongmadrid.com/api", "description": "Production (web, through Firebase Hosting)"},
            {"url": "https://europe-southwest1-mahjong-tournament-suite.cloudfunctions.net/api",
             "description": "Production (Android and desktop, straight to the function)"},
            {"url": "http://localhost:5055/api", "description": "Local test environment (emulators)"},
        ],
        "tags": [{"name": tag} for tag in used_tags],
        "paths": paths,
        "components": {
            "securitySchemes": {
                "bearer": {"type": "http", "scheme": "bearer", "bearerFormat": "Firebase ID token (JWT)"},
            },
            "responses": {
                f"E{code}": {"description": text, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}}
                for code, text in ERRORS.items()
            },
            "schemas": dict(sorted(schemas.items())),
        },
    }


def main() -> None:
    spec = build()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(spec, ensure_ascii=False, indent=1) + "\n")
    print(f"{OUT.relative_to(ROOT)}: {sum(len(v) for v in spec['paths'].values())} routes, {len(spec['components']['schemas'])} schemas")


if __name__ == "__main__":
    main()
