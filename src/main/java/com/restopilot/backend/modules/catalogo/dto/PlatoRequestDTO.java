package com.restopilot.backend.modules.catalogo.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class PlatoRequestDTO {
    @Positive
    private Long restauranteId;
    @Positive
    private Long categoriaId;
    @Size(max=150)
    private String nombre;
    private String descripcion;
    @DecimalMin("0.01") @Digits(integer=8, fraction=2)
    private BigDecimal precio;
    private String ingredientes;
    private String alergenos;
    private String complejidad;
    @Min(5) @Max(15)
    private Integer tiempoPreparacionMinutos;
    private Boolean disponible;
    @Size(max=255)
    private String imagenUrl;
}