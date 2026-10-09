package com.restopilot.backend.modules.reportes.service;

import com.restopilot.backend.core.exception.*;
import com.restopilot.backend.modules.reportes.dto.*;
import com.restopilot.backend.modules.reportes.repository.ReporteRepository;
import com.restopilot.backend.modules.pagos.entity.*;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import com.restopilot.backend.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {
    private final ReporteRepository repository;
    private final RestauranteRepository restauranteRepository;
    private final MesaRepository mesaRepository;
    private final CurrentUser currentUser;
    private final AccesoRestaurante acceso;

    public ReporteResponseDTO generarReporteConsolidado(LocalDate inicio, LocalDate fin) {
        return generarReporte(inicio, fin, TipoReporte.CONSOLIDADO);
    }

    public ReporteResponseDTO generarReportePagos(LocalDate inicio, LocalDate fin) {
        return generarReporte(inicio, fin, TipoReporte.PAGOS);
    }

    public ReporteResponseDTO generarReporte(LocalDate inicio, LocalDate fin, TipoReporte tipo) {
        if (inicio == null || fin == null || inicio.isAfter(fin) || fin.equals(LocalDate.MAX)) {
            throw new BusinessRuleException("El rango de fechas no es válido.");
        }
        Long restauranteId = currentUser.getRestauranteId();
        acceso.exigirGestion(restauranteId);
        LocalDateTime desde = inicio.atStartOfDay();
        LocalDateTime hasta = fin.plusDays(1).atStartOfDay();
        if (repository.contarPedidos(restauranteId, desde, hasta) == 0 && repository.contarReservas(restauranteId, inicio, fin) == 0) {
            return new ReporteResponseDTO("No existen datos registrados para el rango de fechas seleccionado.", null, null, null, null, null);
        }
        boolean todos = tipo == TipoReporte.CONSOLIDADO;
        List<String> platos = todos || tipo == TipoReporte.PLATOS ? repository.platosVendidos(restauranteId, desde, hasta)
                .stream().map(fila -> fila[0] + " (" + ((Number) fila[1]).longValue() + " unidades)").toList() : null;
        Map<String, Integer> demanda = null;
        if (todos || tipo == TipoReporte.DEMANDA) {
            demanda = new LinkedHashMap<>();
            for (Object[] fila : repository.demanda(restauranteId, desde, hasta)) {
                int hora = ((Number) fila[0]).intValue();
                demanda.put(String.format("%02d:00 - %02d:00", hora, hora + 1), Math.toIntExact(((Number) fila[1]).longValue()));
            }
        }
        Integer atenciones = todos || tipo == TipoReporte.ATENCIONES ? Math.toIntExact(repository.pedidosAtendidos(restauranteId, desde, hasta)
                + repository.reservasAtendidas(restauranteId, inicio, fin)) : null;
        Double ocupacion = todos || tipo == TipoReporte.OCUPACION ? calcularOcupacion(restauranteId, inicio, fin) : null;
        EstadoPagosDTO pagos = todos || tipo == TipoReporte.PAGOS ? calcularPagos(restauranteId, desde, hasta) : null;
        return new ReporteResponseDTO("Reporte generado exitosamente.", platos, demanda, atenciones, ocupacion, pagos);
    }

    private EstadoPagosDTO calcularPagos(Long restauranteId, LocalDateTime inicio, LocalDateTime fin) {
        List<Pago> confirmados = repository.pagos(restauranteId, inicio, fin).stream().filter(p -> p.getEstado() == EstadoPago.PAGADO).toList();
        BigDecimal total = confirmados.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Integer> metodos = new TreeMap<>();
        confirmados.stream().filter(p -> p.getMetodo() != null).forEach(p -> metodos.merge(p.getMetodo().name(), 1, Integer::sum));
        long pendientes = repository.pedidosNoCancelados(restauranteId, inicio, fin) - confirmados.size();
        return new EstadoPagosDTO(total, confirmados.size(), Math.toIntExact(pendientes), metodos);
    }

    /** Ocupación reservada: minutos de mesas confirmadas/completadas / minutos disponibles. */
    private double calcularOcupacion(Long restauranteId, LocalDate inicio, LocalDate fin) {
        Restaurante restaurante = restauranteRepository.findById(restauranteId).orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado."));
        long mesas = mesaRepository.findByRestauranteIdAndActivoTrue(restauranteId).size();
        if (mesas == 0 || restaurante.getHoraApertura() == null || restaurante.getHoraCierre() == null) return 0;
        long minutosDia = Duration.between(restaurante.getHoraApertura(), restaurante.getHoraCierre()).toMinutes();
        if (minutosDia <= 0) throw new BusinessRuleException("Configure un horario de atención válido para calcular la ocupación.");
        long ocupados = repository.ocupacion(restauranteId, inicio, fin).stream().mapToLong(r -> {
            LocalTime desde = r.getHoraInicio().isBefore(restaurante.getHoraApertura()) ? restaurante.getHoraApertura() : r.getHoraInicio();
            LocalTime hasta = r.getHoraFin().isAfter(restaurante.getHoraCierre()) ? restaurante.getHoraCierre() : r.getHoraFin();
            return Math.max(0, Duration.between(desde, hasta).toMinutes());
        }).sum();
        double disponibles = mesas * (double) minutosDia * (ChronoUnit.DAYS.between(inicio, fin) + 1);
        return Math.round(10000.0 * ocupados / disponibles) / 100.0;
    }
}
