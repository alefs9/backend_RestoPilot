package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.*;
import com.restopilot.backend.modules.catalogo.repository.*;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.modules.reservas.entity.Mesa;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP y JWT reales sobre H2 aislado. No usa ni certifica la base PostgreSQL del equipo. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:us11http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
    "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
class Us11HttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired RestauranteRepository restaurantes;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired PlatoRepository platos;
    @Autowired MesaRepository mesas;
    @Autowired PasswordEncoder encoder;

    @Test void loginCatalogoCuatroModalidadesYRechazosConContratoReal() {
        var r=restaurantes.saveAndFlush(Restaurante.builder().nombre("Local de prueba HTTP").build());
        var cliente=usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Cliente HTTP").correo("cliente@us11.test")
                .passwordHash(encoder.encode("Prueba123!" )).rol(Rol.CLIENTE).habilitado(true).build());
        var categoria=new Categoria();categoria.setRestauranteId(r.getId());categoria.setNombre("Principal");
        categoria=categorias.saveAndFlush(categoria);
        var plato=new Plato();plato.setRestauranteId(r.getId());plato.setCategoria(categoria);plato.setNombre("Plato histórico HTTP");
        plato.setPrecio(new BigDecimal("12.50"));plato=platos.saveAndFlush(plato);
        var mesa=mesas.saveAndFlush(Mesa.builder().restaurante(r).numero(4).capacidad(4).coordenadaX(0d).coordenadaY(0d).build());

        var login=http.postForEntity("/api/auth/login",Map.of("correo",cliente.getCorreo(),"password","Prueba123!"),JsonNode.class);
        assertEquals(HttpStatus.OK,login.getStatusCode());
        var headers=new HttpHeaders();headers.setBearerAuth(login.getBody().get("token").asText());headers.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(HttpStatus.FORBIDDEN,http.getForEntity("/api/restaurantes",String.class).getStatusCode());
        var lista=http.exchange("/api/restaurantes",HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
        assertEquals(HttpStatus.OK,lista.getStatusCode());assertTrue(lista.getBody().isArray());
        assertTrue(lista.getBody().get(0).has("abierto"));
        assertEquals(HttpStatus.OK,http.exchange("/api/restaurantes/"+r.getId(),HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,http.exchange("/api/restaurantes/999999",HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode());
        assertEquals(HttpStatus.OK,http.exchange("/api/platos/restaurante/"+r.getId(),HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode());
        assertEquals(HttpStatus.OK,http.exchange("/api/mesas/restaurante/"+r.getId(),HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode());

        for(String modalidad:List.of("SALON","LLEVAR","RECOGER","DELIVERY")) {
            Map<String,Object> body=new LinkedHashMap<>();body.put("restauranteId",r.getId());body.put("tipoEntrega",modalidad);
            body.put("mesaId",modalidad.equals("SALON")?mesa.getId():null);
            body.put("direccionEntrega",modalidad.equals("DELIVERY")?"Avenida de prueba 123":null);
            body.put("detalles",List.of(Map.of("platoId",plato.getId(),"cantidad",2)));
            var creado=http.exchange("/api/pedidos",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
            assertEquals(HttpStatus.CREATED,creado.getStatusCode(),()->String.valueOf(creado.getBody()));
            var pedido=creado.getBody();assertEquals("PENDIENTE",pedido.get("estado").asText());
            assertEquals(0,new BigDecimal("25.00").compareTo(pedido.get("total").decimalValue()));
            assertEquals(cliente.getId().longValue(),pedido.get("clienteId").asLong());
            assertEquals("Plato histórico HTTP",pedido.get("detalles").get(0).get("nombrePlatoSnapshot").asText());
            var pago=http.exchange("/api/pedidos/"+pedido.get("id").asLong()+"/pagos",HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
            assertEquals(HttpStatus.OK,pago.getStatusCode());assertEquals("PENDIENTE",pago.getBody().get("estado").asText());
        }
        var invalido=Map.of("restauranteId",r.getId(),"tipoEntrega","SALON","detalles",List.of(Map.of("platoId",plato.getId(),"cantidad",1)));
        assertEquals(HttpStatus.BAD_REQUEST,http.exchange("/api/pedidos",HttpMethod.POST,new HttpEntity<>(invalido,headers),String.class).getStatusCode());
        plato.setDisponible(false);platos.saveAndFlush(plato);
        var agotado=Map.of("restauranteId",r.getId(),"tipoEntrega","LLEVAR","detalles",List.of(Map.of("platoId",plato.getId(),"cantidad",1)));
        assertEquals(HttpStatus.BAD_REQUEST,http.exchange("/api/pedidos",HttpMethod.POST,new HttpEntity<>(agotado,headers),String.class).getStatusCode());
    }
}
