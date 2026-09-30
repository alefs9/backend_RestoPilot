package com.restopilot.backend.modules.reservas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MesaRequestDTO(
        @NotNull(message = "El número de mesa es obligatorio")
        @Min(value = 1, message = "El número de mesa debe ser mayor a 0")
        Integer numero,

        @NotNull(message = "La capacidad es obligatoria")
        @Min(value = 1, message = "La capacidad debe ser al menos 1")
        Integer capacidad,

        @NotNull(message = "La coordenada X es obligatoria")
        Double coordenadaX,

        @NotNull(message = "La coordenada Y es obligatoria")
        Double coordenadaY
) {}
