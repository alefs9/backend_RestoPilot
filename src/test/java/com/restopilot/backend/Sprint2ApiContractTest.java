package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.catalogo.entity.*;
import com.restopilot.backend.modules.catalogo.repository.*;
import com.restopilot.backend.modules.reservas.repository.MesaRepository;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP/JWT/JPA reales en H2 aislado. Los flujos PostgreSQL se verifican por separado. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:sprint2contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","restopilot.demo.enabled=false",
 "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
@Import(Sprint2ApiContractTest.TimeConfig.class)
class Sprint2ApiContractTest {
 @TestConfiguration static class TimeConfig {
  @Bean @Primary Clock contractClock(){return Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"),ZoneId.of("America/Lima"));}
 }
 @Autowired TestRestTemplate http;
 @Autowired RestauranteRepository restaurantes;
 @Autowired UsuarioRepository usuarios;
 @Autowired CategoriaRepository categorias;
 @Autowired PlatoRepository platos;
 @Autowired MesaRepository mesas;
 @Autowired PasswordEncoder encoder;
 @Autowired javax.sql.DataSource datasource;
 Long local,ajeno,category;HttpHeaders admin,owner,client,foreign;long clientId;
 @BeforeEach void prepare(){
  http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
  var r=restaurantes.saveAndFlush(Restaurante.builder().nombre("Contrato Sprint2 "+UUID.randomUUID())
   .activo(true).tieneAtencionFisica(true).aceptaDelivery(true).horaApertura(LocalTime.MIN).horaCierre(LocalTime.of(23,59)).build());local=r.getId();
  var other=restaurantes.saveAndFlush(Restaurante.builder().nombre("Otro contrato").activo(true).tieneAtencionFisica(true).aceptaDelivery(true).horaApertura(LocalTime.MIN).horaCierre(LocalTime.of(23,59)).build());ajeno=other.getId();
  admin=login(Rol.ADMINISTRADOR,r);owner=login(Rol.DUENO,r);client=login(Rol.CLIENTE,null);foreign=login(Rol.DUENO,other);
  var c=new Categoria();c.setRestauranteId(local);c.setNombre("Carta contrato");category=categorias.saveAndFlush(c).getId();
 }
 HttpHeaders login(Rol role,Restaurante r){
  String email=UUID.randomUUID()+"@sprint2.test";
  var u=usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Contrato Sprint2").correo(email).passwordHash(encoder.encode("Prueba123!"))
   .rol(role).restaurante(r).habilitado(true).build());if(role==Rol.CLIENTE)clientId=u.getId();
  var response=http.postForEntity("/api/auth/login",Map.of("correo",email,"password","Prueba123!"),JsonNode.class);assertEquals(HttpStatus.OK,response.getStatusCode());
  var h=new HttpHeaders();h.setBearerAuth(response.getBody().get("token").asText());h.setContentType(MediaType.APPLICATION_JSON);return h;
 }
 ResponseEntity<JsonNode> call(HttpMethod method,String path,Object body,HttpHeaders headers){return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);}
 Map<String,Object> dish(){return new HashMap<>(Map.of("restauranteId",local,"categoriaId",category,"nombre","Plato contrato","precio",new BigDecimal("10.00"),"complejidad","MEDIA","tiempoPreparacionMinutos",10));}
 long createDish(){var r=call(HttpMethod.POST,"/api/platos",dish(),admin);assertEquals(HttpStatus.CREATED,r.getStatusCode(),r.toString());return r.getBody().get("id").asLong();}
 Map<String,Object> orderBody(long dish){return Map.of("restauranteId",local,"tipoEntrega","LLEVAR","detalles",List.of(Map.of("platoId",dish,"cantidad",2)));}
 long createOrder(){var r=call(HttpMethod.POST,"/api/pedidos",orderBody(createDish()),client);assertEquals(HttpStatus.CREATED,r.getStatusCode());return r.getBody().get("id").asLong();}
 long createTable(int number,int capacity){var r=call(HttpMethod.POST,"/api/mesas",Map.of("numero",number,"capacidad",capacity,"coordenadaX",10,"coordenadaY",20),admin);assertEquals(HttpStatus.CREATED,r.getStatusCode());return r.getBody().get("id").asLong();}
 Map<String,Object> reservation(long table,int people){return Map.of("restauranteId",local,"mesaId",table,"fecha","2026-11-06","horaInicio","12:00","horaFin","13:00","numeroComensales",people,"notas","Contrato aislado");}

 @Test void pruebasUsanH2YNoCredencialesDeSupabase() throws Exception {
  try(var c=datasource.getConnection()){assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:"));}
 }
 @Test void swaggerYOpenApiDisponiblesConJWTYAuthPublicaSinIA(){
  var response=http.getForEntity("/v3/api-docs",JsonNode.class);assertEquals(HttpStatus.OK,response.getStatusCode());var doc=response.getBody();
  assertEquals("RestoPilot API",doc.at("/info/title").asText());assertEquals("bearer",doc.at("/components/securitySchemes/bearerAuth/scheme").asText());
  assertTrue(doc.get("paths").size()>30);assertTrue(doc.at("/paths/~1api~1auth~1login/post/security").isArray());assertEquals(0,doc.at("/paths/~1api~1auth~1login/post/security").size());
  doc.get("paths").fieldNames().forEachRemaining(p->assertFalse(p.startsWith("/api/ia")));
  var ui=http.getForEntity("/swagger-ui/index.html",String.class);assertEquals(HttpStatus.OK,ui.getStatusCode());assertTrue(ui.getBody().contains("Swagger UI"));
 }
 @Test void erroresDeRutaMetodoContenidoYParametrosNoSePresentanComo500(){
  assertEquals(HttpStatus.NOT_FOUND,call(HttpMethod.GET,"/api/ruta-inexistente",null,admin).getStatusCode());
  var method=call(HttpMethod.POST,"/api/reportes/operativos",Map.of(),admin);assertEquals(HttpStatus.METHOD_NOT_ALLOWED,method.getStatusCode());assertTrue(method.getHeaders().getAllow().contains(HttpMethod.GET));
  var plain=new HttpHeaders();plain.putAll(admin);plain.setContentType(MediaType.TEXT_PLAIN);
  assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,call(HttpMethod.POST,"/api/platos","texto",plain).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.GET,"/api/platos/abc",null,admin).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.GET,"/api/reportes/operativos?fechaInicio=incorrecta&fechaFin=2026-11-05",null,admin).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.GET,"/api/reportes/operativos",null,admin).getStatusCode());
 }
 @Test void us14CreaEditaDisponibilidadYConservaPrecioHistorico(){
  long dish=createDish();var result=call(HttpMethod.GET,"/api/platos/"+dish,null,client);assertTrue(result.getBody().get("disponible").asBoolean());
  var order=call(HttpMethod.POST,"/api/pedidos",orderBody(dish),client);assertEquals(HttpStatus.CREATED,order.getStatusCode());long id=order.getBody().get("id").asLong();
  assertEquals(HttpStatus.OK,call(HttpMethod.PUT,"/api/platos/"+dish,Map.of("nombre","Nuevo nombre","precio",12),owner).getStatusCode());
  var historical=call(HttpMethod.GET,"/api/pedidos/"+id,null,client).getBody();assertEquals("Plato contrato",historical.at("/detalles/0/nombrePlatoSnapshot").asText());assertEquals(20,historical.get("total").asInt());
  assertEquals(HttpStatus.OK,call(HttpMethod.PATCH,"/api/platos/"+dish+"/disponibilidad?disponible=false",null,admin).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/pedidos",orderBody(dish),client).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.PUT,"/api/platos/"+dish,Map.of("nombre","No"),foreign).getStatusCode());
 }
 @Test void us14ValidaLimitesAntesDePersistirYNoConfundeErroresConConflictos(){
  long before=platos.count();
  for(var invalid:List.of(Map.entry("nombre"," "),Map.entry("nombre","x".repeat(151)),Map.entry("imagenUrl","x".repeat(256)),Map.entry("precio",0),Map.entry("precio",new BigDecimal("0.001")),Map.entry("precio",new BigDecimal("100000000.00")),Map.entry("tiempoPreparacionMinutos",4),Map.entry("tiempoPreparacionMinutos",16),Map.entry("complejidad","OTRA"))){
   var body=dish();body.put(invalid.getKey(),invalid.getValue());assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/platos",body,admin).getStatusCode(),invalid.getKey());
  }
  var missing=dish();missing.remove("restauranteId");assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/platos",missing,admin).getStatusCode());assertEquals(before,platos.count());
  for(int min:List.of(5,15)){var body=dish();body.put("tiempoPreparacionMinutos",min);body.put("precio",new BigDecimal("0.01"));assertEquals(HttpStatus.CREATED,call(HttpMethod.POST,"/api/platos",body,admin).getStatusCode());}
 }
 @Test void us16NumeroUnicoInclusoConDosSolicitudesSimultaneas() throws Exception {
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{Callable<HttpStatusCode> task=()->{start.await();return call(HttpMethod.POST,"/api/mesas",Map.of("numero",99,"capacidad",4,"coordenadaX",0,"coordenadaY",0),admin).getStatusCode();};
   var a=pool.submit(task);var b=pool.submit(task);start.countDown();var statuses=List.of(a.get(15,TimeUnit.SECONDS).value(),b.get(15,TimeUnit.SECONDS).value());
   assertEquals(1,statuses.stream().filter(v->v==201).count());assertTrue(statuses.stream().anyMatch(v->v==400||v==409));assertEquals(1,mesas.findByRestauranteId(local).stream().filter(m->m.getNumero()==99).count());
  }finally{pool.shutdownNow();}
 }
 @Test void us16ReservaVigenteImpideReducirYDesactivarHastaCancelar(){
  long table=createTable(1,6);var reserved=call(HttpMethod.POST,"/api/reservas",reservation(table,6),client);assertEquals(HttpStatus.CREATED,reserved.getStatusCode());long id=reserved.getBody().get("id").asLong();
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.PUT,"/api/mesas/"+table,Map.of("numero",1,"capacidad",4,"coordenadaX",0,"coordenadaY",0),admin).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.PATCH,"/api/mesas/"+table+"/estado",Map.of("activo",false),admin).getStatusCode());
  assertEquals(HttpStatus.OK,call(HttpMethod.PATCH,"/api/reservas/"+id+"/cancelar",null,client).getStatusCode());
  assertEquals(HttpStatus.OK,call(HttpMethod.PATCH,"/api/mesas/"+table+"/estado",Map.of("activo",false),admin).getStatusCode());
  assertEquals(0,call(HttpMethod.GET,"/api/mesas/restaurante/"+local,null,client).getBody().size());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/reservas",reservation(table,2),client).getStatusCode());
 }
 @Test void us16RechazaCoordenadasNoFinitas(){
  for(String x:List.of("NaN","Infinity","-Infinity"))assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/mesas",Map.of("numero",1,"capacidad",4,"coordenadaX",x,"coordenadaY",0),admin).getStatusCode());
  assertTrue(mesas.findByRestauranteId(local).isEmpty());
 }
 @ParameterizedTest @EnumSource(com.restopilot.backend.modules.pagos.entity.MetodoPago.class)
 void us18MetodosPagoPendienteConfirmadoEInmutable(com.restopilot.backend.modules.pagos.entity.MetodoPago method){
  long order=createOrder();var body=new HashMap<String,Object>(Map.of("monto",20,"metodo",method.name(),"estado","PENDIENTE"));
  assertEquals(HttpStatus.CREATED,call(HttpMethod.POST,"/api/pedidos/"+order+"/pagos",body,admin).getStatusCode());
  assertTrue(call(HttpMethod.GET,"/api/pedidos/"+order+"/pagos",null,client).getBody().get("fechaPago").isNull());
  body.put("estado","PAGADO");if(method!=com.restopilot.backend.modules.pagos.entity.MetodoPago.EFECTIVO){assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/pedidos/"+order+"/pagos",body,admin).getStatusCode());body.put("codigoReferencia","QA-CONTRATO");}
  var paid=call(HttpMethod.POST,"/api/pedidos/"+order+"/pagos",body,admin);assertEquals(HttpStatus.CREATED,paid.getStatusCode());assertFalse(paid.getBody().get("fechaPago").isNull());
  assertEquals(HttpStatus.CONFLICT,call(HttpMethod.POST,"/api/pedidos/"+order+"/pagos",body,owner).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.DELETE,"/api/pedidos/"+order,null,admin).getStatusCode());
 }
 @Test void us27PausaConservaReservasYBloqueaMesasReservasYSalon(){
  long table=createTable(1,4);var first=call(HttpMethod.POST,"/api/reservas",reservation(table,2),client);long id=first.getBody().get("id").asLong();
  assertEquals(HttpStatus.OK,call(HttpMethod.PATCH,"/api/reservas/modulo/estado",Map.of("habilitado",false),admin).getStatusCode());
  assertEquals("PENDIENTE",call(HttpMethod.GET,"/api/reservas/"+id,null,client).getBody().get("estado").asText());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/mesas",Map.of("numero",2,"capacidad",4,"coordenadaX",0,"coordenadaY",0),admin).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/reservas",reservation(table,2),client).getStatusCode());
  var body=new HashMap<String,Object>(orderBody(createDish()));body.put("tipoEntrega","SALON");body.put("mesaId",table);assertEquals(HttpStatus.BAD_REQUEST,call(HttpMethod.POST,"/api/pedidos",body,client).getStatusCode());
  body.put("tipoEntrega","LLEVAR");body.remove("mesaId");assertEquals(HttpStatus.CREATED,call(HttpMethod.POST,"/api/pedidos",body,client).getStatusCode());
 }
 @Test void us28Y29NotificacionesPropiasFiltrosYCancelacionAdministrativaSinLimiteCliente(){
  long table=createTable(1,4);var response=call(HttpMethod.POST,"/api/reservas",reservation(table,2),client);long id=response.getBody().get("id").asLong();
  assertEquals(HttpStatus.OK,call(HttpMethod.PATCH,"/api/reservas/"+id+"/estado",Map.of("estado","CONFIRMADA"),admin).getStatusCode());
  var list=call(HttpMethod.GET,"/api/reservas?fecha=2026-11-06&estado=CONFIRMADA",null,owner);assertEquals(1,list.getBody().get("reservas").size());
  assertEquals(0,call(HttpMethod.GET,"/api/reservas?estado=RECHAZADA",null,owner).getBody().get("reservas").size());
  var notices=call(HttpMethod.GET,"/api/notificaciones/me",null,client).getBody().get("content");assertEquals(1,notices.size());assertEquals(clientId,notices.get(0).get("usuarioId").asLong());
  assertEquals(0,call(HttpMethod.GET,"/api/notificaciones/me",null,admin).getBody().get("totalElements").asInt());
  assertEquals(HttpStatus.OK,call(HttpMethod.PUT,"/api/reservas/"+id,reservation(table,2),owner).getStatusCode());
  assertEquals("PENDIENTE",call(HttpMethod.GET,"/api/reservas/"+id,null,client).getBody().get("estado").asText());
  assertEquals(HttpStatus.OK,call(HttpMethod.PATCH,"/api/reservas/"+id+"/cancelar",null,owner).getStatusCode());
 }
}
