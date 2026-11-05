package com.restopilot.backend;

import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP real y commits independientes. H2: no certifica PostgreSQL ni concurrencia. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:jwtaccess;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "restopilot.demo.enabled=false",
        "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"
})
class JwtAccessIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired UsuarioRepository usuarios;
    @Autowired RestauranteRepository restaurantes;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void tokenPrevioSeDeniegaTrasDeshabilitarCadaRol() {
        var tx = new TransactionTemplate(transactionManager);
        Restaurante local = restaurantes.saveAndFlush(Restaurante.builder().nombre("JWT audit").build());
        for (Rol rol : Rol.values()) {
            String correo = rol.name().toLowerCase() + "@jwt.test";
            Usuario cuenta = usuarios.saveAndFlush(Usuario.builder().nombreCompleto("JWT test").correo(correo)
                    .passwordHash(encoder.encode("Prueba123!")) .rol(rol)
                    .restaurante(rol == Rol.CLIENTE ? null : local).habilitado(true).build());
            var login = http.postForEntity("/api/auth/login", Map.of("correo", correo, "password", "Prueba123!"), JsonNode.class);
            assertEquals(HttpStatus.OK, login.getStatusCode());
            String token = login.getBody().get("token").asText();
            HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
            String ruta = rol == Rol.CLIENTE ? "/api/pedidos/me" :
                    "/api/reportes/operativos?fechaInicio=2026-10-07&fechaFin=2026-10-07";
            assertEquals(HttpStatus.OK, http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());
            tx.executeWithoutResult(status -> {
                Usuario persistido = usuarios.findById(cuenta.getId()).orElseThrow();
                persistido.setHabilitado(false); usuarios.saveAndFlush(persistido);
            });
            assertEquals(Boolean.FALSE, tx.execute(status -> usuarios.findById(cuenta.getId()).orElseThrow().isEnabled()));
            assertEquals(HttpStatus.FORBIDDEN, http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());
            headers.setBearerAuth("token-invalido");
            assertEquals(HttpStatus.FORBIDDEN, http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());
        }
    }
}
