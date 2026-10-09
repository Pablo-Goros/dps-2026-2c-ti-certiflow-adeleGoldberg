# Plan de implementación — F3: políticas de certificación por jurisdicción

Seguimiento de implementación: etapas 1–7 del núcleo F3 completadas. `mvn verify` pasó con 220 unitarios y 124 pruebas de integración. Las decisiones definitivas se trasladaron a `DESIGN.md`; la sección 8 permanece como integración posterior pendiente.

## 1. Objetivo y alcance

Implementar [F3 de la Entrega 2](docs/entrega_2.md): cada jurisdicción define las severidades que bloquean la certificación, los plazos de vigencia y si admite certificados condicionales. El mismo conjunto de resultados debe poder conducir a decisiones diferentes por jurisdicción. Agregar una jurisdicción debe requerir registrar una política, sin cambiar el servicio central. Toda decisión debe identificar la política aplicada.

El trabajo principal se realiza sobre el núcleo hexagonal existente, incluyendo emisión del activo completo, emisión parcial, elegibilidad, renovación, auditoría e informes. La interacción con certificados parciales de F1 forma parte del alcance. La programación de versiones de esquemas de F2 es un trabajo independiente.

La Entrega 2 también exige REST, frontend, persistencia real, pruebas de repositorio/API y CI. El repositorio inspeccionado contiene el núcleo y adaptadores livianos, con almacenamiento en memoria para tests; todavía no tiene esas capas ni configuración de CI. La sección 8 registra los pasos adicionales necesarios para integrar F3 en la aplicación completa. Completar solo los pasos del núcleo no satisface todos los requisitos generales de la Entrega 2.

## 2. Arquitectura y comportamiento actuales

Las rutas de clases de producción indicadas a continuación son relativas a `core/src/main/java/ar/edu/itba/dps/certification/`; las de tests, a `core/src/test/java/ar/edu/itba/dps/certification/`.

| Punto actual | Evidencia en el proyecto | Consecuencia para F3 |
|---|---|---|
| Arquitectura hexagonal | `docs/arquitectura-hexagonal.md`, `DESIGN.md`, `ArchitectureBoundaryTest` | Mantener `adapter → application → domain`; los puertos pertenecen a aplicación. |
| Activo y captura histórica | `domain/catalogue/Asset`, `AssetSnapshot`; `RegisterAsset`; `CatalogueAssetDirectory` | Incorporar jurisdicción como dato explícito y conservarla en el snapshot. `location` es texto libre y no identifica una jurisdicción. |
| Coordinación de certificación | `application/certification/CertificateFactory` | Es la ruta de producción para emitir y renovar; consulta hechos, aplica reglas y delega construcción a `CertificateIssuer`. Debe resolver la política por jurisdicción. |
| Composición de restricciones | `domain/certification/issuance/CertificateIssuancePolicy`, `IssuanceRequirements` | Actualmente se agregan siempre seis requisitos estándar; los requisitos adicionales solo restringen. Esa composición no permite reemplazar la regla fija sobre rechazos. |
| Hechos filtrados por alcance | `domain/certification/issuance/CertificationContext` | Ya distingue alcance global/parcial, pero cuenta rechazos sin considerar su severidad. Extender sus hechos sin duplicar reglas por subsistema. |
| Vigencia | `CertificateValidityPolicy`, `FixedDurationValidityPolicy`, `ValidityPeriod` | La fábrica recibe una sola política de vigencia. `FullSystem` usa doce meses; F3 debe seleccionar el plazo del perfil jurisdiccional. |
| Resultado de emisión | `IssuanceDecision`: `Issued`, `Blocked`, `AlreadyIssued` | Falta metadata de política, y la elegibilidad devuelve solo una lista de bloqueos. |
| Certificado y condiciones | `Certificate`, `CertificateStatus`, `CertificateReport` | Los certificados solo tienen estado `VALID/SUSPENDED/EXPIRED`. Se emite con observaciones planificadas, pero la modalidad condicional no está representada. |
| Auditoría | `IssueCertificate`, `RenewCertificate`, `AuditDetail` | Se registra emisión y bloqueo; la repetición informa el certificado existente. Agregar identidad/versionado de política y alcance en forma estructurada. |
| Global derivado de parciales | `AllSubsystemsMustBeInForce`, `GlobalCertificate` | El global es una proyección sin persistencia propia. Debe mostrar si deriva de parciales condicionales y conservar su procedencia. |
| Suspensión y reactivación | `CertificateLifecycle`, `CertificationReactions` | Actualmente una rectificación rechazada suspende sin consultar severidad. Revisar la coherencia cuando una jurisdicción permita ese incumplimiento. |
| Tests y herramientas | `pom.xml`, `support/FullSystem`, `CertificationLifecycleIT`, `PartialCertificationIT`, `RenewalIT` | Java 25, Maven, JUnit 5, AssertJ y Mockito ya están disponibles. Surefire ejecuta `*Test`; Failsafe, `*IT`; JaCoCo reporta cobertura en `verify`. |

