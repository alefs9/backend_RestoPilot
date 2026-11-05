package com.restopilot.backend;

import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.reservas.entity.*;
import com.restopilot.backend.modules.reservas.dto.*;
import com.restopilot.backend.modules.reservas.repository.*;
import com.restopilot.backend.modules.reservas.service.*;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.modules.notificaciones.repository.NotificacionRepository;
import com.restopilot.backend.modules.pedidos.entity.*;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.reportes.service.ReporteService;
import com.restopilot.backend.security.JwtService;
import com.restopilot.backend.core.exception.BusinessRuleException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz",
    "restopilot.demo.enabled=false", "restopilot.reservas.expiracion.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:ciclo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
class CicloReservaIntegrationTest {
    // Solo se admite PostgreSQL de auditoría en un esquema efímero explícito.
    @DynamicPropertySource static void postgresOpcional(DynamicPropertyRegistry props) {
        String url = System.getenv("AUDIT_PG_TEST_URL");
        if (url == null) return;
        if (!url.matches("jdbc:postgresql://localhost:55439/restopilot_audit\\?currentSchema=ciclo_test_[a-z0-9_]+"))
            throw new IllegalStateException("Destino PostgreSQL de pruebas no permitido");
        props.add("spring.datasource.url", () -> url);
        props.add("spring.datasource.username", () -> "restopilot_audit");
        props.add("spring.datasource.password", () -> System.getenv("AUDIT_PG_TEST_PASSWORD"));
        props.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    static class Reloj extends Clock {
        private volatile Instant instante = Instant.parse("2026-10-20T13:00:00Z");
        public ZoneId getZone() { return ZoneId.of("America/Lima"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instante,zone); }
        public Instant instant() { return instante; }
        void fijar(LocalDateTime fecha) { instante=fecha.atZone(getZone()).toInstant(); }
    }
    @TestConfiguration static class Tiempo {
        @Bean @Primary Reloj relojCiclo() { return new Reloj(); }
    }
    @Autowired Reloj clock;
    @Autowired ReservaRepository reservas;
    @Autowired MesaRepository mesas;
    @Autowired UsuarioRepository usuarios;
    @Autowired RestauranteRepository restaurantes;
    @Autowired PedidoRepository pedidos;
    @Autowired NotificacionRepository notificaciones;
    @Autowired AtencionReservaService atencion;
    @Autowired ExpiracionReservasService expiracion;
    @Autowired ReservaService reservaService;
    @Autowired ReporteService reportes;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    Restaurante local, ajeno;
    Usuario cliente, admin, dueno, otro;
    Mesa mesa;
    final LocalDate fecha=LocalDate.of(2026,10,20);

    @BeforeEach void preparar() {
        SecurityContextHolder.clearContext();
        notificaciones.deleteAll(); reservas.deleteAll(); pedidos.deleteAll();
        mesas.deleteAll(); usuarios.deleteAll(); restaurantes.deleteAll();
        clock.fijar(fecha.atTime(8,0));
        local=restaurantes.save(Restaurante.builder().nombre("Ciclo").horaApertura(LocalTime.MIN)
                .horaCierre(LocalTime.of(23,59,59)).build());
        ajeno=restaurantes.save(Restaurante.builder().nombre("Ajeno").build());
        cliente=usuario("cliente",Rol.CLIENTE,null);
        admin=usuario("admin",Rol.ADMINISTRADOR,local);
        dueno=usuario("dueno",Rol.DUENO,local);
        otro=usuario("otro",Rol.ADMINISTRADOR,ajeno);
        mesa=Mesa.builder().restaurante(local).numero(1).capacidad(4).activo(true)
                .coordenadaX(0.0).coordenadaY(0.0).build();
        mesa=mesas.save(mesa);
        autenticar(admin);
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }
    Usuario usuario(String nombre,Rol rol,Restaurante r) {
        return usuarios.save(Usuario.builder().nombreCompleto(nombre).correo(nombre+"@ciclo.test")
                .passwordHash("no-se-usa-en-estos-tests").rol(rol).restaurante(r).build());
    }
    void autenticar(Usuario u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));
    }
    Reserva nueva(EstadoReserva estado) {
        return reservas.saveAndFlush(Reserva.builder().restaurante(local).mesa(mesa).usuario(cliente)
                .fecha(fecha).horaInicio(LocalTime.of(10,0)).horaFin(LocalTime.of(11,0))
                .numeroComensales(2).estado(estado).build());
    }
    String estado(Long id) {
        return jdbc.queryForObject("select estado from reservas where id=?",String.class,id);
    }
    @Test void completaDesdeInicioConLlegadaYEsTerminalSinDuplicados() {
        Reserva r=nueva(EstadoReserva.CONFIRMADA);
        atencion.registrarLlegada(r.getId());
        clock.fijar(fecha.atTime(9,59,59));
        assertThrows(BusinessRuleException.class,()->atencion.completar(r.getId()));
        assertEquals("CONFIRMADA",estado(r.getId()));
        clock.fijar(fecha.atTime(10,0));
        autenticar(dueno);
        var respuesta=atencion.completar(r.getId());
        assertEquals(EstadoReserva.COMPLETADA,respuesta.estado());
        assertNotNull(respuesta.llegadaRegistradaEn()); assertNotNull(respuesta.completadaEn());
        assertEquals("COMPLETADA",estado(r.getId()));
        long mensajes=notificaciones.count();
        assertThrows(BusinessRuleException.class,()->atencion.completar(r.getId()));
        assertThrows(BusinessRuleException.class,()->atencion.registrarLlegada(r.getId()));
        assertThrows(BusinessRuleException.class,()->reservaService.cancelarReserva(r.getId(),null));
        assertThrows(BusinessRuleException.class,()->reservaService.modificarReserva(r.getId(),
                new ReservaRequestDTO(mesa.getId(),local.getId(),fecha,LocalTime.NOON,LocalTime.of(13,0),2,null)));
        assertEquals(mensajes,notificaciones.count());
        assertEquals("COMPLETADA",estado(r.getId()));
    }
    @ParameterizedTest @CsvSource({"-1,false","0,true","1,true"})
    void pendienteExpiraExactamenteAlInicio(int segundos,boolean vence) {
        Reserva r=nueva(EstadoReserva.PENDIENTE);
        clock.fijar(fecha.atTime(10,0).plusSeconds(segundos));
        assertEquals(vence?1:0,expiracion.expirarVencidas());
        assertEquals(vence?"EXPIRADA":"PENDIENTE",estado(r.getId()));
        assertEquals(1,reservas.count());
        assertEquals(0,expiracion.expirarVencidas());
    }
    @ParameterizedTest @CsvSource({"-1,false","0,true","1,true"})
    void confirmadaSinLlegadaExpiraExactamenteAMinutoQuince(int segundos,boolean vence) {
        Reserva r=nueva(EstadoReserva.CONFIRMADA);
        clock.fijar(fecha.atTime(10,15).plusSeconds(segundos));
        assertEquals(vence?1:0,expiracion.expirarVencidas());
        assertEquals(vence?"EXPIRADA":"CONFIRMADA",estado(r.getId()));
        if(vence) {
            long mensajes=notificaciones.count();
            assertThrows(BusinessRuleException.class,()->atencion.registrarLlegada(r.getId()));
            assertThrows(BusinessRuleException.class,()->atencion.completar(r.getId()));
            assertEquals(mensajes,notificaciones.count());
            assertEquals("EXPIRADA",estado(r.getId()));
        }
    }
    @Test void llegadaAntesDelLimiteImpideExpiracionYNoEquivaleACompletar() {
        Reserva r=nueva(EstadoReserva.CONFIRMADA);
        clock.fijar(fecha.atTime(10,14,59));
        atencion.registrarLlegada(r.getId());
        clock.fijar(fecha.atTime(12,0));
        assertEquals(0,expiracion.expirarVencidas());
        assertEquals("CONFIRMADA",estado(r.getId()));
        assertEquals(0,reportes.generarReporteConsolidado(fecha,fecha).reservasCompletadas());
        assertEquals(EstadoReserva.COMPLETADA,atencion.completar(r.getId()).estado());
    }
    @Test void sinLlegadaNoPuedeCompletarYExpirarLiberaMesaConservandoHistorial() {
        Reserva r=nueva(EstadoReserva.CONFIRMADA);
        clock.fijar(fecha.atTime(10,1));
        assertThrows(BusinessRuleException.class,()->atencion.completar(r.getId()));
        clock.fijar(fecha.atTime(10,15));
        autenticar(cliente);
        var disponibilidad=reservaService.consultarDisponibilidad(local.getId(),fecha,
                LocalTime.of(10,16),LocalTime.of(10,45),2);
        assertTrue(disponibilidad.getFirst().disponible());
        assertEquals("EXPIRADA",estado(r.getId()));
        assertTrue(reservaService.obtenerMisReservas().stream().anyMatch(x->x.id().equals(r.getId())));
    }
    @ParameterizedTest @CsvSource({"PENDIENTE","CANCELADA","RECHAZADA","EXPIRADA"})
    void estadosNoConfirmadosNoAdmitenLlegadaNiCompletar(EstadoReserva estado) {
        Reserva r=nueva(estado);
        assertThrows(BusinessRuleException.class,()->atencion.registrarLlegada(r.getId()));
        assertThrows(BusinessRuleException.class,()->atencion.completar(r.getId()));
        assertEquals(estado.name(),estado(r.getId()));
        assertEquals(0,notificaciones.count());
    }
    @Test void endpointsProtegenRolesRestauranteYRecursoInexistente() throws Exception {
        Reserva r=nueva(EstadoReserva.CONFIRMADA);
        for(String accion:new String[]{"llegada","completar"}) {
            String path="/api/reservas/"+r.getId()+"/"+accion;
            mvc.perform(patch(path)).andExpect(status().isForbidden());
            mvc.perform(patch(path).header("Authorization","Bearer "+jwt.generateToken(cliente)))
                    .andExpect(status().isForbidden());
            mvc.perform(patch(path).header("Authorization","Bearer "+jwt.generateToken(otro)))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/reservas/999999/"+accion)
                    .header("Authorization","Bearer "+jwt.generateToken(admin)))
                    .andExpect(status().isNotFound());
        }
        assertEquals("CONFIRMADA",estado(r.getId())); assertEquals(0,notificaciones.count());
        mvc.perform(patch("/api/reservas/"+r.getId()+"/llegada")
                .header("Authorization","Bearer "+jwt.generateToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.llegadaRegistradaEn").isNotEmpty());
        clock.fijar(fecha.atTime(10,0));
        mvc.perform(patch("/api/reservas/"+r.getId()+"/completar")
                .header("Authorization","Bearer "+jwt.generateToken(dueno)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }
    @Test void reporteSeparaPedidosReservasYRangoYRestaurante() {
        Reserva r=nueva(EstadoReserva.CONFIRMADA);
        atencion.registrarLlegada(r.getId()); clock.fijar(fecha.atTime(10,0)); atencion.completar(r.getId());
        Pedido p=new Pedido();p.setCliente(cliente);p.setRestaurante(local);p.setEstado(EstadoPedido.ENTREGADO);
        p.setTipoEntrega(TipoEntrega.LLEVAR);p.setTotal(BigDecimal.TEN);p.setFechaCreacion(fecha.atTime(9,0));
        pedidos.saveAndFlush(p);
        var reporte=reportes.generarReporteConsolidado(fecha,fecha);
        assertEquals(1,reporte.pedidosAtendidos());assertEquals(1,reporte.reservasCompletadas());
        assertEquals(2,reporte.volumenAtenciones()); // compatibilidad; dos operaciones, no dos visitas.
        assertNull(reportes.generarReporteConsolidado(fecha.plusDays(1),fecha.plusDays(1)).reservasCompletadas());
        autenticar(otro);
        assertNull(reportes.generarReporteConsolidado(fecha,fecha).reservasCompletadas());
    }
    @Test void dosCompletadosSimultaneosPersistenUnaSolaAtencionYNotificacion() throws Exception {
        Reserva r=nueva(EstadoReserva.CONFIRMADA);atencion.registrarLlegada(r.getId());
        clock.fijar(fecha.atTime(10,0));long antes=notificaciones.count();
        ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier barrera=new CyclicBarrier(2);
        Callable<Boolean> tarea=()->{autenticar(admin);try{barrera.await(10,TimeUnit.SECONDS);
            atencion.completar(r.getId());return true;
        }catch(BusinessRuleException e){return false;}finally{SecurityContextHolder.clearContext();}};
        try {
            var a=pool.submit(tarea);var b=pool.submit(tarea);
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals("COMPLETADA",estado(r.getId()));assertEquals(antes+1,notificaciones.count());
    }
    @ParameterizedTest @CsvSource({"-1,true","0,false","1,false"})
    void confirmarSoloAntesDeInicio(int segundos,boolean permite) {
        Reserva r=nueva(EstadoReserva.PENDIENTE);
        clock.fijar(fecha.atTime(10,0).plusSeconds(segundos));
        var solicitud=new ProcesarReservaRequestDTO(EstadoReserva.CONFIRMADA,null);
        if(permite) assertEquals(EstadoReserva.CONFIRMADA,reservaService.procesarReserva(r.getId(),solicitud).estado());
        else assertThrows(BusinessRuleException.class,()->reservaService.procesarReserva(r.getId(),solicitud));
        assertEquals(permite?"CONFIRMADA":"EXPIRADA",estado(r.getId()));
        assertEquals(1,notificaciones.count());
    }
}
