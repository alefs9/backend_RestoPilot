package com.restopilot.backend.modules.reservas.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.reservas.dto.MesaDisponibilidadDTO;
import com.restopilot.backend.modules.reservas.dto.ReservaRequestDTO;
import com.restopilot.backend.modules.reservas.dto.ReservaResponseDTO;
import com.restopilot.backend.modules.reservas.entity.EstadoReserva;
import com.restopilot.backend.modules.reservas.entity.Mesa;
import com.restopilot.backend.modules.reservas.entity.Reserva;
import com.restopilot.backend.modules.reservas.mapper.ReservaMapper;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final MesaRepository mesaRepository;
    private final RestauranteRepository restauranteRepository;
    private final ReservaMapper reservaMapper;
    private final CurrentUser currentUser;

    /**
     * TSK-013: Consultar disponibilidad de mesas por fecha y rango horario.
     */
    @Transactional(readOnly = true)
    public List<MesaDisponibilidadDTO> consultarDisponibilidad(
            Long restauranteId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {

        Restaurante restaurante = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante con ID " + restauranteId + " no encontrado"));

        if (Boolean.FALSE.equals(restaurante.getTieneAtencionFisica())) {
            throw new BusinessRuleException(
                    "El restaurante no tiene habilitada la atención física. No se pueden consultar mesas.");
        }

        if (!horaInicio.isBefore(horaFin)) {
            throw new BusinessRuleException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        if (fecha.isBefore(LocalDate.now())) {
            throw new BusinessRuleException("No se puede consultar disponibilidad para una fecha pasada.");
        }

        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null) {
            if (horaInicio.isBefore(restaurante.getHoraApertura()) || horaFin.isAfter(restaurante.getHoraCierre())) {
                throw new BusinessRuleException(
                        "El horario solicitado está fuera del horario de atención del restaurante ("
                                + restaurante.getHoraApertura() + " - " + restaurante.getHoraCierre() + ").");
            }
        }

        List<Mesa> mesasActivas = mesaRepository.findByRestauranteIdAndActivoTrue(restauranteId);

        if (mesasActivas.isEmpty()) {
            throw new ResourceNotFoundException("El restaurante no tiene mesas activas configuradas.");
        }

        List<EstadoReserva> estadosBloqueantes = List.of(
                EstadoReserva.PENDIENTE,
                EstadoReserva.CONFIRMADA
        );

        List<Long> mesasOcupadasIds = reservaRepository.findMesaIdsConReservasSolapadas(
                restauranteId, fecha, horaInicio, horaFin, estadosBloqueantes);

        Set<Long> mesasOcupadasSet = new HashSet<>(mesasOcupadasIds);

        return mesasActivas.stream()
                .map(mesa -> new MesaDisponibilidadDTO(
                        mesa.getId(),
                        mesa.getNumero(),
                        mesa.getCapacidad(),
                        mesa.getCoordenadaX(),
                        mesa.getCoordenadaY(),
                        !mesasOcupadasSet.contains(mesa.getId())
                ))
                .collect(Collectors.toList());
    }

    /**
     * TSK-014 (US06): Crear endpoint POST para registrar reservas originadas
     * desde el plano visual interactivo del salón.
     */
    @Transactional
    public ReservaResponseDTO registrarReserva(ReservaRequestDTO request) {
        Usuario cliente = currentUser.get();

        Restaurante restaurante = restauranteRepository.findById(request.restauranteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurante con ID " + request.restauranteId() + " no encontrado"));

        if (Boolean.FALSE.equals(restaurante.getActivo())) {
            throw new BusinessRuleException("El restaurante se encuentra inactivo actualmente.");
        }

        if (Boolean.FALSE.equals(restaurante.getTieneAtencionFisica())) {
            throw new BusinessRuleException(
                    "El restaurante no tiene habilitada la atención física. No se pueden realizar reservas.");
        }

        if (request.fecha().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("No se pueden registrar reservas en fechas pasadas.");
        }

        if (!request.horaInicio().isBefore(request.horaFin())) {
            throw new BusinessRuleException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        if (request.fecha().isEqual(LocalDate.now()) && request.horaInicio().isBefore(LocalTime.now())) {
            throw new BusinessRuleException("La hora de inicio no puede ser anterior a la hora actual.");
        }

        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null) {
            if (request.horaInicio().isBefore(restaurante.getHoraApertura()) || request.horaFin().isAfter(restaurante.getHoraCierre())) {
                throw new BusinessRuleException(
                        "El horario solicitado está fuera del horario de atención del restaurante ("
                                + restaurante.getHoraApertura() + " a " + restaurante.getHoraCierre() + ").");
            }
        }

        Mesa mesa = mesaRepository.findById(request.mesaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mesa con ID " + request.mesaId() + " no encontrada"));

        if (!mesa.getRestaurante().getId().equals(restaurante.getId())) {
            throw new BusinessRuleException("La mesa seleccionada no pertenece al restaurante especificado.");
        }

        if (Boolean.FALSE.equals(mesa.getActivo())) {
            throw new BusinessRuleException("La mesa seleccionada no se encuentra activa para reservas.");
        }

        if (request.numeroComensales() > mesa.getCapacidad()) {
            throw new BusinessRuleException(
                    "El número de comensales (" + request.numeroComensales() +
                            ") excede la capacidad máxima de la mesa (" + mesa.getCapacidad() + " personas).");
        }

        List<EstadoReserva> estadosBloqueantes = List.of(
                EstadoReserva.PENDIENTE,
                EstadoReserva.CONFIRMADA
        );

        if (reservaRepository.existsReservaSolapada(
                mesa.getId(), request.fecha(), request.horaInicio(), request.horaFin(), estadosBloqueantes)) {
            throw new BusinessRuleException("La mesa seleccionada ya se encuentra reservada en el horario solicitado.");
        }

        Reserva reserva = Reserva.builder()
                .mesa(mesa)
                .usuario(cliente)
                .restaurante(restaurante)
                .fecha(request.fecha())
                .horaInicio(request.horaInicio())
                .horaFin(request.horaFin())
                .numeroComensales(request.numeroComensales())
                .notas(request.notas())
                .estado(EstadoReserva.PENDIENTE)
                .build();

        Reserva reservaGuardada = reservaRepository.save(reserva);
        return reservaMapper.toResponse(reservaGuardada);
    }
}
