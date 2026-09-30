package com.restopilot.backend.modules.reservas.entity;

import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mesas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    // Numero visible de la mesa en el salon (ej: Mesa 1, Mesa 2...)
    @Column(name = "numero", nullable = false)
    private Integer numero;

    // Cantidad maxima de comensales que soporta la mesa
    @Column(name = "capacidad", nullable = false)
    private Integer capacidad;

    // Coordenadas para el plano visual interactivo del salon (frontend)
    @Column(name = "coordenada_x", nullable = false)
    private Double coordenadaX;

    @Column(name = "coordenada_y", nullable = false)
    private Double coordenadaY;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
        if (this.activo == null) {
            this.activo = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }
}