# Decisiones de diseño

El proyecto corresponde a la Entrega 1: un módulo de dominio Java con modelos, contratos, casos de uso concretos y pruebas. La consigna no exige API REST, persistencia real, frontend, seguridad ni despliegue.

Además de las decisiones técnicas, se explicitan las interpretaciones adoptadas por el equipo para precisar comportamientos que el enunciado deja abiertos. Estas interpretaciones delimitan el alcance del modelo; no son requisitos adicionales impuestos por la consigna.

## Registro enumerado de decisiones

La siguiente tabla resume las decisiones principales. Las secciones posteriores desarrollan
el razonamiento y las consecuencias de cada una.

| ID | Patrón o principio aplicado | Dónde se aplica | Por qué se aplica | Alternativa descartada | Consecuencia |
|---|---|---|---|---|---|
| D1 | Arquitectura hexagonal / inversión de dependencias | Interfaces `application/**/port`, casos de uso y adaptadores de `adapter` y `src/test` | Aislar el negocio de persistencia, web, reloj e identidad | JPA, framework web o repositorios concretos dentro del dominio | Hay más interfaces y una composition root explícita; los adaptadores en memoria no dan durabilidad ni transacciones |
| D2 | Agregados y encapsulamiento de DDD | `Asset`, `InspectionSchema`, `Inspection`, `Finding`, `Certificate` | Concentrar invariantes y separar ciclos de vida | Modelo anémico con setters o un agregado único | Las operaciones pasan por métodos de negocio y la coordinación requiere casos de uso |
| D3 | Value Object e identidad explícita | `AssetId`, `InspectionId`, `SchemaVersionId`, `CriterionId`, `ValidityPeriod`, `CorrectionPlan` | Evitar mezclar identificadores y validar conceptos del dominio | `String`, `Date` y `Map` sin semántica | Más tipos pequeños y conversiones explícitas, con errores inválidos detectados antes |
| D4 | Repository y Query Port | `*Repository`, `*Query`, `AssetDirectory`, `SchemaCatalog` | Separar comandos, consultas y almacenamiento | Acceso directo a colecciones o dependencias de infraestructura | Se puede cambiar el adaptador; la consistencia transaccional queda fuera de esta entrega |
| D5 | Application Service / casos de uso | Paquetes `application/**/usecase` y servicios como `FindingService` | Orquestar varios agregados sin trasladar reglas al controlador o repositorio | Lógica en entidades externas, controladores o una clase fachada gigante | Los flujos son explícitos y testeables, aunque existen más clases de coordinación |
| D6 | Strategy | `EvaluationRule`, `NumericRangeRule`, `YesNoRule`, `MappedOptionsRule` | Agregar reglas de evaluación sin modificar `Criterion` | Subclases de `Criterion`, `if/else` centralizados o motor externo | Cada regla debe validar admisibilidad, publicación y evaluación, y necesita sus propios tests |
| D7 | Servicios de dominio y aplicación | `CriterionEvaluator`, `SchemaApplicability`, `CertificateIssuer`, `CertificateLifecycle`, `RectificationConsequences`, `CertificateFactory` | Separar reglas puras de coordinación con puertos externos | Poner la evaluación completa en `CriterionRecord`, `CloseInspection` o repositorios | Las reglas puras quedan en dominio; la coordinación con repositorios queda en aplicación |
| D8 | Policy / Specification-like requirements | `CertificateIssuancePolicy`, `IssuanceRequirement`, `IssuanceBlocker`, `CertificationContext` | Calcular todos los bloqueos de emisión de forma extensible y explicable | `if` encadenados o cortar en el primer error | Se informa una decisión rica (`Issued`, `Blocked`, `AlreadyIssued`), pero hay más objetos para una decisión simple |
| D9 | State explícito sin jerarquía State | Enums de `InspectionStatus`, `CorrectiveActionStatus`, `CertificateStatus` y métodos de transición | Proteger transiciones con invariantes sin sobrediseñar ciclos pequeños | Clase por estado o setters públicos | Menos clases, pero cada operación debe verificar su estado permitido |
| D10 | Publish/Subscribe con eventos de dominio | `DomainEventPublisher`, eventos de `finding/event` e `Inspection.CriterionResultRevised`, `CertificationReactions` | Desacoplar acciones e inspecciones de suspensión/reactivación de certificados | Invocar `Certificate` directamente desde cada caso de uso o usar un broker | El despacho de esta entrega es síncrono y en memoria; no hay entrega durable ni atomicidad distribuida |
| D11 | Snapshot y versionado inmutable | `AssetSnapshot`, `SchemaVersion`, `Inspection.frozenSchemaVersion` | Reconstruir qué activo y reglas existían al iniciar una inspección | Leer siempre el activo y esquema actuales o copiar todo el esquema por inspección | Se conserva historia sin duplicar esquemas; las versiones referenciadas deben permanecer disponibles |
| D12 | Historia explícita y rectificación | `Rectification`, `CriterionEvaluation`, `FindingRevision`, `VoidedObligation`, informes | Corregir hechos sin sobrescribir el original y sin confundir reparación con rectificación | Editar una inspección cerrada, borrar datos o aplicar Event Sourcing completo | Se mantienen estado actual e historial; aumenta el costo de conservar y proyectar cambios |
| D13 | Agregado con colección histórica de acciones | `Finding.correctiveActions()` y `Finding.correctiveAction()` | Mantener una acción vigente sin borrar acciones concluidas cuando reaparece una no conformidad | Reabrir la acción vieja o crear otro hallazgo para el mismo criterio | Hay un hallazgo por criterio y varias acciones históricas; la acción vigente se distingue de sus antecedentes |
| D14 | Composition Root y Test Doubles | `FullSystem`, `InMemory*`, `TestClock`, `SequentialIds`, `FixedActor` | Probar flujos completos determinísticamente sin infraestructura | Tests solo con mocks, base real o reloj del sistema | Se prueban integraciones reales entre casos de uso; no se cubren persistencia ni concurrencia |
| D15 | Proyecciones de consulta / DTO de dominio | `InspectionAct`, `FindingsSummary`, `CertificateReport`, `ReportedValue` | Separar información del dominio de su formato de presentación | Generar PDF o mezclar reporting con entidades | Las salidas son estructuradas y testeables; una capa externa deberá elegir JSON, HTML o PDF |
| D16 | Validación antes de mutar | `Validate`, rectificaciones y constructores de entidades/valores | Evitar cambios parciales cuando una operación inválida afecta varias partes | Mutar y validar paso a paso, o depender de transacciones inexistentes | Se obtiene atomicidad lógica en memoria; la persistencia futura deberá agregar transacciones reales |

