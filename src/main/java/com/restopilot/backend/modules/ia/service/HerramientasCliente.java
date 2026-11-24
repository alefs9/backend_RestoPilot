package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.catalogo.dto.PlatoResponseDTO;
import com.restopilot.backend.modules.catalogo.service.PlatoService;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO.Estado;
import com.restopilot.backend.modules.pedidos.entity.EstadoPedido;
import com.restopilot.backend.modules.pedidos.service.PedidoService;
import com.restopilot.backend.modules.restaurante.service.ConsultaRestauranteService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class HerramientasCliente {
    private static final int LIMITE_PLATOS = 8;

    private final ConsultaRestauranteService restaurantes;
    private final PlatoService platos;
    private final Long restauranteId;
    private final PedidoService pedidos;
    private ChatIAResponseDTO resultado;
    private RuntimeException fallo;

    HerramientasCliente(ConsultaRestauranteService restaurantes, PlatoService platos, Long restauranteId,
                        PedidoService pedidos) {
        this.restaurantes = restaurantes;
        this.platos = platos;
        this.restauranteId = restauranteId;
        this.pedidos = pedidos;
    }

    @Tool(description = "Consulta platos, precios, ingredientes, alérgenos y disponibilidad de la carta seleccionada.",
            returnDirect = true)
    public boolean consultarCarta(@ToolParam(description = "Nombre del plato o categoría, o cadena vacía para toda la carta.")
                                  String busqueda) {
        return registrar(() -> carta(busqueda));
    }

    @Tool(description = "Recomienda platos disponibles de la carta seleccionada según preferencia vegetariana, "
            + "vegana y todas las restricciones indicadas por el cliente. No certifica ausencia de trazas.", returnDirect = true)
    public boolean recomendarPlatos(
            @ToolParam(description = "VEGETARIANA, VEGANA o SIN_PREFERENCIA si solo solicita excluir alérgenos.")
            PreferenciaAlimentaria preferencia,
            @ToolParam(description = "Todas las restricciones explícitas: GLUTEN, LACTEOS (incluye lactosa), HUEVO, MANI, "
                    + "FRUTOS_SECOS, SOJA, PESCADO, MARISCOS, SESAMO. Lista vacía si no hay. "
                    + "Incluye OTRA si alguna no está soportada; nunca omitas restricciones.")
            List<RestriccionAlimentaria> restricciones) {
        return registrar(() -> new RecomendacionesAlimentarias(restaurantes, platos)
                .recomendar(restauranteId, preferencia, restricciones));
    }

    @Tool(description = "Consulta el estado de un pedido propio por su número.", returnDirect = true)
    public boolean consultarPedido(@ToolParam(description = "Número de pedido indicado por el cliente.") Long pedidoId) {
        return registrar(() -> pedido(pedidoId, false));
    }

    @Tool(description = "Indica al cliente cómo confirmar la cancelación de un pedido propio. Esta herramienta solo lee datos.",
            returnDirect = true)
    public boolean prepararCancelacion(@ToolParam(description = "Número de pedido a cancelar indicado por el cliente.") Long pedidoId) {
        return registrar(() -> pedido(pedidoId, true));
    }

    private ChatIAResponseDTO pedido(Long pedidoId, boolean solicitarConfirmacion) {
        if (pedidoId == null || pedidoId <= 0) {
            return new ChatIAResponseDTO(Estado.CLARIFICATION_REQUIRED, "Indica el número de tu pedido.");
        }
        try {
            var pedido = pedidos.getById(pedidoId);
            String estado = pedido.estado().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (solicitarConfirmacion && pedido.estado() == EstadoPedido.PENDIENTE) {
                return new ChatIAResponseDTO(Estado.CLARIFICATION_REQUIRED,
                        "Para confirmar, envía este mensaje: Confirmar cancelar pedido #" + pedidoId, null, pedidoId);
            }
            return new ChatIAResponseDTO(Estado.ANSWER,
                    "Tu pedido #" + pedidoId + " está " + estado + ".", null, pedidoId);
        } catch (ResourceNotFoundException ex) {
            return new ChatIAResponseDTO(Estado.NO_DATA, "No encontré ese pedido en tu historial.");
        }
    }

    ChatIAResponseDTO resultado() {
        return resultado;
    }

    RuntimeException fallo() {
        return fallo;
    }

    private boolean registrar(Supplier<ChatIAResponseDTO> consulta) {
        if (resultado == null && fallo == null) {
            try {
                resultado = consulta.get();
            } catch (RuntimeException ex) {
                fallo = ex;
            }
        }
        return fallo == null;
    }

    private ChatIAResponseDTO carta(String busqueda) {
        if (restauranteId == null) {
            return new ChatIAResponseDTO(Estado.CLARIFICATION_REQUIRED, "Selecciona un restaurante para consultar su carta.");
        }
        try {
            if (!restaurantes.consultar(restauranteId).activo()) return restauranteNoDisponible();
        } catch (ResourceNotFoundException ex) {
            return restauranteNoDisponible();
        }

        String query = normalizar(busqueda).strip();
        var coincidencias = platos.listar(restauranteId, null).stream()
                .filter(plato -> query.isEmpty() || normalizar(plato.getNombre()).contains(query)
                        || normalizar(plato.getCategoriaNombre()).contains(query))
                .sorted(Comparator.comparing(PlatoResponseDTO::getNombre, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PlatoResponseDTO::getId))
                .limit(LIMITE_PLATOS + 1L)
                .toList();
        if (coincidencias.isEmpty()) {
            return new ChatIAResponseDTO(Estado.NO_DATA, "No encontré platos para esta consulta en la carta seleccionada.");
        }
        String detalles = coincidencias.stream().limit(LIMITE_PLATOS).map(this::describirPlato)
                .collect(Collectors.joining(" "));
        String aviso = coincidencias.size() > LIMITE_PLATOS ? " Pide un plato o categoría para acotar la búsqueda." : "";
        return new ChatIAResponseDTO(Estado.ANSWER, detalles + aviso);
    }

    private ChatIAResponseDTO restauranteNoDisponible() {
        return new ChatIAResponseDTO(Estado.NO_DATA, "El restaurante seleccionado no está disponible.");
    }

    private String describirPlato(PlatoResponseDTO plato) {
        String precio = plato.getPrecio() == null ? "precio sin registrar" : "S/ " + plato.getPrecio().toPlainString();
        String disponibilidad = plato.getDisponible() == null ? "disponibilidad sin registrar"
                : plato.getDisponible() ? "disponible" : "agotado";
        return "%s (%s, %s). Ingredientes: %s. Alérgenos: %s.".formatted(
                plato.getNombre(), precio, disponibilidad, datoCarta(plato.getIngredientes()), datoCarta(plato.getAlergenos()));
    }

    private String datoCarta(String valor) {
        return valor == null || valor.isBlank() ? "sin información registrada" : valor.strip();
    }

    private String normalizar(String valor) {
        return valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
