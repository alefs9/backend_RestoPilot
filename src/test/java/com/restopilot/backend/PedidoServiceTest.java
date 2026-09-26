package com.restopilot.backend;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.catalogo.entity.Plato;
import com.restopilot.backend.modules.pedidos.dto.PedidoRequestDTO;
import com.restopilot.backend.modules.pedidos.dto.PedidoResponseDTO;
import com.restopilot.backend.modules.pedidos.entity.DetallePedido;
import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import com.restopilot.backend.modules.pedidos.entity.TipoEntrega;
import com.restopilot.backend.modules.pedidos.mapper.PedidoMapper;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.pedidos.service.PedidoService;
import com.restopilot.backend.security.CurrentUser;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    // Fingimos los repositorios
    @Mock
    private RestauranteRepository restauranteRepository;

    @Mock
    private PedidoMapper pedidoMapper;

    // Fingimos el utilitario de seguridad
    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private PedidoService pedidoService;

    @Test
    @DisplayName("Debe lanzar excepción si el restaurante está inactivo")
    void crearPedido_RestauranteInactivo_LanzaExcepcion() {
        // 1. Preparamos el mock de CurrentUser
        Usuario usuarioMock = new Usuario();
        when(currentUser.get()).thenReturn(usuarioMock);

        // 2. Preparamos el mock del Restaurante (Fingimos que está inactivo)
        Restaurante restauranteInactivo = new Restaurante();
        restauranteInactivo.setId(1L);
        restauranteInactivo.setActivo(false); // Esto debe disparar tu BusinessRuleException

        when(restauranteRepository.findById(1L)).thenReturn(Optional.of(restauranteInactivo));

        // Pedido DELIVERY sin enviar mesa
        PedidoRequestDTO request = new PedidoRequestDTO(1L, TipoEntrega.DELIVERY, null, "Av. Lima 123", new ArrayList<>());

        // 3. Ejecutamos y validamos
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> {
            pedidoService.crearPedido(request);
        });

        assertTrue(exception.getMessage().contains("inactivo actualmente"));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el restaurante está fuera del horario de atención")
    void crearPedido_FueraDeHorario_LanzaExcepcion() {
        Usuario usuarioMock = new Usuario();
        when(currentUser.get()).thenReturn(usuarioMock);

        Restaurante restauranteCerrado = new Restaurante();
        restauranteCerrado.setId(1L);
        restauranteCerrado.setActivo(true);
        // Forzamos un horario en el que siempre estará cerrado al ejecutar la prueba
        restauranteCerrado.setHoraApertura(LocalTime.now().plusHours(1));
        restauranteCerrado.setHoraCierre(LocalTime.now().plusHours(5));

        when(restauranteRepository.findById(1L)).thenReturn(Optional.of(restauranteCerrado));

        PedidoRequestDTO request = new PedidoRequestDTO(1L, TipoEntrega.LLEVAR, null, null, new ArrayList<>());

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> {
            pedidoService.crearPedido(request);
        });

        assertTrue(exception.getMessage().contains("fuera de su horario de atención"));
    }

    @Test
    @DisplayName("La cola de cocina debe priorizar los platos con mayor tiempo de preparación")
    void getPendientesCocina_PriorizaPorTiempoDePreparacion() {
        // Mockeamos la extracción de ID del restaurante desde el JWT
        when(currentUser.getRestauranteId()).thenReturn(1L);

        // Pedido 1: Plato rápido (5 min) creado hace 10 minutos
        Pedido pedidoRapido = new Pedido();
        pedidoRapido.setFechaCreacion(LocalDateTime.now().minusMinutes(10));
        Plato platoRapido = new Plato();
        platoRapido.setTiempoPreparacionMinutos(5);
        DetallePedido detalleRapido = new DetallePedido();
        detalleRapido.setPlato(platoRapido);
        pedidoRapido.setDetalles(List.of(detalleRapido));

        // Pedido 2: Plato complejo (15 min) creado recién (hace 2 min)
        Pedido pedidoComplejo = new Pedido();
        pedidoComplejo.setFechaCreacion(LocalDateTime.now().minusMinutes(2));
        Plato platoComplejo = new Plato();
        platoComplejo.setTiempoPreparacionMinutos(15);
        DetallePedido detalleComplejo = new DetallePedido();
        detalleComplejo.setPlato(platoComplejo);
        pedidoComplejo.setDetalles(List.of(detalleComplejo));

        // El repositorio devuelve los datos sin ordenar (simulamos PostgreSQL)
        when(pedidoRepository.findPendientesParaCocina(1L, List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION)))
                .thenReturn(List.of(pedidoRapido, pedidoComplejo));

        // Mock simple del Mapper
        when(pedidoMapper.toResponse(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido p = invocation.getArgument(0);
            return new PedidoResponseDTO(p.getId(), null, null, null, null, null, null, null, null, null, p.getFechaCreacion(), null, null);
        });

        Page<PedidoResponseDTO> resultado = pedidoService.getPendientesCocina(PageRequest.of(0, 10));

        // Validación: El motor de priorización de tu PedidoService debe colocar el pedido complejo primero (15 min > 5 min), a pesar de que el rápido se creó antes.
        assertEquals(2, resultado.getContent().size());
        assertEquals(pedidoComplejo.getFechaCreacion(), resultado.getContent().getFirst().fechaCreacion());
    }
}