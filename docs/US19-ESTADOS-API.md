# US19 — Verificación de estados del pedido

Fecha: 03/11/2026. Rama: feature/operacion-cocina-api.

El endpoint existente PATCH /api/pedidos/{id}/estado?nuevoEstado=ENUM devuelve 200 con PedidoResponseDTO. Solo ADMINISTRADOR/DUENO del restaurante pueden avanzar PENDIENTE → EN_PREPARACION → LISTO → ENTREGADO. No se permiten saltos, repetición, retrocesos ni avances desde ENTREGADO/CANCELADO. El servicio bloquea el pedido y envía una notificación por transición aceptada. US19 no modifica endpoints, seguridad, entidades ni código de producción del backend.

Us19HttpIntegrationTest usa HTTP real, JWT y H2 aislado. Tres pruebas correctas: secuencia y salida de cola sin alterar compra/pago con un aviso por etapa; las 22 combinaciones inválidas entre cinco estados sin cambios ni avisos; permisos, inexistencia y dos confirmaciones concurrentes (un 200, un 400, un único avance y aviso). También pasaron Us15HttpIntegrationTest y Us13HttpIntegrationTest (3 pruebas cada una).

Reproducir con JDK 21:

~~~powershell
.\mvnw.cmd '-Dtest=Us19HttpIntegrationTest,Us15HttpIntegrationTest,Us13HttpIntegrationTest' test
~~~

El frontend debe consultar ante 400/409 o respuesta incierta antes de otra acción; perder una respuesta no garantiza que no se guardó. El cambio de estado no registra un cobro ni debe duplicar la notificación del backend. Esta evidencia no acredita PostgreSQL ni despliegue del equipo; la aceptación integrada continúa pendiente.
