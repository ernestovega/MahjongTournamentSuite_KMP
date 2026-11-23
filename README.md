# Mahjong Tournament Suite

**Torneos de Mahjong (reglas MCR) de principio a fin: inscripciones, sorteo, mesas en vivo, clasificación y estadísticas.**

Una sola base de código (Kotlin Multiplatform + Compose) que da una app Android, una web y una app de escritorio, con dos partes:

- **Player**: para jugadores y público. Todo es público y no hace falta cuenta para mirar.
- **Admin (Gestión)**: para organizadores, Club Admins y editores de mesa.

Web: https://mts.mahjongmadrid.com · Gestión: `/admin/` · Android: app «MTS» (Player y Gestión en una sola app).

---

## Player

- 🏆 **Torneos**: pestañas En juego · Todos · Estadísticas, con filtros (estado, país, año) y sellos «Jugando / Jugado / Inscrito / En espera».
- 📝 **Inscripciones**: Inscribirme con o sin cuenta, plazas y lista de espera, equipos, restricciones alimentarias y baja. Enlace corto por torneo (p. ej. `/6thmmc`).
- 🀄 **Mesas en vivo**: el jugador elige asientos y apunta sus manos y penalizaciones desde el móvil; todo se ve al momento, con animaciones de mano nueva o corregida.
- ⏰ **Mi mesa**: aviso «Mesa N · Ronda R» al llegar la ronda, botón flotante «Ir a mi mesa», tiempo de ronda restante y QR del torneo que abre tu mesa.
- 📊 **Clasificación** de jugadores y equipos, mano pollo y mejor mano, con fotos.
- 👤 **Ficha de jugador**: Mesas · Mis resultados (tabla de rondas compartible como imagen) · Estadísticas.
- 📈 **Estadísticas públicas** con filtros (jugador, rival, torneo, países, años): habilidades, puestos, récords, rivales y gráficas por ronda.
- 📖 **Reglas a mano**: las 81 combinaciones MCR con fichas, penalizaciones y Green Book (EN/ES/RU).
- 🌍 **8 idiomas** (EN, ES, FR, IT, DE, SV, NL, RU), tema claro/oscuro y perfil con foto.

## Admin

- 🗂️ **Torneos**: crear (individual o por equipos), configurar, hacer público, organizaciones dueñas con logo y color.
- 🎲 **Sorteo** de enfrentamientos sin repeticiones, explicado con un diagrama para que cualquiera lo entienda.
- 📝 **Inscripciones**: plazas, lista de espera, pagado, importar el Google Form, exportar CSV, correos y «Hacer el sorteo».
- 👥 **Jugadores y equipos**: registro EMA, importación, invitaciones por email, sustituciones y cambio del número de jugadores antes de empezar.
- ✍️ **Editor de rondas y mesas**: 16 manos por mesa, totales manuales, penalizaciones y varios editores a la vez sin pisarse.
- ⏱️ **Reloj de ronda** que abre y cierra las mesas solo, con control de ronda para cerrar mesas olvidadas.
- 📺 **Para la sala**: clasificación a pantalla grande que pasa páginas sola, QR del torneo para proyectar o imprimir en tarjetas por mesa.
- 🖨️ **Impresión y exportación**: tarjetas identificativas en PDF (sustitutos y staff incluidos) e informe de resultados para la EMA.
- 🔐 **Roles**: Super Admin, Club Admin, Editor y roles a medida con permisos uno a uno.
- 🕵️ **Control**: registro de actividad (quién cambió qué), errores de las apps y estadísticas iguales a las del Player.

---

## Tecnologías

### Apps (cliente)

