# Mapa de pantallas (wireframe)

Cómo se navega por las apps y qué ve cada rol. Las capturas son las de los tests de capturas (`*/screenshots/*/phone-light.png`, datos de ejemplo), así que se actualizan solas cuando se graban de nuevo. Hay versiones de escritorio, tablet y modo oscuro en las mismas carpetas.

## Cómo encajan las apps

```mermaid
flowchart LR
  subgraph Android["📱 App Android «MTS» (una sola app)"]
    AP["Parte jugador<br/>(playerApp)"] -- "botón «Gestión»<br/>(solo admin/editor)" --> AA["Parte gestión<br/>(adminApp)"]
    AA -- "botón «Vista jugador» / atrás" --> AP
  end
  subgraph Web["🌐 mts.mahjongmadrid.com"]
    WP["/ → web jugador"] -- "tras iniciar sesión,<br/>admin/editor elige" --> WA["/admin/ → web gestión"]
    WA -- "«Vista jugador»" --> WP
  end
  D["🖥️ Escritorio"] --> DA["solo gestión"]
```

## App de jugador

Empieza siempre en la lista de torneos. No hace falta cuenta para ver nada; la cuenta solo sirve para marcar «yo» y para apuntar manos en la propia mesa.

```mermaid
flowchart TD
  S([Arranque]) --> T["Torneos<br/>🌐 todos"]
  T -- "abrir torneo / enlace /t/id" --> TT
  T -- "torneo con inscripciones /<br/>enlace corto /slug" --> TP
  subgraph Torneo["Torneo (pestañas)"]
    TT["Mesas<br/>Yo · Equipo · Todas"]
    TR["Clasificación"]
    TP["Jugadores"]
    TE["Equipos<br/>(si es por equipos)"]
    TS["Estadísticas"]
    TI["Info"]
  end
  TT -- "mesa" --> M["Mesa<br/>🌐 ver · 🀄 apuntar manos"]
  TP -- "jugador" --> J["Ficha de jugador<br/>Mesas · Estadísticas"]
  TR -- "jugador" --> J
  J -- "mesa" --> M
  T -- "pestaña Estadísticas" --> E["Estadísticas globales<br/>Yo · Mis torneos · Torneo · Jugador"]
  T -- "menú" --> R["Reglas"] & SUP["Ayuda / soporte"]
  R --> C["Combinaciones"] & P["Penalizaciones"] & GB["Green Book (PDF)"]
  T -- "menú «Iniciar sesión»<br/>o «Inicia sesión para apuntar manos»" --> L["Iniciar sesión"]
  L --> T
  M -. "recordatorio «tu mesa»" .-> M
```

