# Decisiones de diseño

El proyecto corresponde a la Entrega 1: un módulo de dominio Java con modelos, contratos, casos de uso concretos y pruebas. La consigna no exige API REST, persistencia real, frontend, seguridad ni despliegue.

Además de las decisiones técnicas, se explicitan las interpretaciones adoptadas por el equipo para precisar comportamientos que el enunciado deja abiertos. Estas interpretaciones delimitan el alcance del modelo; no son requisitos adicionales impuestos por la consigna.

## 1. Separación entre dominio y aplicación

Se aplica **inversión de dependencias mediante puertos y adaptadores**. Las entidades, reglas y políticas de `domain` contienen el comportamiento del negocio. Los casos de uso de `application` coordinan repositorios, auditoría y eventos a través de interfaces recibidas por constructor. En las pruebas, `FullSystem` compone el sistema con adaptadores en memoria.

Esta separación permite demostrar el dominio sin elegir una base de datos o un framework de aplicación. Los contratos `Clock`, `IdGenerator` y `ActorProvider` permiten controlar tiempo, identificadores y autoría en los tests.

Se descartó acoplar las entidades a JPA o a un framework web porque introduciría infraestructura ajena al objetivo de esta entrega. Tampoco se usan Singleton ni Service Locator para obtener servicios: ocultarían dependencias y dificultarían aislar escenarios.

La consecuencia es una composición más explícita y un mayor número de interfaces. Los adaptadores en memoria no ofrecen durabilidad, aislamiento ni transacciones; esas garantías deberán definirse al incorporar persistencia.

## 2. Entidades, objetos de valor y responsabilidades

**Supuestos adoptados.** El responsable puede ser una persona o una organización. Las características se definen por tipo de activo; marca, modelo y número de serie son una base para equipos, no atributos universales. Las características del equipo no cambian después del alta; responsable y ubicación sí pueden cambiar con auditoría.

Cada criterio observado o rechazado origina un hallazgo que reúne sus motivos y una acción correctiva asociada. El hallazgo se asigna al responsable actual del activo al generarse durante el cierre y no se reasigna automáticamente por cambios posteriores. El seguimiento de la corrección pertenece a la acción; no se agrega al hallazgo otro estado de resuelto/no resuelto.

Se emplea un **modelo de dominio con operaciones de negocio** en `Asset`, `InspectionSchema`, `Inspection`, `Finding` y `Certificate`. Los identificadores específicos, `CorrectionPlan` y `ValidityPeriod` son objetos de valor. Las relaciones entre agregados se expresan principalmente por identificador, mientras `Finding` contiene su acción correctiva.

Esta organización concentra invariantes y diferencia el incumplimiento histórico de su reparación. Se descartaron tanto un modelo anémico con setters como un agregado único que contuviera todo el sistema: el primero dispersaría validaciones y el segundo mezclaría ciclos de vida independientes.

La coordinación entre agregados queda en los casos de uso. El encapsulamiento tiene un límite actual: algunos accesores exponen objetos internos mutables, aunque las colecciones se copien. También falta restringir por tipo el mapa de características del activo.

## 3. Versiones inmutables y datos históricos

**Decisiones adoptadas.** Cada tipo de activo tiene un único esquema aplicable; un esquema puede servir a varios tipos y activos. Cada inspección corresponde a un activo y un inspector, y comprende el esquema completo. Compartir esquema no comparte respuestas ni evidencias.

Se distinguen asignación e inicio: la primera fija activo, inspector y fecha prevista; el segundo selecciona automáticamente la última versión publicada del esquema aplicable. Los borradores no participan y no se permite iniciar sin una versión publicada.

`InspectionSchema` separa el borrador editable de las versiones publicadas. `StartInspection` fija una `SchemaVersion` y captura un `AssetSnapshot` con identidad, tipo, características, ubicación y datos básicos del responsable. Esos datos históricos permanecen estables incluso mientras la inspección sigue abierta.

