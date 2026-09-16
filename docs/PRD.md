# PRD — Inspección y certificación de activos

Fuente: [consigna del trabajo práctico](docs/consigna_tp.md). Los RF se numeran según su orden en la consigna.

En cada RF, «Qué exige» describe el comportamiento solicitado por la consigna. «Definido» registra las decisiones acordadas. «Falta definir» identifica preguntas pendientes; no representa requisitos adicionales ni decisiones ya acordadas. Las interpretaciones propuestas se identifican por separado.

Las respuestas acordadas se incorporan al RF correspondiente. Las propuestas pendientes no se consideran decisiones adoptadas. Este documento define comportamiento e información del negocio; las clases y los mecanismos de almacenamiento se resolverán posteriormente.

## Supuestos adoptados

| ID | RF | Supuesto |
|---|---|---|
| S1 | RF1 | Las características son predefinidas por tipo de activo. Marca, modelo y número de serie son el punto de partida para equipos; no se asume que apliquen a todos los tipos. |
| S2 | RF2 y RF6 | Inicialmente se admiten reglas de rango numérico, respuestas sí/no y opciones con resultado asignado. |
| S3 | RF5 | El registro progresivo permite cargar parte de una inspección y continuarla en otro momento. |
| S4 | RF1 | Por ahora, marca, modelo, número de serie y demás características del equipo no pueden modificarse después del alta. Responsable y ubicación sí pueden cambiar. |
| S5 | RF7 | El hallazgo se asigna al responsable actual del activo al generarse durante el cierre. Cambios posteriores del responsable del activo no reasignan automáticamente los hallazgos existentes. |

## Consultas para la cátedra

| ID | Consulta |
|---|---|
| C1 | ¿Es válido adoptar características predefinidas por tipo, con marca, modelo y número de serie como base para equipos? Validar S1. |
| C2 | ¿Alcanza con reglas de rango numérico, sí/no y opciones con resultado asignado? Validar S2. |
| C3 | ¿«Registro progresivo» implica permitir cargas incompletas que se continúan posteriormente? Validar S3. |
| C4 | ¿Es correcta la interpretación adoptada de distinguir asignación e inicio, determinar automáticamente el único esquema correspondiente al tipo de activo y fijar su última versión publicada al iniciar? |
| C5 | ¿Se admite cerrar con evidencia obligatoria faltante, rechazando los criterios afectados por ese motivo? Política adoptada por el equipo, pendiente de validación con la cátedra. |
| C6 | ¿Es válida la política adoptada de permitir certificar con observaciones sujetas a corrección en plazo y bloquear la emisión por rechazos hasta verificar sus correcciones? |
| C7 | ¿Es suficiente documentar un hallazgo por evidencia faltante indicando qué se exigía y qué no se presentó? Es la solución adoptada por el equipo. |

## Requisitos funcionales

### RF1 — Catálogo de activos

- Qué exige: Registrar y consultar los activos que se inspeccionan, con tipo, responsable, ubicación y características.
- Supuesto adoptado: Características predefinidas por tipo, según S1. El catálogo concreto de atributos no es necesario para cerrar este PRD.
- Definido: Se permite cambiar responsable y ubicación, conservando los cambios en auditoría.
- Supuesto adoptado: Las características del equipo no se modifican después del alta, según S4.
- Definido: La inspección mantiene la identidad del activo y conserva sus datos históricos relevantes tal como eran al inspeccionarlo. Los cambios posteriores del activo no reescriben esos datos históricos ni crean otro activo.

#### Pendientes y propuestas

- Definido: Al iniciar la inspección se conservan el identificador estable del activo, tipo, características, ubicación e identificador y datos identificatorios básicos del responsable. Los cambios posteriores del catálogo no modifican esta información, incluso durante una inspección abierta.
- Alcance: La lista concreta de características por tipo y campos identificatorios del responsable se posterga; no bloquea la definición de los comportamientos del negocio ni el cierre del PRD.
- Definido: Responsable es una entidad del dominio y puede representar una persona o una organización. Su implementación y datos mínimos se definirán posteriormente.

