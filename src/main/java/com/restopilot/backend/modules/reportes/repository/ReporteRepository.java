package com.restopilot.backend.modules.reportes.repository;

import com.restopilot.backend.modules.pedidos.entity.Pedido;
import com.restopilot.backend.modules.pagos.entity.Pago;
import com.restopilot.backend.modules.reservas.entity.Reserva;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ReporteRepository extends Repository<Pedido, Long> {
    @Query("select count(p) from Pedido p where p.restaurante.id = :restauranteId and p.fechaCreacion >= :inicio and p.fechaCreacion < :fin")
    long contarPedidos(Long restauranteId, LocalDateTime inicio, LocalDateTime fin);

    @Query("select count(r) from Reserva r where r.restaurante.id = :restauranteId and r.fecha between :inicio and :fin")
    long contarReservas(Long restauranteId, LocalDate inicio, LocalDate fin);

    @Query("select d.nombrePlatoSnapshot, sum(d.cantidad) from DetallePedido d where d.pedido.restaurante.id = :restauranteId and d.pedido.fechaCreacion >= :inicio and d.pedido.fechaCreacion < :fin and d.pedido.estado = com.restopilot.backend.modules.pedidos.entity.EstadoPedido.ENTREGADO group by d.nombrePlatoSnapshot order by sum(d.cantidad) desc, d.nombrePlatoSnapshot asc")
    List<Object[]> platosVendidos(Long restauranteId, LocalDateTime inicio, LocalDateTime fin);

    @Query("select hour(p.fechaCreacion), count(p) from Pedido p where p.restaurante.id = :restauranteId and p.fechaCreacion >= :inicio and p.fechaCreacion < :fin and p.estado <> com.restopilot.backend.modules.pedidos.entity.EstadoPedido.CANCELADO group by hour(p.fechaCreacion) order by hour(p.fechaCreacion)")
    List<Object[]> demanda(Long restauranteId, LocalDateTime inicio, LocalDateTime fin);

    @Query("select count(p) from Pedido p where p.restaurante.id = :restauranteId and p.fechaCreacion >= :inicio and p.fechaCreacion < :fin and p.estado = com.restopilot.backend.modules.pedidos.entity.EstadoPedido.ENTREGADO")
    long pedidosAtendidos(Long restauranteId, LocalDateTime inicio, LocalDateTime fin);

    @Query("select count(r) from Reserva r where r.restaurante.id = :restauranteId and r.fecha between :inicio and :fin and r.estado = com.restopilot.backend.modules.reservas.entity.EstadoReserva.COMPLETADA")
    long reservasAtendidas(Long restauranteId, LocalDate inicio, LocalDate fin);

    @Query("select r from Reserva r join fetch r.mesa where r.restaurante.id = :restauranteId and r.fecha between :inicio and :fin and r.mesa.activo = true and r.estado in (com.restopilot.backend.modules.reservas.entity.EstadoReserva.CONFIRMADA, com.restopilot.backend.modules.reservas.entity.EstadoReserva.COMPLETADA)")
    List<Reserva> ocupacion(Long restauranteId, LocalDate inicio, LocalDate fin);

    @Query("select pg from Pago pg, Pedido p where pg.pedidoId = p.id and p.restaurante.id = :restauranteId and p.fechaCreacion >= :inicio and p.fechaCreacion < :fin and p.estado <> com.restopilot.backend.modules.pedidos.entity.EstadoPedido.CANCELADO")
    List<Pago> pagos(Long restauranteId, LocalDateTime inicio, LocalDateTime fin);

    @Query("select count(p) from Pedido p where p.restaurante.id = :restauranteId and p.fechaCreacion >= :inicio and p.fechaCreacion < :fin and p.estado <> com.restopilot.backend.modules.pedidos.entity.EstadoPedido.CANCELADO")
    long pedidosNoCancelados(Long restauranteId, LocalDateTime inicio, LocalDateTime fin);
}
