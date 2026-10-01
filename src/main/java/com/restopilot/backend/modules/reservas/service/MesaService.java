package com.restopilot.backend.modules.reservas.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.reservas.dto.MesaRequestDTO;
import com.restopilot.backend.modules.reservas.dto.MesaResponseDTO;
import com.restopilot.backend.modules.reservas.entity.Mesa;
import com.restopilot.backend.modules.reservas.mapper.MesaMapper;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MesaService {

    private final MesaRepository mesaRepository;
    private final RestauranteRepository restauranteRepository;
    private final MesaMapper mesaMapper;
    private final CurrentUser currentUser;

    // Crear nueva mesa (Admin/Dueno)
    @Transactional
    public MesaResponseDTO crearMesa(MesaRequestDTO request) {
        Long restauranteId = currentUser.getRestauranteId();

        Restaurante restaurante = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        // Validar que el restaurante tenga atencion fisica habilitada (SaaS)
        if (Boolean.FALSE.equals(restaurante.getTieneAtencionFisica())) {
            throw new BusinessRuleException(
                    "El restaurante no tiene habilitada la atención física. No se pueden gestionar mesas.");
        }

        // Validar que no exista otra mesa con el mismo numero en el restaurante
        Optional<Mesa> mesaExistente = mesaRepository.findByRestauranteIdAndNumero(restauranteId, request.numero());
        if (mesaExistente.isPresent()) {
            throw new BusinessRuleException(
                    "Ya existe una mesa con el número " + request.numero() + " en este restaurante.");
        }

        Mesa mesa = Mesa.builder()
                .restaurante(restaurante)
                .numero(request.numero())
                .capacidad(request.capacidad())
                .coordenadaX(request.coordenadaX())
                .coordenadaY(request.coordenadaY())
                .activo(true)
                .build();

        Mesa mesaGuardada = mesaRepository.save(mesa);
        return mesaMapper.toResponse(mesaGuardada);
    }

    // Obtener todas las mesas del restaurante (Admin/Dueno - incluye inactivas)
    @Transactional(readOnly = true)
    public List<MesaResponseDTO> obtenerMesasPorRestaurante() {
        Long restauranteId = currentUser.getRestauranteId();
        return mesaRepository.findByRestauranteId(restauranteId)
                .stream()
                .map(mesaMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Obtener solo mesas activas de un restaurante (Cliente - para el plano interactivo)
    @Transactional(readOnly = true)
    public List<MesaResponseDTO> obtenerMesasActivasPorRestaurante(Long restauranteId) {
        restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        return mesaRepository.findByRestauranteIdAndActivoTrue(restauranteId)
                .stream()
                .map(mesaMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Obtener mesa por ID
    @Transactional(readOnly = true)
    public MesaResponseDTO obtenerMesaPorId(Long mesaId) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa con ID " + mesaId + " no encontrada"));
        return mesaMapper.toResponse(mesa);
    }

    // Actualizar mesa (Admin/Dueno)
    @Transactional
    public MesaResponseDTO actualizarMesa(Long mesaId, MesaRequestDTO request) {
        Long restauranteId = currentUser.getRestauranteId();

        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa con ID " + mesaId + " no encontrada"));

        // Verificar que la mesa pertenece al restaurante del usuario
        if (!mesa.getRestaurante().getId().equals(restauranteId)) {
            throw new BusinessRuleException("No tiene permisos para modificar esta mesa.");
        }

        // Validar duplicado de numero (si cambio el numero)
        if (!mesa.getNumero().equals(request.numero())) {
            Optional<Mesa> mesaExistente = mesaRepository.findByRestauranteIdAndNumero(restauranteId, request.numero());
            if (mesaExistente.isPresent()) {
                throw new BusinessRuleException(
                        "Ya existe una mesa con el número " + request.numero() + " en este restaurante.");
            }
        }

        mesa.setNumero(request.numero());
        mesa.setCapacidad(request.capacidad());
        mesa.setCoordenadaX(request.coordenadaX());
        mesa.setCoordenadaY(request.coordenadaY());

        Mesa mesaActualizada = mesaRepository.save(mesa);
        return mesaMapper.toResponse(mesaActualizada);
    }

    // Activar/Desactivar mesa (Admin/Dueno)
    @Transactional
    public MesaResponseDTO cambiarEstadoMesa(Long mesaId, Boolean activo) {
        Long restauranteId = currentUser.getRestauranteId();

        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa con ID " + mesaId + " no encontrada"));

        if (!mesa.getRestaurante().getId().equals(restauranteId)) {
            throw new BusinessRuleException("No tiene permisos para modificar esta mesa.");
        }

        mesa.setActivo(activo);
        Mesa mesaActualizada = mesaRepository.save(mesa);
        return mesaMapper.toResponse(mesaActualizada);
    }
}
