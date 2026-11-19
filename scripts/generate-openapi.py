#!/usr/bin/env python3
"""Writes docs/api/openapi.json, the OpenAPI description of the backend API (function `api`).

- The schemas come from the Kotlin DTOs of the apps (data/.../backend/dto/*.kt), so they are the shapes the apps
  really send and read. Run this script again after a DTO changes.
- The routes are listed by hand below (ROUTES). Add a route here when you add one in firebase/functions/src/api.
- Only the Python standard library is needed: python3 scripts/generate-openapi.py

See docs/arquitectura/api.md.
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
    "public": "🌐 Público: sin cuenta.",
    "auth": "🔑 Cualquier cuenta con sesión (token de Firebase).",
    "player": "🀄 Jugador: cuenta con sesión cuyo EMA id está en un hueco del torneo.",
    "editor": "✏️ Editor o admin (claim `editor` o `admin`).",
    "tEditor": "✏️ Admin, o editor asignado a este torneo (`tournaments/{id}/members/{uid}`).",
    "admin": "🛡️ Solo admin (claim `admin`).",
}
SECURITY = {"public": [], "auth": [{"bearer": []}], "player": [{"bearer": []}], "editor": [{"bearer": []}],
            "tEditor": [{"bearer": []}], "admin": [{"bearer": []}]}

T = {"name": "tournamentId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Id del torneo."}
R = {"name": "roundId", "in": "path", "required": True, "schema": {"type": "integer"}, "description": "Número de ronda (desde 1)."}
TB = {"name": "tableId", "in": "path", "required": True, "schema": {"type": "integer"}, "description": "Número de mesa en la ronda (desde 1)."}
P = {"name": "playerId", "in": "path", "required": True, "schema": {"type": "integer"}, "description": "Número de hueco (slot) del jugador en el torneo."}
UID = {"name": "uid", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Uid de Firebase Auth de la cuenta."}
EMA = {"name": "emaId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "EMA id (8 cifras)."}
ORG = {"name": "organizationId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Id de la organización."}
ROLE = {"name": "roleId", "in": "path", "required": True, "schema": {"type": "string"}, "description": "Id del rol a medida."}


def q(name, description, schema=None, required=False):
    return {"name": name, "in": "query", "required": required, "schema": schema or {"type": "string"}, "description": description}


PDF = {"application/pdf": {"schema": {"type": "string", "format": "binary"}}}
XLSX = {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": {"schema": {"type": "string", "format": "binary"}}}
OK = "OkResponse"

# (method, path, tag, access, summary, description, params, request schema, response schema or content, status)
ROUTES = [
    # ---- System
    ("get", "/health", "Sistema", "public", "Estado y versión del backend",
     "Responde `{ ok: true, version }`. Lo usa `python3 scripts/versions.py status`. No pasa por App Check.", [], None,
     {"type": "object", "properties": {"ok": {"type": "boolean"}, "version": {"type": "string", "example": "1.0.12"}}}),
    ("get", "/version", "Sistema", "public", "Página HTML de versiones",
     "Tabla con la versión de la web, del backend y de la app Android: la de `main` frente a la publicada. "
     "En el sitio también se sirve en `https://mts.mahjongmadrid.com/version`.", [], None,
     {"text/html": {"schema": {"type": "string"}}}),
    ("get", "/config", "Sistema", "public", "Interruptores de funciones (feature flags)", "", [], None, "FeatureFlags"),
    ("post", "/telemetry/errors", "Sistema", "public", "Enviar un informe de error de una app",
     "Público porque un fallo puede ocurrir antes de iniciar sesión. Límite: 30 por minuto y dirección. "
     "El servidor borra emails, números largos y tokens del texto. Responde 204 sin cuerpo.", [],
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
    ("post", "/support/message", "Sistema", "public", "Formulario «¿Necesitas ayuda?»",
     "Envía un email al buzón de soporte. Límite: 3 mensajes cada 15 minutos por dirección.", [], "SupportMessageRequest", OK),

    # ---- Auth
    ("post", "/auth/signIn", "Sesión", "public", "Iniciar sesión con email y contraseña",
     "El backend llama a Firebase Auth y devuelve el ID token (1 h) y el refresh token. Límite: 30 por minuto.", [],
     "SignInRequest", "SignInResponse"),
    ("post", "/auth/refresh", "Sesión", "public", "Renovar el ID token",
     "Las apps lo llaman al recibir `401 unauthenticated`.", [], "RefreshRequest", "RefreshResponse"),
    ("post", "/auth/passwordReset", "Sesión", "public", "Pedir el email de cambio de contraseña",
     "Firebase envía el email. Siempre responde `ok` para no revelar qué emails existen.", [], "PasswordResetRequest", OK),
    ("get", "/auth/me", "Sesión", "auth", "Mi perfil", "Alias, EMA id, foto y roles de la cuenta.", [], None, "UserProfile"),
    ("put", "/auth/me", "Sesión", "auth", "Cambiar mi alias", "1 a 60 caracteres.", [], "UpdateOwnAliasRequest", "UserProfile"),
    ("put", "/auth/me/photo", "Sesión", "auth", "Subir mi foto", "JPEG o PNG en Base64. Se guarda en Storage `userPhotos/{uid}`.", [],
     "UpdatePlayerPhotoRequest", "UserProfile"),
    ("delete", "/auth/me/photo", "Sesión", "auth", "Quitar mi foto", "", [], None, "UserProfile"),
    ("delete", "/auth/me", "Sesión", "auth", "Borrar mi cuenta",
     "Borra la cuenta y su perfil. El registro EMA y los resultados de los torneos se quedan.", [], None, OK),
    ("get", "/me", "Sesión", "auth", "Mis huecos en los torneos",
     "EMA id de la cuenta y, por cada torneo donde está, su hueco y su equipo. La app de jugador lo usa para marcar al jugador propio.",
     [], None, "MyTournamentsResponse"),

    # ---- Public
    ("get", "/public/tournaments", "Público", "public", "Lista de torneos públicos",
     "Torneos con `isPublic != false`. Caché CDN de 60 s.", [], None, "TournamentsResponse"),
    ("get", "/public/tournaments/{tournamentId}", "Público", "public", "Un torneo público",
     "404 si no existe o no es público. Caché 15 s en juego, 300 s terminado.", [T], None, "Tournament"),
    ("get", "/public/tournaments/{tournamentId}/sync/manifest", "Público", "public", "Revisiones de los datos de un torneo",
     "Las apps comparan revisiones para pedir solo lo que cambió.", [T], None, "DataVersionsManifest"),
    ("get", "/public/tournaments/{tournamentId}/players", "Público", "public", "Jugadores de un torneo",
     "Sin emails ni cuentas. Un jugador con `hiddenFromPublic` aparece como «#N».", [T], None, "TournamentPlayersResponse"),
    ("get", "/public/tournaments/{tournamentId}/teams", "Público", "public", "Equipos de un torneo", "", [T], None, "TournamentTeamsResponse"),
    ("get", "/public/tournaments/{tournamentId}/round-clocks", "Público", "public", "Relojes y control de las rondas",
     "Incluye `serverNowMs` para que la app ajuste su reloj.", [T], None, "RoundClocksResponse"),
    ("get", "/public/tournaments/{tournamentId}/rounds", "Público", "public", "Rondas", "", [T], None, "TournamentRoundsResponse"),
    ("get", "/public/tournaments/{tournamentId}/tables", "Público", "public", "Mesas (resumen)", "",
     [T, q("roundId", "Solo las mesas de esta ronda.", {"type": "integer"})], None, "TournamentTablesResponse"),
    ("get", "/public/tournaments/{tournamentId}/tables-with-hands", "Público", "public", "Todas las mesas con sus 16 manos", "",
     [T], None, "RoundTablesWithHandsResponse"),
    ("get", "/public/tournaments/{tournamentId}/rounds/{roundId}/tables-with-hands", "Público", "public",
     "Mesas de una ronda con sus manos", "", [T, R], None, "RoundTablesWithHandsResponse"),
    ("get", "/public/tournaments/{tournamentId}/tables/{roundId}/{tableId}", "Público", "public", "Una mesa con sus manos", "",
     [T, R, TB], None, "TableWithHandsResponse"),
    ("get", "/public/statistics/overview", "Público", "public", "Estadísticas globales", "Sumas de todos los torneos públicos.", [], None,
     "StatisticsOverview"),
    ("get", "/public/statistics/persons/{emaId}", "Público", "public", "Estadísticas de una persona", "", [EMA], None, "PersonStatistics"),
    ("get", "/public/statistics/persons/{emaId}/standings", "Público", "public", "Puestos de una persona en varios torneos", "",
     [EMA, q("tournaments", "Ids de torneo separados por comas.")], None, "StandingsResponse"),
    ("get", "/public/players", "Público", "public", "Registro EMA público", "Sin fotos. Caché 1 h.", [], None, "PlayersResponse"),
    ("get", "/public/countries", "Público", "public", "Países", "", [], None, "CountriesResponse"),
    ("get", "/public/sync/manifest", "Público", "public", "Revisiones globales (lista de torneos, registro…)", "", [], None,
     "DataVersionsManifest"),

    # ---- Player (hands entered by players at the table)
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/seats", "Jugador en mesa", "player", "Sentar a los jugadores",
     "Fija Este/Sur/Oeste/Norte. `expectedVersion` evita pisar cambios de otro dispositivo (409 si la mesa cambió).",
     [T, R, TB], "SeatsRequest", "SeatsResponse"),
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/hands/preview", "Jugador en mesa", "player",
     "Calcular una mano sin guardarla", "Devuelve cómo quedaría la mesa.", [T, R, TB], "HandRequest", "HandPreview"),
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/hands", "Jugador en mesa", "player", "Apuntar una mano",
     "Solo con la ronda abierta para manos (reloj iniciado o control `open`). 409 si la mesa cambió.",
     [T, R, TB], "HandRequest", "HandPreview"),
    ("post", "/player/tournaments/{tournamentId}/tables/{roundId}/{tableId}/penalties", "Jugador en mesa", "player",
     "Apuntar penalizaciones", "", [T, R, TB], "PenaltiesRequest", OK),

    # ---- Tournaments (management)
    ("get", "/tournaments", "Torneos", "auth", "Torneos que gestiono",
     "Admin: todos. Editor: los asignados. Cuenta solo jugador: lista vacía (usa `/public`).", [], None, "TournamentsResponse"),
    ("post", "/tournaments", "Torneos", "admin", "Crear un torneo",
     "La app genera rondas y mesas (sorteo) y las envía aquí; el backend valida y guarda. Sede y email de contacto obligatorios.",
     [], "CreateTournamentRequest", "Tournament"),
    ("put", "/tournaments/{tournamentId}", "Torneos", "admin", "Renombrar un torneo", "", [T], "RenameTournamentRequest", OK),
    ("put", "/tournaments/{tournamentId}/settings", "Torneos", "admin", "Editar los ajustes del torneo",
     "Nombre, fechas, sede, colores, logo, horarios de rondas, agenda, público/privado, duración de partida…", [T],
     "UpdateTournamentSettingsRequest", "Tournament"),
    ("delete", "/tournaments/{tournamentId}", "Torneos", "admin", "Borrar un torneo", "Borra el torneo y todas sus subcolecciones.", [T], None, OK),
    ("get", "/tournaments/{tournamentId}/sync/manifest", "Torneos", "tEditor", "Revisiones de los datos del torneo", "", [T], None,
     "DataVersionsManifest"),
    ("get", "/sync/manifest", "Torneos", "auth", "Revisiones globales", "", [], None, "DataVersionsManifest"),
    ("get", "/tournaments/{tournamentId}/integrity", "Torneos", "tEditor", "Comprobar la integridad del torneo",
     "Busca incoherencias (huecos, copias de manos, horarios…). Solo lee.", [T], None, {"type": "object"}),
    ("post", "/tournaments/{tournamentId}/complete-open-tables", "Torneos", "admin", "Completar mesas abiertas", "", [T],
     "CompleteTablesRequest", "CompleteTablesResponse"),

    # ---- Players & teams of a tournament
    ("get", "/tournaments/{tournamentId}/players", "Jugadores del torneo", "tEditor", "Huecos del torneo",
     "El email solo llega a admins; los editores reciben `hasAccount` y `hasEmail`.", [T], None, "TournamentPlayersResponse"),
    ("put", "/tournaments/{tournamentId}/players/{playerId}", "Jugadores del torneo", "tEditor", "Asignar un EMA id a un hueco",
     "`emaId` vacío o null deja el hueco libre.", [T, P], "AssignTournamentPlayerRequest", OK),
    ("put", "/tournaments/{tournamentId}/players/{playerId}/substitution", "Jugadores del torneo", "tEditor", "Marcar una sustitución",
     "`reason`: LATE, ILLNESS o DISQUALIFIED.", [T, P], "PlayerSubstitution", OK),
    ("delete", "/tournaments/{tournamentId}/players/{playerId}/substitution", "Jugadores del torneo", "tEditor", "Quitar la sustitución",
     "", [T, P], None, OK),
    ("get", "/tournaments/{tournamentId}/teams", "Jugadores del torneo", "tEditor", "Equipos", "", [T], None, "TournamentTeamsResponse"),
    ("put", "/tournaments/{tournamentId}/teams/{teamId}", "Jugadores del torneo", "tEditor", "Editar un equipo",
     "Nombre y EMA ids de sus miembros.",
     [T, {"name": "teamId", "in": "path", "required": True, "schema": {"type": "integer"}}], "UpdateTournamentTeamRequest", OK),
    ("post", "/tournaments/{tournamentId}/player-accounts/send", "Jugadores del torneo", "tEditor", "Invitar o recordar",
     "Por cada jugador: sin cuenta → crea la cuenta y envía el email de contraseña (`INVITED`); con cuenta → email "
     "recordatorio (`REMINDED`). Hasta 200.", [T], "PlayerAccountActionRequest", "PlayerAccountActionResponse"),
    ("put", "/tournaments/{tournamentId}/player-emails", "Jugadores del torneo", "tEditor", "Guardar emails pendientes de huecos",
     "Hasta 200. Resultado por jugador: SAVED, CLEARED o FAILED.", [T], "PlayerEmailsRequest", "PlayerEmailsResponse"),
    ("get", "/tournaments/{tournamentId}/player-import/export", "Jugadores del torneo", "tEditor", "Exportar jugadores a Excel",
     "El Excel va en Base64 dentro del JSON.", [T], None, "PlayerExport"),
    ("post", "/tournaments/{tournamentId}/player-import/preview", "Jugadores del torneo", "tEditor", "Previsualizar una importación",
     "Lee un CSV o Excel y dice qué cambiaría. No escribe nada.", [T], "PlayerImportRequest", "PlayerImportPreview"),
    ("post", "/tournaments/{tournamentId}/player-import/apply", "Jugadores del torneo", "tEditor", "Aplicar una importación",
     "Sustituye todos los jugadores y equipos. 409 `FILE_HAS_ERRORS` o `TOURNAMENT_STARTED`.", [T], "PlayerImportRequest",
     "PlayerImportResult"),

    # ---- Members (editors of a tournament)
    ("get", "/tournaments/{tournamentId}/members", "Editores del torneo", "tEditor", "Editores asignados", "", [T], None, "MembersResponse"),
    ("put", "/tournaments/{tournamentId}/members/{uid}", "Editores del torneo", "admin", "Asignar un editor", "", [T, UID],
     "UpsertMemberRequest", OK),
    ("delete", "/tournaments/{tournamentId}/members/{uid}", "Editores del torneo", "admin", "Quitar un editor", "", [T, UID], None, OK),
    ("get", "/tournaments/{tournamentId}/users/lookup", "Editores del torneo", "tEditor", "Buscar una cuenta por email", "",
     [T, q("email", "Email de la cuenta.", required=True)], None, "UserProfile"),

    # ---- Rounds & tables
    ("get", "/tournaments/{tournamentId}/rounds", "Rondas y mesas", "tEditor", "Rondas", "", [T], None, "TournamentRoundsResponse"),
    ("get", "/tournaments/{tournamentId}/round-clocks", "Rondas y mesas", "tEditor", "Relojes de las rondas", "", [T], None,
     "RoundClocksResponse"),
    ("post", "/tournaments/{tournamentId}/rounds/{roundId}/clock", "Rondas y mesas", "tEditor", "Manejar el reloj de una ronda",
     "`action`: start, pause, resume, reset, setRemaining o stop. `agoSeconds` permite acciones hechas sin conexión (hasta 10 min).",
     [T, R], "RoundClockActionRequest", "RoundClocksResponse"),
    ("post", "/tournaments/{tournamentId}/rounds/{roundId}/control", "Rondas y mesas", "admin", "Control manual de la ronda",
     "Abre o cierra manos y asientos (`auto`, `open`, `closed`), por ronda o por mesa; reabre o completa mesas.",
     [T, R], "RoundControlRequest", "RoundClocksResponse"),
    ("get", "/tournaments/{tournamentId}/tables", "Rondas y mesas", "tEditor", "Mesas (resumen)", "",
     [T, q("roundId", "Solo las mesas de esta ronda.", {"type": "integer"})], None, "TournamentTablesResponse"),
    ("get", "/tournaments/{tournamentId}/tables-with-hands", "Rondas y mesas", "tEditor", "Todas las mesas con manos", "", [T], None,
     "RoundTablesWithHandsResponse"),
    ("get", "/tournaments/{tournamentId}/rounds/{roundId}/tables-with-hands", "Rondas y mesas", "tEditor", "Mesas de una ronda con manos",
     "", [T, R], None, "RoundTablesWithHandsResponse"),
    ("get", "/tournaments/{tournamentId}/tables/{roundId}/{tableId}", "Rondas y mesas", "tEditor", "Una mesa con sus manos", "",
     [T, R, TB], None, "TableWithHandsResponse"),
    ("put", "/tournaments/{tournamentId}/tables/{roundId}/{tableId}/state", "Rondas y mesas", "tEditor", "Guardar una mesa (editor)",
     "Parche de la mesa y de sus manos en una transacción. `expectedVersion` debe ser la versión actual: si otro guardó antes, 409 "
     "con los datos del servidor. Actualiza estadísticas y registro de cambios en la misma transacción.",
     [T, R, TB], "SaveTableStateRequest", "TableWithHandsResponse"),
    ("post", "/tournaments/{tournamentId}/tables/{roundId}/{tableId}/reset", "Rondas y mesas", "admin", "Vaciar una mesa", "",
     [T, R, TB], None, "TableWithHandsResponse"),

    # ---- Exports
    ("get", "/tournaments/{tournamentId}/id-cards", "Exportaciones", "tEditor", "PDF de acreditaciones (ID cards)",
     "Rechaza torneos con huecos sin asignar.",
     [T, q("layout", "`sheet` para varias por hoja A4; sin valor, una por página."), q("cardsPerSheet", "1 a 40 (por defecto 10).", {"type": "integer"}),
      q("scalePercent", "Tamaño de la tarjeta.", {"type": "integer"}), q("landscape", "true/false"), q("doubleSided", "true/false"),
      q("cutMarks", "true/false")], None, PDF),
    ("post", "/tournaments/id-card-proof", "Exportaciones", "editor", "PDF de prueba de una acreditación", "", [], "IdCardProofRequest", PDF),
    ("post", "/tournaments/id-card-preview", "Exportaciones", "editor", "Vista previa de una acreditación", "Igual que id-card-proof.", [],
     "IdCardProofRequest", PDF),
    ("get", "/tournaments/{tournamentId}/id-list", "Exportaciones", "tEditor", "Excel con la lista de jugadores", "", [T], None, XLSX),
    ("post", "/tournaments/{tournamentId}/ema-report", "Exportaciones", "tEditor", "Excel de resultados para la EMA",
     "La app envía la clasificación final.", [T], "EmaReportRequest", XLSX),

    # ---- Statistics (signed in)
    ("get", "/tournaments/statistics/overview", "Estadísticas", "admin", "Estadísticas globales (todas)", "", [], None, "StatisticsOverview"),
    ("get", "/tournaments/statistics/me", "Estadísticas", "auth", "Mis estadísticas", "", [], None, "PersonStatistics"),
    ("get", "/tournaments/statistics/me/standings", "Estadísticas", "auth", "Mis puestos",
     "", [q("tournaments", "Ids de torneo separados por comas.")], None, "StandingsResponse"),
    ("get", "/tournaments/statistics/persons/{emaId}", "Estadísticas", "auth", "Estadísticas de una persona", "", [EMA], None,
     "PersonStatistics"),
    ("get", "/tournaments/statistics/persons/{emaId}/standings", "Estadísticas", "auth", "Puestos de una persona", "",
     [EMA, q("tournaments", "Ids de torneo separados por comas.")], None, "StandingsResponse"),

    # ---- EMA registry
    ("get", "/ema-player-registry", "Registro EMA", "auth", "Registro EMA completo", "", [], None, "PlayersResponse"),
    ("post", "/ema-player-registry", "Registro EMA", "admin", "Crear un jugador EMA", "", [], "CreatePlayerRequest", "Player"),
    ("put", "/ema-player-registry/{emaId}", "Registro EMA", "admin", "Editar un jugador EMA",
     "Incluye `hiddenFromPublic` (en público sale como «#N»).", [EMA], "UpdatePlayerRequest", OK),
    ("put", "/ema-player-registry/{emaId}/photo", "Registro EMA", "admin", "Foto de un jugador EMA", "Storage `playerPhotos/{emaId}`.",
     [EMA], "UpdatePlayerPhotoRequest", "Player"),
    ("get", "/countries", "Registro EMA", "auth", "Países", "", [], None, "CountriesResponse"),
    ("post", "/venues/resolve-link", "Registro EMA", "editor", "Resolver un enlace corto de Google Maps",
     "Sigue hasta 5 redirecciones de Google y devuelve el enlace completo y, si puede, nombre, dirección, ciudad, país y zona horaria.",
     [], "ResolveVenueLinkRequest", "ResolveVenueLinkResponse"),

    # ---- Admin
    ("get", "/admin/whoami", "Admin", "auth", "Quién soy", "`role` es la capacidad más alta; `roles` todas.", [], None, "WhoAmIResponse"),
    ("get", "/admin/users", "Admin", "editor", "Cuentas", "Admin: todas. Editor: la suya.", [], None, "ManagedUsersResponse"),
    ("post", "/admin/users", "Admin", "editor", "Crear una cuenta", "Responde 201.", [], "SaveManagedUserRequest", "ManagedUser", "201"),
    ("put", "/admin/users/{uid}", "Admin", "editor", "Editar una cuenta",
     "Un editor solo edita la suya. Un EMA id de otra cuenta da 409.", [UID], "SaveManagedUserRequest", "ManagedUser"),
    ("put", "/admin/users/{uid}/photo", "Admin", "admin", "Foto de una cuenta", "", [UID], "UpdatePlayerPhotoRequest", "ManagedUser"),
    ("delete", "/admin/users/{uid}/photo", "Admin", "admin", "Quitar la foto de una cuenta", "", [UID], None, "ManagedUser"),
    ("put", "/admin/users/{uid}/disabled", "Admin", "admin", "Desactivar o activar una cuenta", "", [UID], "SetUserDisabledRequest",
     "ManagedUser"),
    ("delete", "/admin/users/{uid}", "Admin", "admin", "Borrar una cuenta", "", [UID], None, OK),
    ("post", "/admin/users/delete", "Admin", "admin", "Borrar varias cuentas", "", [], "UserDeletesRequest", "UserDeletesResponse"),
    ("post", "/admin/users/password-resets", "Admin", "admin", "Enviar emails de contraseña", "Hasta 200 uids.", [],
     "PasswordResetsRequest", "PasswordResetsResponse"),
    ("get", "/admin/users/lookup", "Admin", "editor", "Buscar una cuenta", "",
     [q("identifier", "Email, uid o EMA id.", required=True)], None, "UserProfile"),
    ("get", "/admin/organizations", "Organizaciones", "editor", "Organizaciones",
     "Clubs y federaciones dueños de los torneos, con `canEdit` y `canDelete` para quien pregunta.", [], None, "OrganizationsResponse"),
    ("post", "/admin/organizations", "Organizaciones", "admin", "Crear una organización", "Solo superadministrador. Responde 201.", [],
     "SaveOrganizationRequest", "Organization", "201"),
    ("put", "/admin/organizations/{organizationId}", "Organizaciones", "admin", "Editar una organización",
     "Superadministrador, o administrador de club de esa organización. Logo JPEG o PNG de hasta 2 MB.", [ORG], "SaveOrganizationRequest",
     "Organization"),
    ("delete", "/admin/organizations/{organizationId}", "Organizaciones", "admin", "Borrar una organización",
     "Solo superadministrador, y solo si no organiza ningún torneo (409).", [ORG], None, OK),
    ("get", "/admin/roles", "Admin", "editor", "Roles", "Roles estándar y a medida, con lo que puede tener cada base.", [], None, "RolesResponse"),
    ("post", "/admin/roles", "Admin", "admin", "Crear un rol a medida", "Solo superadministrador.", [], "SaveRoleRequest", "CustomRole"),
    ("put", "/admin/roles/{roleId}", "Admin", "admin", "Editar un rol a medida", "Solo superadministrador.", [ROLE], "SaveRoleRequest", "CustomRole"),
    ("delete", "/admin/roles/{roleId}", "Admin", "admin", "Borrar un rol a medida", "Sus cuentas vuelven al rol estándar.", [ROLE], None,
     "DeleteRoleResponse"),
    ("put", "/admin/users/{uid}/permissions", "Admin", "admin", "Rol y permisos de una persona",
     "Superadministrador, o administrador de club para el personal de sus organizaciones (solo roles).", [UID], "SetUserPermissionsRequest",
     "ManagedUser"),
    ("get", "/admin/activity", "Admin", "admin", "Registro de actividad",
     "Cambios de mesas y acciones de gestión, del más nuevo al más viejo.",
     [q("limit", "Tamaño de página (máx. 50).", {"type": "integer"}), q("source", "all, tables u other."),
      q("tournamentId", "Filtrar por torneo."), q("uid", "Filtrar por cuenta."), q("origin", "admin, player o system."),
      q("from", "Desde (ms epoch).", {"type": "integer"}), q("to", "Hasta (ms epoch).", {"type": "integer"}),
      q("q", "Texto libre (sin mayúsculas ni acentos)."), q("cursor", "`nextCursor` de la página anterior.")],
     None, "ActivityPage"),
    ("get", "/admin/error-groups", "Admin", "admin", "Errores de las apps agrupados", "",
     [q("limit", "Máx. 200.", {"type": "integer"}), q("since", "Desde (ms epoch).", {"type": "integer"}),
      q("cursorAt", "Del `nextCursor`.", {"type": "integer"}), q("cursorId", "Del `nextCursor`.")], None, "ErrorGroupsResponse"),
    ("get", "/admin/error-groups/{id}", "Admin", "admin", "Un grupo de errores con sus informes", "",
     [{"name": "id", "in": "path", "required": True, "schema": {"type": "string"}}, q("limit", "Máx. 50.", {"type": "integer"}),
      q("cursorAt", "", {"type": "integer"}), q("cursorId", "")], None, "ErrorGroupDetail"),
]

ERRORS = {
    "400": "Petición no válida (`bad_request`). `details` dice qué campo falla.",
    "401": "Sin token, token caducado (`unauthenticated`) o App Check rechazado (`app_check_failed`, solo en modo enforce).",
    "403": "La cuenta no tiene permiso (`forbidden`).",
    "404": "No existe (`not_found`).",
    "409": "Conflicto (`conflict`): la mesa cambió en el servidor, el EMA id ya tiene dueño, el fichero tiene errores…",
    "429": "Demasiadas peticiones (`too_many_requests`).",
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
            "error": {"type": "string", "example": "conflict", "description": "Código estable del error."},
            "message": {"type": "string", "description": "Texto para personas (en inglés)."},
            "details": {"description": "Datos extra, p. ej. `{ code: \"FILE_HAS_ERRORS\" }` o el estado actual de la mesa."},
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
            operation["responses"]["204"] = {"description": "Hecho, sin cuerpo."}
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
                "La API del backend: una sola Cloud Function HTTP (`api`, Express, Node 22, europe-southwest1).\n\n"
                "- Las apps nunca leen ni escriben Firestore directamente (las reglas lo niegan todo): todo pasa por aquí.\n"
                "- **Autenticación:** cabecera `Authorization: Bearer <ID token de Firebase>`, obtenido con `POST /auth/signIn`.\n"
                "- **App Check:** las apps envían `X-Firebase-AppCheck`. Hoy en modo *monitor* (solo se registra).\n"
                "- **Errores:** siempre `{ error, message, details }` (ver el esquema `Error`).\n"
                "- **Límites:** 600 escrituras/min por dirección, 120 por cuenta; 600 lecturas/min por dirección en `/public`.\n\n"
                "Documentación con diagramas: `docs/arquitectura/` en el repositorio.\n\n"
                "En esta página «Try it out» solo está activo para GET: los cambios se hacen desde las apps."
            ),
        },
        "servers": [
            {"url": "https://mts.mahjongmadrid.com/api", "description": "Producción (web, vía Firebase Hosting)"},
            {"url": "https://europe-southwest1-mahjong-tournament-suite.cloudfunctions.net/api",
             "description": "Producción (Android y escritorio, directo a la función)"},
            {"url": "http://localhost:5055/api", "description": "Entorno de pruebas local (emuladores)"},
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
