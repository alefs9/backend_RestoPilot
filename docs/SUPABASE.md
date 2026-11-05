# PostgreSQL de Supabase — RestoPilot

Conexión realizada el 05/11/2026. Rama `chore/supabase-database`. Proyecto `unvxosrwqmcwrtxplrsf`. [Evidencia](evidencias/supabase/resultado.json).

## Dónde ver los datos

En Table Editor, cambiar el selector de esquema de `public` a **`restopilot`**. Las diez tablas están en este esquema privado. `public` está vacío por diseño. También se pueden consultar desde SQL Editor:

```sql
SELECT tablename FROM pg_tables
WHERE schemaname = 'restopilot' ORDER BY tablename;

SELECT id, nombre FROM restopilot.restaurantes;
SELECT id, nombre, precio, disponible FROM restopilot.platos;
```

No habilitar la exposición de `restopilot` en la Data API. El acceso de la aplicación pasa por Spring Boot, que controla roles y pertenencia.

## Configuración

Angular consume `http://localhost:8081/api`. Spring Boot se conecta por JDBC al Session pooler de Supabase (5432), con SSL obligatorio. Se conserva Spring Security/JWT. Las claves API y JWKS de Supabase no sustituyen DB_PASSWORD ni JWT_SECRET y no son necesarias para esta conexión JDBC.

El `.env` local, ignorado por Git, contiene DB_URL con `sslmode=require&currentSchema=restopilot`, DB_USERNAME, DB_PASSWORD y SPRING_PROFILES_ACTIVE=supabase. El secreto JWT anterior no era Base64 válido: se generó uno aleatorio de 32 bytes para permitir el login. El respaldo privado conserva la configuración anterior; no compartir ni versionar estos archivos.

`application-supabase.yml` limita el pool a cinco conexiones, exige SSL, valida el esquema sin modificarlo y desactiva el inicializador Java de muestras. Reiniciar RestoPilotApplication desde IntelliJ después de cambiar `.env`. La instancia del usuario en 8081 se comprobó después de su reinicio.

## SQL entregado

1. `docs/sql/001_esquema_supabase.sql`: esquema privado, diez tablas, secuencias y relaciones. Solo para un destino vacío: falla si ya existe el esquema. No contiene datos ni credenciales.
2. `docs/sql/002_expiracion_supabase.sql`: incluye EXPIRADA en la restricción y crea el índice. La restricción del respaldo local todavía no admitía ese estado.
3. `docs/sql/003_datos_demo.sql`: datos de demostración con IDs generados, referencias reales, precios históricos, totales coherentes y ventana de cancelación de cinco minutos según PedidoService. Fechas relativas a la ejecución con horario de Lima. No contiene contraseñas; requiere un hash BCrypt local.

Para una instalación nueva, ejecutar 001 y 002 en una transacción. La importación realizada conservó además el restaurante y los dos usuarios de la base local. No volver a ejecutar 001 sobre esta base ya cargada.

## Datos de demostración

El seed añade dos restaurantes; dueño, administrador, dos clientes y otro dueño; cuatro categorías, siete platos (uno agotado), siete mesas (una inactiva), siete pedidos de las cuatro modalidades, diez detalles, seis pagos y tres notificaciones.

Hay ocho reservas: pendiente, confirmada, atendiendo con llegada, expirada con fecha, completada con llegada/finalización, rechazada con motivo, cancelada y otra cuenta. La expirada de ayer deja libre su rango; su disponibilidad se consulta al servidor.

Los datos se identifican mediante correos `@restopilot.test` y marcadores `RP-DEMO`. El script conserva los registros existentes, no restablece contraseñas ni estados al repetirse y aborta si un correo reservado pertenece a un registro incompatible. Los accesos generados se entregan en un archivo local fuera de Git. Son cuentas de demostración, no cuentas de producción.

Para ejecutar 003, configurar el hash en la misma sesión:

```sql
BEGIN;
SELECT set_config('restopilot.demo_enabled', 'true', false);
SELECT set_config('restopilot.demo_password_hash', '<HASH_BCRYPT_LOCAL>', false);
-- Ejecutar aquí el contenido de 003_datos_demo.sql.
COMMIT;
```

Sustituir el marcador por un hash BCrypt válido, nunca por una contraseña en texto. No es necesario activar DEMO_ENABLED del backend: corresponde a otro inicializador Java.

## Verificación ejecutada

- Respaldo pg_dump del PostgreSQL local 17.11 fuera de Git, con inventario y SHA-256.
- Importación y dos ejecuciones del seed en una base temporal: sin duplicados y sin diferencias entre totales y detalles.
- Importación a Supabase en una sola transacción, tras comprobar que el destino estaba vacío. Se conservaron los esquemas propios de Supabase.
- Diez tablas en `restopilot`; tres restaurantes y siete usuarios contando los registros originales; EXPIRADA persistida; ningún total inconsistente; `anon` sin acceso de uso al esquema; TLS confirmado en la conexión cliente.
- Hibernate inició con perfil supabase y `validate`. Login por los tres roles y aislamiento entre cuentas/locales: 17 comprobaciones HTTP reales correctas en 8081.
- Angular → Spring Boot/JWT → PostgreSQL Supabase: 22 comprobaciones reales a 390 y 1440. Ejecución final sin respuestas simuladas ni redirección de puertos: carrito (lecturas), pedidos propios, detalle, seguimiento, pago, reservas, expiración/disponibilidad, completada, cocina y categorías.
- El navegador obtiene sesión mediante AuthService desde el harness de desarrollo. Esto no implementa ni acredita el formulario de acceso pendiente del frontend. No se crearon pedidos ni se avanzaron estados durante esas comprobaciones.
- MCP Supabase añadido y autenticado con OAuth. Sus herramientas pueden requerir recargar la sesión; esta importación se realizó mediante PostgreSQL.

No se desplegó la API ni el frontend. Para un despliegue posterior, `apiUrl` debe apuntar a la URL pública de Spring Boot, no a la URL de Supabase.

## Corrección de integridad del Sprint 2

Después de los scripts 001–003, ejecutar `docs/sql/004_cierre_sprint2.sql`. Agrega unicidad de número de mesa por restaurante y ajusta solo los tiempos inválidos de los platos demo. Aborta sin borrar datos si existen duplicados. Aplicado y verificado el 2026-11-05; evidencia en `docs/evidencias/cierre-backend/resultado.json`.
