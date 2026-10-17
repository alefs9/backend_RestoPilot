package com.restopilot.backend.modules.reservas.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoMesaRequestDTO(
        @NotNull(message = "El campo activo es obligatorio") Boolean activo
) {}