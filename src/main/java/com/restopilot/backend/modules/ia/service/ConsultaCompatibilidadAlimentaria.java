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
import static com.restopilot.backend.modules.ia.service.CatalogoIngredientes.normalizar;

final class ConsultaCompatibilidadAlimentaria {
    private final ConsultaRestauranteService restaurantes;
    private final PlatoService platos;
    private final EvaluadorAlimentario evaluador = new EvaluadorAlimentario();

    ConsultaCompatibilidadAlimentaria(ConsultaRestauranteService restaurantes, PlatoService platos) {
        this.restaurantes = restaurantes;
        this.platos = platos;
    }

    ChatIAResponseDTO consultar(Long restauranteId, String busqueda, PreferenciaAlimentaria preferencia,
                                List<RestriccionAlimentaria> restricciones) {
        if (restauranteId == null || restauranteId <= 0) {
            return respuesta(Estado.CLARIFICATION_REQUIRED, "Selecciona un restaurante para consultar la compatibilidad del plato.");
        }
        String query = normalizar(busqueda);
        if (query.isEmpty() || !SolicitudAlimentaria.valida(preferencia, restricciones)) {
            return respuesta(Estado.CLARIFICATION_REQUIRED,
                    "Indica el plato y tu preferencia vegetariana, vegana o restricción alimentaria soportada. "
                    + "Si la restricción no está soportada, consulta al restaurante.");
        }
        try {
            if (!restaurantes.consultar(restauranteId).activo()) return noDisponible();
        } catch (ResourceNotFoundException ex) { return noDisponible(); }

        var carta = platos.listar(restauranteId, null).stream()
                .filter(p -> Objects.equals(restauranteId, p.getRestauranteId()))
                .filter(p -> p.getNombre() != null && !p.getNombre().isBlank()).toList();
        var exactos = carta.stream().filter(p -> normalizar(p.getNombre()).equals(query)).toList();
        var encontrados = !exactos.isEmpty() ? exactos : carta.stream()
                .filter(p -> query.startsWith("#") ? ("#" + p.getId()).equals(query)
                        : normalizar(p.getNombre()).contains(query))
                .sorted(Comparator.comparing(PlatoResponseDTO::getNombre).thenComparing(
                        PlatoResponseDTO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(9).toList();
        if (encontrados.isEmpty()) return respuesta(Estado.NO_DATA, "No encontré ese plato en el restaurante seleccionado.");
        if (encontrados.size() > 1) {
            String opciones = encontrados.stream().limit(8).map(p -> p.getNombre() + " (#" + p.getId() + ")")
                    .collect(Collectors.joining(", "));
            return respuesta(Estado.CLARIFICATION_REQUIRED,
                    "Hay varios platos con ese nombre. Indica el nombre completo o su número precedido de #: " + opciones + ".");
        }
        var plato = encontrados.getFirst();
        if (!Boolean.TRUE.equals(plato.getDisponible())) {
            return respuesta(Estado.NO_DATA, "El plato «" + plato.getNombre() + "» no tiene disponibilidad confirmada. Consulta al restaurante.");
        }
        var diagnostico = evaluador.diagnosticar(plato, preferencia, new HashSet<>(restricciones));
        return switch (diagnostico.resultado()) {
            case NO_EVALUABLE -> respuesta(Estado.NO_DATA,
                    RespuestaInformacionAlimentaria.explicar(plato.getNombre(), diagnostico) + " "
                    + RespuestaInformacionAlimentaria.CONFIRMAR);
            case INCOMPATIBLE -> respuesta(Estado.ANSWER,
                    "Los datos registrados de «" + plato.getNombre() + "» muestran una incompatibilidad "
                    + "con la preferencia o restricciones indicadas. No lo recomiendo para esta consulta. "
                    + RespuestaInformacionAlimentaria.CONFIRMAR);
            case COMPATIBLE_SEGUN_REGISTRO -> respuesta(Estado.ANSWER,
                    "Según los ingredientes y alérgenos registrados, «" + plato.getNombre()
                    + "» es compatible con la consulta. " + RespuestaInformacionAlimentaria.CONFIRMAR);
        };
    }

    private ChatIAResponseDTO noDisponible() {
        return respuesta(Estado.NO_DATA, "El restaurante seleccionado no está disponible.");
    }

    private ChatIAResponseDTO respuesta(Estado estado, String mensaje) {
        return new ChatIAResponseDTO(estado, mensaje);
    }
}
