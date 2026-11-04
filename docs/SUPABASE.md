# Conexion de Supabase con Spring Boot

El flujo es Angular -> API Spring Boot -> PostgreSQL de Supabase. El login y los
JWT siguen siendo del backend; no se migro a Supabase Auth. El frontend usa
http://localhost:8081/api y el token individual del login. DB_PASSWORD,
JWT_SECRET y claves privadas permanecen exclusivamente en el backend.

## Configuracion local

La conexion esta configurada en .env, ignorado por Git. Se conserva el JWT_SECRET
existente. .env.supabase.example documenta los nombres sin incluir secretos.

- Proyecto: kjafcppdrvfndhjlqkxn.
- Session pooler: aws-0-us-west-2.pooler.supabase.com:5432/postgres.
- Usuario: postgres.kjafcppdrvfndhjlqkxn.
- DB_URL contiene sslmode=require; la password se guarda aparte en DB_PASSWORD.
- SPRING_PROFILES_ACTIVE=supabase activa application-supabase.yml.

El perfil exige SSL, limita Hikari a cinco conexiones, desactiva muestras y SQL
en logs, y valida el esquema sin modificarlo. Para arrancar explicitamente:

```powershell
.\mvnw.cmd '-Dspring-boot.run.profiles=supabase' spring-boot:run
```

Para otra maquina, completar su .env desde Connect > Session pooler > JDBC.
No usar el pooler de transacciones (6543) como datasource de Hibernate.
No imprimir ni versionar .env. Al cambiar la password en Supabase, actualizar
DB_PASSWORD localmente y reiniciar el backend. El MCP no recupera esa password.

## Migracion aplicada

Se aplico por MCP restopilot_esquema_inicial_backend, cuyo SQL esta en
sql/supabase_esquema_inicial_20261103.sql, a partir de las entidades de develop.
Completa categorias, platos, mesas, pedidos, detalles_pedido, reservas y
notificaciones; permite metodo de pago pendiente nulo y exige un pago por pedido
con referencia valida. Incluye EXPIRADA en el CHECK de reservas.

El primer intento se revirtio por un pago sin pedido. Tras autorizacion explicita
del usuario, se elimino unicamente pagos.id=1, pedido_id=1, estado=PAGADO, declarado
de prueba. El segundo intento se aplico correctamente. Se conservaron los dos
usuarios existentes. No se insertaron cuentas, pedidos ni restaurantes de muestra.
El SQL es una migracion para el estado inicial inspeccionado, no debe reejecutarse
sobre las tablas ya creadas. La eliminacion autorizada no forma parte de ese SQL.

## Acceso por Data API pendiente

Las siete tablas nuevas tienen RLS habilitado sin politicas para anon/authenticated.
Spring Boot accede mediante su usuario PostgreSQL y aplica sus permisos existentes.
pagos, restaurantes y usuarios siguen con RLS desactivado; no se autoaplico el cambio
sobre esas tablas. Revisar y autorizar la siguiente proteccion antes de habilitar
clientes directos. RLS sin politicas bloqueara el acceso con claves publicas:

```sql
ALTER TABLE public.pagos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurantes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.usuarios ENABLE ROW LEVEL SECURITY;
```

Los JWT actuales no establecen una identidad de Supabase Auth. No agregar politicas
permisivas para aceptar esos JWT ni entregar credenciales privadas al navegador.

## Evidencia de verificacion

- Conexion JDBC real por Session pooler con sslmode=require, comprobada en lectura.
- 10 tablas confirmadas por MCP tras la migracion; pagos queda vacia.
- Arranque real de Spring Boot, perfil supabase, en 8081; Hibernate valido el esquema.
- HTTP local: POST /api/auth/login vacio -> 400; correo inexistente -> 401;
  GET /api/pedidos/me sin token -> 403; OPTIONS /api/auth/login desde
  http://localhost:4200 -> 200, con el origen esperado.
- No se crearon cuentas para estas verificaciones. Queda pendiente login con una
  cuenta real y la aceptacion del recorrido desde las pantallas del frontend.
- En la verificacion previa pasaron 69 pruebas del backend con H2 y 3 pruebas de
  infraestructura HTTP del frontend. Esas pruebas aisladas no usan Supabase.

Referencia: https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot

## Verificacion antes del push (2026-11-04)

La rama feature/supabase parte de origin/develop actualizado. Tras regenerar los
mappers con Maven clean, la suite completo 105 pruebas, sin fallos ni errores,
con BUILD SUCCESS. Usar perfil test explicitamente evita heredar el perfil
supabase de .env durante las pruebas aisladas:

```powershell
.\mvnw.cmd clean '-DforkCount=0' '-Dspring.profiles.active=test' test
```
