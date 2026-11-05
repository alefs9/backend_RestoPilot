# Cierre del backend del Sprint 2, sin IA

Fecha: 2026-11-05. Rama: `fix/backend-sprint2-completo`.

Alcance: las 27 historias funcionales del backend, excluyendo US03 y US04 (IA). Esta revisión no convierte en terminadas las pantallas pendientes del frontend ni incluye despliegue público. La cobertura acredita los casos ejecutados; no garantiza ausencia de cualquier defecto futuro.

## Correcciones realizadas

- Validación HTTP de platos antes de persistir: nombre de hasta 150 caracteres, imagen de hasta 255, precio positivo con dos decimales y preparación entre 5 y 15 minutos. Se conservan las actualizaciones parciales y las comprobaciones de permisos y categoría.
- Operaciones de catálogo transaccionales.
- Número de mesa único por restaurante también en PostgreSQL, para impedir duplicados concurrentes. Coordenadas finitas; se rechazan NaN e infinito.
- Rutas inexistentes, métodos no admitidos y contenido incorrecto responden 404, 405 y 415 en lugar de un error interno genérico.
- Documentación OpenAPI real con JWT: `/v3/api-docs` y `/swagger-ui/index.html`. Las operaciones públicas de autenticación no exigen Bearer en el contrato; IA queda fuera.
- Las pruebas Maven usan el perfil `test` y H2; no heredan la conexión Supabase del `.env` local.
- El seed respeta los tiempos de preparación. `docs/sql/004_cierre_sprint2.sql` agrega el índice único y corrige exclusivamente los platos demo fuera de rango; no elimina ni fusiona mesas. Aborta si encuentra duplicados.

## Cobertura funcional revisada

Las pruebas se encuentran en `src/test/java/com/restopilot/backend`. Los nombres de la última columna identifican suites, no una afirmación de cobertura exhaustiva de todas sus combinaciones.

| US | Funcionalidad del backend | Evidencia automatizada principal |
|---|---|---|
| US01 | Registro de cliente, validaciones y duplicados | AltaPersonalIntegrationTest, ReglasNegocioIntegrationTest; Postman 02/03 |
| US02 | Login, JWT, roles y acceso restringido | JwAccessIntegrationTest, AltaPersonalIntegrationTest; Postman 02/03 |
| US05 | Disponibilidad, capacidad y solapamientos | CicloReservaIntegrationTest, ReglasNegocioIntegrationTest; Postman 03/04 |
| US06 | Registro pendiente y conflictos de mesa | CicloReservaIntegrationTest, ReglasNegocioIntegrationTest; Postman 03/04 |
| US07 | Pedidos y reservas del usuario autenticado | Us12HttpIntegrationTest, Us26HttpIntegrationTest; Postman 02/04 |
| US08 | Reprogramación, estado y concurrencia | CicloReservaIntegrationTest, ReglasNegocioIntegrationTest; Postman 03/04 |
| US09 | Cancelación y límite de dos horas | CicloReservaIntegrationTest, ReglasNegocioIntegrationTest; Postman 03/04 |
| US10 | Carta, categorías y disponibilidad de platos | Us23HttpIntegrationTest, Sprint2ApiContractTest; Postman 02/05 |
| US11 | Registro de pedidos en cuatro modalidades | Us11HttpIntegrationTest, ReglasNegocioIntegrationTest |
| US12 | Pedido propio, precios históricos y pago | Us12HttpIntegrationTest, Sprint2ApiContractTest; Postman 02 |
| US13 | Seguimiento, aislamiento y notificaciones | Us13HttpIntegrationTest; Postman 02 |
| US14 | Gestión de platos y disponibilidad | Sprint2ApiContractTest, ReglasNegocioIntegrationTest; Postman 05 |
| US15 | Cola de cocina y prioridad del servidor | Us15HttpIntegrationTest; Postman 02 |
| US16 | Mesas, unicidad y reservas comprometidas | Sprint2ApiContractTest, ReglasNegocioIntegrationTest; Postman 03/05 |
| US17 | Confirmación, rechazo y motivo | CicloReservaIntegrationTest, ReglasNegocioIntegrationTest; Postman 03/04 |
| US18 | Pagos, importe exacto y cuatro métodos | Sprint2ApiContractTest, ReglasNegocioIntegrationTest; Postman 02 |
| US19 | Preparación, listo, entrega y rechazos | Us19HttpIntegrationTest; Postman 02 |
| US20 | Reportes, períodos, estados y aislamiento | ReglasNegocioIntegrationTest, CicloReservaIntegrationTest; Postman 02/03/04 |
| US21 | Alta atómica de dueño y restaurante | AltaPersonalIntegrationTest; Postman 02/03/05 |
| US22 | Alta de administrador por dueño | AltaPersonalIntegrationTest; Postman 03/05 |
| US23 | Categorías, permisos e integridad | Us23HttpIntegrationTest; Postman 05 |
| US24 | Ajuste y cancelación administrativa | ReglasNegocioIntegrationTest; Postman 02/03/05 |
| US25 | Llegada y finalización con reloj del servidor | Us25HttpIntegrationTest, CicloReservaIntegrationTest; Postman 04 |
| US26 | Expiración, idempotencia y liberación de mesa | Us26HttpIntegrationTest, CicloReservaIntegrationTest; Postman 04 |
| US27 | Atención física y restricciones derivadas | Sprint2ApiContractTest, ReglasNegocioIntegrationTest; Postman 03 |
| US28 | Notificaciones del usuario autenticado | Sprint2ApiContractTest, Us13HttpIntegrationTest; Postman 02/04 |
| US29 | Agenda por restaurante, filtros y acciones | Sprint2ApiContractTest, CicloReservaIntegrationTest; Postman 03/04 |

