package com.restopilot.backend.modules.catalogo.service;

import com.restopilot.backend.modules.catalogo.entity.Categoria;
import com.restopilot.backend.modules.catalogo.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final com.restopilot.backend.security.AccesoRestaurante acceso;

    public List<Categoria> listarPorRestaurante(Long restauranteId) {
        return categoriaRepository.findByRestauranteId(restauranteId);
    }

    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new com.restopilot.backend.core.exception.ResourceNotFoundException("Categoría con ID " + id + " no encontrada."));
    }

    public Categoria guardar(Categoria categoria) {
        acceso.exigirGestion(categoria.getRestauranteId());
        if (categoria.getId() != null || categoria.getNombre() == null || categoria.getNombre().isBlank()) {
            throw new com.restopilot.backend.core.exception.BusinessRuleException("Ingrese una categoría nueva con nombre.");
        }
        return categoriaRepository.save(categoria);
    }

    public Categoria actualizar(Long id, String nombre, String descripcion) {
        Categoria categoria = buscarPorId(id);
        acceso.exigirGestion(categoria.getRestauranteId());
        categoria.actualizarDatos(nombre, descripcion);
        return categoriaRepository.save(categoria);
    }

    public void eliminar(Long id) {
        acceso.exigirGestion(buscarPorId(id).getRestauranteId());
        categoriaRepository.deleteById(id);
    }
}