Se eligieron **versiones compartidas e inmutables y capturas de datos mutables** para cumplir la conservación de reglas exigida por la consigna. Consultar siempre los datos actuales alteraría los antecedentes; copiar el esquema completo en cada inspección duplicaría contenido ya identificado por versión.

Las versiones referenciadas deben seguir siendo recuperables. Las reglas actuales son inmutables y cualquier extensión deberá conservar esa propiedad. El responsable capturado al inicio puede diferir del responsable al que se asigna un hallazgo al cierre: representan momentos distintos.

## 4. Estrategias de evaluación y carga progresiva

**Decisiones adoptadas.** Cada criterio pertenece a una sección y tiene una regla de rango numérico, sí/no u opciones con resultado asignado, además de requisitos de evidencia. La severidad depende del resultado concreto. Antes de publicar se exige un resultado único para toda respuesta admitida; las bandas numéricas declaran unidad y límites sin huecos ni superposiciones.

Interpretamos «registro progresivo» como permitir cargas incompletas que puedan continuarse, corregirse o eliminarse con auditoría. La carga valida formatos, tipos y opciones; una medición válida fuera del rango de aprobación se registra. La evaluación ordinaria ocurre al cerrar. Se permite cerrar con faltantes obligatorios, rechazando los criterios afectados con motivo explícito. Repetir el cierre no debe reevaluar ni duplicar hallazgos y acciones.

Las fotos y documentos se representan por referencias a archivos. El inspector determina su pertinencia y el sistema verifica la presencia exigida por tipo y cantidad. Si falta evidencia, se registra qué se exigía y qué no se presentó.

Se aplica **Strategy**: `Criterion` compone una `EvaluationRule` y `CriterionEvaluator` combina su resultado con los faltantes de evidencia. Esto permite incorporar otra regla sin crear una subclase de criterio para cada variante.

Se descartó un motor externo de reglas por la complejidad adicional de ejecución y diagnóstico. Tampoco se aplica Composite: inicialmente hay una regla por criterio y no se necesitan combinaciones ni secciones anidadas arbitrariamente.

Cada nueva estrategia necesita pruebas de admisibilidad y evaluación. La severidad de faltantes está fijada en `EvaluationReason.MISSING_MANDATORY_DATA`. La identificación actual de requisitos de evidencia por etiqueta todavía necesita validación contra ambigüedades.

## 5. Ciclos de vida con estados explícitos

**Decisiones adoptadas.** Planificar una acción consiste en indicar trabajo, ejecutor y fecha límite. El responsable del hallazgo elige la solución; una vez confirmados esos datos no se modifican. El ejecutor informa la realización con evidencia y el inspector verifica. Si la verificación falla, se conserva el intento y la misma acción permanece abierta.

Cumplir el plazo exige verificación satisfactoria y cierre antes del vencimiento. Una acción vencida admite ejecución y verificación tardías, pero conserva el incumplimiento del plazo incluso después de cerrarse. El plan se modela como datos de la acción, sin exigir un documento independiente.

Inspecciones, acciones y certificados usan **estados explícitos y métodos con condiciones de transición**. No se aplica State mediante una clase por estado porque los ciclos iniciales son pequeños. En `CorrectiveAction`, `deadlineBreached` separa el antecedente de vencimiento del progreso de ejecución.

Tratar el vencimiento como un estado terminal impediría la corrección tardía. La separación elegida permite avanzar sin borrar ese antecedente, aunque exige comprobar conjuntamente estado y plazo.

El tiempo se recibe explícitamente y los casos de uso de barrido materializan vencimientos. Las operaciones deben respetar la vigencia real aun si el barrido no se ejecutó; actualmente hay brechas en esa comprobación para certificados.

## 6. Cierre, elegibilidad y emisión como decisiones separadas

