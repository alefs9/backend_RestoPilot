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
        Categoria categoria = categoriaService.buscarPorId(dto.getCategoriaId());
        Plato plato = platoMapper.toEntity(dto, categoria);
        Plato guardado = platoRepository.save(plato);
        return platoMapper.toDto(guardado);
    }

    public PlatoResponseDTO actualizar(Long id, PlatoRequestDTO dto) {
        Plato plato = platoRepository.findById(id)
                .orElseThrow(() -> new PlatoNotFoundException(id));

        Categoria categoria = null;
        if (dto.getCategoriaId() != null) {
            categoria = categoriaService.buscarPorId(dto.getCategoriaId());
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
        plato.cambiarDisponibilidad(disponible);
        return platoMapper.toDto(platoRepository.save(plato));
    }

    public void eliminar(Long id) {
        if (!platoRepository.existsById(id)) {
            throw new PlatoNotFoundException(id);
        }
        platoRepository.deleteById(id);
    }
}