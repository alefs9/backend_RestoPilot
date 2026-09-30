package com.restopilot.backend.modules.reservas.dto;

import java.time.LocalDateTime;

public record MesaResponseDTO(
        Long id,
        Long restauranteId,
        String restauranteNombre,
        Integer numero,
        Integer capacidad,
        Double coordenadaX,
        Double coordenadaY,
        Boolean activo,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {}
