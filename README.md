# RestoPilot API

Backend modular para autenticación, catálogo, pedidos, pagos administrativos, mesas, reservas, reportes y notificaciones. Java 21, Spring Boot 3.2.4, PostgreSQL y Maven Wrapper.

## Ejecución

Configurar las variables de conexión y JWT a partir de `.env.example`. El puerto predeterminado es 8081.

Antes de utilizar una base existente con esta versión, revisar y ejecutar `docs/sql/actualizacion_tb2.sql`: permite método de pago aún no elegido en registros pendientes, impone un pago por pedido y agrega los campos históricos y notificaciones. No se ejecutó esta migración sobre la base del equipo. Hibernate mantiene `ddl-auto: update`, pero no debe asumirse que elimina automáticamente restricciones NOT NULL antiguas.

```powershell
.\mvnw.cmd spring-boot:run
```

## Validación

```powershell
.\mvnw.cmd '-DforkCount=0' test
```

Las pruebas de integración utilizan H2 en memoria con modo PostgreSQL y un reloj fijo en America/Lima; no modifican la base configurada en `.env`. Mockito usa el generador de mocks por subclases para evitar depender de la conexión de un agente de instrumentación en Windows.

## Sustentación TB2

- `docs/SUSTENTACION_TB2.md`: reglas, componentes del código, cuatro diagramas y guion de explicación.
- `docs/EVIDENCIA_TB2.md`: alcance de las verificaciones y trazabilidad del Sprint Backlog.
- `docs/ALTA_DUENOS_ADMINISTRADORES.md`: US21/US22, contratos, reglas y componentes del nuevo registro.
- `postman/TB2-reglas-negocio.postman_collection.json`: demostración de escenarios válidos y rechazos.
- `postman/US21-US22-altas.postman_collection.json`: alta de dueño/restaurante y de administradores, con casos de rechazo.

US18 registra pagos recibidos por otros medios; no se integra una pasarela. Las notificaciones están disponibles en `/api/notificaciones/me`. La interfaz Angular y el agente IA pertenecen a una fase posterior.
