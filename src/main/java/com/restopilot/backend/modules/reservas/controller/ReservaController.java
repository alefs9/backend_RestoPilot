package com.restopilot.backend.modules.reservas.controller;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.reservas.dto.EstadoModuloRequestDTO;
import com.restopilot.backend.modules.reservas.dto.MesaDisponibilidadDTO;
import com.restopilot.backend.modules.reservas.dto.ModuloReservasStatusDTO;
import com.restopilot.backend.modules.reservas.dto.ProcesarReservaRequestDTO;
import com.restopilot.backend.modules.reservas.dto.ReservaRequestDTO;
import com.restopilot.backend.modules.reservas.dto.ReservaResponseDTO;
import com.restopilot.backend.modules.reservas.entity.EstadoReserva;
import com.restopilot.backend.modules.reservas.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * US05: Consultar disponibilidad por restaurante, fecha y horario.
     */
    @GetMapping("/disponibilidad")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ResponseEntity<Map<String, Object>> consultarDisponibilidad(
            @RequestParam Long restauranteId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaFin,
            @RequestParam(defaultValue = "1") Integer numeroComensales) {

        List<MesaDisponibilidadDTO> mesas =
                reservaService.consultarDisponibilidad(
                        restauranteId,
                        fecha,
                        horaInicio,
                        horaFin,
                        numeroComensales
                );

        boolean hayDisponibles = mesas.stream()
                .anyMatch(MesaDisponibilidadDTO::disponible);

        String mensaje = hayDisponibles
                ? "Mesas disponibles encontradas correctamente."
                : "No hay disponibilidad para el horario seleccionado.";

        return ResponseEntity.ok(Map.of(
                "mensaje", mensaje,
                "mesas", mesas
        ));
    }

    /**
     * US17: Confirmar o rechazar una reserva pendiente.
     */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO procesarReserva(
            @PathVariable Long id,
            @Valid @RequestBody ProcesarReservaRequestDTO request) {

        return reservaService.procesarReserva(id, request);
    }

    /**
     * US06: Registrar una reserva.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO registrarReserva(
            @Valid @RequestBody ReservaRequestDTO request) {

        return reservaService.registrarReserva(request);
    }

    /**
     * US08: Modificar una reserva existente.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO modificarReserva(
            @PathVariable Long id,
            @Valid @RequestBody ReservaRequestDTO request) {

        return reservaService.modificarReserva(id, request)
                .conMensaje("Reserva modificada con éxito.");
    }

    /**
     * US09: Cancelar una reserva con motivo opcional.
     */
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO cancelarReserva(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        String motivo = body != null ? body.get("motivo") : null;
        return reservaService.cancelarReserva(id, motivo);
    }

    /**
     * US07: Consultar las reservas del usuario autenticado.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ResponseEntity<Map<String, Object>> obtenerMisReservas() {
        List<ReservaResponseDTO> reservas =
                reservaService.obtenerMisReservas();

        String mensaje = reservas.isEmpty()
                ? "No se encontraron reservas en el historial."
                : "Historial de reservas obtenido correctamente.";

        return ResponseEntity.ok(Map.of(
                "mensaje", mensaje,
                "reservas", reservas
        ));
    }

    /**
     * Listado administrativo del restaurante propio.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ResponseEntity<Map<String, Object>> obtenerReservasRestaurante(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) EstadoReserva estado) {

        List<ReservaResponseDTO> reservas =
                reservaService.obtenerReservasRestaurante(fecha, estado);

        String mensaje = reservas.isEmpty()
                ? "No se encontraron reservas para el restaurante."
                : "Reservas obtenidas correctamente.";

        return ResponseEntity.ok(Map.of(
                "mensaje", mensaje,
                "reservas", reservas
        ));
    }

    /**
     * Consultar el detalle de una reserva.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO obtenerReservaPorId(@PathVariable Long id) {
        return reservaService.obtenerReservaPorId(id);
    }

    /**
     * Consultar el estado del módulo del restaurante propio.
     */
    @GetMapping("/modulo/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ModuloReservasStatusDTO obtenerEstadoModulo() {
        return reservaService.obtenerEstadoModulo();
    }

    /**
     * Consultar el estado del módulo de un restaurante específico.
     */
    @GetMapping("/modulo/estado/restaurante/{restauranteId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ModuloReservasStatusDTO obtenerEstadoModuloPorRestaurante(
            @PathVariable Long restauranteId) {

        return reservaService.obtenerEstadoModuloPorRestaurante(restauranteId);
    }

    /**
     * Activar o desactivar el módulo del restaurante propio.
     */
    @PatchMapping("/modulo/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ModuloReservasStatusDTO cambiarEstadoModulo(
            @Valid @RequestBody EstadoModuloRequestDTO body) {

        return reservaService.cambiarEstadoModulo(body.habilitado());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRuleException(
            BusinessRuleException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("mensaje", ex.getMessage());

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.NOT_FOUND.value());
        error.put("mensaje", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}