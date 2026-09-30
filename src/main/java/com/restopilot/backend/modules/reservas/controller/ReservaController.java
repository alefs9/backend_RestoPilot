package com.restopilot.backend.modules.reservas.controller;

import com.restopilot.backend.modules.reservas.dto.MesaDisponibilidadDTO;
import com.restopilot.backend.modules.reservas.service.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * TSK-013: Consultar disponibilidad de mesas por fecha y horario.
     *
     * Ejemplo de uso:
     * GET /api/reservas/disponibilidad?restauranteId=1&fecha=2026-10-05&horaInicio=19:00&horaFin=21:00
     *
     * Retorna todas las mesas activas del restaurante con un flag indicando
     * si están disponibles (true) o ya reservadas (false) en ese horario.
     */
    @GetMapping("/disponibilidad")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'DUENO')")
    public List<MesaDisponibilidadDTO> consultarDisponibilidad(
            @RequestParam Long restauranteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaFin) {

        return reservaService.consultarDisponibilidad(restauranteId, fecha, horaInicio, horaFin);
    }
}
