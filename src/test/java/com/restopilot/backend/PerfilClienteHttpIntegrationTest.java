package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** Contrato HTTP y persistencia en H2; no acredita la configuración PostgreSQL/Supabase. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:perfilcliente;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "restopilot.demo.enabled=false",
        "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"
})
class PerfilClienteHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired UsuarioRepository usuarios;
    @Autowired RestauranteRepository restaurantes;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test void clienteEditaSoloNombrePropioYConservaIdentidad() throws Exception {
        Usuario cliente = usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Cliente Inicial")
                .correo("perfil-cliente@restopilot.test").passwordHash(encoder.encode("Prueba123!"))
                .rol(Rol.CLIENTE).habilitado(true).build());
        Usuario otroCliente = usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Otro Cliente")
                .correo("otro-perfil@restopilot.test").passwordHash(encoder.encode("Prueba123!"))
                .rol(Rol.CLIENTE).habilitado(true).build());
        HttpHeaders headers = login(cliente.getCorreo());
        assertEquals(HttpStatus.OK, http.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCode());

        var updated = patch(Map.of("nombreCompleto", "  Nombre Actualizado  ",
                "rol", "DUENO", "restauranteId", 999, "id", otroCliente.getId()), headers);
        assertEquals(200, updated.statusCode());
        JsonNode body = json.readTree(updated.body());
        assertEquals("Nombre Actualizado", body.get("nombreCompleto").asText());
        assertEquals(cliente.getId().longValue(), body.get("id").asLong());
        assertEquals(cliente.getCorreo(), body.get("correo").asText());
        assertEquals("CLIENTE", body.get("rol").asText());
        assertTrue(body.get("restauranteId").isNull());
        Usuario saved = usuarios.findById(cliente.getId()).orElseThrow();
        assertEquals("Nombre Actualizado", saved.getNombreCompleto());
        assertEquals(Rol.CLIENTE, saved.getRol());
        assertNull(saved.getRestaurante());
        assertEquals("Otro Cliente", usuarios.findById(otroCliente.getId()).orElseThrow().getNombreCompleto());

        for (String invalid : new String[]{"   ", "x".repeat(151)}) {
            assertEquals(400, patch(Map.of("nombreCompleto", invalid), headers).statusCode());
        }
        assertEquals("Nombre Actualizado", usuarios.findById(cliente.getId()).orElseThrow().getNombreCompleto());
    }

    @Test void otroRolYVisitanteNoPuedenEditarPerfilCliente() throws Exception {
        Restaurante restaurante = restaurantes.saveAndFlush(Restaurante.builder().nombre("Perfil local").build());
        Usuario administrador = usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Administrador")
                .correo("perfil-admin@restopilot.test").passwordHash(encoder.encode("Prueba123!"))
                .rol(Rol.ADMINISTRADOR).restaurante(restaurante).habilitado(true).build());
        var payload = Map.of("nombreCompleto", "Nombre indebido");
        assertEquals(403, patch(payload, login(administrador.getCorreo())).statusCode());
        assertEquals(403, patch(payload, new HttpHeaders()).statusCode());
        assertEquals("Administrador", usuarios.findById(administrador.getId()).orElseThrow().getNombreCompleto());
    }

    private HttpHeaders login(String correo) {
        var response = http.postForEntity("/api/auth/login",
                Map.of("correo", correo, "password", "Prueba123!"), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getBody().get("token").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpResponse<String> patch(Map<String, ?> payload, HttpHeaders headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        if (headers.getFirst(HttpHeaders.AUTHORIZATION) != null)
            request.header("Authorization", headers.getFirst(HttpHeaders.AUTHORIZATION));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
