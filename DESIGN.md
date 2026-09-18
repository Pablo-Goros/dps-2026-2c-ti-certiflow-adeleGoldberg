# Decisiones de diseño

El proyecto corresponde a la Entrega 1: un módulo de dominio Java con modelos, contratos, casos de uso concretos y pruebas. La consigna no exige API REST, persistencia real, frontend, seguridad ni despliegue.

Además de las decisiones técnicas, se explicitan las interpretaciones adoptadas por el equipo para precisar comportamientos que el enunciado deja abiertos. Estas interpretaciones delimitan el alcance del modelo; no son requisitos adicionales impuestos por la consigna.

## Registro enumerado de decisiones

La siguiente tabla resume las decisiones principales. Las secciones posteriores desarrollan
el razonamiento y las consecuencias de cada una.

| ID | Patrón o principio aplicado | Dónde se aplica | Por qué se aplica | Alternativa descartada | Consecuencia |
|---|---|---|---|---|---|
| D1 | Arquitectura hexagonal / inversión de dependencias | Interfaces `application/**/port`, casos de uso y adaptadores de `src/test` | Aislar el negocio de persistencia, web, reloj e identidad | JPA, framework web o repositorios concretos dentro del dominio | Hay más interfaces y una composition root explícita; los adaptadores en memoria no dan durabilidad ni transacciones |
| D2 | Agregados y encapsulamiento de DDD | `Asset`, `InspectionSchema`, `Inspection`, `Finding`, `Certificate` | Concentrar invariantes y separar ciclos de vida | Modelo anémico con setters o un agregado único | Las operaciones pasan por métodos de negocio y la coordinación requiere casos de uso |
| D3 | Value Object e identidad explícita | `AssetId`, `InspectionId`, `SchemaVersionId`, `CriterionId`, `ValidityPeriod`, `CorrectionPlan` | Evitar mezclar identificadores y validar conceptos del dominio | `String`, `Date` y `Map` sin semántica | Más tipos pequeños y conversiones explícitas, con errores inválidos detectados antes |
| D4 | Repository y Query Port | `*Repository`, `*Query`, `AssetDirectory`, `SchemaCatalog` | Separar comandos, consultas y almacenamiento | Acceso directo a colecciones o dependencias de infraestructura | Se puede cambiar el adaptador; la consistencia transaccional queda fuera de esta entrega |
| D5 | Application Service / casos de uso | Paquetes `application/**/usecase` y servicios como `FindingService` | Orquestar varios agregados sin trasladar reglas al controlador o repositorio | Lógica en entidades externas, controladores o una clase fachada gigante | Los flujos son explícitos y testeables, aunque existen más clases de coordinación |
| D6 | Strategy | `EvaluationRule`, `NumericRangeRule`, `YesNoRule`, `MappedOptionsRule` | Agregar reglas de evaluación sin modificar `Criterion` | Subclases de `Criterion`, `if/else` centralizados o motor externo | Cada regla debe validar admisibilidad, publicación y evaluación, y necesita sus propios tests |
| D7 | Domain Service | `CriterionEvaluator` | Combinar la regla del criterio con respuestas y evidencia de una inspección | Poner la evaluación completa en `CriterionRecord` o en `CloseInspection` | La evaluación es reutilizable y aislada; el servicio no posee estado propio |
| D8 | Policy / Specification-like requirements | `CertificateIssuancePolicy`, `IssuanceRequirement`, `IssuanceBlocker`, `CertificationContext` | Calcular todos los bloqueos de emisión de forma extensible y explicable | `if` encadenados o cortar en el primer error | Se informa una decisión rica (`Issued`, `Blocked`, `AlreadyIssued`), pero hay más objetos para una decisión simple |
| D9 | State explícito sin jerarquía State | Enums de `InspectionStatus`, `CorrectiveActionStatus`, `CertificateStatus` y métodos de transición | Proteger transiciones con invariantes sin sobrediseñar ciclos pequeños | Clase por estado o setters públicos | Menos clases, pero cada operación debe verificar su estado permitido |
| D10 | Publish/Subscribe con eventos de dominio | `DomainEventPublisher`, eventos de `finding/event` e `Inspection.CriterionResultRevised`, `CertificationReactions` | Desacoplar acciones e inspecciones de suspensión/reactivación de certificados | Invocar `Certificate` directamente desde cada caso de uso o usar un broker | El despacho de esta entrega es síncrono y en memoria; no hay entrega durable ni atomicidad distribuida |
| D11 | Snapshot y versionado inmutable | `AssetSnapshot`, `SchemaVersion`, `Inspection.frozenSchemaVersionId` | Reconstruir qué activo y reglas existían al iniciar una inspección | Leer siempre el activo y esquema actuales o copiar todo el esquema por inspección | Se conserva historia sin duplicar esquemas; las versiones referenciadas deben permanecer disponibles |
| D12 | Historia explícita y rectificación | `Rectification`, `CriterionEvaluation`, `FindingRevision`, `VoidedObligation`, informes | Corregir hechos sin sobrescribir el original y sin confundir reparación con rectificación | Editar una inspección cerrada, borrar datos o aplicar Event Sourcing completo | Se mantienen estado actual e historial; aumenta el costo de conservar y proyectar cambios |
| D13 | Agregado con colección histórica de acciones | `Finding.correctiveActions()` y `Finding.correctiveAction()` | Mantener una acción vigente sin borrar acciones concluidas cuando reaparece una no conformidad | Reabrir la acción vieja o crear otro hallazgo para el mismo criterio | Hay un hallazgo por criterio y varias acciones históricas; la acción vigente se distingue de sus antecedentes |
| D14 | Composition Root y Test Doubles | `FullSystem`, `InMemory*`, `TestClock`, `SequentialIds`, `FixedActor` | Probar flujos completos determinísticamente sin infraestructura | Tests solo con mocks, base real o reloj del sistema | Se prueban integraciones reales entre casos de uso; no se cubren persistencia ni concurrencia |
| D15 | Proyecciones de consulta / DTO de dominio | `InspectionAct`, `FindingsSummary`, `CertificateReport`, `ReportedValue` | Separar información del dominio de su formato de presentación | Generar PDF o mezclar reporting con entidades | Las salidas son estructuradas y testeables; una capa externa deberá elegir JSON, HTML o PDF |
| D16 | Validación antes de mutar | `Validate`, rectificaciones y constructores de entidades/valores | Evitar cambios parciales cuando una operación inválida afecta varias partes | Mutar y validar paso a paso, o depender de transacciones inexistentes | Se obtiene atomicidad lógica en memoria; la persistencia futura deberá agregar transacciones reales |

