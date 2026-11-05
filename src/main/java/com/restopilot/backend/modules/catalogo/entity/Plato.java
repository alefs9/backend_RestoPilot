package com.restopilot.backend.modules.catalogo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PLATOS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(columnDefinition = "TEXT")
    private String ingredientes;

    @Column(columnDefinition = "TEXT")
    private String alergenos;

    @Column(length = 50)
    private String complejidad;

    @Column(name = "tiempo_preparacion_minutos")
    private Integer tiempoPreparacionMinutos;

    @Column(nullable = false)
    private Boolean disponible = true;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    public void actualizarDatos(String nombre, String descripcion, BigDecimal precio, String ingredientes,
                                String alergenos, String complejidad, Integer tiempoPreparacionMinutos,
                                String imagenUrl, Categoria categoria) {
        if (nombre != null) this.nombre = nombre;
        if (descripcion != null) this.descripcion = descripcion;
        if (precio != null) this.precio = precio;
        if (ingredientes != null) this.ingredientes = ingredientes;
        if (alergenos != null) this.alergenos = alergenos;
        if (complejidad != null) this.complejidad = complejidad;
        if (tiempoPreparacionMinutos != null) this.tiempoPreparacionMinutos = tiempoPreparacionMinutos;
        if (imagenUrl != null) this.imagenUrl = imagenUrl;
        if (categoria != null) this.categoria = categoria;
    }

    public void cambiarDisponibilidad(Boolean disponible) {
        if (disponible != null) {
            this.disponible = disponible;
        }
    }
}