package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.auth.service.RegistroCuentaService;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:altas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class AltaPersonalIntegrationTest {
    static class FallosRegistro { boolean cuenta; boolean token; }

    @TestConfiguration
    static class FallosControlados {
        @Bean FallosRegistro fallosRegistro() { return new FallosRegistro(); }
        @Bean @Primary RegistroCuentaService cuentasPrueba(UsuarioRepository repository,
                PasswordEncoder encoder, FallosRegistro fallos) {
            return new RegistroCuentaService(repository, encoder) {
                @Override public Usuario guardar(com.restopilot.backend.modules.auth.dto.CuentaRequestDTO datos,
                        String correo, Rol rol, com.restopilot.backend.modules.restaurante.entity.Restaurante restaurante) {
                    if (fallos.cuenta) throw new IllegalStateException("Fallo simulado al crear la cuenta");
                    return super.guardar(datos, correo, rol, restaurante);
                }
            };
        }
        @Bean @Primary JwtService jwtPrueba(FallosRegistro fallos) {
            return new JwtService() {
                @Override public String generateToken(Usuario usuario) {
                    if (fallos.token) throw new IllegalStateException("Fallo simulado de token");
                    return super.generateToken(usuario);
                }
            };
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired RestauranteRepository restaurantes;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired FallosRegistro fallos;
    final String password = "Password123!";

    @AfterEach void limpiarSesion() {
        SecurityContextHolder.clearContext(); fallos.cuenta = false; fallos.token = false;
    }

    Map<String, Object> solicitudDueno(String correo) {
        Map<String, Object> local = new HashMap<>();
        local.put("nombre", "Restaurante del dueño"); local.put("direccion", "Av. Lima 123"); local.put("telefono", "987654321");
        local.put("horaApertura", "09:00"); local.put("horaCierre", "22:00");
        local.put("tieneAtencionFisica", true); local.put("aceptaDelivery", true);
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombreCompleto", "Dueño de prueba"); datos.put("correo", correo); datos.put("password", password);
        datos.put("restaurante", local); return datos;
    }
    Map<String, Object> solicitudAdmin(String correo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombreCompleto", "Administrador de prueba"); datos.put("correo", correo); datos.put("password", password); return datos;
    }
    JsonNode registrarDueno(String correo) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register-owner").contentType("application/json")
                .content(json.writeValueAsString(solicitudDueno(correo)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    String registrarAdmin(String token, String correo) throws Exception {
        return mvc.perform(post("/api/usuarios/administradores").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(solicitudAdmin(correo))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
    }

    @Test void altaDuenoCreaCuentaRestauranteYTokenConDatosAsignadosPorServidor() throws Exception {
        long locales = restaurantes.count(), cuentasAntes = usuarios.count();
        JsonNode respuesta = registrarDueno("OWNER@Test.com");
        Usuario dueno = usuarios.findByCorreo("owner@test.com").orElseThrow();
        assertEquals(locales + 1, restaurantes.count()); assertEquals(cuentasAntes + 1, usuarios.count());
        assertEquals(Rol.DUENO, dueno.getRol());
        assertTrue(passwords.matches(password, dueno.getPasswordHash()));
        assertNotEquals(password, dueno.getPasswordHash());
        assertEquals(dueno.getRestaurante().getId(), respuesta.get("restauranteId").asLong());
        assertEquals("owner@test.com", jwt.extractCorreo(respuesta.get("token").asText()));
        assertEquals("DUENO", jwt.extractClaim(respuesta.get("token").asText(), c -> c.get("rol", String.class)));
    }

    @Test void correoDuplicadoNoCreaRestauranteNiModificaCuentaExistente() throws Exception {
        JsonNode owner = registrarDueno("owner@test.com"); long locales = restaurantes.count(), cuentasAntes = usuarios.count();
        mvc.perform(post("/api/auth/register-owner").contentType("application/json").content(json.writeValueAsString(solicitudDueno("OWNER@test.com"))))
                .andExpect(status().isConflict());
        assertEquals(locales, restaurantes.count()); assertEquals(cuentasAntes, usuarios.count());
        assertEquals(owner.get("restauranteId").asLong(), usuarios.findByCorreo("owner@test.com").orElseThrow().getRestaurante().getId());
    }

    @Test void datosObligatoriosEmailYPasswordInvalidosNoCreanRegistros() throws Exception {
        long locales = restaurantes.count(), cuentasAntes = usuarios.count();
        List<Map<String, Object>> invalidos = new ArrayList<>();
        invalidos.add(new HashMap<>());
        var sinRestaurante = solicitudDueno("owner@test.com"); sinRestaurante.remove("restaurante"); invalidos.add(sinRestaurante);
        var email = solicitudDueno("correo-invalido"); invalidos.add(email);
        var clave = solicitudDueno("owner@test.com"); clave.put("password", "123"); invalidos.add(clave);
        var nombre = solicitudDueno("owner@test.com"); nombre.put("nombreCompleto", " "); invalidos.add(nombre);
        for (var datos : invalidos) mvc.perform(post("/api/auth/register-owner").contentType("application/json").content(json.writeValueAsString(datos)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalles").isArray());
        assertEquals(locales, restaurantes.count()); assertEquals(cuentasAntes, usuarios.count());
    }

    @Test void horarioIgualOInvertidoEsRechazadoAntesDeGuardar() throws Exception {
        long locales = restaurantes.count();
        for (String cierre : List.of("09:00", "08:00")) {
            var datos = solicitudDueno("owner@test.com");
            @SuppressWarnings("unchecked") var local = (Map<String, Object>) datos.get("restaurante"); local.put("horaCierre", cierre);
            mvc.perform(post("/api/auth/register-owner").contentType("application/json").content(json.writeValueAsString(datos))).andExpect(status().isBadRequest());
        }
        assertEquals(locales, restaurantes.count());
    }

    @Test void noPermiteApropiarseDeRestauranteExistenteNiElegirRol() throws Exception {
        long locales = restaurantes.count();
        for (String campo : List.of("restauranteId", "rol", "idAnidado")) {
            var datos = solicitudDueno("owner@test.com");
            if (campo.equals("idAnidado")) {
                @SuppressWarnings("unchecked") var local = (Map<String, Object>) datos.get("restaurante"); local.put("id", 1);
            } else datos.put(campo, campo.equals("rol") ? "ADMINISTRADOR" : 1);
            mvc.perform(post("/api/auth/register-owner").contentType("application/json").content(json.writeValueAsString(datos))).andExpect(status().isBadRequest());
        }
        assertEquals(locales, restaurantes.count()); assertFalse(usuarios.existsByCorreo("owner@test.com"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void falloDespuesDeCrearRestauranteRevierteLaTransaccionReal() throws Exception {
        long locales = restaurantes.count(), cuentasAntes = usuarios.count();
        fallos.cuenta = true;
        mvc.perform(post("/api/auth/register-owner").contentType("application/json").content(json.writeValueAsString(solicitudDueno("rollback@test.com"))))
                .andExpect(status().isInternalServerError());
        assertEquals(locales, restaurantes.count()); assertEquals(cuentasAntes, usuarios.count());
        assertFalse(usuarios.existsByCorreo("rollback@test.com"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void falloAlGenerarTokenRevierteCuentaYRestauranteYaPersistidos() throws Exception {
        long locales = restaurantes.count(), cuentasAntes = usuarios.count();
        fallos.token = true;
        mvc.perform(post("/api/auth/register-owner").contentType("application/json").content(json.writeValueAsString(solicitudDueno("token-error@test.com"))))
                .andExpect(status().isInternalServerError());
        assertEquals(locales, restaurantes.count()); assertEquals(cuentasAntes, usuarios.count());
        assertFalse(usuarios.existsByCorreo("token-error@test.com"));
    }

    @Test void duenoCreaAdministradorConPasswordCifradoYSinEntregarSuToken() throws Exception {
        JsonNode owner = registrarDueno("owner@test.com");
        JsonNode admin = json.readTree(registrarAdmin(owner.get("token").asText(), "ADMIN@Test.com"));
        Usuario empleado = usuarios.findByCorreo("admin@test.com").orElseThrow();
        assertEquals(Rol.ADMINISTRADOR, empleado.getRol()); assertTrue(passwords.matches(password, empleado.getPasswordHash()));
        assertEquals(owner.get("restauranteId").asLong(), empleado.getRestaurante().getId());
        assertEquals(owner.get("restauranteId").asLong(), admin.get("restauranteId").asLong());
    }

    @Test void clienteAdministradorYAnonimoNoPuedenDarAltaDePersonal() throws Exception {
        for (String correo : List.of("cliente@restopilot.com", "cristian@restopilot.com")) {
            String token = jwt.generateToken(usuarios.findByCorreo(correo).orElseThrow());
            mvc.perform(post("/api/usuarios/administradores").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(json.writeValueAsString(solicitudAdmin("nuevo@test.com"))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/usuarios/administradores").contentType("application/json").content(json.writeValueAsString(solicitudAdmin("nuevo@test.com"))))
                .andExpect(status().isForbidden());
        assertFalse(usuarios.existsByCorreo("nuevo@test.com"));
    }

    @Test void duenoNoPuedeIndicarOtroRestauranteNiOtroRol() throws Exception {
        JsonNode owner = registrarDueno("owner@test.com"), otro = registrarDueno("otro@test.com");
        for (String campo : List.of("restauranteId", "rol")) {
            var datos = solicitudAdmin("admin@test.com"); datos.put(campo, campo.equals("rol") ? "DUENO" : otro.get("restauranteId").asLong());
            mvc.perform(post("/api/usuarios/administradores").header("Authorization", "Bearer " + owner.get("token").asText())
                    .contentType("application/json").content(json.writeValueAsString(datos))).andExpect(status().isBadRequest());
        }
        assertFalse(usuarios.existsByCorreo("admin@test.com"));
    }

    @Test void duplicadoDeAdministradorNoCambiaUnaCuentaExistente() throws Exception {
        JsonNode owner = registrarDueno("owner@test.com");
        long cuentasAntes = usuarios.count();
        mvc.perform(post("/api/usuarios/administradores").header("Authorization", "Bearer " + owner.get("token").asText())
                .contentType("application/json").content(json.writeValueAsString(solicitudAdmin("OWNER@test.com"))))
                .andExpect(status().isConflict());
        assertEquals(cuentasAntes, usuarios.count()); assertEquals(Rol.DUENO, usuarios.findByCorreo("owner@test.com").orElseThrow().getRol());
    }

    @Test void datosDeAdministradorInvalidosNoCreanCuenta() throws Exception {
        JsonNode owner = registrarDueno("owner@test.com"); long cuentasAntes = usuarios.count();
        mvc.perform(post("/api/usuarios/administradores").header("Authorization", "Bearer " + owner.get("token").asText())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalles").isArray());
        assertEquals(cuentasAntes, usuarios.count());
    }

    @Test void administradorIniciaSesionYGestionaSoloSuRestaurante() throws Exception {
        JsonNode owner = registrarDueno("owner@test.com"), otro = registrarDueno("otro@test.com");
        registrarAdmin(owner.get("token").asText(), "admin@test.com");
        JsonNode login = json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("correo", "admin@test.com", "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(owner.get("restauranteId").asLong(), login.get("restauranteId").asLong());
        for (JsonNode local : List.of(owner, otro)) {
            var resultado = mvc.perform(post("/api/categorias").header("Authorization", "Bearer " + login.get("token").asText())
                    .contentType("application/json").content(json.writeValueAsString(Map.of("restauranteId", local.get("restauranteId").asLong(), "nombre", "Carta"))));
            resultado.andExpect(local == owner ? status().isCreated() : status().isForbidden());
        }
    }

    @Test void duenoSinRestauranteNoPuedeIncorporarPersonal() throws Exception {
        Usuario owner = usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Sin negocio").correo("sin@test.com")
                .passwordHash(passwords.encode(password)).rol(Rol.DUENO).build());
        mvc.perform(post("/api/usuarios/administradores").header("Authorization", "Bearer " + jwt.generateToken(owner))
                .contentType("application/json").content(json.writeValueAsString(solicitudAdmin("admin@test.com"))))
                .andExpect(status().isForbidden());
    }
}
