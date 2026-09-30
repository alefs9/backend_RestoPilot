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

    // Buscar reservas de un restaurante por fecha (para listados del admin)
    List<Reserva> findByRestauranteIdAndFecha(Long restauranteId, LocalDate fecha);

    // Buscar reservas de un usuario especifico (historial del cliente)
    List<Reserva> findByUsuarioIdOrderByFechaDescHoraInicioDesc(Long usuarioId);

    // Buscar reservas de un restaurante por estado
    List<Reserva> findByRestauranteIdAndEstado(Long restauranteId, EstadoReserva estado);
}
