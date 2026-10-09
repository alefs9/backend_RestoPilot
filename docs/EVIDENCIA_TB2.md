# Evidencia de correcciones del backend TB2

Fecha de verificación: 7 de octubre de 2026. Se corrigieron los pendientes identificados al contrastar el Sprint Backlog con el código local. El alcance es la implementación del backend; Angular, IA y pasarela de pagos quedan fuera del Sprint 1. US18 conserva pagos administrativos.

## Resultado verificado de las correcciones iniciales

Comando ejecutado desde el repositorio:

```powershell
.\mvnw.cmd '-DforkCount=0' test
```

Resultado: **BUILD SUCCESS — 25 pruebas, 0 fallos, 0 errores, 0 omitidas**.

- 3 pruebas unitarias en `PedidoServiceTest`.
- 22 pruebas de integración en `ReglasNegocioIntegrationTest`.
- Una prueba de integración reproduce las **27 peticiones HTTP** de la colección de Postman y verifica sus códigos de respuesta. No ejecuta el JavaScript de Postman; los valores de preparación se suministran desde el test. Los indicadores y reglas se comprueban adicionalmente en las otras pruebas.
- Spring Boot cargó los repositorios JPA y validó las consultas JPQL contra H2 temporal en modo PostgreSQL.
- MapStruct genera el detalle con platoId y precioUnitario; la dirección también se devuelve. Ya no aparecen los avisos de campos sin mapear del pedido.

Los resultados XML completos quedan en `target/surefire-reports/` tras ejecutar las pruebas. Esta carpeta es generada y no reemplaza la evidencia de commits del informe. Las pruebas utilizaron un reloj fijo y no se conectaron a la base PostgreSQL del equipo.

## Trazabilidad del Sprint Backlog

El siguiente estado se refiere a la tarea backend y su código local. Los commits de estas correcciones deben registrarse cuando el equipo los cree; no se generaron ni inventaron hashes.

| Tarea | Implementación o corrección | Evidencia |
|---|---|---|
| TSK-001/002/003 | Usuario, roles, registro, cifrado y JWT existentes; el autorregistro ya no permite roles privilegiados. | Pruebas HTTP de registro de cliente, login y rechazo de autorregistro administrativo. |
| TSK-004/005 | Un pago por pedido; pendiente al crear; importe exacto; confirmación inmutable; permisos por restaurante; referencia electrónica. | Pruebas de importe incorrecto, confirmación, duplicado, referencia, propiedad y pedido pagado no editable. |
| TSK-006 | Entidades y repositorios de categoría y plato existentes. | Persistencia real de carta en H2 y colección HTTP. |
| TSK-007 | GET /api/platos con restauranteId y categoriaId opcional. | Prueba de filtro y categoría sin platos; petición 06 de Postman. |
| TSK-008/009 | Gestión de platos y disponibilidad existentes; se validan complejidad, tiempo y pertenencia de categoría. Se controla rol y restaurante. | Prueba de complejidad/rango; creación de dos platos y rechazo de gestión por cliente vía HTTP. |
| TSK-010/011 | Consultas JPQL reales y reportes consolidados/específicos; rango inclusivo, período vacío y separación por restaurante. | Pruebas de métricas, ocupación 8,33 %, pagos, período vacío, tipo específico y rango inválido. |
| TSK-012 | Mesa con número, capacidad y coordenadas. | Persistencia de mesa en las pruebas y petición 19 de Postman. |
| TSK-013 | Disponibilidad por horario y capacidad; coordenadas e indicador disponible. | Prueba de capacidad, solapamiento y períodos adyacentes; petición 24. |
| TSK-014 | Registro de solicitudes PENDIENTE; protección de solapamientos con bloqueo de mesa. | Pruebas de reserva y rechazo de horario/capacidad; petición 20. |
| TSK-015 | Modificación con validaciones existentes; rechazo de reservas RECHAZADA y retorno a PENDIENTE al reprogramar. | Prueba de modificación bloqueada tras rechazo. |
| TSK-016 | Cancelación del cliente exige más de dos horas. | Prueba del límite exacto de dos horas y cancelación permitida a tres horas. |
| TSK-017 | PATCH /api/reservas/{id}/estado confirma o rechaza; motivo obligatorio; estado previo y propietario; notificación persistida. | Pruebas de confirmación, rechazo, segunda decisión y administrador ajeno; peticiones 21–25. |
| TSK-018 | Activación/desactivación de reservas existente; las reglas respetan atención física. | Prueba de registro bloqueado sin atención física. |
| TSK-019/020 | Pedido y detalles existentes; modalidad, dirección, productos del local, precio histórico y pago pendiente. | Pruebas de persistencia del pedido y solicitudes HTTP 08/09. |
| TSK-021 | Historial paginado propio existente. | Implementación findByClienteId; el alcance no incluye pantalla de historial. |
| TSK-022/023 | Detalle y estado con propiedad del cliente o restaurante; campos completos y precios históricos. | Prueba de cambio del precio de carta y de consulta de pedido ajeno. |
| TSK-024 | Secuencia PENDIENTE → EN_PREPARACION → LISTO → ENTREGADO y notificación. | Prueba de salto inválido, secuencia válida y prohibición de reabrir; peticiones 12–15. |
| TSK-025 | Prioridad complejidad → tiempo → antigüedad; ID como desempate final. | Prueba de cuatro pedidos y página fuera de rango; petición 10. |

