# Configuración CORS (Cross-Origin Resource Sharing)

En el backend de RestoPilot, la política de CORS está gestionada globalmente a través de Spring Security dentro del archivo `SecurityConfig.java`.

## Entornos
- **Desarrollo:** Se permite el acceso desde `http://localhost:4200` (Angular) o cualquier otro puerto de pruebas que utilice el Frontend.
- **Producción:** Los orígenes permitidos deben ser configurados mediante variables de entorno para apuntar estrictamente al dominio del frontend de RestoPilot SaaS.

## Métodos Permitidos
Se permiten los métodos HTTP estándar necesarios para la API REST: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS`.

*Nota:* Asegurarse de que el header `Authorization` esté habilitado en la configuración de CORS para que el token JWT pueda viajar desde el cliente hacia la API.