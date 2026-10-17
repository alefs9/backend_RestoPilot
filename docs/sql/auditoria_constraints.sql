-- Complemento para la base LOCAL de auditoría; no reemplaza migraciones históricas.
-- Las tablas deben existir. Todo cambio es transaccional y no borra registros.
BEGIN;
SET LOCAL search_path = public;
DO $$
DECLARE c record;
BEGIN
    IF current_database() <> 'restopilot_audit' OR current_user <> 'restopilot_audit' THEN
        RAISE EXCEPTION 'Este script solo admite la base restopilot_audit';
    END IF;
    IF to_regclass('public.pagos') IS NULL OR to_regclass('public.pedidos') IS NULL
        OR to_regclass('public.reservas') IS NULL THEN
        RAISE EXCEPTION 'Primero cree las tablas con el backend de auditoría';
    END IF;
    IF EXISTS(SELECT 1 FROM pagos p LEFT JOIN pedidos o ON o.id=p.pedido_id WHERE o.id IS NULL) THEN
        RAISE EXCEPTION 'Hay pagos sin pedido; requiere revisión manual';
    END IF;
    IF EXISTS(SELECT 1 FROM pagos GROUP BY pedido_id HAVING count(*)>1) THEN
        RAISE EXCEPTION 'Hay pagos duplicados; requiere revisión manual';
    END IF;
    IF EXISTS(SELECT 1 FROM reservas WHERE estado NOT IN
        ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELADA','COMPLETADA')) THEN
        RAISE EXCEPTION 'Hay estados de reserva no reconocidos; requiere revisión manual';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.pagos'::regclass
        AND confrelid='public.pedidos'::regclass AND contype='f'
        AND conkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid='public.pagos'::regclass AND attname='pedido_id')]) THEN
        ALTER TABLE pagos ADD CONSTRAINT fk_pagos_pedido_tb2 FOREIGN KEY(pedido_id) REFERENCES pedidos(id);
    END IF;
    ALTER TABLE pagos ALTER COLUMN metodo DROP NOT NULL;
    -- Sustituir únicamente CHECKs de la columna estado; conservar otros controles.
    FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='public.reservas'::regclass
        AND contype='c' AND conkey=ARRAY[(SELECT attnum FROM pg_attribute
            WHERE attrelid='public.reservas'::regclass AND attname='estado')]
    LOOP
        EXECUTE format('ALTER TABLE public.reservas DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE reservas ADD CONSTRAINT reservas_estado_check CHECK
        (estado IN ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELADA','COMPLETADA'));
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.pagos'::regclass
        AND contype='u' AND conkey=ARRAY[(SELECT attnum FROM pg_attribute
            WHERE attrelid='public.pagos'::regclass AND attname='pedido_id')]) THEN
        ALTER TABLE pagos ADD CONSTRAINT uq_pagos_pedido_tb2 UNIQUE(pedido_id);
    END IF;
END $$;
COMMIT;
SELECT conrelid::regclass AS tabla, conname, pg_get_constraintdef(oid) AS definicion
FROM pg_constraint WHERE conrelid IN ('public.pagos'::regclass,'public.reservas'::regclass)
ORDER BY tabla, conname;
