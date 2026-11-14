package com.restopilot.backend.modules.pedidos.repository;

import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    java.util.Optional<Pedido> findByIdForUpdate(@Param("id") Long id);

    // US07: Permite al cliente consultar su historial de pedidos
    Page<Pedido> findByClienteId(Long clienteId, Pageable pageable);

    @Query("select distinct p from Pedido p join fetch p.restaurante join fetch p.cliente " +
            "left join fetch p.mesa left join fetch p.detalles d left join fetch d.plato where p.id in :ids")
    List<Pedido> findDetailedByIdIn(@Param("ids") List<Long> ids);

    @Query(value = "select p.id from Pedido p left join p.detalles d left join d.plato pl " +
            "where p.restaurante.id = :restauranteId and p.estado in :estados group by p.id " +
            "order by max(case when upper(trim(pl.complejidad)) = 'ALTA' then 3 " +
            "when upper(trim(pl.complejidad)) = 'MEDIA' then 2 " +
            "when upper(trim(pl.complejidad)) = 'BAJA' then 1 else 0 end) desc, " +
            "max(case when pl.tiempoPreparacionMinutos >= 0 then pl.tiempoPreparacionMinutos else 0 end) desc, " +
            "p.fechaCreacion asc, p.id asc",
            countQuery = "select count(p) from Pedido p where p.restaurante.id = :restauranteId and p.estado in :estados")
    Page<Long> findPendingIdsForKitchen(@Param("restauranteId") Long restauranteId,
            @Param("estados") List<EstadoPedido> estados, Pageable pageable);

    // US15: Consulta vital para el SaaS. Aísla los pedidos pendientes por restaurante
    // y filtra por los estados de la cocina (ej. REGISTRADO, EN_PREPARACION).
}
