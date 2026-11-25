package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.Categoria;
import com.restopilot.backend.modules.catalogo.entity.Plato;
import com.restopilot.backend.modules.catalogo.repository.CategoriaRepository;
import com.restopilot.backend.modules.catalogo.repository.PlatoRepository;
import com.restopilot.backend.modules.pagos.entity.EstadoPago;
import com.restopilot.backend.modules.pagos.entity.MetodoPago;
import com.restopilot.backend.modules.pagos.entity.Pago;
import com.restopilot.backend.modules.pagos.repository.PagoRepository;
import com.restopilot.backend.modules.pedidos.entity.DetallePedido;
import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import com.restopilot.backend.modules.pedidos.entity.TipoEntrega;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.reservas.entity.EstadoReserva;
import com.restopilot.backend.modules.reservas.entity.Mesa;
import com.restopilot.backend.modules.reservas.entity.Reserva;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * Pruebas automatizadas para las tareas asignadas a Cristian Condori:
 * S3-TSK-010: Interpretar períodos y solicitar fechas cuando el rango sea ambiguo o incompleto.
 * S3-TSK-011: Construir resúmenes con cifras devueltas por los servicios y manejar períodos sin resultados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:agente_ia_periodos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.ai.model.chat=none"
})
class AgenteIAPeriodosYResumenesTest {
    private static final String CHAT_ADMIN = "/api/ia/admin/chat";
    private static final String PASSWORD = "Password123!";

    @Autowired TestRestTemplate http;
    @Autowired UsuarioRepository usuarios;
    @Autowired RestauranteRepository restaurantes;
    @Autowired PasswordEncoder encoder;
    @Autowired PedidoRepository pedidos;
    @Autowired PagoRepository pagos;
    @Autowired CategoriaRepository categorias;
    @Autowired PlatoRepository platos;
    @Autowired MesaRepository mesas;
    @Autowired ReservaRepository reservas;
    @MockitoBean ChatModel modelo;

    private Restaurante restaurante;

    @BeforeEach
    void setup() {
        restaurante = restaurantes.saveAndFlush(Restaurante.builder()
                .nombre("Restaurante Prueba Períodos")
                .horaApertura(LocalTime.of(8, 0))
                .horaCierre(LocalTime.of(23, 0))
                .build());
    }

    @Test
    @DisplayName("S3-TSK-010: Solicita aclaración cuando el rango sea ambiguo, incompleto o inválido")
    void rangoInvalidoOIncompletoSolicitaAclaracion() {
        var jwt = loginAdmin();

        // 1. Fecha inexistente (ej. 30 de febrero)
        simularHerramienta("platosMasVendidos", "2026-02-30", "2026-03-02");
        var resp1 = chat(jwt, Map.of("mensaje", "Platos de febrero 2026"));
        assertEquals(HttpStatus.OK, resp1.getStatusCode());
        assertEquals("CLARIFICATION_REQUIRED", resp1.getBody().path("estado").asText());
        assertTrue(resp1.getBody().path("mensaje").asText().contains("Indica un mes con su año o un rango"));

        // 2. Fecha de inicio posterior a fecha de fin
        simularHerramienta("pagosPorPeriodo", "2026-10-31", "2026-10-01");
        var resp2 = chat(jwt, Map.of("mensaje", "Pagos al revés"));
        assertEquals(HttpStatus.OK, resp2.getStatusCode());
        assertEquals("CLARIFICATION_REQUIRED", resp2.getBody().path("estado").asText());

        // 3. Herramienta directa de aclaración cuando falta período
        simularHerramientaAclarar();
        var resp3 = chat(jwt, Map.of("mensaje", "¿Cómo nos fue?"));
        assertEquals(HttpStatus.OK, resp3.getStatusCode());
        assertEquals("CLARIFICATION_REQUIRED", resp3.getBody().path("estado").asText());
    }

    @Test
    @DisplayName("S3-TSK-010: Interpreta mes en formato YYYY-MM y convierte al primer y último día")
    void interpretaFormatoMesYAno() {
        var jwt = loginAdmin();
        prepararDatosVenta(LocalDate.of(2026, 10, 15));

        simularHerramienta("platosMasVendidos", "2026-10", "2026-10");
        var resp = chat(jwt, Map.of("mensaje", "Platos de octubre 2026"));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("ANSWER", resp.getBody().path("estado").asText());
        assertEquals("2026-10-01", resp.getBody().at("/datos/fechaInicio").asText());
        assertEquals("2026-10-31", resp.getBody().at("/datos/fechaFin").asText());
    }