### Patrones deliberadamente no aplicados

Además de las alternativas específicas de la tabla, se decidió no aplicar estos patrones
porque no aportan valor en el alcance actual:

- **Singleton**: ocultaría dependencias y dificultaría aislar tests. Se prefieren objetos
	construidos por composición y dependencias por constructor.
- **Service Locator**: produciría dependencias implícitas y haría menos visible qué necesita
	cada caso de uso.
- **Composite**: un criterio tiene una regla en esta entrega; no hay reglas compuestas ni
	secciones anidadas arbitrariamente.
- **Chain of Responsibility**: la emisión necesita devolver todos los bloqueos, no detenerse
	en el primero.
- **Event Sourcing**: la auditoría registra cambios y decisiones, pero el estado actual no
	se reconstruye reproduciendo eventos. Event Sourcing agregaría requisitos de replay,
	versionado de eventos y consistencia que no exige la Entrega 1.
- **CQRS completo**: se separan algunos puertos de comando y consulta, pero no se mantienen
	dos modelos persistidos. Mantenerlos sincronizados sería complejidad sin beneficio para
	un módulo sin persistencia real.
- **Saga, Outbox o broker externo**: los eventos se despachan de forma síncrona en memoria.
	La entrega no requiere procesos distribuidos ni reintentos durables.
- **Motor externo de reglas**: las tres familias de reglas actuales se pueden validar y
	probar directamente en Java; incorporar un DSL dificultaría diagnóstico y trazabilidad.
