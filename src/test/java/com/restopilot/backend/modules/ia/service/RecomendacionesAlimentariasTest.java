package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import com.restopilot.backend.modules.restaurante.dto.RestauranteResponseDTO;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.restopilot.backend.modules.ia.service.PreferenciaAlimentaria.*;
import static com.restopilot.backend.modules.ia.service.RestriccionAlimentaria.*;
import static com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.Estado.*;

class RecomendacionesAlimentariasTest {
    final ConsultaRestauranteService locales = mock(ConsultaRestauranteService.class);
    final PlatoService platos = mock(PlatoService.class);
    final RecomendacionesAlimentarias servicio = new RecomendacionesAlimentarias(locales, platos);

    void local(boolean activo) {
        when(locales.consultar(7L)).thenReturn(new RestauranteResponseDTO(7L, "Local", "", activo, true, true, true, null, null));
    }

    PlatoResponseDTO plato(long id, long local, String nombre, Boolean disponible) {
        var p = new PlatoResponseDTO();
        p.setId(id); p.setRestauranteId(local); p.setNombre(nombre); p.setDisponible(disponible);
        p.setIngredientes("quinua, tomate, limón"); p.setAlergenos("ninguno"); p.setPrecio(new BigDecimal("18.50"));
        return p;
    }

    @Test void exigeSeleccionYRestriccionesSoportadasAntesDeConsultar() {
        assertEquals(CLARIFICATION_REQUIRED, servicio.recomendar(null, VEGANA, List.of()).estado());
        assertEquals(CLARIFICATION_REQUIRED, servicio.recomendar(7L, VEGANA, List.of(OTRA, MANI)).estado());
        assertEquals(CLARIFICATION_REQUIRED, servicio.recomendar(7L, SIN_PREFERENCIA, List.of()).estado());
        assertEquals(CLARIFICATION_REQUIRED, servicio.recomendar(7L, null, List.of()).estado());
        assertEquals(CLARIFICATION_REQUIRED, servicio.recomendar(7L, VEGANA, null).estado());
        verifyNoInteractions(locales, platos);
    }

    @Test void noConsultaCartaDeRestauranteInactivoOInexistente() {
        local(false);
        assertEquals(NO_DATA, servicio.recomendar(7L, VEGANA, List.of()).estado());
        when(locales.consultar(7L)).thenThrow(new ResourceNotFoundException("No existe"));
        assertEquals(NO_DATA, servicio.recomendar(7L, VEGANA, List.of()).estado());
        verifyNoInteractions(platos);
    }

    @Test void filtraLocalDisponibilidadYOrigenSinInventarPrecio() {
        local(true);
        var animal = plato(5, 7, "Pollo", true); animal.setIngredientes("pollo, arroz");
        when(platos.listar(7L, null)).thenReturn(List.of(plato(1, 7, "Bowl", true),
                plato(2, 9, "Ajeno", true), plato(3, 7, "Agotado", false), plato(4, 7, "Desconocido", null), animal));
        var r = servicio.recomendar(7L, VEGANA, List.of(MANI));
        assertEquals(ANSWER, r.estado());
        assertTrue(r.mensaje().contains("Bowl - S/ 18.50"));
        assertTrue(r.mensaje().contains("quinua, tomate, limón"));
        for (String nombre : List.of("Ajeno", "Agotado", "Desconocido", "Pollo")) assertFalse(r.mensaje().contains(nombre));
        assertTrue(r.mensaje().contains("no certifica ausencia de trazas"));
        verify(platos).listar(7L, null);
    }

    @Test void limitaResultadosDespuesDeEvaluarTodaLaCarta() {
        local(true);
        var carta = new ArrayList<PlatoResponseDTO>();
        for (int i = 0; i < 10; i++) carta.add(plato(i, 7, "Agotado " + i, false));
        for (int i = 0; i < 9; i++) carta.add(plato(i + 20, 7, "Bowl " + i, true));
        when(platos.listar(7L, null)).thenReturn(carta);
        var r = servicio.recomendar(7L, VEGANA, List.of());
        assertTrue(r.mensaje().contains("Bowl 0")); assertTrue(r.mensaje().contains("Bowl 2"));
        assertFalse(r.mensaje().contains("Bowl 3")); assertTrue(r.mensaje().contains("tres opciones"));
    }

    @Test void devuelveSinDatosSiNoHayOpcionesEvaluables() {
        local(true);
        var p = plato(1, 7, "Bowl", true); p.setIngredientes(null);
        when(platos.listar(7L, null)).thenReturn(List.of(p));
        assertEquals(NO_DATA, servicio.recomendar(7L, VEGANA, List.of()).estado());
        when(platos.listar(7L, null)).thenReturn(List.of());
        assertEquals(NO_DATA, servicio.recomendar(7L, VEGANA, List.of()).estado());
    }
}
