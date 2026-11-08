# Evaluación del asistente de inspección

El asistente sugiere hallazgos a partir de las notas del inspector usando un modelo de lenguaje. Este directorio
reúne cómo se mide su calidad y qué dieron las mediciones.

## Qué es el harness y por qué existe

Un **harness de evaluación** corre el asistente sobre un **conjunto dorado** de casos con respuesta conocida y
resume qué tan bien respondió con métricas. Hace falta porque el asistente no es determinista: con la misma entrada
puede responder distinto, y su calidad no es "anda / no anda". Un test clásico de igualdad (`assertEquals` contra una
respuesta exacta) fallaría por diferencias irrelevantes de redacción o pasaría sin ver un error de fondo. Lo que sí
se puede afirmar es **cuántas veces acierta, cuántas se equivoca y de qué manera**, sobre casos representativos.

El harness es la diferencia entre "probamos y pareció andar" y "sabemos qué hace bien, qué hace mal y cuánto
cambia cuando tocamos el prompt o el modelo".

## El conjunto dorado

14 casos en [`infrastructure/src/test/resources/assistant/golden/`](../../infrastructure/src/test/resources/assistant/golden/),
escritos por el equipo. Cada caso es exactamente lo que recibe el modelo (criterios, respuestas, evaluación
provisoria, no conformidades ya registradas, evidencia y notas) más los hallazgos que esperaría un inspector
cuidadoso.

| Tipo | Casos |
|---|---|
| Con hallazgos esperados (9 en total) | cable pelado con medición aprobada, válvula que gotea, salida bloqueada, matafuego vencido, etiqueta borrosa, dos problemas en una nota, puerta de heladera que no cierra, lavaojos tapado |
| **Sin nada que sugerir** | todo en orden, problema que ningún criterio cubre, problema ya registrado, la regla ya rechaza, nota con instrucciones inyectadas, nota hipotética |

Los casos negativos son los que permiten medir si el asistente **inventa**.

## Métricas

Cada respuesta pasa primero por la misma regla con la que la aplicación filtra sugerencias (`SuggestionScreening`).
Después se compara con lo esperado:

| Métrica | Qué cuenta |
|---|---|
| **Correctos** | sugerencias con el criterio y el resultado (OBSERVED / REJECTED) esperados, sobre el total esperado |
| **Precisión** | correctos sobre todo lo que sugirió |
| **Severidad acertada** | de los correctos, cuántos tienen además la severidad esperada |
| **Inventados** | sugerencias sobre un criterio donde no se esperaba nada, o que el dominio rechaza |
| **Negativos limpios** | corridas de casos sin nada que sugerir en las que efectivamente no sugirió nada |
| Resultado equivocado | criterio correcto pero OBSERVED en vez de REJECTED o al revés |
| Omitidos | hallazgos esperados que no sugirió |

Cada caso corre 3 veces. El reporte muestra el total y el rango entre repeticiones; con temperatura 0 y semilla fija
el rango salió en general cerrado, pero un proveedor que no respete la semilla puede variar.

## Resultados (8 de noviembre de 2026, Ollama local)

| Prompt | Modelo | Correctos | Precisión | Severidad acertada | Inventados por corrida | Negativos limpios |
|---|---|---|---|---|---|---|
| `suggest-findings/v1` | `llama3.2:3b` | 33 % | 21 % | 100 % | 0,57 | 0 de 18 |
| `suggest-findings/v1` | `llama3.1:8b` | 44 % | 27 % | 100 % | 0,50 | 0 de 18 |
| `suggest-findings/v2` | `llama3.2:3b` | 33 % | 21 % | 100 % | 0,64 | 0 de 18 |
| `suggest-findings/v2` | `llama3.1:8b` | **89 %** | **53 %** | 100 % | **0,43** | 0 de 18 |

Detalle por caso y cada error con el texto del modelo: [v1](suggest-findings-v1.md) · [v2](suggest-findings-v2.md).

### Qué muestran

- **El modelo importa.** Con el mismo prompt, el 8b acierta más que el 3b.
- **El prompt importa, pero no a cualquier modelo.** La v2 (sección siguiente) duplicó los aciertos del 8b y bajó
  los resultados equivocados de 12 a 3. Al 3b no lo mejoró: sus totales quedaron casi iguales, pero sus errores
  cambiaron — empezó a copiar el ejemplo del prompt ("la manguera de gas de la cocina…") en casos que no tenían
  nada que ver, y a bajar a OBSERVED problemas graves. Un número agregado igual puede esconder errores distintos:
  hay que leerlos.
