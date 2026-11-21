# RestoPilot API

Backend modular para autenticación, catálogo, pedidos, pagos administrativos, mesas, reservas, reportes y notificaciones. Java 21, Spring Boot 3.2.4, PostgreSQL y Maven Wrapper.

## Ejecución

Configurar las variables de conexión y JWT a partir de `.env.example`. El puerto predeterminado es 8081.

Antes de utilizar una base existente con esta versión, revisar y ejecutar `docs/sql/actualizacion_tb2.sql`: permite método de pago aún no elegido en registros pendientes, impone un pago por pedido y agrega los campos históricos y notificaciones. No se ejecutó esta migración sobre la base del equipo. Hibernate mantiene `ddl-auto: update`, pero no debe asumirse que elimina automáticamente restricciones NOT NULL antiguas.

```powershell
.\mvnw.cmd spring-boot:run
```

## Gemini (Spring AI)

La integración con Gemini Developer API está deshabilitada por defecto. Para activarla en el backend, configura estas variables en el entorno de ejecución o en el archivo local `.env` ignorado por Git:

| Variable | Valor |
| --- | --- |
| `RESTOPILOT_AI_CHAT_PROVIDER` | `google-genai` |
| `GEMINI_API_KEY` | Clave de Gemini Developer API |
| `GEMINI_MODEL` | Opcional. El valor predeterminado es `gemini-3.1-flash-lite`. |

El endpoint inicial es `POST /api/ia/admin/chat` y requiere un token JWT de
`DUENO` o `ADMINISTRADOR`. Envía el token en `Authorization: Bearer <token>`.
Si se omite `restauranteId`, se usa el restaurante del usuario autenticado.

```json
{"mensaje":"¿Qué tipos de reportes ofrece RestoPilot?","restauranteId":1}
```

```json
{"estado":"ANSWER","mensaje":"RestoPilot ofrece reportes administrativos."}
```

Con JWT legado, el acceso anónimo se rechaza con HTTP 403. El acceso a un
restaurante ajeno también devuelve 403. Si la IA está deshabilitada, el
endpoint devuelve HTTP 503 con `codigo: AI_UNAVAILABLE`. Un fallo del
proveedor devuelve HTTP 502 con `codigo: AI_PROVIDER_FAILURE` y un mensaje
genérico.

El chat administrativo ya consulta **platos más vendidos** y **pagos por
período**. Por ejemplo, envía «¿Cuáles fueron los platos más vendidos en
octubre de 2026?» o «¿Cuánto cobramos del 1 al 31 de octubre de 2026?».
El campo `datos` incluye el tipo y las fechas inclusivas, junto con
`platosMasVendidos` en el formato del reporte operativo o `estadoPagos`
en PEN. El reporte de pagos usa el mismo filtro por fecha de creación del
pedido que la vista operativa.

Si el período falta o es inválido, el chat puede devolver
`CLARIFICATION_REQUIRED`. En ese caso, vuelve a enviar la pregunta completa
con las fechas. `NO_DATA` identifica un reporte válido sin registros.
Para añadir atenciones y horarios de mayor demanda, reutiliza
`HerramientasReportes` y amplía los datos del contrato con los campos del
reporte correspondiente.


## Validación

```powershell
.\mvnw.cmd '-DforkCount=0' test
```

Las pruebas de integración utilizan H2 en memoria con modo PostgreSQL y un reloj fijo en America/Lima; no modifican la base configurada en `.env`. Mockito usa el generador de mocks por subclases para evitar depender de la conexión de un agente de instrumentación en Windows.

## Sustentación TB2

- `docs/SUSTENTACION_TB2.md`: reglas, componentes del código, cuatro diagramas y guion de explicación.
- `docs/EVIDENCIA_TB2.md`: alcance de las verificaciones y trazabilidad del Sprint Backlog.
- `docs/ALTA_DUENOS_ADMINISTRADORES.md`: US21/US22, contratos, reglas y componentes del nuevo registro.
- `postman/postman_collection.json`: colección única con API por funcionalidad (incluidas US21/US22) y recorrido por etapas de dueño y cliente. Ver `postman/README.md` para estructura y ejecución.

US18 registra pagos recibidos por otros medios; no se integra una pasarela. Las notificaciones están disponibles en `/api/notificaciones/me`. La interfaz Angular y el agente IA pertenecen a una fase posterior.

## Cierre del Sprint 2 sin IA

Ver [cobertura y resultados del backend](docs/CIERRE-BACKEND-SPRINT2.md). Documentación de la API en `/swagger-ui/index.html` y `/v3/api-docs`.