| D17 | Scope como tipo sellado | `CertificateScope` (`Global` \ | `Partial`), `Certificate.scope`, `CertificationContext.scope` | Distinguir qué cubre un certificado sin campos nulables ni banderas | `Optional<Subsystem>` suelto en `Certificate`, o un `boolean partial` | El `switch` sobre alcance es exhaustivo; cada consulta de certificado por activo o inspección pasa a necesitar el alcance |
| D18 | Subsistema como concepto del catálogo, asociado al criterio por el esquema | `catalogue.Subsystem`, `AssetType.subsystems()`, `Criterion.subsystem` | El subsistema es una parte del activo; el esquema solo dice qué criterio evalúa cuál | Tener el concepto en `domain.schema`, o declararlo en `Section` | Mantiene la dependencia `schema → catalogue` en un solo sentido, sin ciclo; un criterio sin subsistema pesa sobre todos los parciales |
| D19 | Filtrado por alcance en los hechos, no en la política | `CertificationContext.inScope()` | Que los seis requisitos de emisión respondan a un parcial sin saber que existen subsistemas | Un requisito nuevo por subsistema, o duplicar la política | `CertificateIssuancePolicy` e `IssuanceRequirements` quedan sin modificar; el alcance viaja en los hechos |
| D20 | Certificado global derivado, no persistido | `GlobalCertificatePolicy`, `AllSubsystemsMustBeInForce`, `GlobalCertificate`, `DeriveGlobalCertificate` | Que el global no pueda contradecir a sus parciales | Persistir un `Certificate` de alcance `Global` y reconciliarlo | No hay identidad ni auditoría propia del global; a cambio no hay paso de sincronización al suspender un parcial |
| D21 | Ruteo de eventos por alcance | `CertificationReactions` | Que una acción vencida de un subsistema no suspenda a los demás | Suspender todos los certificados de la inspección, o llevar el subsistema en cada evento | `CertificationReactions` recibe `InspectionQuery` para resolver criterio→subsistema; los cuatro eventos quedan sin cambios |
| D22 | El activo declara qué partes tiene | `Asset.subsystems`, `AssetSnapshot.subsystems`, `Inspection.certifiableSubsystems()` | No todo activo de un tipo tiene todas las partes del tipo | Asumir que todos los activos de un tipo son homogéneos | La derivación global exige solo los subsistemas del activo; los criterios de una parte ausente quedan evaluados pero no bloquean |
| D23 | Criterio no aplicable, no un cuarto resultado | `Criterion.appliesToAssetHaving()`, `CriterionRecord.applicable`, `Inspection.start/close`, `ActCriterionLine.applicable` | Que los criterios de una parte ausente no generen hallazgos | Un cuarto `CriterionResult` NOT_APPLICABLE, o no crear el registro | RF5 conserva tres resultados; el registro se conserva y el acta declara que no aplicó, en lugar de dejar un hueco |
| D24 | El modo de certificación es un tipo sellado, no un booleano | `CertificationPlan`, `InspectionSummary.certificationPlan()` | Que el llamador no tenga que leer «sin subsistemas» como «certificalo entero» | Un `boolean certifiedAsAWhole()` con `if/else`, o exponer el conjunto crudo | La regla vive en una sola fábrica y el `switch` del llamador es exhaustivo; hay un tipo más |
| D25 | Vigencia temporal diferida (`effectiveFrom`) | `SchemaVersion`, `InspectionSchema`, `PublishSchemaVersion` | Permitir preparar y publicar versiones normativas antes de su entrada en vigencia operativa | Activar la versión mediante un proceso batch/cron o cronograma externo | Las inspecciones iniciadas antes de la fecha futura siguen usando la versión anterior de forma automática |
| D26 | Consulta temporal de esquemas (`effectiveVersionAt`) | `SchemaCatalog`, `PublishedSchemaCatalog`, `InspectionSchema` | Resolver cuál era/será la versión válida en cualquier punto de la línea de tiempo sin mutar el historial | Filtrar solo la última versión ordenada por número | Permite simular y auditar qué reglas aplicaban o aplicarán en una fecha pasada o futura determinada |

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

Las decisiones que cruzan agregados quedan en servicios de dominio cuando no necesitan puertos, y en aplicación cuando deben consultar repositorios, queries, reloj, identidad o auditoría. Para que el encapsulamiento del agregado no dependa de la disciplina de quien escribe el próximo caso de uso, el **límite del paquete coincide con el límite del agregado**: `CriterionRecord` reside junto a `Inspection` y sus operaciones de escritura son de paquete, de modo que desde afuera solo es alcanzable su superficie de lectura. Antes eran públicas y una inspección cerrada podía modificarse sin rectificación; ningún caso de uso las necesitaba.

`CorrectiveAction` recibió el mismo tratamiento y reside junto a `Finding`, con sus cinco operaciones de escritura de paquete y las delegaciones que los casos de uso necesitan en el hallazgo; los objetos de valor siguen en `finding.action`, porque los construyen los llamadores. Ahí el motivo era más fuerte que el encapsulamiento: desde que el hallazgo registra contra qué resultado se verificó su corrección, verificar o anular directamente sobre la acción la cerraba sin actualizar ese registro, y el hallazgo quedaba bloqueando la certificación para siempre sin señal de error.

`SchemaDraft` también tiene mutadores de paquete. La raíz `InspectionSchema` expone `addSection` y `removeSection`; `OpenDraft` y `EditDraft` devuelven una referencia con superficie pública de lectura. Los casos de uso guardan y auditan cada edición confirmada.

El supuesto S1 se implementa con nombres predefinidos en `AssetType`: laboratorio (`room`, `biosafetyLevel`), fábrica (`room`, `productionLine`), instalación (`room`, `purpose`) y equipo (`room`, `brand`, `model`, `serialNumber`). Son atributos opcionales; se rechazan nombres ajenos al tipo tanto en `Asset` como en `AssetSnapshot`. El enum representa un catálogo inicial cerrado: incorporar otro tipo requiere cambiar el código. Un catálogo dinámico sería una extensión del alcance.

`Asset.relocate` y `Asset.assignResponsible` son comandos. El caso de uso conserva el valor anterior y construye el `FieldChange` para auditoría, de la misma forma que en los cambios de aplicabilidad.

