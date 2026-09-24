package com.restopilot.backend.modules.pedidos.mapper;

import com.restopilot.backend.modules.pedidos.dto.PedidoResponseDTO;
import com.restopilot.backend.modules.pedidos.entity.Pedido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// Le dice a Spring que este mapper será un componente inyectable (@Autowired)
@Mapper(componentModel = "spring")
public interface PedidoMapper {

    // Como la entidad tiene objetos completos (Restaurante y Cliente),
    // le enseñamos a extraer solo los datos específicos que pide el DTO.
    @Mapping(target = "restauranteId", source = "restaurante.id")
    @Mapping(target = "restauranteNombre", source = "restaurante.nombre")
    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "clienteNombre", source = "cliente.nombre")
    PedidoResponseDTO toResponse(Pedido pedido);
}