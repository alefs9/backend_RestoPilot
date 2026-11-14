package com.restopilot.backend.modules.auth.controller;

import com.restopilot.backend.modules.auth.dto.AuthResponseDTO;
import com.restopilot.backend.modules.auth.dto.LoginRequestDTO;
import com.restopilot.backend.modules.auth.dto.RegisterRequestDTO;
import com.restopilot.backend.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.restopilot.backend.modules.auth.entity.Usuario;
import org.springframework.security.core.Authentication;
import java.util.Map;

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
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        Map<String, Object> profile = new java.util.LinkedHashMap<>();
        profile.put("id", usuario.getId());
        profile.put("nombreCompleto", usuario.getNombreCompleto());
        profile.put("correo", usuario.getCorreo());
        profile.put("rol", usuario.getRol().name());
        profile.put("restauranteId", usuario.getRestaurante() == null ? null : usuario.getRestaurante().getId());
        return ResponseEntity.ok(profile);
    }
}
