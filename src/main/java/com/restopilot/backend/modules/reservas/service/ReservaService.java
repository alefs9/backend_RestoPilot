package com.restopilot.backend.modules.reservas.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.reservas.dto.MesaDisponibilidadDTO;
import com.restopilot.backend.modules.reservas.dto.ModuloReservasStatusDTO;
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
import java.time.LocalDateTime;
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
    private final java.time.Clock clock;
    private final com.restopilot.backend.security.AccesoRestaurante acceso;
    private final com.restopilot.backend.modules.notificaciones.service.NotificacionService notificaciones;
    private final CurrentUser currentUser;

    /**
     * TSK-013: Consultar disponibilidad de mesas por fecha y rango horario.
     * Retorna todas las mesas activas del restaurante indicando si están disponibles.
     */
    @Transactional(readOnly = true)
    public List<MesaDisponibilidadDTO> consultarDisponibilidad(
            Long restauranteId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        return consultarDisponibilidad(restauranteId, fecha, horaInicio, horaFin, 1);
    }

    @Transactional(readOnly = true)
    public List<MesaDisponibilidadDTO> consultarDisponibilidad(
            Long restauranteId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer comensales) {

        if (comensales == null || comensales < 1) {
            throw new BusinessRuleException("El número de comensales debe ser mayor a cero.");
        }

        Restaurante restaurante = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante con ID " + restauranteId + " no encontrado"));

        if (Boolean.FALSE.equals(restaurante.getTieneAtencionFisica())) {
            throw new BusinessRuleException(
                    "El restaurante no tiene habilitada la atención física. No se pueden consultar mesas.");
        }

        if (!horaInicio.isBefore(horaFin)) {
            throw new BusinessRuleException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        if (fecha.isBefore(LocalDate.now(clock))) {
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

        List<EstadoReserva> estadosBloqueantes = List.of(
                EstadoReserva.PENDIENTE,
                EstadoReserva.CONFIRMADA
        );

        List<Long> mesasOcupadasIds = reservaRepository.findMesaIdsConReservasSolapadas(
                restauranteId, fecha, horaInicio, horaFin, estadosBloqueantes);

        Set<Long> mesasOcupadasSet = new HashSet<>(mesasOcupadasIds);

        return mesasActivas.stream()
                .filter(mesa -> mesa.getCapacidad() >= comensales)
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
        if (cliente.getRol() != Rol.CLIENTE) acceso.exigirGestion(request.restauranteId());

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

        if (request.fecha().isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleException("No se pueden registrar reservas en fechas pasadas.");
        }

        if (!request.horaInicio().isBefore(request.horaFin())) {
            throw new BusinessRuleException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        if (request.fecha().isEqual(LocalDate.now(clock)) && request.horaInicio().isBefore(LocalTime.now(clock))) {
            throw new BusinessRuleException("La hora de inicio no puede ser anterior a la hora actual.");
        }

        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null) {
            if (request.horaInicio().isBefore(restaurante.getHoraApertura()) || request.horaFin().isAfter(restaurante.getHoraCierre())) {
                throw new BusinessRuleException(
                        "El horario solicitado está fuera del horario de atención del restaurante ("
                                + restaurante.getHoraApertura() + " a " + restaurante.getHoraCierre() + ").");
            }
        }

        Mesa mesa = mesaRepository.findByIdForUpdate(request.mesaId())
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

    /**
     * TSK-015 (US08): Endpoint PUT para modificar reserva existente.
     * Permite cambiar mesa, fecha, horario, comensales y notas.
     * Valida permisos: el cliente solo modifica sus reservas; admin/dueno solo las de su restaurante.
     * Valida que no esté cancelada ni completada y que la mesa esté libre (excluyendo la reserva actual).
     */
    @Transactional
    public ReservaResponseDTO modificarReserva(Long id, ReservaRequestDTO request) {
        Usuario usuario = currentUser.get();

        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva con ID " + id + " no encontrada"));

        // Validar que la reserva no esté en estado definitivo (CANCELADA o COMPLETADA)
        if (reserva.getEstado() == EstadoReserva.CANCELADA || reserva.getEstado() == EstadoReserva.COMPLETADA
                || reserva.getEstado() == EstadoReserva.RECHAZADA) {
            throw new BusinessRuleException(
                    "No se puede modificar una reserva que se encuentra en estado " + reserva.getEstado() + ".");
        }

        // Validar permisos de acceso según el rol
        if (usuario.getRol() == Rol.CLIENTE) {
            if (!reserva.getUsuario().getId().equals(usuario.getId())) {
                throw new BusinessRuleException("No tiene permisos para modificar esta reserva.");
            }
        } else {
            if (usuario.getRestaurante() == null || !reserva.getRestaurante().getId().equals(usuario.getRestaurante().getId())) {
                throw new BusinessRuleException("No tiene permisos para modificar reservas de otro restaurante.");
            }
        }

        Restaurante restaurante = reserva.getRestaurante();
        if (!restaurante.getId().equals(request.restauranteId())) {
            throw new BusinessRuleException("No se puede cambiar el restaurante de una reserva.");
        }

        // Validar restaurante activo y atención física
        if (Boolean.FALSE.equals(restaurante.getActivo())) {
            throw new BusinessRuleException("El restaurante se encuentra inactivo actualmente.");
        }

        if (Boolean.FALSE.equals(restaurante.getTieneAtencionFisica())) {
            throw new BusinessRuleException(
                    "El restaurante no tiene habilitada la atención física. No se pueden modificar reservas.");
        }

        // Validar fechas y horarios
        if (request.fecha().isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleException("No se pueden modificar reservas a fechas pasadas.");
        }

        if (!request.horaInicio().isBefore(request.horaFin())) {
            throw new BusinessRuleException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        if (request.fecha().isEqual(LocalDate.now(clock)) && request.horaInicio().isBefore(LocalTime.now(clock))) {
            throw new BusinessRuleException("La hora de inicio no puede ser anterior a la hora actual.");
        }

        // Validar horario comercial del restaurante
        if (restaurante.getHoraApertura() != null && restaurante.getHoraCierre() != null) {
            if (request.horaInicio().isBefore(restaurante.getHoraApertura()) || request.horaFin().isAfter(restaurante.getHoraCierre())) {
                throw new BusinessRuleException(
                        "El horario solicitado está fuera del horario de atención del restaurante ("
                                + restaurante.getHoraApertura() + " a " + restaurante.getHoraCierre() + ").");
            }
        }

        // Validar mesa asignada
        Mesa mesa = mesaRepository.findByIdForUpdate(request.mesaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mesa con ID " + request.mesaId() + " no encontrada"));

        if (!mesa.getRestaurante().getId().equals(restaurante.getId())) {
            throw new BusinessRuleException("La mesa seleccionada no pertenece al restaurante de la reserva.");
        }

        if (Boolean.FALSE.equals(mesa.getActivo())) {
            throw new BusinessRuleException("La mesa seleccionada no se encuentra activa para reservas.");
        }

        // Validar capacidad de comensales
        if (request.numeroComensales() > mesa.getCapacidad()) {
            throw new BusinessRuleException(
                    "El número de comensales (" + request.numeroComensales() +
                            ") excede la capacidad máxima de la mesa (" + mesa.getCapacidad() + " personas).");
        }

        // Validar colisión de horario excluyendo la propia reserva que se está modificando
        List<EstadoReserva> estadosBloqueantes = List.of(
                EstadoReserva.PENDIENTE,
                EstadoReserva.CONFIRMADA
        );

        if (reservaRepository.existsReservaSolapadaExcluyendoId(
                mesa.getId(), reserva.getId(), request.fecha(), request.horaInicio(), request.horaFin(), estadosBloqueantes)) {
            throw new BusinessRuleException("La mesa seleccionada ya se encuentra reservada en el horario solicitado.");
        }

        // Actualizar datos de la reserva
        reserva.setMesa(mesa);
        reserva.setFecha(request.fecha());
        reserva.setHoraInicio(request.horaInicio());
        reserva.setHoraFin(request.horaFin());
        reserva.setNumeroComensales(request.numeroComensales());
        reserva.setNotas(request.notas());
        // Reprogramar exige una nueva evaluación del restaurante.
        reserva.setEstado(EstadoReserva.PENDIENTE);

        Reserva reservaActualizada = reservaRepository.save(reserva);
        return reservaMapper.toResponse(reservaActualizada);
    }

    /**
     * TSK-016 (US08): Cancelar una reserva (PATCH).
     * Cambia el estado a CANCELADA liberando la mesa para nuevas reservas.
     * Valida permisos de autoría según rol y que la reserva no esté ya cancelada o completada.
     */
    @Transactional
    public ReservaResponseDTO cancelarReserva(Long id, String motivo) {
        Usuario usuario = currentUser.get();
        if (motivo != null && motivo.length() > 300) throw new BusinessRuleException("El motivo no puede superar 300 caracteres.");

        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva con ID " + id + " no encontrada"));

        // Validar que no esté ya cancelada
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new BusinessRuleException("La reserva ya se encuentra cancelada.");
        }

        // Validar que no haya sido completada
        if (reserva.getEstado() == EstadoReserva.COMPLETADA || reserva.getEstado() == EstadoReserva.RECHAZADA) {
            throw new BusinessRuleException("No se puede cancelar una reserva que ya ha sido completada.");
        }

        // Validar permisos de autoría
        if (usuario.getRol() == Rol.CLIENTE) {
            if (!reserva.getUsuario().getId().equals(usuario.getId())) {
                throw new BusinessRuleException("No tiene permisos para cancelar esta reserva.");
            }
            LocalDateTime fechaHoraReserva = LocalDateTime.of(reserva.getFecha(), reserva.getHoraInicio());
            if (!LocalDateTime.now(clock).isBefore(fechaHoraReserva.minusHours(2))) {
                throw new BusinessRuleException("La cancelación requiere más de 2 horas de anticipación. Comuníquese vía telefónica con recepción.");
            }
        } else {
            if (usuario.getRestaurante() == null || !reserva.getRestaurante().getId().equals(usuario.getRestaurante().getId())) {
                throw new BusinessRuleException("No tiene permisos para cancelar reservas de otro restaurante.");
            }
        }

        reserva.setEstado(EstadoReserva.CANCELADA);

        if (motivo != null && !motivo.trim().isEmpty()) {
            String notasActuales = (reserva.getNotas() != null && !reserva.getNotas().isBlank())
                    ? reserva.getNotas() + " | "
                    : "";
            String notas = notasActuales + "Motivo cancelación: " + motivo.trim();
            if (notas.length() > 500) throw new BusinessRuleException("Las notas y el motivo no pueden superar 500 caracteres.");
            reserva.setNotas(notas);
        }

        Reserva reservaCancelada = reservaRepository.save(reserva);
        return reservaMapper.toResponse(reservaCancelada);
    }

    /** US17: Solo el restaurante propietario puede decidir sobre una solicitud pendiente. */
    @Transactional
    public ReservaResponseDTO procesarReserva(Long id,
            com.restopilot.backend.modules.reservas.dto.ProcesarReservaRequestDTO request) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada."));
        acceso.exigirGestion(reserva.getRestaurante().getId());
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new BusinessRuleException("La reserva ya fue gestionada.");
        }
        if (request.estado() != EstadoReserva.CONFIRMADA && request.estado() != EstadoReserva.RECHAZADA) {
            throw new BusinessRuleException("Solo se permite confirmar o rechazar una solicitud pendiente.");
        }
        if (request.estado() == EstadoReserva.RECHAZADA && (request.motivo() == null || request.motivo().isBlank())) {
            throw new BusinessRuleException("Debe indicar el motivo del rechazo.");
        }
        if (request.estado() == EstadoReserva.CONFIRMADA
                && (!Boolean.TRUE.equals(reserva.getRestaurante().getActivo())
                || !Boolean.TRUE.equals(reserva.getRestaurante().getTieneAtencionFisica())
                || !LocalDateTime.of(reserva.getFecha(), reserva.getHoraInicio()).isAfter(LocalDateTime.now(clock)))) {
            throw new BusinessRuleException("No se puede confirmar una reserva pasada o de un restaurante sin atención física activa.");
        }
        reserva.setEstado(request.estado());
        reserva.setMotivoRechazo(request.estado() == EstadoReserva.RECHAZADA ? request.motivo().trim() : null);
        reservaRepository.saveAndFlush(reserva);
        notificaciones.enviar(reserva.getUsuario().getId(), "Reserva #" + id + ": " + request.estado()
                + (reserva.getMotivoRechazo() == null ? "" : ". Motivo: " + reserva.getMotivoRechazo()));
        return reservaMapper.toResponse(reserva);
    }

    /**
     * TSK-017 (US07): Consultar historial de reservas del cliente autenticado.
     * Retorna todas las reservas del cliente ordenadas cronológicamente (más recientes primero).
     */
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> obtenerMisReservas() {
        Usuario cliente = currentUser.get();
        return reservaRepository.findByUsuarioIdOrderByFechaDescHoraInicioDesc(cliente.getId())
                .stream()
                .map(reservaMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * TSK-017 (US07): Consultar listado de reservas para el restaurante (vista Admin/Dueño).
     * Permite filtrar opcionalmente por fecha y por estado de reserva.
     */
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> obtenerReservasRestaurante(LocalDate fecha, EstadoReserva estado) {
        Long restauranteId = currentUser.getRestauranteId();

        List<Reserva> reservas;
        if (fecha != null && estado != null) {
            reservas = reservaRepository.findByRestauranteIdAndFechaAndEstadoOrderByHoraInicioAsc(restauranteId, fecha, estado);
        } else if (fecha != null) {
            reservas = reservaRepository.findByRestauranteIdAndFechaOrderByHoraInicioAsc(restauranteId, fecha);
        } else if (estado != null) {
            reservas = reservaRepository.findByRestauranteIdAndEstadoOrderByFechaDescHoraInicioDesc(restauranteId, estado);
        } else {
            reservas = reservaRepository.findByRestauranteIdOrderByFechaDescHoraInicioDesc(restauranteId);
        }

        return reservas.stream()
                .map(reservaMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * TSK-017 (US07): Obtener detalle de una reserva específica por su ID.
     * Valida permisos: el cliente solo ve sus reservas; admin/dueno solo las de su restaurante.
     */
    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerReservaPorId(Long id) {
        Usuario usuario = currentUser.get();

        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva con ID " + id + " no encontrada"));

        if (usuario.getRol() == Rol.CLIENTE) {
            if (!reserva.getUsuario().getId().equals(usuario.getId())) {
                throw new BusinessRuleException("No tiene permisos para ver esta reserva.");
            }
        } else {
            if (usuario.getRestaurante() == null || !reserva.getRestaurante().getId().equals(usuario.getRestaurante().getId())) {
                throw new BusinessRuleException("No tiene permisos para ver reservas de otro restaurante.");
            }
        }

        return reservaMapper.toResponse(reserva);
    }

    /**
     * TSK-018: Consultar el estado de activación del módulo de reservas
     * para el restaurante del usuario autenticado (Admin/Dueño).
     */
    @Transactional(readOnly = true)
    public ModuloReservasStatusDTO obtenerEstadoModulo() {
        Long restauranteId = currentUser.getRestauranteId();
        return obtenerEstadoModuloPorRestaurante(restauranteId);
    }

    /**
     * TSK-018: Consultar si un restaurante específico tiene habilitado el módulo de reservas.
     * Útil para clientes y vistas públicas (para saber si mostrar la opción de reservar).
     */
    @Transactional(readOnly = true)
    public ModuloReservasStatusDTO obtenerEstadoModuloPorRestaurante(Long restauranteId) {
        Restaurante restaurante = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante con ID " + restauranteId + " no encontrado"));

        boolean habilitado = Boolean.TRUE.equals(restaurante.getTieneAtencionFisica());

        List<EstadoReserva> estadosActivos = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);
        long reservasActivas = reservaRepository.countByRestauranteIdAndEstadoInAndFechaGreaterThanEqual(
                restauranteId, estadosActivos, LocalDate.now(clock));

        String mensaje = habilitado
                ? "El módulo de reservas se encuentra activo para este restaurante."
                : "El módulo de reservas se encuentra deshabilitado (el restaurante opera en modalidad virtual/delivery).";

        return new ModuloReservasStatusDTO(
                restaurante.getId(),
                restaurante.getNombre(),
                habilitado,
                reservasActivas,
                mensaje
        );
    }

    /**
     * TSK-018: Activar o desactivar condicionalmente el módulo completo de reservas
     * según la configuración SaaS de atención física del restaurante (Admin/Dueño).
     */
    @Transactional
    public ModuloReservasStatusDTO cambiarEstadoModulo(Boolean habilitado) {
        Long restauranteId = currentUser.getRestauranteId();

        Restaurante restaurante = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        restaurante.setTieneAtencionFisica(habilitado);
        restauranteRepository.save(restaurante);

        List<EstadoReserva> estadosActivos = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);
        long reservasActivas = reservaRepository.countByRestauranteIdAndEstadoInAndFechaGreaterThanEqual(
                restauranteId, estadosActivos, LocalDate.now(clock));

        String mensaje;
        if (Boolean.TRUE.equals(habilitado)) {
            mensaje = "Módulo de reservas activado exitosamente. Ahora se permite la gestión de mesas y creación de reservas.";
        } else {
            mensaje = "Módulo de reservas desactivado. Se bloqueará la creación y modificación de reservas.";
            if (reservasActivas > 0) {
                mensaje += " Atención: Existen " + reservasActivas + " reservas vigentes pendientes de gestionar.";
            }
        }

        return new ModuloReservasStatusDTO(
                restaurante.getId(),
                restaurante.getNombre(),
                habilitado,
                reservasActivas,
                mensaje
        );
    }
}
