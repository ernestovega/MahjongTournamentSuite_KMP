# Plan del cliente de jugador

## Resumen

Añadir un cliente de jugador separado dentro del mismo repositorio KMP.

El cliente reutilizará `domain`, `data`, autenticación y el backend actual. Tendrá navegación, permisos y pantallas propias.

El backend permitirá:

- Invitar cuentas nuevas o existentes.
- Asociar cada cuenta a un único `emaId`.
- Asociar esa cuenta a un slot de jugador del torneo.
- Consultar torneos, rondas y mesas en modo lectura.
- Registrar manos y validarlas con tres confirmaciones.

## Puntos abiertos antes de implementar

Esta revisión compara el plan con el código y el backend actuales. Resolver estos puntos antes de empezar cualquier fase. Si un punto contradice una sección posterior del plan, este punto tiene prioridad hasta que se actualice esa sección.

### Bloqueantes

#### 1. Una cuenta de jugador tendría acceso de editor

- `GET /admin/whoami` devuelve `ADMIN` con la claim `admin` y `EDITOR` en cualquier otro caso (`firebase/functions/src/api/routes/admin.routes.ts`).
- Rutas como `POST /admin/users` solo usan `requireAuth`.
- Cualquier cuenta sin la claim `admin` se trata como editor.
- Una cuenta `PLAYER` podría usar la API de gestión y crear cuentas de editor.

Corrección:

- Exigir `EDITOR` o `ADMIN` de forma explícita en cada ruta de gestión.
- Denegar el acceso a cuentas sin capacidades.
- Hacer este cambio como paso 0 del despliegue, antes de crear cualquier cuenta `PLAYER`.
- Extender `verifyRolePermissions` para llamar a las rutas de editor con una cuenta solo `PLAYER` y esperar `403`.

#### 2. El nombre `PlayerRepository` ya existe

`domain/.../repository/PlayerRepository.kt` ya gestiona el registro compartido de jugadores EMA.

Elegir otro nombre, por ejemplo `PlayerClientRepository` o `ParticipantRepository`.

#### 3. Los horarios ya existen y no coinciden con el plan

El plan espera `scheduledStartAt: Instant`, `scheduledEndAt` y `timeZoneId`. El código actual tiene:

- `TournamentRoundSchedule(roundId, date "yyyy-MM-dd", startTime "HH:mm")`, sin hora de fin.
- `TournamentAgendaItem`.
- Ninguna zona horaria en `Tournament`.

Decidir:

- Añadir `Tournament.timeZoneId` o derivarlo de `hostCountry` y `hostCity`.
- Calcular el instante UTC en `data` o en el backend, no en `domain`.
- Extender `TournamentRoundSchedule` en lugar de añadir campos nuevos a `TournamentRound`.
- Definir si una ronda necesita hora de fin o si se calcula.

Sin esta decisión, los avisos no se pueden calcular de forma fiable.

#### 4. Los slots sin cuenta nunca reúnen tres confirmaciones

El plan no permite cuentas para no miembros. El mismo problema aparece con jugadores que no aceptan la invitación o no tienen teléfono.

Elegir una alternativa:

- Solo confirman los slots con una cuenta aceptada.
- Un editor sustituye las confirmaciones que faltan.
- La mesa queda solo para editores si tiene menos de cuatro cuentas.

#### 5. El ejemplo de `TournamentPlayer` elimina campos

El ejemplo quita `nonMember`, `createdAt` y `updatedAt`. El cambio debe extender la clase actual, no sustituirla.

### Flujo de manos

