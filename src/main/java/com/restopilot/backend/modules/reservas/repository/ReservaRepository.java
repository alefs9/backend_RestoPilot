package com.restopilot.backend.modules.reservas.repository;

import com.restopilot.backend.modules.reservas.entity.EstadoReserva;
import com.restopilot.backend.modules.reservas.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * Busca los IDs de las mesas que YA tienen reservas activas (PENDIENTE o CONFIRMADA)
     * que se solapan con el rango horario solicitado en una fecha específica.
     *
     * Lógica de solapamiento: dos rangos [A, B] y [C, D] se solapan si A < D AND B > C
     */
    @Query("SELECT DISTINCT r.mesa.id FROM Reserva r " +
           "WHERE r.restaurante.id = :restauranteId " +
           "AND r.fecha = :fecha " +
           "AND r.estado IN :estadosBloqueantes " +
           "AND r.horaInicio < :horaFin " +
           "AND r.horaFin > :horaInicio")
    List<Long> findMesaIdsConReservasSolapadas(
            @Param("restauranteId") Long restauranteId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("estadosBloqueantes") List<EstadoReserva> estadosBloqueantes);

    /**
     * Verifica si una mesa específica ya tiene una reserva activa (PENDIENTE o CONFIRMADA)
     * que se solapa con el rango horario solicitado en una fecha.
     */
    @Query("SELECT COUNT(r) > 0 FROM Reserva r " +
           "WHERE r.mesa.id = :mesaId " +
           "AND r.fecha = :fecha " +
           "AND r.estado IN :estadosBloqueantes " +
           "AND r.horaInicio < :horaFin " +
           "AND r.horaFin > :horaInicio")
    boolean existsReservaSolapada(
            @Param("mesaId") Long mesaId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("estadosBloqueantes") List<EstadoReserva> estadosBloqueantes);

    /**
     * TSK-015: Verifica si una mesa ya tiene una reserva activa (PENDIENTE o CONFIRMADA)
     * que se solapa con el rango horario solicitado, EXCLUYENDO una reserva específica.
     */
    @Query("SELECT COUNT(r) > 0 FROM Reserva r " +
           "WHERE r.mesa.id = :mesaId " +
           "AND r.id != :reservaId " +
           "AND r.fecha = :fecha " +
           "AND r.estado IN :estadosBloqueantes " +
           "AND r.horaInicio < :horaFin " +
           "AND r.horaFin > :horaInicio")
    boolean existsReservaSolapadaExcluyendoId(
            @Param("mesaId") Long mesaId,
            @Param("reservaId") Long reservaId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("estadosBloqueantes") List<EstadoReserva> estadosBloqueantes);

    // TSK-017: Historial de reservas de un usuario específico ordenado cronológicamente (más recientes primero)
    List<Reserva> findByUsuarioIdOrderByFechaDescHoraInicioDesc(Long usuarioId);

    // TSK-017: Todas las reservas del restaurante ordenadas cronológicamente
    List<Reserva> findByRestauranteIdOrderByFechaDescHoraInicioDesc(Long restauranteId);

    // TSK-017: Reservas del restaurante filtradas por fecha
    List<Reserva> findByRestauranteIdAndFechaOrderByHoraInicioAsc(Long restauranteId, LocalDate fecha);

    // TSK-017: Reservas del restaurante filtradas por estado
    List<Reserva> findByRestauranteIdAndEstadoOrderByFechaDescHoraInicioDesc(Long restauranteId, EstadoReserva estado);

    // TSK-017: Reservas del restaurante filtradas por fecha y estado
    List<Reserva> findByRestauranteIdAndFechaAndEstadoOrderByHoraInicioAsc(Long restauranteId, LocalDate fecha, EstadoReserva estado);

    // TSK-018: Contar reservas activas vigentes (futuras o de hoy) para advertencias al desactivar el módulo
    long countByRestauranteIdAndEstadoInAndFechaGreaterThanEqual(
            Long restauranteId, List<EstadoReserva> estados, LocalDate fecha);
}
