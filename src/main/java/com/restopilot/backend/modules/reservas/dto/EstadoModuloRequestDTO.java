package com.restopilot.backend.modules.reservas.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoModuloRequestDTO(
        @NotNull(message = "El campo habilitado es obligatorio") Boolean habilitado
) {}