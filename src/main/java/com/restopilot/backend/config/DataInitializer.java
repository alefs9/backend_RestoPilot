package com.restopilot.backend.config;

import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RestauranteRepository restauranteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Restaurante restaurante = restauranteRepository.findById(1L).orElseGet(() -> {
            Restaurante nuevo = Restaurante.builder()
                    .nombre("RestoPilot Central")
                    .direccion("Av. Primavera 123, Surco")
                    .telefono("987654321")
                    .horaApertura(LocalTime.of(8, 0))
                    .horaCierre(LocalTime.of(23, 30))
                    .activo(true)
                    .tieneAtencionFisica(true)
                    .aceptaDelivery(true)
                    .build();
            return restauranteRepository.save(nuevo);
        });

        if (!usuarioRepository.existsByCorreo("cristian@restopilot.com")) {
            Usuario admin = Usuario.builder()
                    .nombreCompleto("Cristian Condori")
                    .correo("cristian@restopilot.com")
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .rol(Rol.ADMINISTRADOR)
                    .restaurante(restaurante)
                    .habilitado(true)
                    .build();
            usuarioRepository.save(admin);
        }

        if (!usuarioRepository.existsByCorreo("cliente@restopilot.com")) {
            Usuario cliente = Usuario.builder()
                    .nombreCompleto("Cliente Demo")
                    .correo("cliente@restopilot.com")
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .rol(Rol.CLIENTE)
                    .restaurante(restaurante)
                    .habilitado(true)
                    .build();
            usuarioRepository.save(cliente);
        }
    }
}
