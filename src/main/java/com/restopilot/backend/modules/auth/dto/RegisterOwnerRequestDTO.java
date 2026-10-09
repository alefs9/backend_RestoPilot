package com.restopilot.backend.modules.auth.dto;

import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.restaurante.dto.RestauranteAltaRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterOwnerRequestDTO extends CuentaRequestDTO {
    @Valid @NotNull
    private RestauranteAltaRequestDTO restaurante;
    // Se reconocen para rechazar intentos explícitos de elegir un negocio existente o un rol.
    @Null(message = "No puede vincular el dueño a un restaurante existente")
    private Long restauranteId;
    @Null(message = "El servidor asigna el rol DUENO")
    private Rol rol;
}
