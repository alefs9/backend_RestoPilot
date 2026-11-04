# US23 — Contratos y validación del backend

04/11/2026. Rama feature/us23-categorias-api, base origin/develop 734bc95. No se modifican endpoints, esquema ni configuración de producción.

## Cambios

CategoriaService valida nombres no vacíos y de hasta 100 caracteres tanto en alta como en edición. Quita espacios exteriores. Conserva el contrato PUT parcial: omisión/null no cambia un campo; descripción vacía lo limpia. El alta copia solo campos editables a una nueva entidad y no acepta identidad ni timestamps controlados por el cliente.

Eliminar verifica platos asociados mediante existsByCategoriaId y devuelve 409 con mensaje específico. Conserva las claves foráneas como protección definitiva ante asociaciones concurrentes. No agrega cascada ni modifica platos. Los permisos existentes de ADMINISTRADOR/DUENO y pertenencia al restaurante se mantienen mediante AccesoRestaurante.

## Contratos verificados

- POST /api/auth/login: 200 con JWT real en las pruebas aisladas.
- GET /api/categorias/restaurante/{restauranteId}: 200 con lista; accesible a usuarios autenticados, incluido CLIENTE para la carta.
- POST /api/categorias: 201; restauranteId/nombre/descripcion.
- PUT /api/categorias/{id}: 200; nombre y/o descripcion; no cambia restaurante.
- DELETE /api/categorias/{id}: 204; 409 ante asociaciones.
- Validación 400, gestión ajena/rol no autorizado 403, identidad inexistente 404.

No hay GET individual: la edición Angular puede seleccionar la categoría del listado autorizado. No se añaden endpoints equivalentes.

## Evidencia ejecutada

Java 21 y Maven wrapper, sin modificar dependencias.

1. `mvnw.cmd -DforkCount=0 -Dtest=Us23HttpIntegrationTest test`: ocho casos iniciales correctos.
2. `mvnw.cmd -DforkCount=0 test`: 113 pruebas, cero fallos/errores/omitidas, BUILD SUCCESS. Incluye las regresiones de pedidos, cocina, reservas y los ocho casos US23.
3. Se añadió comprobación de CORS con java.net.http.HttpClient real y se repitió `mvnw.cmd -DforkCount=0 -Dtest=Us23HttpIntegrationTest test`: nueve pruebas, cero fallos/errores/omitidas, BUILD SUCCESS.

Cobertura US23: ciclo crear/listar/editar/eliminar para administrador y dueño; nombre vacío/blanco/excesivo; límite 100 y actualización parcial; categoría con plato conserva ambos; gestión ajena bloqueada incluso con restauranteId falsificado en PUT; cliente puede consultar pero no escribir; acceso sin sesión rechazado; inexistentes 404; alta con identidad rechazada y timestamps del servidor; preflight desde http://localhost:4200 permite GET/POST/PUT/DELETE y Authorization.

Pruebas de integración ejecutadas contra servidor Spring Boot de puerto aleatorio, login BCrypt/JWT real y JPA/H2 en memoria `us23http` aislada, con MODE=PostgreSQL. Datos de prueba creados exclusivamente en esa instancia efímera; no se reutilizaron cuentas o bases del equipo. Las credenciales de fixtures no son cuentas de desarrollo ni producción.

## Limitaciones

H2 en modo PostgreSQL **no certifica PostgreSQL**. Solo se localizaron ejemplos de .env y ninguna variable de conexión autorizada. Falta configuración local de desarrollo proporcionada por el responsable del backend (DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET), fuera del control de versiones y sin pegar secretos en el chat. No se cambió application.yml, no se ejecutaron migraciones ni se borraron datos existentes.

No se ejecutó recorrido de navegador US23: las cuatro interfaces y modal siguen pendientes por indisponibilidad del contexto de Figma MCP en la conversación. Las pruebas de CORS son HTTP reales, no una prueba de navegador. El estado global de US23 permanece INCOMPLETA; el complemento del frontend detalla la integración pendiente y las 17 pruebas simuladas del store/servicio.
