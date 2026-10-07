package com.restopilot.backend.modules.catalogo.service;

import com.restopilot.backend.modules.catalogo.dto.PlatoRequestDTO;
import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.entity.Categoria;
import com.restopilot.backend.modules.catalogo.entity.Plato;
import com.restopilot.backend.modules.catalogo.exception.PlatoNotFoundException;
import com.restopilot.backend.modules.catalogo.mapper.PlatoMapper;
import com.restopilot.backend.modules.catalogo.repository.PlatoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatoService {

    private final PlatoRepository platoRepository;
    private final CategoriaService categoriaService;
    private final PlatoMapper platoMapper;
    private final com.restopilot.backend.security.AccesoRestaurante acceso;

    public List<PlatoResponseDTO> listar(Long restauranteId, Long categoriaId) {
        return (categoriaId == null ? platoRepository.findByRestauranteId(restauranteId)
                : platoRepository.findByRestauranteIdAndCategoriaId(restauranteId, categoriaId))
                .stream().map(platoMapper::toDto).toList();
    }

    public List<PlatoResponseDTO> listarPorRestaurante(Long restauranteId) {
        return platoRepository.findByRestauranteId(restauranteId).stream()
                .map(platoMapper::toDto)
                .collect(Collectors.toList());
    }

    public PlatoResponseDTO buscarPorId(Long id) {
        Plato plato = platoRepository.findById(id)
                .orElseThrow(() -> new PlatoNotFoundException(id));
        return platoMapper.toDto(plato);
    }

    public PlatoResponseDTO crear(PlatoRequestDTO dto) {
        acceso.exigirGestion(dto.getRestauranteId());
        validarDatos(dto, true);
        Categoria categoria = categoriaService.buscarPorId(dto.getCategoriaId());
        validarCategoria(categoria, dto.getRestauranteId());
        Plato plato = platoMapper.toEntity(dto, categoria);
        Plato guardado = platoRepository.save(plato);
        return platoMapper.toDto(guardado);
    }

    public PlatoResponseDTO actualizar(Long id, PlatoRequestDTO dto) {
        Plato plato = platoRepository.findById(id)
                .orElseThrow(() -> new PlatoNotFoundException(id));

        acceso.exigirGestion(plato.getRestauranteId());
        validarDatos(dto, false);
        if (dto.getRestauranteId() != null && !dto.getRestauranteId().equals(plato.getRestauranteId())) {
            throw new com.restopilot.backend.core.exception.BusinessRuleException("No se puede cambiar el restaurante del plato.");
        }
        Categoria categoria = null;
        if (dto.getCategoriaId() != null) {
            categoria = categoriaService.buscarPorId(dto.getCategoriaId());
            validarCategoria(categoria, plato.getRestauranteId());
        }

        plato.actualizarDatos(
                dto.getNombre(),
                dto.getDescripcion(),
                dto.getPrecio(),
                dto.getIngredientes(),
                dto.getAlergenos(),
                dto.getComplejidad(),
                dto.getTiempoPreparacionMinutos(),
                dto.getImagenUrl(),
                categoria
        );

        Plato actualizado = platoRepository.save(plato);
        return platoMapper.toDto(actualizado);
    }

    public PlatoResponseDTO cambiarDisponibilidad(Long id, Boolean disponible) {
        Plato plato = platoRepository.findById(id)
                .orElseThrow(() -> new PlatoNotFoundException(id));
        acceso.exigirGestion(plato.getRestauranteId());
        plato.cambiarDisponibilidad(disponible);
        return platoMapper.toDto(platoRepository.save(plato));
    }

    public void eliminar(Long id) {
        Plato plato = platoRepository.findById(id).orElseThrow(() -> new PlatoNotFoundException(id));
        acceso.exigirGestion(plato.getRestauranteId());
        platoRepository.deleteById(id);
    }

    private void validarCategoria(Categoria categoria, Long restauranteId) {
        if (!java.util.Objects.equals(categoria.getRestauranteId(), restauranteId)) {
            throw new com.restopilot.backend.core.exception.BusinessRuleException("La categoría no pertenece al restaurante.");
        }
    }

    private void validarDatos(PlatoRequestDTO dto, boolean nuevo) {
        if ((nuevo && (dto.getNombre() == null || dto.getNombre().isBlank() || dto.getCategoriaId() == null
                || dto.getPrecio() == null || dto.getComplejidad() == null || dto.getTiempoPreparacionMinutos() == null))
                || (dto.getNombre() != null && dto.getNombre().isBlank())
                || (dto.getPrecio() != null && dto.getPrecio().signum() <= 0)) {
            throw new com.restopilot.backend.core.exception.BusinessRuleException("Ingrese nombre, categoría, precio positivo, complejidad y tiempo de preparación.");
        }
        if (dto.getComplejidad() != null) {
            String nivel = dto.getComplejidad().trim().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("BAJA", "MEDIA", "ALTA").contains(nivel)) {
                throw new com.restopilot.backend.core.exception.BusinessRuleException("La complejidad debe ser BAJA, MEDIA o ALTA.");
            }
            dto.setComplejidad(nivel);
        }
        if (dto.getTiempoPreparacionMinutos() != null && (dto.getTiempoPreparacionMinutos() < 5 || dto.getTiempoPreparacionMinutos() > 15)) {
            throw new com.restopilot.backend.core.exception.BusinessRuleException("El tiempo de preparación debe estar entre 5 y 15 minutos.");
        }
    }
}
