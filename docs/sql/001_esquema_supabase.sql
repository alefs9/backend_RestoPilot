-- RestoPilot: esquema privado para Spring Boot. No contiene credenciales.
-- Ejecutar antes del perfil supabase, en una transacción y sin tablas existentes.
CREATE SCHEMA restopilot;
REVOKE ALL ON SCHEMA restopilot FROM PUBLIC;
--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: categorias; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.categorias (
    id bigint NOT NULL,
    actualizado_en timestamp(6) without time zone,
    creado_en timestamp(6) without time zone,
    descripcion text,
    nombre character varying(100) NOT NULL,
    restaurante_id bigint NOT NULL
);


--
-- Name: categorias_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.categorias_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categorias_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.categorias_id_seq OWNED BY restopilot.categorias.id;


--
-- Name: detalles_pedido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.detalles_pedido (
    id bigint NOT NULL,
    cantidad integer NOT NULL,
    nombre_plato_snapshot character varying(255) NOT NULL,
    notas text,
    precio_unitario numeric(10,2),
    subtotal numeric(10,2) NOT NULL,
    pedido_id bigint NOT NULL,
    plato_id bigint NOT NULL
);


--
-- Name: detalles_pedido_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.detalles_pedido_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: detalles_pedido_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.detalles_pedido_id_seq OWNED BY restopilot.detalles_pedido.id;


--
-- Name: mesas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.mesas (
    id bigint NOT NULL,
    activo boolean NOT NULL,
    actualizado_en timestamp(6) without time zone,
    capacidad integer NOT NULL,
    coordenada_x double precision NOT NULL,
    coordenada_y double precision NOT NULL,
    creado_en timestamp(6) without time zone NOT NULL,
    numero integer NOT NULL,
    restaurante_id bigint NOT NULL
);


--
-- Name: mesas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.mesas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mesas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.mesas_id_seq OWNED BY restopilot.mesas.id;


--
-- Name: notificaciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.notificaciones (
    id bigint NOT NULL,
    fecha_creacion timestamp(6) without time zone NOT NULL,
    mensaje character varying(500) NOT NULL,
    usuario_id bigint NOT NULL
);


--
-- Name: notificaciones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.notificaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificaciones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.notificaciones_id_seq OWNED BY restopilot.notificaciones.id;


--
-- Name: pagos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.pagos (
    id bigint NOT NULL,
    actualizado_en timestamp(6) without time zone,
    codigo_referencia character varying(100),
    creado_en timestamp(6) without time zone NOT NULL,
    estado character varying(30) NOT NULL,
    fecha_pago timestamp(6) without time zone,
    metodo character varying(30),
    monto numeric(10,2) NOT NULL,
    pedido_id bigint NOT NULL,
    CONSTRAINT pagos_estado_check CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'PAGADO'::character varying])::text[]))),
    CONSTRAINT pagos_metodo_check CHECK (((metodo)::text = ANY ((ARRAY['YAPE'::character varying, 'PLIN'::character varying, 'TARJETA'::character varying, 'EFECTIVO'::character varying])::text[])))
);


--
-- Name: pagos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.pagos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pagos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.pagos_id_seq OWNED BY restopilot.pagos.id;


--
-- Name: pedidos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.pedidos (
    id bigint NOT NULL,
    direccion_entrega character varying(255),
    estado character varying(20) NOT NULL,
    fecha_creacion timestamp(6) without time zone NOT NULL,
    limite_cancelacion timestamp(6) without time zone,
    tipo_entrega character varying(20) NOT NULL,
    total numeric(10,2) NOT NULL,
    cliente_id bigint NOT NULL,
    mesa_id bigint,
    restaurante_id bigint NOT NULL,
    CONSTRAINT pedidos_estado_check CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'EN_PREPARACION'::character varying, 'LISTO'::character varying, 'ENTREGADO'::character varying, 'CANCELADO'::character varying])::text[]))),
    CONSTRAINT pedidos_tipo_entrega_check CHECK (((tipo_entrega)::text = ANY ((ARRAY['SALON'::character varying, 'LLEVAR'::character varying, 'RECOGER'::character varying, 'DELIVERY'::character varying])::text[])))
);


--
-- Name: pedidos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.pedidos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedidos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.pedidos_id_seq OWNED BY restopilot.pedidos.id;


--
-- Name: platos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.platos (
    id bigint NOT NULL,
    actualizado_en timestamp(6) without time zone,
    alergenos text,
    complejidad character varying(50),
    creado_en timestamp(6) without time zone,
    descripcion text,
    disponible boolean NOT NULL,
    imagen_url character varying(255),
    ingredientes text,
    nombre character varying(150) NOT NULL,
    precio numeric(10,2) NOT NULL,
    restaurante_id bigint NOT NULL,
    tiempo_preparacion_minutos integer,
    categoria_id bigint NOT NULL
);


