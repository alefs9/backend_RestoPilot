# US15 — Contrato de consulta de cocina

Rama: feature/operacion-cocina-api. Cambio aditivo en PedidoResponseDTO: complejidadMaxima (ALTA/MEDIA/BAJA/null) y tiempoPreparacionMaximoMinutos (entero no negativo/null). Se devuelven tanto en cola como en detalle y en las demás respuestas del DTO, sin retirar campos existentes.

PedidoPrioridad comparte la regla entre el mapper y el orden de la cola. Máxima complejidad y máximo tiempo se calculan independientemente entre platos; no se suman ni multiplican por cantidad. Valores de complejidad desconocidos y tiempos ausentes se muestran como null; cero es válido y tiempos negativos inválidos se descartan. Los desconocidos se ordenan con rango/tiempo cero. No se alteran nombres, precios ni subtotales históricos.

Se conservan GET /api/pedidos/pendientes y GET /api/pedidos/{id}. La cola usa Page Spring, incluye únicamente PENDIENTE/EN_PREPARACION, ordena globalmente antes de paginar y obtiene el restaurante del usuario autenticado. ADMINISTRADOR y DUENO gestionan solo su local. No se añaden endpoints, cambios de seguridad ni migraciones. US19 sigue pendiente en frontend; no se cambia su contrato de transición.

Pruebas: Us15HttpIntegrationTest, 3 escenarios HTTP con JWT/H2 aislado, correctos. Regresión: PedidoServiceTest (3), Us11HttpIntegrationTest (1), Us12HttpIntegrationTest (3), Us13HttpIntegrationTest (3), correctos. Sin autenticar, la seguridad actual responde 403: no se cambió a 401 para satisfacer una suposición de prueba.

Pendiente: desplegar/integrar ambos commits en el entorno compartido y aceptación del equipo con su base PostgreSQL. No se usaron credenciales ni pedidos del equipo; estas pruebas no acreditan el despliegue.
