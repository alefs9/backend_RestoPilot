package com.restopilot.backend.modules.restaurante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "restaurantes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 255)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    // Horarios de atencion para validar reservas y pedidos
    @Column(name = "hora_apertura")
    private LocalTime horaApertura;

    @Column(name = "hora_cierre")
    private LocalTime horaCierre;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    // Banderas de adaptabilidad SaaS
    @Builder.Default
    @Column(name = "tiene_atencion_fisica", nullable = false)
    private Boolean tieneAtencionFisica = true;

    @Builder.Default
    @Column(name = "acepta_delivery", nullable = false)
    private Boolean aceptaDelivery = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
        if (this.tieneAtencionFisica == null) {
            this.tieneAtencionFisica = true;
        }
        if (this.aceptaDelivery == null) {
            this.aceptaDelivery = true;
        }
        if (this.activo == null) {
            this.activo = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }
}