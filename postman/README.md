# Rutas finales del backend

Base local: `http://localhost:8081`. En Docker de auditoría: `http://localhost:18082`.

Salvo registro y login, las rutas requieren `Authorization: Bearer <token>`. **Gestión** significa administrador o dueño del restaurante propio. Un cliente no puede gestionar catálogo, mesas, personal o estados de pedidos/reservas. Los identificadores se obtienen de las respuestas; no usar IDs de otra corrida.

## Autenticación y personal

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Registrar cliente | `POST /api/auth/register` | Datos válidos; correo duplicado; rol administrativo no permitido; nombre/correo largos; contraseña >72 bytes UTF-8 | `201` con mensaje, token, id y rol CLIENTE; `400` validación, `409` correo duplicado, `403` rol no permitido |
| Iniciar sesión | `POST /api/auth/login` | Credenciales válidas, incorrectas, cuenta deshabilitada o contraseña >72 bytes | `200` con token y datos; `401` credenciales incorrectas; `403` deshabilitada; `400` validación |
| Registrar dueño y restaurante | `POST /api/auth/register-owner` | Alta completa; correo repetido; datos/horario inválidos; intento de elegir rol o restaurante existente | `201` con dueño DUENO, restauranteId y token; `400` validación; `409` duplicado. Si falla la transacción no deben quedar altas parciales |
| Crear administrador | `POST /api/usuarios/administradores` | Dueño crea personal propio; cliente/administrador/otro rol intenta crear; correo repetido; elegir restaurante/rol | `201` con administrador vinculado al dueño, sin contraseña/hash/token del empleado; `400`, `403` o `409` según el caso |

Una contraseña se limita a **72 bytes UTF-8**, no a 72 caracteres. Las cuentas históricas que excedían ese límite requieren tratamiento de restablecimiento; no se truncan contraseñas ni se convierten hashes automáticamente.

## Categorías

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Ver categorías | `GET /api/categorias/restaurante/{restauranteId}` | Restaurante con categorías o sin coincidencias | `200` con array de categorías, que puede ser `[]` |
| Crear categoría | `POST /api/categorias` | Gestión del local propio; nombre vacío, ID previo o restaurante ajeno | `201` con categoría creada; error de validación/negocio o permiso según el caso |
| Editar categoría | `PUT /api/categorias/{id}` | Categoría existente, inexistente o de otro restaurante | `200` con categoría actualizada; `404` inexistente; `403` local ajeno |
| Eliminar categoría | `DELETE /api/categorias/{id}` | Categoría existente, inexistente, ajena o con referencias | `204` sin cuerpo; `404` inexistente; `403` ajena; posible `409` por integridad |

## Carta y platos

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Consultar carta y filtrar categoría | `GET /api/platos?restauranteId={id}&categoriaId={idOpcional}` | Con platos; sin platos; categoría sin coincidencias | `200` con `{mensaje, platos}`. Vacío conserva `platos: []` y mensaje |
| Listado alternativo por restaurante | `GET /api/platos/restaurante/{restauranteId}` | Con o sin platos | `200` con array raíz; no utiliza la envoltura de la ruta anterior |
| Ver plato | `GET /api/platos/{id}` | Existente o inexistente | `200` con plato; `404` si no existe |
| Crear plato | `POST /api/platos` | Gestión propia; precio, complejidad o tiempo inválidos; cliente o local ajeno | `201` con plato; error de negocio o permiso |
| Editar plato | `PUT /api/platos/{id}` | Plato propio; inexistente; local ajeno; datos inválidos | `200` con plato; `404`, `403` o error de negocio |
| Cambiar disponibilidad | `PATCH /api/platos/{id}/disponibilidad?disponible=true` | true/false explícito; parámetro ausente/inválido; plato ajeno | `200` con plato; `400` parámetro inválido/ausente; error de permiso o recurso |
| Eliminar plato | `DELETE /api/platos/{id}` | Plato propio, inexistente, ajeno o con referencias | `204` sin cuerpo; `404`, `403` o posible `409` de integridad |

