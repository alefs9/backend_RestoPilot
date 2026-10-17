package com.restopilot.backend;

import com.restopilot.backend.core.exception.*;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.*;
import com.restopilot.backend.modules.catalogo.dto.*;
import com.restopilot.backend.modules.catalogo.repository.*;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import com.restopilot.backend.modules.pedidos.entity.*;
import com.restopilot.backend.modules.pedidos.dto.*;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.pedidos.service.PedidoService;
import com.restopilot.backend.modules.pagos.entity.*;
import com.restopilot.backend.modules.pagos.dto.PagoRequestDTO;
import com.restopilot.backend.modules.pagos.repository.PagoRepository;
import com.restopilot.backend.modules.pagos.service.PagoService;
import com.restopilot.backend.modules.reservas.entity.*;
import com.restopilot.backend.modules.reservas.dto.*;
import com.restopilot.backend.modules.reservas.repository.*;
import com.restopilot.backend.modules.reservas.service.ReservaService;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.modules.reportes.service.ReporteService;
import com.restopilot.backend.modules.reportes.dto.TipoReporte;
import com.restopilot.backend.modules.notificaciones.repository.NotificacionRepository;
import com.restopilot.backend.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz",
        "restopilot.demo.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:reglas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false"})
@AutoConfigureMockMvc
@Transactional
class ReglasNegocioIntegrationTest {
    @TestConfiguration
    static class TiempoFijo {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-07T17:00:00Z"), ZoneId.of("America/Lima"));
        }
    }
    @Autowired PedidoService pedidos;
    @Autowired PagoService pagos;
    @Autowired ReservaService reservas;
    @Autowired PlatoService catalogo;
    @Autowired ReporteService reportes;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PagoRepository pagoRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired RestauranteRepository restauranteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired PlatoRepository platoRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired NotificacionRepository notificaciones;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    Restaurante restaurante, otroRestaurante;
    Usuario cliente, otroCliente, admin, otroAdmin;
    Categoria categoria;
    Plato plato;
    Mesa mesa;
    final LocalDate fecha = LocalDate.of(2026, 10, 7);

    @BeforeEach void preparar() {
        restaurante = restauranteRepository.save(Restaurante.builder().nombre("Pruebas")
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
        otroRestaurante = restauranteRepository.save(Restaurante.builder().nombre("Otro restaurante").build());
        cliente = usuario("cliente-prueba", Rol.CLIENTE, null);
        otroCliente = usuario("otro-cliente", Rol.CLIENTE, null);
        admin = usuario("admin-prueba", Rol.ADMINISTRADOR, restaurante);
        otroAdmin = usuario("otro-admin", Rol.ADMINISTRADOR, otroRestaurante);
        categoria = new Categoria(); categoria.setRestauranteId(restaurante.getId()); categoria.setNombre("Principales");
        categoria = categoriaRepository.save(categoria);
        plato = plato("Lomo", "MEDIA", 10);
        mesa = new Mesa(); mesa.setRestaurante(restaurante); mesa.setNumero(1); mesa.setCapacidad(4); mesa.setActivo(true);
        mesa.setCoordenadaX(0.0); mesa.setCoordenadaY(0.0);
        mesa = mesaRepository.save(mesa);
        autenticar(cliente);
    }
    @AfterEach void limpiarSesion() { SecurityContextHolder.clearContext(); }

    Usuario usuario(String nombre, Rol rol, Restaurante local) {
        return usuarioRepository.save(Usuario.builder().nombreCompleto(nombre).correo(nombre + "@test.com")
                .passwordHash("no-se-utiliza-en-estas-pruebas").rol(rol).restaurante(local).build());
    }
    Plato plato(String nombre, String complejidad, int minutos) {
        Plato p = new Plato(); p.setRestauranteId(restaurante.getId()); p.setCategoria(categoria);
        p.setNombre(nombre); p.setPrecio(new BigDecimal("20.00")); p.setComplejidad(complejidad); p.setTiempoPreparacionMinutos(minutos);
        return platoRepository.save(p);
    }
    void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
    }
    PedidoRequestDTO solicitud(Plato p) {
        return new PedidoRequestDTO(restaurante.getId(), TipoEntrega.DELIVERY, null, "Av. Lima 123",
                List.of(new DetallePedidoRequestDTO(p.getId(), 2, "Sin ají")));
    }
    Long crearPedido() { autenticar(cliente); return pedidos.crearPedido(solicitud(plato)).id(); }
    Reserva reservar(LocalTime inicio, LocalTime fin) {
        autenticar(cliente);
        var dto = reservas.registrarReserva(new ReservaRequestDTO(mesa.getId(), restaurante.getId(), fecha, inicio, fin, 2, null));
        return reservaRepository.findById(dto.id()).orElseThrow();
    }
    void entregar(Long id) {
        autenticar(admin);
        pedidos.actualizarEstado(id, EstadoPedido.EN_PREPARACION);
        pedidos.actualizarEstado(id, EstadoPedido.LISTO);
        pedidos.actualizarEstado(id, EstadoPedido.ENTREGADO);
    }

    @Test void pedidoGuardaImporteDireccionYPrecioHistorico() {
        Long id = crearPedido();
        plato.setPrecio(new BigDecimal("99.00")); platoRepository.saveAndFlush(plato);
        var detalle = pedidos.getById(id);
        assertEquals(new BigDecimal("40.00"), detalle.total());
        assertEquals("Av. Lima 123", detalle.direccionEntrega());
        assertEquals(plato.getId(), detalle.detalles().getFirst().platoId());
        assertEquals(new BigDecimal("20.00"), detalle.detalles().getFirst().precioUnitario());
        assertEquals(EstadoPago.PENDIENTE, pagoRepository.findByPedidoId(id).orElseThrow().getEstado());
    }
    @Test void clienteNoConsultaPedidoAjeno() {
        Long id = crearPedido(); autenticar(otroCliente);
        assertThrows(ResourceNotFoundException.class, () -> pedidos.getById(id));
        assertThrows(ResourceNotFoundException.class, () -> pagos.obtenerPagoPorPedido(id));
    }
    @Test void administradorNoGestionaPedidoDeOtroRestaurante() {
        Long id = crearPedido(); autenticar(otroAdmin);
        assertThrows(AccessDeniedException.class, () -> pedidos.actualizarEstado(id, EstadoPedido.EN_PREPARACION));
    }
    @Test void pedidoRechazaPlatoDeOtroRestaurante() {
        plato.setRestauranteId(otroRestaurante.getId()); platoRepository.saveAndFlush(plato);
        assertThrows(BusinessRuleException.class, () -> pedidos.crearPedido(solicitud(plato)));
    }
    @Test void pedidoRechazaPlatoAgotadoYListaVacia() {
        plato.setDisponible(false);
        assertThrows(BusinessRuleException.class, () -> pedidos.crearPedido(solicitud(plato)));
        assertThrows(BusinessRuleException.class, () -> pedidos.crearPedido(new PedidoRequestDTO(restaurante.getId(), TipoEntrega.LLEVAR, null, null, List.of())));
    }
    @Test void noPermiteSaltarPreparacionNiReabrirPedidoEntregado() {
        Long id = crearPedido(); autenticar(admin);
        assertThrows(BusinessRuleException.class, () -> pedidos.actualizarEstado(id, EstadoPedido.ENTREGADO));
        entregar(id);
        assertThrows(BusinessRuleException.class, () -> pedidos.actualizarEstado(id, EstadoPedido.EN_PREPARACION));
        assertEquals(3, notificaciones.findByUsuarioIdOrderByFechaCreacionDesc(cliente.getId(), PageRequest.of(0, 20)).getTotalElements());
    }
    @Test void prioridadEsComplejidadLuegoTiempoLuegoAntiguedad() {
        Long medio = pedidos.crearPedido(solicitud(plato)).id();
        Long altaCorta = pedidos.crearPedido(solicitud(plato("Complejo corto", "ALTA", 5))).id();
        Plato complejo = plato("Complejo largo", "ALTA", 15);
        Long antiguo = pedidos.crearPedido(solicitud(complejo)).id();
        Long reciente = pedidos.crearPedido(solicitud(complejo)).id();
        pedidoRepository.findById(antiguo).orElseThrow().setFechaCreacion(fecha.atTime(11, 0));
        autenticar(admin);
        assertEquals(List.of(antiguo, reciente, altaCorta, medio), pedidos.getPendientesCocina(PageRequest.of(0, 20)).getContent().stream().map(PedidoResponseDTO::id).toList());
        assertTrue(pedidos.getPendientesCocina(PageRequest.of(100, 20)).isEmpty());
    }
    @Test void pagoValidaImporteYEsInmutableTrasConfirmarse() {
        Long id = crearPedido(); autenticar(admin);
        var incorrecto = PagoRequestDTO.builder().monto(new BigDecimal("1.00")).metodo(MetodoPago.EFECTIVO).build();
        assertThrows(BusinessRuleException.class, () -> pagos.registrarPago(id, incorrecto));
        var correcto = PagoRequestDTO.builder().monto(new BigDecimal("40.00")).metodo(MetodoPago.EFECTIVO).build();
        assertEquals(EstadoPago.PAGADO, pagos.registrarPago(id, correcto).getEstado());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> pagos.registrarPago(id, correcto));
        assertThrows(BusinessRuleException.class, () -> pedidos.actualizarPedido(id, solicitud(plato)));
        assertThrows(BusinessRuleException.class, () -> pedidos.eliminarPedido(id));
    }
    @Test void pagoElectronicoExigeReferenciaYClienteNoPuedeRegistrarPago() {
        Long id = crearPedido();
        var pago = PagoRequestDTO.builder().monto(new BigDecimal("40.00")).metodo(MetodoPago.YAPE).build();
        assertThrows(AccessDeniedException.class, () -> pagos.registrarPago(id, pago));
        autenticar(admin);
        assertThrows(BusinessRuleException.class, () -> pagos.registrarPago(id, pago));
        pago.setCodigoReferencia("OPERACION-123");
        assertNotNull(pagos.registrarPago(id, pago).getFechaPago());
    }
    @Test void cancelarPedidoConservaHistorialYExcluyePagoPendienteDelReporte() {
        Long id = crearPedido(); autenticar(admin); pedidos.eliminarPedido(id);
        assertEquals(EstadoPedido.CANCELADO, pedidoRepository.findById(id).orElseThrow().getEstado());
        assertTrue(pagoRepository.findByPedidoId(id).isPresent());
        assertEquals(0, reportes.generarReportePagos(fecha, fecha).estadoPagos().pagosPendientes());
    }
    @Test void disponibilidadFiltraCapacidadYDetectaSolapamientos() {
        reservar(LocalTime.of(15, 0), LocalTime.of(16, 0));
        assertFalse(reservas.consultarDisponibilidad(restaurante.getId(), fecha, LocalTime.of(15, 30), LocalTime.of(16, 30), 2).getFirst().disponible());
        assertTrue(reservas.consultarDisponibilidad(restaurante.getId(), fecha, LocalTime.of(16, 0), LocalTime.of(17, 0), 2).getFirst().disponible());
        assertTrue(reservas.consultarDisponibilidad(restaurante.getId(), fecha, LocalTime.of(16, 0), LocalTime.of(17, 0), 5).isEmpty());
        assertThrows(BusinessRuleException.class, () -> reservar(LocalTime.of(15, 30), LocalTime.of(16, 30)));
    }
    @Test void confirmaReservaSoloUnaVezYNotifica() {
        Reserva r = reservar(LocalTime.of(15, 0), LocalTime.of(16, 0)); autenticar(admin);
        reservas.procesarReserva(r.getId(), new ProcesarReservaRequestDTO(EstadoReserva.CONFIRMADA, null));
        assertThrows(BusinessRuleException.class, () -> reservas.procesarReserva(r.getId(), new ProcesarReservaRequestDTO(EstadoReserva.CONFIRMADA, null)));
        assertEquals(1, notificaciones.findByUsuarioIdOrderByFechaCreacionDesc(cliente.getId(), PageRequest.of(0, 20)).getTotalElements());
    }
    @Test void rechazoExigeMotivoLiberaMesaYBloqueaModificacion() {
        Reserva r = reservar(LocalTime.of(15, 0), LocalTime.of(16, 0)); autenticar(admin);
        assertThrows(BusinessRuleException.class, () -> reservas.procesarReserva(r.getId(), new ProcesarReservaRequestDTO(EstadoReserva.RECHAZADA, " ")));
        var rechazo = reservas.procesarReserva(r.getId(), new ProcesarReservaRequestDTO(EstadoReserva.RECHAZADA, "Cierre excepcional"));
        assertEquals("Cierre excepcional", rechazo.motivoRechazo());
        autenticar(cliente);
        assertTrue(reservas.consultarDisponibilidad(restaurante.getId(), fecha, LocalTime.of(15, 0), LocalTime.of(16, 0), 2).getFirst().disponible());
        assertThrows(BusinessRuleException.class, () -> reservas.modificarReserva(r.getId(), new ReservaRequestDTO(mesa.getId(), restaurante.getId(), fecha, LocalTime.of(16, 0), LocalTime.of(17, 0), 2, null)));
    }
    @Test void administradorAjenoNoProcesaReserva() {
        Reserva r = reservar(LocalTime.of(15, 0), LocalTime.of(16, 0)); autenticar(otroAdmin);
        assertThrows(AccessDeniedException.class, () -> reservas.procesarReserva(r.getId(), new ProcesarReservaRequestDTO(EstadoReserva.CONFIRMADA, null)));
    }
    @Test void cancelacionExigeMasDeDosHorasInclusoEnElLimite() {
        Reserva limite = reservar(LocalTime.of(14, 0), LocalTime.of(15, 0));
        assertThrows(BusinessRuleException.class, () -> reservas.cancelarReserva(limite.getId(), null));
        Reserva permitida = reservar(LocalTime.of(15, 0), LocalTime.of(16, 0));
        assertEquals(EstadoReserva.CANCELADA, reservas.cancelarReserva(permitida.getId(), null).estado());
    }
    @Test void reservaRespetaHorarioCapacidadYModuloFisico() {
        assertThrows(BusinessRuleException.class, () -> reservas.registrarReserva(new ReservaRequestDTO(mesa.getId(), restaurante.getId(), fecha, LocalTime.of(19, 0), LocalTime.of(21, 0), 2, null)));
        assertThrows(BusinessRuleException.class, () -> reservas.registrarReserva(new ReservaRequestDTO(mesa.getId(), restaurante.getId(), fecha, LocalTime.of(15, 0), LocalTime.of(16, 0), 5, null)));
        restaurante.setTieneAtencionFisica(false);
        assertThrows(BusinessRuleException.class, () -> reservar(LocalTime.of(15, 0), LocalTime.of(16, 0)));
    }
    @Test void catalogoFiltraCategoriaYValidaTiempoYComplejidad() {
        Categoria otra = new Categoria(); otra.setRestauranteId(restaurante.getId()); otra.setNombre("Bebidas"); otra = categoriaRepository.save(otra);
        assertEquals(1, catalogo.listar(restaurante.getId(), categoria.getId()).size());
        assertTrue(catalogo.listar(restaurante.getId(), otra.getId()).isEmpty());
        autenticar(admin);
        PlatoRequestDTO dto = new PlatoRequestDTO(); dto.setTiempoPreparacionMinutos(16);
        assertThrows(BusinessRuleException.class, () -> catalogo.actualizar(plato.getId(), dto));
        dto.setTiempoPreparacionMinutos(15); dto.setComplejidad("desconocida");
        assertThrows(BusinessRuleException.class, () -> catalogo.actualizar(plato.getId(), dto));
        dto.setComplejidad("alta"); assertEquals("ALTA", catalogo.actualizar(plato.getId(), dto).getComplejidad());
    }
    @Test void reporteConsolidaDatosRealesYExcluyeOtroRestaurante() {
        Long id = crearPedido(); entregar(id);
        pagos.registrarPago(id, PagoRequestDTO.builder().monto(new BigDecimal("40.00")).metodo(MetodoPago.EFECTIVO).build());
        Reserva r = reservar(LocalTime.of(15, 0), LocalTime.of(16, 0)); autenticar(admin);
        reservas.procesarReserva(r.getId(), new ProcesarReservaRequestDTO(EstadoReserva.CONFIRMADA, null));
        Long pendiente = crearPedido(); autenticar(admin);
        var resultado = reportes.generarReporteConsolidado(fecha, fecha);
        assertEquals(List.of("Lomo (2 unidades)"), resultado.platosMasVendidos());
        assertEquals(2, resultado.horariosMayorDemanda().get("12:00 - 13:00"));
        assertEquals(1, resultado.volumenAtenciones());
        assertEquals(1, resultado.pedidosAtendidos());
        assertEquals(0, resultado.reservasCompletadas());
        assertEquals(8.33, resultado.ocupacionMesasPorcentaje());
        assertEquals(new BigDecimal("40.00"), resultado.estadoPagos().montoTotal());
        assertEquals(1, resultado.estadoPagos().pedidosPagados());
        assertEquals(1, resultado.estadoPagos().pagosPendientes());
        autenticar(otroAdmin);
        assertNull(reportes.generarReporteConsolidado(fecha, fecha).estadoPagos());
    }
    @Test void reporteSinDatosRangoInvalidoYReporteEspecifico() {
        autenticar(admin);
        assertEquals("No existen datos registrados para el rango de fechas seleccionado.", reportes.generarReporteConsolidado(fecha, fecha).mensaje());
        assertThrows(BusinessRuleException.class, () -> reportes.generarReporteConsolidado(fecha.plusDays(1), fecha));
        crearPedido(); autenticar(admin);
        var resultado = reportes.generarReporte(fecha, fecha, TipoReporte.PAGOS);
        assertNull(resultado.platosMasVendidos()); assertEquals(1, resultado.estadoPagos().pagosPendientes());
    }
    @Test void apiJwtNiegaGestionDeCatalogoAClienteYNoPermiteAutoregistroAdmin() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/platos").header("Authorization", "Bearer " + jwt.generateToken(cliente))
                .contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"nombreCompleto\":\"Intruso\",\"correo\":\"intruso@test.com\",\"password\":\"Password123!\",\"rol\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void apiExponeFiltroConfirmacionYNotificaciones() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/platos").param("restauranteId", restaurante.getId().toString())
                        .param("categoriaId", categoria.getId().toString()).header("Authorization", "Bearer " + jwt.generateToken(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.platos[0].nombre").value("Lomo"));
        Reserva r = reservar(LocalTime.of(15, 0), LocalTime.of(16, 0));
        SecurityContextHolder.clearContext();
        mvc.perform(patch("/api/reservas/{id}/estado", r.getId()).header("Authorization", "Bearer " + jwt.generateToken(admin))
                        .contentType("application/json").content("{\"estado\":\"CONFIRMADA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mvc.perform(get("/api/notificaciones/me").header("Authorization", "Bearer " + jwt.generateToken(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void contratosDeCatalogoDisponibilidadEHistorial() throws Exception {
        SecurityContextHolder.clearContext();
        String token = jwt.generateToken(cliente);
        mvc.perform(get("/api/platos").param("restauranteId", restaurante.getId().toString())
                        .param("categoriaId", categoria.getId().toString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.platos").isArray());
        mvc.perform(get("/api/reservas/disponibilidad").param("restauranteId", restaurante.getId().toString())
                        .param("fecha", fecha.toString()).param("horaInicio", "15:00").param("horaFin", "16:00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mesas[0].disponible").value(true));
        mvc.perform(get("/api/pedidos/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pedidos").isEmpty())
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get("/api/reservas/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reservas").isEmpty());
    }

    @Test void entradasInvalidasResponden400YPlatoInexistente404() throws Exception {
        Long id = crearPedido();
        SecurityContextHolder.clearContext();
        String token = jwt.generateToken(admin);
        mvc.perform(get("/api/platos/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value("Plato no encontrado."));
        mvc.perform(patch("/api/pedidos/{id}/estado", id).param("nuevoEstado", "INVALIDO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("El valor de un parámetro no es válido."));
        for (String ruta : List.of("/api/reportes/operativos", "/api/reportes/pagos")) {
            mvc.perform(get(ruta).param("fechaInicio", "no-fecha").param("fechaFin", fecha.toString())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("El valor de un parámetro no es válido."));
        }
        mvc.perform(get("/api/reportes/operativos").param("fechaFin", fecha.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("Falta un parámetro obligatorio."));
        assertEquals(EstadoPedido.PENDIENTE, pedidoRepository.findById(id).orElseThrow().getEstado());
        assertEquals(0, notificaciones.count());
    }

    @Test void disponibilidadRechazaHoraPasadaYRestauranteInactivo() {
        assertThrows(BusinessRuleException.class, () -> reservas.consultarDisponibilidad(
                restaurante.getId(), fecha, LocalTime.of(9, 0), LocalTime.of(10, 0), 2));
        assertThrows(BusinessRuleException.class, () -> reservar(LocalTime.of(9, 0), LocalTime.of(10, 0)));
        restaurante.setActivo(false); restauranteRepository.saveAndFlush(restaurante);
        assertThrows(BusinessRuleException.class, () -> reservas.consultarDisponibilidad(
                restaurante.getId(), fecha, LocalTime.of(15, 0), LocalTime.of(16, 0), 2));
        assertThrows(BusinessRuleException.class, () -> reservar(LocalTime.of(15, 0), LocalTime.of(16, 0)));
        assertEquals(0, reservaRepository.count());
        restaurante.setActivo(true); restauranteRepository.saveAndFlush(restaurante);
        assertTrue(reservas.consultarDisponibilidad(restaurante.getId(), fecha,
                LocalTime.of(15, 0), LocalTime.of(16, 0), 2).getFirst().disponible());
    }

    @Test void catalogoVacioYDisponibilidadSinCapacidadMantienenContrato() throws Exception {
        SecurityContextHolder.clearContext(); String token = jwt.generateToken(cliente);
        mvc.perform(get("/api/platos").param("restauranteId", otroRestaurante.getId().toString())
                        .param("categoriaId", "999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.platos").isEmpty())
                .andExpect(jsonPath("$.mensaje").value("No hay platos disponibles en esta categoría."));
        mvc.perform(get("/api/reservas/disponibilidad").param("restauranteId", restaurante.getId().toString())
                        .param("fecha", fecha.toString()).param("horaInicio", "15:00").param("horaFin", "16:00")
                        .param("numeroComensales", "5").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mesas").isEmpty())
                .andExpect(jsonPath("$.mensaje").value("No hay disponibilidad para el horario seleccionado."));
    }

    @Test void mensajesDeExitoConservanIdsEnRaiz() throws Exception {
        Reserva reserva = reservar(LocalTime.of(15, 0), LocalTime.of(16, 0));
        SecurityContextHolder.clearContext(); String token = jwt.generateToken(admin);
        String body = "{\"numero\":42,\"capacidad\":6,\"coordenadaX\":1,\"coordenadaY\":2}";
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String alta = mvc.perform(post("/api/mesas").contentType("application/json").content(body)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Mesa registrada con éxito."))
                .andExpect(jsonPath("$.id").isNumber()).andReturn().getResponse().getContentAsString();
        long mesaId = mapper.readTree(alta).get("id").asLong();
        mvc.perform(put("/api/mesas/{id}", mesaId).contentType("application/json").content(body)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Mesa actualizada con éxito."));
        String cambio = "{\"mesaId\":" + mesa.getId() + ",\"restauranteId\":" + restaurante.getId()
                + ",\"fecha\":\"2026-10-07\",\"horaInicio\":\"16:00\",\"horaFin\":\"17:00\",\"numeroComensales\":3}";
        mvc.perform(put("/api/reservas/{id}", reserva.getId()).contentType("application/json").content(cambio)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reserva.getId()))
                .andExpect(jsonPath("$.mensaje").value("Reserva modificada con éxito."));
        assertEquals(3, reservaRepository.findById(reserva.getId()).orElseThrow().getNumeroComensales());
    }
}