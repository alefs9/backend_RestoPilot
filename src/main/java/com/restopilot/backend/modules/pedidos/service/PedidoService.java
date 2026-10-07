package com.restopilot.backend.modules.pedidos.service;

import com.restopilot.backend.core.exception.*;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.catalogo.entity.Plato;
import com.restopilot.backend.modules.catalogo.repository.PlatoRepository;
import com.restopilot.backend.modules.pedidos.dto.*;
import com.restopilot.backend.modules.pedidos.entity.*;
import com.restopilot.backend.modules.pedidos.mapper.PedidoMapper;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import com.restopilot.backend.modules.pagos.service.PagoService;
import com.restopilot.backend.modules.notificaciones.service.NotificacionService;
import com.restopilot.backend.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PedidoService {
    private final PedidoRepository pedidoRepository;
    private final RestauranteRepository restauranteRepository;
    private final PlatoRepository platoRepository;
    private final PedidoMapper pedidoMapper;
    private final CurrentUser currentUser;
    private final AccesoRestaurante acceso;
    private final MesaRepository mesaRepository;
    private final PagoService pagos;
    private final NotificacionService notificaciones;
    private final Clock clock;

    @Transactional
    public PedidoResponseDTO crearPedido(PedidoRequestDTO request) {
        Usuario cliente = currentUser.get();
        Restaurante restaurante = restauranteRepository.findById(request.restauranteId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
        if (!Boolean.TRUE.equals(restaurante.getActivo())) {
            throw new BusinessRuleException("Lo sentimos, el restaurante se encuentra inactivo actualmente.");
        }
        LocalTime ahora = LocalTime.now(clock);
        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null
                && (ahora.isBefore(restaurante.getHoraApertura()) || !ahora.isBefore(restaurante.getHoraCierre()))) {
            throw new BusinessRuleException("El restaurante está fuera de su horario de atención.");
        }
        Pedido pedido = new Pedido();
        pedido.setRestaurante(restaurante);
        pedido.setCliente(cliente);
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setFechaCreacion(LocalDateTime.now(clock));
        pedido.setLimiteCancelacion(LocalDateTime.now(clock).plusMinutes(5));
        pedido.setDetalles(new ArrayList<>());
        aplicarModalidad(pedido, request);
        actualizarDetalles(pedido, request.detalles());
        Pedido guardado = pedidoRepository.save(pedido);
        pagos.sincronizarPendiente(guardado);
        return pedidoMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Page<PedidoResponseDTO> getMisPedidos(Pageable pageable) {
        return pedidoRepository.findByClienteId(currentUser.get().getId(), pageable).map(pedidoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO getById(Long id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado."));
        Usuario usuario = currentUser.get();
        if (usuario.getRol() == Rol.CLIENTE) {
            // No revelar si existe un pedido de otra cuenta.
            if (!Objects.equals(pedido.getCliente().getId(), usuario.getId())) throw new ResourceNotFoundException("Pedido no encontrado.");
        } else acceso.exigirGestion(pedido.getRestaurante().getId());
        return pedidoMapper.toResponse(pedido);
    }

    @Transactional(readOnly = true)
    public Page<PedidoResponseDTO> getPendientesCocina(Pageable pageable) {
        Long restauranteId = currentUser.getRestauranteId();
        acceso.exigirGestion(restauranteId);
        List<Pedido> ordenados = pedidoRepository.findPendientesParaCocina(restauranteId,
                List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION)).stream()
                .sorted(Comparator.comparingInt(this::calcularComplejidadMaxima).reversed()
                        .thenComparing(Comparator.comparingInt(this::calcularTiempoMaximoPreparacion).reversed())
                        .thenComparing(Pedido::getFechaCreacion, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Pedido::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        long offset = pageable.getOffset();
        int inicio = (int) Math.min(offset, ordenados.size());
        int fin = (int) Math.min(offset + pageable.getPageSize(), ordenados.size());
        return new PageImpl<>(ordenados.subList(inicio, fin).stream().map(pedidoMapper::toResponse).toList(), pageable, ordenados.size());
    }

    @Transactional
    public PedidoResponseDTO actualizarPedido(Long id, PedidoRequestDTO request) {
        Pedido pedido = buscarParaGestion(id);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE || pagos.estaPagado(id)) {
            throw new BusinessRuleException("Solo puede modificar un pedido pendiente y no pagado.");
        }
        if (!pedido.getRestaurante().getId().equals(request.restauranteId())) {
            throw new BusinessRuleException("No se puede cambiar el restaurante del pedido.");
        }
        aplicarModalidad(pedido, request);
        actualizarDetalles(pedido, request.detalles());
        pagos.sincronizarPendiente(pedido);
        return pedidoMapper.toResponse(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO actualizarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = buscarParaGestion(id);
        EstadoPedido siguiente = switch (pedido.getEstado()) {
            case PENDIENTE -> EstadoPedido.EN_PREPARACION;
            case EN_PREPARACION -> EstadoPedido.LISTO;
            case LISTO -> EstadoPedido.ENTREGADO;
            case ENTREGADO, CANCELADO -> null;
        };
        if (nuevoEstado == null || nuevoEstado != siguiente) {
            throw new BusinessRuleException("Transición de estado no válida. El pedido debe pasar por la etapa de preparación.");
        }
        pedido.setEstado(nuevoEstado);
        Pedido guardado = pedidoRepository.saveAndFlush(pedido);
        notificaciones.enviar(pedido.getCliente().getId(), "Pedido #" + id + ": " + nuevoEstado);
        return pedidoMapper.toResponse(guardado);
    }

    /** Cancelación administrativa: conserva el historial y el registro de pago. */
    @Transactional
    public void eliminarPedido(Long id) {
        Pedido pedido = buscarParaGestion(id);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE || pagos.estaPagado(id)) {
            throw new BusinessRuleException("Solo se puede cancelar un pedido pendiente y no pagado.");
        }
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.saveAndFlush(pedido);
        notificaciones.enviar(pedido.getCliente().getId(), "Pedido #" + id + ": CANCELADO");
    }

    private Pedido buscarParaGestion(Long id) {
        Pedido pedido = pedidoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado."));
        acceso.exigirGestion(pedido.getRestaurante().getId());
        return pedido;
    }

    private void aplicarModalidad(Pedido pedido, PedidoRequestDTO request) {
        if (request.tipoEntrega() == null) throw new BusinessRuleException("La modalidad es obligatoria.");
        pedido.setTipoEntrega(request.tipoEntrega());
        pedido.setMesa(null);
        pedido.setDireccionEntrega(null);
        if (request.tipoEntrega() == TipoEntrega.DELIVERY) {
            if (!Boolean.TRUE.equals(pedido.getRestaurante().getAceptaDelivery())
                    || request.direccionEntrega() == null || request.direccionEntrega().isBlank()
                    || request.direccionEntrega().trim().length() > 255) {
                throw new BusinessRuleException("Delivery requiere estar habilitado y una dirección válida.");
            }
            pedido.setDireccionEntrega(request.direccionEntrega().trim());
        } else if (request.tipoEntrega() == TipoEntrega.SALON) {
            if (!Boolean.TRUE.equals(pedido.getRestaurante().getTieneAtencionFisica()) || request.mesaId() == null) {
                throw new BusinessRuleException("El consumo en salón requiere atención física y una mesa.");
            }
            var mesa = mesaRepository.findById(request.mesaId()).orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada."));
            if (!Boolean.TRUE.equals(mesa.getActivo()) || !mesa.getRestaurante().getId().equals(pedido.getRestaurante().getId())) {
                throw new BusinessRuleException("La mesa debe estar activa y pertenecer al restaurante.");
            }
            pedido.setMesa(mesa);
        }
    }

    private void actualizarDetalles(Pedido pedido, List<DetallePedidoRequestDTO> detalles) {
        if (detalles == null || detalles.isEmpty()) throw new BusinessRuleException("Debe seleccionar al menos un plato.");
        List<DetallePedido> nuevos = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedidoRequestDTO dto : detalles) {
            Plato plato = platoRepository.findById(dto.platoId()).orElseThrow(() -> new ResourceNotFoundException("Plato no encontrado."));
            if (!Objects.equals(plato.getRestauranteId(), pedido.getRestaurante().getId())) {
                throw new BusinessRuleException("El plato no pertenece al restaurante del pedido.");
            }
            if (!Boolean.TRUE.equals(plato.getDisponible())) throw new BusinessRuleException("El plato " + plato.getNombre() + " se encuentra agotado.");
            if (dto.cantidad() == null || dto.cantidad() < 1) throw new BusinessRuleException("La cantidad debe ser mayor a cero.");
            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setPlato(plato);
            detalle.setCantidad(dto.cantidad());
            detalle.setNombrePlatoSnapshot(plato.getNombre());
            detalle.setPrecioUnitario(plato.getPrecio());
            detalle.setNotas(dto.notas());
            detalle.setSubtotal(plato.getPrecio().multiply(BigDecimal.valueOf(dto.cantidad())));
            total = total.add(detalle.getSubtotal());
            nuevos.add(detalle);
        }
        pedido.getDetalles().clear();
        pedido.getDetalles().addAll(nuevos);
        pedido.setTotal(total);
    }

    private int calcularTiempoMaximoPreparacion(Pedido pedido) {
        return platos(pedido).stream().map(Plato::getTiempoPreparacionMinutos).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
    }

    private int calcularComplejidadMaxima(Pedido pedido) {
        return platos(pedido).stream().mapToInt(plato -> switch (plato.getComplejidad() == null ? "" : plato.getComplejidad().trim().toUpperCase(Locale.ROOT)) {
            case "ALTA" -> 3;
            case "MEDIA" -> 2;
            case "BAJA" -> 1;
            default -> 0;
        }).max().orElse(0);
    }

    private List<Plato> platos(Pedido pedido) {
        return pedido.getDetalles() == null ? List.of() : pedido.getDetalles().stream().map(DetallePedido::getPlato).filter(Objects::nonNull).toList();
    }
}
