package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.restopilot.backend.modules.ia.service.CatalogoIngredientes.normalizar;

/** Evaluación de datos registrados, no certificación de ausencia de trazas o contaminación cruzada. */
final class EvaluadorAlimentario {
    enum Resultado { COMPATIBLE_SEGUN_REGISTRO, INCOMPATIBLE, NO_EVALUABLE }

    Resultado evaluar(PlatoResponseDTO plato, PreferenciaAlimentaria preferencia,
                      Set<RestriccionAlimentaria> restricciones) {
        if (preferencia == null || restricciones == null || restricciones.contains(RestriccionAlimentaria.OTRA)) {
            return Resultado.NO_EVALUABLE;
        }
        List<String> ingredientes = separar(plato.getIngredientes());
        if (ingredientes.isEmpty()) return Resultado.NO_EVALUABLE;

        Set<RestriccionAlimentaria> presentes = new HashSet<>();
        boolean desconocido = false;
        boolean incompatible = false;
        for (String ingrediente : ingredientes) {
            var perfil = CatalogoIngredientes.buscar(ingrediente);
            if (perfil == null) { desconocido = true; continue; }
            presentes.addAll(perfil.restricciones());
            incompatible |= preferencia == PreferenciaAlimentaria.VEGANA && perfil.animal()
                    || preferencia == PreferenciaAlimentaria.VEGETARIANA && perfil.carne();
        }
        // No interpretar campo vacío como ausencia de alérgenos.
        List<String> alergenos = separar(plato.getAlergenos());
        if (alergenos.isEmpty()) desconocido = true;
        for (String alergeno : alergenos) {
            if (Set.of("ninguno", "ninguna", "ningunos", "sin alergenos", "sin alergenos declarados").contains(alergeno)) continue;
            var restriccion = alergeno(alergeno);
            if (restriccion == null) desconocido = true;
            else presentes.add(restriccion);
        }
        incompatible |= presentes.stream().anyMatch(restricciones::contains);
        // Las declaraciones de alérgenos también son evidencia de origen animal.
        incompatible |= preferencia != PreferenciaAlimentaria.SIN_PREFERENCIA
                && (presentes.contains(RestriccionAlimentaria.PESCADO) || presentes.contains(RestriccionAlimentaria.MARISCOS));
        incompatible |= preferencia == PreferenciaAlimentaria.VEGANA
                && (presentes.contains(RestriccionAlimentaria.LACTEOS) || presentes.contains(RestriccionAlimentaria.HUEVO));
        if (incompatible) return Resultado.INCOMPATIBLE;
        return desconocido ? Resultado.NO_EVALUABLE : Resultado.COMPATIBLE_SEGUN_REGISTRO;
    }

    private List<String> separar(String texto) {
        String normalizado = normalizar(texto == null ? null : texto.replace('\n', ','));
        if (normalizado.isEmpty()) return List.of();
        return Arrays.stream(normalizado.split("[,;\\n]|\\s+y\\s+"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private RestriccionAlimentaria alergeno(String nombre) {
        return switch (nombre) {
            case "gluten", "trigo", "cebada", "centeno" -> RestriccionAlimentaria.GLUTEN;
            case "lacteos", "lactosa", "leche" -> RestriccionAlimentaria.LACTEOS;
            case "huevo", "huevos" -> RestriccionAlimentaria.HUEVO;
            case "mani", "cacahuate", "cacahuete" -> RestriccionAlimentaria.MANI;
            case "frutos secos", "nueces", "almendras", "avellanas", "pistachos" -> RestriccionAlimentaria.FRUTOS_SECOS;
            case "soja", "soya" -> RestriccionAlimentaria.SOJA;
            case "pescado", "pescados" -> RestriccionAlimentaria.PESCADO;
            case "mariscos", "crustaceos", "moluscos" -> RestriccionAlimentaria.MARISCOS;
            case "sesamo", "ajonjoli" -> RestriccionAlimentaria.SESAMO;
            default -> null;
        };
    }
}
