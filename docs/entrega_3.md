# Entrega 3: propuesta de valor y presentación final

Este documento resume únicamente lo que agrega la Entrega 3. Los requisitos funcionales generales de
[entrega_1.md](entrega_1.md) y las funcionalidades y requisitos no funcionales de
[entrega_2.md](entrega_2.md) siguen vigentes y no se repiten.

## Propuesta de valor: asistente con IA y harness de evaluación

La propuesta de valor es un requerimiento adicional que se implementa sobre la aplicación existente y se integra a
su arquitectura. Consiste en incorporar un **asistente basado en un modelo de lenguaje (LLM)** que ayude al inspector,
junto con el **harness de evaluación** que permite confiar en él. El valor no está solo en llamar a un modelo: está en
integrarlo detrás de un puerto propio, con límites claros y con una forma verificable de medir su calidad, que es lo
que diferencia un experimento de una funcionalidad profesional.

| Elemento | Requisito |
|---|---|
| Funcionalidad asistida | Al menos una: sugerir hallazgos y severidades a partir de las observaciones y evidencias registradas, o redactar el borrador del acta de inspección. |
| Guardrails | Las sugerencias nunca cierran una inspección ni emiten certificados; un humano las acepta o descarta, y queda trazado qué fue sugerido por la IA y qué decidió el inspector. |

## Requisitos no funcionales

### Arquitectura

La aplicación conserva la arquitectura elegida (Clean Architecture, DDD o Hexagonal). La propuesta de valor se
integra detrás de contratos propios: el módulo de dominio sigue libre de dependencias de framework y de proveedores
externos, y la lógica de negocio no se duplica en la capa nueva.

### Requisitos de la propuesta de valor

| Elemento | Requisito |
|---|---|
| Puerto propio | Un contrato como `InspectionAssistant` definido por el dominio; la aplicación no depende del proveedor del modelo. |
| Harness de evaluación | Un conjunto dorado de al menos diez casos reales o realistas (observaciones de entrada y hallazgos esperados), ejecutable de forma automatizada, con métricas simples: hallazgos correctos, severidades acertadas y hallazgos inventados. |
| Versionado de prompts | Los prompts se tratan como configuración versionada; un cambio de prompt vuelve a ejecutar el harness y compara resultados. |
| CI determinista | La CI usa una implementación stub del puerto; el harness contra el modelo real se ejecuta por separado. |

## Entregables

| Entregable | Contenido mínimo |
|---|---|
| Repositorio | URL del repositorio de GitHub y hash del último commit que corresponde a la entrega. |
| Código de la aplicación | Aplicación completa con la propuesta de valor integrada, cumpliendo los requisitos funcionales y no funcionales. |
| Tests | Los indicados en los requisitos no funcionales, ejecutables en la integración continua. |
| Justificación escrita | Solo si el equipo sostiene que la propuesta no aplica al dominio. |
| Presentación oral | Frente a toda la clase, con demostración en vivo. |
| Decisiones de diseño | `DESIGN.md` en la raíz: qué patrón o principio se aplicó, dónde y por qué; qué alternativas se descartaron; y qué patrones se decidió no aplicar, con sus consecuencias. |

Si el equipo considera que la propuesta no aplica a su dominio, debe entregar una justificación técnica escrita y
defenderla en la presentación, indicando qué problemas o costos tendría incorporarla y qué alternativa aportaría un
valor equivalente.

## Presentación oral

Presenta un integrante elegido al azar al comienzo de la clase, así que **todos deben estar preparados para
presentar el trabajo completo**. El resto del grupo puede participar en las preguntas. La gestión del tiempo es parte
de la evaluación.

| Momento | Contenido |
|---|---|
| Dominio | Problema, actores, reglas centrales y puntos de extensión del diseño, explicado para una audiencia que no conoce el dominio. |
| Propuesta de valor | Qué es, por qué aporta valor en este dominio, cómo se integró a la arquitectura y demostración. |
| Decisiones clave | Las decisiones de implementación más importantes: alternativas consideradas, decisión tomada y consecuencias observadas. |
| Preguntas | Del docente y de la clase, dirigidas a cualquier integrante. |

En la presentación hay que explicar qué es un harness de evaluación y por qué un componente no determinista no puede
probarse solo con tests clásicos de igualdad, mostrar los resultados del conjunto dorado y discutir los límites: qué
errores comete el asistente, cómo se detectan y por qué la decisión final sigue siendo humana.

## Qué no se exige

No se exige rehacer la aplicación ni incorporar funcionalidades ajenas a la propuesta de valor. El esfuerzo debe
concentrarse en integrarla bien a la arquitectura existente y en poder explicar cada decisión.