- **Manos que todavía no existen.** El backend crea las manos cuando se abre la mesa por primera vez. `saveTableState` falla con `Hand not found` si la mano no existe. Decidir si la propuesta crea la mano o si exige que exista.
- **Asignación de `handId`.** Definir quién elige el siguiente `handId`, qué pasa si dos jugadores registran la misma mano a la vez y si se puede registrar la mano N+1 con la mano N pendiente. Propuesta: el servidor asigna el siguiente `handId` y solo existe una propuesta abierta por mesa.
- **Mesas solo con totales.** `useTotalsOnly` vale `true` por defecto y no encaja con un flujo por manos. Bloquear las propuestas en esas mesas o definir una propuesta de totales finales.
- **Penalizaciones.** Normalmente las decide el árbitro. Valorar si deben quedar solo para editores.
- **Estado `OVERRIDDEN`.** Definir el disparador: un editor edita la mano o reinicia la mesa con una propuesta abierta.
- **Reenvío tras rechazo.** Si solo el autor puede reenviar y se marcha, la mano queda bloqueada. Permitir que cualquier jugador de la mesa cree una propuesta nueva tras un rechazo.
- **Contención de escrituras.** La copia a la mano canónica debe reutilizar `saveTableState` para recalcular el resumen en el servidor. Esa transacción también lee todas las mesas incompletas del torneo, así que muchas confirmaciones al final de una ronda compiten por los mismos documentos. Cada copia incrementa además el `version` de la mesa, y los editores con la mesa abierta reciben un `409`. Hacer una prueba de carga con unas 40 mesas confirmando a la vez.
- **Rutas de confirmación.** `POST /player/submissions/{submissionId}/confirm` no incluye el torneo y obliga a una consulta de grupo de colecciones. Anidar las rutas bajo `/player/tournaments/{id}/tables/{roundId}/{tableId}/...` para simplificar la comprobación de permisos.
- **Reasignación de slots.** Reasignar o intercambiar el EMA de un slot debe revocar su invitación y limpiar `accountUid`.

### Cuentas e invitaciones

- **Dos emails para cuentas nuevas.** El plan envía un email de restablecimiento y otro de invitación. Valorar un único enlace de invitación: el backend valida el token y define la contraseña con el Admin SDK al aceptarla.
- **Requisitos del servicio de email.** La extensión Trigger Email necesita el plan Blaze y un proveedor SMTP. Añadir ambos a la lista de despliegue.
- **Cuentas de editor sin `emaId`.** Definir quién asigna el `emaId` a una cuenta existente y cómo se verifica.
- **Compatibilidad de `whoami`.** Los clientes desplegados exigen `whoami.role` (ver `docs/firebase-role-rollout.md`). Devolver `roles` junto a `role`, sin sustituirlo.
- **Otros modelos con rol único.** `ManagedUser.role` y `TournamentMember.role` también deben pasar a un conjunto de capacidades.
- **Visibilidad para el jugador.** Definir si un jugador ve solo sus mesas o todas las mesas y clasificaciones del torneo. Es una decisión de privacidad porque expone emails e IDs EMA de otros jugadores.
- **`EDITOR + PLAYER` en el mismo torneo.** Decidir si un editor que juega puede editar directamente las manos de su propia mesa.

### Cliente y estructura de módulos

- **Componentes compartidos.** `KeyboardFocus.kt`, `AppErrorDialog`, las barras de desplazamiento y `ScreenLayouts` están en `composeApp/.../presentation/components`. `playerApp` no debe depender de `composeApp`. Extraerlos a un módulo de UI compartido, por ejemplo `:ui`.
- **Entrada Android.** Con AGP 9, los módulos KMP usan el plugin de librería. El cliente necesita una entrada propia, por ejemplo `playerAndroidApp`, igual de fina que `androidApp`.
- **Web.** Definir un sitio de Firebase Hosting o una ruta propia para el cliente de jugador.
- **Desktop.** Valorar eliminar Desktop del cliente de jugador. Los jugadores usan el teléfono en la mesa y los avisos de Desktop son limitados.
- **iOS.** iOS está fuera de alcance, pero muchos jugadores usan iPhone. Su única vía es la app web, y Web Push en iOS solo funciona desde una app añadida a la pantalla de inicio en iOS 16.4 o posterior. Documentarlo de forma explícita.
- **Consultas periódicas.** `docs/backend.md` indica que no hay consultas en segundo plano. Cada consulta cada 5 segundos es una llamada a Functions y lecturas de Firestore. Responder `304` según el `version` de la mesa y valorar un intervalo de 10 a 15 segundos.
- **Dependencias nuevas.** FCM en Android y la interoperabilidad con el SDK JS de Firebase en web son dependencias nuevas. Justificarlas en el plan según la regla de dependencias de `AGENTS.md`.

### Fases propuestas

El plan contiene tres proyectos. Propuesta de fases:

1. **Roles y seguridad.** Comprobaciones explícitas de `EDITOR`, `emaId`, invitaciones y cliente de jugador de solo lectura en Android y Web.
2. **Propuestas de manos.** El flujo de manos con las alternativas de este apartado.
3. **Horarios y avisos.** Zona horaria, proceso programado, FCM y Web Push.

### Puntos menores

- El resto de `docs/` está en inglés y este plan está en español.
- Las pruebas de conversión de zona horaria pertenecen a `data`, no a `domain`.

## Cambios principales

### Cuentas e invitaciones

- Cambiar el modelo de rol único a capacidades combinables:
  - `ADMIN`
  - `EDITOR`
  - `PLAYER`
