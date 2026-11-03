# Verificación de seguimiento — US13

El seguimiento reutiliza GET /api/pedidos/{id}; PedidoService valida propiedad del cliente y devuelve el enum real PENDIENTE, EN_PREPARACION, LISTO, ENTREGADO o CANCELADO. No hay endpoint nuevo para el cliente ni cambios de producción en esta entrega.

Us13HttpIntegrationTest usa servidor Spring Boot, login/JWT reales y H2 aislado. Comprueba:

- Tras cambios autorizados de operación, nuevas consultas del cliente muestran preparación, listo y entrega conservando ID, detalles, precios y total. El pago mantiene su estado independiente.
- Cancelado conserva compra/pago y no se reinicia al consultar de nuevo.
- El cliente no puede avanzar estados; un pedido ajeno permanece indistinguible de uno inexistente, sin exponer detalles.
- Cada transición genera un aviso para su cliente. GET /api/notificaciones/me lo devuelve a la cuenta correcta, sin avisos de otra cuenta.

~~~powershell
.\mvnw.cmd '-Dtest=Us13HttpIntegrationTest' test
~~~

Resultado: tres pruebas correctas, sin errores ni omisiones. La prueba configura JdkClientHttpRequestFactory para permitir PATCH; HttpURLConnection no lo admite. No se añadieron dependencias ni se modificaron CORS, seguridad o reglas de pedidos.

El frontend feature/cliente-pedido-api añade actualización manual y errores identificados como información anterior, sin modificar pedidos. La pantalla de notificaciones conserva su tarea propia; comprobar su endpoint en H2 no acredita la integración de esa pantalla. PostgreSQL/Supabase, el recorrido del equipo y despliegue no se validan con estas pruebas.
