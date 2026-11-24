package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.Estado;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class RecomendacionesAlimentarias {
    private static final int LIMITE = 8;
    private final EvaluadorAlimentario evaluador = new EvaluadorAlimentario();
    private final ConsultaRestauranteService restaurantes;
    private final PlatoService platos;

    RecomendacionesAlimentarias(ConsultaRestauranteService restaurantes, PlatoService platos) {
        this.restaurantes = restaurantes;
        this.platos = platos;
    }

    ChatIAResponseDTO recomendar(Long restauranteId, PreferenciaAlimentaria preferencia,
                                 List<RestriccionAlimentaria> restricciones) {
        if (restauranteId == null || restauranteId <= 0) {
            return new ChatIAResponseDTO(Estado.CLARIFICATION_REQUIRED,
                    "Selecciona un restaurante para consultar recomendaciones de su carta.");
        }
        if (preferencia == null || restricciones == null || restricciones.stream().anyMatch(Objects::isNull)
                || restricciones.contains(RestriccionAlimentaria.OTRA)
                || preferencia == PreferenciaAlimentaria.SIN_PREFERENCIA && restricciones.isEmpty()) {
            return new ChatIAResponseDTO(Estado.CLARIFICATION_REQUIRED,
                    "Indica si buscas opciones vegetarianas, veganas o sin gluten, lácteos, huevo, maní, "
                    + "frutos secos, soja, pescado, mariscos o sésamo. Para otras restricciones, consulta al restaurante.");
        }
        try {
            if (!restaurantes.consultar(restauranteId).activo()) return noDisponible();
        } catch (ResourceNotFoundException ex) {
            return noDisponible();
        }
        var exclusiones = new HashSet<>(restricciones);
        var compatibles = platos.listar(restauranteId, null).stream()
                .filter(plato -> Objects.equals(restauranteId, plato.getRestauranteId()))
                .filter(plato -> Boolean.TRUE.equals(plato.getDisponible()))
                .filter(plato -> plato.getNombre() != null && !plato.getNombre().isBlank())
                .filter(plato -> evaluador.evaluar(plato, preferencia, exclusiones)
                        == EvaluadorAlimentario.Resultado.COMPATIBLE_SEGUN_REGISTRO)
                .sorted(Comparator.comparing(PlatoResponseDTO::getNombre, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PlatoResponseDTO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(LIMITE + 1L).toList();
        if (compatibles.isEmpty()) {
            return new ChatIAResponseDTO(Estado.NO_DATA,
                    "No encontré opciones disponibles que pueda recomendar para esa preferencia y restricciones "
                    + "con la información registrada. Consulta al personal del restaurante.");
        }
        String detalles = compatibles.stream().limit(LIMITE).map(this::describir).collect(Collectors.joining("\n"));
        String mas = compatibles.size() > LIMITE ? "\nSe muestran las primeras 8 opciones por nombre." : "";
        return new ChatIAResponseDTO(Estado.ANSWER,
                "Opciones según los ingredientes y alérgenos registrados: " + switch (preferencia) {
                    case VEGETARIANA -> "preferencia vegetariana";
                    case VEGANA -> "preferencia vegana";
                    case SIN_PREFERENCIA -> "restricciones indicadas";
                }
                + (exclusiones.isEmpty() ? "" : ", excluyendo " + exclusiones.stream().sorted()
                        .map(r -> r.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(", ")))
                + ":\n" + detalles + mas
                + "\nLa carta no certifica ausencia de trazas ni contaminación cruzada. Confirma la preparación con el restaurante.");
    }

    private ChatIAResponseDTO noDisponible() {
        return new ChatIAResponseDTO(Estado.NO_DATA, "El restaurante seleccionado no está disponible.");
    }

    private String describir(PlatoResponseDTO plato) {
        String precio = plato.getPrecio() == null ? "precio sin registrar" : "S/ " + plato.getPrecio().toPlainString();
        return "%s (%s, disponible). Ingredientes: %s. Alérgenos: %s.".formatted(
                plato.getNombre(), precio, plato.getIngredientes().strip(), plato.getAlergenos().strip());
    }
}
