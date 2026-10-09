package com.restopilot.backend.modules.pedidos.mapper;

import com.restopilot.backend.modules.pedidos.dto.PedidoResponseDTO;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import com.restopilot.backend.modules.pedidos.entity.DetallePedido;
import com.restopilot.backend.modules.pedidos.dto.DetallePedidoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PedidoMapper {

    @Mapping(target = "restauranteId", source = "restaurante.id")
    @Mapping(target = "restauranteNombre", source = "restaurante.nombre")
    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "clienteNombre", source = "cliente.nombreCompleto")
    @Mapping(target = "mesaId", source = "mesa.id")
    PedidoResponseDTO toResponse(Pedido pedido);

    @Mapping(target = "platoId", source = "plato.id")
    @Mapping(target = "precioUnitario", expression = "java(precioHistorico(detalle))")
    DetallePedidoResponseDTO toDetalle(DetallePedido detalle);

    default java.math.BigDecimal precioHistorico(DetallePedido detalle) {
        if (detalle.getPrecioUnitario() != null) return detalle.getPrecioUnitario();
        // Compatibilidad con pedidos anteriores a la incorporación del precio snapshot.
        return detalle.getSubtotal().divide(java.math.BigDecimal.valueOf(detalle.getCantidad()), 2,
                java.math.RoundingMode.HALF_UP);
    }
}