## 3. Decisiones de negocio propuestas

Estas decisiones concretan puntos que F3 no especifica. Documentarlas en `DESIGN.md` al implementar; los ejemplos de perfiles son datos de prueba y no normativa real.

1. **Jurisdicción explícita e inmutable del activo.** Crear `JurisdictionId` como objeto de valor de identificador abierto, validado y no vacío; evitar un enum cerrado. Exigirlo en el alta y capturarlo al iniciar la inspección. La reubicación modifica la ubicación textual; cambiar la jurisdicción requiere un futuro caso de uso específico, fuera de este incremento.
2. **Política seleccionada al evaluar o emitir.** Resolver el perfil registrado para la jurisdicción del activo en cada solicitud nueva. Una renovación resuelve el perfil actual y conserva el vínculo al certificado anterior. La versión del esquema congelada por la inspección y la versión de la política de certificación son conceptos independientes.
3. **Políticas inmutables y versionadas.** Identificar cada política con ID y revisión. Actualizarla implica registrar una revisión nueva, manteniendo accesible la anterior. Cada evaluación captura una referencia y una definición inmutable de las reglas aplicadas; los certificados existentes conservan la definición de su emisión.
4. **Incumplimientos pendientes.** Un criterio no aprobado pesa mientras no tenga corrección verificada que cubra su evaluación actual ni obligación anulada. No alcanza con que la acción tenga estado terminal: una rectificación posterior puede hacer reaparecer el incumplimiento. Usar la lógica de revisiones de `Finding`, extendiendo su consulta de resolución para observaciones y rechazos. Si falta el hallazgo correspondiente a una evaluación no aprobada, contar el incumplimiento y el bloqueo por acción faltante.
5. **Severidad y condicionalidad.** Una severidad configurada como bloqueante impide emitir, tanto para `OBSERVED` como para `REJECTED`. Un incumplimiento pendiente de severidad permitida puede emitir un certificado condicional únicamente si la política lo permite y la acción está planificada y dentro de plazo. Sin incumplimientos pendientes, emitir en modalidad regular. Una corrección verificada deja de bloquear sin borrar el resultado histórico.
6. **Reglas comunes obligatorias.** Mantener inspección cerrada, ausencia de acciones abiertas vencidas, planificación de acciones pendientes, ausencia de otro certificado vivo del mismo alcance e inspección no reemplazada por una posterior. Una política jurisdiccional no puede deshabilitar estas garantías. Separarlas de las reglas variables de severidad, rechazo y condicionalidad.
7. **Compatibilidad explícita.** Registrar un perfil de referencia para los fixtures actuales: doce meses, observaciones planificadas permitidas y rechazos pendientes bloqueantes. Su regla adicional sobre rechazos conserva el comportamiento previo; los perfiles F3 pueden decidir por severidad. Mantener la posibilidad de sumar restricciones particulares. Este perfil debe estar registrado expresamente: una jurisdicción desconocida no recibe una política predeterminada silenciosa.
8. **Modalidad distinta del estado.** Agregar `CertificateMode` (`REGULAR`, `CONDITIONAL`) como dato de emisión. Un condicional puede estar vigente, suspendido o vencido. La modalidad original se conserva aunque después se cumplan sus compromisos; el informe muestra los pendientes actuales. Una renovación vuelve a determinar la modalidad.
9. **Resolución inválida.** Jurisdicción sin política o configuración incoherente produce un error explícito de configuración/dominio, sin emitir ni consumir ID de certificado. Distinguirlo de `Blocked`, que expresa una decisión de negocio tomada con una política válida. Cuando no hubo política aplicada, registrar ese hecho como error de resolución, sin inventar una referencia.
10. **Idempotencia histórica.** `AlreadyIssued` devuelve la referencia y modalidad del certificado existente, incluso si cambió o desapareció la configuración vigente. Resolver primero el certificado existente en las rutas de emisión/renovación repetidas y evitar duplicar certificados o auditorías de emisión.
11. **Tiempo determinista.** Usar un único instante de evaluación y su fecha UTC en cada operación, en línea con la política de vigencia actual. La consulta previa es informativa: emisión reevalúa porque pueden cambiar políticas, tiempo o acciones. Para los mismos hechos, instante y revisión, ambos caminos deben coincidir.

