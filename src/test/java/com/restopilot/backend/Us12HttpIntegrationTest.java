package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.*;
import com.restopilot.backend.modules.catalogo.repository.*;
import com.restopilot.backend.modules.pagos.entity.*;
import com.restopilot.backend.modules.pagos.repository.PagoRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato HTTP real con JWT y H2 aislado; no accede a PostgreSQL del equipo. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:us12http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
 "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
class Us12HttpIntegrationTest {
 @Autowired TestRestTemplate http;
 @Autowired RestauranteRepository restaurantes;
 @Autowired UsuarioRepository usuarios;
 @Autowired CategoriaRepository categorias;
 @Autowired PlatoRepository platos;
 @Autowired PagoRepository pagos;
 @Autowired PasswordEncoder encoder;
 record Fixture(Long pedidoId,Long platoId,Long clienteId,HttpHeaders own,HttpHeaders other){}

 HttpHeaders login(String correo){
  var response=http.postForEntity("/api/auth/login",Map.of("correo",correo,"password","Prueba123!"),JsonNode.class);
  assertEquals(HttpStatus.OK,response.getStatusCode());
  var headers=new HttpHeaders();headers.setBearerAuth(response.getBody().get("token").asText());headers.setContentType(MediaType.APPLICATION_JSON);return headers;
 }
 Fixture fixture(){
  String suffix=UUID.randomUUID().toString();
  var restaurante=restaurantes.saveAndFlush(Restaurante.builder().nombre("Restaurante US12").build());
  var owner=usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Cliente US12").correo("own-"+suffix+"@us12.test")
   .passwordHash(encoder.encode("Prueba123!" )).rol(Rol.CLIENTE).habilitado(true).build());
  var other=usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Otra cuenta").correo("other-"+suffix+"@us12.test")
   .passwordHash(encoder.encode("Prueba123!" )).rol(Rol.CLIENTE).habilitado(true).build());
  var cat=new Categoria();cat.setRestauranteId(restaurante.getId());cat.setNombre("Principal");cat=categorias.saveAndFlush(cat);
  var dish=new Plato();dish.setRestauranteId(restaurante.getId());dish.setCategoria(cat);dish.setNombre("Nombre original US12");dish.setPrecio(new BigDecimal("12.50"));dish=platos.saveAndFlush(dish);
  var ownHeaders=login(owner.getCorreo());var otherHeaders=login(other.getCorreo());
  var request=Map.of("restauranteId",restaurante.getId(),"tipoEntrega","DELIVERY","direccionEntrega","Calle de prueba 123",
    "detalles",List.of(Map.of("platoId",dish.getId(),"cantidad",2)));
  var created=http.exchange("/api/pedidos",HttpMethod.POST,new HttpEntity<>(request,ownHeaders),JsonNode.class);
  assertEquals(HttpStatus.CREATED,created.getStatusCode());
  return new Fixture(created.getBody().get("id").asLong(),dish.getId(),owner.getId(),ownHeaders,otherHeaders);
 }
 ResponseEntity<JsonNode> get(String path,HttpHeaders headers){return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);}

 @Test void conservaCompraHistoricaYListadoPropioConPrecioDeCartaModificado(){
  var f=fixture();var dish=platos.findById(f.platoId()).orElseThrow();dish.setNombre("Nombre actual distinto");dish.setPrecio(new BigDecimal("99.00"));platos.saveAndFlush(dish);
  var response=get("/api/pedidos/"+f.pedidoId(),f.own());assertEquals(HttpStatus.OK,response.getStatusCode());var order=response.getBody();
  assertEquals("Nombre original US12",order.get("detalles").get(0).get("nombrePlatoSnapshot").asText());
  assertEquals(0,new BigDecimal("12.50").compareTo(order.get("detalles").get(0).get("precioUnitario").decimalValue()));
  assertEquals(0,new BigDecimal("25.00").compareTo(order.get("total").decimalValue()));assertEquals("Calle de prueba 123",order.get("direccionEntrega").asText());
  var list=get("/api/pedidos/me?page=0&size=1&sort=fechaCreacion,desc",f.own());assertEquals(HttpStatus.OK,list.getStatusCode());
  assertTrue(list.getBody().has("pedidos"));assertFalse(list.getBody().has("content"));assertEquals(1,list.getBody().get("totalElementos").asInt());
  assertEquals(f.clienteId().longValue(),list.getBody().get("pedidos").get(0).get("clienteId").asLong());
 }
 @Test void consultaPagoPendientePagadoYAusenteSinInventarMetodoNiFecha(){
  var f=fixture();String route="/api/pedidos/"+f.pedidoId()+"/pagos";
  var pending=get(route,f.own());assertEquals(HttpStatus.OK,pending.getStatusCode());assertEquals("PENDIENTE",pending.getBody().get("estado").asText());
  assertTrue(pending.getBody().get("metodo").isNull());assertTrue(pending.getBody().get("fechaPago").isNull());
  var record=pagos.findByPedidoId(f.pedidoId()).orElseThrow();record.setEstado(EstadoPago.PAGADO);record.setMetodo(MetodoPago.PLIN);
  record.setCodigoReferencia("REF-US12");record.setFechaPago(LocalDateTime.of(2026,11,2,15,30));pagos.saveAndFlush(record);
  var paid=get(route,f.own());assertEquals(HttpStatus.OK,paid.getStatusCode());assertEquals("PAGADO",paid.getBody().get("estado").asText());
  assertEquals("PLIN",paid.getBody().get("metodo").asText());assertEquals("REF-US12",paid.getBody().get("codigoReferencia").asText());assertFalse(paid.getBody().get("fechaPago").isNull());
  pagos.deleteById(record.getId());pagos.flush();var missing=get(route,f.own());assertEquals(HttpStatus.NOT_FOUND,missing.getStatusCode());
  assertEquals(HttpStatus.OK,get("/api/pedidos/"+f.pedidoId(),f.own()).getStatusCode());
 }
 @Test void pedidosYPagosAjenosSonIndistinguiblesDeInexistentes(){
  var f=fixture();
  for(String suffix:List.of("","/pagos")){
   var foreign=get("/api/pedidos/"+f.pedidoId()+suffix,f.other());var missing=get("/api/pedidos/999999999"+suffix,f.other());
   assertEquals(HttpStatus.NOT_FOUND,foreign.getStatusCode());assertEquals(HttpStatus.NOT_FOUND,missing.getStatusCode());
   assertEquals(missing.getBody().get("mensaje").asText(),foreign.getBody().get("mensaje").asText());
   assertFalse(foreign.getBody().has("detalles"));assertFalse(foreign.getBody().has("pedidoId"));
  }
 }
}
