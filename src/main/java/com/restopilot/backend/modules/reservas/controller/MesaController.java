package com.restopilot.backend.modules.reservas.controller;

import com.restopilot.backend.modules.reservas.dto.MesaRequestDTO;
import com.restopilot.backend.modules.reservas.dto.MesaResponseDTO;
import com.restopilot.backend.modules.reservas.service.MesaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
public class MesaController {

    private final MesaService mesaService;

    // POST - Crear mesa (Admin/Dueno) - US16
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public MesaResponseDTO crearMesa(@Valid @RequestBody MesaRequestDTO request) {
        return mesaService.crearMesa(request);
    }

    // GET - Obtener todas las mesas del restaurante del usuario (Admin/Dueno)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public List<MesaResponseDTO> obtenerMesasPorRestaurante() {
        return mesaService.obtenerMesasPorRestaurante();
    }

    // GET - Obtener mesas activas de un restaurante especifico (Cliente - para el plano interactivo)
    @GetMapping("/restaurante/{restauranteId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'DUENO')")
    public List<MesaResponseDTO> obtenerMesasActivasPorRestaurante(@PathVariable Long restauranteId) {
        return mesaService.obtenerMesasActivasPorRestaurante(restauranteId);
    }

    // GET - Obtener mesa por ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'DUENO')")
    public MesaResponseDTO obtenerMesaPorId(@PathVariable Long id) {
        return mesaService.obtenerMesaPorId(id);
    }

    // PUT - Actualizar mesa completa (Admin/Dueno) - US16
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public MesaResponseDTO actualizarMesa(@PathVariable Long id, @Valid @RequestBody MesaRequestDTO request) {
        return mesaService.actualizarMesa(id, request);
    }

    // PATCH - Activar/Desactivar mesa (Admin/Dueno) - US16
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public MesaResponseDTO cambiarEstadoMesa(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean activo = body.get("activo");
        return mesaService.cambiarEstadoMesa(id, activo);
    }
}
