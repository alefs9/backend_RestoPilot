package com.restopilot.backend.modules.reservas.mapper;

import com.restopilot.backend.modules.reservas.dto.ReservaResponseDTO;
import com.restopilot.backend.modules.reservas.entity.Reserva;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class ReservaMapper {
    @org.springframework.beans.factory.annotation.Autowired
    private java.time.Clock clock;

    public ReservaResponseDTO toResponse(Reserva reserva) {
        java.time.LocalDateTime ahora=java.time.LocalDateTime.now(clock);
        return toBaseResponse(reserva).conControles(ahora,
            com.restopilot.backend.modules.reservas.service.ReglasCicloReserva.puedeRegistrarLlegada(reserva,ahora),
            com.restopilot.backend.modules.reservas.service.ReglasCicloReserva.puedeCompletar(reserva,ahora));
    }


    @Mapping(target = "mesaId", source = "mesa.id")
    @Mapping(target = "mesaNumero", source = "mesa.numero")
    @Mapping(target = "restauranteId", source = "restaurante.id")
    @Mapping(target = "restauranteNombre", source = "restaurante.nombre")
    @Mapping(target = "clienteId", source = "usuario.id")
    @Mapping(target = "clienteNombre", source = "usuario.nombreCompleto")
    @Mapping(target = "mensaje", ignore = true)
    @Mapping(target = "fechaHoraServidor", ignore = true)
    @Mapping(target = "puedeRegistrarLlegada", ignore = true)
    @Mapping(target = "puedeCompletar", ignore = true)
    protected abstract ReservaResponseDTO toBaseResponse(Reserva reserva);
}