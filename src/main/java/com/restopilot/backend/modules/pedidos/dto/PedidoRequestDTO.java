package com.restopilot.backend.modules.pedidos.dto;

import com.restopilot.backend.modules.pedidos.entity.TipoEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PedidoRequestDTO(
        @NotNull(message = "El ID del restaurante es obligatorio")
        Long restauranteId,

        @NotNull(message = "El tipo de entrega es obligatorio")
        TipoEntrega tipoEntrega,

        @NotEmpty(message = "El pedido debe contener al menos un detalle")
        @Valid
        List<DetallePedidoRequestDTO> detalles
) {}