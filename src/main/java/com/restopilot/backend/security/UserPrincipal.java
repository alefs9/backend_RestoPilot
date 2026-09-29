package com.restopilot.backend.security;

import com.restopilot.backend.modules.auth.entity.Usuario;
import lombok.Getter;

@Getter
public class UserPrincipal extends Usuario {

    public UserPrincipal(Usuario usuario) {
        this.setId(usuario.getId());
        this.setNombreCompleto(usuario.getNombreCompleto());
        this.setCorreo(usuario.getCorreo());
        this.setPasswordHash(usuario.getPasswordHash());
        this.setRol(usuario.getRol());
        this.setHabilitado(usuario.getHabilitado());
        this.setRestaurante(usuario.getRestaurante());
    }

    public Usuario getUsuario() {
        return this;
    }
}
