package com.restopilot.backend.modules.pedidos.controller;

import com.restopilot.backend.modules.pedidos.dto.PedidoRequestDTO;
import com.restopilot.backend.modules.pedidos.dto.PedidoResponseDTO;
import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    // POST (Crear pedido - US11)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENTE')")
    public PedidoResponseDTO crearPedido(@Valid @RequestBody PedidoRequestDTO request) {
        return pedidoService.crearPedido(request);
    }

    // GET (Consultar historial del cliente - US07)
    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENTE')")
    public Page<PedidoResponseDTO> getMisPedidos(Pageable pageable) {
        return pedidoService.getMisPedidos(pageable);
    }

    // GET (Consultar detalle por ID - US12)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'DUENO')")
    public PedidoResponseDTO getById(@PathVariable Long id) {
        return pedidoService.getById(id);
    }

    // GET (Consultar cola de cocina priorizada - US15)
    @GetMapping("/pendientes")
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public Page<PedidoResponseDTO> getPendientesCocina(Pageable pageable) {
        return pedidoService.getPendientesCocina(pageable);
    }

    // PUT (Actualizar pedido completo)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public PedidoResponseDTO actualizarPedidoCompleto(@PathVariable Long id, @Valid @RequestBody PedidoRequestDTO request) {
        return pedidoService.actualizarPedido(id, request);
    }

    // PATCH / UPDATE (Actualizar solo el estado - US19)
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public PedidoResponseDTO actualizarEstado(@PathVariable Long id, @RequestParam EstadoPedido nuevoEstado) {
        return pedidoService.actualizarEstado(id, nuevoEstado);
    }

    // DELETE (Eliminar/Cancelar pedido de forma manual administrativa)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'DUENO')")
    public void eliminarPedido(@PathVariable Long id) {
        pedidoService.eliminarPedido(id);
    }
}