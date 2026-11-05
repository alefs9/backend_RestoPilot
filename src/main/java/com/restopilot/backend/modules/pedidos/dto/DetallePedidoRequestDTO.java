package com.restopilot.backend.modules.pedidos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DetallePedidoRequestDTO(
        @NotNull(message = "El ID del plato es obligatorio")
        Long platoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        Integer cantidad,

        @Size(max = 255, message = "Las notas no pueden exceder los 255 caracteres")
        String notas
) {}