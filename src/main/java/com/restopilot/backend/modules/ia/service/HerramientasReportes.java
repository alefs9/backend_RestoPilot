package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.DatosReporte;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.Estado;
import com.restopilot.backend.modules.reportes.dto.TipoReporte;
import com.restopilot.backend.modules.reportes.service.ReporteService;
import com.restopilot.backend.security.AccesoRestaurante;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

final class HerramientasReportes {
    private final ReporteService reportes;
    private final AccesoRestaurante acceso;
    private final Long restauranteId;
    private ChatIAResponseDTO resultado;
    private RuntimeException fallo;

    HerramientasReportes(ReporteService reportes, AccesoRestaurante acceso, Long restauranteId) {
        this.reportes = reportes;
        this.acceso = acceso;
        this.restauranteId = restauranteId;
    }

    @Tool(description = "Consulta el ranking de platos vendidos para un período", returnDirect = true)
    public boolean platosMasVendidos(@ToolParam(description = "Primer día inclusive, YYYY-MM-DD") String fechaInicio,
                                  @ToolParam(description = "Último día inclusive, YYYY-MM-DD") String fechaFin) {
        consultar(TipoReporte.PLATOS, fechaInicio, fechaFin);
        return fallo == null;
    }

    @Tool(description = "Consulta pagos confirmados y pendientes para un período", returnDirect = true)
    public boolean pagosPorPeriodo(@ToolParam(description = "Primer día inclusive, YYYY-MM-DD") String fechaInicio,
                                @ToolParam(description = "Último día inclusive, YYYY-MM-DD") String fechaFin) {
        consultar(TipoReporte.PAGOS, fechaInicio, fechaFin);
        return fallo == null;
    }

    @Tool(description = "Solicita un período cuando falta o es ambiguo", returnDirect = true)
    public boolean aclararPeriodo() {
        if (resultado == null) {
            resultado = new ChatIAResponseDTO(Estado.CLARIFICATION_REQUIRED,
                    "Indica un mes con su año o un rango con fecha de inicio y fin.");
        }
        return true;
    }

    private void consultar(TipoReporte tipo, String fechaInicio, String fechaFin) {
        if (resultado != null || fallo != null) {
            return;
        }
        try {
            acceso.exigirGestion(restauranteId);
            LocalDate inicio = fechaIso(fechaInicio);
            LocalDate fin = fechaIso(fechaFin);
            if (inicio == null || fin == null || inicio.isAfter(fin)) {
                aclararPeriodo();
                return;
            }

            var reporte = reportes.generarReporte(inicio, fin, tipo);
            var platos = reporte.platosMasVendidos();
            var pagos = reporte.estadoPagos();
            boolean sinDatos = tipo == TipoReporte.PLATOS
                    ? platos == null || platos.isEmpty()
                    : pagos == null || (pagos.pedidosPagados() == 0 && pagos.pagosPendientes() == 0);
            var datos = new DatosReporte(tipo, inicio, fin, tipo == TipoReporte.PLATOS ? "unidades" : null,
                    tipo == TipoReporte.PAGOS ? "PEN" : null, platos, pagos);
            String mensaje = tipo == TipoReporte.PLATOS ? "Platos más vendidos del período."
                    : "Pagos confirmados y pendientes del período.";
            resultado = sinDatos ? new ChatIAResponseDTO(Estado.NO_DATA,
                    "No hay registros para este reporte en el período solicitado.",
                    new DatosReporte(tipo, inicio, fin, datos.unidad(), datos.moneda(), null, null))
                    : new ChatIAResponseDTO(Estado.ANSWER, mensaje, datos);
        } catch (RuntimeException ex) {
            fallo = ex;
        }
    }

    private LocalDate fechaIso(String fecha) {
        if (fecha == null || !fecha.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            return null;
        }
        try {
            LocalDate dia = LocalDate.parse(fecha);
            return dia.getYear() > 0 ? dia : null;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    ChatIAResponseDTO resultado() {
        return resultado;
    }

    RuntimeException fallo() {
        return fallo;
    }
}
