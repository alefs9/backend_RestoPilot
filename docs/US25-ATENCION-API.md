# US25 — Atención de reservas y controles temporales

Fecha: 03/11/2026. Rama: feature/operacion-reserva-atencion-api, desde develop actualizado.

Se conservan GET /api/reservas/{id}, PATCH /api/reservas/{id}/llegada y PATCH /api/reservas/{id}/completar. El DTO añade fechaHoraServidor (LocalDateTime), puedeRegistrarLlegada y puedeCompletar (boolean), sin retirar campos ni añadir entidades o migraciones.

ReservaMapper evalúa una vez Clock para cada respuesta; conMensaje conserva los controles. Las condiciones se comparten con AtencionReservaService mediante ReglasCicloReserva. No cambia el negocio: llegada antes de inicio + 15 minutos, llegada anticipada permitida, completar con llegada desde inicio, sin esperar a horaFin. La llegada mantiene CONFIRMADA; completar conserva llegada y registra COMPLETADA/completadaEn. La autorización por rol/local sigue siendo independiente de los indicadores.

El frontend no debe usar su reloj para sustituir esos indicadores ni asumir que un indicador previo garantiza el éxito de una operación posterior. El servidor vuelve a evaluar bajo bloqueo. El proceso de expiración sigue pudiendo confirmar EXPIRADA aunque una petición tardía sea rechazada; no se duplican avisos ni se liberan mesas desde Angular.

Pruebas ejecutadas con JDK 21:

~~~powershell
.\mvnw.cmd '-Dtest=Us25HttpIntegrationTest,CicloReservaIntegrationTest' test
~~~

Resultado: 8 pruebas HTTP/JWT/Clock/H2 y 19 pruebas existentes del ciclo correctas. Verifican el límite antes/exacto/después de los 15 minutos; finalización desde inicio; fechas preservadas; repetición y concurrencia; permisos; llegada anticipada sin restricción inventada; ausencia de expiración tras llegada y disponibilidad después de completar.

El GET de otro restaurante devuelve actualmente 400 por BusinessRuleException; los PATCH ajenos devuelven 403. Se comprobó y documentó sin cambiar los permisos ni el contrato de errores para satisfacer una suposición de prueba.

Pendiente aceptación con autenticación/PostgreSQL del equipo y continuación de US26. Esta evidencia no certifica despliegue. No se usaron credenciales ni datos del equipo ni se crearon commits automáticamente.
