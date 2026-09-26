package com.restopilot.backend.modules.reportes.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReporteResponseDTO(
        String mensaje,
        List<String> platosMasVendidos,
        Map<String, Integer> horariosMayorDemanda,
        Integer volumenAtenciones,
        Double ocupacionMesasPorcentaje,
        EstadoPagosDTO estadoPagos
) {}

record EstadoPagosDTO(
        BigDecimal montoTotal,
        Integer pedidosPagados,
        Integer pagosPendientes,
        Map<String, Integer> metodosPagoUtilizados
) {}