package com.restopilot.backend.modules.restaurante.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalTime;

@Getter
@Setter
public class RestauranteAltaRequestDTO {
    @Null(message = "El alta debe crear un restaurante nuevo, sin ID previo")
    private Long id;
    @NotBlank @Size(max = 150)
    private String nombre;
    @NotBlank @Size(max = 255)
    private String direccion;
    @NotBlank @Size(max = 20)
    private String telefono;
    @NotNull
    private LocalTime horaApertura;
    @NotNull
    private LocalTime horaCierre;
    @NotNull
    private Boolean tieneAtencionFisica;
    @NotNull
    private Boolean aceptaDelivery;
}
