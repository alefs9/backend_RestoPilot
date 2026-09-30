package com.restopilot.backend.modules.reservas.repository;

import com.restopilot.backend.modules.reservas.entity.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    // Buscar todas las mesas activas de un restaurante (para el plano interactivo)
    List<Mesa> findByRestauranteIdAndActivoTrue(Long restauranteId);

    // Buscar todas las mesas de un restaurante (incluidas las inactivas, vista Admin)
    List<Mesa> findByRestauranteId(Long restauranteId);

    // Verificar si ya existe una mesa con ese numero en el mismo restaurante
    Optional<Mesa> findByRestauranteIdAndNumero(Long restauranteId, Integer numero);
}
