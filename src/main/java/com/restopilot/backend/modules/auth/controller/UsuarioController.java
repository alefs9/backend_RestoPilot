package com.restopilot.backend.modules.auth.controller;

import com.restopilot.backend.modules.auth.dto.*;
import com.restopilot.backend.modules.auth.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarios;

    @PostMapping("/administradores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DUENO')")
    public AdministradorResponseDTO crearAdministrador(@Valid @RequestBody CrearAdministradorRequestDTO request) {
        return usuarios.crearAdministrador(request);
    }
}
