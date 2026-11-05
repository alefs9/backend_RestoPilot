-- Ejecutar en transacción después del esquema. No modifica los estados guardados.
ALTER TABLE restopilot.reservas DROP CONSTRAINT IF EXISTS reservas_estado_check;
ALTER TABLE restopilot.reservas ADD CONSTRAINT reservas_estado_check
 CHECK (estado IN ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELADA','COMPLETADA','EXPIRADA'));
CREATE INDEX IF NOT EXISTS ix_reservas_expiracion ON restopilot.reservas(estado,fecha);