    @Test
    @DisplayName("S3-TSK-011: Maneja períodos sin resultados con estado NO_DATA sin arrojar error 500")
    void periodoSinResultadosRetornaNoData() {
        var jwt = loginAdmin();
        // No hay pedidos ni reservas en noviembre 2026
        simularHerramienta("pagosPorPeriodo", "2026-11-01", "2026-11-30");
        var resp = chat(jwt, Map.of("mensaje", "Pagos de noviembre de 2026"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("NO_DATA", resp.getBody().path("estado").asText());
        assertTrue(resp.getBody().path("mensaje").asText().contains("No hay registros"));
        assertFalse(resp.getBody().path("mensaje").asText().contains("500"));
    }

    @Test
    @DisplayName("S3-TSK-011: Construye resumen textual con cifras reales devueltas de platos vendidos")
    void construyeResumenConCifrasRealesDePlatos() {
        var jwt = loginAdmin();
        prepararDatosVenta(LocalDate.of(2026, 10, 10));

        simularHerramienta("platosMasVendidos", "2026-10-01", "2026-10-31");
        var resp = chat(jwt, Map.of("mensaje", "Platos más vendidos de octubre 2026"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("ANSWER", resp.getBody().path("estado").asText());
        // El resumen destaca el plato líder con sus cifras
        String mensaje = resp.getBody().path("mensaje").asText();
        assertTrue(mensaje.contains("Platos más vendidos del período"));
        assertTrue(mensaje.contains("Lomo saltado Especial"));
        assertTrue(mensaje.contains("lidera la preferencia"));

        // Comprueba datos cuantitativos estructurados
        assertEquals("unidades", resp.getBody().at("/datos/unidad").asText());
        assertEquals(1, resp.getBody().at("/datos/platosMasVendidos").size());
        assertEquals("Lomo saltado Especial (5 unidades)", resp.getBody().at("/datos/platosMasVendidos/0").asText());
    }

    @Test
    @DisplayName("S3-TSK-011: Construye resumen textual con cifras reales devueltas de pagos por período")
    void construyeResumenConCifrasRealesDePagos() {
        var jwt = loginAdmin();
        prepararDatosVenta(LocalDate.of(2026, 10, 20));

        simularHerramienta("pagosPorPeriodo", "2026-10-01", "2026-10-31");
        var resp = chat(jwt, Map.of("mensaje", "Pagos de octubre 2026"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("ANSWER", resp.getBody().path("estado").asText());
        String mensaje = resp.getBody().path("mensaje").asText();
        // Verifica que el resumen sintetiza el monto y las cantidades
        assertTrue(mensaje.contains("Pagos confirmados y pendientes del período"));
        assertTrue(mensaje.contains("125.00"));
        assertTrue(mensaje.contains("1 pedido(s) pagado(s)"));

        // Estructura de datos
        assertEquals("PEN", resp.getBody().at("/datos/moneda").asText());
        assertEquals(125.0, resp.getBody().at("/datos/estadoPagos/montoTotal").asDouble());
        assertEquals(1, resp.getBody().at("/datos/estadoPagos/pedidosPagados").asInt());
        assertEquals(0, resp.getBody().at("/datos/estadoPagos/pagosPendientes").asInt());
    }

    @Test
    @DisplayName("S3-TSK-011: Soporta volumen de atención y horarios de mayor demanda con cifras exactas")
    void construyeResumenAtencionesYDemanda() {
        var jwt = loginAdmin();
        prepararDatosVentaYReserva(LocalDate.of(2026, 10, 5));

        // 1. Volumen de atención
        simularHerramienta("volumenAtencion", "2026-10-01", "2026-10-31");
        var respAtencion = chat(jwt, Map.of("mensaje", "Volumen de atención de octubre 2026"));
        assertEquals(HttpStatus.OK, respAtencion.getStatusCode());
        assertEquals("ANSWER", respAtencion.getBody().path("estado").asText());
        String mensajeAtencion = respAtencion.getBody().path("mensaje").asText();
        assertTrue(mensajeAtencion.contains("Volumen de atención del período"));
        assertTrue(mensajeAtencion.contains("1 pedidos atendidos"));
        assertTrue(mensajeAtencion.contains("1 reservas completadas"));
        assertEquals(1, respAtencion.getBody().at("/datos/pedidosAtendidos").asInt());
        assertEquals(1, respAtencion.getBody().at("/datos/reservasCompletadas").asInt());

        // 2. Horarios de mayor demanda
        simularHerramienta("horariosMayorDemanda", "2026-10-01", "2026-10-31");
        var respDemanda = chat(jwt, Map.of("mensaje", "¿Cuáles fueron las horas de mayor actividad?"));
        assertEquals(HttpStatus.OK, respDemanda.getStatusCode());
        assertEquals("ANSWER", respDemanda.getBody().path("estado").asText());
        String mensajeDemanda = respDemanda.getBody().path("mensaje").asText();
        assertTrue(mensajeDemanda.contains("Horarios de mayor demanda"));
        assertTrue(mensajeDemanda.contains("13:00 - 14:00"));
        assertNotNull(respDemanda.getBody().at("/datos/horariosMayorDemanda"));
    }

    private void prepararDatosVenta(LocalDate fecha) {
        var cliente = usuarios.saveAndFlush(Usuario.builder()
                .nombreCompleto("Cliente Test Periodos")
                .correo(UUID.randomUUID() + "@periodos.test")
                .passwordHash(encoder.encode(PASSWORD))
                .rol(Rol.CLIENTE)
                .habilitado(true)
                .build());

        var cat = new Categoria();
        cat.setNombre("Fondos Especiales");
        cat.setRestauranteId(restaurante.getId());
        cat = categorias.saveAndFlush(cat);

        var plato = new Plato();
        plato.setNombre("Lomo saltado Especial");
        plato.setRestauranteId(restaurante.getId());
        plato.setCategoria(cat);
        plato.setPrecio(new BigDecimal("25.00"));
        plato = platos.saveAndFlush(plato);

        var pedido = new Pedido();
        pedido.setRestaurante(restaurante);
        pedido.setCliente(cliente);
        pedido.setTipoEntrega(TipoEntrega.SALON);
        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setTotal(new BigDecimal("125.00"));
        pedido.setFechaCreacion(fecha.atTime(13, 15));

        var detalle = new DetallePedido();
        detalle.setPedido(pedido);
        detalle.setPlato(plato);
        detalle.setCantidad(5);
        detalle.setNombrePlatoSnapshot(plato.getNombre());
        detalle.setSubtotal(pedido.getTotal());
        pedido.setDetalles(List.of(detalle));
        pedido = pedidos.saveAndFlush(pedido);

        pagos.saveAndFlush(Pago.builder()
                .pedidoId(pedido.getId())
                .estado(EstadoPago.PAGADO)
                .metodo(MetodoPago.YAPE)
                .monto(pedido.getTotal())
                .build());
    }

    private void prepararDatosVentaYReserva(LocalDate fecha) {
        prepararDatosVenta(fecha);

        var cliente = usuarios.saveAndFlush(Usuario.builder()
                .nombreCompleto("Cliente Reserva Periodos")
                .correo(UUID.randomUUID() + "@reserva.test")
                .passwordHash(encoder.encode(PASSWORD))
                .rol(Rol.CLIENTE)
                .habilitado(true)
                .build());

        var mesa = mesas.saveAndFlush(Mesa.builder()
                .numero(1)
                .capacidad(4)
                .coordenadaX(10.0)
                .coordenadaY(20.0)
                .restaurante(restaurante)
                .activo(true)
                .build());

        var reserva = new Reserva();
        reserva.setRestaurante(restaurante);
        reserva.setUsuario(cliente);
        reserva.setMesa(mesa);
        reserva.setFecha(fecha);
        reserva.setNumeroComensales(2);
        reserva.setHoraInicio(LocalTime.of(13, 0));
        reserva.setHoraFin(LocalTime.of(14, 0));
        reserva.setEstado(EstadoReserva.COMPLETADA);
        reservas.saveAndFlush(reserva);
    }

    private void simularHerramienta(String nombre, String inicio, String fin) {
        org.mockito.Mockito.doAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            var opciones = (ToolCallingChatOptions) prompt.getOptions();
            var herramienta = opciones.getToolCallbacks().stream()
                    .filter(tool -> nombre.equals(tool.getToolDefinition().name())).findFirst().orElseThrow();
            herramienta.call("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(inicio, fin));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Respuesta simulada"))));
        }).when(modelo).call(any(Prompt.class));
    }

    private void simularHerramientaAclarar() {
        org.mockito.Mockito.doAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            var opciones = (ToolCallingChatOptions) prompt.getOptions();
            var herramienta = opciones.getToolCallbacks().stream()
                    .filter(tool -> "aclararPeriodo".equals(tool.getToolDefinition().name())).findFirst().orElseThrow();
            herramienta.call("{}");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Respuesta simulada aclaración"))));
        }).when(modelo).call(any(Prompt.class));
    }

    private org.springframework.http.ResponseEntity<JsonNode> chat(HttpHeaders headers, Map<String, Object> body) {
        return http.postForEntity(CHAT_ADMIN, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private HttpHeaders loginAdmin() {
        String correo = UUID.randomUUID() + "@admin.test";
        usuarios.saveAndFlush(Usuario.builder()
                .nombreCompleto("Administrador Test")
                .correo(correo)
                .passwordHash(encoder.encode(PASSWORD))
                .rol(Rol.ADMINISTRADOR)
                .restaurante(restaurante)
                .habilitado(true)
                .build());

        var resp = http.postForEntity("/api/auth/login", Map.of("correo", correo, "password", PASSWORD), JsonNode.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        var headers = new HttpHeaders();
        headers.setBearerAuth(resp.getBody().path("token").asText());
        return headers;
    }
}
