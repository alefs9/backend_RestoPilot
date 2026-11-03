# US26 — Disponibilidad después de expirar

Fecha: 03/11/2026. Rama: feature/reserva-expirada-api, desde develop con US25 incorporada.

Se mantiene GET /api/reservas/disponibilidad y su DTO. La consulta admite fecha/horario ya iniciados o pasados para verificar la liberación de una reserva expirada. validarConsultaDisponibilidad conserva restaurante activo/atención física, fecha/horas obligatorias, inicio anterior a fin, horario comercial y comensales positivos. Crear/reprogramar usan validarRestauranteYHorario y siguen rechazando pasado. No se añaden endpoints, entidades, migraciones o reglas de expiración.

La expiración existente excluye la reserva del conjunto bloqueante PENDIENTE/CONFIRMADA. Al expirar, su mesa queda disponible si no existe otro bloqueo solapado. Una nueva reserva válida posterior puede ocupar un rango solapado; esto no revive la expirada. expiradaEn registra el instante de proceso, que puede ser posterior al límite. No se guarda una causa específica y no debe deducirse de la marca temporal.

El frontend usa GET de detalle, /me, listado del restaurante y disponibilidad, sin mutaciones de expiración. Mantiene el resultado real de US25 y no copia fixtures para simular persistencia.

Pruebas ejecutadas con JDK 21 y H2 aislado:

~~~powershell
.\mvnw.cmd '-Dtest=Us26HttpIntegrationTest' test
.\mvnw.cmd '-Dtest=ReglasNegocioIntegrationTest,Us25HttpIntegrationTest,CicloReservaIntegrationTest' test
~~~

Resultado: 11 pruebas US26 con HTTP/JWT/Clock y 53 regresiones correctas (26 reglas, 8 HTTP US25 y 19 ciclo). Se verifican antes/exacto/después de los límites, marcas e historial, aislamiento, concurrencia con un único aviso, estado preservado tras solicitudes tardías, mesa libre tras expirar y ocupación por una reserva posterior permitida.

ReglasNegocioIntegrationTest adapta su expectativa de lectura histórica a la semántica autorizada y conserva el rechazo de escritura pasada y de restaurante inactivo. La consulta histórica no concede permisos para reservar/reprogramar en el pasado. No se desactivaron verificaciones.

Pendiente aceptación con datos y PostgreSQL del equipo. Las pruebas no acreditan despliegue ni usaron credenciales o reservas del equipo. No se crearon commits automáticamente.