| Pieza | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.4 (Kotlin Multiplatform) |
| UI | Compose Multiplatform 1.12 + Material 3, design system propio (`:designsystem`) |
| Plataformas | Android (minSdk 24, targetSdk 36), web con Kotlin/Wasm, escritorio JVM |
| Inyección de dependencias | Koin 4 |
| Red | Ktor client 3 (OkHttp en Android, CIO en escritorio, JS en web) + kotlinx.serialization |
| Concurrencia y fechas | Kotlin Coroutines, kotlinx-datetime |
| Navegación | Navigation Compose (JetBrains) |
| Imágenes y QR | Coil 3, QRose |
| Logs | Kermit |
| Firebase en Android | App Check (Play Integrity), Analytics (GA4) |
| Build | Gradle 9 + AGP 9, version catalog (`gradle/libs.versions.toml`) |

Módulos: `domain` (modelos y reglas, sin dependencias de plataforma), `data` (cliente de la API, DTOs, cachés), `ui` (componentes y pantallas compartidas), `designsystem`, `playerApp`, `adminApp` y `androidApp` (la app Android que junta las dos partes).

### Backend

| Pieza | Tecnología |
|---|---|
| API | Una Cloud Function HTTP `api` (2ª gen) en `europe-southwest1` (Madrid): Node 22 + Express 5 + TypeScript |
| Tareas | Cloud Tasks (cierre de rondas al acabar el tiempo) y Cloud Scheduler (job horario de inscripciones), en `europe-west1` |
| Base de datos | Cloud Firestore (`eur3`). Reglas *deny all*: solo la función lee y escribe |
| Ficheros | Cloud Storage (fotos de jugadores y cuentas, logos de organizaciones) |
| Autenticación | Firebase Auth con *custom claims* para los roles y permisos con nombre |
| Seguridad | App Check, límites de peticiones, secretos en Secret Manager |
| Hosting | Firebase Hosting: las dos webs, `/api-docs/` y reenvío de `/api/**` a la función |
| PDF, Excel y correo | PDFKit (tarjetas ID y QR), ExcelJS (importar el Google Form), Nodemailer (SMTP), sharp (imágenes) |
| Externos | Google Places API (sedes), registro y rankings de la EMA |

Documentación de la API (OpenAPI / Swagger UI): https://mts.mahjongmadrid.com/api-docs/ (fuente [docs/api/openapi.json](docs/api/openapi.json), generada con `python3 scripts/generate-openapi.py`).

### Calidad y despliegue

- **CI/CD con GitHub Actions**: deploy de Firebase y web, subida a Google Play, staging, galería de capturas y ensayos.
- **Entornos**: producción y staging (`mts---staging.web.app`).
- **Versiones** separadas de web, app Android y backend (`python3 scripts/versions.py status`).
- **Tests**: unitarios y multiplataforma (kotlin.test, Koin verify), backend con `node --test` y emuladores de Firebase, E2E en emuladores Android, capturas con Roborazzi (Git LFS) de todas las pantallas en los 8 idiomas, ktlint y Kover.
- **Game day**: bots que juegan torneos enteros contra staging para probar carga y casos límite.

Más detalle en [docs/arquitectura/](docs/arquitectura/README.md): diagramas del sistema, modelo de datos, despliegues y versiones, y mapa de pantallas.

## Compilar y ejecutar

```bash
# Android
./gradlew :androidApp:assembleDebug

# Escritorio (gestión)
./gradlew :adminApp:run

# Web (Wasm)
./gradlew :adminApp:wasmJsBrowserDevelopmentRun
./gradlew :playerApp:wasmJsBrowserDevelopmentRun
```

Las webs usan el backend de producción por defecto. Para un backend local con datos de prueba, ver [docs/test-environment.md](docs/test-environment.md).

## Tests

```bash
# Kotlin
./gradlew :domain:allTests :data:allTests :adminApp:jvmTest

# Backend
cd firebase/functions && npm test
```

Capturas: [docs/screenshot-tests.md](docs/screenshot-tests.md) · Tests de interfaz: [docs/instrumented-ui-tests.md](docs/instrumented-ui-tests.md).

## Licencia

Apache 2.0. Ver [LICENSE](LICENSE).