## 4. Diseño propuesto

### 4.1. Tipos y responsabilidades

Los nombres nuevos son propuestas de implementación; respetar los límites de paquetes aunque se ajuste algún nombre.

| Tipo o contrato | Ubicación | Responsabilidad |
|---|---|---|
| `JurisdictionId` | `domain/catalogue` | Identificar jurisdicciones sin depender de certificación ni de esquemas. |
| `CertificationPolicyRef` | `domain/certification/policy` | Identificar jurisdicción, política y revisión. |
| `CertificationPolicySnapshot` | `domain/certification/policy` | Guardar definición inmutable: severidades bloqueantes, permiso condicional, plazos por modalidad y restricciones particulares identificables. No almacenar lambdas como historial. |
| `JurisdictionCertificationPolicy` | `domain/certification/policy` | Contrato de estrategia pura: referencia/definición, evaluación de hechos y cálculo de vigencia. Sin consultas a repositorios. |
| `ConfiguredJurisdictionCertificationPolicy` | `domain/certification/policy` | Implementación por configuración para los perfiles habituales; componerse con requisitos adicionales. |
| `CertificationPolicyRegistry` | `application/certification/port` | Resolver la política activa por jurisdicción y recuperar una revisión histórica. No inferir jurisdicción de la ubicación. |
| `RegisteredCertificationPolicies` | `adapter/certification` | Registro explícito por mapa/configuración; rechazar entradas ambiguas o incompatibles. Registrar otra jurisdicción no modifica `CertificateFactory`. |
| `CertificationAssessment` | `domain/certification/issuance` | Resultado de elegibilidad con alcance, instante, snapshot de política, todos los bloqueos y modalidad posible. Modalidad ausente si está bloqueado. |
| `CertificateMode` | `domain/certification` | Modalidad regular o condicional, independiente del estado de ciclo de vida. |
| Detalle estructurado de decisión | `domain/audit/AuditDetail` | Registrar operación, inspección, alcance, política, modalidad/resultado y motivos; reutilizar `AuditRecorder` y `AuditTrail`. |

Reutilizar `CertificateValidityPolicy` y `FixedDurationValidityPolicy` dentro del perfil. Validar los plazos al registrar la política, incluidos períodos cero/negativos y combinaciones inválidas. El plazo condicional puede ser distinto del regular; no agregar reglas sobre plazos de acciones correctivas que F3 no exige.

La referencia y el snapshot deben coincidir con la jurisdicción solicitada. El registro no permite reutilizar la misma identidad/revisión para otra definición ni usar una política de otra jurisdicción. Copiar defensivamente colecciones y validar todos los campos antes de modificar el registro.

### 4.2. Evaluación y emisión compartidas

Flujo propuesto para una solicitud nueva:

```text
caso de uso → CertificateFactory
  → cargar inspección y validar alcance
  → consultar jurisdicción mediante AssetDirectory
  → resolver política mediante CertificationPolicyRegistry
  → reunir hechos por alcance y tiempo de evaluación
  → evaluar requisitos comunes + política jurisdiccional
  → devolver evaluación bloqueada, o calcular modalidad y vigencia
  → construir mediante CertificateIssuer
caso de uso → guardar certificado y registrar decisión en auditoría
```

