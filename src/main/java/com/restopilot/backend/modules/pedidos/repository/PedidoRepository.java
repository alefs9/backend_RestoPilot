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

    // Para que el cliente vea su historial de pedidos
    Page<Pedido> findByClienteId(Long clienteId, Pageable pageable);

    // Asegura que el restaurante solo vea SUS propios pedidos pendientes
    // Más adelante, la priorización por complejidad y tiempo de preparación (5 a 15 min)
    // se aplicará sobre estos resultados en la capa Service.
    @Query("SELECT p FROM Pedido p WHERE p.restaurante.id = :restauranteId AND p.estado IN :estados ORDER BY p.fechaCreacion ASC")
    Page<Pedido> findPendientesParaCocina(
            @Param("restauranteId") Long restauranteId,
            @Param("estados") List<EstadoPedido> estados,
            Pageable pageable
    );
}