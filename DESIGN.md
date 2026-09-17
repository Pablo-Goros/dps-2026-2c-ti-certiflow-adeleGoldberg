# Decisiones de diseño

La [consigna](docs/consigna_tp.md) exige un módulo de dominio Java compilable y probado, y un documento que explique los patrones y principios aplicados, dónde y por qué, las alternativas descartadas y sus consecuencias. El [PRD](docs/PRD.md) aporta las decisiones de negocio; sus preguntas pendientes no equivalen a requisitos acordados.

Este documento relaciona las decisiones técnicas con las definiciones y supuestos de negocio adoptados en el PRD para resolver ambigüedades del enunciado. Los bloques «Definiciones del PRD» indican lo acordado, no una garantía de que la implementación ya lo cumpla por completo. Se conservan los identificadores RF, S y C para distinguir requisitos, supuestos adoptados y consultas de validación con la cátedra. Las alternativas técnicas sirven para justificar el diseño actual; no pretenden reconstruir deliberaciones históricas que no estén registradas. Los diagramas y el inventario de componentes están en [arquitectura-actual.md](docs/arquitectura-actual.md).

## 1. Separar reglas de negocio y coordinación mediante puertos

**Decisión y aplicación.** Se aplica inversión de dependencias mediante puertos y adaptadores. Las entidades, reglas y políticas de `domain` no dependen de infraestructura. Los casos de uso de `application` coordinan esas reglas a través de contratos de repositorio, consulta, auditoría y publicación de eventos. Las dependencias se reciben por constructor. `FullSystem`, en los tests, compone el sistema con adaptadores en memoria.

**Motivo.** La entrega debe demostrar el dominio sin exigir base de datos, API ni interfaz. Los puertos `Clock`, `IdGenerator` y `ActorProvider` permiten controlar tiempo, identidad y autoría en las pruebas. La separación también evita que la elección posterior de persistencia determine las reglas.

**Alternativas no adoptadas.** Acoplar las entidades a JPA o a un framework de aplicación introduciría dependencias innecesarias para esta entrega. Tampoco se usa un Singleton o un Service Locator para obtener servicios: ocultarían las dependencias y dificultarían aislar escenarios.

**Consecuencias.** Hay más contratos y composición explícita. Los adaptadores en memoria solo demuestran los casos de uso; no ofrecen persistencia durable, aislamiento ni transacciones. Incorporar infraestructura exigirá definir esas garantías, además de implementar interfaces.

## 2. Modelar invariantes con entidades y objetos de valor