### RF2 — Esquemas de inspección

- Qué exige: Crear plantillas organizadas en secciones y criterios. El enunciado también establece evidencias obligatorias, niveles de severidad y reglas de aprobación.
- Definido: Un mismo esquema puede utilizarse para inspeccionar varios activos. Cada inspección conserva sus propias evidencias y resultados; compartir el esquema no implica compartir esos registros.
- Definido: Un esquema puede aplicar a más de un tipo de activo.
- Definido: El esquema representa la metodología completa de evaluación, organizada en secciones y criterios. Cada tipo de activo responde a un único esquema en un momento dado; todos los activos de ese tipo se evalúan con ese esquema. Los cambios de metodología se representan mediante nuevas versiones del mismo esquema.
- Definido: Cada criterio pertenece a una única sección dentro del esquema.
- Definido: Cada criterio tiene una sola regla de evaluación, además de sus requisitos de evidencia. A futuro podría admitir varias reglas combinadas; esa capacidad queda fuera del alcance inicial.
- Supuesto adoptado: Reglas de rango numérico, sí/no y opciones con resultado asignado, según S2.
- Definido: Cada criterio declara las evidencias requeridas, su tipo, obligatoriedad y cantidad cuando corresponda.
- Definido: Cada evidencia se vincula al criterio dentro de una inspección concreta. El inspector se responsabiliza de la pertinencia de fotografías y documentos y registra las respuestas necesarias; el sistema comprueba la presencia de la evidencia requerida y evalúa los datos estructurados. No se exige interpretación automática de imágenes o documentos.

#### Selección automática

- Definido: El tipo de activo determina el único esquema aplicable, sin elección manual entre esquemas independientes. «Latest» significa la última versión publicada de ese esquema, que se fija al iniciar la inspección; los borradores no participan de esa selección.

### RF3 — Versionado

- Qué exige: Publicar nuevas versiones de los esquemas sin modificar las inspecciones anteriores. Cada inspección debe conservar las reglas vigentes cuando fue iniciada, aunque después se publique una versión nueva.
- Información que se debe conservar: Las versiones publicadas del esquema, con sus secciones, criterios, evidencias obligatorias, severidades y reglas de aprobación. Debe poder identificarse qué versión utilizó cada inspección y recuperarse su contenido original.
- Relación con RF10: Conservar versiones permite reconstruir las reglas utilizadas. La auditoría registra las acciones sobre el esquema, como quién publicó una versión y cuándo; son necesidades diferentes.
- Definido: Una versión publicada es inmutable. Cualquier cambio de contenido requiere preparar y publicar una nueva versión.
- Definido: Antes de publicar se valida que cada regla produzca exactamente un resultado para toda respuesta válida. Las reglas numéricas declaran unidad y límites inclusivos/exclusivos, sin superposiciones ni huecos en el dominio de respuestas admitidas. Las reglas de sí/no y opciones asignan un resultado a cada respuesta admitida.
- Definido: Al iniciar la inspección se fija la última versión publicada del esquema correspondiente al tipo de activo. Esa referencia permanece inmutable para la inspección. Una nueva versión publicada después no afecta a la inspección iniciada.

### RF4 — Asignación de inspecciones

- Qué exige: Designar inspector, fecha prevista y alcance de una inspección.
- Definido: Una inspección corresponde a un único activo y un único inspector.
- Definido: El alcance comprende el esquema completo. No se admiten inspecciones parciales.
- Definido: Se distinguen asignación e inicio. Al asignar se indican activo, inspector y fecha prevista; el esquema se determina automáticamente por el tipo de activo, no se elige manualmente. Al iniciar se fija su última versión publicada. La consigna no prescribe el nombre de un estado para la etapa previa al inicio. Ver C4.
- Definido: No se permite iniciar si el tipo de activo no tiene un esquema aplicable con una versión publicada.