- **ORM o Active Record**: las entidades no contienen anotaciones ni operaciones de
	persistencia, preservando el aislamiento del dominio.

## 1. Separación entre dominio y aplicación

Se aplica **inversión de dependencias mediante puertos y adaptadores**. Las entidades, reglas y políticas de `domain` contienen el comportamiento del negocio. Los casos de uso de `application` coordinan repositorios, auditoría y eventos a través de interfaces recibidas por constructor. En las pruebas, `FullSystem` compone el sistema con adaptadores en memoria.

Esta separación permite demostrar el dominio sin elegir una base de datos o un framework de aplicación. Los contratos `Clock`, `IdGenerator` y `ActorProvider` permiten controlar tiempo, identificadores y autoría en los tests.

Se descartó acoplar las entidades a JPA o a un framework web porque introduciría infraestructura ajena al objetivo de esta entrega. Tampoco se usan Singleton ni Service Locator para obtener servicios: ocultarían dependencias y dificultarían aislar escenarios.

La consecuencia es una composición más explícita y un mayor número de interfaces. Los adaptadores en memoria no ofrecen durabilidad, aislamiento ni transacciones; esas garantías deberán definirse al incorporar persistencia.

## 2. Entidades, objetos de valor y responsabilidades

**Supuestos adoptados.** El responsable puede ser una persona o una organización. Las características se definen por tipo de activo; marca, modelo y número de serie son una base para equipos, no atributos universales. Las características del equipo no cambian después del alta; responsable y ubicación sí pueden cambiar con auditoría.

Cada criterio observado o rechazado origina un hallazgo que reúne sus motivos y una acción correctiva asociada. El hallazgo se asigna al responsable actual del activo al generarse durante el cierre y no se reasigna automáticamente por cambios posteriores. El seguimiento de la corrección pertenece a la acción; no se agrega al hallazgo otro estado de resuelto/no resuelto.

Se emplea un **modelo de dominio con operaciones de negocio** en `Asset`, `InspectionSchema`, `Inspection`, `Finding` y `Certificate`. Los identificadores específicos, `CorrectionPlan` y `ValidityPeriod` son objetos de valor. Las relaciones entre agregados se expresan principalmente por identificador, mientras `Finding` contiene sus acciones correctivas, con una vigente por vez.

Esta organización concentra invariantes y diferencia el incumplimiento histórico de su reparación. Se descartaron tanto un modelo anémico con setters como un agregado único que contuviera todo el sistema: el primero dispersaría validaciones y el segundo mezclaría ciclos de vida independientes.

La coordinación entre agregados queda en los casos de uso. Para que el encapsulamiento del agregado no dependa de la disciplina de quien escribe el próximo caso de uso, el **límite del paquete coincide con el límite del agregado**: `CriterionRecord` reside junto a `Inspection` y sus operaciones de escritura son de paquete, de modo que desde afuera solo es alcanzable su superficie de lectura. Antes eran públicas y una inspección cerrada podía modificarse sin rectificación; ningún caso de uso las necesitaba.

`CorrectiveAction` recibió el mismo tratamiento y reside junto a `Finding`, con sus cinco operaciones de escritura de paquete y las delegaciones que los casos de uso necesitan en el hallazgo; los objetos de valor siguen en `finding.action`, porque los construyen los llamadores. Ahí el motivo era más fuerte que el encapsulamiento: desde que el hallazgo registra contra qué resultado se verificó su corrección, verificar o anular directamente sobre la acción la cerraba sin actualizar ese registro, y el hallazgo quedaba bloqueando la certificación para siempre sin señal de error.

El tercer agregado con entidades internas no lo necesita: `InspectionSchema` expone su `SchemaDraft`, pero no deriva estado de él y la publicación valida el borrador entero, así que no hay nada que pueda quedar desincronizado. Lo que sí queda abierto es el mapa de características: se exige que cada entrada tenga nombre y valor, pero no se restringen los nombres por tipo de activo, porque RF1 pospone ese catálogo de forma explícita y todavía no hay contra qué validarlos.