**Subsistemas.** Un activo puede dividirse en partes certificables por separado. El concepto
pertenece al catálogo: `Subsystem` vive en `domain.catalogue` y `AssetType` declara qué partes puede
tener un activo de ese tipo, igual que ya declaraba sus características (S1). Cada activo declara
cuáles tiene de ese catálogo; por omisión se le asignan todas, y declarar ninguna significa que se
certifica entero, con todos los criterios contando contra el activo y no contra una parte. El
`AssetSnapshot` las captura al iniciar, como la ubicación y el responsable.

Tener el concepto en el catálogo y no en el esquema no es solo ontológico: `domain.schema` ya importa
`domain.catalogue` y no al revés, de modo que declarar las partes en el activo con `Subsystem` en el
esquema habría cerrado un ciclo entre ambos paquetes. `ArchitectureBoundaryTest` lo impide ahora.
Se descartó modelarlo como un enum propio: qué partes existen es vocabulario por tipo de activo y ya
lo declara `AssetType`.

## 3. Versiones inmutables y datos históricos

**Decisiones adoptadas.** Cada tipo de activo tiene un único esquema aplicable; un esquema puede servir a varios tipos y activos. Cada inspección corresponde a un activo y un inspector, y comprende el esquema completo. Compartir esquema no comparte respuestas ni evidencias.

Se distinguen asignación e inicio: la primera fija activo, inspector y fecha prevista; el segundo selecciona automáticamente la última versión publicada del esquema aplicable. Los borradores no participan y no se permite iniciar sin una versión publicada.

`SchemaApplicability` concentra la exclusividad de un esquema por tipo al crear y ampliar esquemas. El servicio expone operaciones públicas de creación, alta, baja y transferencia; los mutadores reales de aplicabilidad quedan encapsulados en `InspectionSchema` con visibilidad de paquete. Quitar un tipo inexistente o repetir una incorporación se rechaza sin auditoría. Para retirar una cobertura se exige transferirla a otro esquema con versión publicada; `ChangeSchemaApplicability.transferTo` guarda y audita ambas raíces. La raíz de origen debe conservar al menos un tipo. La persistencia futura debe ejecutar la transferencia y la validación de exclusividad en una transacción.

`InspectionSchema` separa el borrador editable de las versiones publicadas. `StartInspection` fija una `SchemaVersion` y captura un `AssetSnapshot` con identidad, tipo, características, ubicación y datos básicos del responsable. Esos datos históricos permanecen estables incluso mientras la inspección sigue abierta.

Se eligieron **versiones compartidas e inmutables y capturas de datos mutables** para cumplir la conservación de reglas exigida por la consigna. Consultar siempre los datos actuales alteraría los antecedentes; copiar el esquema completo en cada inspección duplicaría contenido ya identificado por versión.

Las versiones referenciadas deben seguir siendo recuperables. Las reglas actuales son inmutables y cualquier extensión deberá conservar esa propiedad. El responsable capturado al inicio puede diferir del responsable al que se asigna un hallazgo al cierre: representan momentos distintos.

**Cobertura de subsistemas.** Una versión no se publica si deja sin evaluar una parte que los activos
de sus tipos aplicables pueden tener: `InspectionSchema.publish` lo informa como violación, nombrando
tipo y subsistema. Sin esa regla, un activo con sistema de presión podía quedar certificado «como un
todo» por un esquema que nunca lo inspeccionó.

La invariante relaciona dos cosas que cambian por separado —los criterios de la versión y el conjunto
de tipos aplicables—, así que se verifica en los dos puntos de mutación. `SchemaApplicability` la
exige también al agregar un tipo a un esquema ya publicado y al transferirlo, junto a la regla
hermana de que el esquema destino tenga una versión publicada. Validarla solo al publicar dejaba
abierta la vía de la aplicabilidad, porque la versión publicada es inmutable y nadie la vuelve a
revisar.

## 4. Estrategias de evaluación y carga progresiva

**Decisiones adoptadas.** Cada criterio pertenece a una sección y tiene una regla de rango numérico, sí/no u opciones con resultado asignado, además de requisitos de evidencia. La severidad depende del resultado concreto. Antes de publicar se exige un resultado único para toda respuesta admitida; las bandas numéricas declaran unidad y límites sin huecos ni superposiciones.

Interpretamos «registro progresivo» como permitir cargas incompletas que puedan continuarse, corregirse o eliminarse con auditoría. La carga valida formatos, tipos y opciones; una medición válida fuera del rango de aprobación se registra. La evaluación ordinaria ocurre al cerrar. Se permite cerrar con faltantes obligatorios, rechazando los criterios afectados con motivo explícito. Repetir el cierre no debe reevaluar ni duplicar hallazgos y acciones. Corregir y eliminar alcanza a los cuatro tipos de registro, incluidas las observaciones; corregir una observación conserva autor e instante, porque quién observó y cuándo son hechos y solo el texto se corrige. Después del cierre las cuatro operaciones se rechazan y exigen rectificación.

Las fotos y documentos se representan por referencias a archivos. El inspector determina su pertinencia y el sistema verifica la presencia exigida por tipo y cantidad. Si falta evidencia, se registra qué se exigía y qué no se presentó.

Se aplica **Strategy**: `Criterion` compone una `EvaluationRule` y `CriterionEvaluator` combina su resultado con los faltantes de evidencia. `Inspection` conserva la `SchemaVersion` inmutable seleccionada al inicio, valida por sí misma la admisibilidad al cargar y evalúa al cerrar y al rectificar. `close` recibe únicamente el actor y el instante; ninguna API pública permite inyectar resultados ni dejar una rectificación sin reevaluar. La carga sigue sin producir resultados anticipados, conforme a RF5. Las evidencias deben corresponder a un requisito de la versión congelada: `attachEvidence` recibe la etiqueta del requisito y la propia raíz construye el `EvidenceRecord` con el tipo declarado, de modo que el caso de uso ya no repite esa búsqueda contra el catálogo. Esto permite incorporar otra regla sin crear una subclase de criterio para cada variante.

Se descartó un motor externo de reglas por la complejidad adicional de ejecución y diagnóstico. Tampoco se aplica Composite: inicialmente hay una regla por criterio y no se necesitan combinaciones ni secciones anidadas arbitrariamente.

Cada nueva estrategia necesita pruebas de admisibilidad y evaluación. La severidad de faltantes está fijada en `EvaluationReason.MISSING_MANDATORY_DATA`. Los requisitos de evidencia se identifican por etiqueta, y `Criterion` exige que sean únicas dentro del criterio: sin esa unicidad una adjunción no podía atribuirse a un requisito concreto y un faltante obligatorio podía darse por cubierto.

