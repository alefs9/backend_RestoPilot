package com.restopilot.backend.modules.restaurante.service;

import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.modules.auth.dto.*;
import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.service.RegistroCuentaService;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AltaRestauranteService {
    private final RestauranteRepository restauranteRepository;
    private final RegistroCuentaService cuentas;
    private final JwtService jwtService;

    /** US21: cuenta y establecimiento constituyen una única operación atómica. */
    @Transactional
    public AuthResponseDTO registrar(@Valid RegisterOwnerRequestDTO request) {
        var datos = request.getRestaurante();
        if (request.getRestauranteId() != null || datos.getId() != null || request.getRol() != null) {
            throw new BusinessRuleException("El dueño solo puede registrar un restaurante nuevo; el rol lo asigna el servidor.");
        }
        String correo = cuentas.validarCorreoDisponible(request.getCorreo());
        if (!datos.getHoraApertura().isBefore(datos.getHoraCierre())) {
            throw new BusinessRuleException("La hora de apertura debe ser anterior a la hora de cierre.");
        }
        Restaurante restaurante = restauranteRepository.saveAndFlush(Restaurante.builder()
                .nombre(datos.getNombre().trim()).direccion(datos.getDireccion().trim()).telefono(datos.getTelefono().trim())
                .horaApertura(datos.getHoraApertura()).horaCierre(datos.getHoraCierre())
                .tieneAtencionFisica(datos.getTieneAtencionFisica()).aceptaDelivery(datos.getAceptaDelivery()).activo(true).build());
        var dueño = cuentas.guardar(request, correo, Rol.DUENO, restaurante);
        return AuthResponseDTO.builder().mensaje("Cuenta de dueño y restaurante creados con éxito.")
                .token(jwtService.generateToken(dueño)).id(dueño.getId()).nombreCompleto(dueño.getNombreCompleto())
                .correo(dueño.getCorreo()).rol(dueño.getRol()).restauranteId(restaurante.getId()).build();
    }
}
