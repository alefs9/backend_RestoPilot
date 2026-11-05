package com.restopilot.backend.modules.auth.dto;

import com.restopilot.backend.modules.auth.entity.Rol;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearAdministradorRequestDTO extends CuentaRequestDTO {
    @Null(message = "El restaurante se obtiene del dueño autenticado")
    private Long restauranteId;
    @Null(message = "El servidor asigna el rol ADMINISTRADOR")
    private Rol rol;
}
