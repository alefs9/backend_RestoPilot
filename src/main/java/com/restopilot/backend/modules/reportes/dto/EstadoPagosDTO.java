package com.restopilot.backend.modules.reportes.dto;

import java.math.BigDecimal;
import java.util.Map;

public record EstadoPagosDTO(
        BigDecimal montoTotal,
        Integer pedidosPagados,
        Integer pagosPendientes,
        Map<String, Integer> metodosPagoUtilizados
) {}