- Mantener compatibilidad con la claim actual `admin`.
- Añadir `emaId` al perfil de usuario.
- Impedir que dos cuentas usen el mismo `emaId`.
- Conservar `emaId` cuando cambia el email.
- Permitir cambiar el email:
  - El jugador usa verificación del nuevo email.
  - El organizador puede corregirlo desde gestión.
- Añadir a cada slot:
  - `accountUid`
  - estado de invitación
  - fecha de invitación
- Permitir invitaciones solo para slots con un EMA asignado.
- No permitir cuentas de jugador para slots de no miembros.

Flujos:

- Cuenta nueva:
  - El organizador introduce el email en `Tournament Players`.
  - El backend crea una cuenta `PLAYER`.
  - Asocia la cuenta al `emaId` y al slot.
  - Envía un email para definir la contraseña y aceptar el torneo.
- Cuenta existente:
  - El backend verifica su `emaId`.
  - Añade la asociación al nuevo slot.
  - Envía un enlace específico para ese torneo.
- Las invitaciones tendrán expiración, uso único y asociación con el email esperado.
- Usar la extensión de email de Firebase para enviar enlaces de torneo.
- Mantener el email de restablecimiento de Firebase para cuentas nuevas.

### Backend y contratos

Añadir middleware específico para jugadores. El backend no dependerá de ocultar botones en la interfaz.

Nuevos grupos de endpoints:

- `GET /player/tournaments`
- `GET /player/tournaments/{id}`
- `GET /player/tournaments/{id}/tables`
- `GET /player/tournaments/{id}/tables/{roundId}/{tableId}`
- `POST /player/invitations/{token}/accept`
- `POST /tournaments/{id}/player-invitations`
- `POST /tournaments/{id}/player-invitations/{invitationId}/resend`
- `DELETE /tournaments/{id}/player-invitations/{invitationId}`
- `POST /player/tournaments/{id}/tables/{roundId}/{tableId}/hands/{handId}/submissions`
- `POST /player/submissions/{submissionId}/confirm`
- `POST /player/submissions/{submissionId}/reject`
- `POST /player/submissions/{submissionId}/resubmit`

Actualizar los contratos comunes:

```kotlin
enum class GlobalUserRole {
    EDITOR,
    ADMIN,
    PLAYER,
}

data class UserProfile(
    val uid: String,
    val email: String,
    val alias: String = "",
    val emaId: String? = null,
    val roles: Set<GlobalUserRole> = emptySet(),
)

data class TournamentRound(
    val roundId: Int,
    val scheduledStartAt: Instant? = null,
    val scheduledEndAt: Instant? = null,
)

data class TournamentPlayer(
    val id: Int,
    val name: String,
    val team: Int,
    val country: String = "",
    val assignedEmaId: String? = null,
    val accountUid: String? = null,
    val invitationStatus: InvitationStatus = InvitationStatus.NONE,
)
```

Crear una interfaz `PlayerRepository` para separar las operaciones del jugador de `TournamentRepository`.

### Horarios y avisos

Consumir los horarios creados por la futura funcionalidad de programación.

Usar:

- `scheduledStartAt` como instante UTC.
- `scheduledEndAt` opcional.
- `timeZoneId` del torneo para mostrar la hora local.

Si una ronda no tiene horario, el cliente muestra “Horario pendiente” y no crea avisos.

Comportamiento por defecto:

- Mostrar una alerta persistente dentro de la app desde 15 minutos antes.
- Mantenerla hasta que el jugador abra su mesa o termine la ronda.
- Enviar un aviso del sistema al inicio de la alerta.
- Enviar otro aviso al comenzar la ronda si el jugador no ha entrado.
- Consultar el estado cada 5 segundos en la mesa activa.
- Consultar el estado cada 30 segundos en la pantalla del torneo.
- Suspender consultas cuando la aplicación queda en segundo plano.

Canales:

- Android: FCM y notificación nativa.
- Web: FCM, permiso del navegador y service worker.
- Desktop: notificación del sistema mientras el proceso está activo.
- Si el usuario deniega permisos, conservar la alerta dentro de la app.

Añadir un proceso programado de Firebase Functions para enviar avisos de rondas próximas. Registrar y eliminar instalaciones de notificación por usuario.

### Flujo de manos

Mantener los campos actuales de `TableHand`:

- ganador
- perdedor
- puntuación
- mano especial
- penalizaciones

No escribir directamente la mano canónica desde el cliente de jugador.

Crear una propuesta de mano con:

