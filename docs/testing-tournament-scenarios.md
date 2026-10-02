# Pruebas de escenarios de torneo

Este proyecto usa pruebas deterministas para validar torneos completos.
Cada escenario de prueba usa datos fijos y guarda el resultado esperado.
La generación real usa una semilla aleatoria nueva en cada ejecución.

## Pruebas incluidas

- Un torneo de 16 jugadores con dos rondas.
- Jugadores EMA y jugadores invitados.
- Rankings de jugadores y equipos.
- Recarga de mesas en un orden diferente.
- Manos completas e incompletas.
- Puntuaciones y puntos con signo.
- Validación de entradas del generador de horarios.
- Finalización del torneo cuando termina la última mesa.
- Protección contra conflictos de versión.

## Ejecutar las pruebas

Desde la raíz del proyecto:

```bash
./gradlew :domain:allTests :data:allTests :composeApp:allTests
```

Para las reglas y servicios de Firebase:

```bash
cd firebase/functions
npm test
```

## Prueba manual de aceptación

1. Crear un torneo de 16 jugadores y dos rondas.
2. Asignar 12 jugadores EMA y cuatro invitados.
3. Abrir la misma mesa en dos sesiones.
4. Guardar un cambio desde la primera sesión.
5. Intentar guardar un cambio antiguo desde la segunda sesión.
6. Confirmar el conflicto y recargar la mesa.
7. Completar todas las mesas.
8. Recargar el torneo y comprobar el ranking.
9. Reiniciar una mesa y comprobar que el torneo deja de estar completo.

Los fallos deben guardar la semilla, el torneo, la ronda, la mesa y la versión.
Esto permite repetir el mismo caso sin depender de datos aleatorios.

La generación aleatoria de horarios necesita una prueba adicional de rendimiento.
En pruebas locales puede tardar demasiado con 16 jugadores y varias rondas.