**A qué parte responde cada criterio.** La asociación criterio→subsistema es metodología y pertenece
al esquema: `Criterion.subsystem` dice qué parte evalúa cada criterio. Se declara en el criterio y no
en la sección —la consigna admite ambos niveles— para que una sección pueda agrupar criterios de
distintas partes sin necesitar una regla de coherencia entre ambas. Un criterio que no declara parte
es **transversal** y pesa sobre todas: la regla vive solo en `Criterion.weighsOn`, y `SchemaVersion` e
`Inspection` delegan en ella.

Los criterios de una parte que el activo no tiene quedan **no aplicables** al iniciar la inspección.
No se pueden contestar, no se evalúan al cerrar y no generan hallazgo; el acta conserva la línea y
declara que no aplicó, en lugar de dejar un hueco indistinguible de «sin evaluar todavía». Pedirle al
inspector que contestara por una parte inexistente convertía su ausencia en una no conformidad. No se
agregó un cuarto resultado al criterio: RF5 conserva aprobado, observado y rechazado, y la
aplicabilidad es un dato del registro.

## 5. Ciclos de vida con estados explícitos

**Decisiones adoptadas.** Planificar una acción consiste en indicar trabajo, ejecutor y fecha límite. El responsable del hallazgo elige la solución; una vez confirmados esos datos no se modifican. El ejecutor informa la realización con evidencia y el inspector verifica. Si la verificación falla, se conserva el intento y la misma acción permanece abierta.

`Finding` conserva al inspector al crearse y lo transmite a sus acciones, incluidas las de reemplazo. `CorrectiveAction` rechaza planificar al inspector como ejecutor y exige que verifique el inspector, que además debe ser distinto del ejecutor. La ejecución debe corresponder al ejecutor planificado. Los casos de uso obtienen el usuario únicamente de `ActorProvider` (ya no existen overloads que reciban la autoría por parámetro) y usan el mismo actor capturado para el registro de negocio y su auditoría. Las operaciones automáticas no pueden ejecutar ni verificar correcciones.

Confirmar el plan corresponde al responsable del hallazgo, como establece RF8: `Finding.planCorrection` recibe a quien planifica y rechaza a cualquier otra parte. Del mismo modo, `Inspection` exige que inicio, carga, corrección previa al cierre, cierre y rectificación los realice el inspector asignado; la regla vive en la raíz y no en los casos de uso, por lo que una llamada directa al agregado tampoco la saltea. La asignación y la reasignación exigen que el inspector sea una persona registrada (`Party.asInspector`): una organización o un identificador inexistente dejaban una inspección que nadie podía ejecutar.

Una acción dispone de 30 días corridos desde su creación para planificarse, usando la fecha UTC del hecho. Este valor es una decisión de implementación para cerrar el hueco identificado por el informe, no un plazo impuesto por la consigna. Antes de confirmar el plan se compara su fecha límite con `Clock.today()`: puede ser hoy, nunca anterior. La fecha límite es inclusiva. Una planificación tardía conserva el incumplimiento del plazo de planificación, incluso si todavía no se ejecutó el barrido, y este emite el evento de vencimiento una sola vez. Los informes incluyen tanto el plazo de planificación como el plazo de corrección confirmado.

Cumplir el plazo exige verificación satisfactoria y cierre antes del vencimiento. Una acción vencida admite ejecución y verificación tardías, pero conserva el incumplimiento del plazo incluso después de cerrarse. El plan se modela como datos de la acción, sin exigir un documento independiente.

Inspecciones, acciones y certificados usan **estados explícitos y métodos con condiciones de transición**. No se aplica State mediante una clase por estado porque los ciclos iniciales son pequeños. En `CorrectiveAction`, `deadlineBreached` separa el antecedente de vencimiento del progreso de ejecución.

Tratar el vencimiento como un estado terminal impediría la corrección tardía. La separación elegida permite avanzar sin borrar ese antecedente, aunque exige comprobar conjuntamente estado y plazo.

El tiempo se recibe explícitamente y los casos de uso de barrido materializan vencimientos. Como el barrido puede no haberse ejecutado todavía, conviven dos nociones de vencimiento: el estado `EXPIRED`, que es el barrido ya aplicado, y el instante de `ValidityPeriod`, que es el hecho. Toda operación de `Certificate` consulta ambas por un único predicado interno, de modo que un certificado vencido en el tiempo no puede suspenderse ni reactivarse por no haber sido marcado aún. Antes suspender miraba solo el estado y podía dejar un certificado muerto en `SUSPENDED`.

## 6. Cierre, elegibilidad y emisión como decisiones separadas

**Política adoptada (F3).** Cada activo declara una jurisdicción y cada solicitud nueva resuelve su política activa. La política define severidades bloqueantes para observaciones y rechazos, permiso de certificados condicionales y duración por modalidad. El perfil explícito de referencia conserva la regla anterior: observaciones planificadas permitidas y rechazos pendientes bloqueantes. Todas las políticas exigen cierre, planificación y ausencia de acciones abiertas vencidas; la sección 12 detalla su procedencia histórica.

La emisión se solicita explícitamente y cada inspección respalda como máximo un certificado; una solicitud repetida identifica el existente. Al emitir se conserva su vencimiento. Renovar exige que el anterior haya vencido, una nueva inspección completa y un nuevo certificado vinculado al anterior. Las acciones de inspecciones anteriores conservan su historia, pero no bloquean la renovación ni suspenden el certificado nuevo. La fábrica carga el predecesor del mismo activo y valida las condiciones de renovación antes de construir el certificado.

`Inspection.close` evalúa; `CloseInspection` guarda el cierre y registra las no conformidades. El registro es idempotente y se repite en cada solicitud de cierre: si el cierre quedó guardado pero sus hallazgos no llegaron a registrarse, un nuevo intento lo completa en lugar de dejar la inspección cerrada, sin hallazgos y sin posibilidad de certificar. `CertificateFactory` reúne los hechos tanto para emitir como para consultar la elegibilidad (`EvaluateIssuanceEligibility` delega en ella), de modo que ambas respuestas no pueden divergir; antes existía un ensamblador paralelo en aplicación. `ConfiguredJurisdictionCertificationPolicy` evalúa requisitos comunes mediante `IssuanceRequirement` y las reglas variables de su definición inmutable. Sustituye a `CertificateIssuancePolicy`. `CertificateFactory` decide emitir y renovar; `IssueCertificate` y `RenewCertificate` guardan y auditan sus decisiones. `CertificateValidityPolicy` separa el cálculo de vigencia.

