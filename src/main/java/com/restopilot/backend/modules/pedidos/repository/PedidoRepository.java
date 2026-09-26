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

    // US07: Permite al cliente consultar su historial de pedidos
    Page<Pedido> findByClienteId(Long clienteId, Pageable pageable);

    // US15: Consulta vital para el SaaS. Aísla los pedidos pendientes por restaurante
    // y filtra por los estados de la cocina (ej. REGISTRADO, EN_PREPARACION).
    @Query("SELECT p FROM Pedido p WHERE p.restaurante.id = :restauranteId AND p.estado IN :estados ORDER BY p.fechaCreacion ASC")
    List<Pedido> findPendientesParaCocina(
            @Param("restauranteId") Long restauranteId,
            @Param("estados") List<EstadoPedido> estados
    );
}