## Mesas

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Crear mesa | `POST /api/mesas` | Gestión propia; número duplicado; capacidad inválida; módulo físico deshabilitado | `201` con id, datos y mensaje de éxito; `400` regla/validación; `403` rol no permitido |
| Listar mesas propias | `GET /api/mesas` | Con o sin mesas, incluidas inactivas | `200` con array de mesas; requiere gestión |
| Listar mesas activas del restaurante | `GET /api/mesas/restaurante/{restauranteId}` | Con mesas activas, vacío o restaurante inexistente | `200` con array; `404` restaurante inexistente |
| Consultar mesa | `GET /api/mesas/{id}` | Mesa existente o inexistente | `200` con mesa; `404` inexistente |
| Editar mesa | `PUT /api/mesas/{id}` | Cambio válido; número duplicado; reducir capacidad por debajo de una reserva vigente; mesa ajena | `200` con mesa y mensaje; `400` si compromete una reserva o incumple reglas. Se conserva la mesa ante rechazo |
| Activar/desactivar mesa | `PATCH /api/mesas/{id}/estado` | Body `{ "activo": true/false }`; vacío, null o tipo inválido; desactivar con reserva vigente | `200` con mesa; `400` sin valor válido o con compromiso vigente; sin cambios ante rechazo |

Una mesa con reserva PENDIENTE/CONFIRMADA cuyo intervalo no terminó no puede desactivarse ni reducirse por debajo de los comensales comprometidos. La expiración libera ese compromiso. Los cambios se coordinan mediante bloqueo transaccional de mesa.

## Reservas

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Consultar disponibilidad | `GET /api/reservas/disponibilidad?restauranteId={id}&fecha=AAAA-MM-DD&horaInicio=HH:mm&horaFin=HH:mm&numeroComensales=2` | Mesa libre/ocupada; sin capacidad; hora pasada de hoy; horario inválido; restaurante inactivo o módulo apagado | `200` con `{mensaje, mesas}`; ocupadas conservan `disponible:false`; sin opciones muestra mensaje. `400` por fecha/hora/reglas; `404` restaurante inexistente |
| Solicitar reserva | `POST /api/reservas` | Mesa libre; solapamiento; falta de capacidad; mesa inactiva; restaurante distinto; fecha/hora inválida | `201` con reserva PENDIENTE; `400` regla/validación, `404` recurso. Dos altas simultáneas incompatibles no deben dejar dos reservas bloqueantes |
| Confirmar/rechazar solicitud | `PATCH /api/reservas/{id}/estado` | Body `{ "estado": "CONFIRMADA" }` o `{ "estado": "RECHAZADA", "motivo": "..." }`; reserva no pendiente, vencida, ajena o inexistente | `200` con estado y notificación persistida; `400` transición/motivo inválido; `403` restaurante ajeno; `404` inexistente. Confirmar valida mesa, capacidad y horario |
| Modificar reserva | `PUT /api/reservas/{id}` | Reserva propia o gestión del local; nuevo horario ocupado; terminal; llegada registrada | `200` con reserva PENDIENTE y mensaje de éxito; `400` regla. Ante un horario ocupado conserva los datos originales |
| Cancelar reserva | `PATCH /api/reservas/{id}/cancelar` | Motivo opcional; cliente con más de dos horas; exactamente dos horas o menos; terminal o llegada registrada | `200` con CANCELADA; `400` regla. Para cliente exige **más de dos horas**, no dos horas exactas |
| Consultar historial propio | `GET /api/reservas/me` | Con datos o vacío; incluye reservas expiradas/completadas | `200` con `{mensaje, reservas}`; vacío mantiene array y mensaje |
| Listar reservas del restaurante | `GET /api/reservas?fecha=AAAA-MM-DD&estado=CONFIRMADA` | Filtros opcionales; con datos o vacío; requiere gestión | `200` con `{mensaje, reservas}`; `400` filtro mal formado |
| Ver detalle de reserva | `GET /api/reservas/{id}` | Propia; local administrado; reserva ajena; inexistente | `200` con reserva; rechazo de acceso según política de servicio; `404` inexistente |
| Consultar módulo propio | `GET /api/reservas/modulo/estado` | Activado o desactivado; requiere gestión | `200` con restauranteId, habilitado, reservasActivasPendientes y mensaje |
| Consultar módulo de restaurante | `GET /api/reservas/modulo/estado/restaurante/{restauranteId}` | Local físico o virtual; inexistente | `200` con estado del módulo; `404` inexistente |
| Activar/desactivar módulo propio | `PATCH /api/reservas/modulo/estado` | Body `{ "habilitado": true/false }`; `{}`, null o tipo inválido | `200` con estado solicitado; `400` entrada inválida. `{}` no reactiva el módulo |
| Registrar llegada | `PATCH /api/reservas/{id}/llegada` | Administrador/dueño propio; solo CONFIRMADA; antes del límite de 15 minutos; llegada repetida, vencida o local ajeno | `200` con llegadaRegistradaEn y mensaje; `400` si no está confirmada, ya llegó o venció; `403` permiso; `404` inexistente |
| Completar atención manualmente | `PATCH /api/reservas/{id}/completar` | CONFIRMADA con llegada registrada, desde la hora de inicio; sin llegada, antes de inicio, repetida, terminal o local ajeno | `200` con COMPLETADA, llegadaRegistradaEn, completadaEn y mensaje; `400` regla; `403` permiso; `404` inexistente |