Esta **composición de políticas** mantiene las decisiones fuera de los repositorios y permite informar todos los bloqueos. Se descartó emitir automáticamente al cerrar porque puede ser necesario completar correcciones antes de solicitar certificación. Tampoco se usa Chain of Responsibility con interrupción en el primer fallo: interesa explicar todos los impedimentos.

Los resultados son variantes explícitas: emitido, bloqueado o ya emitido, sin crear un certificado rechazado. La implementación también limita a uno los certificados no vencidos por activo; esa restricción adicional requiere revisar su alcance. Los doce meses elegidos en los tests no establecen una duración universal.

`Certificate` conserva constructor de paquete y la creación material queda encapsulada en `CertificateIssuer`, un servicio puro del dominio. `CertificateFactory` vive en aplicación y es la ruta de producción para emitir o renovar: carga los hechos mediante puertos de aplicación, delega la construcción al issuer y aplica la política del dominio. `CertificationContext` contiene inspección, hallazgos y fecha; calcula los bloqueos en el dominio e incluye incumplimientos cuyas acciones aún no fueron registradas. Las restricciones identificadas en el snapshot suman reglas del perfil; ninguna estrategia puede quitar las garantías comunes que también comprueba la fábrica. Renovar exige otra inspección iniciada estrictamente después de emitir el certificado anterior, que debe estar vencido. Repetir una renovación devuelve el certificado ya emitido.

La misma razón que impide renovar con una inspección antigua impide emitir con una inspección superada: el requisito estándar `InspectionMustBeTheLatestOfTheAsset` bloquea la emisión cuando el activo tiene otra inspección cerrada iniciada después (`IssuanceBlocker.SupersededInspection`), porque certificaría un estado del activo que ya fue reemplazado por uno más reciente. Además, una vez vencido el certificado de un activo, `issue` se rechaza: el siguiente certificado se obtiene renovando, y así conserva el vínculo con el anterior que exige RF9.

**Certificación por partes.** El alcance de un certificado es un tipo sellado, `CertificateScope`:
`Global` para el activo entero o `Partial` para una parte. `Certificate` lo conserva como dato propio,
de modo que la unicidad «un activo no puede tener dos certificados vivos» pasa a valer por alcance y
dos partes pueden estar certificadas a la vez, cada una con su vigencia.

Los hechos sobre los que decide la política se filtran por alcance en `CertificationContext`, no en
los requisitos: los requisitos comunes y el perfil jurisdiccional responden a un parcial sin conocer los repositorios,
porque simplemente ven menos criterios. Se descartó agregar un requisito por subsistema, que habría
multiplicado `IssuanceRequirements` y mezclado el alcance con las reglas de negocio.

**El certificado global se deriva, no se emite.** `GlobalCertificatePolicy` es una interfaz del
dominio y `AllSubsystemsMustBeInForce` su implementación por defecto: el activo está certificado como
un todo mientras cada parte que tiene posea un parcial en vigor —ni suspendido, ni vencido, ni fuera
de su ventana—, y la vigencia derivada es la ventana común, así que el global no sobrevive a la parte
más débil. Se recalcula en cada consulta, por lo que no puede contradecir a sus parciales ni exige
reconciliar nada cuando uno se suspende, se reactiva o vence. Se descartó persistirlo como un
`Certificate` de alcance `Global`: quedaba uniforme con el resto, pero obligaba a sincronizarlo y un
global desincronizado afirma algo que sus parciales contradicen.

La derivación exige las partes **del activo**, no las que la versión evalúa. Son dos preguntas
distintas: qué parciales se pueden emitir es la intersección de ambas, pero si el activo está entero
lo deciden las partes que tiene. Un esquema que no evaluara una de ellas produciría un global que
afirma más de lo inspeccionado; la regla de cobertura de la sección 3 evita que ese esquema exista.

Qué operación corresponde lo responde `InspectionSummary.certificationPlan()`, un tipo sellado
`AsAWhole` o `ByParts`. El llamador no interpreta un conjunto vacío ni necesita saber cómo está
construido el activo, y el `switch` es exhaustivo: una tercera forma de certificar detiene la
compilación en lugar de tomar una rama equivocada en silencio. No se unificó en una sola operación
que emitiera todo, porque la vigencia independiente existe justamente para certificar cada parte
cuando cierran sus propias acciones correctivas.

**Comportamiento resultante.** Con un mismo esquema publicado para un tipo y tres activos que
declaran sus partes distinto:

| | sin partes declaradas | menos partes que su tipo | todas sus partes |
|---|---|---|---|
| plan de certificación | `AsAWhole` | `ByParts(1)` | `ByParts(3)` |
| criterios evaluados al cerrar | todos | solo los de su parte y los transversales | todos |
| certificados emitidos | 1, alcance `Global` | 1 parcial | 3 parciales |
| global derivado | no corresponde | sobre 1 parte | sobre 3 partes |

La primera y la última columna evalúan los mismos criterios y producen estructuras opuestas; lo único
que cambia es la declaración del activo. Ante un rechazo en una parte, el activo sin partes declaradas
no obtiene ningún certificado, mientras que el certificado por partes emite las partes que cumplen y
bloquea solo la rechazada, que es lo que exige aprobar algunos subsistemas y rechazar otros.

## 7. Eventos para coordinar efectos entre agregados

**Política adoptada (F3).** Cada evento relevante provoca una reconciliación de los hechos actuales por alcance con el snapshot del certificado. Un condicional se suspende ante severidades bloqueantes, restricciones del perfil, falta de plan o acciones abiertas vencidas. Un regular se suspende ante cualquier incumplimiento pendiente nuevo: cambiarlo a condicional requiere otra decisión. Se conservan las causas y se reactiva únicamente cuando todas quedan resueltas y el certificado sigue dentro de vigencia. Reactivar conserva el vencimiento y la modalidad originales.

Los agregados acumulan eventos como `CorrectiveActionExpired`, `CorrectiveActionClosed`, `CorrectiveActionVoided` y `CriterionResultRevised`. Este último se emite cada vez que una rectificación cambia la evaluación, no solo el resultado: un rechazo que reaparece con otros motivos después de una corrección verificada es una no conformidad nueva, y `CertificateLifecycle` contrasta el incumplimiento actual con la política histórica, incluso si el resultado es una observación o un rechazo permitido; el contenido de un evento antiguo no sustituye esos hechos. Antes ese caso dejaba el certificado vigente mientras el hallazgo volvía a bloquear la emisión. Los servicios puros de dominio (`SchemaApplicability`, `CertificateLifecycle`) se mantienen en `domain`; los servicios que consultan puertos (`RectificationConsequences`, `CertificationReactions`) viven en `application`. Los casos de uso guardan y auditan antes de publicar eventos. En una rectificación se guardan y auditan la inspección y todas las consecuencias sobre hallazgos antes de despachar cualquier evento. `CertificateLifecycle` decide suspensiones y reactivaciones en el dominio; `CertificationReactions` persiste y audita esas decisiones. Se usa **publicación/suscripción**, con despacho síncrono en memoria en las pruebas.