**Definiciones del PRD — [RF1](docs/PRD.md#rf1--catálogo-de-activos), [RF7](docs/PRD.md#rf7--hallazgos) y [RF8](docs/PRD.md#rf8--acciones-correctivas).** El responsable es una entidad que puede representar una persona o una organización. S1 adopta características predefinidas por tipo: marca, modelo y número de serie son una base para equipos, no atributos universales. S4 mantiene inmutables las características del equipo después del alta y permite cambiar responsable y ubicación con auditoría. Esto separa la identidad estable del activo de sus datos modificables; los atributos concretos y los campos identificatorios del responsable siguen postergados.

Cada criterio observado o rechazado genera exactamente un hallazgo que reúne sus motivos, y cada hallazgo genera automáticamente una acción pendiente de planificación. Un criterio aprobado no genera hallazgo. Esta cardinalidad uno a uno es una decisión del equipo, no una cantidad impuesta por la consigna. S5 asigna el hallazgo al responsable actual del activo al generarlo durante el cierre; cambios posteriores del catálogo no lo reasignan. El hallazgo no tiene un estado adicional de resuelto/no resuelto: la ejecución, verificación y cierre corresponden a su acción. Estas definiciones delimitan las responsabilidades de ambas entidades.

**Decisión y aplicación.** `Asset`, `InspectionSchema`, `Inspection`, `Finding` y `Certificate` tienen identidad y operaciones de negocio. Los identificadores específicos, `CorrectionPlan`, `ValidityPeriod` y los registros de evaluación son objetos de valor. Las relaciones entre agregados se expresan principalmente por identificadores. `Finding` contiene su acción correctiva, siguiendo la relación uno a uno adoptada en RF7–RF8.

**Motivo.** Las operaciones expresan intención y concentran validaciones: iniciar, cerrar, confirmar una planificación o resolver causas de suspensión. Los tipos de identificador evitan mezclar referencias por accidente. Separar el hallazgo de la ejecución correctiva preserva el hecho detectado: reparar el activo no cambia retroactivamente el resultado de la inspección.

**Alternativas no adoptadas.** Un modelo anémico con setters trasladaría las invariantes a todos los consumidores. Tampoco se creó un agregado único para activo, inspecciones, hallazgos y certificados: sus ciclos de vida y necesidades de consulta son diferentes.

**Consecuencias y límite actual.** Las operaciones entre agregados necesitan coordinación. Las copias de colecciones protegen su estructura, pero no vuelven inmutables los objetos contenidos: `Inspection.requireRecord()`, `Finding.correctiveAction()` y `Certificate.suspensions()` exponen objetos mutables. El encapsulamiento no está completo y no debe presentarse como garantía de que toda modificación atraviesa el caso de uso auditado. Las características de `Asset` se representan con un mapa inmutable, sin una jerarquía por tipo de activo; falta restringir sus atributos por tipo para cumplir S1, aunque el catálogo concreto de atributos siga pendiente.

## 3. Conservar versiones publicadas y capturas históricas

**Definiciones del PRD — [RF1](docs/PRD.md#rf1--catálogo-de-activos), [RF2](docs/PRD.md#rf2--esquemas-de-inspección), [RF3](docs/PRD.md#rf3--versionado) y [RF4](docs/PRD.md#rf4--asignación-de-inspecciones).** Cada tipo de activo responde a un único esquema en un momento dado; un esquema puede servir a varios tipos y activos. Compartir metodología no implica compartir respuestas, evidencias ni resultados. Cada criterio pertenece a una única sección. Los cambios de metodología se representan mediante nuevas versiones del mismo esquema, y las versiones publicadas son inmutables.

Una inspección corresponde a un activo y un inspector, y su alcance es el esquema completo: no se admiten inspecciones parciales. Asignar fija activo, inspector y fecha prevista; iniciar selecciona automáticamente la última versión publicada del esquema aplicable. «Última» excluye borradores y no significa elegir manualmente entre esquemas independientes. Sin esquema aplicable publicado no se puede iniciar. Esta interpretación está adoptada, con validación pendiente en C4; el nombre del estado anterior al inicio no está prescrito por la consigna.

Al iniciar se conservan identificador estable, tipo, características y ubicación del activo, junto con identificador y datos básicos de su responsable. Esa información no cambia por modificaciones posteriores del catálogo, incluso mientras la inspección sigue abierta. Por eso el momento de la captura coincide con la fijación de las reglas y no con la asignación o el cierre.

**Decisión y aplicación.** `InspectionSchema` separa un borrador editable de sus versiones publicadas. La publicación valida el contenido y conserva una nueva `SchemaVersion`. `StartInspection` fija la última versión publicada aplicable y captura un `AssetSnapshot`. Cierre y rectificación consultan la versión fijada, no la última disponible.

**Motivo.** RF3 exige reproducir las reglas vigentes al iniciar. El PRD distingue asignación e inicio: una publicación entre ambos momentos debe participar de la selección. La captura del activo conserva ubicación, características y responsable aunque el catálogo cambie. El responsable histórico de la inspección y el responsable actual al generar un hallazgo pueden diferir por S5.

**Alternativas descartadas.** Consultar siempre el esquema o activo actual alteraría la interpretación de los antecedentes. Copiar el esquema completo en cada inspección preservaría la historia, pero duplicaría contenido ya identificado por una versión compartida. Se conserva la referencia a la versión y una captura de los datos mutables del activo.

**Consecuencias.** Las versiones referenciadas deben seguir siendo recuperables. Las implementaciones actuales de reglas son inmutables; una futura implementación de `EvaluationRule` deberá respetar esa condición, que la interfaz por sí sola no impone. La auditoría complementa las versiones: registrar quién publicó no sustituye conservar las reglas publicadas.

## 4. Componer criterios con estrategias de evaluación

**Definiciones del PRD — [RF2](docs/PRD.md#rf2--esquemas-de-inspección), [RF3](docs/PRD.md#rf3--versionado), [RF5](docs/PRD.md#rf5--ejecución) y [RF6](docs/PRD.md#rf6--evaluación).** S2 limita inicialmente las reglas a rango numérico, sí/no y opciones con resultado asignado, con una sola regla por criterio. Antes de publicar se exige un resultado único para toda respuesta admitida: las bandas numéricas declaran unidad y límites inclusivos/exclusivos sin huecos ni superposiciones; sí/no y opciones cubren sus respuestas admitidas. La severidad depende del resultado concreto de la evaluación, no es una constante del criterio.

S3 interpreta «registro progresivo» como poder cargar información incompleta y continuar después. Antes del cierre se pueden corregir o eliminar registros con auditoría. La carga rechaza formatos, tipos y opciones inválidos, pero admite mediciones válidas fuera del rango de aprobación. La evaluación ordinaria ocurre solo al cerrar; «pendiente» significa aún no evaluado y no agrega un cuarto resultado. Se permite cerrar una inspección iniciada y abierta aunque falten datos obligatorios: los criterios afectados se rechazan con motivo explícito y los demás se evalúan normalmente. Repetir el cierre no reevalúa ni duplica hallazgos o acciones. S3 y la política de cierre están adoptadas, sujetas a C3 y C5.

Cada criterio declara tipo, obligatoriedad y cantidad de evidencia. Para fotos y documentos se guardan referencias a archivos; el inspector juzga su pertinencia y el sistema comprueba presencia y datos estructurados, sin interpretar automáticamente su contenido. Ante un faltante se documenta qué se exigía y qué no se presentó, sin inventar evidencia (RF7, C7). Esto delimita la automatización y permite explicar un rechazo aun cuando no existe un archivo presentado.

**Decisión y aplicación.** `Criterion` contiene una `EvaluationRule`: Strategy permite intercambiar rango numérico, sí/no u opciones sin crear una subclase de criterio para cada regla. `CriterionEvaluator` combina el resultado de esa estrategia con la comprobación de evidencias obligatorias. Cada resultado no aprobado conserva motivos y severidad.

**Motivo.** S2 acota las reglas iniciales. RF3 exige una respuesta inequívoca para cada dato admitido; por eso se comprueban cobertura y límites de las bandas al publicar. La carga valida admisibilidad, mientras la evaluación ordinaria ocurre al cerrar. Esto permite registros progresivos sin producir rechazos anticipados por información todavía incompleta.

**Alternativas descartadas.** Un motor externo de reglas o un lenguaje de expresiones agregaría validación, ejecución y diagnóstico que las tres estrategias actuales no necesitan. No se aplica Composite para combinar reglas ni para anidar secciones arbitrariamente: el PRD define una regla por criterio y una estructura de secciones y criterios.

**Consecuencias.** Agregar una regla exige una implementación Java y sus pruebas de admisibilidad, publicación y evaluación. La severidad de faltantes obligatorios está fijada en `EvaluationReason.MISSING_MANDATORY_DATA`; no es configurable por esquema. Las evidencias se identifican actualmente por etiqueta dentro del criterio: esa elección exige evitar ambigüedades, y la validación de etiquetas repetidas todavía está incompleta.

## 5. Expresar ciclos de vida con estados explícitos

**Definiciones del PRD — [RF8](docs/PRD.md#rf8--acciones-correctivas).** Planificar significa indicar trabajo, ejecutor y fecha límite en la propia acción; no exige otro documento o entidad de negocio llamada Plan. El responsable del hallazgo decide la solución y confirma esos datos, que luego no pueden modificarse. El sistema registra y sigue esa decisión humana. El ejecutor informa la realización con evidencia y el inspector verifica su suficiencia. Una verificación fallida mantiene abierta la misma acción para otro intento, conservando verificaciones y motivos.

Cumplir el plazo exige verificación satisfactoria y cierre antes del vencimiento; informar la ejecución no basta. Una acción vencida admite ejecución y verificación posteriores, pero conserva el incumplimiento del plazo aun después de cerrarse. Estas definiciones justifican separar el progreso de la acción de su antecedente de vencimiento y no permitir replanificar para borrar ese incumplimiento.

**Decisión y aplicación.** Inspecciones, acciones y certificados usan enums y métodos con condiciones de transición. No se aplica el patrón State mediante una clase por estado. En acciones correctivas, `deadlineBreached` conserva el incumplimiento del plazo independientemente del avance de la ejecución; el vencimiento no impide ejecutar y verificar tarde.

**Motivo.** Los ciclos iniciales tienen pocos estados y sus reglas caben en las entidades. Separar el incumplimiento del plazo del progreso evita perderlo cuando una acción finalmente se cierra. En certificados, las causas de suspensión se conservan individualmente para impedir la reactivación mientras quede alguna pendiente.

**Alternativas descartadas.** Una jerarquía State agregaría clases y delegación para transiciones todavía pequeñas. Un único booleano de suspensión no permitiría explicar ni resolver varias causas. Tratar una acción vencida como terminal impediría la corrección tardía acordada en RF8.

**Consecuencias.** Al crecer las transiciones habrá que revisar si los condicionales siguen siendo manejables. El tiempo se recibe explícitamente y los casos de uso de barrido materializan vencimientos; no hay planificador externo en esta entrega. Las operaciones que dependen de vigencia deben ser coherentes con la fecha real aunque el barrido aún no se haya ejecutado; esa coherencia todavía tiene brechas en certificados.

## 6. Separar cierre, elegibilidad y emisión

**Definiciones del PRD — [RF6](docs/PRD.md#rf6--evaluación) y [RF9](docs/PRD.md#rf9--certificación).** «Observado» significa una desviación que requiere corrección en plazo y no bloquea por sí sola la certificación. Con todos los criterios aprobados se permite emitir; con observaciones también, si sus acciones pendientes están planificadas y tienen fecha límite. Para emitir se exige inspección cerrada, ningún rechazo sin corrección verificada y ninguna acción abierta vencida. Verificar una corrección habilita la emisión sin reescribir el resultado histórico ni exigir repetir toda la inspección por el solo hecho de un rechazo. Esta política es la adoptada bajo consulta C6.

La emisión es una solicitud explícita posterior al cierre y comprueba las condiciones vigentes en ese momento. Cada inspección respalda como máximo un certificado; repetir la solicitud identifica el existente y no crea otro. Al emitir se fija y conserva el vencimiento, aunque la duración predeterminada siga sin acordarse. Renovar exige que el anterior haya vencido, una nueva inspección completa con las reglas vigentes al iniciarla y un nuevo certificado vinculado al anterior; la renovación anticipada queda fuera del alcance. Solo cuentan la nueva inspección y sus acciones: las obligaciones anteriores conservan su historia, pero no bloquean la renovación ni suspenden el nuevo certificado.

**Decisión y aplicación.** `CloseInspection` evalúa y registra no conformidades. `CertificationContextAssembler` reúne los datos necesarios para `CertificateIssuancePolicy`, que evalúa una lista de `IssuanceRequirement` y devuelve todos los bloqueos. `IssueCertificate` registra la decisión y crea el certificado; `RenewCertificate` agrega la coordinación de renovación. `CertificateValidityPolicy` separa el cálculo de vigencia.

**Motivo.** Un resultado observado o rechazado es un antecedente; la elegibilidad también depende del seguimiento correctivo actual. El PRD exige una solicitud explícita de emisión y distingue cierre de aprobación. La política puede explicar simultáneamente varios impedimentos sin conocer repositorios.

**Alternativas descartadas.** Emitir al cerrar confundiría decisiones distintas y no permitiría completar correcciones antes de solicitar emisión. Concentrar las consultas en `Certificate` acoplaría una entidad a otros agregados. No se usa Chain of Responsibility con interrupción en el primer fallo: se necesitan todos los motivos del bloqueo.

**Consecuencias.** Hay un paso de ensamblado y varios objetos de requisito. Las decisiones de emisión son variantes explícitas (`Issued`, `Blocked`, `AlreadyIssued`), sin crear un certificado rechazado. La composición incluye una restricción de un certificado no vencido por activo, adicional al máximo de uno por inspección acordado en el PRD; esa restricción requiere conciliación con el producto. La duración es inyectable y los tests eligen doce meses; no establece una duración universal acordada.

## 7. Coordinar efectos entre agregados mediante eventos

**Definiciones del PRD — [RF9](docs/PRD.md#rf9--certificación) y [RF10](docs/PRD.md#rf10--auditoría).** Las causas iniciales de suspensión son el vencimiento de una acción asociada y un rechazo descubierto por rectificación de la inspección de respaldo. El rechazo suspende inmediatamente el certificado no vencido o agrega otra causa si ya estaba suspendido. Una observación nueva no lo suspende de inmediato; su acción puede hacerlo si vence. La reactivación es automática al verificar y cerrar las acciones que resuelven la última causa pendiente, siempre que el certificado siga dentro de su vigencia. Una verificación fallida u otras causas pendientes mantienen la suspensión.

Reactivar conserva el vencimiento original y el historial, y la suspensión por sí sola no exige otra inspección completa. Si el certificado ya venció, corresponde renovar. El vínculo con la inspección de respaldo limita qué eventos afectan a cada certificado; las acciones de inspecciones anteriores no deben propagarse al renovado. La anulación de una obligación por rectificación es la excepción acordada al cierre verificado y se explica en la decisión 8.

**Decisión y aplicación.** Los casos de uso publican eventos como `CorrectiveActionExpired`, `CorrectiveActionClosed`, `CorrectiveActionVoided` y `CriterionResultRevised`. `CertificationReactions` los consume para suspender certificados o resolver sus causas. Los tests usan un despachador síncrono en memoria, una aplicación del mecanismo de publicación/suscripción.

**Motivo.** El seguimiento de una acción o la rectificación de una inspección no necesita conocer cómo se modifica un certificado. Los eventos comunican hechos de negocio y mantienen las reacciones en el componente que conoce la certificación.

**Alternativas no adoptadas.** Invocar la certificación desde cada entidad introduciría dependencias entre ciclos de vida. Un broker, una saga o un outbox durable exceden las necesidades de infraestructura de esta entrega.

**Consecuencias y límite actual.** La composición debe registrar el consumidor; publicar por sí solo no ejecuta la reacción. No hay garantías de entrega durable, rollback o atomicidad entre guardados, auditoría y eventos. Esto tampoco justifica dejar cambios parciales ante errores de validación: la rectificación actual necesita resolver ese problema incluso en memoria. La idempotencia de algunas operaciones no equivale a una garantía transaccional.

## 8. Conservar correcciones y auditoría sin Event Sourcing

**Definiciones del PRD — [RF10](docs/PRD.md#rf10--auditoría).** La auditoría abarca activos, esquemas, inspecciones, hallazgos, acciones y certificados. Registra cada modificación confirmada, también de borradores y cargas parciales, no cada pulsación. Conserva elemento afectado, acción, fecha, autor o ejecución automática y motivo cuando corresponde; las transiciones incluyen estado anterior y nuevo, y los cambios de datos permiten reconstruir ambos valores. Esa historia complementa, sin reemplazarlos, las versiones y certificados del negocio.

Después del cierre, el inspector asignado puede rectificar notas descriptivas y errores en respuestas, mediciones y referencias de evidencia, con motivo obligatorio, autor, fecha y valores originales y corregidos. No puede cambiar activo ni versión. Se reevalúan los criterios afectados con las reglas originales y se conservan ambos resultados y su explicación. Reparar el activo posteriormente se registra en la acción correctiva, no como rectificación de los hechos originales.

Si la rectificación elimina el incumplimiento, se conservan hallazgo y acción como antecedentes y se anula la obligación y sus efectos sobre la certificación, vinculando rectificación y motivo. No se inventan ejecución o verificación satisfactorias ni se agrega una resolución al hallazgo. La acción anulada deja de bloquear emisión o suspender por plazo; si era la última causa, se puede reactivar el certificado no vencido sin cambiar su vencimiento. Si se descubre un incumplimiento en un criterio antes aprobado, se generan hallazgo y acción; rechazo y observación producen los efectos diferenciados de la decisión 7. El tratamiento de un incumplimiento que persiste con otros motivos o resultado sigue pendiente en el PRD.

**Decisión y aplicación.** `Rectification` identifica autor, motivo, fecha y cambios anteriores/posteriores. Los registros de criterio conservan evaluaciones sucesivas. `AuditRecorder` agrega entradas sobre cambios y decisiones de los casos de uso. Anular la exigencia correctiva se representa con `VoidedObligation`, separada de ejecutar o verificar una reparación.

**Motivo.** La consigna prohíbe alterar libremente inspecciones cerradas. RF10 también distingue un error del registro original de una reparación posterior. Una rectificación que elimina el incumplimiento no debe inventar una verificación satisfactoria para cerrar su historia.

**Alternativas descartadas.** Sobrescribir valores sin antecedente impediría reconstruir decisiones. Event Sourcing exigiría reconstruir agregados desde un historial completo de eventos, además de versionarlos y reproducirlos. Aquí el estado actual reside en entidades y la historia lo complementa.

**Consecuencias y límite actual.** Estado, rectificaciones, hallazgos y auditoría deben mantenerse consistentes. La implementación conserva el plan al revisar un incumplimiento persistente, pero RF10 todavía deja pendiente esa política: no constituye una decisión de producto acordada. El tratamiento de acciones ya cerradas y hallazgos previamente anulados necesita definición y corrección. Además, algunas entradas actuales registran descripciones o estados genéricos insuficientes para reconstruir los cambios exigidos; disponer de un puerto de auditoría no demuestra por sí solo su cumplimiento.

## 9. Producir informes estructurados con consultas específicas

**Definiciones del PRD — [RF11](docs/PRD.md#rf11--informes).** Para esta entrega bastan salidas estructuradas o texto, sin PDF ni diseño visual. El acta conserva contexto histórico del activo, inspector, fechas, esquema y versión, datos registrados, resultados por criterio y rectificaciones. El resumen reúne motivos, severidad, responsable, evidencia presentada o faltante y acción con plazo, estado y verificaciones, sin inventar un estado de resolución del hallazgo. El certificado identifica activo, inspección y versión de respaldo, emisión, vencimiento y estado actual; si se emite con observaciones, incluye compromisos pendientes y sus fechas límite.

Impedir la certificación no impide producir acta y resumen: el intento informa los motivos de bloqueo y no genera un certificado rechazado. Los informes posteriores identifican las rectificaciones y distinguen información original y corregida (RF10). Esto exige proyecciones con historia y resultados explícitos, aunque no se implemente una representación visual.

**Decisión y aplicación.** Los generadores de acta, resumen y certificado construyen valores de salida. `ReportedValue` permite distinguir valores originales y rectificados. Los contratos de consulta ofrecen los datos necesarios sin imponer almacenamiento separado.

**Motivo.** RF11 admite salidas estructuradas o texto. Separar la proyección del documento de las reglas permite probar contenido e historia y añadir una representación visual posteriormente.

**Alternativas descartadas.** Generar PDF en el dominio introduciría formato y herramientas visuales ajenos al alcance. Tampoco se aplica CQRS completo con bases o modelos persistidos distintos: hay separación de algunos contratos, sin la sincronización adicional de dos almacenes.

**Consecuencias.** Los informes se construyen al consultar; su estado y compromisos pueden cambiar entre consultas. No existe una copia archivada del documento emitido. Las proyecciones deben conservar la atribución correcta de sucesivas rectificaciones y distinguir sus antecedentes; hay brechas actuales en esa atribución y en el resumen de hallazgos.

## 10. Verificar comportamiento con tiempo y adaptadores controlados

**Decisión y aplicación.** JUnit y AssertJ prueban reglas y ciclos de vida. Las pruebas de integración componen casos de uso con repositorios en memoria, reloj controlable y eventos síncronos. Maven separa unitarios (`mvn test`) de la verificación completa (`mvn verify`, que agrega integración y JaCoCo). El proyecto compila para Java 25.

**Motivo.** La consigna pide casos relevantes del negocio. Controlar el tiempo permite verificar vencimientos y reactivaciones sin esperas; componer los casos de uso permite comprobar consecuencias entre inspecciones, acciones y certificados.

**Alternativas no adoptadas.** Una base real o pruebas de interfaz no aportan a las reglas de esta entrega. Sustituir todas las colaboraciones por mocks tampoco mostraría si la emisión y sus reacciones funcionan juntas.

**Consecuencias.** Los tests no prueban concurrencia, persistencia ni fallos de entrega. Los adaptadores conservan referencias a objetos mutables: una excepción puede dejar cambios visibles aun sin llamar a `save`. La cobertura cuantitativa debe complementarse con escenarios de error, múltiples rectificaciones y exactitud de auditoría; una suite verde no garantiza todas las invariantes.

## 11. Estado de las definiciones y consultas

Los supuestos S1–S5 y los puntos marcados «Definido» en el PRD son decisiones adoptadas por el equipo. Que algunas se consulten con la cátedra no las convierte en propuestas sin adoptar ni significa que ya hayan sido validadas externamente. Las consultas se conservan en el [PRD](docs/PRD.md#consultas-para-la-cátedra):

| Consulta | Interpretación adoptada cuya validación se solicita | Decisión de este documento |
|---|---|---|
| C1 | Características predefinidas por tipo (S1). | 2 |
| C2 | Tres familias iniciales de reglas (S2). | 4 |
| C3 | Carga incompleta que puede continuarse (S3). | 4 |
| C4 | Asignación e inicio separados y selección automática de versión al iniciar. | 3 |
| C5 | Cierre permitido con faltantes obligatorios que producen rechazo. | 4 |
| C6 | Observaciones compatibles con certificación y rechazos que exigen corrección verificada. | 6 |
| C7 | Documentar lo exigido y lo ausente ante evidencia faltante. | 4 |

Siguen pendientes el catálogo concreto de características y los campos identificatorios del responsable (RF1), una duración predeterminada de certificados si se considera necesaria (RF9), y el tratamiento de hallazgos y acciones existentes cuando el incumplimiento persiste pero cambia (RF10). La postergación de esos detalles no debe confundirse con una alternativa descartada. Tampoco las elecciones actuales del código, como conservar siempre una acción existente o limitar a uno los certificados no vencidos por activo, se convierten por aparecer aquí en acuerdos del PRD.
