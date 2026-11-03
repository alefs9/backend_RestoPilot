package com.restopilot.backend;

import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultaRestauranteServiceTest {
    private final RestauranteRepository repository = mock(RestauranteRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T17:00:00Z"), ZoneId.of("America/Lima"));
    private final ConsultaRestauranteService service = new ConsultaRestauranteService(repository, clock);

    private Restaurante restaurant(long id, boolean activo) {
        return Restaurante.builder().id(id).nombre("Prueba").activo(activo).tieneAtencionFisica(true)
                .aceptaDelivery(false).horaApertura(LocalTime.of(9,0)).horaCierre(LocalTime.of(22,0)).build();
    }
    @Test void listaSoloActivosConDatosDeAtencionYOrdenEstable() {
        when(repository.findAll(Sort.by("id"))).thenReturn(List.of(restaurant(7,true),restaurant(8,false)));
        var result=service.listar();
        assertEquals(1,result.size()); assertEquals(7L,result.getFirst().id());
        assertTrue(result.getFirst().abierto()); assertTrue(result.getFirst().tieneAtencionFisica());
        assertFalse(result.getFirst().aceptaDelivery());
    }
    @Test void cierreEsExclusivoYLaAperturaEsInclusivaComoEnPedidos() {
        var r=restaurant(7,true);
        r.setHoraApertura(LocalTime.NOON);r.setHoraCierre(LocalTime.of(13,0));
        when(repository.findById(7L)).thenReturn(Optional.of(r));
        assertTrue(service.consultar(7L).abierto());
        r.setHoraApertura(LocalTime.of(9,0));r.setHoraCierre(LocalTime.NOON);
        assertFalse(service.consultar(7L).abierto());
    }
    @Test void inactivoNuncaEstaAbiertoYHorarioAusenteNoAgregaUnaRestriccion() {
        var r=restaurant(7,false);r.setHoraApertura(null);r.setHoraCierre(null);
        when(repository.findById(7L)).thenReturn(Optional.of(r));
        assertFalse(service.consultar(7L).abierto());
        r.setActivo(true);assertTrue(service.consultar(7L).abierto());
    }
    @Test void inexistenteNoDevuelveUnRestauranteDeRespaldo() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.consultar(99L));
    }
}
