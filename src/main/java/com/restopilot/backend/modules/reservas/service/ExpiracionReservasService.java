package com.restopilot.backend.modules.reservas.service;

import com.restopilot.backend.modules.reservas.entity.EstadoReserva;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.modules.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service
@RequiredArgsConstructor
public class ExpiracionReservasService {
    private final ReservaRepository reservas;
    private final NotificacionService notificaciones;
    private final Clock clock;

    // La expiración es un hecho temporal independiente: no se revierte si después
    // una petición tardía intenta completar/cancelar y recibe un error de negocio.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expirarVencidas() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        int total = 0;
        for (Long id : reservas.findCandidatasExpiracion(ahora.toLocalDate())) {
            var r = reservas.findByIdForUpdate(id).orElse(null);
            if (r != null && ReglasCicloReserva.vencida(r, ahora)) {
                r.setEstado(EstadoReserva.EXPIRADA);
                r.setExpiradaEn(ahora);
                reservas.saveAndFlush(r);
                notificaciones.enviar(r.getUsuario().getId(), "Reserva #" + id + ": EXPIRADA. Se liberó la mesa.");
                total++;
            }
        }
        return total;
    }
}
