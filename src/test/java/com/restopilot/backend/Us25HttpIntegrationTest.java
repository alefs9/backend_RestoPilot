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
 "spring.datasource.url=jdbc:h2:mem:us25http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","restopilot.demo.enabled=false",
 "restopilot.reservas.expiracion.enabled=false","jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItcmVzdG9waWxvdC0yMDI2LXRlc3Rz"})
@Import(Us25HttpIntegrationTest.Tiempo.class)
class Us25HttpIntegrationTest {
 static class Reloj extends Clock {
  private volatile LocalDateTime now=LocalDateTime.of(2026,11,3,9,58);
  public ZoneId getZone(){return ZoneId.of("America/Lima");}
  public Instant instant(){return now.atZone(getZone()).toInstant();}
  public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
  void fijar(LocalDateTime value){now=value;}
 }
 @TestConfiguration static class Tiempo {@Bean @Primary Reloj relojUs25(){return new Reloj();}}
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
 Usuario usuario(Rol role,Restaurante r){return usuarios.saveAndFlush(Usuario.builder().nombreCompleto("Prueba US25").correo(UUID.randomUUID()+"@us25.test")
  .passwordHash("no-usado-en-prueba-jwt").rol(role).restaurante(r).habilitado(true).build());}
 HttpHeaders headers(Usuario u){var h=new HttpHeaders();h.setBearerAuth(jwt.generateToken(u));return h;}
 Fixture fixture(){
  var local=restaurantes.saveAndFlush(Restaurante.builder().nombre("Local US25").activo(true).tieneAtencionFisica(true)
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

 @ParameterizedTest @CsvSource({"899,true","900,false","901,false"})
 void llegadaRespetaLimiteExactoYFlagsDelRelojServidor(int seconds,boolean allowed){
  var f=fixture();clock.fijar(fecha.atTime(10,0).plusSeconds(seconds));
  var before=read(f);assertEquals(allowed,before.get("puedeRegistrarLlegada").asBoolean());assertFalse(before.get("puedeCompletar").asBoolean());
  assertEquals(LocalDateTime.now(clock),LocalDateTime.parse(before.get("fechaHoraServidor").asText()));
  var result=patch(f,"llegada",f.admin());assertEquals(allowed?HttpStatus.OK:HttpStatus.BAD_REQUEST,result.getStatusCode());
  var after=read(f);assertEquals(allowed?"CONFIRMADA":"EXPIRADA",after.get("estado").asText());
  if(allowed)assertEquals(before.get("fechaHoraServidor").asText(),after.get("llegadaRegistradaEn").asText());else assertTrue(after.get("llegadaRegistradaEn").isNull());
  assertEquals(1,avisos(f));
 }
 @Test void llegadaYFinalizacionConservanFechasYNoSeRepitenYLaMesaQuedaDisponible(){
  var f=fixture();assertTrue(read(f).get("puedeRegistrarLlegada").asBoolean());
  var arrival=patch(f,"llegada",f.admin());assertEquals(HttpStatus.OK,arrival.getStatusCode());var arrivalAt=arrival.getBody().get("llegadaRegistradaEn");
  assertFalse(arrival.getBody().get("puedeRegistrarLlegada").asBoolean());assertFalse(arrival.getBody().get("puedeCompletar").asBoolean());
  assertEquals(HttpStatus.BAD_REQUEST,patch(f,"llegada",f.admin()).getStatusCode());
  assertEquals(HttpStatus.BAD_REQUEST,patch(f,"completar",f.admin()).getStatusCode());assertEquals(1,avisos(f));
  clock.fijar(fecha.atTime(10,0));assertTrue(read(f).get("puedeCompletar").asBoolean());
  var completed=patch(f,"completar",f.dueno());assertEquals(HttpStatus.OK,completed.getStatusCode());var result=completed.getBody();
  assertEquals("COMPLETADA",result.get("estado").asText());assertEquals(arrivalAt,result.get("llegadaRegistradaEn"));assertEquals(fecha.atTime(10,0),LocalDateTime.parse(result.get("completadaEn").asText()));
  assertFalse(result.get("puedeCompletar").asBoolean());assertFalse(result.get("puedeRegistrarLlegada").asBoolean());
  assertEquals(HttpStatus.BAD_REQUEST,patch(f,"completar",f.admin()).getStatusCode());assertEquals(result.get("completadaEn"),read(f).get("completadaEn"));assertEquals(2,avisos(f));
  var available=get("/api/reservas/disponibilidad?restauranteId="+f.reserva().getRestaurante().getId()+"&fecha=2026-11-03&horaInicio=10:00&horaFin=11:00&numeroComensales=2",f.client());
  assertEquals(HttpStatus.OK,available.getStatusCode());assertTrue(available.getBody().get("mesas").get(0).get("disponible").asBoolean());
 }
 @Test void noCompletaSinLlegadaNiAtiendeReservasAjenas(){
  var f=fixture();assertEquals(HttpStatus.BAD_REQUEST,patch(f,"completar",f.admin()).getStatusCode());
  var other=fixture();var noLocal=headers(usuario(Rol.ADMINISTRADOR,null));
  for(var action:List.of("llegada","completar")){
   assertEquals(HttpStatus.FORBIDDEN,patch(f,action,f.client()).getStatusCode());
   assertEquals(HttpStatus.FORBIDDEN,patch(f,action,other.admin()).getStatusCode());
   assertEquals(HttpStatus.FORBIDDEN,patch(f,action,noLocal).getStatusCode());
   var missing=http.exchange("/api/reservas/99999999/"+action,HttpMethod.PATCH,new HttpEntity<>(f.admin()),JsonNode.class);assertEquals(HttpStatus.NOT_FOUND,missing.getStatusCode());
  }
  assertEquals(HttpStatus.BAD_REQUEST,get("/api/reservas/"+f.reserva().getId(),other.admin()).getStatusCode());
  assertTrue(read(f).get("llegadaRegistradaEn").isNull());assertEquals(0,avisos(f));
 }
 @Test void llegadasConcurrentesPersistenUnaFechaYUnaNotificacion(){
  var f=fixture();var gate=new CountDownLatch(1);
  java.util.function.Supplier<HttpStatusCode> send=()->{try{gate.await();return patch(f,"llegada",f.admin()).getStatusCode();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}};
  var a=CompletableFuture.supplyAsync(send);var b=CompletableFuture.supplyAsync(send);gate.countDown();var statuses=List.of(a.join(),b.join());
  assertEquals(1,statuses.stream().filter(HttpStatus.OK::equals).count());assertEquals(1,statuses.stream().filter(HttpStatus.BAD_REQUEST::equals).count());
  assertEquals(fecha.atTime(9,58),LocalDateTime.parse(read(f).get("llegadaRegistradaEn").asText()));assertEquals(1,avisos(f));
 }
 @Test void llegadaRegistradaNoExpiraYNoEsNecesarioEsperarAlFin(){
  var f=fixture();assertEquals(HttpStatus.OK,patch(f,"llegada",f.admin()).getStatusCode());clock.fijar(fecha.atTime(10,16));
  var r=read(f);assertEquals("CONFIRMADA",r.get("estado").asText());assertTrue(r.get("expiradaEn").isNull());assertTrue(r.get("puedeCompletar").asBoolean());
  assertEquals(HttpStatus.OK,patch(f,"completar",f.admin()).getStatusCode());
 }
 @Test void llegadaAnticipadaNoInventaUnaRestriccionDeMismoDia(){
  var f=fixture();var r=reservas.findById(f.reserva().getId()).orElseThrow();r.setFecha(fecha.plusDays(1));reservas.saveAndFlush(r);
  assertTrue(read(f).get("puedeRegistrarLlegada").asBoolean());assertEquals(HttpStatus.OK,patch(f,"llegada",f.admin()).getStatusCode());assertFalse(read(f).get("puedeCompletar").asBoolean());
 }
}
