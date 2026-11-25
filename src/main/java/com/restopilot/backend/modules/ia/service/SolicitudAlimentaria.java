package com.restopilot.backend.modules.ia.service;

import java.util.Collection;
import java.util.Objects;

final class SolicitudAlimentaria {
    private SolicitudAlimentaria() {}

    static boolean valida(PreferenciaAlimentaria preferencia, Collection<RestriccionAlimentaria> restricciones) {
        return preferencia != null && restricciones != null
                && restricciones.stream().noneMatch(Objects::isNull)
                && !restricciones.contains(RestriccionAlimentaria.OTRA)
                && (preferencia != PreferenciaAlimentaria.SIN_PREFERENCIA || !restricciones.isEmpty());
    }
}
