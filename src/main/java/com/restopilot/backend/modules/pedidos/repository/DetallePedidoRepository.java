package com.restopilot.backend.modules.pedidos.repository;

import com.restopilot.backend.modules.pedidos.entity.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {
}