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
 "spring.datasource.url=jdbc:h2:mem:us19http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
 "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
class Us19HttpIntegrationTest {
 @Autowired com.restopilot.backend.modules.pedidos.repository.PedidoRepository pedidos;
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

 ResponseEntity<JsonNode> patch(Fixture f,String target,HttpHeaders headers){
  return http.exchange("/api/pedidos/"+f.id()+"/estado?nuevoEstado="+target,HttpMethod.PATCH,new HttpEntity<>(headers),JsonNode.class);
 }
 @Test void secuenciaCompletaActualizaColaSinModificarCompraPagoYNotificaUnaVezPorEtapa(){
  var f=fixture();var initial=order(f);
  for(String state:List.of("EN_PREPARACION","LISTO","ENTREGADO")){
   var response=patch(f,state,f.admin());assertEquals(HttpStatus.OK,response.getStatusCode());assertEquals(state,response.getBody().get("estado").asText());
   var current=order(f);assertEquals(initial.get("detalles"),current.get("detalles"));assertEquals(initial.get("total"),current.get("total"));
   assertEquals("PENDIENTE",get("/api/pedidos/"+f.id()+"/pagos",f.own()).getBody().get("estado").asText());
   var queue=get("/api/pedidos/pendientes",f.admin()).getBody().get("content");
   boolean present=false;for(var item:queue)if(item.get("id").asLong()==f.id())present=true;
   assertEquals(state.equals("EN_PREPARACION"),present);
  }
  assertEquals(3,get("/api/notificaciones/me",f.own()).getBody().get("totalElements").asInt());
  assertEquals(0,get("/api/notificaciones/me",f.other()).getBody().get("totalElements").asInt());
 }
 @Test void todaEtapaOmitidaRepetidaRetrocesoYEstadoTerminalEsRechazadaSinNotificar(){
  var f=fixture();
  var states=com.restopilot.backend.modules.pedidos.entity.EstadoPedido.values();
  for(var from:states)for(var to:states){
   boolean valid=(from.name().equals("PENDIENTE")&&to.name().equals("EN_PREPARACION"))||
    (from.name().equals("EN_PREPARACION")&&to.name().equals("LISTO"))||(from.name().equals("LISTO")&&to.name().equals("ENTREGADO"));
   if(valid)continue;
   var p=pedidos.findById(f.id()).orElseThrow();p.setEstado(from);pedidos.saveAndFlush(p);
   assertEquals(HttpStatus.BAD_REQUEST,patch(f,to.name(),f.admin()).getStatusCode(),from+" -> "+to);
   assertEquals(from.name(),order(f).get("estado").asText());
  }
  assertEquals(0,get("/api/notificaciones/me",f.own()).getBody().get("totalElements").asInt());
 }
 @Test void permisosPedidoAjenoInexistenteYDosConfirmacionesConcurrentes(){
  var f=fixture();
  var otherLocal=restaurantes.saveAndFlush(Restaurante.builder().nombre("Otro local US19").build());
  String mail=UUID.randomUUID()+"@other-us19.test";
  usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Otro operador").correo(mail).passwordHash(encoder.encode("Prueba123!"))
   .rol(Rol.ADMINISTRADOR).restaurante(otherLocal).habilitado(true).build());
  assertEquals(HttpStatus.FORBIDDEN,patch(f,"EN_PREPARACION",login(mail)).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,patch(f,"EN_PREPARACION",f.own()).getStatusCode());
  var missing=http.exchange("/api/pedidos/999999999/estado?nuevoEstado=EN_PREPARACION",HttpMethod.PATCH,new HttpEntity<>(f.admin()),JsonNode.class);
  assertEquals(HttpStatus.NOT_FOUND,missing.getStatusCode());assertEquals("PENDIENTE",order(f).get("estado").asText());
  var gate=new java.util.concurrent.CountDownLatch(1);
  java.util.function.Supplier<HttpStatusCode> send=()->{try{gate.await();return patch(f,"EN_PREPARACION",f.admin()).getStatusCode();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}};
  var first=java.util.concurrent.CompletableFuture.supplyAsync(send);var second=java.util.concurrent.CompletableFuture.supplyAsync(send);gate.countDown();
  var statuses=List.of(first.join(),second.join());assertEquals(1,statuses.stream().filter(HttpStatus.OK::equals).count());assertEquals(1,statuses.stream().filter(HttpStatus.BAD_REQUEST::equals).count());
  assertEquals("EN_PREPARACION",order(f).get("estado").asText());assertEquals(1,get("/api/notificaciones/me",f.own()).getBody().get("totalElements").asInt());
 }
}
