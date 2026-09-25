package com.restopilot.backend.modules.catalogo.controller;

import com.restopilot.backend.modules.catalogo.dto.PlatoRequestDTO;
import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platos")
@RequiredArgsConstructor
public class PlatoController {

    private final PlatoService platoService;

    @GetMapping("/restaurante/{restauranteId}")
    public ResponseEntity<List<PlatoResponseDTO>> listarPorRestaurante(@PathVariable Long restauranteId) {
        return ResponseEntity.ok(platoService.listarPorRestaurante(restauranteId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlatoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(platoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<PlatoResponseDTO> crear(@RequestBody PlatoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platoService.crear(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlatoResponseDTO> actualizar(@PathVariable Long id, @RequestBody PlatoRequestDTO dto) {
        return ResponseEntity.ok(platoService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<PlatoResponseDTO> cambiarDisponibilidad(@PathVariable Long id, @RequestParam Boolean disponible) {
        return ResponseEntity.ok(platoService.cambiarDisponibilidad(id, disponible));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        platoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}