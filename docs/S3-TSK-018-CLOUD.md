# S3-TSK-018: verificacion cloud sin control de pantalla

Rama: test/s3-tsk-018-cloud. Servicio: https://backend-restopilot.onrender.com.

Tras el despliegue del commit ef4915e se repitieron las 14 comprobaciones HTTP: todas aprobadas. GET /health/ready devuelve 200/UP. Autenticacion real cliente/dueno, perfiles de PostgreSQL, consultas propias, Gemini con reporte PAGOS y periodo exacto 2026-11-01/2026-11-30, roles, token invalido y aislamiento de restaurante respondieron conforme al contrato.

La ejecucion anterior habia detectado 401 en readiness; ese resultado quedo resuelto al actualizar el despliegue. La evidencia adjunta corresponde a la repeticion aprobada.

La prueba real utilizo cliente y dueno. No habia credenciales de administrador en esta ejecucion; ese rol se cubrio con pruebas controladas del frontend y del backend, sin afirmar aceptacion real cloud de una tercera cuenta.

## Repetir

Python 3, sin dependencias. Usar un JSON privado fuera del repositorio con supabaseUrl, publishableKey y accounts. accounts contiene client/owner (y opcional admin), cada uno con email/password. Solo admite los destinos Render y Supabase autorizados; no registra tokens ni mensajes personales.

python scripts/cloud_smoke.py --accounts RUTA_PRIVADA --output artifacts/cloud-smoke/results.json

No se crearon, editaron ni cancelaron pedidos/reservas ni se registraron pagos. Se iniciaron sesiones de prueba y se efectuaron consultas de lectura. La evidencia versionable contiene solo nombre de prueba, estado HTTP, duracion y resultado.
