package com.restopilot.backend.modules.catalogo.repository;

import com.restopilot.backend.modules.catalogo.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByRestauranteId(Long restauranteId);
}