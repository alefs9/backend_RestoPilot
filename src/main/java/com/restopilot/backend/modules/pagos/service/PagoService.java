package com.restopilot.backend.modules.pagos.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.pagos.dto.*;
import com.restopilot.backend.modules.pagos.entity.*;
import com.restopilot.backend.modules.pagos.repository.PagoRepository;
import com.restopilot.backend.modules.pedidos.entity.*;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PagoService {
    private final PagoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final CurrentUser currentUser;
    private final AccesoRestaurante acceso;
    private final Clock clock;

    /** Se ejecuta dentro de la misma transacción que crea o modifica el pedido. */
    @Transactional
    public void sincronizarPendiente(Pedido pedido) {
        Pago pago = pagoRepository.findByPedidoId(pedido.getId()).orElseGet(() -> Pago.builder()
                .pedidoId(pedido.getId()).estado(EstadoPago.PENDIENTE).build());
        if (pago.getEstado() == EstadoPago.PAGADO) {
            throw new BusinessRuleException("Un pedido pagado no puede modificar su importe.");
        }
        pago.setMonto(pedido.getTotal());
        pagoRepository.save(pago);
    }

    @Transactional(readOnly = true)
    public boolean estaPagado(Long pedidoId) {
        return pagoRepository.findByPedidoId(pedidoId).map(p -> p.getEstado() == EstadoPago.PAGADO).orElse(false);
    }

    @Transactional
    public PagoResponseDTO registrarPago(Long pedidoId, PagoRequestDTO request) {
        // Bloquea el pedido para que dos solicitudes no confirmen o alteren el importe a la vez.
        Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado."));
        acceso.exigirGestion(pedido.getRestaurante().getId());
        if (pedido.getEstado() == EstadoPedido.CANCELADO) throw new BusinessRuleException("No se puede pagar un pedido cancelado.");
        if (request.getMonto() == null || request.getMonto().compareTo(pedido.getTotal()) != 0) {
            throw new BusinessRuleException("El monto debe coincidir con el total del pedido.");
        }
        Pago pago = pagoRepository.findByPedidoId(pedidoId).orElseGet(() -> Pago.builder().pedidoId(pedidoId).build());
        if (pago.getEstado() == EstadoPago.PAGADO) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "El pedido ya cuenta con un pago confirmado e inamovible.");
        }
        EstadoPago estado = request.getEstado() == null ? EstadoPago.PAGADO : request.getEstado();
        if (request.getMetodo() == null) throw new BusinessRuleException("El método de pago es obligatorio.");
        if (estado == EstadoPago.PAGADO && request.getMetodo() != MetodoPago.EFECTIVO
                && (request.getCodigoReferencia() == null || request.getCodigoReferencia().isBlank())) {
            throw new BusinessRuleException("Los pagos electrónicos requieren un código de referencia.");
        }
        pago.setMonto(pedido.getTotal());
        pago.setMetodo(request.getMetodo());
        pago.setEstado(estado);
        pago.setCodigoReferencia(request.getCodigoReferencia());
        pago.setFechaPago(estado == EstadoPago.PAGADO ? LocalDateTime.now(clock) : null);
        return mapear(pagoRepository.save(pago), "Pago registrado exitosamente.");
    }

    @Transactional(readOnly = true)
    public PagoResponseDTO obtenerPagoPorPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado."));
        var usuario = currentUser.get();
        if (usuario.getRol() == Rol.CLIENTE) {
            if (!pedido.getCliente().getId().equals(usuario.getId())) throw new ResourceNotFoundException("Pedido no encontrado.");
        } else acceso.exigirGestion(pedido.getRestaurante().getId());
        return mapear(pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe registro de pago para el pedido.")), "Detalle de pago consultado.");
    }

    private PagoResponseDTO mapear(Pago pago, String mensaje) {
        return PagoResponseDTO.builder().mensaje(mensaje).id(pago.getId()).pedidoId(pago.getPedidoId())
                .monto(pago.getMonto()).metodo(pago.getMetodo()).estado(pago.getEstado())
                .codigoReferencia(pago.getCodigoReferencia()).fechaPago(pago.getFechaPago()).creadoEn(pago.getCreadoEn()).build();
    }
}