Las tareas corregidas pueden registrarse como Done para su alcance backend con estas evidencias. Esto no declara completadas sus pantallas ni todas las historias de extremo a extremo.

## Ejecución de la demostración en PostgreSQL

1. Revisar `docs/sql/actualizacion_tb2.sql` y ejecutarlo en la base de demostración antes de arrancar esta versión sobre un esquema existente. La migración no se ejecutó durante este trabajo. Si encuentra pagos duplicados u huérfanos, se detiene para revisión; no borra registros.
2. Iniciar el backend con `.\mvnw.cmd spring-boot:run` y las variables habituales de la base de demostración.
3. Importar `postman/TB2-reglas-negocio.postman_collection.json` en Postman.
4. Configurar baseUrl, restauranteId y credenciales del administrador de ese restaurante. Los valores de ejemplo coinciden con DataInitializer.
5. Comprobar que el restaurante esté abierto y habilite delivery y atención física. Las peticiones de pedido respetan esas reglas; no deben deshabilitarse para lograr un resultado verde.
6. Ejecutar en orden mediante Collection Runner. Se crean datos de prueba y se conservan para el historial; cada ejecución genera nombres y correo nuevos.
7. Guardar las capturas del resultado del Runner, de ejemplos válidos y de los rechazos para incorporarlas a Development Evidence.

La consulta de prioridad solicita hasta 1000 registros. Si la base contiene una cola mayor, preparar un restaurante de demostración con menos pedidos para que ambos ejemplos estén en la página consultada.

## Límites de esta evidencia

No se ejecutó una verificación sobre PostgreSQL, una migración sobre la base compartida ni una prueba de carga concurrente. Los bloqueos transaccionales y el control de versión están implementados, pero no se presentan como resultados de una prueba de concurrencia. No se creó frontend, integración de IA ni pasarela. Los reportes usan las fórmulas documentadas en SUSTENTACION_TB2.md y la ocupación mide reservas, no asistencia física comprobada.

## Cambios de contrato para el equipo

- Las cuentas administrativas se provisionan fuera del registro público; el endpoint register solo crea clientes.
- Los platos nuevos requieren complejidad BAJA/MEDIA/ALTA y tiempo entre 5 y 15 minutos.
- ReservaResponseDTO incorpora motivoRechazo; EstadoReserva incorpora RECHAZADA.
- PedidoResponseDTO conserva sus campos y ahora devuelve dirección y precio histórico correctamente.
- DELETE /api/pedidos/{id} cancela un pedido pendiente y no pagado conservando el registro, en lugar de borrarlo físicamente.
- Un pago pendiente puede no tener método elegido; confirmar un pago exige método y, si es electrónico, referencia.
- Las notificaciones son mensajes persistidos consultables por el cliente; no son correos, SMS ni actualizaciones push del frontend.

Estos cambios deben comunicarse al equipo que implemente las pantallas para que consuma el contrato actualizado.

## Incremento US21 y US22

Después del commit inicial 8ab73ca se incorporaron el alta transaccional del dueño con su restaurante y la creación de administradores autorizada por ese dueño. Los detalles están en ALTA_DUENOS_ADMINISTRADORES.md.

La suite completa se volvió a ejecutar el 7 de octubre de 2026: **39 pruebas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**. Incluye las 25 pruebas anteriores y 14 de AltaPersonalIntegrationTest. Estas últimas comprueban altas válidas, correos duplicados, campos inválidos, horario, asignaciones prohibidas, reversión real de operaciones fallidas, login del administrador y aislamiento por restaurante. Se utilizó H2 temporal, sin modificar PostgreSQL.

Los archivos del segundo incremento están preparados para un commit separado; el hash de ese segundo commit debe añadirse después de crearlo. No se creó automáticamente un commit del nuevo flujo.
