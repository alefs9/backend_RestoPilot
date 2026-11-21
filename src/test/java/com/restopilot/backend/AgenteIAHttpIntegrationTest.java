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
import com.restopilot.backend.modules.pagos.entity.Pago;
import com.restopilot.backend.modules.pagos.repository.PagoRepository;
import com.restopilot.backend.modules.pedidos.entity.DetallePedido;
import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import com.restopilot.backend.modules.pedidos.entity.TipoEntrega;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:agente_ia;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.ai.model.chat=none"
})
class AgenteIAHttpIntegrationTest {
    private static final String CHAT = "/api/ia/admin/chat";
    private static final String PASSWORD = "PruebaChat123!";

    @Autowired TestRestTemplate http;
    @Autowired UsuarioRepository usuarios;
    @Autowired RestauranteRepository restaurantes;
    @Autowired PasswordEncoder encoder;
    @Autowired PedidoRepository pedidos;
    @Autowired PagoRepository pagos;
    @Autowired CategoriaRepository categorias;
    @Autowired PlatoRepository platos;
    @MockitoBean ChatModel modelo;

    private Restaurante restaurante;

    @BeforeEach
    void crearRestaurante() {
        restaurante = restaurantes.saveAndFlush(Restaurante.builder().nombre("Local de prueba IA").build());
    }

    @Test
    void aceptaJwtDeDuenoYAdministrador() {
        when(modelo.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hola")))));

        for (Rol rol : List.of(Rol.DUENO, Rol.ADMINISTRADOR)) {
            var respuesta = chat(login(rol), Map.of("mensaje", "Hola", "restauranteId", restaurante.getId()));
            assertEquals(HttpStatus.OK, respuesta.getStatusCode());
            assertEquals("ANSWER", respuesta.getBody().path("estado").asText());
            assertEquals("Hola", respuesta.getBody().path("mensaje").asText());
        }
    }

    @Test
    void rechazaAnonimoClienteYRestauranteAjenoAntesDeConsultar() {
        var mensaje = Map.<String, Object>of("mensaje", "Hola");
        assertEquals(HttpStatus.FORBIDDEN, chat(new HttpHeaders(), mensaje).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, chat(login(Rol.CLIENTE), mensaje).getStatusCode());

        var ajeno = restaurantes.saveAndFlush(Restaurante.builder().nombre("Otro local IA").build());
        assertEquals(HttpStatus.FORBIDDEN, chat(login(Rol.DUENO),
                Map.of("mensaje", "Hola", "restauranteId", ajeno.getId())).getStatusCode());
        verifyNoInteractions(modelo);
    }

    @Test
    void controlaFalloDelProveedorSinExponerSuMensaje() {
        when(modelo.call(any(Prompt.class))).thenThrow(new IllegalStateException("detalle-interno"));
        var respuesta = chat(login(Rol.DUENO), Map.of("mensaje", "Hola"));

        assertEquals(HttpStatus.BAD_GATEWAY, respuesta.getStatusCode());
        assertEquals("AI_PROVIDER_FAILURE", respuesta.getBody().path("codigo").asText());
        assertFalse(respuesta.getBody().toString().contains("detalle-interno"));
    }

    @Test
    void platosYPagosCoincidenConLosReportesOperativos() {
        var cliente = usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Cliente de prueba")
                .correo(UUID.randomUUID() + "@ia.test").passwordHash(encoder.encode(PASSWORD))
                .rol(Rol.CLIENTE).habilitado(true).build());
        var categoria = new Categoria();
        categoria.setNombre("Fondos");
        categoria.setRestauranteId(restaurante.getId());
        categoria = categorias.saveAndFlush(categoria);
        var plato = new Plato();
        plato.setNombre("Lomo saltado");
        plato.setRestauranteId(restaurante.getId());
        plato.setCategoria(categoria);
        plato.setPrecio(new BigDecimal("20.00"));
        plato = platos.saveAndFlush(plato);
        var pedido = new Pedido();
        pedido.setRestaurante(restaurante);
        pedido.setCliente(cliente);
        pedido.setTipoEntrega(TipoEntrega.LLEVAR);
        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setTotal(new BigDecimal("60.00"));
        pedido.setFechaCreacion(LocalDate.of(2026, 10, 7).atTime(12, 0));
        var detalle = new DetallePedido();
        detalle.setPedido(pedido);
        detalle.setPlato(plato);
        detalle.setCantidad(3);
        detalle.setNombrePlatoSnapshot(plato.getNombre());
        detalle.setSubtotal(pedido.getTotal());
        pedido.setDetalles(List.of(detalle));
        pedido = pedidos.saveAndFlush(pedido);
        pagos.saveAndFlush(Pago.builder().pedidoId(pedido.getId()).estado(EstadoPago.PAGADO)
                .monto(pedido.getTotal()).build());

        var jwt = login(Rol.DUENO);
        var fechas = "?fechaInicio=2026-10-01&fechaFin=2026-10-31";
        simularHerramienta("platosMasVendidos", "2026-10-01", "2026-10-31");
        var ranking = chat(jwt, Map.of("mensaje", "Platos más vendidos de octubre de 2026"));
        assertEquals(HttpStatus.OK, ranking.getStatusCode());
        assertEquals("Lomo saltado (3 unidades)", ranking.getBody().at("/datos/platosMasVendidos/0").asText());
        assertEquals(reporte(jwt, "/api/reportes/operativos" + fechas + "&tipo=PLATOS").path("platosMasVendidos"),
                ranking.getBody().at("/datos/platosMasVendidos"));

        simularHerramienta("pagosPorPeriodo", "2026-10-01", "2026-10-31");
        var resumen = chat(jwt, Map.of("mensaje", "Pagos de octubre de 2026"));
        assertEquals(HttpStatus.OK, resumen.getStatusCode());
        assertEquals("PEN", resumen.getBody().at("/datos/moneda").asText());
        assertEquals(0, new BigDecimal("60.00").compareTo(resumen.getBody().at("/datos/estadoPagos/montoTotal").decimalValue()));
        assertEquals(reporte(jwt, "/api/reportes/pagos" + fechas).path("estadoPagos"),
                resumen.getBody().at("/datos/estadoPagos"));
    }