- Extender `AssetDirectory` con consulta de jurisdicción; al ser inmutable, coincide con el snapshot de una inspección iniciada. Esto permite resolver también consultas sobre inspecciones asignadas que todavía no tienen snapshot y devolver `InspectionNotClosed` con metadata de política.
- Extender `CertificationContext` con una proyección de incumplimientos pendientes por criterio, resultado y severidad. Filtrar por alcance una sola vez. Los criterios transversales pesan sobre todos los parciales; los de partes ausentes no participan. Incluir conteo/lista de compromisos pendientes para determinar modalidad.
- Refactorizar `IssuanceRequirements.standard()` para distinguir garantías comunes y reglas del perfil. Mantener la acumulación de todos los bloqueos; agregar bloqueadores tipados para severidad y condicionalidad prohibida.
- Agregar `assess(...)` al contrato de elegibilidad y compartirlo con emisión/renovación. Si se conservan `blockersFor(...)` para compatibilidad, deben delegar a esa evaluación. Las evaluaciones siguen siendo consultas sin guardar certificados; el resultado expone qué política se aplicó.
- Extender `IssuanceDecision.Blocked` con la evaluación/política aplicada; `Issued` obtiene la metadata del certificado; `AlreadyIssued` obtiene la metadata histórica. Actualizar constructores, factories y consumidores de los tipos sellados.
- La consulta sobre un certificado ya emitido debe devolver su situación y procedencia histórica, o expresar claramente que evalúa una solicitud nueva. Evitar mostrar una nueva política como si fuera la usada por el certificado existente.
- Mantener en la fábrica las validaciones de cierre, fecha de emisión y vínculo de renovación, incluso con estrategias personalizadas. Verificar que la vigencia comience en el instante de emisión y termine después antes de generar un ID.

### 4.3. Historia, informes y coherencia del ciclo de vida

- `Certificate` y `CertificateIssuer` reciben snapshot de política y modalidad. Mantener encapsulada la construcción del agregado.
- `IssueCertificate` y `RenewCertificate` registran decisiones exitosas y bloqueadas con política, revisión y alcance. Una respuesta repetida referencia el certificado original sin volver a registrar una emisión.
- `CertificateReport` e `IssuanceAttemptReport` exponen jurisdicción, política/revisión, modalidad y alcance. Cambiar `reportBlockedAttempt` para recibir una decisión/evaluación completa; una lista de bloqueos aislada no permite recuperar qué política se aplicó. Los informes históricos leen la metadata almacenada, no recalculan con la política vigente.
- `GlobalCertificate` deriva modalidad condicional si al menos un parcial es condicional. Exponer la procedencia de cada parcial y sus políticas, que podrían tener revisiones distintas si se emitieron en momentos diferentes. Mantener la intersección de vigencias y la exigencia de que todos los parciales estén en vigor; no persistir otro certificado global.
- Ajustar la reacción a rectificaciones para evitar suspender automáticamente por un rechazo que la política aplicada admite. `CertificationReactions` obtiene los hechos actuales y aporta una evaluación de cumplimiento a `CertificateLifecycle`, que sigue siendo puro. Usar el snapshot del certificado, no la política vigente.
- Para reevaluar un certificado existente, usar solo reglas de cumplimiento: severidad, restricciones del perfil, condicionalidad, planificación y plazos. No reaplicar bloqueos de emisión inicial sobre certificado ya existente, inspección posterior o predecesor. Si un regular adquiere incumplimientos pendientes, suspenderlo: cambiarlo a condicional requiere una nueva decisión explícita y queda fuera de este incremento.
- Conservar las causas auditables e idempotentes de suspensión y su ruteo por subsistema. Una nueva severidad bloqueante observada también debe poder originar una causa; revisar `SuspensionCause` y la resolución por criterio. No reactivar si queda otra causa o si venció. Cubrir reintentos de eventos antiguos para que no reapliquen hechos ya reemplazados por rectificaciones posteriores.

## 5. Secuencia de implementación

