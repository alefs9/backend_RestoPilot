package com.restopilot.backend.modules.restaurante.controller;

import com.restopilot.backend.modules.restaurante.dto.RestauranteResponseDTO;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/restaurantes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR', 'DUENO')")
public class RestauranteController {
    private final ConsultaRestauranteService service;

    @GetMapping
    public List<RestauranteResponseDTO> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public RestauranteResponseDTO consultar(@PathVariable Long id) { return service.consultar(id); }
}