Llegada y completar no necesitan body. La llegada anticipada se puede registrar si la reserva está confirmada; completar exige que ya haya comenzado. Registrar llegada confirma asistencia, no finaliza automáticamente la atención.

### Ciclo y tolerancia acordados

- PENDIENTE debe confirmarse antes del inicio; al llegar la hora inicial pasa a EXPIRADA.
- CONFIRMADA sin llegada pasa a EXPIRADA **al cumplirse exactamente 15 minutos** desde el inicio. A los 14 min 59 s todavía admite llegada.
- CONFIRMADA con llegada no expira por inasistencia. Solo personal autorizado la completa manualmente desde la hora de inicio; puede terminar antes de la hora de fin.
- COMPLETADA es terminal: repetir completar devuelve 400, sin otra notificación ni otra atención.
- EXPIRADA conserva historial, expiradaEn y libera mesa; no se elimina físicamente.
- El proceso periódico comprueba vencimientos cada 30 segundos; consulta de disponibilidad y operaciones relevantes también actualizan vencimientos antes de decidir. No es una garantía de notificación en el milisegundo del límite.
- El tiempo de negocio usa Clock en America/Lima. No se expone una ruta para cambiar el reloj.

## Pedidos

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Crear pedido | `POST /api/pedidos` | Cliente; platos válidos; plato agotado o ajeno; lista vacía; entrega inválida | `201` con pedido; `400` reglas/validación; `403` rol no permitido |
| Historial de pedidos | `GET /api/pedidos/me?page=0&size=20` | Con pedidos, vacío o página fuera del rango | `200` con `{mensaje, pedidos, totalElementos, totalPaginas}`; mensaje de vacío depende del total, no solo de la página |
| Detalle/estado de pedido | `GET /api/pedidos/{id}` | Cliente propietario o gestión del local; pedido de otro cliente/local; inexistente | `200` con pedido; `404` ajeno a otro cliente o inexistente; `403` gestión de otro local |
| Cola de cocina | `GET /api/pedidos/pendientes?page=0&size=20` | Pedidos pendientes del restaurante, cola vacía o incorporación de otro pedido | `200` con página (`content`, totales); orden por complejidad, tiempo de preparación, antigüedad e ID |
| Editar pedido | `PUT /api/pedidos/{id}` | Gestión propia; datos/estado permitidos; local ajeno | `200` con pedido; error de negocio, recurso o permiso |
| Cambiar estado | `PATCH /api/pedidos/{id}/estado?nuevoEstado=EN_PREPARACION` | PENDIENTE → EN_PREPARACION → LISTO → ENTREGADO; salto, retroceso, estado desconocido o terminal | `200` con pedido; `400` transición o enum inválido; error de recurso/permiso |
| Cancelar administrativamente | `DELETE /api/pedidos/{id}` | Gestión propia y estado cancelable; estado terminal o local ajeno | `204` sin cuerpo; conserva historial; error de negocio/recurso/permiso |

## Pagos

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Registrar pago | `POST /api/pedidos/{pedidoId}/pagos` | Gestión propia, monto exacto y método; referencia requerida para electrónico; pedido inexistente; pago ya confirmado | `201` con pago; `400` regla/validación; `403` acceso; `404` pedido inexistente; posible `409` por integridad |
| Consultar pago | `GET /api/pedidos/{pedidoId}/pagos` | Cliente propietario o gestión propia; pedido ajeno/inexistente | `200` con pago/estado; error de acceso/recurso |

El pago registra cobros recibidos por otros medios. No es una pasarela. PostgreSQL conserva la FK pago→pedido y unicidad de pedido_id.

## Reportes

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Reporte operativo | `GET /api/reportes/operativos?fechaInicio=AAAA-MM-DD&fechaFin=AAAA-MM-DD&tipo=CONSOLIDADO` | CONSOLIDADO, PLATOS, DEMANDA, ATENCIONES, OCUPACION o PAGOS; rango con/sin datos; otro local; fecha/enum inválido | `200` con indicadores y mensaje. Sin datos: mensaje y métricas null; indicadores no pedidos también null. `400` rango/parámetro inválido; `403` rol no permitido |
| Reporte de pagos | `GET /api/reportes/pagos?fechaInicio=AAAA-MM-DD&fechaFin=AAAA-MM-DD` | Pagos del restaurante propio dentro del rango; rango vacío o inválido | `200` con estadoPagos y mensaje; `400` rango inválido; `403` acceso |

