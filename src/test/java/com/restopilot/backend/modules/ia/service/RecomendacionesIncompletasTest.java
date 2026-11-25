package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import com.restopilot.backend.modules.restaurante.dto.RestauranteResponseDTO;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.restopilot.backend.modules.ia.service.PreferenciaAlimentaria.VEGANA;
import static com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.Estado.*;

class RecomendacionesIncompletasTest {
    final ConsultaRestauranteService locales = mock(ConsultaRestauranteService.class);
    final PlatoService platos = mock(PlatoService.class);
    final RecomendacionesAlimentarias servicio = new RecomendacionesAlimentarias(locales, platos);
    @BeforeEach void preparar() {
        when(locales.consultar(7L)).thenReturn(new RestauranteResponseDTO(7L, "Local", "", true, true, true, true, null, null));
    }
    PlatoResponseDTO plato(long id, String nombre, String ingredientes, String alergenos) {
        var p = new PlatoResponseDTO(); p.setId(id); p.setRestauranteId(7L); p.setNombre(nombre);
        p.setDisponible(true); p.setIngredientes(ingredientes); p.setAlergenos(alergenos); return p;
    }
    @Test void distingueCartaVaciaDeIncompatiblesYDeDatosFaltantes() {
        when(platos.listar(7L, null)).thenReturn(List.of());
        assertTrue(servicio.recomendar(7L, VEGANA, List.of()).mensaje().contains("No hay platos disponibles"));
        when(platos.listar(7L, null)).thenReturn(List.of(plato(1, "Pollo", "pollo", "ninguno")));
        var incompatible = servicio.recomendar(7L, VEGANA, List.of());
        assertEquals(NO_DATA, incompatible.estado()); assertTrue(incompatible.mensaje().contains("incompatibilidades"));
        when(platos.listar(7L, null)).thenReturn(List.of(plato(2, "Bowl", "arroz", null)));
        var incompleto = servicio.recomendar(7L, VEGANA, List.of());
        assertEquals(NO_DATA, incompleto.estado()); assertTrue(incompleto.mensaje().contains("faltan los alérgenos"));
    }
    @Test void mantieneOpcionesEvaluablesYSeparaLasDesconocidas() {
        when(platos.listar(7L, null)).thenReturn(List.of(plato(1, "A completo", "arroz", "ninguno"),
                plato(2, "B incompleto", null, null)));
        var r = servicio.recomendar(7L, VEGANA, List.of());
        assertEquals(ANSWER, r.estado());
        assertTrue(r.mensaje().indexOf("A completo") < r.mensaje().indexOf("Otros platos no recomendados"));
        assertTrue(r.mensaje().indexOf("B incompleto") > r.mensaje().indexOf("Otros platos no recomendados"));
    }
    @Test void limitaDetalleSinOcultarQueHayMasPlatos() {
        var lista = new ArrayList<PlatoResponseDTO>();
        for (int i = 0; i < 10; i++) lista.add(plato(i, "Bowl " + i, null, null));
        when(platos.listar(7L, null)).thenReturn(lista);
        var r = servicio.recomendar(7L, VEGANA, List.of());
        assertTrue(r.mensaje().contains("Bowl 7")); assertFalse(r.mensaje().contains("Bowl 8"));
        assertTrue(r.mensaje().contains("Hay más platos"));
    }
}