## 3. Versiones inmutables y datos históricos

**Decisiones adoptadas.** Cada tipo de activo tiene un único esquema aplicable; un esquema puede servir a varios tipos y activos. Cada inspección corresponde a un activo y un inspector, y comprende el esquema completo. Compartir esquema no comparte respuestas ni evidencias.

Se distinguen asignación e inicio: la primera fija activo, inspector y fecha prevista; el segundo selecciona automáticamente la última versión publicada del esquema aplicable. Los borradores no participan y no se permite iniciar sin una versión publicada.

`InspectionSchema` separa el borrador editable de las versiones publicadas. `StartInspection` fija una `SchemaVersion` y captura un `AssetSnapshot` con identidad, tipo, características, ubicación y datos básicos del responsable. Esos datos históricos permanecen estables incluso mientras la inspección sigue abierta.

Se eligieron **versiones compartidas e inmutables y capturas de datos mutables** para cumplir la conservación de reglas exigida por la consigna. Consultar siempre los datos actuales alteraría los antecedentes; copiar el esquema completo en cada inspección duplicaría contenido ya identificado por versión.

Las versiones referenciadas deben seguir siendo recuperables. Las reglas actuales son inmutables y cualquier extensión deberá conservar esa propiedad. El responsable capturado al inicio puede diferir del responsable al que se asigna un hallazgo al cierre: representan momentos distintos.

## 4. Estrategias de evaluación y carga progresiva

**Decisiones adoptadas.** Cada criterio pertenece a una sección y tiene una regla de rango numérico, sí/no u opciones con resultado asignado, además de requisitos de evidencia. La severidad depende del resultado concreto. Antes de publicar se exige un resultado único para toda respuesta admitida; las bandas numéricas declaran unidad y límites sin huecos ni superposiciones.

Interpretamos «registro progresivo» como permitir cargas incompletas que puedan continuarse, corregirse o eliminarse con auditoría. La carga valida formatos, tipos y opciones; una medición válida fuera del rango de aprobación se registra. La evaluación ordinaria ocurre al cerrar. Se permite cerrar con faltantes obligatorios, rechazando los criterios afectados con motivo explícito. Repetir el cierre no debe reevaluar ni duplicar hallazgos y acciones. Corregir y eliminar alcanza a los cuatro tipos de registro, incluidas las observaciones; corregir una observación conserva autor e instante, porque quién observó y cuándo son hechos y solo el texto se corrige. Después del cierre las cuatro operaciones se rechazan y exigen rectificación.

Las fotos y documentos se representan por referencias a archivos. El inspector determina su pertinencia y el sistema verifica la presencia exigida por tipo y cantidad. Si falta evidencia, se registra qué se exigía y qué no se presentó.

Se aplica **Strategy**: `Criterion` compone una `EvaluationRule` y `CriterionEvaluator` combina su resultado con los faltantes de evidencia. Esto permite incorporar otra regla sin crear una subclase de criterio para cada variante.

Se descartó un motor externo de reglas por la complejidad adicional de ejecución y diagnóstico. Tampoco se aplica Composite: inicialmente hay una regla por criterio y no se necesitan combinaciones ni secciones anidadas arbitrariamente.

Cada nueva estrategia necesita pruebas de admisibilidad y evaluación. La severidad de faltantes está fijada en `EvaluationReason.MISSING_MANDATORY_DATA`. Los requisitos de evidencia se identifican por etiqueta, y `Criterion` exige que sean únicas dentro del criterio: sin esa unicidad una adjunción no podía atribuirse a un requisito concreto y un faltante obligatorio podía darse por cubierto.

## 5. Ciclos de vida con estados explícitos

**Decisiones adoptadas.** Planificar una acción consiste en indicar trabajo, ejecutor y fecha límite. El responsable del hallazgo elige la solución; una vez confirmados esos datos no se modifican. El ejecutor informa la realización con evidencia y el inspector verifica. Si la verificación falla, se conserva el intento y la misma acción permanece abierta.

