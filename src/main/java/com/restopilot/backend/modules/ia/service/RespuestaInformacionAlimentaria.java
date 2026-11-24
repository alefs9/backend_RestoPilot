package com.restopilot.backend.modules.ia.service;

import java.util.stream.Collectors;

final class RespuestaInformacionAlimentaria {
    static final String CONFIRMAR = "Consulta al personal del restaurante antes de elegir el plato. "
            + "La carta no certifica ausencia de trazas ni contaminación cruzada.";

    private RespuestaInformacionAlimentaria() {}

    static String explicar(String nombre, DiagnosticoAlimentario diagnostico) {
        String razones = diagnostico.motivos().stream().sorted().map(motivo -> switch (motivo) {
            case SOLICITUD_INCOMPLETA -> "falta precisar una preferencia o restricción soportada";
            case INGREDIENTES_AUSENTES -> "faltan los ingredientes registrados";
            case ALERGENOS_AUSENTES -> "faltan los alérgenos registrados";
            case INGREDIENTES_NO_INTERPRETABLES -> "hay ingredientes cuya composición no puedo interpretar";
            case ALERGENOS_NO_INTERPRETABLES -> "hay declaraciones de alérgenos que no puedo interpretar";
        }).collect(Collectors.joining("; "));
        return "No puedo confirmar la compatibilidad de «" + nombre + "»: " + razones + ".";
    }
}
