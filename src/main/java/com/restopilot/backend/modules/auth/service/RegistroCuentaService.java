package com.restopilot.backend.modules.auth.service;

import com.restopilot.backend.modules.auth.dto.CuentaRequestDTO;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistroCuentaService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public String validarCorreoDisponible(String correo) {
        String normalizado = correo.trim().toLowerCase(Locale.ROOT);
        if (repository.existsByCorreo(normalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo electrónico ya se encuentra registrado.");
        }
        return normalizado;
    }

    public Usuario guardar(CuentaRequestDTO datos, String correo, Rol rol, Restaurante restaurante) {
        // La restricción única del correo también protege solicitudes simultáneas.
        return repository.saveAndFlush(Usuario.builder().nombreCompleto(datos.getNombreCompleto().trim())
                .correo(correo).passwordHash(passwordEncoder.encode(datos.getPassword()))
                .rol(rol).restaurante(restaurante).habilitado(true).build());
    }
}