    @Test
    void distinguePeriodoInvalidoYSinDatos() {
        var jwt = login(Rol.ADMINISTRADOR);
        simularHerramienta("platosMasVendidos", "2026-02-30", "2026-03-02");
        assertEquals("CLARIFICATION_REQUIRED",
                chat(jwt, Map.of("mensaje", "Platos más vendidos")).getBody().path("estado").asText());
        simularHerramienta("pagosPorPeriodo", "2026-10-01", "2026-10-31");
        assertEquals("NO_DATA", chat(jwt, Map.of("mensaje", "Pagos de octubre de 2026"))
                .getBody().path("estado").asText());
    }

    @Test
    void asistenteClienteSoloConsultaPedidosPropios() {
        String ruta = "/api/ia/cliente/chat";
        var mensaje = Map.of("mensaje", "¿Cómo va mi último pedido?");
        assertEquals(HttpStatus.FORBIDDEN, http.postForEntity(ruta,
                new HttpEntity<>(mensaje, login(Rol.DUENO)), JsonNode.class).getStatusCode());
        verifyNoInteractions(modelo);

        var cliente = usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Cliente IA")
                .correo(UUID.randomUUID() + "@ia.test").passwordHash(encoder.encode(PASSWORD))
                .rol(Rol.CLIENTE).habilitado(true).build());
        var pedido = new Pedido();
        pedido.setRestaurante(restaurante);
        pedido.setCliente(cliente);
        pedido.setTipoEntrega(TipoEntrega.LLEVAR);
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setTotal(BigDecimal.TEN);
        Long pedidoId = pedidos.saveAndFlush(pedido).getId();

        when(modelo.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("PEDIDO")))));
        var propio = http.postForEntity(ruta, new HttpEntity<>(mensaje, login(cliente.getCorreo())), JsonNode.class);
        assertEquals(HttpStatus.OK, propio.getStatusCode());
        assertEquals("ANSWER", propio.getBody().path("estado").asText());
        assertEquals("Tu pedido más reciente es el #" + pedidoId + ". Estado: pendiente.",
                propio.getBody().path("mensaje").asText());

        var ajeno = http.postForEntity(ruta, new HttpEntity<>(mensaje, login(Rol.CLIENTE)), JsonNode.class);
        assertEquals(HttpStatus.OK, ajeno.getStatusCode());
        assertEquals("NO_DATA", ajeno.getBody().path("estado").asText());
    }

    @Test
    void asistenteClienteUsaLaDireccionRegistradaDelRestauranteSeleccionado() {
        restaurante.setDireccion("Av. Demo 123");
        restaurantes.saveAndFlush(restaurante);
        when(modelo.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("DIRECCION")))));
        var cliente = login(Rol.CLIENTE);
        String ruta = "/api/ia/cliente/chat";
        var sinSeleccion = http.postForEntity(ruta,
                new HttpEntity<>(Map.of("mensaje", "¿Dónde queda?"), cliente), JsonNode.class);
        assertEquals("CLARIFICATION_REQUIRED", sinSeleccion.getBody().path("estado").asText());
        var conSeleccion = http.postForEntity(ruta,
                new HttpEntity<>(Map.of("mensaje", "¿Dónde queda?", "restauranteId", restaurante.getId()), cliente),
                JsonNode.class);
        assertEquals(HttpStatus.OK, conSeleccion.getStatusCode());
        assertEquals("Local de prueba IA queda en Av. Demo 123.", conSeleccion.getBody().path("mensaje").asText());
    }

    private JsonNode reporte(HttpHeaders jwt, String ruta) {
        var respuesta = http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(jwt), JsonNode.class);
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        return respuesta.getBody();
    }

    private void simularHerramienta(String nombre, String inicio, String fin) {
        org.mockito.Mockito.doAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            var opciones = (ToolCallingChatOptions) prompt.getOptions();
            var herramienta = opciones.getToolCallbacks().stream()
                    .filter(tool -> nombre.equals(tool.getToolDefinition().name())).findFirst().orElseThrow();
            herramienta.call("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(inicio, fin));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Texto simulado"))));
        }).when(modelo).call(any(Prompt.class));
    }

    private ResponseEntity<JsonNode> chat(HttpHeaders headers, Map<String, Object> mensaje) {
        return http.postForEntity(CHAT, new HttpEntity<>(mensaje, headers), JsonNode.class);
    }

    private HttpHeaders login(Rol rol) {
        String correo = UUID.randomUUID() + "@ia.test";
        usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Usuario de prueba IA").correo(correo)
                .passwordHash(encoder.encode(PASSWORD)).rol(rol).restaurante(restaurante).habilitado(true).build());
        return login(correo);
    }

    private HttpHeaders login(String correo) {
        var respuesta = http.postForEntity("/api/auth/login", Map.of("correo", correo, "password", PASSWORD),
                JsonNode.class);
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        var headers = new HttpHeaders();
        headers.setBearerAuth(respuesta.getBody().path("token").asText());
        return headers;
    }
}
