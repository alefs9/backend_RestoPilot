package com.restopilot.backend.modules.restaurante.repository;

import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RestauranteRepository extends JpaRepository<Restaurante, Long> {
}