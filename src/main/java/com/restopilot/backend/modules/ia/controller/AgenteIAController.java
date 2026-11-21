package com.restopilot.backend.modules.ia.controller;

import com.restopilot.backend.modules.ia.dto.ChatIARequestDTO;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.modules.ia.service.AgenteIAService;
import com.restopilot.backend.security.AccesoRestaurante;
import com.restopilot.backend.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ia/admin")
@RequiredArgsConstructor
public class AgenteIAController {
    private final AgenteIAService agente;
    private final AccesoRestaurante accesoRestaurante;
    private final CurrentUser currentUser;

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('DUENO', 'ADMINISTRADOR')")
    public ChatIAResponseDTO chat(@Valid @RequestBody ChatIARequestDTO solicitud) {
        Long restauranteId = solicitud.restauranteId() == null
                ? currentUser.getRestauranteId() : solicitud.restauranteId();
        accesoRestaurante.exigirGestion(restauranteId);
        return agente.responderAdministrativo(solicitud.mensaje(), restauranteId);
    }

    @ExceptionHandler(AgenteIAService.FalloChat.class)
    public ResponseEntity<ErrorChat> errorChat(AgenteIAService.FalloChat error) {
        return ResponseEntity.status(error.estado())
                .body(new ErrorChat(error.codigo(), error.getMessage()));
    }

    public record ErrorChat(String codigo, String mensaje) {}
}