## Resultados de esta revisión

- Compilación del paquete: correcta.
- Maven: 129 pruebas registradas, 128 aprobadas, 0 fallidas, 0 errores y 1 omitida (prueba visual opcional de US23).
- PostgreSQL local aislado: 251 solicitudes y 754 aserciones aprobadas en las carpetas 02, 03, 04 y 05 de la colección versionada. Incluye solicitudes auxiliares de los escenarios concurrentes.
- El ciclo 04 requiere fixtures SQL del ejecutor `scripts/ejecutar-postman-ciclo.cjs`. En esta revisión se adaptó el transporte SQL a una base local efímera, conservando los mismos eventos y la restricción de actualizar únicamente reservas creadas por la corrida. La ejecución directa sin fixtures no sirve para evaluar la expiración.
- Postman CLI ejecutó 02, 03 y 05 sin fallos de solicitudes ni aserciones. La publicación de resultados requiere login; no se publicaron resultados. El ciclo 04 se ejecutó con Newman y terminó sin fallos.
- Supabase: migración 004 aplicada; índice único presente, cero grupos de mesas duplicados y cero platos demo con preparación fuera de rango.

El detalle sanitizado está en `docs/evidencias/cierre-backend/resultado.json`. No se versionan contraseñas, tokens, archivos de conexión ni cuerpos completos de respuestas de autenticación.

## Cómo continuar

Ejecutar `mvnw.cmd test` antes de integrar cambios. El perfil de pruebas no necesita Supabase. Para usar los cambios desde el frontend, reiniciar la aplicación en IntelliJ y mantener la URL del API en su entorno actual. Swagger aparece después de ese reinicio.

Para una instalación nueva en Supabase, seguir `docs/SUPABASE.md` y aplicar 004 después del esquema y del seed. En una instalación existente, ejecutar 004 una sola vez o repetirla de forma idempotente. Si se detectan mesas duplicadas, resolver sus referencias de negocio antes de reintentar; el script no borra datos.
