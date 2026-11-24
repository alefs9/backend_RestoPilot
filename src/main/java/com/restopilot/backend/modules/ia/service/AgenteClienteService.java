package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.core.exception.BusinessRuleException;
import com.restopilot.backend.modules.pedidos.repository.PedidoRepository;
import com.restopilot.backend.modules.pedidos.service.PedidoService;
import com.restopilot.backend.modules.reservas.repository.ReservaRepository;
import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgenteClienteService {
    private static final Pattern CONFIRMACION = Pattern.compile(
            "confirmar cancelar pedido\\s+#?([1-9][0-9]{0,18})", Pattern.CASE_INSENSITIVE);

    private static final String INSTRUCCIONES = """
            Eres el asistente del cliente de RestoPilot.
            Para consultas de carta, precios, ingredientes, alérgenos o disponibilidad llama a consultarCarta.
            Usa el nombre del plato o categoría como búsqueda, o una cadena vacía para la carta general.
            El restaurante siempre se selecciona en el chat. Si indican otro restaurante o piden
            recomendaciones para una restricción alimentaria, responde AYUDA.
            Si indican el número de un pedido propio, usa consultarPedido para conocer el estado
            o prepararCancelacion si quieren cancelarlo. Si quieren cancelar pero no indican el número
            exacto, responde NUMERO_PEDIDO. Realiza como máximo una llamada a una herramienta.
            Para las consultas restantes, responde únicamente una palabra.
            PEDIDO si quiere conocer el estado de su pedido más reciente.
            RESERVA si quiere conocer el estado de su reserva más reciente.
            HORARIO para el horario del restaurante seleccionado o si está abierto ahora.
            DIRECCION para saber dónde queda el restaurante seleccionado.
            ATENCION para saber si el restaurante seleccionado tiene delivery o atención en el local.
            CANCELACION para preguntar cómo o cuándo se puede cancelar una reserva.
            AYUDA para otras solicitudes, incluidos los cambios de pedido y las consultas por número de reserva.
            """;

    private final ObjectProvider<ChatModel> modelos;
    private final PedidoRepository pedidos;
    private final ReservaRepository reservas;
    private final ConsultaRestauranteService restaurantes;
    private final PlatoService platos;
    private final PedidoService gestionPedidos;

    public ChatIAResponseDTO responder(String mensaje, Long clienteId, Long restauranteId) {
        var confirmacion = CONFIRMACION.matcher(mensaje.strip());
        if (confirmacion.matches()) return cancelarPedido(confirmacion.group(1));

        var modelo = modelos.getIfAvailable();
        if (modelo == null) {
            throw new AgenteIAService.FalloChat(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_UNAVAILABLE", "El asistente no está disponible.");
        }

        var herramientas = new HerramientasCliente(restaurantes, platos, restauranteId, gestionPedidos);
        String intent;
        try {
            intent = ChatClient.create(modelo).prompt().system(INSTRUCCIONES).user(mensaje)
                    .tools(herramientas).call().content();
        } catch (RuntimeException ex) {
            if (herramientas.fallo() != null) throw herramientas.fallo();
            throw new AgenteIAService.FalloChat(HttpStatus.BAD_GATEWAY,
                    "AI_PROVIDER_FAILURE", "El asistente no pudo responder. Intenta nuevamente.");
        }

        if (herramientas.fallo() != null) throw herramientas.fallo();
        var resultado = herramientas.resultado();
        if (resultado != null) return resultado;

        return switch (intent == null ? "" : intent.strip().toUpperCase(Locale.ROOT)) {
            case "PEDIDO" -> ultimoPedido(clienteId);
            case "NUMERO_PEDIDO" -> new ChatIAResponseDTO(ChatIAResponseDTO.Estado.CLARIFICATION_REQUIRED,
                    "Indica el número del pedido que quieres cancelar.");
            case "RESERVA" -> ultimaReserva(clienteId);
            case "HORARIO", "DIRECCION", "ATENCION" ->
                    datosRestaurante(intent.strip().toUpperCase(Locale.ROOT), restauranteId);
            case "CANCELACION" -> respuesta("Desde Mis reservas puedes cancelar una reserva pendiente "
                    + "o confirmada con más de dos horas de anticipación y sin llegada registrada. "
                    + "Si ya estás dentro de ese plazo, comunícate con el restaurante.");
            default -> new ChatIAResponseDTO(ChatIAResponseDTO.Estado.CLARIFICATION_REQUIRED,
                    "Puedo consultar tus pedidos, ayudarte a cancelarlos o revisar tu última reserva. "
                    + "Para consultar la carta, horarios, dirección o atención, selecciona primero el restaurante.");
        };
    }

    private ChatIAResponseDTO cancelarPedido(String numero) {
        try {
            Long pedidoId = Long.valueOf(numero);
            gestionPedidos.cancelarPedidoPropio(pedidoId);
            return new ChatIAResponseDTO(ChatIAResponseDTO.Estado.ANSWER,
                    "Tu pedido #" + pedidoId + " está cancelado.", null, pedidoId);
        } catch (ResourceNotFoundException | NumberFormatException ex) {
            return new ChatIAResponseDTO(ChatIAResponseDTO.Estado.NO_DATA, "No encontré ese pedido en tu historial.");
        } catch (BusinessRuleException ex) {
            return new ChatIAResponseDTO(ChatIAResponseDTO.Estado.ANSWER, ex.getMessage(), null, Long.valueOf(numero));
        }
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
                        + pedido.getEstado().name().toLowerCase(Locale.forLanguageTag("es")).replace('_', ' ') + ".",
                        null, pedido.getId()))
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