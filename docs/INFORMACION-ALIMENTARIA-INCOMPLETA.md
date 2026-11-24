# Información alimentaria incompleta

Rama `feature/informacion-alimentaria-incompleta`, tarea S3-TSK-008. Base: `develop` 76a4e30, que ya integra las recomendaciones del PR #43.

## Qué cambia

El asistente distingue ingredientes ausentes, alérgenos ausentes y declaraciones que no puede interpretar. Explica el motivo por plato y solicita consultar al personal antes de elegir. No completa recetas por el nombre ni trata la falta de datos como ausencia de alérgenos.

La herramienta `consultarCompatibilidad` permite preguntar por un plato concreto y todas las restricciones indicadas. Se usa el restaurante seleccionado en el chat; el modelo no puede sustituir ese ID. El nombre se normaliza sin tildes y sin distinguir mayúsculas. Una coincidencia exacta tiene prioridad; si hay varias, se solicita nombre completo o ID con `#`. Los IDs también se buscan únicamente en la carta seleccionada.

No se cambian el endpoint, autenticación ni DTO del chat: se sigue respondiendo con `estado` y `mensaje`. Tampoco se modifica la base de datos o el frontend.

## Reglas y respuestas

| Situación | Respuesta |
| --- | --- |
| Falta restaurante, plato o restricción/preferencia soportada | `CLARIFICATION_REQUIRED`, solicita precisar la consulta |
| Varias coincidencias | `CLARIFICATION_REQUIRED`, lista hasta ocho nombres e IDs |
| Plato fuera del restaurante, restaurante inactivo o plato sin disponibilidad confirmada | `NO_DATA` |
| Falta composición para decidir | `NO_DATA`, identifica qué datos faltan y explica que no puede confirmar compatibilidad |
| Texto presente pero no interpretable | `NO_DATA`, indica que la composición/declaración no se puede interpretar; no afirma que el campo esté vacío |
| Evidencia de incompatibilidad aunque falten otros datos | `ANSWER`, informa incompatibilidad y no recomienda el plato |
| Datos evaluables y compatibles | `ANSWER`, limita la conclusión a los datos registrados e incluye la advertencia sobre trazas |

Los campos nulos, vacíos y marcadores completos como «sin información», «no registrado», «pendiente», «por confirmar», «n/a» o «-» cuentan como información ausente. Una declaración explícita «ninguno» en alérgenos conserva su significado anterior. No se usa esa declaración para ignorar un ingrediente incompatible.

El diagnóstico es inmutable y conserva todos los motivos aplicables. El método anterior `evaluar` sigue disponible para sus consumidores. La evidencia positiva de incompatibilidad se procesa aunque falten ingredientes; por ejemplo, un alérgeno «maní» impide recomendar para esa restricción aunque la lista de ingredientes esté vacía.

Las recomendaciones generales mantienen los platos evaluables en su sección y muestran por separado hasta ocho platos **no recomendados por información insuficiente**. Si todos son incompatibles, se indica eso; si no hay disponibles, se distingue de los datos incompletos. Cada consulta lee la carta actual, sin almacenar un diagnóstico entre mensajes.

## Pruebas

Resultado ejecutado: 70 casos específicos del asistente correctos. Suite completa: 211 casos, 0 fallos, 0 errores y 1 caso de navegador US23 omitido por su condición de entorno preexistente. `git diff --check` sin incidencias.

Java 21; pruebas H2 y modelo controlado, sin llamadas a Gemini ni bases compartidas:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DiagnosticoAlimentarioTest,ConsultaCompatibilidadAlimentariaTest,RecomendacionesIncompletasTest,EvaluadorAlimentarioTest,RestriccionesAlimentariasTest,RecomendacionesAlimentariasTest,AgenteIAHttpIntegrationTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress test
```

Cobertura: campos ausentes y marcadores, motivos simultáneos, composición desconocida, incompatibilidad con datos parciales, restricciones no soportadas, nombre ambiguo y selección por ID, aislamiento de restaurante, disponibilidad, respuestas mixtas y límites. La prueba HTTP usa datos persistidos y verifica que completar la carta cambia la respuesta siguiente sin inventar información.

Pendiente: comprobar selección de herramienta y extracción de restricciones con Gemini real. El vocabulario conservador y sus limitaciones se documentan en [recomendaciones alimentarias](RECOMENDACIONES-ALIMENTARIAS.md). Esta tarea no amplía el vocabulario ni certifica ausencia de contaminación cruzada.
