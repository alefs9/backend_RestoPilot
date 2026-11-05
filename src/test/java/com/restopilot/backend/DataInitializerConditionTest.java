package com.restopilot.backend;

import com.restopilot.backend.config.DataInitializer;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataInitializerConditionTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(RestauranteRepository.class, () -> mock(RestauranteRepository.class))
            .withBean(UsuarioRepository.class, () -> mock(UsuarioRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withUserConfiguration(DataInitializer.class);
    @Test void ausenteNoActivaDemo() {
        context.run(ctx -> assertThat(ctx).doesNotHaveBean(DataInitializer.class));
    }
    @Test void falsoNoActivaDemo() {
        context.withPropertyValues("restopilot.demo.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DataInitializer.class));
    }
    @Test void verdaderoActivaDemo() {
        context.withPropertyValues("restopilot.demo.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DataInitializer.class));
    }
}