### RF5 — Ejecución

- Qué exige: Registrar progresivamente respuestas, mediciones, evidencias y observaciones.
- Supuesto adoptado: Se permite cargar información parcialmente y continuarla posteriormente, según S3. Debe consultarse con la cátedra.
- Definido: Para fotografías y documentos alcanza con registrar una referencia al archivo. No se requiere gestionar su contenido en esta entrega.
- Definido: Antes del cierre se permite corregir o eliminar registros, conservando los cambios en auditoría. Después del cierre se requiere rectificación auditable.
- Definido: Se permite cerrar con datos obligatorios faltantes. Al cerrar, los criterios afectados se rechazan con un motivo explícito de información o evidencia obligatoria faltante; los demás se evalúan normalmente. Ver C5.
- Definido: La evaluación automática se realiza únicamente como parte del cierre. Durante la carga se registran y corrigen datos sin evaluar criterios.
- Aclaración: Antes del cierre, «pendiente» significa que todavía no se evaluó; no incorpora un cuarto resultado a aprobado, observado y rechazado ni exige un estado específico por criterio. La carga incompleta no genera rechazos durante la ejecución.

#### Validación de carga y cierre

- Definido: Durante la carga se rechazan datos con formato o tipo inválido y opciones no admitidas. Una medición numérica válida fuera del rango de aprobación se registra; su resultado se determina al cerrar. Validar la carga no implica evaluar anticipadamente el criterio.
- Definido: Solo se puede cerrar una inspección iniciada y todavía abierta. No se exige información completa: los faltantes obligatorios producen los rechazos acordados. El cierre evalúa los criterios y genera los hallazgos y acciones correspondientes. Un nuevo intento de cierre no vuelve a evaluar ni duplica esos registros. Cerrar una inspección y aprobar sus resultados son decisiones distintas.

### RF6 — Evaluación

- Qué exige: Determinar automáticamente si cada criterio está aprobado, observado o rechazado, a partir de los datos registrados y las reglas aplicables.
- Supuesto adoptado: Reglas de rango numérico, sí/no y opciones con resultado asignado, según S2. Cada regla configura sus límites, unidad o resultados por respuesta según corresponda, con las validaciones de publicación de RF3.
- Definido: Al cerrar, la falta de información obligatoria rechaza el criterio afectado, según RF5.
- Definido: La evaluación ordinaria se realiza únicamente como parte del cierre y se conservan los resultados. No se evalúa durante la carga. Una rectificación posterior requiere la comprobación excepcional definida en RF10.
- Definido: Cada criterio configura cómo sus respuestas o mediciones se traducen en aprobado, observado o rechazado mediante una única regla. No se combinan varias reglas por criterio en el alcance inicial; la obligatoriedad de evidencias se comprueba adicionalmente.
- Definido: Toda respuesta válida tiene un único resultado según la regla configurada; la ausencia de información o evidencia obligatoria produce rechazo. La política de certificación acordada se describe en RF9.
- Definido: La severidad se determina a partir del resultado concreto de evaluar el criterio, aplicando una escala de severidad a ese resultado. No es un valor fijo del criterio y puede variar según la respuesta o medición registrada.

#### Significado acordado de observaciones

La regla define las condiciones para obtener «observado»: una desviación que requiere corrección en plazo y que, por sí sola, no bloquea la certificación. La ausencia de evidencia obligatoria sigue produciendo rechazo. Esta política fue acordada por el equipo y se mantiene como consulta de validación C6; ver RF9.

### RF7 — Hallazgos

- Qué exige: Crear no conformidades con severidad, evidencia y responsable.
- Supuesto adoptado: Se asigna inicialmente al responsable del activo, según S5.
- Definido: Ante evidencia obligatoria faltante se registra qué se exigía y qué no se presentó. No se inventa una evidencia presentada; consultar C7.
- Definido: Cada criterio observado o rechazado genera exactamente un hallazgo dentro de la inspección, reuniendo sus motivos. Los criterios aprobados no generan hallazgos. Es una decisión de alcance del equipo, no una cantidad impuesta por la consigna.

