package com.restopilot.backend.modules.auth.controller;

import com.restopilot.backend.modules.auth.dto.*;
import com.restopilot.backend.modules.restaurante.service.AltaRestauranteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OwnerRegistrationController {
    private final AltaRestauranteService altas;

    @PostMapping("/register-owner")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponseDTO registrar(@Valid @RequestBody RegisterOwnerRequestDTO request) {
        return altas.registrar(request);
    }
}
