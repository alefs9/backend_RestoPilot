package com.restopilot.backend.modules.reservas.mapper;

import com.restopilot.backend.modules.reservas.dto.ReservaResponseDTO;
import com.restopilot.backend.modules.reservas.entity.Reserva;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReservaMapper {

    @Mapping(target = "mesaId", source = "mesa.id")
    @Mapping(target = "mesaNumero", source = "mesa.numero")
    @Mapping(target = "restauranteId", source = "restaurante.id")
    @Mapping(target = "restauranteNombre", source = "restaurante.nombre")
    @Mapping(target = "clienteId", source = "usuario.id")
    @Mapping(target = "clienteNombre", source = "usuario.nombreCompleto")
    ReservaResponseDTO toResponse(Reserva reserva);
}
