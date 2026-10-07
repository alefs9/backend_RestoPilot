package com.restopilot.backend.modules.reservas.dto;

import com.restopilot.backend.modules.reservas.entity.EstadoReserva;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProcesarReservaRequestDTO(
        @NotNull EstadoReserva estado,
        @Size(max = 300) String motivo) {}