Cada etapa incluye sus tests unitarios. Completar una etapa antes de extender el flujo siguiente.

### Etapa 1 — Jurisdicción del activo

- [x] Crear `JurisdictionId` con validación y tests de valor.
- [x] Agregar jurisdicción obligatoria a `Asset`, `AssetSnapshot` y `RegisterAsset`, incluyendo auditoría del alta.
- [x] Extender `AssetDirectory` y `CatalogueAssetDirectory` para consultar jurisdicción.
- [x] Actualizar constructores y fixtures en `DomainWorld`, `FullSystem` y tests que crean activos/snapshots directamente. Asignar una jurisdicción explícita de referencia; evitar defaults implícitos en producción.
- [x] Probar que el snapshot conserva jurisdicción y que una reubicación textual no altera ese dato.

### Etapa 2 — Perfiles jurisdiccionales y registro

- [x] Crear referencia, snapshot, estrategia y perfil configurable; validar identidad, revisión, colecciones y períodos.
- [x] Crear puerto `CertificationPolicyRegistry` y adaptador `RegisteredCertificationPolicies`.
- [x] Registrar explícitamente el perfil de compatibilidad y dos perfiles de ejemplo con diferentes severidades, plazos y permiso condicional.
- [x] Probar resolución, recuperación histórica, duplicados, errores de configuración y adición de una tercera jurisdicción con la misma fábrica.

### Etapa 3 — Hechos y evaluación pura

- [x] Extender la consulta de resolución de `Finding` para cualquier resultado no aprobado, respetando revisiones y verificaciones.
- [x] Incorporar incumplimientos y compromisos pendientes en `CertificationContext`, con filtrado por alcance.
- [x] Separar requisitos comunes de los variables y crear los bloqueadores nuevos.
- [x] Implementar `CertificationAssessment` y elección de modalidad/vigencia con un solo instante por operación.
- [x] Agregar pruebas parametrizadas de reglas y de los casos límite de fechas, severidades y acciones.

### Etapa 4 — Integración en emisión y renovación

- [x] Inyectar `AssetDirectory` y `CertificationPolicyRegistry` en `CertificateFactory`; sustituir la dependencia de una única política global de emisión/vigencia.
- [x] Compartir evaluación entre `EvaluateIssuanceEligibility`, emisión completa/parcial y renovación completa/parcial.
- [x] Agregar metadata y modalidad a certificado/decisiones y actualizar `CertificateIssuer`.
- [x] Mantener idempotencia por inspección y alcance, y las validaciones de predecesor. Resolver repeticiones antes de consultar configuración vigente.
- [x] Actualizar `FullSystem`, helpers `Decisions` y sitios que construyen fábricas/políticas directamente, en especial `ReviewCorrectionsIT`.
- [x] Incorporar tests unitarios con mocks de puertos y assertions sobre efectos y ausencia de efectos en errores.

### Etapa 5 — Auditoría, informes y F1

- [x] Incorporar detalle estructurado de certificación en `AuditDetail` y actualizar emisión/renovación.
- [x] Extender informes de certificado y de intentos bloqueados, conservando validaciones de consistencia.
- [x] Propagar modalidad y procedencia en la derivación global de parciales.
- [x] Incorporar tests unitarios de auditoría, informes y derivación; adaptar `ReportingIT` y `AuditTrailIT`.

### Etapa 6 — Rectificación y ciclo de vida

- [x] Reevaluar cumplimiento con el snapshot aplicado al certificado; pasar hechos/evaluación al servicio puro de ciclo de vida.
- [x] Ajustar causas de suspensión por criterio y su resolución para severidades bloqueantes, incluyendo observaciones.
- [x] Probar permitido/bloqueante, regular/condicional, reintentos, resolución parcial de causas y vencimiento.
- [x] Adaptar `CertificateSuspensionTest`, `RectificationIT`, `RecurringNonConformityIT` y `PartialCertificationIT` según las reglas explícitas de F3.

### Etapa 7 — Flujos completos y documentación