**Política adoptada.** Una observación exige corrección en plazo, pero permite certificar si su acción está planificada. Un rechazo bloquea hasta verificar la corrección, sin exigir repetir toda la inspección ni reescribir su resultado histórico. La emisión requiere una inspección cerrada, ningún rechazo sin corregir y ninguna acción abierta vencida.

La emisión se solicita explícitamente y cada inspección respalda como máximo un certificado; una solicitud repetida identifica el existente. Al emitir se conserva su vencimiento. Renovar exige que el anterior haya vencido, una nueva inspección completa y un nuevo certificado vinculado al anterior. Las acciones de inspecciones anteriores conservan su historia, pero no bloquean la renovación ni suspenden el certificado nuevo.

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

`Rectification` conserva los cambios y los registros de criterio mantienen evaluaciones sucesivas. `VoidedObligation` distingue la anulación de una obligación de una reparación verificada. `AuditRecorder` registra modificaciones confirmadas y decisiones sobre activos, esquemas, inspecciones, hallazgos, acciones y certificados, incluyendo borradores y cargas parciales.

Se eligió **estado actual acompañado de historia explícita**. Sobrescribir sin antecedentes impediría reconstruir decisiones. Event Sourcing exigiría reconstruir agregados desde eventos completos y mantener su reproducción y versionado; esa complejidad no es necesaria para el enfoque elegido.

La auditoría debe conservar elemento, acción, fecha, autor o ejecución automática, motivo y datos o estados anteriores y nuevos. Complementa las versiones y certificados, sin reemplazarlos. Algunas entradas actuales todavía contienen detalles insuficientes para reconstruir los cambios.

## 9. Informes como valores de salida

**Decisión adoptada.** Para la entrega del módulo de dominio se producen salidas estructuradas: acta con datos históricos y rectificaciones, resumen de hallazgos con sus acciones, y certificado con vigencia, estado y compromisos pendientes. Un bloqueo de emisión no impide generar acta y resumen; se informan los motivos y no se crea un certificado rechazado.

Los generadores construyen **proyecciones de consulta**. `ReportedValue` distingue valores originales y corregidos. Separar contenido y presentación permite probar la información exigida sin introducir formato visual en las reglas.

Se descartó generar PDF dentro del dominio. Tampoco se aplica CQRS completo con almacenes distintos: la separación de algunos contratos de consulta basta y evita sincronizar dos modelos persistidos.

Los informes se construyen al consultar y no son copias archivadas de un documento emitido. Sus datos actuales pueden cambiar, pero deben conservar la atribución de las rectificaciones; todavía hay brechas en esa atribución y en la historia mostrada por el resumen de hallazgos.

## 10. Pruebas de comportamiento con dependencias controladas

JUnit y AssertJ prueban reglas y ciclos de vida. Las pruebas de integración componen casos de uso con repositorios en memoria, reloj controlable y eventos síncronos. Esto permite comprobar vencimientos y efectos entre agregados sin esperas ni infraestructura externa.

Se descartaron pruebas con base de datos o interfaz para esta entrega. Sustituir todas las colaboraciones por mocks tampoco demostraría que los casos de uso funcionan juntos. Los adaptadores en memoria permiten esa integración, aunque no prueban concurrencia ni persistencia y conservan referencias a objetos mutables.

El proyecto compila para Java 25. `mvn test` ejecuta unitarios y `mvn verify` agrega integración y el reporte JaCoCo. La cobertura debe complementarse con escenarios de errores, rectificaciones sucesivas y exactitud de auditoría.

## 11. Definiciones que siguen abiertas

Quedan por precisar el catálogo concreto de características por tipo de activo, los campos identificatorios del responsable y una duración predeterminada de certificados, si se necesita.

También falta cerrar el tratamiento de hallazgos y acciones existentes cuando una rectificación cambia el incumplimiento sin eliminarlo. La implementación conserva el plan, pero esa solución no cubre satisfactoriamente acciones ya cerradas ni hallazgos previamente anulados. Es una limitación pendiente, no una política resuelta.
