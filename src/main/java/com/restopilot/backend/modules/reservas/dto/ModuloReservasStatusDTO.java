package com.restopilot.backend.modules.reservas.dto;

/**
 * TSK-018: DTO que expone el estado de activación del módulo de reservas
 * para un restaurante según la configuración de adaptabilidad SaaS (tieneAtencionFisica).
 */
public record ModuloReservasStatusDTO(
        Long restauranteId,
        String restauranteNombre,
        Boolean habilitado,
        Long reservasActivasPendientes,
        String mensaje
) {}