- [x] Agregar `JurisdictionCertificationIT` con `FullSystem` y registro real en memoria.
- [x] Ejecutar unitarios y luego integración/cobertura con los comandos de la sección 6.
- [x] Revisar regresiones de certificación, renovación, informes, auditoría, rectificaciones y F1.
- [x] Actualizar `DESIGN.md` con clases agregadas/modificadas, refactorizaciones, alternativas, supuestos y deuda pendiente; actualizar `docs/arquitectura-hexagonal.md` con el puerto/adaptador nuevo.

## 6. Testing unitario: happy path, unhappy path y límites

Usar JUnit 5 y AssertJ. Los tests de dominio construyen objetos reales y controlan los instantes explícitamente. Los tests de aplicación usan Mockito para sustituir puertos (`InspectionQuery`, `FindingQuery`, `CertificateRepository`, `AssetDirectory`, `CertificationPolicyRegistry`, `Clock`, `IdGenerator`, `AuditTrail`) o colaboradores de los casos de uso. Evitar mockear entidades para probar reglas propias del dominio. Usar `TestClock`/fechas fijas y verificar el comportamiento observable.

Los `*IT` existentes combinan casos de uso con adaptadores en memoria; complementan, pero no reemplazan, los unitarios nuevos ni comprueban persistencia real o REST.

### 6.1. Matriz de pruebas

| Suite propuesta | Happy path | Unhappy path y bordes |
|---|---|---|
| `JurisdictionIdTest`, `AssetJurisdictionTest` | Alta, igualdad y snapshot con jurisdicción explícita; reubicación conserva jurisdicción. | Null, vacío/blancos; alta inválida sin guardar activo; snapshot inválido; fixtures sin dato obligatorio deben actualizarse. |
| `RegisteredCertificationPoliciesTest` | Resolver dos jurisdicciones; agregar una tercera sin cambiar la fábrica; recuperar revisión anterior luego de activar una nueva. | Jurisdicción no registrada; duplicado/ambigüedad; revisión inválida; reutilizar referencia con otro contenido; política de otra jurisdicción; colecciones mutadas desde afuera no alteran la definición. |
| `JurisdictionCertificationPolicyTest` | Aprobado → regular; incumplimiento permitido y planificado → condicional; corrección verificada → regular; distintos plazos por perfil/modalidad. | Severidad bloqueante → bloqueado; condicionalidad prohibida; acción faltante/no planificada/vencida; varias causas se acumulan; perfil no puede quitar garantías comunes. |
| `CertificationContextTest` | Incumplimientos filtrados por parcial; criterio transversal pesa en cada parcial; corrección verificada y obligación anulada dejan de pesar. | Hallazgo de otra inspección rechazado; hallazgo ausente no permite emitir; acción cerrada para una evaluación vieja no resuelve una rectificación nueva; criterio de parte ausente no pesa. |
| `CertificateFactoryTest` | Resolver jurisdicción una vez por evaluación y usar el mismo perfil para decisión/vigencia; igualdad entre elegibilidad y emisión para hechos fijos; emisión y renovación parciales. | Inspección inexistente/abierta; subsistema null/no declarado; política ausente/inválida; vigencia que empieza en otra fecha o no termina después; otro certificado vivo del mismo alcance; inspección reemplazada; cierre posterior a emisión; no generar ID si falla validación. |
| `IssueCertificateTest` | Guardar un certificado con política/modalidad y auditar decisión completa. | Bloqueado: no guardar certificado y auditar todos los bloqueos con política y alcance; error del registro: no emitir; `AlreadyIssued`: no guardar ni duplicar emisión; fallo de guardado: no auditar éxito. |
| `RenewCertificateTest` | Nueva inspección y predecesor vencido; vínculo histórico; usar perfil actual para el nuevo y preservar perfil anterior. | Sin predecesor; aún vigente; misma inspección; inspección iniciada antes o exactamente al emitir el anterior; repetición devuelve metadata histórica aunque cambie/falte configuración. |
| `CertificationAuditTest`, `GenerateCertificateReportTest` | Identidad/revisión, alcance, modalidad y vencimiento coinciden con decisión/certificado; reportar intento bloqueado completo. | Metadata ausente/incoherente; informes no reinterpretan historia tras cambiar el registro; bloqueos de otro alcance no se mezclan; no crear informe de éxito a partir de decisión bloqueada. |
| `ConditionalGlobalDerivationTest` | Todos los parciales en vigor; alguno condicional → global condicional; procedencia por parcial; fin igual al menor vencimiento. | Parcial faltante, suspendido o vencido; una política nueva no altera metadata de parciales existentes; varios perfiles/revisiones no se presentan como una única política aplicada. |
| `PolicyAwareCertificateLifecycleTest`, `CertificationReactionsTest` | Rectificación dentro de lo permitido mantiene condicional; nueva severidad bloqueante suspende; última causa resuelta reactiva. | Regular con incumplimiento nuevo suspende; otra causa pendiente impide reactivar; vencido no reactiva; evento de otro subsistema no afecta; reintento no duplica causa/auditoría; evento viejo no restituye incumplimiento ya superado. |

