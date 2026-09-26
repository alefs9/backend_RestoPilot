package com.restopilot.backend.modules.pedidos.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.catalogo.entity.Plato;
import com.restopilot.backend.modules.catalogo.repository.PlatoRepository;
import com.restopilot.backend.modules.pedidos.dto.DetallePedidoRequestDTO;
import com.restopilot.backend.modules.pedidos.dto.PedidoRequestDTO;
import com.restopilot.backend.modules.pedidos.dto.PedidoResponseDTO;
import com.restopilot.backend.modules.pedidos.entity.DetallePedido;
import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import com.restopilot.backend.modules.pedidos.mapper.PedidoMapper;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.security.CurrentUser;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final RestauranteRepository restauranteRepository;
    private final PlatoRepository platoRepository;
    private final PedidoMapper pedidoMapper;

    // TODO: Descomentar inyección cuando Cristian termine el módulo Auth/Security
    // private final CurrentUser currentUser;

    @Transactional
    public PedidoResponseDTO crearPedido(PedidoRequestDTO request) {

        // Simulación temporal del cliente logueado (Hardcoding)
        // Usuario cliente = currentUser.get();
        Usuario cliente = new Usuario();
        cliente.setId(1L); // TODO: Borrar al integrar Auth

        Restaurante restaurante = restauranteRepository.findById(request.restauranteId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        // REGLA 1: Validación operativa del local (Horarios)
        if (Boolean.FALSE.equals(restaurante.getActivo())) {
            throw new BusinessRuleException("Lo sentimos, el restaurante se encuentra inactivo actualmente.");
        }

        LocalTime ahora = LocalTime.now();
        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null) {
            if (ahora.isBefore(restaurante.getHoraApertura()) || ahora.isAfter(restaurante.getHoraCierre())) {
                throw new BusinessRuleException("El restaurante está cerrado. Horario: "
                        + restaurante.getHoraApertura() + " a " + restaurante.getHoraCierre());
            }
        }

        Pedido pedido = new Pedido();
        pedido.setRestaurante(restaurante);
        pedido.setCliente(cliente);
        pedido.setTipoEntrega(request.tipoEntrega());
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setFechaCreacion(LocalDateTime.now());
        pedido.setDetalles(new ArrayList<>());

        // REGLA 2: Límite estricto de cancelación (Ventana IA de 5 min)
        pedido.setLimiteCancelacion(LocalDateTime.now().plusMinutes(5));

        BigDecimal total = BigDecimal.ZERO;

        // REGLA 3: Validación de disponibilidad sin afectar inventario
        for (DetallePedidoRequestDTO detalleDto : request.detalles()) {
            Plato plato = platoRepository.findById(detalleDto.platoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plato no encontrado"));

            if (!plato.getDisponible()) {
                throw new BusinessRuleException("El plato " + plato.getNombre() + " se encuentra agotado.");
            }

            DetallePedido detalle = new DetallePedido();
            detalle.setPlato(plato);
            detalle.setCantidad(detalleDto.cantidad());
            detalle.setNombrePlatoSnapshot(plato.getNombre());
            detalle.setNotas(detalleDto.notas());

            BigDecimal subtotal = plato.getPrecio().multiply(BigDecimal.valueOf(detalleDto.cantidad()));
            detalle.setSubtotal(subtotal);

            detalle.setPedido(pedido);
            pedido.getDetalles().add(detalle);
            total = total.add(subtotal);
        }

        pedido.setTotal(total);
        return pedidoMapper.toResponse(pedidoRepository.save(pedido));
    }

    @Transactional(readOnly = true)
    public Page<PedidoResponseDTO> getPendientesCocina(Pageable pageable) {

        // REGLA 4: Aislamiento SaaS - El repositorio filtra usando el ID seguro
        // Long miRestauranteId = currentUser.getRestauranteId();
        Long miRestauranteId = 1L; // TODO: Borrar al integrar Auth

        List<EstadoPedido> estados = List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION);
        List<Pedido> pedidosPendientes = pedidoRepository.findPendientesParaCocina(miRestauranteId, estados);

        // REGLA 5: Motor de Priorización en Cocina (Complejidad y Tiempos de 5 a 15 min)
        // Ordenamos en memoria priorizando los pedidos que tienen platos que toman más tiempo en prepararse.
        List<Pedido> pedidosPriorizados = pedidosPendientes.stream()
                .sorted(Comparator.comparingInt(this::calcularTiempoMaximoPreparacion).reversed()
                        .thenComparing(Pedido::getFechaCreacion))
                .collect(Collectors.toList());

        // Paginación manual de la lista priorizada
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), pedidosPriorizados.size());
        List<PedidoResponseDTO> pageContent = pedidosPriorizados.subList(start, end).stream()
                .map(pedidoMapper::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(pageContent, pageable, pedidosPriorizados.size());
    }

    // Método auxiliar para el motor de priorización
    private int calcularTiempoMaximoPreparacion(Pedido pedido) {
        return pedido.getDetalles().stream()
                .mapToInt(d -> d.getPlato().getTiempoPreparacionMinutos())
                .max()
                .orElse(0);
    }
}