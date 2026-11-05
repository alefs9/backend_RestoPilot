package com.restopilot.backend.modules.reservas.service;

import com.restopilot.backend.modules.reservas.entity.*;
import java.time.LocalDateTime;

public final class ReglasCicloReserva {
    private ReglasCicloReserva() {}
    public static LocalDateTime inicio(Reserva r) {
        return LocalDateTime.of(r.getFecha(), r.getHoraInicio());
    }
    public static boolean puedeRegistrarLlegada(Reserva r, LocalDateTime ahora) {
        return r.getEstado()==EstadoReserva.CONFIRMADA && r.getLlegadaRegistradaEn()==null && !vencida(r,ahora);
    }
    public static boolean puedeCompletar(Reserva r, LocalDateTime ahora) {
        return r.getEstado()==EstadoReserva.CONFIRMADA && r.getLlegadaRegistradaEn()!=null && !ahora.isBefore(inicio(r));
    }
    public static boolean vencida(Reserva r, LocalDateTime ahora) {
        if (r.getEstado() == EstadoReserva.PENDIENTE) return !ahora.isBefore(inicio(r));
        return r.getEstado() == EstadoReserva.CONFIRMADA && r.getLlegadaRegistradaEn() == null
                && !ahora.isBefore(inicio(r).plusMinutes(15));
    }
}
