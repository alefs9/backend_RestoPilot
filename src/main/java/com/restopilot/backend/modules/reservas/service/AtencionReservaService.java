package com.restopilot.backend.modules.reservas.service;

import com.restopilot.backend.core.exception.*;
import com.restopilot.backend.modules.reservas.dto.ReservaResponseDTO;
import com.restopilot.backend.modules.reservas.entity.*;
import com.restopilot.backend.modules.reservas.mapper.ReservaMapper;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.modules.notificaciones.service.NotificacionService;
import com.restopilot.backend.security.AccesoRestaurante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
@RequiredArgsConstructor
public class AtencionReservaService {
    private final ReservaRepository reservas;
    private final ReservaMapper mapper;
    private final AccesoRestaurante acceso;
    private final NotificacionService notificaciones;
    private final ExpiracionReservasService expiracion;
    private final Clock clock;

    private Reserva obtenerParaGestion(Long id) {
        expiracion.expirarVencidas();
        Reserva r = reservas.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada."));
        acceso.exigirGestion(r.getRestaurante().getId());
        if (r.getEstado() != EstadoReserva.CONFIRMADA)
            throw new BusinessRuleException("La reserva debe estar CONFIRMADA.");
        return r;
    }

    @Transactional
    public ReservaResponseDTO registrarLlegada(Long id) {
        Reserva r = obtenerParaGestion(id);
        if (r.getLlegadaRegistradaEn() != null)
            throw new BusinessRuleException("La llegada ya fue registrada.");
        // Se vuelve a evaluar tras adquirir el bloqueo por si transcurrió el límite.
        if (ReglasCicloReserva.vencida(r, LocalDateTime.now(clock)))
            throw new BusinessRuleException("La tolerancia de llegada ya venció.");
        r.setLlegadaRegistradaEn(LocalDateTime.now(clock));
        reservas.saveAndFlush(r);
        notificaciones.enviar(r.getUsuario().getId(), "Reserva #" + id + ": llegada registrada.");
        return mapper.toResponse(r).conMensaje("Llegada registrada con éxito.");
    }

    @Transactional
    public ReservaResponseDTO completar(Long id) {
        Reserva r = obtenerParaGestion(id);
        if (r.getLlegadaRegistradaEn() == null)
            throw new BusinessRuleException("Debe registrar la llegada antes de completar la reserva.");
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (ahora.isBefore(ReglasCicloReserva.inicio(r)))
            throw new BusinessRuleException("No se puede completar antes de la hora de inicio.");
        r.setEstado(EstadoReserva.COMPLETADA);
        r.setCompletadaEn(ahora);
        reservas.saveAndFlush(r);
        notificaciones.enviar(r.getUsuario().getId(), "Reserva #" + id + ": COMPLETADA.");
        return mapper.toResponse(r).conMensaje("Reserva completada con éxito.");
    }
}
