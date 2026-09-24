package com.restopilot.backend.modules.pagos.controller;

import com.restopilot.backend.modules.pagos.dto.PagoRequestDTO;
import com.restopilot.backend.modules.pagos.dto.PagoResponseDTO;
import com.restopilot.backend.modules.pagos.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos/{pedidoId}/pagos")
@CrossOrigin(origins = "*")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping
    public ResponseEntity<PagoResponseDTO> registrarPago(
            @PathVariable Long pedidoId,
            @Valid @RequestBody PagoRequestDTO request
    ) {
        PagoResponseDTO response = pagoService.registrarPago(pedidoId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PagoResponseDTO> consultarPagoPorPedido(
            @PathVariable Long pedidoId
    ) {
        PagoResponseDTO response = pagoService.obtenerPagoPorPedido(pedidoId);
        return ResponseEntity.ok(response);
    }
}