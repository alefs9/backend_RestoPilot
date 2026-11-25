package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import com.restopilot.backend.modules.restaurante.dto.RestauranteResponseDTO;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.restopilot.backend.modules.ia.service.PreferenciaAlimentaria.*;
import static com.restopilot.backend.modules.ia.service.RestriccionAlimentaria.*;
import static com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.Estado.*;

class ConsultaCompatibilidadAlimentariaTest {
    final ConsultaRestauranteService locales = mock(ConsultaRestauranteService.class);
    final PlatoService platos = mock(PlatoService.class);
    final ConsultaCompatibilidadAlimentaria servicio = new ConsultaCompatibilidadAlimentaria(locales, platos);
    @BeforeEach void preparar() {
        when(locales.consultar(7L)).thenReturn(new RestauranteResponseDTO(7L, "Local", "", true, true, true, true, null, null));
    }
    PlatoResponseDTO plato(long id, String nombre, String ingredientes, String alergenos) {
        var p = new PlatoResponseDTO(); p.setId(id); p.setRestauranteId(7L); p.setNombre(nombre);
        p.setIngredientes(ingredientes); p.setAlergenos(alergenos); p.setDisponible(true); return p;
    }
    @Test void explicaAmbosDatosFaltantesSinAfirmarCompatibilidad() {
        when(platos.listar(7L, null)).thenReturn(List.of(plato(1, "Bowl", null, null)));
        var r = servicio.consultar(7L, "bowl", SIN_PREFERENCIA, List.of(LACTEOS));
        assertEquals(NO_DATA, r.estado());
        assertTrue(r.mensaje().contains("faltan los ingredientes"));
        assertTrue(r.mensaje().contains("faltan los alérgenos"));
        assertTrue(r.mensaje().contains("Consulta al personal"));
        assertFalse(r.mensaje().contains("es compatible"));
    }
    @Test void requiereSeleccionPlatoYRestriccionSoportada() {
        assertEquals(CLARIFICATION_REQUIRED, servicio.consultar(null, "Bowl", VEGANA, List.of()).estado());
        assertEquals(CLARIFICATION_REQUIRED, servicio.consultar(7L, "", VEGANA, List.of()).estado());
        assertEquals(CLARIFICATION_REQUIRED, servicio.consultar(7L, "Bowl", VEGANA, List.of(OTRA)).estado());
        verifyNoInteractions(platos);
    }
    @Test void restringeLaBusquedaAlLocalSeleccionado() {
        var ajeno = plato(1, "Bowl", "arroz", "ninguno"); ajeno.setRestauranteId(9L);
        when(platos.listar(7L, null)).thenReturn(List.of(ajeno));
        assertEquals(NO_DATA, servicio.consultar(7L, "#1", VEGANA, List.of()).estado());
    }
    @Test void preguntaAnteNombresAmbiguosYPermiteIdentificarPorNumero() {
        when(platos.listar(7L, null)).thenReturn(List.of(plato(1, "Bowl verde", "arroz", "ninguno"),
                plato(2, "Bowl verde", null, null)));
        var ambiguo = servicio.consultar(7L, "bowl verde", VEGANA, List.of());
        assertEquals(CLARIFICATION_REQUIRED, ambiguo.estado());
        assertTrue(ambiguo.mensaje().contains("#2"));
        assertEquals(NO_DATA, servicio.consultar(7L, "#2", VEGANA, List.of()).estado());
        assertEquals(ANSWER, servicio.consultar(7L, "#1", VEGANA, List.of()).estado());
    }
    @Test void prefiereNombreExactoYNormalizaTildes() {
        when(platos.listar(7L, null)).thenReturn(List.of(plato(1, "Quinúa", "quinua", "ninguno"),
                plato(2, "Quinúa especial", null, null)));
        assertEquals(ANSWER, servicio.consultar(7L, " QUINUA ", VEGANA, List.of()).estado());
    }
    @Test void informaIncompatibilidadInclusoConIngredientesAusentes() {
        when(platos.listar(7L, null)).thenReturn(List.of(plato(1, "Bowl", null, "maní")));
        var r = servicio.consultar(7L, "Bowl", SIN_PREFERENCIA, List.of(MANI));
        assertEquals(ANSWER, r.estado()); assertTrue(r.mensaje().contains("incompatibilidad"));
        assertFalse(r.mensaje().contains("es compatible"));
    }
    @Test void noRecomiendaAgotadosNiDisponibilidadDesconocida() {
        var p = plato(1, "Bowl", "arroz", "ninguno");
        when(platos.listar(7L, null)).thenReturn(List.of(p));
        for (Boolean disponible : new Boolean[]{false, null}) {
            p.setDisponible(disponible);
            assertEquals(NO_DATA, servicio.consultar(7L, "Bowl", VEGANA, List.of()).estado());
        }
    }
    @Test void noConsultaCartaDeRestauranteInactivoOInexistente() {
        when(locales.consultar(7L)).thenReturn(new RestauranteResponseDTO(7L, "Local", "", false, false, true, true, null, null));
        assertEquals(NO_DATA, servicio.consultar(7L, "Bowl", VEGANA, List.of()).estado());
        when(locales.consultar(7L)).thenThrow(new com.restopilot.backend.core.exception.ResourceNotFoundException("No existe"));
        assertEquals(NO_DATA, servicio.consultar(7L, "Bowl", VEGANA, List.of()).estado());
        verifyNoInteractions(platos);
    }
}
