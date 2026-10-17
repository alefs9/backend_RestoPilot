# Postman y Newman de RestoPilot

Colección nueva v2.1 con 129 solicitudes, preparación explícita y casos positivos/negativos. No necesita cuentas demo ni IDs fijos. No incluye tokens ni contraseñas reales.

## Ejecución

Importar `postman_collection.json` y, si se desea, `local.postman_environment.json`. baseUrl debe apuntar a un backend LOCAL conectado a la base de auditoría. Predeterminado de colección: http://localhost:8081; entorno de Compose: http://localhost:18082.

```powershell
newman run postman/postman_collection.json --env-var baseUrl=http://localhost:18082 --bail
newman run postman/postman_collection.json --folder '02 Flujos de usuario' --env-var baseUrl=http://localhost:18082 --bail
```

Newman 6.2.3 se usó para la validación. `--bail` conserva el primer fallo real y evita continuar con fixtures inválidos. Cada carpeta superior incluye su preparación; ejecutar una carpeta interior aislada requiere preparar antes sus datos.

El prerequisito del primer registro genera correos, contraseña sintética e identificador de corrida nuevos. Calcula hoy y mañana para America/Lima sin Intl. `auditDate` es un override opcional de entorno únicamente para un backend cuyo reloj de pruebas esté controlado; dejarlo vacío en ejecución normal. No modifica el reloj del sistema.

Los fixtures de restaurante atienden 00:00–23:59:59 para que el pedido de demostración pueda crearse a distintas horas. Las reservas se programan para mañana. Se preparan dos dueños, un administrador, dos clientes, dos categorías y dos mesas; los tokens y los IDs se capturan desde las respuestas. Las cuentas y operaciones permanecen en la base aislada. Repetir no depende de limpiar datos.

## Contratos utilizados

- GET /api/platos: `{mensaje, platos}`. GET /api/platos/restaurante/{id} conserva array raíz.
- Disponibilidad: `{mensaje, mesas}`; mesas ocupadas conservan `disponible:false`.
- GET /api/pedidos/me: `{mensaje, pedidos, totalElementos, totalPaginas}`. Admite page/size.
- GET /api/reservas/me y listado administrativo: `{mensaje, reservas}`.
- Cola de cocina y notificaciones: paginación con `content` y `totalElements`.
- POST/PUT mesa y PUT reserva: datos originales con `id` en la raíz y `mensaje` de éxito adicional. No se introdujo una envoltura `mesa` o `reserva`.
- Rechazo de reserva: enviar `{estado:"RECHAZADA", motivo:"..."}`; `motivoRechazo` es un campo de respuesta, no de esa solicitud.

La colección comprueba códigos esperados, contratos, aislamiento entre usuarios/restaurantes, vacíos, transiciones, pagos y mensajes. Un 400/401/403/404/409 esperado es una prueba positiva de rechazo. No suprimir aserciones para obtener verde.

## Límites

JWT tras deshabilitación, fallos de rollback inyectados, reloj exacto y restricciones/concurrencia PostgreSQL requieren pruebas Java/SQL complementarias. No existe una ruta pública nueva para deshabilitar cuentas. No se agregaron endpoints de prueba para poder verificarlas desde Postman.

Un reporte Newman completo puede contener contraseñas sintéticas, variables y JWT de la corrida. No publicar exportaciones de entorno, globals ni reportes crudos. El paquete solo incluye resúmenes sanitizados sin cuerpos ni cabeceras de autenticación.