### 6.2. Parametrización y resultados concretos

- Usar `@MethodSource` para cruzar `LOW/MEDIUM/HIGH/CRITICAL`, resultado `OBSERVED/REJECTED`, severidades configuradas y permiso condicional. El resultado `APPROVED` no lleva severidad: cubrirlo aparte sin forzar un valor ficticio.
- Crear perfiles de prueba A y B: A bloquea `HIGH/CRITICAL`, admite condicionales y usa doce meses regular/seis condicional; B bloquea `MEDIUM/HIGH/CRITICAL`, prohíbe condicionales y usa seis meses regular. Con el mismo incumplimiento `MEDIUM` planificado, A emite condicional y B bloquea. Con resultados aprobados, ambos emiten regulares con vencimientos distintos.
- Para comparar jurisdicciones, usar los mismos hechos de evaluación y acciones en tests de política; en integración, crear activos/inspecciones equivalentes con distinta jurisdicción, sin modificar una inspección histórica.
- Cruzar corrección pendiente, verificada, anulada y verificada para una revisión anterior. Distinguir ejecución reportada de verificación satisfactoria: reportar ejecución no resuelve el incumplimiento.
- Probar fecha de acción exactamente igual a hoy y un día después de su límite; vencimiento del certificado un instante antes, exactamente en `expiresAt` y después. En `ValidityPeriod`, el extremo de vencimiento es exclusivo.
- Probar meses de distinta duración/año bisiesto usando la aritmética UTC de `FixedDurationValidityPolicy`; rechazar configuraciones cero/negativas antes de emitir.
- Verificar varios bloqueos simultáneos y comparar sus tipos/datos; evitar depender solo del texto del mensaje o del orden si este no forma parte del contrato.
- En tests de aplicación, usar `verify(..., never())` para ausencia de guardado/generación de ID y capturar el detalle auditado. Los tests del caso de uso deben distinguir error de configuración de bloqueo de negocio.

### 6.3. Integración y comandos para la implementación

`JurisdictionCertificationIT` debe cubrir: dos jurisdicciones con resultados equivalentes; incorporación de una tercera con la fábrica ya construida; emisión/bloqueo auditados; parciales y global condicional; cambio de revisión entre emisiones; renovación con perfil nuevo; reintento con metadata original; rectificación y suspensión según la política aplicada.

Con JDK 25 o superior y Maven disponibles:

```powershell
mvn test
mvn verify
```

`test` ejecuta unitarios, incluidos los límites arquitectónicos; `verify` agrega integración y el reporte JaCoCo en `target/site/jacoco/index.html`. Revisar cobertura de ramas nuevas y justificar ramas no cubiertas; el POM actual genera reporte, pero no impone un umbral de cobertura. Estos comandos se ejecutaron durante la implementación del núcleo F3; la validación final también revisó el reporte de cobertura.

## 7. Criterios de aceptación del núcleo F3

