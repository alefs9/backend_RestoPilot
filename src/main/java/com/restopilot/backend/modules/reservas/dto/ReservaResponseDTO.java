package com.restopilot.backend.modules.reservas.dto;

import com.restopilot.backend.modules.reservas.entity.EstadoReserva;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO de salida con el detalle completo de una reserva registrada.
 */
public record ReservaResponseDTO(
        Long id,
        Long mesaId,
        Integer mesaNumero,
        Long restauranteId,
        String restauranteNombre,
        Long clienteId,
        String clienteNombre,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        Integer numeroComensales,
        EstadoReserva estado,
        String notas,
        LocalDateTime creadoEn
) {}
