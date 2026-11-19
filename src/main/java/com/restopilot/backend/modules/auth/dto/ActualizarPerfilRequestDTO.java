package com.restopilot.backend.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El cliente solo puede editar su nombre; identidad, correo y rol vienen de la sesión. */
public record ActualizarPerfilRequestDTO(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre completo no puede superar 150 caracteres")
        String nombreCompleto) {
}
