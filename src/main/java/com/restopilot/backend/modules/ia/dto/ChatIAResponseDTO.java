package com.restopilot.backend.modules.ia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.restopilot.backend.modules.reportes.dto.EstadoPagosDTO;
import com.restopilot.backend.modules.reportes.dto.TipoReporte;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ChatIAResponseDTO(Estado estado, String mensaje, DatosReporte datos,
                                @JsonInclude(JsonInclude.Include.NON_NULL) Long pedidoId) {
    public ChatIAResponseDTO(Estado estado, String mensaje, DatosReporte datos) {
        this(estado, mensaje, datos, null);
    }

    public ChatIAResponseDTO(Estado estado, String mensaje) {
        this(estado, mensaje, null);
    }

    public enum Estado {
        ANSWER, CLARIFICATION_REQUIRED, NO_DATA
    }

    public record DatosReporte(
            TipoReporte tipo,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String unidad,
            String moneda,
            List<String> platosMasVendidos,
            EstadoPagosDTO estadoPagos,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Integer> horariosMayorDemanda,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer pedidosAtendidos,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer reservasCompletadas,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer volumenAtenciones
    ) {
        public DatosReporte(TipoReporte tipo, LocalDate fechaInicio, LocalDate fechaFin,
                            String unidad, String moneda, List<String> platosMasVendidos,
                            EstadoPagosDTO estadoPagos) {
            this(tipo, fechaInicio, fechaFin, unidad, moneda, platosMasVendidos, estadoPagos,
                    null, null, null, null);
        }
    }
}
