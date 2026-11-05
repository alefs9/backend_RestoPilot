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
        LocalDateTime actualizadoEn,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String mensaje
) {
    public MesaResponseDTO(
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
    ) {
        this(id, restauranteId, restauranteNombre, numero, capacidad, coordenadaX, coordenadaY, activo, creadoEn, actualizadoEn, null);
    }

    public MesaResponseDTO conMensaje(String mensaje) {
        return new MesaResponseDTO(id, restauranteId, restauranteNombre, numero, capacidad, coordenadaX, coordenadaY, activo, creadoEn, actualizadoEn, mensaje);
    }
}