#### Seguimiento de la corrección

- Definido: El hallazgo registra el incumplimiento detectado. No tiene un estado adicional de resuelto/no resuelto ni una operación de resolución. La ejecución, las evidencias de corrección, las verificaciones y el cierre se registran en su acción correctiva asociada. La certificación consulta esa información sin modificar el resultado histórico del criterio.

### RF8 — Acciones correctivas

- Qué exige: Gestionar la planificación, el vencimiento, la verificación y el cierre de las acciones correctivas.
- Definido: Se genera automáticamente una acción correctiva por cada hallazgo. La relación inicial es uno a uno: cada hallazgo tiene una acción y cada acción corresponde a un hallazgo.
- Definido: Planificar la acción consiste en indicar qué se hará, quién debe ejecutarla y su fecha límite.
- Definido: Una vez confirmada la planificación, no se permite modificar el trabajo previsto, el ejecutor ni la fecha límite. Esto no impide registrar ejecuciones, evidencias y nuevos intentos de verificación en la misma acción.
- Aclaración terminológica: La consigna exige «planificación» de acciones correctivas. «Plan» se refiere a esos datos de la misma acción; no se requiere un documento ni una entidad adicional llamada Plan.
- Definido: El sistema crea el hallazgo y el registro de su acción pendiente de planificar. El responsable del hallazgo decide cómo resolverlo y completa la acción indicando el trabajo, ejecutor y fecha límite. La decisión sobre la solución es humana; el sistema registra esa decisión y gestiona su seguimiento, vencimiento, verificación y cierre.
- Definido: El responsable de ejecutar la acción informa su realización y adjunta evidencia. El inspector verifica si la corrección fue suficiente para cerrarla.
- Definido: Si la verificación falla, la misma acción permanece abierta para otro intento. Se conservan las verificaciones realizadas y sus motivos.

- Definido: Una acción vencida admite ejecución tardía y verificación posterior. Se conserva el incumplimiento del plazo aunque después se cierre satisfactoriamente.
- Definido: El vencimiento de una acción correctiva vinculada a un certificado emitido provoca su suspensión.

- Definido: Cumplir el plazo exige una verificación satisfactoria y el cierre de la acción antes del vencimiento. Informar la ejecución no basta. Una verificación satisfactoria posterior permite cerrar la acción, pero no elimina el incumplimiento del plazo registrado; la reactivación del certificado se rige por RF9.

### RF9 — Certificación

