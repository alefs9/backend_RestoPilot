package com.restopilot.backend.modules.notificaciones.controller;

import com.restopilot.backend.modules.notificaciones.entity.Notificacion;
import com.restopilot.backend.modules.notificaciones.repository.NotificacionRepository;
import com.restopilot.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {
    private final NotificacionRepository repository;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    public Page<Notificacion> consultar(Pageable pageable) {
        return repository.findByUsuarioIdOrderByFechaCreacionDesc(currentUser.get().getId(), pageable);
    }
}