--
-- Name: platos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.platos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.platos_id_seq OWNED BY restopilot.platos.id;


--
-- Name: reservas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.reservas (
    id bigint NOT NULL,
    actualizado_en timestamp(6) without time zone,
    creado_en timestamp(6) without time zone NOT NULL,
    estado character varying(20) NOT NULL,
    fecha date NOT NULL,
    hora_fin time(6) without time zone NOT NULL,
    hora_inicio time(6) without time zone NOT NULL,
    motivo_rechazo character varying(300),
    notas character varying(500),
    numero_comensales integer NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    mesa_id bigint NOT NULL,
    restaurante_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    completada_en timestamp(6) without time zone,
    expirada_en timestamp(6) without time zone,
    llegada_registrada_en timestamp(6) without time zone,
    CONSTRAINT reservas_estado_check CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'CONFIRMADA'::character varying, 'RECHAZADA'::character varying, 'CANCELADA'::character varying, 'COMPLETADA'::character varying])::text[])))
);


--
-- Name: reservas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.reservas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.reservas_id_seq OWNED BY restopilot.reservas.id;


--
-- Name: restaurantes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.restaurantes (
    id bigint NOT NULL,
    acepta_delivery boolean NOT NULL,
    activo boolean NOT NULL,
    actualizado_en timestamp(6) without time zone,
    creado_en timestamp(6) without time zone NOT NULL,
    direccion character varying(255),
    hora_apertura time(6) without time zone,
    hora_cierre time(6) without time zone,
    nombre character varying(150) NOT NULL,
    telefono character varying(20),
    tiene_atencion_fisica boolean NOT NULL
);


--
-- Name: restaurantes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.restaurantes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: restaurantes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.restaurantes_id_seq OWNED BY restopilot.restaurantes.id;


--
-- Name: usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE restopilot.usuarios (
    id bigint NOT NULL,
    actualizado_en timestamp(6) without time zone,
    correo character varying(100) NOT NULL,
    creado_en timestamp(6) without time zone NOT NULL,
    habilitado boolean NOT NULL,
    nombre_completo character varying(150) NOT NULL,
    password_hash character varying(255) NOT NULL,
    rol character varying(30) NOT NULL,
    restaurante_id bigint,
    CONSTRAINT usuarios_rol_check CHECK (((rol)::text = ANY ((ARRAY['CLIENTE'::character varying, 'ADMINISTRADOR'::character varying, 'DUENO'::character varying])::text[])))
);


--
-- Name: usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE restopilot.usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE restopilot.usuarios_id_seq OWNED BY restopilot.usuarios.id;


--
-- Name: categorias id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.categorias ALTER COLUMN id SET DEFAULT nextval('restopilot.categorias_id_seq'::regclass);


--
-- Name: detalles_pedido id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.detalles_pedido ALTER COLUMN id SET DEFAULT nextval('restopilot.detalles_pedido_id_seq'::regclass);


--
-- Name: mesas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.mesas ALTER COLUMN id SET DEFAULT nextval('restopilot.mesas_id_seq'::regclass);


--
-- Name: notificaciones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.notificaciones ALTER COLUMN id SET DEFAULT nextval('restopilot.notificaciones_id_seq'::regclass);


--
-- Name: pagos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pagos ALTER COLUMN id SET DEFAULT nextval('restopilot.pagos_id_seq'::regclass);


--
-- Name: pedidos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pedidos ALTER COLUMN id SET DEFAULT nextval('restopilot.pedidos_id_seq'::regclass);


--
-- Name: platos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.platos ALTER COLUMN id SET DEFAULT nextval('restopilot.platos_id_seq'::regclass);


--
-- Name: reservas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.reservas ALTER COLUMN id SET DEFAULT nextval('restopilot.reservas_id_seq'::regclass);


--
-- Name: restaurantes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.restaurantes ALTER COLUMN id SET DEFAULT nextval('restopilot.restaurantes_id_seq'::regclass);


--
-- Name: usuarios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.usuarios ALTER COLUMN id SET DEFAULT nextval('restopilot.usuarios_id_seq'::regclass);


--
-- Name: categorias categorias_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.categorias
    ADD CONSTRAINT categorias_pkey PRIMARY KEY (id);


--
-- Name: detalles_pedido detalles_pedido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.detalles_pedido
    ADD CONSTRAINT detalles_pedido_pkey PRIMARY KEY (id);


