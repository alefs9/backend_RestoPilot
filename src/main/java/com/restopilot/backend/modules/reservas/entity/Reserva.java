package com.restopilot.backend.modules.reservas.entity;

import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "reservas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(length = 300)
    private String motivoRechazo;

    // Mesa asignada a esta reserva
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    // Cliente que realizó la reserva
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Restaurante al que pertenece la reserva (denormalizado para consultas rapidas)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    // Fecha de la reserva (solo la fecha, sin hora)
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    // Hora de inicio de la reserva
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    // Hora de fin de la reserva
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    // Cantidad de personas que asistirán
    @Column(name = "numero_comensales", nullable = false)
    private Integer numeroComensales;

    // Estado actual de la reserva (PENDIENTE, CONFIRMADA, CANCELADA, COMPLETADA)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.PENDIENTE;

    // Notas adicionales del cliente (ej: "Cumpleaños", "Ventana", "Alergias")
    @Column(name = "notas", length = 500)
    private String notas;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoReserva.PENDIENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }
}
