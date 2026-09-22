package com.restopilot.backend.modules.pedidos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DetallePedidoRequestDTO(
        @NotNull(message = "El ID del plato es obligatorio")
        Long platoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        Integer cantidad
) {}