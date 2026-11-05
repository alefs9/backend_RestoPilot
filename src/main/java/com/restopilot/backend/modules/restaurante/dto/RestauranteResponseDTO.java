package com.restopilot.backend.modules.restaurante.dto;

import java.time.LocalTime;

/** Datos públicos necesarios para consultar la carta y registrar pedidos. */
public record RestauranteResponseDTO(Long id, String nombre, String direccion,
        boolean activo, boolean abierto, boolean tieneAtencionFisica, boolean aceptaDelivery,
        LocalTime horaApertura, LocalTime horaCierre) {}