| Pantalla | Quién | Qué hace | Captura |
|---|---|---|---|
| **Torneos (sin cuenta)** | Todos | Lista de torneos públicos con filtros (estado, país, año). Botón de iniciar sesión. | <img src="../../playerApp/screenshots/tournaments-guest/phone-light.png" width="140" alt="Torneos (sin cuenta)"> |
| **Torneos (con cuenta)** | Jugador | Igual, más el filtro «Mis torneos». | <img src="../../playerApp/screenshots/tournaments/phone-light.png" width="140" alt="Torneos (con cuenta)"> |
| **Torneo › Mesas** | Todos | Pestañas Yo / Equipo / Todas. Mesas por ronda con resultados en vivo. | <img src="../../playerApp/screenshots/tournament-tables/phone-light.png" width="140" alt="Torneo › Mesas"> |
| **Torneo › Clasificación** | Todos | Jugadores, equipos, manos pollo y mejor mano. | <img src="../../playerApp/screenshots/tournament-ranking/phone-light.png" width="140" alt="Torneo › Clasificación"> |
| **Torneo › Jugadores** | Todos | Buscador; abre la ficha del jugador. | <img src="../../playerApp/screenshots/tournament-players/phone-light.png" width="140" alt="Torneo › Jugadores"> |
| **Torneo › Jugadores (inscripciones)** | Todos ver · cualquiera se inscribe | Antes del sorteo solo hay Jugadores e Info. Plazas, lista de espera, Inscribirme, Mi inscripción (comida, equipo, baja) y «No tengo número EMA». Enlace corto `/<slug>`, p. ej. `/6thmmc` (docs/registrations.md). | <img src="../../playerApp/screenshots/tournament-registrations/phone-light.png" width="140" alt="Torneo › Jugadores (inscripciones)"> |
| **Torneo › Equipos** | Todos | Solo en torneos por equipos. | <img src="../../playerApp/screenshots/tournament-teams/phone-light.png" width="140" alt="Torneo › Equipos"> |
| **Torneo › Estadísticas** | Todos | Totales y reparto de manos, por ronda. | <img src="../../playerApp/screenshots/tournament-statistics/phone-light.png" width="140" alt="Torneo › Estadísticas"> |
| **Torneo › Info** | Todos | Progreso, horario de rondas, sede, agenda y contacto. | <img src="../../playerApp/screenshots/tournament-info/phone-light.png" width="140" alt="Torneo › Info"> |
| **Ficha de jugador › Mesas** | Todos | Sus mesas en el torneo. | <img src="../../playerApp/screenshots/tournament-player-tables/phone-light.png" width="140" alt="Ficha de jugador › Mesas"> |
| **Ficha de jugador › Estadísticas** | Todos | Puesto, habilidades y totales. | <img src="../../playerApp/screenshots/tournament-player-statistics/phone-light.png" width="140" alt="Ficha de jugador › Estadísticas"> |
| **Mesa (en juego)** | Todos ver · Jugador de la mesa apunta | Asientos, puntos y manos. El jugador de la mesa apunta manos, asientos y penalizaciones cuando la ronda está abierta. | <img src="../../playerApp/screenshots/table-in-play/phone-light.png" width="140" alt="Mesa (en juego)"> |
| **Mesa (terminada)** | Todos | Resultado final y detalle de manos. | <img src="../../playerApp/screenshots/table-completed/phone-light.png" width="140" alt="Mesa (terminada)"> |
| **Estadísticas globales** | Todos | Pestañas Yo / Mis torneos / Torneo / Jugador. | <img src="../../playerApp/screenshots/statistics-player/phone-light.png" width="140" alt="Estadísticas globales"> |
| **Combinaciones** | Todos | Las 81 combinaciones MCR con ejemplos de fichas. | <img src="../../playerApp/screenshots/combinations/phone-light.png" width="140" alt="Combinaciones"> |
| **Penalizaciones** | Todos | Tabla de penalizaciones. | <img src="../../playerApp/screenshots/penalties/phone-light.png" width="140" alt="Penalizaciones"> |
| **Iniciar sesión** | Staff (super admin, admin de país, editor) | Web: si la cuenta es de staff elige «Vista jugador» o «Gestión». | <img src="../../adminApp/screenshots/sign-in/phone-light.png" width="140" alt="Iniciar sesión"> |
| **Torneos** | Super admin: todos · Admin de país: los de sus países · Editor: asignados | Crear (super admin y admin de país en sus países), editar, abrir. Barra superior: cuentas, registro EMA, reloj; menú DEV (super admin). | <img src="../../adminApp/screenshots/tournaments/phone-light.png" width="140" alt="Torneos"> |
| **Torneo (editor de rondas)** | Staff del torneo | Ronda › mesa: jugadores, asientos, puntos y las 16 manos. Menú: reloj, clasificación, estadísticas, exportar, control de ronda (también editores), actividad (admins). | <img src="../../adminApp/screenshots/tournament/phone-light.png" width="140" alt="Torneo (editor de rondas)"> |
| **Inscripciones** | Staff del torneo | Antes del sorteo: enlace público, plazas, lista de espera, pagado, comida, importar el Google Form, ajustes y Hacer el sorteo. | <img src="../../adminApp/screenshots/registrations/phone-light.png" width="140" alt="Inscripciones"> |
| **Mesas** | Staff del torneo | Todas las mesas con su estado y filtros. | <img src="../../adminApp/screenshots/tables/phone-light.png" width="140" alt="Mesas"> |
| **Jugadores del torneo** | Staff del torneo | Huecos, EMA ids, invitaciones, importación, sustituciones, tira de mesas por ronda. | <img src="../../adminApp/screenshots/players/phone-light.png" width="140" alt="Jugadores del torneo"> |
| **Número de jugadores** | Quien configura el torneo | Antes de la primera mano: marcar quién sale o añadir mesas, revisar y sorteo nuevo; quienes se quedan mantienen su número (docs/player-count-change.md). | <img src="../../adminApp/screenshots/player-count-remove/phone-light.png" width="140" alt="Número de jugadores"> |
| **QR e IDs de mesa** | Admin o editor del torneo | Botón "QR" de la topbar del torneo: ventana aparte con el QR del torneo para proyectarlo (abre en el Player las mesas del jugador con su mesa en juego o la próxima). Menú More > "Tables IDs": diálogo con PDF de una tarjeta por mesa (número grande + QR), 1, 2, 4, 6 u 8 por A4, con vista previa (docs/table-qr.md). | <img src="../../adminApp/screenshots/dialogs/tables-ids/narrow-light-es.png" width="140" alt="IDs de mesa"> |
| **Equipos** | Staff del torneo | Nombre y miembros de cada equipo. | <img src="../../adminApp/screenshots/teams/phone-light.png" width="140" alt="Equipos"> |
| **Clasificación (pantalla grande)** | Staff del torneo | Para proyectar en la sala; pasa páginas sola. | <img src="../../adminApp/screenshots/ranking-standalone/phone-light.png" width="140" alt="Clasificación (pantalla grande)"> |
| **Reloj de ronda** | Staff del torneo | Inicia, pausa y ajusta el tiempo de la ronda; abre las manos a los jugadores. | <img src="../../adminApp/screenshots/timer/phone-light.png" width="140" alt="Reloj de ronda"> |
| **Estadísticas** | Staff | Del torneo, de una persona o de todos los torneos. | <img src="../../adminApp/screenshots/statistics/phone-light.png" width="140" alt="Estadísticas"> |
| **Cuentas** | Staff: todos los jugadores y el staff de sus países · Super admin: todas | Crear (jugadores todos; editores y admins de país el admin de país), editar, quitar de mis países y solicitudes (admin de país), desactivar y borrar (super admin), email de contraseña. Botón **Permisos** de una cuenta de staff: rol a medida (también el Club Admin, en sus organizaciones) y casillas sueltas (super admin). | <img src="../../adminApp/screenshots/users/phone-light.png" width="140" alt="Cuentas"> |
| **Roles** | Super admin | Roles estándar (solo lectura, se pueden duplicar) y roles a medida que parten de Editor o Club Admin: nombre, casillas de permisos, cuántas cuentas lo usan. Se abre desde Cuentas. | <img src="../../adminApp/screenshots/roles/phone-light.png" width="140" alt="Roles"> |
| **Registro EMA** | Super admin edita · Admin de país crea · Editor consulta | Jugadores EMA, fotos, ocultar del público, borrar jugadores EMA. | <img src="../../adminApp/screenshots/player-base/phone-light.png" width="140" alt="Registro EMA"> |
| **Actividad** | Super admin (toda la app) · Admin de país (sus torneos) | Quién cambió qué y cuándo, con filtros. | <img src="../../adminApp/screenshots/activity/phone-light.png" width="140" alt="Actividad"> |
| **Errores de las apps** | Super admin | Errores agrupados de todas las apps. | <img src="../../adminApp/screenshots/errors/phone-light.png" width="140" alt="Errores de las apps"> |
| **Design system** | Super admin | Catálogo de colores y componentes (menú DEV). | <img src="../../adminApp/screenshots/design-system/phone-light.png" width="140" alt="Design system"> |

