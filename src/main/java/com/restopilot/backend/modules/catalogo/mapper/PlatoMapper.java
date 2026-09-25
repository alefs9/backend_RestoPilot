package com.restopilot.backend.modules.catalogo.mapper;

import com.restopilot.backend.modules.catalogo.dto.PlatoRequestDTO;
import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.entity.Categoria;
import com.restopilot.backend.modules.catalogo.entity.Plato;
import org.springframework.stereotype.Component;

@Component
public class PlatoMapper {

    public Plato toEntity(PlatoRequestDTO dto, Categoria categoria) {
        if (dto == null) return null;
        Plato plato = new Plato();
        plato.setRestauranteId(dto.getRestauranteId());
        plato.setCategoria(categoria);
        plato.setNombre(dto.getNombre());
        plato.setDescripcion(dto.getDescripcion());
        plato.setPrecio(dto.getPrecio());
        plato.setIngredientes(dto.getIngredientes());
        plato.setAlergenos(dto.getAlergenos());
        plato.setComplejidad(dto.getComplejidad());
        plato.setTiempoPreparacionMinutos(dto.getTiempoPreparacionMinutos());
        plato.setDisponible(dto.getDisponible() != null ? dto.getDisponible() : true);
        plato.setImagenUrl(dto.getImagenUrl());
        return plato;
    }

    public PlatoResponseDTO toDto(Plato plato) {
        if (plato == null) return null;
        PlatoResponseDTO dto = new PlatoResponseDTO();
        dto.setId(plato.getId());
        dto.setRestauranteId(plato.getRestauranteId());
        if (plato.getCategoria() != null) {
            dto.setCategoriaId(plato.getCategoria().getId());
            dto.setCategoriaNombre(plato.getCategoria().getNombre());
        }
        dto.setNombre(plato.getNombre());
        dto.setDescripcion(plato.getDescripcion());
        dto.setPrecio(plato.getPrecio());
        dto.setIngredientes(plato.getIngredientes());
        dto.setAlergenos(plato.getAlergenos());
        dto.setComplejidad(plato.getComplejidad());
        dto.setTiempoPreparacionMinutos(plato.getTiempoPreparacionMinutos());
        dto.setDisponible(plato.getDisponible());
        plato.setImagenUrl(dto.getImagenUrl());
        dto.setCreadoEn(plato.getCreadoEn());
        dto.setActualizadoEn(plato.getActualizadoEn());
        return dto;
    }
}