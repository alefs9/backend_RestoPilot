package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AgenteClienteService {
    private static final String INSTRUCCIONES = """
            Clasifica la consulta del cliente de RestoPilot. Responde únicamente una palabra.
            PEDIDO si quiere conocer el estado de su pedido más reciente.
            RESERVA si quiere conocer el estado de su reserva más reciente.
            HORARIO para el horario del restaurante seleccionado o si está abierto ahora.
            DIRECCION para saber dónde queda el restaurante seleccionado.
            ATENCION para saber si el restaurante seleccionado tiene delivery o atención en el local.
            CANCELACION para preguntar cómo o cuándo se puede cancelar una reserva.
            AYUDA para otras solicitudes, incluidos cambios y consultas por número de pedido o reserva.
            Si el cliente menciona un restaurante en el texto, responde AYUDA para indicar que debe
            seleccionarlo en el chat antes de preguntar por sus datos.
            """;

    private final ObjectProvider<ChatModel> modelos;
    private final PedidoRepository pedidos;
    private final ReservaRepository reservas;
    private final ConsultaRestauranteService restaurantes;

    public ChatIAResponseDTO responder(String mensaje, Long clienteId, Long restauranteId) {
        var modelo = modelos.getIfAvailable();
        if (modelo == null) {
            throw new AgenteIAService.FalloChat(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_UNAVAILABLE", "El asistente no está disponible.");
        }

        String intent;
        try {
            intent = ChatClient.create(modelo).prompt().system(INSTRUCCIONES).user(mensaje).call().content();
        } catch (RuntimeException ex) {
            throw new AgenteIAService.FalloChat(HttpStatus.BAD_GATEWAY,
                    "AI_PROVIDER_FAILURE", "El asistente no pudo responder. Intenta nuevamente.");
        }

        return switch (intent == null ? "" : intent.strip().toUpperCase(Locale.ROOT)) {
            case "PEDIDO" -> ultimoPedido(clienteId);
            case "RESERVA" -> ultimaReserva(clienteId);
            case "HORARIO", "DIRECCION", "ATENCION" ->
                    datosRestaurante(intent.strip().toUpperCase(Locale.ROOT), restauranteId);
            case "CANCELACION" -> respuesta("Desde Mis reservas puedes cancelar una reserva pendiente "
                    + "o confirmada con más de dos horas de anticipación y sin llegada registrada. "
                    + "Si ya estás dentro de ese plazo, comunícate con el restaurante.");
            default -> new ChatIAResponseDTO(ChatIAResponseDTO.Estado.CLARIFICATION_REQUIRED,
                    "Puedo consultar tu último pedido o reserva, o explicarte cómo cancelar una reserva. "
                    + "Para preguntas de horarios, dirección o atención, selecciona primero el restaurante.");
        };
    }

    private ChatIAResponseDTO datosRestaurante(String consulta, Long restauranteId) {
        if (restauranteId == null) {
            return new ChatIAResponseDTO(ChatIAResponseDTO.Estado.CLARIFICATION_REQUIRED,
                    "Selecciona un restaurante en el chat y vuelve a hacer la pregunta.");
        }
        try {
            var local = restaurantes.consultar(restauranteId);
            if (!local.activo()) return restauranteNoDisponible();
            return switch (consulta) {
                case "HORARIO" -> local.horaApertura() == null || local.horaCierre() == null
                        ? respuesta(local.nombre() + " no tiene un horario registrado.")
                        : respuesta(local.nombre() + " atiende de " + local.horaApertura() + " a "
                                + local.horaCierre() + " (hora de Lima). Ahora está "
                                + (local.abierto() ? "abierto." : "cerrado."));
                case "DIRECCION" -> respuesta(local.nombre()
                        + (local.direccion() == null || local.direccion().isBlank()
                                ? " no tiene una dirección registrada."
                                : " queda en " + local.direccion() + "."));
                default -> respuesta(local.nombre() + (local.aceptaDelivery()
                        ? " tiene delivery habilitado" : " tiene el delivery deshabilitado")
                        + (local.tieneAtencionFisica()
                                ? " y ofrece atención en el local." : " y tiene la atención en el local deshabilitada."));
            };
        } catch (ResourceNotFoundException ignored) {
            return restauranteNoDisponible();
        }
    }

    private ChatIAResponseDTO restauranteNoDisponible() {
        return new ChatIAResponseDTO(ChatIAResponseDTO.Estado.NO_DATA,
                "El restaurante seleccionado no está disponible. Elige otro restaurante.");
    }

    private ChatIAResponseDTO respuesta(String mensaje) {
        return new ChatIAResponseDTO(ChatIAResponseDTO.Estado.ANSWER, mensaje);
    }

    private ChatIAResponseDTO ultimoPedido(Long clienteId) {
        return pedidos.findByClienteId(clienteId,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "fechaCreacion", "id")))
                .stream().findFirst()
                .map(pedido -> new ChatIAResponseDTO(ChatIAResponseDTO.Estado.ANSWER,
                        "Tu pedido más reciente es el #" + pedido.getId() + ". Estado: "
                        + pedido.getEstado().name().toLowerCase(Locale.forLanguageTag("es")).replace('_', ' ') + "."))
                .orElseGet(() -> new ChatIAResponseDTO(ChatIAResponseDTO.Estado.NO_DATA, "Todavía no tienes pedidos."));
    }

    private ChatIAResponseDTO ultimaReserva(Long clienteId) {
        return reservas.findPaginaUsuario(clienteId,
                        PageRequest.of(0, 1))
                .stream().findFirst()
                .map(reserva -> new ChatIAResponseDTO(ChatIAResponseDTO.Estado.ANSWER,
                        "Tu reserva más reciente es la #" + reserva.getId() + ", para el " + reserva.getFecha()
                        + " a las " + reserva.getHoraInicio() + ". Estado: "
                        + reserva.getEstado().name().toLowerCase(Locale.forLanguageTag("es")).replace('_', ' ') + "."))
                .orElseGet(() -> new ChatIAResponseDTO(ChatIAResponseDTO.Estado.NO_DATA, "Todavía no tienes reservas."));
    }
}