--
-- Name: mesas mesas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.mesas
    ADD CONSTRAINT mesas_pkey PRIMARY KEY (id);


--
-- Name: notificaciones notificaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.notificaciones
    ADD CONSTRAINT notificaciones_pkey PRIMARY KEY (id);


--
-- Name: pagos pagos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pagos
    ADD CONSTRAINT pagos_pkey PRIMARY KEY (id);


--
-- Name: pedidos pedidos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pedidos
    ADD CONSTRAINT pedidos_pkey PRIMARY KEY (id);


--
-- Name: platos platos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.platos
    ADD CONSTRAINT platos_pkey PRIMARY KEY (id);


--
-- Name: reservas reservas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.reservas
    ADD CONSTRAINT reservas_pkey PRIMARY KEY (id);


--
-- Name: restaurantes restaurantes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.restaurantes
    ADD CONSTRAINT restaurantes_pkey PRIMARY KEY (id);


--
-- Name: pagos uk_9ibmqk82q3wpbw2l1jcych5kv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pagos
    ADD CONSTRAINT uk_9ibmqk82q3wpbw2l1jcych5kv UNIQUE (pedido_id);


--
-- Name: usuarios uk_cdmw5hxlfj78uf4997i3qyyw5; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.usuarios
    ADD CONSTRAINT uk_cdmw5hxlfj78uf4997i3qyyw5 UNIQUE (correo);


--
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);


--
-- Name: platos fk1q3vxt814ymgxp8hlnspb8phc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.platos
    ADD CONSTRAINT fk1q3vxt814ymgxp8hlnspb8phc FOREIGN KEY (categoria_id) REFERENCES restopilot.categorias(id);


--
-- Name: usuarios fk396qtxwodge9uffa2s4vrevy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.usuarios
    ADD CONSTRAINT fk396qtxwodge9uffa2s4vrevy FOREIGN KEY (restaurante_id) REFERENCES restopilot.restaurantes(id);


--
-- Name: detalles_pedido fk4qmqlxyy78kjl4ec4wjnfmggu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.detalles_pedido
    ADD CONSTRAINT fk4qmqlxyy78kjl4ec4wjnfmggu FOREIGN KEY (pedido_id) REFERENCES restopilot.pedidos(id);


--
-- Name: pedidos fk6dtctern9votxnaydg7g2uifb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pedidos
    ADD CONSTRAINT fk6dtctern9votxnaydg7g2uifb FOREIGN KEY (cliente_id) REFERENCES restopilot.usuarios(id);


--
-- Name: detalles_pedido fkc8y2mk7bqnqm6pqos8x1bky4k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.detalles_pedido
    ADD CONSTRAINT fkc8y2mk7bqnqm6pqos8x1bky4k FOREIGN KEY (plato_id) REFERENCES restopilot.platos(id);


--
-- Name: reservas fkcfh7qcr7oxomqk5hhbxdg2m7p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.reservas
    ADD CONSTRAINT fkcfh7qcr7oxomqk5hhbxdg2m7p FOREIGN KEY (usuario_id) REFERENCES restopilot.usuarios(id);


--
-- Name: reservas fkdwyrfr7ladvq3f83rltlg13sv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.reservas
    ADD CONSTRAINT fkdwyrfr7ladvq3f83rltlg13sv FOREIGN KEY (restaurante_id) REFERENCES restopilot.restaurantes(id);


--
-- Name: pedidos fkf3mf88pcxawf3nt06400qmqn3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pedidos
    ADD CONSTRAINT fkf3mf88pcxawf3nt06400qmqn3 FOREIGN KEY (restaurante_id) REFERENCES restopilot.restaurantes(id);


--
-- Name: mesas fkhetp6x3qq5k40vl3caxchqw13; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.mesas
    ADD CONSTRAINT fkhetp6x3qq5k40vl3caxchqw13 FOREIGN KEY (restaurante_id) REFERENCES restopilot.restaurantes(id);


--
-- Name: pedidos fkjknq12ps8d4nqyk1xdytecupx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.pedidos
    ADD CONSTRAINT fkjknq12ps8d4nqyk1xdytecupx FOREIGN KEY (mesa_id) REFERENCES restopilot.mesas(id);


--
-- Name: reservas fkk33nidm20a0k0ssnyjfjmnnnp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY restopilot.reservas
    ADD CONSTRAINT fkk33nidm20a0k0ssnyjfjmnnnp FOREIGN KEY (mesa_id) REFERENCES restopilot.mesas(id);


--
-- PostgreSQL database dump complete
--


