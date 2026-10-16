package com.restopilot.backend.modules.catalogo.controller;

import com.restopilot.backend.modules.catalogo.dto.PlatoRequestDTO;
import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.service.PlatoService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platos")
@RequiredArgsConstructor
public class PlatoController {

    private final PlatoService platoService;

    // Listar platos y filtrar por categoría
    @GetMapping
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam Long restauranteId,
            @RequestParam(required = false) Long categoriaId) {

        List<PlatoResponseDTO> platos =
                platoService.listar(restauranteId, categoriaId);

        String mensaje;

        if (platos.isEmpty()) {
            mensaje = categoriaId != null
                    ? "No hay platos disponibles en esta categoría."
                    : "No hay platos disponibles en este restaurante.";
        } else {
            mensaje = "Platos encontrados correctamente.";
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", mensaje,
                "platos", platos
        ));
    }

    // Listar platos por restaurante
    @GetMapping("/restaurante/{restauranteId}")
    public ResponseEntity<List<PlatoResponseDTO>> listarPorRestaurante(
            @PathVariable Long restauranteId) {

        return ResponseEntity.ok(
                platoService.listarPorRestaurante(restauranteId)
        );
    }

    // Buscar plato por ID
    @GetMapping("/{id}")
    public ResponseEntity<PlatoResponseDTO> buscarPorId(
            @PathVariable Long id) {

        return ResponseEntity.ok(platoService.buscarPorId(id));
    }

    // Crear plato
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ResponseEntity<PlatoResponseDTO> crear(
            @RequestBody PlatoRequestDTO dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platoService.crear(dto));
    }

    // Actualizar plato
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ResponseEntity<PlatoResponseDTO> actualizar(
            @PathVariable Long id,
            @RequestBody PlatoRequestDTO dto) {

        return ResponseEntity.ok(platoService.actualizar(id, dto));
    }

    // Cambiar disponibilidad
    @PatchMapping("/{id}/disponibilidad")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ResponseEntity<PlatoResponseDTO> cambiarDisponibilidad(
            @PathVariable Long id,
            @RequestParam Boolean disponible) {

        return ResponseEntity.ok(
                platoService.cambiarDisponibilidad(id, disponible)
        );
    }

    // Eliminar plato
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DUENO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {

        platoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}