Esa separación de responsabilidades se verifica, no solo se documenta. Quién ejecuta es un dato del plan, así que `CorrectiveAction` rechaza por sí misma un informe de ejecución firmado por otra parte. Quién verifica exige conocer la inspección de respaldo, que está en otro agregado, de modo que la comprobación vive en `VerifyCorrectiveAction`, igual que la del predecesor en `IssueCertificate`. Sin ellas, el mismo actor podía informar una corrección ajena y darla por buena. Una referencia de evidencia en blanco tampoco es evidencia: se valida cada elemento de la lista y no solo que la lista tenga alguno.

Cumplir el plazo exige verificación satisfactoria y cierre antes del vencimiento. Una acción vencida admite ejecución y verificación tardías, pero conserva el incumplimiento del plazo incluso después de cerrarse. El plan se modela como datos de la acción, sin exigir un documento independiente.

Inspecciones, acciones y certificados usan **estados explícitos y métodos con condiciones de transición**. No se aplica State mediante una clase por estado porque los ciclos iniciales son pequeños. En `CorrectiveAction`, `deadlineBreached` separa el antecedente de vencimiento del progreso de ejecución.

Tratar el vencimiento como un estado terminal impediría la corrección tardía. La separación elegida permite avanzar sin borrar ese antecedente, aunque exige comprobar conjuntamente estado y plazo.

El tiempo se recibe explícitamente y los casos de uso de barrido materializan vencimientos. Como el barrido puede no haberse ejecutado todavía, conviven dos nociones de vencimiento: el estado `EXPIRED`, que es el barrido ya aplicado, y el instante de `ValidityPeriod`, que es el hecho. Toda operación de `Certificate` consulta ambas por un único predicado interno, de modo que un certificado vencido en el tiempo no puede suspenderse ni reactivarse por no haber sido marcado aún. Antes suspender miraba solo el estado y podía dejar un certificado muerto en `SUSPENDED`.

## 6. Cierre, elegibilidad y emisión como decisiones separadas

**Política adoptada.** Una observación exige corrección en plazo, pero permite certificar si su acción está planificada. Un rechazo bloquea hasta verificar la corrección, sin exigir repetir toda la inspección ni reescribir su resultado histórico. La emisión requiere una inspección cerrada, ningún rechazo sin corregir y ninguna acción abierta vencida.

La emisión se solicita explícitamente y cada inspección respalda como máximo un certificado; una solicitud repetida identifica el existente. Al emitir se conserva su vencimiento. Renovar exige que el anterior haya vencido, una nueva inspección completa y un nuevo certificado vinculado al anterior. Las acciones de inspecciones anteriores conservan su historia, pero no bloquean la renovación ni suspenden el certificado nuevo. El vínculo con el certificado anterior se comprueba: debe existir y pertenecer al mismo activo, y ninguno puede sucederse a sí mismo. Emitir con predecesor solo es alcanzable desde `RenewCertificate`, de modo que no puede eludirse la exigencia de que el anterior haya vencido.

`CloseInspection` evalúa y registra no conformidades. `CertificationContextAssembler` reúne el estado necesario y `CertificateIssuancePolicy` evalúa requisitos independientes mediante `IssuanceRequirement`. `IssueCertificate` y `RenewCertificate` coordinan las operaciones; `CertificateValidityPolicy` separa el cálculo de vigencia.

Esta **composición de políticas** mantiene las decisiones fuera de los repositorios y permite informar todos los bloqueos. Se descartó emitir automáticamente al cerrar porque puede ser necesario completar correcciones antes de solicitar certificación. Tampoco se usa Chain of Responsibility con interrupción en el primer fallo: interesa explicar todos los impedimentos.

Los resultados son variantes explícitas: emitido, bloqueado o ya emitido, sin crear un certificado rechazado. La implementación también limita a uno los certificados no vencidos por activo; esa restricción adicional requiere revisar su alcance. Los doce meses elegidos en los tests no establecen una duración universal.

## 7. Eventos para coordinar efectos entre agregados

