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
    private final com.restopilot.backend.modules.catalogo.repository.PlatoRepository platos;

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
        validarNombre(categoria.getNombre());
        // Solo aceptar campos editables; las fechas e identidad pertenecen al servidor.
        Categoria nueva = new Categoria();
        nueva.setRestauranteId(categoria.getRestauranteId());
        nueva.setNombre(categoria.getNombre().strip());
        nueva.setDescripcion(categoria.getDescripcion());
        return categoriaRepository.save(nueva);
    }

    public Categoria actualizar(Long id, String nombre, String descripcion) {
        Categoria categoria = buscarPorId(id);
        acceso.exigirGestion(categoria.getRestauranteId());
        // null conserva el campo, conforme al contrato de actualización existente.
        if (nombre != null) validarNombre(nombre);
        categoria.actualizarDatos(nombre == null ? null : nombre.strip(), descripcion);
        return categoriaRepository.save(categoria);
    }

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.length() > 100) {
            throw new com.restopilot.backend.core.exception.BusinessRuleException(
                    "El nombre de la categoría es obligatorio y admite hasta 100 caracteres.");
        }
    }

    public void eliminar(Long id) {
        acceso.exigirGestion(buscarPorId(id).getRestauranteId());
        if (platos.existsByCategoriaId(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "No se puede eliminar la categoría porque tiene platos asociados.");
        }
        try {
            categoriaRepository.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // La FK sigue siendo la protección final ante asociaciones concurrentes.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "No se puede eliminar la categoría porque tiene datos asociados.");
        }
    }
}
