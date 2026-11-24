# Recomendaciones alimentarias del cliente

Rama: `feature/recomendaciones-alimentarias`. Alcance: S3-TSK-007.

## Comportamiento

El endpoint existente `POST /api/ia/cliente/chat` conserva su autenticación y contrato (`mensaje`, `restauranteId`). El agente incorpora la herramienta `recomendarPlatos`, que recibe una preferencia (`VEGETARIANA`, `VEGANA` o `SIN_PREFERENCIA`) y todas las restricciones expresadas. El restaurante procede de la solicitud autenticada, nunca de los argumentos generados por el modelo.

Las recomendaciones se construyen en Java a partir de `PlatoService.listar`. No se usa el texto libre de Gemini como lista de platos ni se inventan precios o ingredientes. Solo se incluyen platos del restaurante seleccionado, activo, con disponibilidad explícita y composición evaluable. El resultado se ordena por nombre e ID y muestra hasta ocho opciones después de filtrar. La respuesta incluye ingredientes, alérgenos y precio registrados.

Restricciones soportadas: gluten, lácteos (se excluyen también ante una solicitud sin lactosa), huevo, maní, frutos secos, soja, pescado, mariscos y sésamo. Se aplican conjuntamente con la preferencia. Una restricción adicional no soportada produce aclaración mediante `OTRA`; no debe omitirse para ofrecer resultados parciales.

## Evaluación conservadora

El catálogo actual guarda ingredientes y alérgenos en texto, sin etiquetas dietéticas estructuradas. `CatalogoIngredientes` define un vocabulario explícito y extensible de ingredientes y derivados. Se admiten listas separadas por comas, punto y coma, saltos de línea o «y», sin distinguir mayúsculas ni tildes. Se comparan ingredientes completos: «leche de coco» no se confunde con «leche».

Un ingrediente o preparación compuesta desconocida, una lista vacía o un alérgeno sin interpretar impide recomendar el plato. No se deduce una receta por el nombre del plato ni por palabras sueltas. La lista de alérgenos admite categorías explícitas y declaraciones como «ninguno»; un campo vacío no significa que no haya alérgenos. Evidencia conocida de incompatibilidad prevalece sobre datos faltantes.

Esto favorece omitir candidatos dudosos: no es un analizador exhaustivo de recetas. Al ampliar el vocabulario, registrar la composición concreta y agregar casos de prueba. Preparaciones genéricas como «salsa especial» o «arroz con pollo» deben desglosarse en ingredientes. No se certifican trazas, contaminación cruzada ni seguridad médica; la respuesta indica confirmar la preparación con el restaurante.

## Estados y dependencias

- `ANSWER`: opciones disponibles compatibles según los datos evaluados.
- `CLARIFICATION_REQUIRED`: falta restaurante, preferencia/restricción o hay una restricción no soportada.
- `NO_DATA`: restaurante no disponible o ninguna opción recomendable con los datos actuales.

La respuesta ahora distingue ausencia de platos disponibles, incompatibilidad e información insuficiente. S3-TSK-008 agrega el detalle por plato, respuestas mixtas y consulta de compatibilidad individual; ver [información alimentaria incompleta](INFORMACION-ALIMENTARIA-INCOMPLETA.md).

La base de Spring AI y las herramientas de carta ya están integradas en `develop` (73d28ab); no hay bloqueo pendiente del equipo. No se modificaron el frontend, la base de datos ni el contrato de respuesta.

## Verificación

Resultado ejecutado: suite completa con 184 casos, 0 fallos, 0 errores y 1 caso omitido preexistente en US23. Las pruebas de recomendaciones y del asistente pasaron. `git diff --check` sin incidencias.

Usar Java 21. Las pruebas usan H2 y un modelo controlado; no requieren Gemini ni PostgreSQL compartido.

```powershell
.\mvnw.cmd "-Dtest=EvaluadorAlimentarioTest,RestriccionesAlimentariasTest,RecomendacionesAlimentariasTest,AgenteIAHttpIntegrationTest" test
.\mvnw.cmd test
```

Casos: vegetariano/vegano, nueve restricciones por ingrediente y por declaración, restricciones combinadas, aliases y tildes, composición desconocida o incompleta, disponibilidad y aislamiento de restaurante, orden/límite de resultados y llamada a herramienta desde el endpoint con datos persistidos en H2. Las pruebas HTTP existentes cubren también autenticación, carta y cancelación de pedidos para detectar regresiones.

La selección de intención y extracción de restricciones por el proveedor real deben comprobarse en el entorno con Gemini configurado; las pruebas controladas no acreditan esa parte.
