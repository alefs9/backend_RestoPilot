package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.*;
import com.restopilot.backend.modules.catalogo.repository.*;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:us15http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
 "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
class Us15HttpIntegrationTest {
 @Autowired TestRestTemplate http;
 @Autowired RestauranteRepository restaurantes;
 @Autowired UsuarioRepository usuarios;
 @Autowired CategoriaRepository categorias;
 @Autowired PlatoRepository platos;
 @Autowired PedidoRepository pedidos;
 @Autowired PasswordEncoder encoder;
 @Autowired JdbcTemplate jdbc;
 record Fixture(Restaurante local,Categoria categoria,HttpHeaders cliente,HttpHeaders admin,HttpHeaders dueno){}
 HttpHeaders usuario(Rol rol,Restaurante local){
  String mail=UUID.randomUUID()+"@us15.test";
  usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Cuenta de prueba US15").correo(mail)
   .passwordHash(encoder.encode("Prueba123!")).rol(rol).restaurante(local).habilitado(true).build());
  var response=http.postForEntity("/api/auth/login",Map.of("correo",mail,"password","Prueba123!"),JsonNode.class);
  assertEquals(HttpStatus.OK,response.getStatusCode());
  var headers=new HttpHeaders();headers.setBearerAuth(response.getBody().get("token").asText());headers.setContentType(MediaType.APPLICATION_JSON);return headers;
 }
 Fixture fixture(){
  var local=restaurantes.saveAndFlush(Restaurante.builder().nombre("Local de prueba US15").build());
  var categoria=new Categoria();categoria.setRestauranteId(local.getId());categoria.setNombre("Principal");categoria=categorias.saveAndFlush(categoria);
  return new Fixture(local,categoria,usuario(Rol.CLIENTE,null),usuario(Rol.ADMINISTRADOR,local),usuario(Rol.DUENO,local));
 }
 Plato plato(Fixture f,String complejidad,Integer minutos){
  var p=new Plato();p.setRestauranteId(f.local().getId());p.setCategoria(f.categoria());p.setNombre("Nombre histórico");p.setPrecio(new BigDecimal("12.50"));
  p.setComplejidad(complejidad);p.setTiempoPreparacionMinutos(minutos);return platos.saveAndFlush(p);
 }
 long crear(Fixture f,Plato... lines){
  var detalles=Arrays.stream(lines).map(p->Map.of("platoId",p.getId(),"cantidad",2)).toList();
  var response=http.exchange("/api/pedidos",HttpMethod.POST,new HttpEntity<>(Map.of("restauranteId",f.local().getId(),"tipoEntrega","LLEVAR","detalles",detalles),f.cliente()),JsonNode.class);
  assertEquals(HttpStatus.CREATED,response.getStatusCode());return response.getBody().get("id").asLong();
 }
 ResponseEntity<JsonNode> get(String url,HttpHeaders headers){return http.exchange(url,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);}
 JsonNode cola(Fixture f,int page,int size){var r=get("/api/pedidos/pendientes?page="+page+"&size="+size,f.admin());assertEquals(HttpStatus.OK,r.getStatusCode());return r.getBody();}
 void estado(long id,EstadoPedido state){var p=pedidos.findById(id).orElseThrow();p.setEstado(state);pedidos.saveAndFlush(p);}
 void fecha(long id,String date){jdbc.update("update pedidos set fecha_creacion = ? where id = ?",java.sql.Timestamp.valueOf(date),id);}

 @Test void prioridadGlobalAntesDePaginarConMaximosIndependientesYDesempates(){
  var f=fixture();
  long baja=crear(f,plato(f,"BAJA",80));
  long altaCorta=crear(f,plato(f,"ALTA",20));
  long altaLarga=crear(f,plato(f," alta ",5),plato(f,"BAJA",30));
  long empate=crear(f,plato(f,"ALTA",30));
  long antigua=crear(f,plato(f,"ALTA",30));
  long media=crear(f,plato(f,"MEDIA",90));
  for(long id:new long[]{baja,altaCorta,altaLarga,empate,media})fecha(id,"2026-11-01 12:00:00");
  fecha(antigua,"2026-11-01 11:00:00");estado(altaCorta,EstadoPedido.EN_PREPARACION);
  var expected=List.of(antigua,altaLarga,empate,altaCorta,media,baja);
  var all=new ArrayList<Long>();
  for(int page=0;page<3;page++){
   var result=cola(f,page,2);assertEquals(6,result.get("totalElements").asInt());assertEquals(3,result.get("totalPages").asInt());
   for(var item:result.get("content"))all.add(item.get("id").asLong());
  }
  assertEquals(expected,all);
  var detail=get("/api/pedidos/"+altaLarga,f.admin()).getBody();
  assertEquals("ALTA",detail.get("complejidadMaxima").asText());assertEquals(30,detail.get("tiempoPreparacionMaximoMinutos").asInt());
  assertEquals(2,detail.get("detalles").size());assertEquals(50.0,detail.get("total").asDouble());
 }
 @Test void aislamientoRolesEstadosActivosYActualizacion(){
  var f=fixture();var other=fixture();var dish=plato(f,"MEDIA",15);
  long active=crear(f,dish),ready=crear(f,dish),delivered=crear(f,dish),cancelled=crear(f,dish),foreign=crear(other,plato(other,"ALTA",90));
  estado(ready,EstadoPedido.LISTO);estado(delivered,EstadoPedido.ENTREGADO);estado(cancelled,EstadoPedido.CANCELADO);
  var result=cola(f,0,20);assertEquals(1,result.get("totalElements").asInt());assertEquals(active,result.get("content").get(0).get("id").asLong());
  assertEquals(HttpStatus.OK,get("/api/pedidos/pendientes",f.dueno()).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,get("/api/pedidos/pendientes",f.cliente()).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,get("/api/pedidos/pendientes",usuario(Rol.ADMINISTRADOR,null)).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,get("/api/pedidos/"+foreign,f.admin()).getStatusCode());
  assertEquals(HttpStatus.OK,get("/api/pedidos/"+ready,f.admin()).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,get("/api/pedidos/pendientes",new HttpHeaders()).getStatusCode());
  long added=crear(f,plato(f,"ALTA",20));assertEquals(added,cola(f,0,20).get("content").get(0).get("id").asLong());
  estado(active,EstadoPedido.LISTO);estado(added,EstadoPedido.ENTREGADO);assertEquals(0,cola(f,0,20).get("totalElements").asInt());
 }
 @Test void metadatosAusentesYCeroNoInventanInformacionNiAlteranSnapshots(){
  var f=fixture();var unknown=plato(f,null,null);long id=crear(f,unknown);
  var initial=get("/api/pedidos/"+id,f.admin()).getBody();assertTrue(initial.get("complejidadMaxima").isNull());assertTrue(initial.get("tiempoPreparacionMaximoMinutos").isNull());
  unknown.setNombre("Nombre actual");unknown.setPrecio(new BigDecimal("99.00"));unknown.setComplejidad("BAJA");unknown.setTiempoPreparacionMinutos(0);platos.saveAndFlush(unknown);
  var updated=get("/api/pedidos/"+id,f.admin()).getBody();
  assertEquals("BAJA",updated.get("complejidadMaxima").asText());assertEquals(0,updated.get("tiempoPreparacionMaximoMinutos").asInt());
  assertEquals(initial.get("detalles"),updated.get("detalles"));assertEquals(initial.get("total"),updated.get("total"));
 }
}
