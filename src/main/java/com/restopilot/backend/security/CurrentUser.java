package com.restopilot.backend.security;

import com.restopilot.backend.modules.auth.entity.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Usuario get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new RuntimeException("Usuario no autenticado en el sistema");
        }
        return (Usuario) authentication.getPrincipal();
    }

    public Long getRestauranteId() {
        Usuario usuario = get();
        if (usuario.getRestaurante() == null) {
            throw new RuntimeException("El usuario no tiene un restaurante asignado");
        }
        return usuario.getRestaurante().getId();
    }
}