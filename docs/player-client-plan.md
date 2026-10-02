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
