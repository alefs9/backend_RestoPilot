package com.restopilot.backend.modules.pedidos.dto;

import java.math.BigDecimal;

public record DetallePedidoResponseDTO(
        Long id,
        Long platoId,
        String nombrePlatoSnapshot,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal,
        String notas
) {}