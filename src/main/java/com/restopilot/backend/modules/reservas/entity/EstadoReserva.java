package com.restopilot.backend.modules.reservas.entity;

/**
 * Estados posibles del ciclo de vida de una reserva.
 * Solo PENDIENTE y CONFIRMADA bloquean la mesa para nuevas reservas.
 */
public enum EstadoReserva {
    PENDIENTE,     // Reserva creada, esperando confirmacion del restaurante
    CONFIRMADA,    // Reserva confirmada por el restaurante
    CANCELADA,     // Reserva cancelada (por el cliente o el restaurante)
    COMPLETADA     // Los comensales asistieron y terminaron
}
