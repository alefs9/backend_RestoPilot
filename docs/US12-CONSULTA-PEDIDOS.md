# Verificación de consulta de pedido y pago — US12

Los contratos de producción ya existían: GET /api/pedidos/{id}, GET /api/pedidos/{id}/pagos y GET /api/pedidos/me. Esta entrega añade pruebas, sin modificar controladores, DTO, reglas de pago, base de datos ni configuración de seguridad.

Us12HttpIntegrationTest usa Spring Boot con servidor HTTP, login/JWT reales y H2 aislado. Comprueba:

- Compra histórica: cambiar nombre/precio de la carta no modifica nombrePlatoSnapshot, precioUnitario ni total consultados; el listado retorna solo pedidos del cliente y los metadatos acordados.
- Pago Pendiente sin método/fecha; Pagado con método/referencia/fecha; ausencia de registro de pago con pedido todavía accesible.
- Pedido/pago ajeno e inexistente devuelven el mismo mensaje 404, sin detalles de otra cuenta.

~~~powershell
.\mvnw.cmd '-Dtest=Us12HttpIntegrationTest' test
~~~

Resultado: tres pruebas correctas, sin errores ni omisiones. No usan PostgreSQL/Supabase del equipo ni terminan la aceptación del recorrido de frontend. El frontend feature/cliente-pedido-api implementa las consultas y documenta su uso en GUIA-INTEGRACION-PEDIDOS.md.

Un 404 de pago puede significar pago ausente o pedido inaccesible: el frontend vuelve a consultar el pedido antes de informar ausencia de registro. No agregar un pago ficticio ni cambiar el endpoint para ocultar esa diferencia.
