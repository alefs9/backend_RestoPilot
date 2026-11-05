package com.restopilot.backend.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

/** Contrato de los endpoints HTTP existentes. IA queda fuera del Sprint 2. */
@Configuration
public class OpenApiConfig {
    @Bean OpenAPI restopilotApi() {
        return new OpenAPI().info(new Info().title("RestoPilot API").version("1.0.0")
            .description("Sprint 2 sin IA. Autenticación JWT y operaciones por restaurante."))
            .components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
    @Bean OpenApiCustomizer publicAuthOperations() {
        return api -> {
            if(api.getPaths()==null)return;
            api.getPaths().entrySet().removeIf(e->e.getKey().startsWith("/api/ia"));
            api.getPaths().forEach((path,item)->{if(path.startsWith("/api/auth/"))
                item.readOperations().forEach(op->op.setSecurity(java.util.List.of()));});
        };
    }
}
