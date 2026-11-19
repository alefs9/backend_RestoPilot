package com.restopilot.backend.modules.auth.controller;

import com.restopilot.backend.modules.auth.dto.AuthResponseDTO;
import com.restopilot.backend.modules.auth.dto.ActualizarPerfilRequestDTO;
import com.restopilot.backend.modules.auth.dto.PerfilResponseDTO;
import com.restopilot.backend.modules.auth.dto.LoginRequestDTO;
import com.restopilot.backend.modules.auth.dto.RegisterRequestDTO;
import com.restopilot.backend.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.restopilot.backend.modules.auth.entity.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        AuthResponseDTO response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<PerfilResponseDTO> me(Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(PerfilResponseDTO.from(usuario));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<PerfilResponseDTO> actualizarPerfil(
            Authentication authentication, @Valid @RequestBody ActualizarPerfilRequestDTO request) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(authService.actualizarPerfilCliente(usuario.getId(), request.nombreCompleto()));
    }
}
