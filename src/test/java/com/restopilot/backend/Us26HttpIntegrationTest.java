package com.restopilot.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.restopilot.backend.modules.auth.entity.*;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import com.restopilot.backend.modules.reservas.entity.*;
import com.restopilot.backend.modules.reservas.repository.*;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import com.restopilot.backend.security.JwtService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:us26http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
 "restopilot.reservas.expiracion.enabled=false","jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
@Import(Us26HttpIntegrationTest.Tiempo.class)
class Us26HttpIntegrationTest {
 static class Reloj extends Clock {
  private volatile LocalDateTime now=LocalDateTime.of(2026,11,3,9,58);
  public ZoneId getZone(){return ZoneId.of("America/Lima");}
  public Instant instant(){return now.atZone(getZone()).toInstant();}
  public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
  void fijar(LocalDateTime value){now=value;}
 }
 @TestConfiguration static class Tiempo {@Bean @Primary Reloj relojUs26(){return new Reloj();}}
 @Autowired Reloj clock;
 @Autowired TestRestTemplate http;
 @Autowired RestauranteRepository restaurantes;
 @Autowired UsuarioRepository usuarios;
 @Autowired MesaRepository mesas;
 @Autowired ReservaRepository reservas;
 @Autowired JwtService jwt;
 final LocalDate fecha=LocalDate.of(2026,11,3);
 record Fixture(Reserva reserva,Usuario cliente,HttpHeaders admin,HttpHeaders dueno,HttpHeaders client){}
 @BeforeEach void setup(){clock.fijar(fecha.atTime(9,58));http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());}
 Usuario usuario(Rol role,Restaurante r){return usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Prueba US26").correo(UUID.randomUUID()+"@us26.test")
  .passwordHash("no-usado-en-prueba-jwt").rol(role).restaurante(r).habilitado(true).build());}
 HttpHeaders headers(Usuario u){var h=new HttpHeaders();h.setBearerAuth(jwt.generateToken(u));return h;}
 Fixture fixture(){
  var local=restaurantes.saveAndFlush(Restaurante.builder().nombre("Local US26").activo(true).tieneAtencionFisica(true)
   .horaApertura(LocalTime.MIN).horaCierre(LocalTime.of(23,59,59)).build());
  var mesa=mesas.saveAndFlush(Mesa.builder().restaurante(local).numero(4).capacidad(4).activo(true).coordenadaX(0.0).coordenadaY(0.0).build());
  var client=usuario(Rol.CLIENTE,null);
  var r=reservas.saveAndFlush(Reserva.builder().restaurante(local).mesa(mesa).usuario(client).fecha(fecha).horaInicio(LocalTime.of(10,0))
   .horaFin(LocalTime.of(11,0)).numeroComensales(2).estado(EstadoReserva.CONFIRMADA).build());
  return new Fixture(r,client,headers(usuario(Rol.ADMINISTRADOR,local)),headers(usuario(Rol.DUENO,local)),headers(client));
 }
 ResponseEntity<JsonNode> get(String url,HttpHeaders h){return http.exchange(url,HttpMethod.GET,new HttpEntity<>(h),JsonNode.class);}
 ResponseEntity<JsonNode> patch(Fixture f,String action,HttpHeaders h){return http.exchange("/api/reservas/"+f.reserva().getId()+"/"+action,HttpMethod.PATCH,new HttpEntity<>(h),JsonNode.class);}
 JsonNode read(Fixture f){var response=get("/api/reservas/"+f.reserva().getId(),f.admin());assertEquals(HttpStatus.OK,response.getStatusCode());return response.getBody();}
 int avisos(Fixture f){return get("/api/notificaciones/me",f.client()).getBody().get("totalElements").asInt();}


 boolean disponible(Fixture f){
  var r=f.reserva();var result=get("/api/reservas/disponibilidad?restauranteId="+r.getRestaurante().getId()+"&fecha="+r.getFecha()+"&horaInicio=10:00&horaFin=11:00&numeroComensales=2",f.client());
  assertEquals(HttpStatus.OK,result.getStatusCode());return result.getBody().get("mesas").get(0).get("disponible").asBoolean();
 }
 JsonNode row(JsonNode body,Long id){for(var r:body.get("reservas"))if(r.get("id").asLong()==id)return r;throw new AssertionError("No aparece el ID esperado");}
 @ParameterizedTest @CsvSource({"PENDIENTE,-1,false","PENDIENTE,0,true","PENDIENTE,1,true","CONFIRMADA,899,false","CONFIRMADA,900,true","CONFIRMADA,901,true"})
 void limitesConservanHistorialYLiberanMesaSinDuplicarAvisos(String initial,int seconds,boolean expired){
  var f=fixture();var r=reservas.findById(f.reserva().getId()).orElseThrow();r.setEstado(EstadoReserva.valueOf(initial));reservas.saveAndFlush(r);
  clock.fijar(fecha.atTime(10,0).plusSeconds(seconds));var detail=read(f);
  assertEquals(expired?"EXPIRADA":initial,detail.get("estado").asText());assertEquals(expired,disponible(f));
  if(expired)assertEquals(LocalDateTime.now(clock),LocalDateTime.parse(detail.get("expiradaEn").asText()));else assertTrue(detail.get("expiradaEn").isNull());
  var history=get("/api/reservas/me",f.client());assertEquals(HttpStatus.OK,history.getStatusCode());
  assertEquals(detail.get("estado"),row(history.getBody(),r.getId()).get("estado"));assertEquals(detail.get("expiradaEn"),row(history.getBody(),r.getId()).get("expiradaEn"));
  var administrative=get("/api/reservas",f.admin());assertEquals(detail.get("estado"),row(administrative.getBody(),r.getId()).get("estado"));
  read(f);disponible(f);assertEquals(expired?1:0,avisos(f));
 }
 @Test void reservaAtendidaYCompletadaNoReapareceExpiradaEnHistorial(){
  var f=fixture();var arrival=patch(f,"llegada",f.admin());assertEquals(HttpStatus.OK,arrival.getStatusCode());var arrivedAt=arrival.getBody().get("llegadaRegistradaEn");
  clock.fijar(fecha.atTime(10,16));assertEquals("CONFIRMADA",read(f).get("estado").asText());assertFalse(disponible(f));
  var completed=patch(f,"completar",f.dueno());assertEquals(HttpStatus.OK,completed.getStatusCode());clock.fijar(fecha.plusDays(1).atTime(12,0));
  var after=read(f);assertEquals("COMPLETADA",after.get("estado").asText());assertEquals(arrivedAt,after.get("llegadaRegistradaEn"));
  assertEquals(completed.getBody().get("completadaEn"),after.get("completadaEn"));assertTrue(after.get("expiradaEn").isNull());assertTrue(disponible(f));
  assertEquals("COMPLETADA",row(get("/api/reservas/me",f.client()).getBody(),f.reserva().getId()).get("estado").asText());assertEquals(2,avisos(f));
 }
 @Test void expiracionConcurrenteEsUnaSolaMarcaYNotificacion(){
  var f=fixture();clock.fijar(fecha.atTime(10,15));var gate=new CountDownLatch(1);
  java.util.function.Supplier<JsonNode> query=()->{try{gate.await();return read(f);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}};
  var a=CompletableFuture.supplyAsync(query);var b=CompletableFuture.supplyAsync(query);gate.countDown();
  var first=a.join();var second=b.join();assertEquals("EXPIRADA",first.get("estado").asText());assertEquals(first.get("expiradaEn"),second.get("expiradaEn"));assertEquals(1,avisos(f));assertTrue(disponible(f));
 }
 @Test void solicitudTardiaNoReactivaYEscriturasPasadasSiguenRechazadas(){
  var f=fixture();clock.fijar(fecha.atTime(10,15));var path="/api/reservas/"+f.reserva().getId();
  assertEquals(HttpStatus.BAD_REQUEST,patch(f,"completar",f.admin()).getStatusCode());assertEquals("EXPIRADA",read(f).get("estado").asText());
  var confirm=http.exchange(path+"/estado",HttpMethod.PATCH,new HttpEntity<>(Map.of("estado","CONFIRMADA"),f.admin()),JsonNode.class);assertEquals(HttpStatus.BAD_REQUEST,confirm.getStatusCode());
  var cancel=http.exchange(path+"/cancelar",HttpMethod.PATCH,new HttpEntity<>(f.client()),JsonNode.class);assertEquals(HttpStatus.BAD_REQUEST,cancel.getStatusCode());
  var future=Map.of("mesaId",f.reserva().getMesa().getId(),"restauranteId",f.reserva().getRestaurante().getId(),"fecha",fecha.toString(),"horaInicio","10:30:00","horaFin","11:00:00","numeroComensales",2);
  assertEquals(HttpStatus.BAD_REQUEST,http.exchange(path,HttpMethod.PUT,new HttpEntity<>(future,f.client()),JsonNode.class).getStatusCode());
  var past=Map.of("mesaId",f.reserva().getMesa().getId(),"restauranteId",f.reserva().getRestaurante().getId(),"fecha",fecha.toString(),"horaInicio","10:00:00","horaFin","11:00:00","numeroComensales",2);
  assertTrue(disponible(f));assertEquals(HttpStatus.BAD_REQUEST,http.exchange("/api/reservas",HttpMethod.POST,new HttpEntity<>(past,f.client()),JsonNode.class).getStatusCode());
  assertEquals("EXPIRADA",read(f).get("estado").asText());assertEquals(1,avisos(f));
  var booked=http.exchange("/api/reservas",HttpMethod.POST,new HttpEntity<>(future,f.client()),JsonNode.class);assertEquals(HttpStatus.CREATED,booked.getStatusCode());assertFalse(disponible(f));
 }
 @Test void consultaAdmiteDiaPasadoPeroNoPierdeValidacionDeRangoYCapacidad(){
  var f=fixture();clock.fijar(fecha.plusDays(1).atTime(10,0));assertTrue(disponible(f));assertEquals("EXPIRADA",read(f).get("estado").asText());
  var base="/api/reservas/disponibilidad?restauranteId="+f.reserva().getRestaurante().getId()+"&fecha="+fecha;
  assertEquals(HttpStatus.BAD_REQUEST,get(base+"&horaInicio=11:00&horaFin=10:00&numeroComensales=2",f.client()).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,get(base+"&horaInicio=10:00&horaFin=11:00&numeroComensales=0",f.client()).getStatusCode());
  assertEquals(0,get(base+"&horaInicio=10:00&horaFin=11:00&numeroComensales=99",f.client()).getBody().get("mesas").size());
 }
 @Test void consultasNoMezclanClientesNiRestaurantes(){
  var own=fixture();var other=fixture();clock.fijar(fecha.atTime(10,15));
  var client=get("/api/reservas/me",own.client()).getBody().get("reservas");assertEquals(1,client.size());assertEquals(own.reserva().getId().longValue(),client.get(0).get("id").asLong());
  var admin=get("/api/reservas",own.admin()).getBody().get("reservas");assertEquals(1,admin.size());assertEquals(own.reserva().getId().longValue(),admin.get(0).get("id").asLong());
  assertEquals(HttpStatus.BAD_REQUEST,get("/api/reservas/"+own.reserva().getId(),other.admin()).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,get("/api/reservas",own.client()).getStatusCode());
 }
}
