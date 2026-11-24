package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.restopilot.backend.modules.ia.service.EvaluadorAlimentario.Resultado.INCOMPATIBLE;

class RestriccionesAlimentariasTest {
    @ParameterizedTest
    @CsvSource({
        "GLUTEN,pan rallado,trigo", "LACTEOS,caseína,lactosa", "HUEVO,mayonesa,huevos",
        "MANI,cacahuete,maní", "FRUTOS_SECOS,pecanas,almendras", "SOJA,tofu,soya",
        "PESCADO,anchoas,pescados", "MARISCOS,salsa de ostras,crustáceos", "SESAMO,tahini,ajonjolí"
    })
    void excluyeCadaRestriccionPorIngredienteOPorAlergeno(RestriccionAlimentaria restriccion,
                                                        String ingrediente, String alergeno) {
        var evaluador = new EvaluadorAlimentario();
        var plato = new PlatoResponseDTO();
        plato.setIngredientes(ingrediente);
        plato.setAlergenos("ninguno");
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato, PreferenciaAlimentaria.SIN_PREFERENCIA, Set.of(restriccion)));
        plato.setIngredientes("arroz");
        plato.setAlergenos(alergeno);
        assertEquals(INCOMPATIBLE, evaluador.evaluar(plato, PreferenciaAlimentaria.SIN_PREFERENCIA, Set.of(restriccion)));
    }
}
