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
    private static final int LIMITE = 3;
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
        if (compatibles.isEmpty()) {
            if (!incompletos.isEmpty()) return new ChatIAResponseDTO(Estado.NO_DATA,
                    "Busquemos una opción que encaje contigo. Para recomendarte con confianza, "
                    + "conviene confirmar los ingredientes y alérgenos con el restaurante. "
                    + "Puedes preguntarme por un plato concreto para revisar su información registrada.");
            return new ChatIAResponseDTO(Estado.NO_DATA,
                    evaluados.isEmpty()
                    ? "Podemos empezar eligiendo otro restaurante para explorar su carta. Usa Cambiar restaurante en el chat."
                    : "Para tu preferencia, te sugiero consultar al restaurante por una preparación adaptada "
                    + "o elegir otro local desde Cambiar restaurante. Mantengamos tus restricciones al buscar alternativas.");
        }
        String detalles = compatibles.stream().limit(LIMITE).map(this::describir).collect(Collectors.joining("\n\n"));
        String preferenciaTexto = switch (preferencia) {
            case VEGETARIANA -> "vegetarianas";
            case VEGANA -> "veganas";
            case SIN_PREFERENCIA -> "para las restricciones que indicaste";
        };
        String inicio = compatibles.size() == 1
                ? "Te propongo esta opción " + (preferencia == PreferenciaAlimentaria.VEGETARIANA ? "vegetariana"
                    : preferencia == PreferenciaAlimentaria.VEGANA ? "vegana" : "para tu consulta")
                : "Te propongo estas opciones " + preferenciaTexto;
        String cierre = compatibles.size() > LIMITE
                ? "Estas son tres opciones para empezar. Puedes preguntarme por un plato concreto para conocerlo mejor."
                : "¿Cuál te llama más la atención? Puedes preguntarme por sus ingredientes indicando el nombre del plato.";
        String cuidado = exclusiones.isEmpty()
                ? "Si tienes alguna alergia, confirma la preparación con el restaurante: la carta no certifica ausencia de trazas ni contaminación cruzada."
                : "Para tus restricciones, confirma la preparación con el restaurante: la carta no certifica ausencia de trazas ni contaminación cruzada.";
        return new ChatIAResponseDTO(Estado.ANSWER,
                inicio + ", según la carta registrada:\n\n" + detalles + "\n\n" + cierre + "\n\n" + cuidado);
    }

    private record PlatoEvaluado(PlatoResponseDTO plato, DiagnosticoAlimentario diagnostico) {}

    private ChatIAResponseDTO noDisponible() {
        return new ChatIAResponseDTO(Estado.NO_DATA, "El restaurante seleccionado no está disponible.");
    }

    private String describir(PlatoResponseDTO plato) {
        String precio = plato.getPrecio() == null ? "Consulta el precio con el restaurante"
                : "S/ " + plato.getPrecio().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String alergenos = plato.getAlergenos().strip();
        boolean sinDeclarados = alergenos.toLowerCase(java.util.Locale.ROOT)
                .matches("(ninguno|ninguna|sin alérgenos declarados|sin alergenos declarados|no declarados)" );
        return plato.getNombre() + " - " + precio + "\n"
                + "Lleva " + plato.getIngredientes().strip() + "."
                + (sinDeclarados ? "" : "\nAlérgenos registrados: " + alergenos + ".");
    }
}
