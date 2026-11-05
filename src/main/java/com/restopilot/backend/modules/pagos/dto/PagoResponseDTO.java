package com.restopilot.backend.modules.pagos.dto;

import com.restopilot.backend.modules.pagos.entity.EstadoPago;
import com.restopilot.backend.modules.pagos.entity.MetodoPago;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoResponseDTO {

    private String mensaje;
    private Long id;
    private Long pedidoId;
    private BigDecimal monto;
    private MetodoPago metodo;
    private EstadoPago estado;
    private String codigoReferencia;
    private LocalDateTime fechaPago;
    private LocalDateTime creadoEn;
}