package com.restopilot.backend.modules.catalogo.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PlatoResponseDTO {
    private Long id;
    private Long restauranteId;
    private Long categoriaId;
    private String categoriaNombre;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private String ingredientes;
    private String alergenos;
    private String complejidad;
    private Integer tiempoPreparacionMinutos;
    private Boolean disponible;
    private String imagenUrl;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
}