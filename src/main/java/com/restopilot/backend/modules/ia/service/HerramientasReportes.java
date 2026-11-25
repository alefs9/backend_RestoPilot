package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.DatosReporte;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.Estado;
import com.restopilot.backend.modules.reportes.dto.EstadoPagosDTO;
import com.restopilot.backend.modules.reportes.dto.TipoReporte;
import com.restopilot.backend.modules.reportes.service.ReporteService;
import com.restopilot.backend.security.AccesoRestaurante;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    @Tool(description = "Consulta el volumen de atención (pedidos atendidos y reservas completadas) para un período", returnDirect = true)
    public boolean volumenAtencion(@ToolParam(description = "Primer día inclusive, YYYY-MM-DD") String fechaInicio,
                                @ToolParam(description = "Último día inclusive, YYYY-MM-DD") String fechaFin) {
        consultar(TipoReporte.ATENCIONES, fechaInicio, fechaFin);
        return fallo == null;
    }

    @Tool(description = "Consulta los horarios con mayor demanda de pedidos para un período", returnDirect = true)
    public boolean horariosMayorDemanda(@ToolParam(description = "Primer día inclusive, YYYY-MM-DD") String fechaInicio,
                                     @ToolParam(description = "Último día inclusive, YYYY-MM-DD") String fechaFin) {
        consultar(TipoReporte.DEMANDA, fechaInicio, fechaFin);
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
            LocalDate inicio = interpretarFecha(fechaInicio, false);
            LocalDate fin = interpretarFecha(fechaFin, true);
            if (inicio == null || fin == null || inicio.isAfter(fin)) {
                aclararPeriodo();
                return;
            }

            var reporte = reportes.generarReporte(inicio, fin, tipo);
            var platos = reporte.platosMasVendidos();
            var pagos = reporte.estadoPagos();
            var demanda = reporte.horariosMayorDemanda();
            Integer pedidosAtendidos = reporte.pedidosAtendidos();
            Integer reservasCompletadas = reporte.reservasCompletadas();
            Integer volumenAtenciones = reporte.volumenAtenciones();

            boolean sinDatos = switch (tipo) {
                case PLATOS -> platos == null || platos.isEmpty();
                case PAGOS -> pagos == null || (pagos.pedidosPagados() == 0 && pagos.pagosPendientes() == 0);
                case ATENCIONES -> (pedidosAtendidos == null || pedidosAtendidos == 0)
                        && (reservasCompletadas == null || reservasCompletadas == 0);
                case DEMANDA -> demanda == null || demanda.isEmpty();
                default -> false;
            };

            String unidad = tipo == TipoReporte.PLATOS ? "unidades" : null;
            String moneda = tipo == TipoReporte.PAGOS ? "PEN" : null;

            if (sinDatos) {
                resultado = new ChatIAResponseDTO(Estado.NO_DATA,
                        "No hay registros para este reporte en el período solicitado.",
                        new DatosReporte(tipo, inicio, fin, unidad, moneda, null, null, null, null, null, null));
                return;
            }

            String mensaje = construirResumen(tipo, platos, pagos, demanda, pedidosAtendidos, reservasCompletadas);
            var datos = new DatosReporte(tipo, inicio, fin, unidad, moneda, platos, pagos, demanda,
                    pedidosAtendidos, reservasCompletadas, volumenAtenciones);
            resultado = new ChatIAResponseDTO(Estado.ANSWER, mensaje, datos);
        } catch (RuntimeException ex) {
            fallo = ex;
        }
    }

    private String construirResumen(TipoReporte tipo, List<String> platos, EstadoPagosDTO pagos,
                                   Map<String, Integer> demanda,
                                   Integer pedidosAtendidos, Integer reservasCompletadas) {
        return switch (tipo) {
            case PLATOS -> {
                if (platos != null && !platos.isEmpty()) {
                    String lider = platos.getFirst();
                    yield "Platos más vendidos del período. " + lider + " lidera la preferencia de los clientes.";
                }
                yield "Platos más vendidos del período.";
            }
            case PAGOS -> {
                if (pagos != null) {
                    yield String.format(Locale.forLanguageTag("es-PE"),
                            "Pagos confirmados y pendientes del período: total de S/ %,.2f en %d pedido(s) pagado(s) y %d pago(s) pendiente(s).",
                            pagos.montoTotal(), pagos.pedidosPagados(), pagos.pagosPendientes());
                }
                yield "Pagos confirmados y pendientes del período.";
            }
            case ATENCIONES -> {
                int ped = pedidosAtendidos != null ? pedidosAtendidos : 0;
                int res = reservasCompletadas != null ? reservasCompletadas : 0;
                yield String.format("Volumen de atención del período: %d pedidos atendidos y %d reservas completadas.", ped, res);
            }
            case DEMANDA -> {
                if (demanda != null && !demanda.isEmpty()) {
                    var pico = demanda.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
                    if (pico != null) {
                        yield String.format("Horarios de mayor demanda: el pico de actividad se registró a las %s con %d pedido(s).", pico.getKey(), pico.getValue());
                    }
                }
                yield "Horarios de mayor demanda de pedidos del período.";
            }
            default -> "Reporte generado exitosamente.";
        };
    }

    private LocalDate interpretarFecha(String fecha, boolean finDeMesPorDefecto) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        String limpia = fecha.trim();
        if (limpia.matches("^[0-9]{4}-[0-9]{2}$")) {
            try {
                YearMonth ym = YearMonth.parse(limpia);
                return finDeMesPorDefecto ? ym.atEndOfMonth() : ym.atDay(1);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        if (!limpia.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) {
            return null;
        }
        try {
            LocalDate dia = LocalDate.parse(limpia);
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
