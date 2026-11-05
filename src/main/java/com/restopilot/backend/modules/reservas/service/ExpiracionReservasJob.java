package com.restopilot.backend.modules.reservas.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;

@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "restopilot.reservas.expiracion.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiracionReservasJob {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExpiracionReservasJob.class);
    private final ExpiracionReservasService expiracion;

    @Scheduled(fixedDelayString = "${restopilot.reservas.expiracion.intervalo-ms:30000}",
            initialDelayString = "${restopilot.reservas.expiracion.intervalo-ms:30000}")
    public void ejecutar() {
        try { expiracion.expirarVencidas(); }
        catch (RuntimeException ex) {
            log.error("No se pudo ejecutar expiración de reservas. tipo={}", ex.getClass().getName());
        }
    }
}
