package com.restopilot.backend.modules.pedidos.model;

import com.restopilot.backend.modules.pedidos.entity.Pedido;
import com.restopilot.backend.modules.pedidos.entity.DetallePedido;
import com.restopilot.backend.modules.catalogo.entity.Plato;
import java.util.*;

/** Una sola regla para el orden de cocina y los metadatos que se muestran. */
public final class PedidoPrioridad {
    private PedidoPrioridad() {}
    private static List<Plato> platos(Pedido pedido) {
        return pedido.getDetalles()==null ? List.of() : pedido.getDetalles().stream()
            .map(DetallePedido::getPlato).filter(Objects::nonNull).toList();
    }
    private static int rango(String value) {
        return switch(value==null ? "" : value.trim().toUpperCase(Locale.ROOT)) {
            case "ALTA" -> 3; case "MEDIA" -> 2; case "BAJA" -> 1; default -> 0;
        };
    }
    public static int complejidad(Pedido pedido) {
        return platos(pedido).stream().mapToInt(p->rango(p.getComplejidad())).max().orElse(0);
    }
    public static String complejidadMaxima(Pedido pedido) {
        return switch(complejidad(pedido)) {case 3 -> "ALTA"; case 2 -> "MEDIA"; case 1 -> "BAJA"; default -> null;};
    }
    public static Integer tiempoMaximo(Pedido pedido) {
        return platos(pedido).stream().map(Plato::getTiempoPreparacionMinutos).filter(Objects::nonNull)
            .filter(t->t>=0).max(Integer::compareTo).orElse(null);
    }
    public static int tiempoParaOrden(Pedido pedido) {
        Integer tiempo=tiempoMaximo(pedido);return tiempo==null?0:tiempo;
    }
}
