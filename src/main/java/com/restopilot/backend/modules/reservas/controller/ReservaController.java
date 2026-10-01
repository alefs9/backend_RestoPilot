package com.restopilot.backend.modules.reservas.controller;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.reservas.dto.MesaDisponibilidadDTO;
import com.restopilot.backend.modules.reservas.dto.ModuloReservasStatusDTO;
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
     * TSK-013: Consultar disponibilidad de mesas por fecha y horario.
     */
    @GetMapping("/disponibilidad")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
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
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO registrarReserva(@Valid @RequestBody ReservaRequestDTO request) {
        return reservaService.registrarReserva(request);
    }

    /**
     * TSK-015 (US08): Endpoint PUT para modificar una reserva existente.
     * Permite modificar mesa, fecha, horario, comensales y notas.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
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
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO cancelarReserva(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = (body != null) ? body.get("motivo") : null;
        return reservaService.cancelarReserva(id, motivo);
    }

    /**
     * TSK-017 (US07): Consultar historial de reservas del cliente autenticado.
     * Retorna todas las reservas del cliente ordenadas cronológicamente (más recientes primero).
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public List<ReservaResponseDTO> obtenerMisReservas() {
        return reservaService.obtenerMisReservas();
    }

    /**
     * TSK-017 (US07): Consultar listado de reservas para gestión del restaurante (Admin/Dueño).
     * Permite filtrar opcionalmente por fecha y por estado.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public List<ReservaResponseDTO> obtenerReservasRestaurante(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) EstadoReserva estado) {
        return reservaService.obtenerReservasRestaurante(fecha, estado);
    }

    /**
     * TSK-017 (US07): Consultar detalle de una reserva específica por su ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ReservaResponseDTO obtenerReservaPorId(@PathVariable Long id) {
        return reservaService.obtenerReservaPorId(id);
    }

    /**
     * TSK-018: Consultar el estado de activación SaaS del módulo de reservas del restaurante propio.
     */
    @GetMapping("/modulo/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ModuloReservasStatusDTO obtenerEstadoModulo() {
        return reservaService.obtenerEstadoModulo();
    }

    /**
     * TSK-018: Consultar si un restaurante tiene habilitado el módulo de reservas (público / cliente).
     */
    @GetMapping("/modulo/estado/restaurante/{restauranteId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
    public ModuloReservasStatusDTO obtenerEstadoModuloPorRestaurante(@PathVariable Long restauranteId) {
        return reservaService.obtenerEstadoModuloPorRestaurante(restauranteId);
    }

    /**
     * TSK-018: Activar o desactivar condicionalmente el módulo completo de reservas (Admin/Dueño).
     * Modifica la bandera tieneAtencionFisica del Restaurante en el modelo SaaS.
     */
    @PatchMapping("/modulo/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ModuloReservasStatusDTO cambiarEstadoModulo(@RequestBody Map<String, Boolean> body) {
        Boolean habilitado = body.getOrDefault("habilitado", true);
        return reservaService.cambiarEstadoModulo(habilitado);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRuleException(BusinessRuleException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("mensaje", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.NOT_FOUND.value());
        error.put("mensaje", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
