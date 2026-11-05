# Consulta de restaurantes para US11

Rama: feature/restaurantes-consulta. Cambio de lectura necesario para que el frontend reciba restaurante, horario y modalidades reales, sin construirlos desde muestras.

## Contrato

GET /api/restaurantes devuelve una lista de restaurantes activos ordenada por ID.
GET /api/restaurantes/{id} devuelve el detalle solicitado; un ID inexistente devuelve 404 con el manejador de errores existente.

Las consultas requieren autenticación y un rol CLIENTE, ADMINISTRADOR o DUENO. No se habilitan rutas públicas ni se modifica CORS. La respuesta contiene únicamente id, nombre, direccion, activo, abierto, tieneAtencionFisica, aceptaDelivery, horaApertura y horaCierre.

abierto se calcula usando el Clock del negocio y la misma ventana [apertura,cierre) de PedidoService; un restaurante inactivo no está abierto. Si falta un extremo del horario, se conserva el comportamiento actual de registro de pedidos, sin agregar una regla distinta desde esta consulta. La lectura no garantiza que el restaurante siga abierto cuando se envíe el pedido: crearPedido vuelve a validar.

No modifica entidades, esquema, credenciales, configuración de despliegue, pagos ni el contrato de POST /api/pedidos. AuthController, PlatoController y MesaController se reutilizan sin cambios.

## Verificación

Ejecutar:

~~~powershell
.\mvnw.cmd '-Dtest=ConsultaRestauranteServiceTest,Us11HttpIntegrationTest' test
~~~

Se ejecutaron cuatro pruebas unitarias de consulta/horarios y una prueba HTTP real sobre Spring Boot y H2 aislado. Esta última obtiene JWT desde login, consulta restaurantes/platos/mesas, registra pedidos SALON/LLEVAR/RECOGER/DELIVERY, comprueba importe/snapshot/pago inicial Pendiente y rechazos por mesa faltante y plato agotado. La consulta sin autenticación y el ID inexistente también se comprueban.

Las pruebas no utilizan la base PostgreSQL/Supabase del equipo y no certifican ese entorno. No se cargaron datos de muestra en una base real ni se realizó despliegue.

## Coordinación con frontend

La rama frontend feature/cliente-pedido-api contiene CatalogoService y CarritoStore.prepareSelection para consumir estas consultas y los endpoints existentes. Los componentes de acceso y carta conservan sus tareas; deben reutilizar AuthService y estos contratos, evitando otra sesión o IDs inventados.

La guía GUIA-INTEGRACION-PEDIDOS.md del frontend explica la conexión de componentes, las pruebas simuladas y los pendientes de US12/US13. Este cambio no declara terminadas las tareas de esas pantallas ni reasigna responsables.
