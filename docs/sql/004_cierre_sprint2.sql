-- PostgreSQL. Transaccional; no elimina ni fusiona mesas si existen duplicados.
BEGIN;
SET LOCAL search_path=restopilot,public;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM mesas GROUP BY restaurante_id,numero HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Existen números de mesa duplicados por restaurante. Revisar antes de continuar.';
 END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS ux_mesas_restaurante_numero ON mesas(restaurante_id,numero);
-- Corregir exclusivamente los platos del seed identificados por su dueño de demostración.
UPDATE platos p SET tiempo_preparacion_minutos=v.minutos FROM
 (VALUES ('Lomo saltado',14),('Ají de gallina',12),('Arroz con mariscos',15),('Causa limeña',8),('Chicha morada',5),('Suspiro limeño',10),('Pasta de otro local',10)) v(nombre,minutos)
 WHERE p.nombre=v.nombre AND (p.tiempo_preparacion_minutos<5 OR p.tiempo_preparacion_minutos>15)
 AND p.restaurante_id IN (SELECT restaurante_id FROM usuarios WHERE correo IN ('dueno.demo@restopilot.test','otro.dueno.demo@restopilot.test'));
COMMIT;