- Qué exige: Emitir, suspender, renovar y gestionar el vencimiento de certificados.
- Información que se debe conservar: Los certificados otorgados, el activo al que corresponden, la inspección que los respalda, su vigencia y su estado. Los acontecimientos y motivos que explican sus cambios se conservan en la auditoría de RF10. En una renovación se conserva el vínculo con el certificado anterior.
- Definido: Con todos los criterios aprobados se permite emitir. Con observaciones y sin rechazos se permite emitir antes de completar las acciones, exigiendo su corrección en plazo. Los rechazos bloquean la emisión hasta verificar satisfactoriamente las correcciones que la impiden; no se exige repetir toda la inspección por el solo hecho de haber obtenido rechazos.
- Definido: La verificación de correcciones no reescribe los resultados originales de la inspección cerrada. Verificar una acción en RF8 es distinto de evaluar los criterios al cierre en RF6.
- Definido: Si vence una acción vinculada a un certificado emitido, ese certificado se suspende.
- Definido: Si una rectificación da como resultado un criterio rechazado, se suspende de inmediato el certificado no vencido respaldado por esa inspección. Si el certificado ya estaba suspendido, el rechazo se agrega como una causa que debe resolverse antes de reactivarlo. Un resultado observado no genera una suspensión inmediata: mantiene el tratamiento ordinario de las observaciones y su acción puede causar la suspensión posterior si vence sin una corrección verificada.
- Definido: Se permite levantar la suspensión después de verificar satisfactoriamente las correcciones y cerrar las acciones correspondientes, siempre que estén resueltas todas las causas de suspensión y el certificado continúe dentro de su vigencia. Si falla la verificación o persisten otras causas, permanece suspendido.
- Definido: La reactivación conserva la fecha de vencimiento original y el historial de la suspensión. La causa de suspensión, por sí sola, no exige una nueva inspección completa. Si el certificado ya venció, corresponde gestionar la renovación.
- Definido: Para emitir se requiere una inspección cerrada, ningún rechazo sin corrección verificada y ninguna acción abierta vencida. Las acciones pendientes deben estar planificadas y tener fecha límite.
- Definido: Al emitir se indica y conserva la fecha de vencimiento. La duración predeterminada puede definirse posteriormente.
- Definido: Renovar exige una nueva inspección completa, con la versión vigente del esquema al iniciarla, y emitir un nuevo certificado vinculado al anterior. Inicialmente se renueva una vez vencido el certificado anterior; la renovación anticipada queda fuera del alcance.
- Definido: Inicialmente las causas de suspensión son el vencimiento de una acción correctiva asociada y un criterio rechazado descubierto mediante una rectificación de la inspección que respalda el certificado. La reactivación es automática al verificar satisfactoriamente y cerrar la última acción correspondiente a las causas de suspensión, siempre que el certificado no haya vencido.
- Definido: La emisión se solicita explícitamente después del cierre. Cerrar una inspección no emite ni intenta emitir automáticamente un certificado. Cada solicitud comprueba las condiciones de emisión vigentes en ese momento.
- Definido: Cada inspección puede respaldar como máximo un certificado.
- Definido: Una solicitud repetida de emisión no crea otro certificado: informa que la inspección ya tiene uno y lo identifica.
- Definido: La renovación se determina por la nueva inspección que la respalda y sus acciones asociadas. Las acciones pendientes de inspecciones anteriores no bloquean la renovación ni suspenden el nuevo certificado; conservan su vínculo e historia originales, sin considerarse cerradas automáticamente por renovar.

#### Pendientes y propuestas

- Definir una duración predeterminada si se considera necesaria.

### RF10 — Auditoría

- Qué exige: Conservar un historial de modificaciones, decisiones y transiciones de estado. Una inspección cerrada no puede alterarse libremente: las correcciones posteriores deben realizarse mediante una rectificación auditable.
- Definido: La auditoría abarca activos, esquemas, inspecciones, hallazgos, acciones correctivas y certificados; no se limita a rectificaciones de inspecciones.

#### Información e historia que se conserva

- Información que se debe conservar en cada evento: El elemento afectado, la acción o cambio realizado, la fecha y hora, quién lo realizó (o si fue una operación automática) y el motivo cuando corresponda. Para cambios de estado, el estado anterior y el nuevo; para modificaciones de datos auditadas, la información anterior y posterior necesaria para reconstruir el cambio.
- Acontecimientos que debe cubrir el historial:
  - Activos: Cambios de responsable y ubicación, conservando los valores anteriores y nuevos.
  - Esquemas: Creación, modificaciones confirmadas de borradores y publicación de versiones, identificando la versión involucrada.
  - Inspecciones: Modificaciones auditadas, decisiones y transiciones, incluido el cierre y las rectificaciones posteriores.
  - Hallazgos: Creación y modificaciones de sus datos, si se habilitan. No se incorporan transiciones de resolución; las verificaciones y el cierre corresponden a la acción correctiva.
  - Acciones correctivas: Planificación, vencimiento, resultado de la verificación y cierre, junto con sus modificaciones.
  - Certificados: Emisión, suspensión, reactivación, renovación y vencimiento, con sus respectivos cambios y motivos cuando correspondan.
