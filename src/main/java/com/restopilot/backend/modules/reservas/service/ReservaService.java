package com.restopilot.backend.modules.reservas.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.reservas.dto.MesaDisponibilidadDTO;
import com.restopilot.backend.modules.reservas.entity.EstadoReserva;
import com.restopilot.backend.modules.reservas.entity.Mesa;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final MesaRepository mesaRepository;
    private final RestauranteRepository restauranteRepository;

    /**
     * TSK-013: Consultar disponibilidad de mesas por fecha y rango horario.
     *
     * Retorna TODAS las mesas activas del restaurante, cada una con un booleano
     * indicando si está disponible (true) o ya reservada (false) en ese horario.
     * Esto permite al frontend pintar el plano interactivo con colores.
     *
     * @param restauranteId ID del restaurante a consultar
     * @param fecha         Fecha de la reserva deseada
     * @param horaInicio    Hora de inicio deseada
     * @param horaFin       Hora de fin deseada
     * @return Lista de mesas con su estado de disponibilidad
     */
    @Transactional(readOnly = true)
    public List<MesaDisponibilidadDTO> consultarDisponibilidad(
            Long restauranteId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {

        // Validar que el restaurante existe
        Restaurante restaurante = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante con ID " + restauranteId + " no encontrado"));

        // Validar que el restaurante tiene atencion fisica habilitada
        if (Boolean.FALSE.equals(restaurante.getTieneAtencionFisica())) {
            throw new BusinessRuleException(
                    "El restaurante no tiene habilitada la atención física. No se pueden consultar mesas.");
        }

        // Validar que la hora de inicio sea anterior a la hora de fin
        if (!horaInicio.isBefore(horaFin)) {
            throw new BusinessRuleException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        // Validar que la fecha no sea anterior a hoy
        if (fecha.isBefore(LocalDate.now())) {
            throw new BusinessRuleException("No se puede consultar disponibilidad para una fecha pasada.");
        }

        // Validar horario de atencion del restaurante (si tiene configurado)
        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null) {
            if (horaInicio.isBefore(restaurante.getHoraApertura()) || horaFin.isAfter(restaurante.getHoraCierre())) {
                throw new BusinessRuleException(
                        "El horario solicitado está fuera del horario de atención del restaurante ("
                                + restaurante.getHoraApertura() + " - " + restaurante.getHoraCierre() + ").");
            }
        }

        // Obtener todas las mesas activas del restaurante
        List<Mesa> mesasActivas = mesaRepository.findByRestauranteIdAndActivoTrue(restauranteId);

        if (mesasActivas.isEmpty()) {
            throw new ResourceNotFoundException("El restaurante no tiene mesas activas configuradas.");
        }

        // Estados que bloquean la mesa (PENDIENTE y CONFIRMADA)
        List<EstadoReserva> estadosBloqueantes = List.of(
                EstadoReserva.PENDIENTE,
                EstadoReserva.CONFIRMADA
        );

        // Obtener IDs de mesas que YA tienen reservas solapadas en ese horario
        List<Long> mesasOcupadasIds = reservaRepository.findMesaIdsConReservasSolapadas(
                restauranteId, fecha, horaInicio, horaFin, estadosBloqueantes);

        Set<Long> mesasOcupadasSet = new HashSet<>(mesasOcupadasIds);

        // Construir la respuesta: cada mesa con su disponibilidad
        return mesasActivas.stream()
                .map(mesa -> new MesaDisponibilidadDTO(
                        mesa.getId(),
                        mesa.getNumero(),
                        mesa.getCapacidad(),
                        mesa.getCoordenadaX(),
                        mesa.getCoordenadaY(),
                        !mesasOcupadasSet.contains(mesa.getId())  // true si NO está en la lista de ocupadas
                ))
                .collect(Collectors.toList());
    }
}