Así, las operaciones sobre acciones e inspecciones comunican hechos sin conocer cómo se actualiza un certificado. Invocar certificación desde esas entidades introduciría dependencias entre ciclos de vida. Un broker, una saga o un outbox durable no se incorporan en esta entrega.

La composición debe registrar el consumidor. Una publicación fallida mantiene el evento pendiente. `PublishPendingDomainEvents` permite reintentarlo sin repetir la operación de negocio; los consumidores de certificación toleran duplicados. Los adaptadores deben conservar también los eventos pendientes al persistir el agregado. No hay garantías de entrega durable ni atomicidad entre guardados, auditoría y eventos. Los errores de validación deben evitar cambios parciales incluso en memoria; los eventos por sí solos no resuelven esa consistencia.

**Ruteo por alcance.** Una inspección respalda un certificado por alcance, así que un evento alcanza
a aquellos cuya parte pesa sobre el criterio afectado: un criterio de una parte mueve solo su
certificado, y uno transversal mueve todos. `CertificationReactions` resuelve esa correspondencia
consultando la inspección; los cuatro eventos quedaron sin cambios, en lugar de hacerles llevar el
subsistema y tocar también a sus publicadores.

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

Los generadores construyen **proyecciones de consulta**. `ReportedValue` distingue valores originales y corregidos en respuestas, observaciones y referencias de evidencia. Separar contenido y presentación permite probar la información exigida sin introducir formato visual en las reglas.

Se descartó generar PDF dentro del dominio. Tampoco se aplica CQRS completo con almacenes distintos: la separación de algunos contratos de consulta basta y evita sincronizar dos modelos persistidos.

Los informes se construyen al consultar y no son copias archivadas de un documento emitido. Sus datos actuales pueden cambiar, pero deben conservar la atribución de las rectificaciones. Ante rectificaciones sucesivas sobre un mismo valor, el valor original proviene de la primera y la atribución de la última, que es la que produjo el valor vigente; atribuirla a la primera hacía que el acta mostrara un motivo que contradecía el valor exhibido. El encadenamiento completo sigue estando en la lista de rectificaciones del acta.

**Alcance en los informes.** El certificado informa qué cubre, y sus compromisos pendientes se filtran
por ese alcance: sin el filtro, el certificado de una parte listaba las acciones pendientes de otra,
que es información equivocada y no apenas faltante. El acta declara si cada criterio aplicó al activo
y omite los faltantes de evidencia de los que no, por el mismo motivo.

## 10. Pruebas de comportamiento con dependencias controladas

JUnit y AssertJ prueban reglas y ciclos de vida. Las pruebas de integración componen casos de uso con repositorios en memoria, reloj controlable y eventos síncronos. Esto permite comprobar vencimientos y efectos entre agregados sin esperas ni infraestructura externa.

Se descartaron pruebas con base de datos o interfaz para esta entrega. Sustituir todas las colaboraciones por mocks tampoco demostraría que los casos de uso funcionan juntos. Los adaptadores en memoria permiten esa integración, aunque no prueban concurrencia ni persistencia y conservan referencias a objetos mutables.

El proyecto compila para Java 25. `mvn test` ejecuta unitarios y `mvn verify` agrega integración y el reporte JaCoCo. Las rectificaciones sucesivas, la exactitud de la auditoría y la corrección de registros antes del cierre tienen escenarios propios.

## 11. Definiciones que siguen abiertas

El catálogo inicial de características y el plazo de planificación se explicitan en las secciones 2 y 5. Quedan por precisar los campos identificatorios del responsable. F3 define los plazos por perfil registrado y evita una duración predeterminada implícita.

Las validaciones de argumentos inválidos producen `InvalidArgumentException`, subtipo distinguible de `DomainException`, que se reserva para condiciones de negocio. `AuditEntry` conserva la validación del motivo según `AuditAction.requiresReason()` y ahora exige texto no vacío cuando se proporciona uno. Se mantiene este contrato en ejecución para una auditoría genérica; separar cada acción en tipos de comandos sería una extensión y no una condición para garantizar que se conserve el motivo.

El tratamiento de hallazgos y acciones cuando una rectificación cambia el incumplimiento sin eliminarlo quedó resuelto y se describe en la sección 8, incluidos los casos de acciones ya cerradas y hallazgos previamente anulados. Abrir una acción correctiva nueva sobre el mismo hallazgo es una decisión adoptada por el equipo sobre un punto que RF10 dejó abierto.


## 12. F3: políticas jurisdiccionales versionadas

**Datos y selección.** `JurisdictionId` es un valor abierto, validado y obligatorio en
`Asset`, `AssetSnapshot` y `RegisterAsset`. La reubicación textual no lo modifica;
cambiar de jurisdicción requerirá otro caso de uso. `AssetDirectory.jurisdictionOf`
y `CatalogueAssetDirectory` permiten resolver también inspecciones aún asignadas.
La fábrica comprueba que la jurisdicción actual coincide con el snapshot capturado.

`CertificationPolicyRegistry` es un puerto de aplicación. Su adaptador
`RegisteredCertificationPolicies` registra explícitamente estrategias por jurisdicción,
activa revisiones estrictamente crecientes del mismo ID y conserva el acceso histórico.
Rechaza duplicados, cambios de definición bajo una referencia existente, referencias de
otra jurisdicción y estrategias cuya definición registrada cambió. Agregar un perfil
no requiere modificar ni reconstruir `CertificateFactory`. El registro es configuración
en memoria, sin garantías de concurrencia o persistencia.

**Definición de reglas.** `CertificationPolicyRef` identifica jurisdicción, ID y revisión;
`CertificationPolicySnapshot` copia defensivamente las severidades y restricciones y
valida períodos positivos por modalidad. Una política que prohíbe condicionales declara
el mismo plazo en ambos campos para evitar configuración inactiva contradictoria.
`JurisdictionCertificationPolicy` define una estrategia pura e inmutable;
`ConfiguredJurisdictionCertificationPolicy` interpreta la configuración habitual y usa
`FixedDurationValidityPolicy` para aritmética de calendario UTC. `PolicyRestriction`
identifica reglas recuperables, inicialmente `REJECTIONS_MUST_BE_CORRECTED`, utilizada
por el perfil de referencia. No se guardan lambdas como historia: nuevas restricciones
requieren definiciones explícitas en el dominio, y una estrategia propia debe representar
**todas** sus reglas en el snapshot que interpretará el ciclo de vida.

