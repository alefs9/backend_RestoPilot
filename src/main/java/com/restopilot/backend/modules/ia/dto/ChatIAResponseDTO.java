package com.restopilot.backend.modules.ia.dto;

import com.restopilot.backend.modules.reportes.dto.EstadoPagosDTO;
import com.restopilot.backend.modules.reportes.dto.TipoReporte;
import java.time.LocalDate;
import java.util.List;

public record ChatIAResponseDTO(Estado estado, String mensaje, DatosReporte datos) {
    public ChatIAResponseDTO(Estado estado, String mensaje) {
        this(estado, mensaje, null);
    }

    public enum Estado {
        ANSWER, CLARIFICATION_REQUIRED, NO_DATA
    }

    public record DatosReporte(TipoReporte tipo, LocalDate fechaInicio, LocalDate fechaFin,
                               String unidad, String moneda, List<String> platosMasVendidos,
                               EstadoPagosDTO estadoPagos) {}
}
