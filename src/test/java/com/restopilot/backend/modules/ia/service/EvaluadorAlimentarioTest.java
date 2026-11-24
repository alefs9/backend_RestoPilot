package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.restopilot.backend.modules.ia.service.EvaluadorAlimentario.Resultado.*;
import static com.restopilot.backend.modules.ia.service.PreferenciaAlimentaria.*;
import static com.restopilot.backend.modules.ia.service.RestriccionAlimentaria.*;

class EvaluadorAlimentarioTest {
    private final EvaluadorAlimentario evaluador = new EvaluadorAlimentario();

    private PlatoResponseDTO plato(String ingredientes, String alergenos) {
        var plato = new PlatoResponseDTO();
        plato.setIngredientes(ingredientes);
        plato.setAlergenos(alergenos);
        return plato;
    }

    @Test void admiteVegetalesConTildesSeparadoresYMayusculas() {
        assertEquals(COMPATIBLE_SEGUN_REGISTRO, evaluador.evaluar(
                plato("  QUINUA; tomate\nlimón y aceite de oliva ", "Ninguno"), VEGANA, Set.of()));
    }

    @Test void distingueVegetarianoDeVegano() {
        var plato = plato("arroz, huevo, queso", "Huevo y leche");
        assertEquals(COMPATIBLE_SEGUN_REGISTRO, evaluador.evaluar(plato, VEGETARIANA, Set.of()));
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato, VEGANA, Set.of()));
    }

    @ParameterizedTest @ValueSource(strings = {"pollo", "res", "pescado", "langostinos", "gelatina", "caldo de pollo"})
    void vegetarianoExcluyeCarneYDerivados(String ingrediente) {
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato(ingrediente, "ninguno"), VEGETARIANA, Set.of()));
    }

    @Test void aplicaVariasRestriccionesAunqueElCampoAlergenosDigaNinguno() {
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato("arroz, sillao, maní", "ninguno"),
                VEGANA, Set.of(GLUTEN, MANI)));
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato("arroz, huevo", "ninguno"),
                SIN_PREFERENCIA, Set.of(HUEVO)));
    }

    @Test void alergenosDeclaradosPrevalecenSobreListaVegetal() {
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato("arroz, tomate", "leche"), VEGANA, Set.of()));
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato("arroz, tomate", "maní"), VEGANA, Set.of(MANI)));
    }

    @Test void reconoceLechesVegetalesSinCoincidenciasParciales() {
        assertEquals(COMPATIBLE_SEGUN_REGISTRO, evaluador.evaluar(plato("leche de coco, arroz", "ninguno"), VEGANA, Set.of(LACTEOS)));
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato("leche de almendras, arroz", "frutos secos"), VEGANA, Set.of(FRUTOS_SECOS)));
    }

    @ParameterizedTest @ValueSource(strings = {"salsa especial", "arroz con pollo", "sin información", "", "arroz, ingrediente secreto", "arroz (con caldo)"})
    void noDeduceCompatibilidadDeTextoDesconocido(String ingredientes) {
        assertEquals(NO_EVALUABLE, evaluador.evaluar(plato(ingredientes, "ninguno"), VEGANA, Set.of()));
    }

    @Test void noConfundeDatosAusentesOTrazasConAusenciaDeAlergenos() {
        for (String dato : new String[] {null, "", "no registrado", "puede contener maní"}) {
            assertEquals(NO_EVALUABLE, evaluador.evaluar(plato("arroz", dato), VEGANA, Set.of(MANI)));
        }
        assertEquals(NO_EVALUABLE, evaluador.evaluar(plato(null, "ninguno"), VEGANA, Set.of()));
    }
}
