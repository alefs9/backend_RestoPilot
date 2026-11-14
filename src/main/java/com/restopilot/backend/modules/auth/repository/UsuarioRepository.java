package com.restopilot.backend.modules.auth.repository;

import com.restopilot.backend.modules.auth.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);
    @Query(value = "SELECT correo FROM restopilot.usuarios WHERE auth_user_id = :authId AND habilitado = true", nativeQuery = true)
    Optional<String> findCorreoByAuthUserId(@Param("authId") UUID authId);
    boolean existsByCorreo(String correo);
    List<Usuario> findByNombreCompletoContainingIgnoreCase(String nombreCompleto);
}