**Política adoptada.** Un certificado se suspende por vencimiento de una acción asociada o por un rechazo descubierto al rectificar su inspección de respaldo. Una observación nueva no suspende de inmediato. Se conservan todas las causas y la reactivación ocurre al resolver la última, siempre que el certificado no haya vencido. Reactivar conserva el vencimiento original; si ya venció, corresponde renovar.

Los casos de uso publican eventos como `CorrectiveActionExpired`, `CorrectiveActionClosed`, `CorrectiveActionVoided` y `CriterionResultRevised`. `CertificationReactions` los consume para modificar certificados. Se usa **publicación/suscripción**, con despacho síncrono en memoria en las pruebas.

Así, las operaciones sobre acciones e inspecciones comunican hechos sin conocer cómo se actualiza un certificado. Invocar certificación desde esas entidades introduciría dependencias entre ciclos de vida. Un broker, una saga o un outbox durable no se incorporan en esta entrega.

La composición debe registrar el consumidor. No hay garantías de entrega durable ni atomicidad entre guardados, auditoría y eventos. Los errores de validación deben evitar cambios parciales incluso en memoria; los eventos por sí solos no resuelven esa consistencia.

## 8. Rectificaciones e historial sin Event Sourcing

**Decisiones adoptadas.** Después del cierre, el inspector asignado puede rectificar errores en notas, respuestas, mediciones y referencias de evidencia con motivo, autor, fecha y valores anteriores y nuevos. No puede cambiar activo ni versión. Se reevalúan los criterios afectados con las reglas originales y se conserva su historia. Una reparación posterior pertenece a la acción correctiva.

Si la rectificación elimina el incumplimiento, se conservan hallazgo y acción, pero se anula la obligación y sus efectos sobre la certificación. Esto permite levantar la última causa de suspensión de un certificado no vencido sin inventar una ejecución o verificación. Si aparece un incumplimiento antes inexistente, se generan hallazgo y acción y se aplican las políticas de suspensión.

**Reaparición de un incumplimiento ya resuelto.** RF10 dejó sin definir qué ocurre cuando el incumplimiento vuelve después de que la acción concluyó, sea porque se anuló la obligación o porque la corrección se verificó y cerró. Se adoptó aceptar la rectificación igual: el acta debe registrar lo que realmente se midió, y negarla dejaría constancia de un dato que se sabe equivocado.

Una corrección **cubre el resultado contra el que fue verificada, no cualquier resultado posterior**. El hallazgo registra cuántas revisiones tenía cuando su acción concluyó; si después aparece otra, el resultado vigente queda sin cubrir y el hallazgo vuelve a bloquear la certificación. Se prefirió contar revisiones antes que comparar instantes porque dos hechos del mismo momento no quedarían ordenados.

Para que esa nueva ocurrencia pueda corregirse, el hallazgo **abre una acción correctiva nueva** y conserva las anteriores con su plan, sus ejecuciones y sus verificaciones intactos. El hallazgo sigue siendo uno por criterio, como exige RF7; la relación con la acción es 1:1 en cada momento y 1:N a lo largo del tiempo. Al verificar y cerrar la acción vigente se resuelve la causa y el certificado se reactiva automáticamente conservando su vencimiento, que es lo que RF9 pide al establecer que «la causa de suspensión, por sí sola, no exige una nueva inspección completa».

Se descartó reabrir la acción concluida, porque RF8 declara inmutable el plan confirmado, y crear un hallazgo nuevo, porque contradiría el «exactamente un hallazgo por criterio» de RF7. También se descartó dejar la suspensión sin salida ordinaria: reservaba la renovación para un caso que RF9 resuelve por reactivación.

Una rectificación se valida por completo antes de aplicarse: motivo, existencia del destino de cada corrección y admisibilidad de la respuesta contra la versión congelada. Una solicitud rechazada no deja datos modificados ni registros parciales. Como los adaptadores no ofrecen transacciones, validar antes de mutar es lo que sostiene esa atomicidad; comprobar durante la aplicación dejaría el agregado a medio camino.

