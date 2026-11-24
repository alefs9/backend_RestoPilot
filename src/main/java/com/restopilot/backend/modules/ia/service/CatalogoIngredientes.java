package com.restopilot.backend.modules.ia.service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.restopilot.backend.modules.ia.service.RestriccionAlimentaria.*;

/** Vocabulario explícito: una preparación compuesta desconocida nunca se infiere por su nombre. */
final class CatalogoIngredientes {
    record Perfil(boolean carne, boolean animal, Set<RestriccionAlimentaria> restricciones) {}

    private static final Map<String, Perfil> INGREDIENTES = crear();

    private CatalogoIngredientes() {}

    static Perfil buscar(String ingrediente) {
        return INGREDIENTES.get(normalizar(ingrediente));
    }

    static String normalizar(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }

    private static Map<String, Perfil> crear() {
        Map<String, Perfil> mapa = new HashMap<>();
        agregar(mapa, false, false, Set.of(),
                "arroz|arroz integral|quinua|quinoa|papa|papas|camote|yuca|choclo|maiz|tomate|tomates|cebolla|cebolla roja|ajo|ajos|zanahoria|lechuga|espinaca|brocoli|coliflor|pepino|palta|aguacate|zapallo|zapallito|berenjena|pimiento|pimientos|champiñones|hongos|lentejas|garbanzos|frejoles|frijoles|habas|arvejas|sal|pimienta|pimienta negra|comino|oregano|cilantro|culantro|perejil|albahaca|aji|aji amarillo|rocoto|limon|lima|naranja|manzana|platano|azucar|aceite|aceite vegetal|aceite de oliva|agua|vinagre|coco|leche de coco|leche de arroz");
        agregar(mapa, true, true, Set.of(),
                "carne|res|carne de res|lomo de res|pollo|pechuga de pollo|cerdo|pavo|cordero|cuy|pato|tocino|jamon|gelatina|caldo de pollo|caldo de carne|manteca de cerdo");
        agregar(mapa, true, true, Set.of(PESCADO),
                "pescado|atun|salmon|trucha|bonito|anchoveta|anchoas|salsa de pescado");
        agregar(mapa, true, true, Set.of(MARISCOS),
                "mariscos|camaron|camarones|langostinos|cangrejo|pulpo|calamar|conchas|ostras|mejillones|salsa de ostras");
        agregar(mapa, false, true, Set.of(LACTEOS),
                "leche|leche de vaca|leche evaporada|leche condensada|queso|queso fresco|queso parmesano|parmesano|mantequilla|crema de leche|yogur|yogurt|suero de leche|caseina");
        agregar(mapa, false, true, Set.of(HUEVO), "huevo|huevos|clara de huevo|yema de huevo|mayonesa");
        agregar(mapa, false, true, Set.of(), "miel|miel de abeja");
        agregar(mapa, false, false, Set.of(GLUTEN),
                "trigo|harina de trigo|cebada|centeno|pan|pan rallado|cuscus|seitan");
        agregar(mapa, false, false, Set.of(SOJA, GLUTEN), "sillao|salsa de soja|salsa de soya");
        agregar(mapa, false, false, Set.of(SOJA), "soja|soya|tofu|leche de soja|leche de soya|edamame");
        agregar(mapa, false, false, Set.of(MANI), "mani|cacahuate|cacahuete|aceite de mani|mantequilla de mani");
        agregar(mapa, false, false, Set.of(FRUTOS_SECOS),
                "almendra|almendras|nuez|nueces|avellana|avellanas|pistacho|pistachos|anacardo|pecanas|leche de almendras");
        agregar(mapa, false, false, Set.of(SESAMO), "sesamo|ajonjoli|aceite de sesamo|tahini");
        return Map.copyOf(mapa);
    }

    private static void agregar(Map<String, Perfil> mapa, boolean carne, boolean animal,
                                Set<RestriccionAlimentaria> restricciones, String nombres) {
        var perfil = new Perfil(carne, animal, restricciones);
        for (String nombre : nombres.split("\\|")) mapa.put(normalizar(nombre), perfil);
    }
}
