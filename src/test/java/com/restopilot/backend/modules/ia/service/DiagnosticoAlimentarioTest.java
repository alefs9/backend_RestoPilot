package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static com.restopilot.backend.modules.ia.service.DiagnosticoAlimentario.Motivo.*;
import static com.restopilot.backend.modules.ia.service.EvaluadorAlimentario.Resultado.*;
import static com.restopilot.backend.modules.ia.service.PreferenciaAlimentaria.*;
import static com.restopilot.backend.modules.ia.service.RestriccionAlimentaria.*;

class DiagnosticoAlimentarioTest {
    final EvaluadorAlimentario evaluador = new EvaluadorAlimentario();
    PlatoResponseDTO plato(String ingredientes, String alergenos) {
        var p = new PlatoResponseDTO(); p.setIngredientes(ingredientes); p.setAlergenos(alergenos); return p;
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"  ", "SIN INFORMACIÓN", "no registrado", "pendiente", "n/a", "-", ", ; "})
    void identificaCamposAusentesSinConfundirlosConNinguno(String vacio) {
        var d = evaluador.diagnosticar(plato(vacio, vacio), VEGANA, Set.of());
        assertEquals(NO_EVALUABLE, d.resultado());
        assertEquals(Set.of(INGREDIENTES_AUSENTES, ALERGENOS_AUSENTES), d.motivos());
    }

    @Test void conservaAmbosMotivosYExplicaCadaCampo() {
        var d = evaluador.diagnosticar(plato(null, null), VEGANA, Set.of());
        String mensaje = RespuestaInformacionAlimentaria.explicar("Bowl", d);
        assertTrue(mensaje.contains("faltan los ingredientes registrados"));
        assertTrue(mensaje.contains("faltan los alérgenos registrados"));
        assertTrue(mensaje.startsWith("No puedo confirmar"));
        assertThrows(UnsupportedOperationException.class, () -> d.motivos().clear());
    }

    @Test void distingueComposicionDesconocidaDeDatoAusente() {
        var d = evaluador.diagnosticar(plato("salsa especial", "puede contener trazas"), VEGANA, Set.of());
        assertEquals(Set.of(INGREDIENTES_NO_INTERPRETABLES, ALERGENOS_NO_INTERPRETABLES), d.motivos());
        assertEquals(NO_EVALUABLE, d.resultado());
    }

    @Test void incompatibilidadDeclaradaPrevaleceAunqueFaltenIngredientes() {
        var d = evaluador.diagnosticar(plato(null, "maní"), SIN_PREFERENCIA, Set.of(MANI));
        assertEquals(INCOMPATIBLE, d.resultado());
        assertEquals(Set.of(INGREDIENTES_AUSENTES), d.motivos());
    }

    @Test void incompatibilidadDeIngredientePrevaleceSobreAlergenosAusentes() {
        var d = evaluador.diagnosticar(plato("leche", null), VEGANA, Set.of());
        assertEquals(INCOMPATIBLE, d.resultado());
        assertTrue(d.motivos().contains(ALERGENOS_AUSENTES));
    }

    @Test void declaracionExplicitaSinAlergenosPermiteEvaluar() {
        var d = evaluador.diagnosticar(plato("arroz, tomate", "ninguno"), VEGANA, Set.of());
        assertEquals(COMPATIBLE_SEGUN_REGISTRO, d.resultado());
        assertTrue(d.motivos().isEmpty());
    }

    @Test void noIgnoraRestriccionesDesconocidasONulas() {
        assertEquals(Set.of(SOLICITUD_INCOMPLETA), evaluador.diagnosticar(plato("arroz", "ninguno"),
                VEGANA, Set.of(OTRA)).motivos());
        assertFalse(SolicitudAlimentaria.valida(VEGANA, java.util.Arrays.asList(MANI, null)));
        assertFalse(SolicitudAlimentaria.valida(SIN_PREFERENCIA, Set.of()));
    }
}