Se descartó un enum de jurisdicciones y un switch central por jurisdicción: obligarían
a modificar código para agregar perfiles. También se descartó conservar la regla fija
de rechazo dentro de las garantías comunes: impediría certificar rechazos de severidad
permitida, que F3 admite. `CertificateIssuancePolicy` fue sustituida por la estrategia
jurisdiccional; `IssuanceRequirements.common()` mantiene las cinco garantías universales.
La fábrica las comprueba incluso ante una estrategia personalizada.

**Hechos y decisión.** `Finding.pendingNonConformity()` extiende la resolución histórica
a observaciones y rechazos. Una ejecución reportada no resuelve un hallazgo; hace falta
verificación satisfactoria que cubra la revisión vigente o anulación de la obligación.
Una revisión posterior vuelve a pesar sin borrar el resultado anterior.
`CertificationContext.pendingNonConformities()` proyecta criterio, resultado y severidad,
filtrados una vez por alcance. Un hallazgo faltante conserva el incumplimiento y el bloqueo
por acción faltante. Los criterios transversales pesan en cada parcial; los de partes
no presentes no se evalúan.

`CertificationAssessment` identifica inspección, alcance, instante, snapshot, todos los
bloqueos y modalidad posible. `EvaluateIssuanceEligibility.assess` evalúa siempre una
**solicitud nueva**, con la política activa; la procedencia de un certificado existente
se consulta en el certificado o en su informe. Los métodos `blockersFor` delegan a esa
evaluación. Emisión y renovación usan los mismos hechos y reglas, un único instante y
su fecha UTC. La emisión vuelve a evaluar; una consulta previa no reserva el resultado.
Una severidad bloqueante impide certificar tanto `OBSERVED` como `REJECTED`; un pendiente
permitido y planificado puede producir un condicional únicamente si el perfil lo permite.
Sin pendientes se emite regular. `BlockingSeverity` y `ConditionalNotAllowed` son motivos
tipados que se acumulan con todos los demás impedimentos.

`CertificateMode.REGULAR/CONDITIONAL` es independiente de `CertificateStatus`.
`Certificate`, `CertificateIssuer` e `IssuanceDecision` conservan snapshot y modalidad.
El plazo condicional puede ser distinto al regular; una renovación determina de nuevo
perfil y modalidad, y conserva el vínculo al predecesor. Repetir emisión o renovación
resuelve primero el certificado existente, conserva su metadata original y no consulta
el registro activo ni duplica una emisión auditada. `PolicyResolutionException` expresa
falta o incoherencia de configuración, distinta de una decisión `Blocked`; la fábrica
valida cierre, fecha de emisión y vigencia antes de consumir un ID.

**Auditoría, informes y parciales.** `AuditDetail.CertificationDecision` registra operación,
inspección, alcance, snapshot, modalidad/resultado, certificado, instante y vigencia, o
todos los bloqueos. `PolicyResolutionFailed` registra errores sin inventar una política
aplicada. Los casos de uso guardan antes de auditar éxito. `CertificateStateChanged`
conserva política, alcance, modalidad y causas al suspender, reactivar o vencer.
`CertificateReport` y `IssuanceAttemptReport` exponen snapshot y alcance; el informe de
bloqueo recibe la decisión completa. Sus metadatos históricos no se recalculan a partir
del registro. Los compromisos del informe usan la resolución de la revisión actual.

`GlobalCertificate` sigue siendo una proyección, con modalidad condicional si al menos
un parcial lo es. `PartialProvenance` expone certificado, subsistema, snapshot y modalidad
por parcial: dos parciales pueden tener revisiones diferentes. `AllSubsystemsMustBeInForce`
mantiene la intersección de vigencias y rechaza derivar cuando falta un parcial vigente.
`GlobalDerivationContext` valida procedencia de activo, inspección, esquema y jurisdicción.

**Reconciliación histórica.** `CertificationReactions` reúne inspección, hallazgos y
un instante actual y delega en `CertificateLifecycle.reconcile`. El servicio puro usa
solamente cumplimiento, nunca reglas de emisión inicial como inspección posterior,
predecesor o certificado ya vivo. `SuspensionCause.NonConformity` permite registrar
observaciones, rechazos, planes faltantes y su revisión; `OverdueAction` conserva el
incumplimiento de plazo como causa independiente. El reintento de un evento antiguo
usa los hechos actuales y no reinstala causas resueltas. La última causa resuelta
reactiva únicamente dentro de vigencia. `CorrectiveActionPlanned` permite reconciliar
al confirmar un plan; `Finding` lo conserva pendiente para reintentos de publicación,
y `PlanCorrectiveAction` guarda y audita antes de publicarlo.

**Validación.** Los unitarios nuevos cubren valores, registro, matriz parametrizada de
severidad/resultado/condicionalidad, revisiones de corrección, fechas inclusivas de acciones,
calendario bisiesto UTC, alcance, fábrica, efectos de casos de uso y reconciliación.
`JurisdictionCertificationIT` demuestra A/B con hechos equivalentes, incorporación de C
con la misma fábrica, decisiones auditadas, global condicional, revisión nueva y renovación,
reintentos históricos y rectificaciones según la política original. Los perfiles A/B/C y
REFERENCE son datos ficticios de tests, **no normativa real**. Se ejecutan `mvn test` y
`mvn verify`; el segundo produce cobertura JaCoCo. Se excluyen de instrumentación las
clases generadas por Mockito, cuyo bytecode depende del JDK de ejecución, conservando
la medición del código de producción compilado para Java 25.

La verificación final ejecutó 220 unitarios y 124 escenarios de integración, sin fallos,
errores ni omisiones. El paquete `domain.certification.policy` cubre 56/56 líneas y
40/40 ramas; el registro cubre 27/28 líneas y 16/20 ramas; la coordinación de
certificación cubre 170/171 líneas y 58/72 ramas. Se revisaron las ramas restantes:
principalmente son defensas ante referencias/snapshots nulos o incoherentes devueltos
por estrategias personalizadas, combinaciones inválidas de valores de salida y algunos
mensajes de bloqueos que los tests comprueban por tipo/datos. No se impone cobertura
100% global: se priorizan decisiones de negocio y efectos observables; las garantías
de persistencia y concurrencia continúan requiriendo las pruebas reales pendientes.