En CONSOLIDADO/ATENCIONES se separan **pedidosAtendidos** (ENTREGADO) y **reservasCompletadas** (COMPLETADA). Una llegada no suma una reserva completada y EXPIRADA no suma atención. `volumenAtenciones` conserva la suma anterior solo por compatibilidad: no representa visitas únicas. El rango de reservas corresponde a su fecha programada; no se atribuye la visita a otro día por la fecha de finalización.

## Notificaciones

| Escenario principal | Ruta | Posibles escenarios | Qué responde |
|---|---|---|---|
| Consultar notificaciones propias | `GET /api/notificaciones/me?page=0&size=20` | Con mensajes o vacío; solo usuario autenticado | `200` con página (`content`, `totalElements`, `totalPages`); no devuelve notificaciones ajenas |

## Errores comunes

- `400`: JSON/parámetros inválidos, validación o regla de negocio.
- `401`: credenciales incorrectas al iniciar sesión.
- `403`: permiso insuficiente; el filtro actual también rechaza con 403 peticiones protegidas sin token o con JWT de cuenta deshabilitada.
- `404`: recurso inexistente; algunas consultas propias lo usan para ocultar recursos ajenos.
- `409`: conflicto de persistencia o modificación concurrente.
- `500`: fallo técnico inesperado con mensaje público genérico e incidente; no debe revelar SQL, clases o trazas.

Las rutas de tablas/carta no acreditan una interfaz visual. No hay endpoints de IA, pasarela, eliminación física de reservas ni control público del reloj.

## Ejecutar la colección principal

`postman_collection.json` incluye los escenarios anteriores, RV01–RV04, el ciclo nuevo y CRUD complementario. Cada carpeta superior prepara cuentas y datos nuevos; las subcarpetas internas necesitan esa preparación. No requiere cuentas demo ni IDs estáticos de otra corrida.

Para las tres carpetas sin fixtures temporales SQL puede usarse Postman/Newman. Por ejemplo:

```powershell
npx.cmd --yes newman@6.2.3 run postman/postman_collection.json --folder "03 Escenarios nuevos RV01-RV04" --env-var baseUrl=http://localhost:18082 --bail
```

Para **todos los escenarios**, incluido COMPLETADA/EXPIRADA, usar el ejecutor local que prepara únicamente reservas creadas en esa misma corrida en restopilot_audit:

```powershell
# Solo si todavía no está instalado localmente:
npm.cmd install --prefix target/postman-runner --no-save newman@6.2.3

# Colección completa:
node scripts/ejecutar-postman-ciclo.cjs

# Solo ciclo con preparación propia:
node scripts/ejecutar-postman-ciclo.cjs --folder "04 Ciclo de reservas COMPLETADA y EXPIRADA"
```

El ejecutor verifica Docker, puerto 18082, conexión a restopilot_audit:55439 y base/usuario antes de usar SQL. No acepta una base compartida ni Supabase. Mueve únicamente el horario de fixtures creados por él para reproducir atención y vencimiento sin esperar hasta mañana; no altera el reloj. No es una ruta del backend. Ejecutar la carpeta 04 directamente sin preparar esos fixtures produce un error explícito y no una prueba verde ficticia.

Los límites exactos inicio±1 segundo y 15 minutos±1 segundo se verifican además con `CicloReservaIntegrationTest`, mediante Clock controlado. El CLI guarda reportes sanitizados en `auditoria/ciclo-reservas-2026-10-17`. No exportar entornos ni reportes crudos con JWT o contraseñas.

Después de actualizar el código, reconstruir la imagen y ejecutar `scripts/migrar-auditoria.ps1` solo sobre la base auditada: incorpora columnas de llegada/finalización/expiración y CHECK con EXPIRADA. El SQL no borra reservas ni convierte asistencias históricas. Para un despliegue fuera de auditoría se debe revisar/adaptar la migración con el equipo; su protección actual rechaza otra base/usuario.

## Verificación de esta actualización

46 rutas documentadas y representadas en la colección; 350 solicitudes guardadas, 354 peticiones HTTP (incluye dos pares simultáneos), 1.049 aserciones sin fallos. Pasaron 69 pruebas Java y 19 escenarios de ciclo sobre PostgreSQL en un esquema efímero, con reloj controlado. Las peticiones HTTP reales se ejecutaron contra el backend Docker de auditoría en 18082.

Evidencia sanitizada: `auditoria/ciclo-reservas-2026-10-17/`. No se declara validación de interfaz, visitas únicas, carga masiva ni todos los escenarios imaginables por estos resultados.
