package com.restopilot.backend.modules.auth.service;

import com.restopilot.backend.config.JwtService;
import com.restopilot.backend.modules.auth.dto.AuthResponseDTO;
import com.restopilot.backend.modules.auth.dto.LoginRequestDTO;
import com.restopilot.backend.modules.auth.dto.RegisterRequestDTO;
import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RestauranteRepository restauranteRepository;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RestauranteRepository restauranteRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.restauranteRepository = restauranteRepository;
    }
    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {

        Restaurante restaurante = null;
        if (request.getRestauranteId() != null) {
            restaurante = restauranteRepository.findById(request.getRestauranteId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "El restaurante especificado no existe."
                    ));
        }

        String correoLimpio = request.getCorreo().trim().toLowerCase();

        if (usuarioRepository.existsByCorreo(correoLimpio)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo electrónico ya se encuentra registrado."
            );
        }

        Rol rolAsignado = (request.getRol() != null) ? request.getRol() : Rol.CLIENTE;

        Usuario usuario = Usuario.builder()
                .nombreCompleto(request.getNombreCompleto().trim())
                .correo(correoLimpio)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(rolAsignado)
                .restaurante(restaurante)
                .habilitado(true)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);
        String token = jwtService.generateToken(guardado);

        return AuthResponseDTO.builder()
                .mensaje("Cuenta creada con éxito.")
                .token(token)
                .id(guardado.getId())
                .nombreCompleto(guardado.getNombreCompleto())
                .correo(guardado.getCorreo())
                .rol(guardado.getRol())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO request) {
        String correoLimpio = request.getCorreo().trim().toLowerCase();

        Usuario usuario = usuarioRepository.findByCorreo(correoLimpio)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Correo o contraseña incorrectos."
                ));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Correo o contraseña incorrectos."
            );
        }

        if (!usuario.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La cuenta se encuentra inactiva o deshabilitada."
            );
        }

        String token = jwtService.generateToken(usuario);

        return AuthResponseDTO.builder()
                .mensaje("Inicio de sesión exitoso.")
                .token(token)
                .id(usuario.getId())
                .nombreCompleto(usuario.getNombreCompleto())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .build();
    }
}