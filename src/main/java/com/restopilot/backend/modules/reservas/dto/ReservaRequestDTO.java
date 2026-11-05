package com.restopilot.backend.modules.reservas.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de solicitud para registrar una nueva reserva originada
 * desde la selección en el plano visual interactivo del salón.
 */
public record ReservaRequestDTO(
        @NotNull(message = "El ID de la mesa es obligatorio")
        Long mesaId,

        @NotNull(message = "El ID del restaurante es obligatorio")
        Long restauranteId,

        @NotNull(message = "La fecha de reserva es obligatoria")
        @FutureOrPresent(message = "La fecha de reserva no puede ser en el pasado")
        LocalDate fecha,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime horaInicio,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime horaFin,

        @NotNull(message = "El número de comensales es obligatorio")
        @Min(value = 1, message = "Debe haber al menos 1 comensal")
        Integer numeroComensales,

        @Size(max = 500, message = "Las notas no pueden exceder los 500 caracteres")
        String notas
) {}
