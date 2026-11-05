package com.restopilot.backend.modules.auth.dto;

import com.restopilot.backend.modules.auth.entity.Rol;

/** No expone contraseña, hash ni un token de sesión del empleado. */
public record AdministradorResponseDTO(String mensaje, Long id, String nombreCompleto,
        String correo, Rol rol, Long restauranteId) {}