Una rectificación que solo corrige una referencia de evidencia no cambia resultado ni motivos, pero sí lo que el hallazgo declara como evidencia presentada. Esa copia se refresca sin registrar una revisión ni tocar la acción correctiva: registrarla como revisión abriría una acción nueva por corregir un enlace, según la política de reaparición descrita más arriba. Sin ese refresco, el acta y el resumen informaban evidencias distintas del mismo hecho.

`Rectification` conserva los cambios y los registros de criterio mantienen evaluaciones sucesivas. `VoidedObligation` distingue la anulación de una obligación de una reparación verificada. `AuditRecorder` registra modificaciones confirmadas y decisiones sobre activos, esquemas, inspecciones, hallazgos, acciones y certificados, incluyendo borradores y cargas parciales.

Se eligió **estado actual acompañado de historia explícita**. Sobrescribir sin antecedentes impediría reconstruir decisiones. Event Sourcing exigiría reconstruir agregados desde eventos completos y mantener su reproducción y versionado; esa complejidad no es necesaria para el enfoque elegido.

La auditoría debe conservar elemento, acción, fecha, autor o ejecución automática, motivo y datos o estados anteriores y nuevos. Complementa las versiones y certificados, sin reemplazarlos. El estado anterior se lee antes de mutar y no se escribe fijo: hacerlo registraba transiciones que nunca ocurrieron, como una segunda causa anotada «VALID → SUSPENDED» sobre un certificado ya suspendido. Solo conservan un literal las transiciones cuyo estado previo garantiza la propia guarda del método.

## 9. Informes como valores de salida

**Decisión adoptada.** Para la entrega del módulo de dominio se producen salidas estructuradas: acta con datos históricos y rectificaciones, resumen de hallazgos con sus acciones, y certificado con vigencia, estado y compromisos pendientes. Un bloqueo de emisión no impide generar acta y resumen; se informan los motivos y no se crea un certificado rechazado.

Los generadores construyen **proyecciones de consulta**. `ReportedValue` distingue valores originales y corregidos. Separar contenido y presentación permite probar la información exigida sin introducir formato visual en las reglas.

Se descartó generar PDF dentro del dominio. Tampoco se aplica CQRS completo con almacenes distintos: la separación de algunos contratos de consulta basta y evita sincronizar dos modelos persistidos.

Los informes se construyen al consultar y no son copias archivadas de un documento emitido. Sus datos actuales pueden cambiar, pero deben conservar la atribución de las rectificaciones. Ante rectificaciones sucesivas sobre un mismo valor, el valor original proviene de la primera y la atribución de la última, que es la que produjo el valor vigente; atribuirla a la primera hacía que el acta mostrara un motivo que contradecía el valor exhibido. El encadenamiento completo sigue estando en la lista de rectificaciones del acta.

## 10. Pruebas de comportamiento con dependencias controladas

JUnit y AssertJ prueban reglas y ciclos de vida. Las pruebas de integración componen casos de uso con repositorios en memoria, reloj controlable y eventos síncronos. Esto permite comprobar vencimientos y efectos entre agregados sin esperas ni infraestructura externa.

Se descartaron pruebas con base de datos o interfaz para esta entrega. Sustituir todas las colaboraciones por mocks tampoco demostraría que los casos de uso funcionan juntos. Los adaptadores en memoria permiten esa integración, aunque no prueban concurrencia ni persistencia y conservan referencias a objetos mutables.

El proyecto compila para Java 25. `mvn test` ejecuta unitarios y `mvn verify` agrega integración y el reporte JaCoCo. Las rectificaciones sucesivas, la exactitud de la auditoría y la corrección de registros antes del cierre tienen escenarios propios.

## 11. Definiciones que siguen abiertas

Quedan por precisar el catálogo concreto de características por tipo de activo, los campos identificatorios del responsable y una duración predeterminada de certificados, si se necesita.

El tratamiento de hallazgos y acciones cuando una rectificación cambia el incumplimiento sin eliminarlo quedó resuelto y se describe en la sección 8, incluidos los casos de acciones ya cerradas y hallazgos previamente anulados. Abrir una acción correctiva nueva sobre el mismo hallazgo es una decisión adoptada por el equipo sobre un punto que RF10 dejó abierto.