- [x] Los activos y snapshots contienen jurisdicción explícita.
- [x] Resultados equivalentes producen decisiones o vencimientos diferentes según perfiles jurisdiccionales.
- [x] Agregar una jurisdicción requiere registrar su perfil; no editar `CertificateFactory` ni los casos de uso.
- [x] Elegibilidad, emisión y renovación comparten evaluación y reglas por alcance.
- [x] Certificados regulares/condicionales se distinguen del estado de vigencia/suspensión/vencimiento.
- [x] Toda evaluación devuelve la política aplicada; emisiones y bloqueos se auditan con identidad, revisión y alcance.
- [x] Certificados, repeticiones e informes conservan procedencia histórica al cambiar la configuración.
- [x] La falta de política/configuración válida impide emitir y no se resuelve con defaults ocultos.
- [x] F1 mantiene el aislamiento entre subsistemas, criterios transversales, vigencias independientes y global derivado.
- [x] Rectificaciones no producen decisiones incoherentes con la política del certificado.
- [x] Los unitarios cubren éxito, error y bordes; la integración demuestra flujos entre componentes.
- [x] `mvn verify` pasa y la documentación registra clases, refactorizaciones y deuda.

## 8. Integración posterior con la aplicación completa de la Entrega 2

Estos pasos dependen de incorporar infraestructura/API/frontend al proyecto. Mantener los contratos anteriores como frontera del núcleo.

- [ ] **Persistencia:** guardar jurisdicción, definición/revisión de políticas, metadata del certificado y decisiones auditadas. Conservar revisiones históricas; definir migración explícita para activos/certificados anteriores sin esos datos. Implementar transacción para guardado del certificado y auditoría, y unicidad por inspección/alcance frente a concurrencia.
- [ ] **REST:** admitir jurisdicción en alta/consulta de activo; consultar políticas disponibles; devolver evaluación con política, alcance, bloqueos y modalidad; emitir/renovar devolviendo metadata histórica. Definir contratos y mapeo de errores de validación, recurso ausente y configuración sin filtrar detalles internos al usuario.
- [ ] **Frontend mínimo:** elegir jurisdicción al registrar activo y mostrar resultado, razones de bloqueo, modalidad, vencimiento y política aplicada en certificación. La política debe seleccionarla el backend a partir del activo.
- [ ] **Pruebas de repositorio reales:** persistir/recuperar jurisdicción y políticas versionadas; comprobar certificados/auditoría tras una nueva sesión, unicidad y rollback con la base elegida.
- [ ] **Pruebas de API reales:** alta → inspección → elegibilidad → emisión/renovación para las dos jurisdicciones; validar JSON, códigos y persistencia real, incluidos bloqueos, datos inválidos y jurisdicción sin política.
- [ ] **CI:** configurar JDK 25 y `mvn verify`, agregar ejecución de tests de infraestructura/API según su configuración y exigir checks exitosos para integrar PRs. La protección de ramas requiere configuración del hosting del repositorio.

## 9. Riesgos y deuda a registrar

- La principal refactorización es separar reglas universales y jurisdiccionales. El test `ReviewCorrectionsIT.additionalPolicyRequirementsCannotBypassTheMandatoryIssuanceRules` debe conservar su intención para garantías comunes y el perfil de compatibilidad, sin impedir perfiles que F3 admite por severidad.
- Agregar parámetros obligatorios cambia constructores, records y helpers ampliamente usados. Actualizar todos los sitios de creación en la misma etapa; no ocultar errores de adaptación con defaults productivos.
- La reevaluación por política requiere tratar observaciones resueltas y revisiones históricas correctamente; reutilizar el registro de verificación de `Finding` para evitar inferencias por estado de la acción.
- El registro por configuración es suficiente para incorporar jurisdicciones en el núcleo. Administración dinámica de jurisdicciones, aprobación de cambios normativos, vigencia futura de políticas y cambio de jurisdicción de un activo son extensiones pendientes.
- Para estrategias personalizadas, identidad y versión deben apuntar a una definición histórica recuperable. No prometer reconstrucción de reglas a partir de un nombre libre o de objetos ejecutables mutables.
- Los adaptadores en memoria no garantizan durabilidad, atomicidad ni concurrencia. Los tests unitarios pueden asegurar orden y ausencia de efectos ante validación fallida; las garantías de rollback y unicidad requieren los adaptadores reales de la sección 8.
- El archivo es temporal: al cerrar la implementación, trasladar las decisiones definitivas a `DESIGN.md` y conservar allí la deuda restante.
