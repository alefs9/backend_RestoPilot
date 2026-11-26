# S3-TSK-017: backend en Render

Rama: ops/s3-tsk-017-render. El servicio existente es https://backend-restopilot.onrender.com.

Render usa Dockerfile, Java 21 y PORT inyectado por la plataforma. render.yaml declara la configuracion reproducible sin secretos. No crear otro servicio si ya existe: aplicar los mismos parametros al servicio actual.

Perfiles necesarios: supabase,supabase-api,cloud. El primero configura PostgreSQL sin cambios de esquema; el segundo valida JWT Supabase y obtiene permisos desde los usuarios vinculados; cloud limita errores publicos y desactiva documentacion API publica.

DB_URL debe usar el pooler Session con sslmode=require y currentSchema=restopilot. DB_USERNAME y DB_PASSWORD deben pertenecer al mismo proyecto que SUPABASE_AUTH_ISSUER y SUPABASE_PUBLISHABLE_KEY. JWT_SECRET se conserva para compatibilidad interna. GEMINI_API_KEY se guarda solo en Render. No copiar el .env local al repositorio ni mostrar secretos en evidencias.

Health check: GET /health/ready devuelve solo UP/DOWN y valida conexion con PostgreSQL. No llama a Gemini ni expone claves; las pruebas del proveedor pertenecen a S3-TSK-018.

Actualizar la imagen con el commit de esta rama y conservar la imagen anterior para rollback. El plan Free puede dormir con inactividad; comprobar el arranque antes de evaluar tiempos de respuesta. La publicacion frontend pertenece a S3-TSK-019.
