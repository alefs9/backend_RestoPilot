package com.restopilot.backend.modules.auth.dto;

import com.restopilot.backend.modules.auth.entity.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDTO {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombreCompleto;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato de correo no es válido")
    @Size(max = 100, message = "El correo no puede superar 100 caracteres")
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*?&._\\-#]).{8,}$",
            message = "La contraseña debe tener al menos 8 caracteres, incluir letras, al menos un número y un carácter especial (@$!%*?&._-#)"
    )
    private String password;
    private Long restauranteId;
    private Rol rol;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "La contraseña no puede superar 72 bytes UTF-8")
    public boolean isPasswordDentroDelLimiteUtf8() {
        return com.restopilot.backend.security.PasswordUtf8.dentroDelLimite(password);
    }

}