package com.restopilot.backend.modules.reservas.controller;

import com.restopilot.backend.modules.reservas.dto.MesaDisponibilidadDTO;
import com.restopilot.backend.modules.reservas.dto.ReservaRequestDTO;
import com.restopilot.backend.modules.reservas.dto.ReservaResponseDTO;
import com.restopilot.backend.modules.reservas.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * TSK-013: Consultar disponibilidad de mesas por fecha y horario.
     */
    @GetMapping("/disponibilidad")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'ADMINISTRADOR', 'DUENO')")
    public List<MesaDisponibilidadDTO> consultarDisponibilidad(
            @RequestParam Long restauranteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaFin) {

        return reservaService.consultarDisponibilidad(restauranteId, fecha, horaInicio, horaFin);
    }

    /**
     * TSK-014 (US06): Crear endpoint POST para registrar reservas originadas
     * desde el plano visual interactivo del salón.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO registrarReserva(@Valid @RequestBody ReservaRequestDTO request) {
        return reservaService.registrarReserva(request);
    }

    /**
     * TSK-015 (US08): Endpoint PUT para modificar una reserva existente.
     * Permite modificar mesa, fecha, horario, comensales y notas.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO modificarReserva(
            @PathVariable Long id,
            @Valid @RequestBody ReservaRequestDTO request) {
        return reservaService.modificarReserva(id, request);
    }

    /**
     * TSK-016 (US08): Endpoint PATCH para cancelar una reserva.
     * Permite al cliente cancelar su propia reserva o al admin/dueno cancelar una de su restaurante.
     * Acepta opcionalmente un motivo en el cuerpo de la petición.
     */
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO cancelarReserva(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = (body != null) ? body.get("motivo") : null;
        return reservaService.cancelarReserva(id, motivo);
    }
}
