package com.restopilot.backend.modules.auth.dto;

import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;

public record PerfilResponseDTO(Long id, String nombreCompleto, String correo, Rol rol, Long restauranteId) {
    public static PerfilResponseDTO from(Usuario usuario) {
        return new PerfilResponseDTO(usuario.getId(), usuario.getNombreCompleto(), usuario.getCorreo(),
                usuario.getRol(), usuario.getRestaurante() == null ? null : usuario.getRestaurante().getId());
    }
}