- `submissionId`
- `handId`
- slot que registra la mano
- valores propuestos
- estado
- confirmaciones por slot
- fechas de creación y actualización
- motivo de rechazo opcional

Estados:

- `PENDING`
- `REJECTED`
- `VALID`
- `OVERRIDDEN`

Reglas:

- El jugador que registra la mano no necesita confirmarse.
- Los otros tres jugadores deben confirmarla.
- Una confirmación negativa cambia la propuesta a `REJECTED`.
- Solo el jugador que registró la mano puede corregirla y reenviarla.
- Una transacción de Firestore validará las tres confirmaciones.
- Solo entonces se copiarán los datos a la mano canónica.
- La mano canónica se marcará como `isDone`.
- El editor podrá cancelar o validar una propuesta bloqueada.
- El backend impedirá confirmaciones duplicadas o de jugadores ajenos a la mesa.
- El backend mantendrá el historial de propuestas para auditoría.

## Cliente de jugador

Crear un módulo KMP `playerApp` con una entrada Android propia y navegación independiente.

Pantallas:

- Inicio de sesión y recuperación de acceso.
- Lista de torneos asignados.
- Detalle de torneo:
  - rondas
  - horarios
  - mesas
  - estado de cada mesa
- Detalle de mesa:
  - cuatro jugadores
  - manos registradas
  - resultados validados
  - propuesta pendiente
- Registro de mano.
- Confirmación o rechazo de una mano.
- Perfil y cambio de email.

La interfaz del jugador no incluirá:

- edición de torneos
- asignación de EMA
- edición de mesas
- reinicio de mesas
- edición directa de manos
- exportación de resultados

Las pantallas nuevas usarán los componentes de foco compartidos y `Modifier.appFocusGroup()`.

La pantalla inicial enfocará el próximo torneo o la alerta de mesa. Los diálogos restaurarán el foco al control que los abrió.

Actualizar `PlayersScreen` para incluir:

- asignación de cuenta
- estado de invitación
- reenviar invitación
- revocar invitación
- mostrar el email asociado

Actualizar la gestión de usuarios para mostrar capacidades combinadas y el `emaId`.

## Pruebas y aceptación

### Dominio

Añadir pruebas comunes para:

- capacidades combinadas
- asociación única entre cuenta y EMA
- cambio de email sin perder `emaId`
- cálculo de próxima mesa
- conversión de zona horaria
- propuesta pendiente
- tres confirmaciones
- rechazo y reenvío
- confirmación duplicada
- jugador incorrecto
- propuesta ya validada

### Firebase Functions

Añadir pruebas para:

- creación de cuenta nueva
- asociación con una cuenta existente
- validación de email y EMA
- acceso solo a torneos asignados
- acceso solo a mesas visibles para el jugador
- bloqueo de escrituras editoriales
- confirmación atómica de tres jugadores
- concurrencia entre dos confirmaciones
- revocación y expiración de invitaciones
- permisos de cuentas `EDITOR + PLAYER` y `ADMIN + PLAYER`
- generación idempotente de avisos

Extender `verifyRolePermissions` con cuentas de jugador y capacidades combinadas.

### Validación manual

Probar Android, Web y Desktop:

- invitación de cuenta nueva
- invitación de cuenta existente
- acceso a varios torneos
- cambio de email
- alerta de próxima mesa
- aviso en segundo plano
- aviso Desktop con la aplicación minimizada
- registro de una mano
- confirmación por los otros tres jugadores
- rechazo, corrección y reenvío
- recarga durante una propuesta pendiente
- pérdida de conexión y reintento
- confirmaciones repetidas

## Migración y despliegue

- Mantener nulos los campos nuevos en datos existentes.
- Convertir usuarios actuales:
  - administradores a `ADMIN`
  - editores a `EDITOR`
- No cambiar manos existentes.
- Añadir propuestas como subcolecciones nuevas.
- Desplegar primero backend, roles y permisos.
- Configurar el servicio de email, FCM, Web Push y el proceso programado.
- Desplegar después el cliente de jugador.
- Probar con un torneo piloto antes de activar todas las invitaciones.

## Supuestos fijados

- Una cuenta representa un único jugador EMA.
- Una cuenta puede tener varias capacidades.
- Una cuenta puede participar en muchos torneos.
- Una cuenta puede cambiar de email sin cambiar de identidad EMA.
- El cliente de jugador no modifica datos canónicos directamente.
- Desktop no recibe avisos con el proceso completamente cerrado.
- El aviso persistente dentro de la app siempre funciona cuando existe horario.