Los diálogos (crear/editar torneo, invitar, importar jugadores, control de ronda, perfil…) tienen sus capturas en `adminApp/screenshots/dialogs/` y `playerApp/screenshots/dialogs/`.

## Qué rol ve qué (resumen)

| | Sin cuenta | Jugador | Editor | Admin de país | Super admin |
|---|:-:|:-:|:-:|:-:|:-:|
| Ver torneos públicos, mesas, clasificaciones, estadísticas | ✅ | ✅ | ✅ | ✅ | ✅ |
| Marcar «yo», «mis torneos» | | ✅ | ✅* | ✅* | ✅* |
| Apuntar manos/asientos en su mesa (ronda abierta) | | ✅ | ✅* | ✅* | ✅* |
| Gestionar torneos (jugadores, mesas, manos, reloj, control de ronda, exportar) | | | Asignados | Sus países | Todos |
| Crear/borrar/configurar torneos, vaciar mesa, actividad del torneo | | | | Sus países | Todos |
| Cambiar el país de un torneo, borrar uno pasado con datos | | | | | ✅ |
| Cuentas: crear jugadores | | | ✅ | ✅ | ✅ |
| Cuentas: editores y admins de país | | | | Sus países | ✅ |
| Desactivar/borrar cuentas, email o EMA id de una cuenta, solicitudes | | | | | ✅ |
| Registro EMA: crear / editar y borrar | | | | Crear | ✅ |
| Menú DEV: actividad de la app, errores, design system | | | | | ✅ |

\* si la cuenta también tiene EMA id. La matriz detallada está en [user-roles-matrix.md](../user-roles-matrix.md).
