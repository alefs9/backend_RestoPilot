-- Solo desarrollo/demostración. No incluye contraseñas ni claves de Supabase.
-- Antes de ejecutar, configurar en la misma sesión:
-- SELECT set_config('restopilot.demo_enabled', 'true', false);
-- SELECT set_config('restopilot.demo_password_hash', '<BCRYPT_GENERADO_LOCALMENTE>', false);
-- Ejecutar dentro de una transacción. Los registros existentes se conservan.
DO $seed$
DECLARE
    app_schema text;
    demo_hash text := current_setting('restopilot.demo_password_hash', true);
    local_now timestamp := CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima';
    tomorrow date := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date + 1;
    yesterday date := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date - 1;
    live_start timestamp;
    live_end timestamp;
    restaurant_id bigint;
    other_restaurant_id bigint;
    owner_id bigint;
    client_id bigint;
    second_client_id bigint;
    category_id bigint;
    dish_id bigint;
    second_dish_id bigint;
    table_id bigint;
    order_id bigint;
    current_client bigint;
    current_restaurant bigint;
    detail_total numeric(10,2);
    price_one numeric(10,2);
    price_two numeric(10,2);
    full_total numeric(10,2);
    case_start timestamp;
    case_end timestamp;
    item record;
BEGIN
    IF current_setting('restopilot.demo_enabled', true) IS DISTINCT FROM 'true' THEN
        RAISE EXCEPTION 'Datos demo desactivados. Activarlos explícitamente en esta sesión.';
    END IF;
    IF demo_hash IS NULL OR demo_hash !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$' THEN
        RAISE EXCEPTION 'Falta un hash BCrypt válido generado localmente para las cuentas demo.';
    END IF;
    IF to_regclass('restopilot.usuarios') IS NOT NULL THEN app_schema := 'restopilot';
    ELSIF to_regclass('public.usuarios') IS NOT NULL THEN app_schema := 'public';
    ELSE RAISE EXCEPTION 'Primero debe instalarse el esquema de RestoPilot.';
    END IF;
    PERFORM set_config('search_path', format('%I,pg_catalog', app_schema), true);
    -- Serializa dos ejecuciones del seed sin bloquear las operaciones normales de la app.
    PERFORM pg_advisory_xact_lock(727001126);

    SELECT restaurante_id INTO restaurant_id FROM usuarios WHERE correo='dueno.demo@restopilot.test';
    IF FOUND AND NOT EXISTS (SELECT FROM usuarios u JOIN restaurantes r ON r.id=u.restaurante_id
        WHERE u.correo='dueno.demo@restopilot.test' AND u.rol='DUENO'
        AND u.nombre_completo='Dueño Demo RestoPilot' AND r.nombre='RestoPilot Demo') THEN
        RAISE EXCEPTION 'El correo reservado del dueño demo ya pertenece a otro registro.';
    END IF;
    IF restaurant_id IS NULL THEN
        INSERT INTO restaurantes(nombre,direccion,telefono,hora_apertura,hora_cierre,activo,tiene_atencion_fisica,acepta_delivery,creado_en,actualizado_en)
        VALUES('RestoPilot Demo','Dirección ficticia de demostración','900000001','00:00','23:59',true,true,true,local_now,local_now) RETURNING id INTO restaurant_id;
        INSERT INTO usuarios(nombre_completo,correo,password_hash,rol,restaurante_id,habilitado,creado_en,actualizado_en)
        VALUES('Dueño Demo RestoPilot','dueno.demo@restopilot.test',demo_hash,'DUENO',restaurant_id,true,local_now,local_now);
    END IF;

    SELECT restaurante_id INTO other_restaurant_id FROM usuarios WHERE correo='otro.dueno.demo@restopilot.test';
    IF FOUND AND NOT EXISTS (SELECT FROM usuarios u JOIN restaurantes r ON r.id=u.restaurante_id
        WHERE u.correo='otro.dueno.demo@restopilot.test' AND u.rol='DUENO'
        AND u.nombre_completo='Otro Dueño Demo RestoPilot' AND r.nombre='Otro Local Demo') THEN
        RAISE EXCEPTION 'El correo reservado del otro dueño demo ya pertenece a otro registro.';
    END IF;
    IF other_restaurant_id IS NULL THEN
        INSERT INTO restaurantes(nombre,direccion,telefono,hora_apertura,hora_cierre,activo,tiene_atencion_fisica,acepta_delivery,creado_en,actualizado_en)
        VALUES('Otro Local Demo','Dirección ficticia del segundo local','900000002','00:00','23:59',true,true,true,local_now,local_now) RETURNING id INTO other_restaurant_id;
        INSERT INTO usuarios(nombre_completo,correo,password_hash,rol,restaurante_id,habilitado,creado_en,actualizado_en)
        VALUES('Otro Dueño Demo RestoPilot','otro.dueno.demo@restopilot.test',demo_hash,'DUENO',other_restaurant_id,true,local_now,local_now);
    END IF;
    FOR item IN SELECT * FROM (VALUES
      ('Administrador Demo RestoPilot','admin.demo@restopilot.test','ADMINISTRADOR',restaurant_id),
      ('Cliente Demo RestoPilot','cliente.demo@restopilot.test','CLIENTE',NULL::bigint),
      ('Otro Cliente Demo RestoPilot','otro.cliente.demo@restopilot.test','CLIENTE',NULL::bigint)
    ) AS accounts(display_name,email,role_name,local_id) LOOP
        IF EXISTS (SELECT FROM usuarios WHERE correo=item.email AND
            (nombre_completo<>item.display_name OR rol<>item.role_name OR restaurante_id IS DISTINCT FROM item.local_id)) THEN
            RAISE EXCEPTION 'Un correo reservado demo pertenece a otro registro; se conserva sin cambios.';
        END IF;
        INSERT INTO usuarios(nombre_completo,correo,password_hash,rol,restaurante_id,habilitado,creado_en,actualizado_en)
        SELECT item.display_name,item.email,demo_hash,item.role_name,item.local_id,true,local_now,local_now
        WHERE NOT EXISTS (SELECT FROM usuarios WHERE correo=item.email);
    END LOOP;
    SELECT id INTO client_id FROM usuarios WHERE correo='cliente.demo@restopilot.test';
    SELECT id INTO second_client_id FROM usuarios WHERE correo='otro.cliente.demo@restopilot.test';

    FOR item IN SELECT * FROM (VALUES
      (restaurant_id,'Entradas','Para compartir'),(restaurant_id,'Fondos','Platos principales'),
      (restaurant_id,'Bebidas y postres','Bebidas y dulces'),(other_restaurant_id,'Carta de otro local','Aislamiento entre restaurantes')
    ) AS categories(local_id,category_name,description) LOOP
        INSERT INTO categorias(restaurante_id,nombre,descripcion,creado_en,actualizado_en)
        SELECT item.local_id,item.category_name,item.description,local_now,local_now
        WHERE NOT EXISTS (SELECT FROM categorias WHERE restaurante_id=item.local_id AND nombre=item.category_name);
    END LOOP;
    FOR item IN SELECT * FROM (VALUES
      (restaurant_id,'Fondos','Lomo saltado',38.00,'Carne, cebolla, tomate, papa y arroz','Soya','ALTA',30,true),
      (restaurant_id,'Fondos','Ají de gallina',28.00,'Pollo, ají amarillo, leche y pan','Gluten, lácteos','MEDIA',25,true),
      (restaurant_id,'Fondos','Arroz con mariscos',32.00,'Arroz, mariscos y verduras','Mariscos','ALTA',35,true),
      (restaurant_id,'Entradas','Causa limeña',18.00,'Papa amarilla, pollo, palta y mayonesa','Huevo','BAJA',10,true),
      (restaurant_id,'Bebidas y postres','Chicha morada',8.00,'Maíz morado, piña y canela','Sin alérgenos declarados','BAJA',3,true),
      (restaurant_id,'Bebidas y postres','Suspiro limeño',13.00,'Leche, azúcar y huevo','Huevo, lácteos','MEDIA',15,false),
      (other_restaurant_id,'Carta de otro local','Pasta de otro local',25.00,'Pasta y verduras','Gluten','MEDIA',20,true)
    ) AS dishes(local_id,category_name,dish_name,price,ingredients,allergens,complexity,minutes,available) LOOP
        SELECT id INTO category_id FROM categorias WHERE restaurante_id=item.local_id AND nombre=item.category_name ORDER BY id LIMIT 1;
        INSERT INTO platos(restaurante_id,categoria_id,nombre,descripcion,precio,ingredientes,alergenos,complejidad,tiempo_preparacion_minutos,disponible,imagen_url,creado_en,actualizado_en)
        SELECT item.local_id,category_id,item.dish_name,'Plato de demostración',item.price,item.ingredients,item.allergens,item.complexity,item.minutes,item.available,NULL,local_now,local_now
        WHERE NOT EXISTS (SELECT FROM platos WHERE restaurante_id=item.local_id AND nombre=item.dish_name);
    END LOOP;
    FOR item IN SELECT n FROM generate_series(1,6) AS n LOOP
        INSERT INTO mesas(restaurante_id,numero,capacidad,coordenada_x,coordenada_y,activo,creado_en,actualizado_en)
        SELECT restaurant_id,item.n,CASE WHEN item.n=1 THEN 2 WHEN item.n=6 THEN 6 ELSE 4 END,
          ((item.n-1)%3)*180+60,((item.n-1)/3)*160+60,item.n<>6,local_now,local_now
        WHERE NOT EXISTS (SELECT FROM mesas WHERE restaurante_id=restaurant_id AND numero=item.n);
    END LOOP;
    INSERT INTO mesas(restaurante_id,numero,capacidad,coordenada_x,coordenada_y,activo,creado_en,actualizado_en)
    SELECT other_restaurant_id,1,4,80,80,true,local_now,local_now
    WHERE NOT EXISTS (SELECT FROM mesas WHERE restaurante_id=other_restaurant_id AND numero=1);

    live_start:=GREATEST(date_trunc('minute',local_now)-interval '5 minutes',date_trunc('day',local_now));
    live_end:=LEAST(live_start+interval '1 hour',date_trunc('day',live_start)+interval '23 hours 59 minutes');
    FOR item IN SELECT * FROM (VALUES
      ('pendiente','PENDIENTE',2,tomorrow+time '12:00',tomorrow+time '13:00',NULL::timestamp,NULL::timestamp,NULL::timestamp,NULL::text),
      ('confirmada','CONFIRMADA',3,tomorrow+time '14:00',tomorrow+time '15:00',NULL,NULL,NULL,NULL),
      ('atendiendo','CONFIRMADA',4,live_start,live_end,live_start,NULL,NULL,NULL),
      ('expirada','EXPIRADA',5,yesterday+time '10:00',yesterday+time '11:00',NULL,NULL,yesterday+time '10:15',NULL),
      ('completada','COMPLETADA',2,yesterday+time '12:00',yesterday+time '13:00',yesterday+time '11:55',yesterday+time '12:45',NULL,NULL),
      ('rechazada','RECHAZADA',3,tomorrow+time '16:00',tomorrow+time '17:00',NULL,NULL,NULL,'Sin disponibilidad para esa solicitud de demostración'),
      ('cancelada','CANCELADA',4,tomorrow+time '18:00',tomorrow+time '19:00',NULL,NULL,NULL,NULL)
    ) AS reservations(tag,state_name,table_number,start_at,end_at,arrived_at,completed_at,expired_at,rejection) LOOP
        SELECT id INTO table_id FROM mesas WHERE restaurante_id=restaurant_id AND numero=item.table_number ORDER BY id LIMIT 1;
        INSERT INTO reservas(restaurante_id,usuario_id,mesa_id,fecha,hora_inicio,hora_fin,numero_comensales,estado,notas,motivo_rechazo,llegada_registrada_en,completada_en,expirada_en,version,creado_en,actualizado_en)
        SELECT restaurant_id,client_id,table_id,item.start_at::date,item.start_at::time,item.end_at::time,2,item.state_name,
          'RP-DEMO:reserva-'||item.tag,item.rejection,item.arrived_at,item.completed_at,item.expired_at,0,local_now-interval '2 days',local_now
        WHERE NOT EXISTS (SELECT FROM reservas WHERE restaurante_id=restaurant_id AND usuario_id=client_id AND notas='RP-DEMO:reserva-'||item.tag);
    END LOOP;
    -- Un registro ajeno permite comprobar el aislamiento entre cuentas.
    SELECT id INTO table_id FROM mesas WHERE restaurante_id=restaurant_id AND numero=5 ORDER BY id LIMIT 1;
    INSERT INTO reservas(restaurante_id,usuario_id,mesa_id,fecha,hora_inicio,hora_fin,numero_comensales,estado,notas,version,creado_en,actualizado_en)
    SELECT restaurant_id,second_client_id,table_id,tomorrow,'19:00','20:00',2,'CONFIRMADA','RP-DEMO:reserva-otro-cliente',0,local_now,local_now
    WHERE NOT EXISTS (SELECT FROM reservas WHERE usuario_id=second_client_id AND restaurante_id=restaurant_id AND notas='RP-DEMO:reserva-otro-cliente');

    FOR item IN SELECT * FROM (VALUES
      ('pendiente','PENDIENTE','SALON','Lomo saltado',2,'Chicha morada',1,false,false),
      ('preparacion','EN_PREPARACION','LLEVAR','Ají de gallina',1,'Causa limeña',1,false,false),
      ('listo','LISTO','RECOGER','Lomo saltado',1,NULL::text,0,false,false),
      ('entregado','ENTREGADO','DELIVERY','Ají de gallina',2,'Chicha morada',2,false,false),
      ('cancelado','CANCELADO','RECOGER','Causa limeña',1,NULL,0,false,false),
      ('otro-cliente','PENDIENTE','DELIVERY','Arroz con mariscos',1,NULL,0,true,false),
      ('otro-local','PENDIENTE','RECOGER','Pasta de otro local',1,NULL,0,true,true)
    ) AS orders(tag,state_name,modality,dish_one,qty_one,dish_two,qty_two,second_client,other_local) LOOP
        current_client:=CASE WHEN item.second_client THEN second_client_id ELSE client_id END;
        current_restaurant:=CASE WHEN item.other_local THEN other_restaurant_id ELSE restaurant_id END;
        SELECT p.id INTO order_id FROM pedidos p JOIN detalles_pedido d ON d.pedido_id=p.id
          WHERE p.restaurante_id=current_restaurant AND p.cliente_id=current_client AND d.notas='RP-DEMO:pedido-'||item.tag ORDER BY p.id LIMIT 1;
        IF order_id IS NOT NULL THEN CONTINUE; END IF;
        SELECT id,precio INTO dish_id,price_one FROM platos WHERE restaurante_id=current_restaurant AND nombre=item.dish_one ORDER BY id LIMIT 1;
        price_two:=0;second_dish_id:=NULL;
        IF item.qty_two>0 THEN SELECT id,precio INTO second_dish_id,price_two FROM platos WHERE restaurante_id=current_restaurant AND nombre=item.dish_two ORDER BY id LIMIT 1; END IF;
        full_total:=price_one*item.qty_one+price_two*item.qty_two;
        table_id:=NULL;
        IF item.modality='SALON' THEN SELECT id INTO table_id FROM mesas WHERE restaurante_id=current_restaurant AND numero=1 ORDER BY id LIMIT 1; END IF;
        INSERT INTO pedidos(restaurante_id,cliente_id,mesa_id,estado,tipo_entrega,total,direccion_entrega,fecha_creacion,limite_cancelacion)
        VALUES(current_restaurant,current_client,table_id,item.state_name,item.modality,full_total,
          CASE WHEN item.modality='DELIVERY' THEN 'Dirección ficticia para pruebas de delivery' ELSE NULL END,
          CASE WHEN item.state_name='PENDIENTE' THEN local_now-interval '1 minute' ELSE local_now-interval '30 minutes' END,
          CASE WHEN item.state_name='PENDIENTE' THEN local_now+interval '4 minutes' ELSE local_now-interval '25 minutes' END) RETURNING id INTO order_id;
        INSERT INTO detalles_pedido(pedido_id,plato_id,nombre_plato_snapshot,cantidad,precio_unitario,subtotal,notas)
        VALUES(order_id,dish_id,item.dish_one,item.qty_one,price_one,price_one*item.qty_one,'RP-DEMO:pedido-'||item.tag);
        IF item.qty_two>0 THEN INSERT INTO detalles_pedido(pedido_id,plato_id,nombre_plato_snapshot,cantidad,precio_unitario,subtotal,notas)
          VALUES(order_id,second_dish_id,item.dish_two,item.qty_two,price_two,price_two*item.qty_two,'RP-DEMO:pedido-'||item.tag); END IF;
        -- Preparación sin fila de pago permite probar también la ausencia de registro.
        IF item.tag<>'preparacion' THEN
            INSERT INTO pagos(pedido_id,monto,estado,metodo,codigo_referencia,fecha_pago,creado_en,actualizado_en)
            VALUES(order_id,full_total,CASE WHEN item.tag='entregado' THEN 'PAGADO' ELSE 'PENDIENTE' END,
              CASE WHEN item.tag='entregado' THEN 'EFECTIVO' ELSE NULL END,
              CASE WHEN item.tag='entregado' THEN 'RP-DEMO-PAGO-ENTREGADO' ELSE NULL END,
              CASE WHEN item.tag='entregado' THEN local_now-interval '15 minutes' ELSE NULL END,local_now,local_now);
        END IF;
    END LOOP;
    FOR item IN SELECT * FROM (VALUES
      (client_id,'RP-DEMO: Tu pedido de demostración está listo para recoger.'),
      (client_id,'RP-DEMO: Tu historial contiene una reserva expirada con fecha registrada.'),
      (second_client_id,'RP-DEMO: Aviso exclusivo del segundo cliente; no debe aparecer en otra cuenta.')
    ) AS notices(user_id,message_text) LOOP
        INSERT INTO notificaciones(usuario_id,mensaje,fecha_creacion)
        SELECT item.user_id,item.message_text,local_now WHERE NOT EXISTS (SELECT FROM notificaciones WHERE usuario_id=item.user_id AND mensaje=item.message_text);
    END LOOP;
END
$seed$;