**Deuda de integración.** El núcleo F2/F3 no completa la aplicación de la Entrega 2:
faltan adaptadores persistentes y migración explícita de datos anteriores, transacción
certificado/auditoría y unicidad concurrente, REST con contratos de error, frontend,
pruebas reales de repositorio/API y CI con protección de ramas. También quedan fuera
administración dinámica o aprobación de políticas y cambio de
jurisdicción de un activo. No se ofrece atomicidad durable entre guardados, auditoría
y eventos con los adaptadores actuales. Estas dependencias corresponden a la sección 8
del plan y no se ocultan mediante defaults de producción.


## 13. Entrega 2: persistencia JDBC (módulo `infrastructure`)

**Qué se agregó.** Un módulo Maven nuevo, `certification-infrastructure`, que depende del núcleo
y nunca al revés. Implementa los siete puertos de repositorio y la auditoría sobre H2 con JDBC
plano y migraciones Flyway (`V1__create_persistence_schema.sql`). El núcleo no se modificó; sólo
publica su carpeta de pruebas `support` como `test-jar` para reutilizar `FullSystem` y los
adaptadores en memoria.

**Clases agregadas** (paquete `infrastructure.persistence`):
`JdbcPersistence` (fábrica y migración), `codec.StateCodec` y `codec.Json` (serialización),
`jdbc.JdbcTransactions` (unidad de trabajo), `jdbc.DocumentRepository` (base común),
`JdbcPartyRepository`, `JdbcAssetRepository`, `JdbcSchemaRepository`, `JdbcInspectionRepository`,
`JdbcFindingRepository`, `JdbcCertificateRepository`, `JdbcAuditTrail`, y las excepciones técnicas
`PersistenceException`, `DuplicateKeyException`, `StaleAggregateException`.

**Decisión: agregado como documento.** Cada agregado es una fila `(seq, id, row_version,
columnas indexadas, doc)`. El documento es el estado completo en JSON; las columnas indexadas son
una proyección que se reescribe en cada guardado y sólo sirven para acotar búsquedas. Las
comparaciones exactas se rehacen en Java sobre el agregado reconstruido (por ejemplo, los
vencimientos se indexan en milisegundos redondeados hacia arriba y luego se filtran con exactitud).
Alternativa descartada: mapear cada entidad a tablas normalizadas (o JPA). Obligaba a modificar el
dominio (constructores sin argumentos, campos no finales, setters) o a duplicar el modelo en
entidades de persistencia, y las colecciones inmutables y los registros del núcleo no encajan.

**Decisión: códec reflexivo.** `StateCodec` escribe campos por nombre; los registros se
reconstruyen por su constructor canónico (validan igual que en el dominio) y las clases sin
constructor público se instancian con `ReflectionFactory`, escribiendo los campos directamente.
Sólo se instancian clases bajo `ar.edu.itba.dps.certification.` y escalares JDK de una lista
cerrada. Los ciclos, lambdas y clases anónimas fallan indicando la ruta del campo.

**Decisión: unidad de trabajo.** `JdbcTransactions.execute` agrupa varios guardados en una
transacción; las llamadas anidadas se unen a la existente y una excepción deshace todo y se
relanza sin envolver. Dentro de una transacción un mismo registro devuelve la misma instancia
(mapa de identidad), lo que conserva la semántica de referencias que los casos de uso esperan de
los repositorios en memoria. El bloqueo optimista usa `row_version`: un guardado sobre una versión
vieja lanza `StaleAggregateException`.

**Reglas impuestas por la base**, no sólo por el código: un esquema por tipo de activo (clave
primaria de `schema_applicability.asset_type`) y un certificado por inspección y alcance (índice
único `certificate(backing_inspection_id, scope_key)`). Una violación de unicidad (SQLState 23505)
se traduce a `DuplicateKeyException`. Esto cubre la "unicidad concurrente" que la sección 12
dejaba pendiente.

**Cómo se prueba.** Las pruebas de repositorio usan H2 real, no mocks. `RepositoryParityIT` ejecuta
el mismo escenario sobre los repositorios JDBC y sobre los en memoria (que actúan como oráculo) y
compara resultados; `JdbcTransactionsIT` cubre commit, rollback, anidamiento, mapa de identidad y
escritura obsoleta con hilos; `DatabaseConstraintsIT` verifica las restricciones; `JdbcLifecycleIT`
corre los casos de uso reales (ciclo completo con reinicio de la base, rectificación que suspende
un certificado de forma atómica, operación rechazada sin rastro). `StateCodecTest` cubre el códec.

**Refactorizaciones del núcleo:** ninguna. **Clases del núcleo modificadas:** ninguna (sólo
`core/pom.xml`, para publicar el `test-jar`).

**Deuda técnica deliberada.**
- *Formato almacenado.* Los nombres de clase y de campo forman parte del formato y no hay
  migración de documentos: renombrar un campo del dominio exige migrar los datos. `FORMAT_VERSION`
  existe para habilitarlo, pero no se implementó.
- *JEP 500.* Escribir campos `final` por reflexión funciona en Java 25 con advertencia futura; a
  partir de JDK 26 la plataforma empieza a restringirlo. Es la deuda más seria del códec; la salida
  es agregar constructores o fábricas de reconstrucción al dominio.
- *Consultas.* Los métodos que no tienen columna indexada (por ejemplo `PublishPendingDomainEvents`
  sobre `findAll()`) recorren la tabla completa. Aceptable para el volumen de la entrega.
- *Eventos y transacciones.* Los casos de uso publican eventos de forma síncrona dentro de la misma
  transacción; si un manejador falla se deshace toda la operación, lo que contradice la semántica
  documentada de "publicación fallida deja el evento pendiente". Se resuelve con un *outbox*
  transaccional en el paso de procesos de infraestructura (encolar en la misma transacción,
  despachar luego del commit, reintentar).
- *Portabilidad de SQL.* Se usa SQL portable (`FETCH FIRST`, `LOCATE`, `GENERATED ALWAYS AS
  IDENTITY`) para no atar los repositorios a H2; no se probó contra otro motor en producción.
- *Referencias compartidas.* Cada lectura fuera de una transacción devuelve una instancia nueva.
  Las pruebas de integración del núcleo que dependen de compartir referencias siguen corriendo
  sólo en memoria; las de este módulo reconstruyen el estado desde la base.
- *Políticas.* Siguen siendo configuración (`RegisteredCertificationPolicies`); cada certificado
  persiste su instantánea de política.
