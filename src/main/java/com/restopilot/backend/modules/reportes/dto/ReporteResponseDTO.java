package com.restopilot.backend.modules.reportes.dto;

import java.util.List;
import java.util.Map;

/** Métricas separadas; volumenAtenciones conserva la suma histórica por compatibilidad. */
public record ReporteResponseDTO(
        String mensaje,
        List<String> platosMasVendidos,
        Map<String, Integer> horariosMayorDemanda,
        Integer volumenAtenciones,
        Double ocupacionMesasPorcentaje,
        EstadoPagosDTO estadoPagos,
        Integer pedidosAtendidos,
        Integer reservasCompletadas
) {
    public ReporteResponseDTO(String mensaje, List<String> platosMasVendidos,
            Map<String, Integer> horariosMayorDemanda, Integer volumenAtenciones,
            Double ocupacionMesasPorcentaje, EstadoPagosDTO estadoPagos) {
        this(mensaje, platosMasVendidos, horariosMayorDemanda, volumenAtenciones,
                ocupacionMesasPorcentaje, estadoPagos, null, null);
    }
}
