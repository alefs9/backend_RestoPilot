package com.restopilot.backend.modules.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ChatIARequestDTO(
        @NotBlank @Size(max = 2000) String mensaje,
        @Positive Long restauranteId
) {}
