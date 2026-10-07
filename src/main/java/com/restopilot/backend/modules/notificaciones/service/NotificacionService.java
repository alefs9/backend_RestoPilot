package com.restopilot.backend.modules.notificaciones.service;

import com.restopilot.backend.modules.notificaciones.entity.Notificacion;
import com.restopilot.backend.modules.notificaciones.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificacionService {
    private final NotificacionRepository repository;
    private final Clock clock;

    @Transactional
    public void enviar(Long usuarioId, String mensaje) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(usuarioId);
        notificacion.setMensaje(mensaje);
        notificacion.setFechaCreacion(LocalDateTime.now(clock));
        repository.save(notificacion);
    }
}
