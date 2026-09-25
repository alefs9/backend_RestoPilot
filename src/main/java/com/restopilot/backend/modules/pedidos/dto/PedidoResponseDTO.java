package com.restopilot.backend.modules.pedidos.dto;

import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.entity.TipoEntrega;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponseDTO(
        Long id,
        Long restauranteId,
        String restauranteNombre,
        Long clienteId,
        String clienteNombre,
        Long mesaId,
        EstadoPedido estado,
        TipoEntrega tipoEntrega,
        String direccionEntrega,
        BigDecimal total,
        LocalDateTime fechaCreacion,
        LocalDateTime limiteCancelacion,
        List<DetallePedidoResponseDTO> detalles
) {}