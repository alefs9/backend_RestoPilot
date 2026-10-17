-- Ejecutar después del esquema base. No elimina reservas ni inventa asistencia histórica.
BEGIN;
DO $$
BEGIN
    IF current_database() <> 'restopilot_audit' OR current_user <> 'restopilot_audit' THEN
        RAISE EXCEPTION 'Esta ejecución solo admite la base y usuario de auditoría';
    END IF;
END $$;
ALTER TABLE public.reservas ADD COLUMN IF NOT EXISTS llegada_registrada_en timestamp;
ALTER TABLE public.reservas ADD COLUMN IF NOT EXISTS completada_en timestamp;
ALTER TABLE public.reservas ADD COLUMN IF NOT EXISTS expirada_en timestamp;
ALTER TABLE public.reservas DROP CONSTRAINT IF EXISTS reservas_estado_check;
ALTER TABLE public.reservas ADD CONSTRAINT reservas_estado_check CHECK
    (estado IN ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELADA','COMPLETADA','EXPIRADA'));
CREATE INDEX IF NOT EXISTS ix_reservas_expiracion ON public.reservas(estado, fecha);
COMMIT;
