package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.*;
import com.restopilot.backend.modules.catalogo.repository.*;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Consulta repetida del estado con HTTP/JWT reales y H2 aislado, sin datos del equipo. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:us13http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
 "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
class Us13HttpIntegrationTest {
 @org.junit.jupiter.api.BeforeEach void configurarClienteHttp() {
  // HttpURLConnection no admite PATCH; usar el cliente JDK sin añadir dependencias.
  http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
 }
 @Autowired TestRestTemplate http;
 @Autowired RestauranteRepository restaurantes;
 @Autowired UsuarioRepository usuarios;
 @Autowired CategoriaRepository categorias;
 @Autowired PlatoRepository platos;
 @Autowired PasswordEncoder encoder;
 record Fixture(Long id,Long clienteId,HttpHeaders own,HttpHeaders other,HttpHeaders admin){}
 HttpHeaders login(String correo){
  var result=http.postForEntity("/api/auth/login",Map.of("correo",correo,"password","Prueba123!"),JsonNode.class);
  assertEquals(HttpStatus.OK,result.getStatusCode());var headers=new HttpHeaders();
  headers.setBearerAuth(result.getBody().get("token").asText());headers.setContentType(MediaType.APPLICATION_JSON);return headers;
 }
 Fixture fixture(){
  String suffix=UUID.randomUUID().toString();var local=restaurantes.saveAndFlush(Restaurante.builder().nombre("Local US13").build());
  var owner=usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Cliente US13").correo("own-"+suffix+"@us13.test")
   .passwordHash(encoder.encode("Prueba123!" )).rol(Rol.CLIENTE).habilitado(true).build());
  var other=usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Otra cuenta").correo("other-"+suffix+"@us13.test")
   .passwordHash(encoder.encode("Prueba123!" )).rol(Rol.CLIENTE).habilitado(true).build());
  var admin=usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Operación US13").correo("admin-"+suffix+"@us13.test")
   .passwordHash(encoder.encode("Prueba123!" )).rol(Rol.ADMINISTRADOR).restaurante(local).habilitado(true).build());
  var cat=new Categoria();cat.setRestauranteId(local.getId());cat.setNombre("Principal");cat=categorias.saveAndFlush(cat);
  var dish=new Plato();dish.setRestauranteId(local.getId());dish.setCategoria(cat);dish.setNombre("Snapshot US13");dish.setPrecio(new BigDecimal("12.50"));dish=platos.saveAndFlush(dish);
  var ownHeaders=login(owner.getCorreo());var otherHeaders=login(other.getCorreo());var adminHeaders=login(admin.getCorreo());
  var request=Map.of("restauranteId",local.getId(),"tipoEntrega","LLEVAR","detalles",List.of(Map.of("platoId",dish.getId(),"cantidad",2)));
  var created=http.exchange("/api/pedidos",HttpMethod.POST,new HttpEntity<>(request,ownHeaders),JsonNode.class);
  assertEquals(HttpStatus.CREATED,created.getStatusCode());return new Fixture(created.getBody().get("id").asLong(),owner.getId(),ownHeaders,otherHeaders,adminHeaders);
 }
 ResponseEntity<JsonNode> get(String route,HttpHeaders headers){return http.exchange(route,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);}
 JsonNode order(Fixture f){var response=get("/api/pedidos/"+f.id(),f.own());assertEquals(HttpStatus.OK,response.getStatusCode());return response.getBody();}
 void transition(Fixture f,String next){
  var result=http.exchange("/api/pedidos/"+f.id()+"/estado?nuevoEstado="+next,HttpMethod.PATCH,new HttpEntity<>(f.admin()),JsonNode.class);
  assertEquals(HttpStatus.OK,result.getStatusCode());
 }
 @Test void nuevasConsultasReflejanPreparacionListoYEntregaSinCambiarCompraNiPago(){
  var f=fixture();var initial=order(f);assertEquals("PENDIENTE",initial.get("estado").asText());
  for(String state:List.of("EN_PREPARACION","LISTO","ENTREGADO")){
   transition(f,state);var current=order(f);assertEquals(state,current.get("estado").asText());
   assertEquals(initial.get("id"),current.get("id"));assertEquals(initial.get("detalles"),current.get("detalles"));assertEquals(initial.get("total"),current.get("total"));
   assertEquals("PENDIENTE",get("/api/pedidos/"+f.id()+"/pagos",f.own()).getBody().get("estado").asText());
  }
  assertEquals("ENTREGADO",order(f).get("estado").asText());
  var notifications=get("/api/notificaciones/me",f.own());assertEquals(HttpStatus.OK,notifications.getStatusCode());assertEquals(3,notifications.getBody().get("totalElements").asInt());
  for(JsonNode notice:notifications.getBody().get("content")){assertEquals(f.clienteId().longValue(),notice.get("usuarioId").asLong());assertTrue(notice.get("mensaje").asText().contains("#"+f.id()));}
  assertEquals(0,get("/api/notificaciones/me",f.other()).getBody().get("totalElements").asInt());
 }
 @Test void canceladoConservaDetallesYPagoYNoSeReiniciaAlConsultar(){
  var f=fixture();var initial=order(f);
  var cancelled=http.exchange("/api/pedidos/"+f.id(),HttpMethod.DELETE,new HttpEntity<>(f.admin()),Void.class);assertEquals(HttpStatus.NO_CONTENT,cancelled.getStatusCode());
  for(int i=0;i<2;i++){var current=order(f);assertEquals("CANCELADO",current.get("estado").asText());assertEquals(initial.get("detalles"),current.get("detalles"));assertEquals(initial.get("total"),current.get("total"));}
  assertEquals("PENDIENTE",get("/api/pedidos/"+f.id()+"/pagos",f.own()).getBody().get("estado").asText());
 }
 @Test void clienteNoPuedeAvanzarEstadosNiConsultarSeguimientoAjeno(){
  var f=fixture();
  var rejected=http.exchange("/api/pedidos/"+f.id()+"/estado?nuevoEstado=EN_PREPARACION",HttpMethod.PATCH,new HttpEntity<>(f.own()),String.class);
  assertEquals(HttpStatus.FORBIDDEN,rejected.getStatusCode());assertEquals("PENDIENTE",order(f).get("estado").asText());
  transition(f,"EN_PREPARACION");
  var foreign=get("/api/pedidos/"+f.id(),f.other());var missing=get("/api/pedidos/999999999",f.other());
  assertEquals(HttpStatus.NOT_FOUND,foreign.getStatusCode());assertEquals(HttpStatus.NOT_FOUND,missing.getStatusCode());
  assertEquals(missing.getBody().get("mensaje").asText(),foreign.getBody().get("mensaje").asText());assertFalse(foreign.getBody().has("estado"));assertFalse(foreign.getBody().has("detalles"));
 }
}
