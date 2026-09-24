package com.restopilot.backend.modules.pedidos.dto;

import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.entity.TipoEntrega;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoResponseDTO(
        Long id,
        Long restauranteId,
        String restauranteNombre,
        Long clienteId,
        String clienteNombre,
        EstadoPedido estado,
        TipoEntrega tipoEntrega,
        BigDecimal total,
        LocalDateTime fechaCreacion,
        LocalDateTime limiteCancelacion
) {}