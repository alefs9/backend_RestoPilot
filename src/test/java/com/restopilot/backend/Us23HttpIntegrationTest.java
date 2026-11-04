package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.*;
import com.restopilot.backend.modules.catalogo.repository.*;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Servidor, JWT y JPA reales; H2 aislado, no PostgreSQL. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:us23http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
 "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
class Us23HttpIntegrationTest {
 @Autowired TestRestTemplate http;
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired RestauranteRepository restaurantes;
 @Autowired UsuarioRepository usuarios;
 @Autowired CategoriaRepository categorias;
 @Autowired PlatoRepository platos;
 @Autowired PasswordEncoder encoder;
 Long local,ajeno; HttpHeaders admin,dueno,cliente;
 @BeforeEach void preparar() {
  var r=restaurantes.saveAndFlush(Restaurante.builder().nombre("US23 prueba aislada").build());local=r.getId();
  ajeno=restaurantes.saveAndFlush(Restaurante.builder().nombre("Otro local US23").build()).getId();
  admin=login(Rol.ADMINISTRADOR,r);dueno=login(Rol.DUENO,r);cliente=login(Rol.CLIENTE,null);
 }
 HttpHeaders login(Rol rol,Restaurante r) {
  String correo=UUID.randomUUID()+"@us23.test";
  usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Prueba US23").correo(correo)
   .passwordHash(encoder.encode("Prueba123!")).rol(rol).restaurante(r).habilitado(true).build());
  var response=http.postForEntity("/api/auth/login",Map.of("correo",correo,"password","Prueba123!"),JsonNode.class);
  assertEquals(HttpStatus.OK,response.getStatusCode());
  var headers=new HttpHeaders();headers.setBearerAuth(response.getBody().get("token").asText());headers.setContentType(MediaType.APPLICATION_JSON);return headers;
 }
 ResponseEntity<JsonNode> call(HttpMethod method,String path,Object body,HttpHeaders headers) {
  return http.exchange("/api/categorias"+path,method,new HttpEntity<>(body,headers),JsonNode.class);
 }
 long crear() {
  var result=call(HttpMethod.POST,"",Map.of("restauranteId",local,"nombre","Entradas","descripcion","Inicial"),admin);
  assertEquals(HttpStatus.CREATED,result.getStatusCode());return result.getBody().get("id").asLong();
 }
 @Test void cicloCrearListarEditarEliminarConDuenoYAdministrador() {
  long id=crear();var lista=call(HttpMethod.GET,"/restaurante/"+local,null,admin);
  assertEquals(HttpStatus.OK,lista.getStatusCode());assertEquals(1,lista.getBody().size());
  assertEquals(id,lista.getBody().get(0).get("id").asLong());
  var editado=call(HttpMethod.PUT,"/"+id,Map.of("nombre","Postres","descripcion",""),dueno);
  assertEquals(HttpStatus.OK,editado.getStatusCode());assertEquals("Postres",editado.getBody().get("nombre").asText());
  assertEquals("",categorias.findById(id).orElseThrow().getDescripcion());
  assertEquals(HttpStatus.NO_CONTENT,call(HttpMethod.DELETE,"/"+id,null,admin).getStatusCode());
  assertFalse(categorias.existsById(id));
 }
 @Test void rechazaNombreVacioYExcesivoSinModificarPersistencia() {
  long id=crear();
  for(String nombre:List.of("","   ","x".repeat(101))) {
   assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"",Map.of("restauranteId",local,"nombre",nombre),admin).getStatusCode());
   assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.PUT,"/"+id,Map.of("nombre",nombre),admin).getStatusCode());
  }
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"",Map.of("restauranteId",local),admin).getStatusCode());
  assertEquals("Entradas",categorias.findById(id).orElseThrow().getNombre());
  assertEquals(1,categorias.findByRestauranteId(local).size());
 }
 @Test void mantieneActualizacionParcialYLimiteDelModelo() {
  long id=crear();
  assertEquals(HttpStatus.OK,call(HttpMethod.PUT,"/"+id,Map.of("descripcion","Nueva"),admin).getStatusCode());
  assertEquals("Entradas",categorias.findById(id).orElseThrow().getNombre());
  assertEquals(HttpStatus.OK,call(HttpMethod.PUT,"/"+id,Map.of("nombre","x".repeat(100)),admin).getStatusCode());
 }
 @Test void conservaCategoriaYPlatoAlRechazarEliminacion() {
  long id=crear();var plato=new Plato();plato.setRestauranteId(local);plato.setCategoria(categorias.findById(id).orElseThrow());
  plato.setNombre("Plato US23");plato.setPrecio(BigDecimal.TEN);plato=platos.saveAndFlush(plato);
  var result=call(HttpMethod.DELETE,"/"+id,null,admin);
  assertEquals(HttpStatus.CONFLICT,result.getStatusCode());assertTrue(result.getBody().get("mensaje").asText().contains("platos asociados"));
  assertTrue(categorias.existsById(id));assertTrue(platos.existsById(plato.getId()));
 }
 @Test void impideGestionAjenaYSuplantacionDelRestaurante() {
  var otra=new Categoria();otra.setRestauranteId(ajeno);otra.setNombre("Ajena");long id=categorias.saveAndFlush(otra).getId();
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.POST,"",Map.of("restauranteId",ajeno,"nombre","No"),admin).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.PUT,"/"+id,Map.of("restauranteId",local,"nombre","No"),admin).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.DELETE,"/"+id,null,dueno).getStatusCode());
  assertEquals("Ajena",categorias.findById(id).orElseThrow().getNombre());
 }
 @Test void clienteConsultaCartaPeroNoPuedeGestionarYNecesitaSesion() {
  long id=crear();
  assertEquals(HttpStatus.OK,call(HttpMethod.GET,"/restaurante/"+local,null,cliente).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.POST,"",Map.of("restauranteId",local,"nombre","No"),cliente).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.PUT,"/"+id,Map.of("nombre","No"),cliente).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.DELETE,"/"+id,null,cliente).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.GET,"/restaurante/"+local,null,new HttpHeaders()).getStatusCode());
 }
 @Test void devuelve404ParaIdentificadorInexistente() {
  assertEquals(HttpStatus.NOT_FOUND,call(HttpMethod.PUT,"/99999999",Map.of("nombre","No"),admin).getStatusCode());
  assertEquals(HttpStatus.NOT_FOUND,call(HttpMethod.DELETE,"/99999999",null,admin).getStatusCode());
 }
 @Test void noAceptaIdentidadEnAltaNiFechasControladasPorCliente() {
  long id=crear();
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"",Map.of("id",id,"restauranteId",local,"nombre","No"),admin).getStatusCode());
  var response=call(HttpMethod.POST,"",Map.of("restauranteId",local,"nombre","  Nueva  ","creadoEn","2000-01-01T00:00:00"),admin);
  assertEquals(HttpStatus.CREATED,response.getStatusCode());assertEquals("Nueva",response.getBody().get("nombre").asText());
  assertFalse(response.getBody().get("creadoEn").asText().startsWith("2000-"));
 }
 @Test void preflightAngularPermiteJwtYOperacionesDeCategorias() throws Exception {
  var client=java.net.http.HttpClient.newHttpClient();
  for(String method:List.of("GET","POST","PUT","DELETE")) {
   var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/categorias"))
    .header("Origin","http://localhost:4200").header("Access-Control-Request-Method",method)
    .header("Access-Control-Request-Headers","authorization,content-type")
    .method("OPTIONS",java.net.http.HttpRequest.BodyPublishers.noBody()).build();
   var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.discarding());
   assertEquals(200,response.statusCode());
   assertEquals("http://localhost:4200",response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
   assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow().contains(method));
   assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase().contains("authorization"));
  }
 }

 @Test
 @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_US23_BROWSER",matches="true")
 void navegadorAngularConJwtYJpaReales() throws Exception {
  var r=restaurantes.findById(local).orElseThrow();r.setNombre("La Mesa Verde");restaurantes.saveAndFlush(r);
  long principal=0;
  String[] nombres={"Platos de fondo","Entradas","Bebidas"};
  String[] descripciones={"Nuestros clásicos de siempre","Para comenzar a compartir","Acompañamientos para cada momento"};
  for(int i=0;i<nombres.length;i++){
   var c=new Categoria();c.setRestauranteId(local);c.setNombre(nombres[i]);c.setDescripcion(descripciones[i]);c=categorias.saveAndFlush(c);
   if(i==0)principal=c.getId();
  }
  var plato=new Plato();plato.setRestauranteId(local);plato.setCategoria(categorias.findById(principal).orElseThrow());plato.setNombre("Plato asociado US23");plato.setPrecio(BigDecimal.TEN);platos.saveAndFlush(plato);
  String correo=UUID.randomUUID()+"@us23-browser.test";
  String password=UUID.randomUUID()+"aA1!";
  usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Operación de prueba US23").correo(correo).passwordHash(encoder.encode(password)).rol(Rol.ADMINISTRADOR).restaurante(r).habilitado(true).build());
  var process=new ProcessBuilder(System.getenv("US23_NODE"),System.getenv("US23_BROWSER_SCRIPT"));
  process.environment().put("US23_API","http://localhost:"+port);
  process.environment().put("US23_EMAIL",correo);process.environment().put("US23_PASSWORD",password);
  process.environment().put("US23_CATEGORY_ID",String.valueOf(principal));
  process.redirectErrorStream(true);
  var log=java.nio.file.Path.of("target/us23-browser.log");process.redirectOutput(log.toFile());
  var running=process.start();
  if(!running.waitFor(150,java.util.concurrent.TimeUnit.SECONDS)){running.destroyForcibly();fail("El recorrido excedió 150 segundos; revisar target/us23-browser.log.");}
  assertEquals(0,running.exitValue(),"Recorrido Angular fallido: revisar target/us23-browser.log (sin credenciales).");
  assertTrue(categorias.existsById(principal));assertTrue(platos.existsByCategoriaId(principal));
  assertEquals(3,categorias.findByRestauranteId(local).size(),"El recorrido crea, edita y elimina únicamente su categoría de prueba.");
 }

}
