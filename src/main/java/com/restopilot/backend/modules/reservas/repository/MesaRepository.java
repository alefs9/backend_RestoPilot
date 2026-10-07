package com.restopilot.backend.modules.reservas.repository;

import com.restopilot.backend.modules.reservas.entity.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Mesa m where m.id = :id")
    Optional<Mesa> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    // Buscar todas las mesas activas de un restaurante (para el plano interactivo)
    List<Mesa> findByRestauranteIdAndActivoTrue(Long restauranteId);

    // Buscar todas las mesas de un restaurante (incluidas las inactivas, vista Admin)
    List<Mesa> findByRestauranteId(Long restauranteId);

    // Verificar si ya existe una mesa con ese numero en el mismo restaurante
    Optional<Mesa> findByRestauranteIdAndNumero(Long restauranteId, Integer numero);
}
