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
    private final CurrentUser currentUser;

    @Transactional
    public PedidoResponseDTO crearPedido(PedidoRequestDTO request) {
        Usuario cliente = currentUser.get();

        Restaurante restaurante = restauranteRepository.findById(request.restauranteId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        if (Boolean.FALSE.equals(restaurante.getActivo())) {
            throw new BusinessRuleException("Lo sentimos, el restaurante se encuentra inactivo actualmente.");
        }

        LocalTime ahora = LocalTime.now();
        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null) {
            if (ahora.isBefore(restaurante.getHoraApertura()) || ahora.isAfter(restaurante.getHoraCierre())) {
                throw new BusinessRuleException("El restaurante está fuera de su horario de atención ("
                        + restaurante.getHoraApertura() + " a " + restaurante.getHoraCierre() + ").");
            }
        }

        Pedido pedido = new Pedido();
        pedido.setRestaurante(restaurante);
        pedido.setCliente(cliente);
        pedido.setTipoEntrega(request.tipoEntrega());
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setFechaCreacion(LocalDateTime.now());
        pedido.setDetalles(new ArrayList<>());

        pedido.setLimiteCancelacion(LocalDateTime.now().plusMinutes(5));

        BigDecimal total = BigDecimal.ZERO;

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
    public Page<PedidoResponseDTO> getMisPedidos(Pageable pageable) {
        Usuario cliente = currentUser.get();
        return pedidoRepository.findByClienteId(cliente.getId(), pageable)
                .map(pedidoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO getById(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return pedidoMapper.toResponse(pedido);
    }

    @Transactional(readOnly = true)
    public Page<PedidoResponseDTO> getPendientesCocina(Pageable pageable) {
        Long miRestauranteId = currentUser.getRestauranteId();

        List<EstadoPedido> estados = List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION);
        List<Pedido> pedidosPendientes = pedidoRepository.findPendientesParaCocina(miRestauranteId, estados);

        List<Pedido> pedidosPriorizados = pedidosPendientes.stream()
                .sorted(Comparator.comparingInt(this::calcularTiempoMaximoPreparacion).reversed()
                        .thenComparing(Pedido::getFechaCreacion))
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), pedidosPriorizados.size());

        List<PedidoResponseDTO> pageContent = new ArrayList<>();
        if (start <= end) {
            pageContent = pedidosPriorizados.subList(start, end).stream()
                    .map(pedidoMapper::toResponse)
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(pageContent, pageable, pedidosPriorizados.size());
    }

    @Transactional
    public PedidoResponseDTO actualizarPedido(Long id, PedidoRequestDTO request) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        pedido.setTipoEntrega(request.tipoEntrega());
        pedido.getDetalles().clear();

        BigDecimal total = BigDecimal.ZERO;

        for (DetallePedidoRequestDTO detalleDto : request.detalles()) {
            Plato plato = platoRepository.findById(detalleDto.platoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plato no encontrado"));

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

    @Transactional
    public PedidoResponseDTO actualizarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        pedido.setEstado(nuevoEstado);
        return pedidoMapper.toResponse(pedidoRepository.save(pedido));
    }

    @Transactional
    public void eliminarPedido(Long id) {
        if (!pedidoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pedido no encontrado");
        }
        pedidoRepository.deleteById(id);
    }

    private int calcularTiempoMaximoPreparacion(Pedido pedido) {
        return pedido.getDetalles().stream()
                .mapToInt(d -> d.getPlato().getTiempoPreparacionMinutos())
                .max()
                .orElse(0);
    }
}