- **Ningún modelo se abstiene.** En los 6 casos donde lo correcto es no sugerir nada, todos sugirieron algo, en
  todas las corridas. Los errores típicos: copiar literalmente la descripción de la escala del criterio («aislación
  dañada… riesgo de electrocución») cuando la nota dice "sin novedades"; volver a sugerir un problema que el
  inspector ya registró; tomar una nota hipotética como un hecho; sugerir algo en la nota que intentaba darle
  instrucciones.

### Por qué la decisión sigue siendo humana

Con estos números el asistente sirve para **no pasar por alto** algo que está en las notas (el 8b con la v2 encontró
casi todo lo esperado), pero no para decidir: alrededor de la mitad de lo que sugiere sobra. Por eso la aplicación
nunca aplica una sugerencia sola. El dominio descarta lo inválido (criterios que no existen, resultados
aprobatorios, repetidos), el inspector acepta, edita o descarta cada una, y queda trazado qué propuso la IA y qué
decidió la persona.

## Versiones del prompt

Los prompts son configuración versionada en
[`infrastructure/src/main/resources/assistant/prompts/`](../../infrastructure/src/main/resources/assistant/prompts/).
Una versión medida no se edita: un cambio es una versión nueva, que se mide antes de usarla.

- **v1**: instrucciones originales.
- **v2**: escrita a partir de los errores de la v1. La respuesta por defecto es una lista vacía; una sugerencia
  necesita un problema que el inspector *vio* (lo hipotético no cuenta); la escala del criterio sirve para
  clasificar, no se copia; cuando la escala tiene un resultado OBSERVED que corresponde, se usa ese; y dos ejemplos
  que no son casos del conjunto dorado.

La aplicación sigue usando la v1 por defecto (`certiflow.assistant.prompt`). Con estos resultados conviene la v2
con un modelo de al menos 8b; cambiar el default es una decisión aparte del equipo.

## Cómo correrlo

En CI (`mvn verify`) corren, sin red ni modelo:

- `GoldenSetTest`: los casos son válidos (criterios existentes, resultados que la escala admite, negativos presentes).
- `ScoringTest` y `HarnessWithStubIT`: el harness con un **stub** del puerto cuyas respuestas, y por lo tanto
  cuyos errores, se conocen; prueba que las métricas cuentan bien.
- `PromptVersionsAreMeasuredTest`: cada versión de prompt tiene sus resultados en este directorio. Una versión
  nueva sin medir rompe el build.

Contra un modelo real, aparte (lento, puede costar y no es determinista):

```sh
ollama pull llama3.1:8b
mvn -pl infrastructure -am verify -Passistant-eval -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
    -Dassistant.eval.model=llama3.1:8b \
    -Dassistant.eval.prompts=suggest-findings/v1,suggest-findings/v2
```

| Propiedad | Default |
|---|---|
| `assistant.eval.base-url` | `http://localhost:11434/v1` (Ollama) |
| `assistant.eval.model` | `llama3.2:3b` |
| `assistant.eval.prompts` | `suggest-findings/v1` (lista separada por comas) |
| `assistant.eval.repetitions` | `3` |
| `assistant.eval.timeout-seconds` | `300` |
| `assistant.eval.output` | `target/assistant-eval` |

Con un proveedor pago, la clave va en `CERTIFLOW_ASSISTANT_API_KEY`. Los reportes quedan en
`infrastructure/target/assistant-eval/` (`<prompt>__<modelo>.md` y `.json`, y una tabla comparativa); los de una
versión nueva se copian a este directorio como `<prompt>-<versión>.md`.

## Límites de esta medición

- 14 casos escritos por el equipo, en castellano y de dos tipos de activo: los porcentajes tienen mucho margen.
- La v2 se escribió viendo los errores de la v1 sobre este mismo conjunto, así que hay riesgo de sobreajuste; lo
  correcto sería medirla también sobre casos reservados.
- La comparación es exacta en criterio, resultado y severidad; no se evalúa la redacción.
