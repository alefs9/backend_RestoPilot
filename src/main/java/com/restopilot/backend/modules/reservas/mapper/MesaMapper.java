package com.restopilot.backend.modules.reservas.mapper;

import com.restopilot.backend.modules.reservas.dto.MesaResponseDTO;
import com.restopilot.backend.modules.reservas.entity.Mesa;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MesaMapper {

    @Mapping(target = "restauranteId", source = "restaurante.id")
    @Mapping(target = "restauranteNombre", source = "restaurante.nombre")
    MesaResponseDTO toResponse(Mesa mesa);
}
