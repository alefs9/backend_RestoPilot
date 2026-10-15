# Colección Postman unificada

Importar `postman_collection.json`. Configurar `baseUrl` (por defecto `http://localhost:8081`).

## Estructura

**01 API por funcionalidad** conserva las historias de usuario originales e incluye US21 y US22 con altas, validaciones y permisos. La API original requiere cuentas de demostración; US21/US22 genera sus propios datos y usa variables `altas_`.

**02 Flujos de usuario** representa un recorrido compartido dividido por actor y momento:

1. **01 Dueño - Abrir y preparar el negocio** (6 peticiones): registrar dueño/restaurante, login del dueño, incorporar administrador, crear categoría, publicar plato y crear mesa.
2. **02 Cliente - Reservar y realizar un pedido** (8 peticiones): registro, login, carta, disponibilidad, solicitud de reserva, pedido delivery, consulta de pago pendiente e historial.
3. **03 Dueño - Atender y registrar el cobro** (7 peticiones): login del dueño, confirmar la reserva, preparar pedido, marcarlo listo, entregarlo, registrar pago recibido y revisar reporte.
4. **04 Cliente - Consultar el resultado de la atención** (5 peticiones): login del cliente, consultar reserva confirmada, pago confirmado, pedido entregado y notificaciones.

## Ejecución y evidencias

Ejecutar `02 Flujos de usuario` completo en Collection Runner, con una iteración y el orden original, o ejecutar sus cuatro etapas sucesivamente. Las etapas dependen de los datos generados por las anteriores: no son recorridos independientes. Para repetir la demostración, comenzar por la etapa 01.

La etapa 01 inicializa las variables `flujo_` y crea un restaurante propio. Las acciones de cada etapa usan exclusivamente el rol indicado: el cliente no crea restaurantes, administra la carta, cambia estados del pedido ni registra cobros. El dueño no se registra como cliente ni realiza pedidos. El administrador incorporado se usa como evidencia de US22; no participa en la atención de este recorrido.

Los scripts comprueban estados HTTP y resultados del negocio, y guardan tokens e identificadores para el paso siguiente. Capturar el resumen del Runner y ejemplos individuales: registro del dueño en etapa 01, creación del pedido en etapa 02 y consulta del pedido entregado en etapa 04. Los totales de aserciones se toman de la ejecución real.

Usar una base de demostración: los registros se conservan. El restaurante acepta delivery y abre de 00:00 a 23:59 America/Lima; evitar el último minuto del día. La reserva se genera para mañana de 15:00 a 16:00. La API por funcionalidad conserva sus variables y scripts originales.