- Rectificaciones: Deben conservar el registro original y permitir identificar la corrección realizada y su explicación. Corregir un dato erróneo de una inspección no equivale a registrar que el activo fue reparado después de inspeccionarlo.
- Relación con RF3 y RF9: El historial de auditoría complementa las versiones de esquemas y los certificados; no reemplaza esos registros del negocio.

#### Rectificaciones

- El inspector asignado puede rectificar observaciones descriptivas y errores de carga en respuestas, mediciones y referencias de evidencias. Se registran motivo obligatorio, autor, fecha y valores anteriores y nuevos, conservando el original.
- No se puede cambiar el activo ni la versión del esquema.
- El sistema vuelve a evaluar los criterios afectados con la versión original. La rectificación puede cambiar su resultado; se conservan el resultado original, el corregido y el motivo del cambio, sin sobrescribir la historia.
- Los informes posteriores identifican las rectificaciones y distinguen la información original de la corregida.
- Una reparación posterior se registra en la acción correctiva, no como rectificación de los hechos originales.
- Definido: Si una rectificación elimina el incumplimiento que originó un hallazgo y su acción, ambos se conservan como antecedentes, pero se deja sin efecto la exigencia de corregir y sus consecuencias sobre la certificación. Se registra el vínculo con la rectificación y su motivo. No se registra una ejecución ni una verificación satisfactoria ficticia, ni se agrega una operación de resolución al hallazgo.
- Definido: Una acción cuya exigencia quedó sin efecto por rectificación no bloquea la emisión ni causa suspensión por su plazo. Si era la única causa de suspensión, se permite reactivar el certificado siempre que no haya vencido; se conserva el historial y la fecha de vencimiento original. Esta es una excepción al tratamiento ordinario de acciones de RF8 y RF9, no una modificación de su planificación.
- Definido: Si la rectificación revela un incumplimiento en un criterio antes aprobado, se generan el hallazgo y la acción correspondientes según RF7 y RF8.
- Definido: Si el nuevo resultado es rechazado, se suspende de inmediato el certificado no vencido respaldado por la inspección rectificada. Si es observado, no se suspende de inmediato; el hallazgo y la acción siguen el tratamiento ordinario, incluido el vencimiento como posible causa posterior de suspensión. La suspensión, sus causas y una eventual reactivación se rigen por RF9 y se registran en la auditoría.
- Pendiente: Definir el tratamiento de hallazgos y acciones existentes cuando el incumplimiento no desaparece pero cambian sus motivos o resultado. La auditoría conserva los cambios, pero no determina por sí sola esas consecuencias de negocio.

#### Detalle de las modificaciones auditadas

- Definido: Se registra cada modificación confirmada, con autor, fecha y valores anteriores y nuevos, también en borradores de esquemas y cargas parciales de inspecciones. No se registra cada pulsación de teclado, sino cada operación de modificación confirmada.

### RF11 — Informes

- Qué exige: Producir el acta de inspección, el resumen de hallazgos y el certificado.
- Definido: El acta incluye identificación de la inspección, activo y datos históricos, inspector, fechas, esquema y versión, respuestas, mediciones, referencias de evidencia, resultados por criterio y rectificaciones.
- Definido: El resumen de hallazgos lista, por inspección, criterio, motivos, severidad, responsable, evidencia presentada o faltante y acción asociada con su plazo, estado y verificaciones. No se agrega un estado de resolución al hallazgo.
- Definido: El certificado incluye identificador, activo, inspección de respaldo, esquema y versión, emisión, vencimiento y estado actual. Si se emite con observaciones incluye compromisos pendientes y fechas límite.
- Definido: Si no se puede certificar, se producen igualmente el acta y el resumen; el intento de emisión informa los motivos del bloqueo. No se genera un certificado rechazado.
- Definido: Para esta entrega alcanza con salidas estructuradas o texto. No se exige PDF ni diseño visual.
