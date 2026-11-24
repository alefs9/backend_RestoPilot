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
        if (!SolicitudAlimentaria.valida(preferencia, restricciones)) {
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
        var evaluados = platos.listar(restauranteId, null).stream()
                .filter(plato -> Objects.equals(restauranteId, plato.getRestauranteId()))
                .filter(plato -> Boolean.TRUE.equals(plato.getDisponible()))
                .filter(plato -> plato.getNombre() != null && !plato.getNombre().isBlank())
                .sorted(Comparator.comparing(PlatoResponseDTO::getNombre, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PlatoResponseDTO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(plato -> new PlatoEvaluado(plato, evaluador.diagnosticar(plato, preferencia, exclusiones))).toList();
        var compatibles = evaluados.stream()
                .filter(p -> p.diagnostico().resultado() == EvaluadorAlimentario.Resultado.COMPATIBLE_SEGUN_REGISTRO)
                .map(PlatoEvaluado::plato)
                .limit(LIMITE + 1L).toList();
        var incompletos = evaluados.stream()
                .filter(p -> p.diagnostico().resultado() == EvaluadorAlimentario.Resultado.NO_EVALUABLE).toList();
        String faltantes = incompletos.stream().limit(LIMITE)
                .map(p -> RespuestaInformacionAlimentaria.explicar(p.plato().getNombre(), p.diagnostico()))
                .collect(Collectors.joining("\n"));
        if (incompletos.size() > LIMITE) faltantes += "\nHay más platos sin información suficiente; consulta un plato concreto.";
        if (compatibles.isEmpty()) {
            if (!incompletos.isEmpty()) return new ChatIAResponseDTO(Estado.NO_DATA,
                    "No puedo recomendar opciones con los datos actuales.\n" + faltantes + "\n"
                    + RespuestaInformacionAlimentaria.CONFIRMAR);
            return new ChatIAResponseDTO(Estado.NO_DATA,
                    evaluados.isEmpty() ? "No hay platos disponibles para evaluar en la carta seleccionada."
                    : "Los platos disponibles evaluados presentan incompatibilidades con tu consulta. "
                    + "Consulta al personal del restaurante por otras opciones.");
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
                + (incompletos.isEmpty() ? "" : "\nOtros platos no recomendados por información insuficiente:\n" + faltantes)
                + "\nLa carta no certifica ausencia de trazas ni contaminación cruzada. Confirma la preparación con el restaurante.");
    }

    private record PlatoEvaluado(PlatoResponseDTO plato, DiagnosticoAlimentario diagnostico) {}

    private ChatIAResponseDTO noDisponible() {
        return new ChatIAResponseDTO(Estado.NO_DATA, "El restaurante seleccionado no está disponible.");
    }

    private String describir(PlatoResponseDTO plato) {
        String precio = plato.getPrecio() == null ? "precio sin registrar" : "S/ " + plato.getPrecio().toPlainString();
        return "%s (%s, disponible). Ingredientes: %s. Alérgenos: %s.".formatted(
                plato.getNombre(), precio, plato.getIngredientes().strip(), plato.getAlergenos().strip());
    }
}
