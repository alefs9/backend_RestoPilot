package com.restopilot.backend.modules.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** Datos de una cuenta nueva; el rol se determina en el servidor. */
@Getter
@Setter
public abstract class CuentaRequestDTO {
    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150)
    private String nombreCompleto;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato de correo no es válido")
    @Size(max = 100)
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*?&._\\-#]).{8,}$",
            message = "La contraseña debe incluir letras, un número y un carácter especial")
    private String password;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "La contraseña no puede superar 72 bytes UTF-8")
    public boolean isPasswordDentroDelLimiteUtf8() {
        return com.restopilot.backend.security.PasswordUtf8.dentroDelLimite(password);
    }

}