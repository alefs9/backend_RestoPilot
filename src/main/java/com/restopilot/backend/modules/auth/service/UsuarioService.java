package com.restopilot.backend.modules.auth.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.modules.auth.dto.*;
import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UsuarioService {
    private final CurrentUser currentUser;
    private final RegistroCuentaService cuentas;

    /** US22: ninguna identidad de restaurante o rol se toma del formulario. */
    @Transactional
    public AdministradorResponseDTO crearAdministrador(@Valid CrearAdministradorRequestDTO request) {
        var dueño = currentUser.get();
        if (dueño.getRol() != Rol.DUENO || !dueño.isEnabled() || dueño.getRestaurante() == null) {
            throw new AccessDeniedException("Solo un dueño con restaurante puede incorporar administradores.");
        }
        if (request.getRestauranteId() != null || request.getRol() != null) {
            throw new BusinessRuleException("El servidor determina el rol y el restaurante del administrador.");
        }
        String correo = cuentas.validarCorreoDisponible(request.getCorreo());
        var usuario = cuentas.guardar(request, correo, Rol.ADMINISTRADOR, dueño.getRestaurante());
        return new AdministradorResponseDTO("Administrador incorporado con éxito.", usuario.getId(),
                usuario.getNombreCompleto(), usuario.getCorreo(), usuario.getRol(), dueño.getRestaurante().getId());
    }
}
