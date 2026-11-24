package com.restopilot.backend.modules.ia.service;

import java.util.Set;

record DiagnosticoAlimentario(EvaluadorAlimentario.Resultado resultado, Set<Motivo> motivos) {
    enum Motivo {
        SOLICITUD_INCOMPLETA,
        INGREDIENTES_AUSENTES,
        ALERGENOS_AUSENTES,
        INGREDIENTES_NO_INTERPRETABLES,
        ALERGENOS_NO_INTERPRETABLES
    }

    DiagnosticoAlimentario {
        motivos = Set.copyOf(motivos);
    }
}
