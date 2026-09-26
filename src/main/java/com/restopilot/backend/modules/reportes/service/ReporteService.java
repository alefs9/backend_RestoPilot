package com.restopilot.backend.modules.reportes.service;

import com.restopilot.backend.modules.reportes.dto.ReporteResponseDTO;
import com.restopilot.backend.modules.reportes.dto.EstadoPagosDTO;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private final PedidoRepository pedidoRepository;
    private final ReservaRepository reservaRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public ReporteResponseDTO generarReporteConsolidado(LocalDate fechaInicio, LocalDate fechaFin) {
        Long restauranteId = currentUser.getRestauranteId();

        // Escenario 2: Período sin datos registrados
        if (!existenDatosEnPeriodo(restauranteId, fechaInicio, fechaFin)) {
            return new ReporteResponseDTO(
                    "No existen datos registrados para el rango de fechas seleccionado.",
                    null, null, null, null, null
            );
        }

        // Escenario 1: Procesamiento de información y consolidación de indicadores
        return new ReporteResponseDTO(
                "Reporte consolidado generado exitosamente.",
                obtenerPlatosMasVendidos(restauranteId, fechaInicio, fechaFin),
                obtenerHorariosDemanda(restauranteId, fechaInicio, fechaFin),
                calcularVolumenAtenciones(restauranteId, fechaInicio, fechaFin),
                calcularOcupacionMesas(restauranteId, fechaInicio, fechaFin),
                generarEstadoPagos(restauranteId, fechaInicio, fechaFin)
        );
    }

    @Transactional(readOnly = true)
    public ReporteResponseDTO generarReportePagos(LocalDate fechaInicio, LocalDate fechaFin) {
        Long restauranteId = currentUser.getRestauranteId();

        // Escenario 2 aplicado a consultas específicas
        if (!existenDatosEnPeriodo(restauranteId, fechaInicio, fechaFin)) {
            return new ReporteResponseDTO(
                    "No existen datos registrados para el rango de fechas seleccionado.",
                    null, null, null, null, null
            );
        }

        // Escenario 3 y 4: Retorna únicamente la información correspondiente al reporte de pagos
        return new ReporteResponseDTO(
                "Reporte de estado de pagos generado exitosamente.",
                null, null, null, null,
                generarEstadoPagos(restauranteId, fechaInicio, fechaFin)
        );
    }

    // Métodos auxiliares de extracción (Deberán conectarse a consultas JPQL en los repositorios)

    private boolean existenDatosEnPeriodo(Long restauranteId, LocalDate inicio, LocalDate fin) {
        // Valida contra la base de datos si existen pedidos o reservas en las fechas
        // return pedidoRepository.countByRestauranteIdAndFechas(...) > 0;
        return true;
    }

    private EstadoPagosDTO generarEstadoPagos(Long restauranteId, LocalDate inicio, LocalDate fin) {
        // Estructura para el Escenario 4: monto total, pedidos pagados, pendientes y métodos utilizados
        return new EstadoPagosDTO(
                BigDecimal.valueOf(15250.50), // Monto total (Ejemplo)
                185,                          // Pagados
                12,                           // Pendientes
                Map.of("YAPE", 80, "TARJETA", 65, "PLIN", 40) // Métodos
        );
    }

    private List<String> obtenerPlatosMasVendidos(Long restauranteId, LocalDate inicio, LocalDate fin) {
        return List.of("Ceviche Clásico", "Lomo Saltado", "Ají de Gallina");
    }

    private Map<String, Integer> obtenerHorariosDemanda(Long restauranteId, LocalDate inicio, LocalDate fin) {
        return Map.of("13:00 - 14:00", 45, "19:00 - 20:00", 60);
    }

    private Integer calcularVolumenAtenciones(Long restauranteId, LocalDate inicio, LocalDate fin) {
        return 210;
    }

    private Double calcularOcupacionMesas(Long restauranteId, LocalDate inicio, LocalDate fin) {
        return 82.5;
    }
}