package com.restopilot.backend.security;

import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AccesoRestaurante {
    private final CurrentUser currentUser;

    public void exigirGestion(Long restauranteId) {
        Usuario usuario = currentUser.get();
        if ((usuario.getRol() != Rol.ADMINISTRADOR && usuario.getRol() != Rol.DUENO)
                || usuario.getRestaurante() == null
                || !Objects.equals(usuario.getRestaurante().getId(), restauranteId)) {
            throw new AccessDeniedException("No puede administrar otro restaurante.");
        }
    }
}
