package com.restopilot.backend.modules.reservas.dto;

/**
 * DTO que representa una mesa con su estado de disponibilidad
 * para una fecha y rango horario consultados.
 * Se usa en el plano interactivo del salón (frontend).
 */
public record MesaDisponibilidadDTO(
        Long mesaId,
        Integer numero,
        Integer capacidad,
        Double coordenadaX,
        Double coordenadaY,
        Boolean disponible   // true = libre, false = ya reservada en ese horario
) {}
