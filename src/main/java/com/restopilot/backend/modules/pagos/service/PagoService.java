package com.restopilot.backend.modules.pagos.service;

import com.restopilot.backend.modules.pagos.dto.PagoRequestDTO;
import com.restopilot.backend.modules.pagos.dto.PagoResponseDTO;
import com.restopilot.backend.modules.pagos.entity.EstadoPago;
import com.restopilot.backend.modules.pagos.entity.Pago;
import com.restopilot.backend.modules.pagos.repository.PagoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;

    public PagoService(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    @Transactional
    public PagoResponseDTO registrarPago(Long pedidoId, PagoRequestDTO request) {
        Optional<Pago> pagoExistente = pagoRepository.findByPedidoId(pedidoId);

        if (pagoExistente.isPresent() && pagoExistente.get().getEstado() == EstadoPago.PAGADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El pedido #" + pedidoId + " ya cuenta con un pago confirmado y es un registro inamovible."
            );
        }

        EstadoPago estadoFinal = (request.getEstado() != null) ? request.getEstado() : EstadoPago.PAGADO;
        LocalDateTime fechaPagoFinal = (estadoFinal == EstadoPago.PAGADO) ? LocalDateTime.now() : null;

        Pago pagoAGuardar;

        if (pagoExistente.isPresent()) {
            // Actualizacion pago existente (de PENDIENTE a PAGADO)
            pagoAGuardar = pagoExistente.get();
            pagoAGuardar.setMonto(request.getMonto());
            pagoAGuardar.setMetodo(request.getMetodo());
            pagoAGuardar.setEstado(estadoFinal);
            pagoAGuardar.setCodigoReferencia(request.getCodigoReferencia());
            pagoAGuardar.setFechaPago(fechaPagoFinal);
        } else {
            // Registro de nuevo pago para el pedido
            pagoAGuardar = Pago.builder()
                    .pedidoId(pedidoId)
                    .monto(request.getMonto())
                    .metodo(request.getMetodo())
                    .estado(estadoFinal)
                    .codigoReferencia(request.getCodigoReferencia())
                    .fechaPago(fechaPagoFinal)
                    .build();
        }

        Pago guardado = pagoRepository.save(pagoAGuardar);

        return mapearADTO(guardado, "Pago registrado exitosamente para el pedido #" + pedidoId);
    }

    @Transactional(readOnly = true)
    public PagoResponseDTO obtenerPagoPorPedido(Long pedidoId) {
        Pago pago = pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró ningún registro de pago para el pedido #" + pedidoId
                ));

        return mapearADTO(pago, "Detalle de pago consultado exitosamente.");
    }

    private PagoResponseDTO mapearADTO(Pago pago, String mensaje) {
        return PagoResponseDTO.builder()
                .mensaje(mensaje)
                .id(pago.getId())
                .pedidoId(pago.getPedidoId())
                .monto(pago.getMonto())
                .metodo(pago.getMetodo())
                .estado(pago.getEstado())
                .codigoReferencia(pago.getCodigoReferencia())
                .fechaPago(pago.getFechaPago())
                .creadoEn(pago.getCreadoEn())
                .build();
    }
}