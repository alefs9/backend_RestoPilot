package com.restopilot.backend.modules.catalogo.controller;

import com.restopilot.backend.modules.catalogo.entity.Categoria;
import com.restopilot.backend.modules.catalogo.service.CategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping("/restaurante/{restauranteId}")
    public ResponseEntity<List<Categoria>> listarPorRestaurante(@PathVariable Long restauranteId) {
        return ResponseEntity.ok(categoriaService.listarPorRestaurante(restauranteId));
    }

    @PostMapping
    public ResponseEntity<Categoria> crear(@RequestBody Categoria categoria) {
        return ResponseEntity.status(HttpStatus.CREATED).json(categoriaService.guardar(categoria)); // Ajustado de ser necesario
    }

    @PutMapping("/{id}")
    public ResponseEntity<Categoria> actualizar(@PathVariable Long id, @RequestBody Categoria categoria) {
        return ResponseEntity.ok(categoriaService.actualizar(id, categoria.getNombre(), categoria.getDescripcion()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}