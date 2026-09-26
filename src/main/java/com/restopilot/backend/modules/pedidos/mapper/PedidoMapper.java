package com.restopilot.backend.modules.pedidos.mapper;

import com.restopilot.backend.modules.pedidos.dto.PedidoResponseDTO;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PedidoMapper {

    @Mapping(target = "restauranteId", source = "restaurante.id")
    @Mapping(target = "restauranteNombre", source = "restaurante.nombre")
    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "clienteNombre", source = "cliente.nombre")
    @Mapping(target = "mesaId", source = "mesa.id")
    PedidoResponseDTO toResponse(Pedido pedido);
}