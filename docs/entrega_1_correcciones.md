# adeleGoldberg

Consigna: CertiFlow

Entrega 1

Commit revisado: `baf3cc9` · 137 tests en verde

Los hallazgos marcados con **(test del revisor)** no salen de los tests del grupo: los reproduje yo con tests descartables escritos sobre una copia del repositorio, y cada uno va acompañado del test que lo demuestra. Los tests usan el harness del grupo (`FullSystem`) y los mismos helpers que sus tests de integración (`assignAndStart`, `inspectAndClose`, `rectifyTemperature`). El repositorio del grupo no se modificó.

Este informe se limita a lo que hay que arreglar o definir antes de avanzar. Lo que es opinable queda como preguntas abiertas al final.

## Lo que está bien

El dominio está modelado con cuidado y el DESIGN.md es muy bueno: 16 decisiones con dónde, por qué, alternativa descartada y consecuencia, más una lista de patrones que decidieron no aplicar.

- Los ids de cada concepto son tipos propios. `SchemaVersionId` junta el esquema y el número de versión, así que no se puede pedir la versión 3 de un esquema con el id de otro.
- El versionado de esquemas está muy bien resuelto. Publicar valida el borrador entero y devuelve todas las violaciones juntas, la versión publicada es inmutable, y la inspección congela la versión y un `AssetSnapshot` al iniciar.
- La severidad viaja en el resultado de la regla (`RuleOutcome`) y se autovalida, y las reglas de evaluación son un Strategy genuino.
- No hay ningún setter en el dominio. Las entidades internas (`CriterionRecord`, `CorrectiveAction`) tienen sus mutadores en visibilidad de paquete, y las colecciones se devuelven como copias inmutables.
- Java puro, sin dependencias de producción, con `Clock`, `IdGenerator` y `ActorProvider` inyectados.

## Hay que arreglar

### 1. La inspección no protege su evaluación

Es el centro del dominio y el agregado acepta lo que le pasen. `close` solo verifica que haya una evaluación por criterio: quién la calculó y con qué regla lo decide `CloseInspection`. Llamando a `close` directamente, una inspección sin respuestas ni evidencias cerró con todo aprobado **(test del revisor)**.

```java
public InspectionClosureResult close(Instant at, Map<CriterionId, CriterionEvaluation> evaluations) {
    // ...
    requireInProgress("close");
    Validate.ensure(evaluations.keySet().equals(records.keySet()),
            "every criterion of the frozen version must be evaluated exactly once at close");
    evaluations.forEach((criterionId, evaluation) -> requireRecord(criterionId).recordClosureEvaluation(evaluation));
    this.status = InspectionStatus.CLOSED;
```

```java
@Test
void anInspectionWithoutAnswersClosesAsApprovedWhenTheCallerSaysSo() {
    InspectionId id = assignAndStart();   // sin respuestas ni evidencias
    Inspection inspection = system.inspections.require(id);
    Instant now = system.clock.now();

    inspection.close(now, Map.of(
            TEMPERATURE, CriterionEvaluation.approved(now),
            DOCUMENTATION, CriterionEvaluation.approved(now)));

    assertThat(inspection.status()).isEqualTo(InspectionStatus.CLOSED);
    assertThat(inspection.requireRecord(TEMPERATURE).answer()).isEmpty();
    assertThat(inspection.currentEvaluations().values()).allMatch(e -> e.result().approved());
}
```

Lo mismo pasa con la admisibilidad: `recordAnswer` no mira la regla del criterio, eso lo hace el caso de uso `RecordAnswer`. Llamado directo, el agregado aceptó un sí/no en un criterio numérico, y el error apareció recién al intentar cerrar **(test del revisor)**.

```java
@Test
void theAggregateAcceptsAYesNoAnswerOnANumericCriterion() {
    InspectionId id = assignAndStart();
    Inspection inspection = system.inspections.require(id);

    inspection.recordAnswer(TEMPERATURE, YesNoAnswer.yes());   // TEMP es numérico
    system.recordAnswer.record(id, DOCUMENTATION, YesNoAnswer.yes());
    system.attachEvidence.attach(id, DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");

    assertThat(inspection.requireRecord(TEMPERATURE).answer()).contains(YesNoAnswer.yes());
    assertThatThrownBy(() -> system.closeInspection.close(id))
            .hasMessageContaining("holds an answer its rule does not admit");
    assertThat(inspection.status()).isEqualTo(InspectionStatus.IN_PROGRESS);
}
```

Y con la rectificación: `rectify` sí valida la admisibilidad, pero reevaluar es un segundo paso que hace el caso de uso. Llamado directo, quedó la medición nueva (30 °C, fuera de rango) con el resultado viejo (aprobado) **(test del revisor)**.

```java
@Test
void aRectificationWithoutReevaluationKeepsTheOldResult() {
    InspectionId id = inspectAndClose("5");   // aprobado
    Inspection inspection = system.inspections.require(id);

    inspection.rectify(system.schemaCatalog.requireVersion(inspection.requireFrozenSchemaVersionId()),
            RectificationId.of("r-1"), inspector.id(), system.clock.now(), "probe misread",
            List.of(new Correction.AnswerCorrection(TEMPERATURE, Measurement.of("30", "c"))));

    assertThat(inspection.requireRecord(TEMPERATURE).answer().orElseThrow().describe()).contains("30");
    assertThat(inspection.currentEvaluations().get(TEMPERATURE).result().approved()).isTrue();
}
```

*Por qué es un error:* la evaluación automática (aprobado, observado, rechazado) es la regla central del enunciado, y un agregado tiene que garantizar sus invariantes sin importar quién lo llame. Si el resultado lo decide quien llama, que una inspección esté cerrada no dice nada sobre si sus resultados son correctos, y los hallazgos y certificados que salen de ella heredan esa falta de garantía.

*Causa:* al iniciar, la inspección guarda solo el id de la versión congelada (`frozenSchemaVersionId`), no la `SchemaVersion`. Sin los criterios ni sus reglas no puede evaluar ni validar respuestas, así que esa lógica se fue a `CloseInspection` y `RecordAnswer`. La prueba es `rectify`: recibe la versión por parámetro y ahí sí valida la admisibilidad adentro. Es la misma regla con dos dueños.

*Sugerencia:* que la inspección reciba (o guarde, porque es inmutable) la `SchemaVersion` congelada y evalúe ella misma al responder, al cerrar y al rectificar.

### 2. Emitir y renovar no pasan por el dominio

El constructor de `Certificate` es público y solo valida no-nulos y que no se suceda a sí mismo. Se construyó un certificado válido sobre una inspección todavía en curso **(test del revisor)**.

```java
public Certificate(CertificateId id, AssetId assetId, InspectionId backingInspectionId,
        SchemaVersionId schemaVersionId, ValidityPeriod validity,
        CertificateId previousCertificateId) {
    this.id = Validate.required(id, "certificate id");
    // ... solo required
    Validate.ensure(!this.id.equals(previousCertificateId),
            "certificate " + id + " cannot succeed itself");
```

```java
@Test
void aCertificateCanBeBuiltOnAnOpenInspection() {
    InspectionId open = assignAndStart();
    Inspection inspection = system.inspections.require(open);
    Instant now = system.clock.now();

    Certificate certificate = new Certificate(CertificateId.of("c-1"), asset.id(), open,
            inspection.requireFrozenSchemaVersionId(),
            new ValidityPeriod(now, now.plus(Duration.ofDays(365))), null);

    assertThat(inspection.status()).isEqualTo(InspectionStatus.IN_PROGRESS);
    assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
}
```

La renovación tiene el mismo problema: su regla vive en un caso de uso que llama a otro, y no exige que la inspección nueva sea posterior al certificado que reemplaza. Se renovó con una inspección cerrada un día antes de emitir el certificado anterior **(test del revisor)**.

```java
@Test
void aCertificateIsRenewedWithAnInspectionOlderThanTheCertificateItReplaces() {
    InspectionId older = inspectAndClose("5");
    system.clock.advanceDays(1);
    Certificate first = issuedCertificate(system.issueCertificate.issue(inspectAndClose("5")));
    system.clock.advanceDays(400);
    system.expireCertificates.sweep();

    Certificate renewed = issuedCertificate(system.renewCertificate.renew(older));

    assertThat(system.inspections.require(older).closedAt().orElseThrow())
            .isBefore(first.validity().issuedAt());
    assertThat(renewed.previousCertificateId()).contains(first.id());
}
```

*Por qué es un error:* la condición para emitir es la regla más importante de la certificación. Si el modelo permite construir un certificado sin cumplirla, que un certificado exista no prueba que su inspección fuera elegible: la regla depende de que cada llamador se acuerde de consultar la política. Y renovar con una inspección anterior al certificado vencido certifica un estado del activo que ya había sido superado.

*Causa:* emitir se pensó como una coordinación entre agregados (inspección, hallazgos, certificados), así que quedó en el caso de uso `IssueCertificate`, y `Certificate` quedó como un contenedor de datos con constructor público. No hay ningún objeto del dominio que represente la decisión de emitir ni la de renovar.

*Sugerencia:* una fábrica en el dominio que exija la decisión de la política, con el constructor package-private, y que la renovación sea una operación de esa fábrica con su propia regla de antigüedad.

### 3. Una acción correctiva puede quedar sin plazo o nacer vencida

Está bien que la fecha límite venga del plan (lo dice el PRD). Lo que falta es un plazo para planificar: sin plan, `expireIfOverdue` devuelve `false` para siempre. Antes de emitir no importa, porque `EveryActionMustBePlanned` bloquea. Pero después de emitir, una rectificación que revela una observación deja una acción que nunca vence **(test del revisor)**.

```java
boolean expireIfOverdue(LocalDate today) {
    if (status.terminal() || plan == null || !plan.overdueOn(today)) {
        return false;
    }
```

```java
@Test
void anUnplannedActionNeverExpires() {
    InspectionId id = inspectAndClose("5");
    Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));
    rectifyTemperature(id, "20");   // revela una observación

    system.clock.advanceDays(300);
    system.expireActions.sweep();

    Finding finding = system.findings.findByInspection(id).getFirst();
    assertThat(finding.correctiveAction().status()).isEqualTo(CorrectiveActionStatus.PENDING_PLANNING);
    assertThat(finding.correctiveAction().deadlineBreached()).isFalse();
    assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
}
```

Además `CorrectionPlan` acepta una fecha límite ya vencida, así que la acción nace vencida. Si todavía no hay certificado, bloquea la emisión desde el primer momento. Si el certificado ya estaba emitido, lo suspende en el próximo barrido **(test del revisor, ambos casos)**.

```java
public record CorrectionPlan(String work, PartyId executor, LocalDate dueDate) {
    public CorrectionPlan {
        work = Validate.requiredText(work, "planned work");
        Validate.required(executor, "executor");
        Validate.required(dueDate, "due date");   // no se compara con la fecha de planificación
    }
```

```java
@Test
void aPlanDueInThePastBlocksIssuanceFromTheStart() {
    InspectionId id = inspectAndClose("20");   // observación
    Finding finding = system.findings.findByInspection(id).getFirst();

    system.planCorrectiveAction.plan(finding.id(), "improve ventilation", PartyId.of("executor"),
            LocalDate.parse("2026-02-01"));   // hoy es 2026-03-01

    assertThat(blockers(system.issueCertificate.issue(id))).isNotEmpty();   // OverdueOpenAction
}

@Test
void aPlanDueInThePastSuspendsAnIssuedCertificateOnTheNextSweep() {
    InspectionId id = inspectAndClose("5");
    Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));
    rectifyTemperature(id, "20");
    Finding finding = system.findings.findByInspection(id).getFirst();

    system.planCorrectiveAction.plan(finding.id(), "improve ventilation", PartyId.of("executor"),
            LocalDate.parse("2026-02-01"));
    system.expireActions.sweep();

    assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
}
```

*Por qué es un error:* el PRD dice que una observación revelada por rectificación sigue "el tratamiento ordinario", con el vencimiento como posible causa de suspensión. Hoy un certificado puede seguir vigente indefinidamente con una observación que nadie atiende. Y una planificación que nace vencida no es una planificación válida: quien planifica no se entera, y el bloqueo o la suspensión le llegan de sorpresa.

*Causa:* el plazo existe solo como atributo del plan (`CorrectionPlan.dueDate`). El ciclo de vida de la acción no tiene ningún plazo antes de llegar a `PLANNED`. Y `CorrectionPlan` se valida solo, sin conocer la fecha en que se planifica, así que no tiene contra qué comparar.

*Sugerencia:* un plazo para planificar desde la creación del hallazgo, y que confirmar el plan reciba la fecha de hoy y rechace una fecha límite anterior.

### 4. El modelo no tiene una noción confiable de quién actúa

El DESIGN afirma que la separación entre quien ejecuta y quien verifica "se verifica, no solo se documenta". No es así: el agregado controla que informe el ejecutor planificado y el caso de uso que verifique el inspector, pero nadie impide que sean la misma persona. Planifiqué con el inspector como ejecutor, informó el inspector, verificó el inspector y la acción cerró **(test del revisor)**.

```java
@Test
void theInspectorVerifiesTheirOwnExecution() {
    InspectionId id = inspectAndClose("30");   // rechazo
    Finding finding = system.findings.findByInspection(id).getFirst();

    system.planCorrectiveAction.plan(finding.id(), "recalibrate", inspector.id(), LocalDate.parse("2026-04-01"));
    system.reportExecution.report(finding.id(), "done", List.of("file://photo.jpg"), inspector.id());
    system.verifyCorrectiveAction.verify(finding.id(), true, "looks fine", inspector.id());

    assertThat(system.findings.require(finding.id()).correctiveAction().status())
            .isEqualTo(CorrectiveActionStatus.CLOSED);
}
```

Además, en `VerifyCorrectiveAction` la regla de rol se chequea contra un parámetro, no contra quien está actuando. Con el actor autenticado como un tercero ("intruder") y pasando el `PartyId` del inspector, la verificación se aceptó y la acción cerró. La `Verification` quedó a nombre del inspector y la `AuditEntry` de `CORRECTIVE_ACTION_VERIFIED` a nombre del intruso **(test del revisor)**.

```java
public Finding verify(FindingId findingId, boolean satisfactory, String reason, PartyId verifiedBy) {
    // ...
    Validate.ensure(inspection.inspector().equals(verifiedBy), /* ... */);
    // ...
    new Verification(satisfactory, reason, verifiedBy, at)
    // ...
    audit.record(/* ... */);   // actor = actors.current(), otra fuente
```

```java
@Test
void aThirdPartyVerifiesByPassingTheInspectorsId() {
    InspectionId id = inspectAndClose("30");
    Finding finding = system.findings.findByInspection(id).getFirst();
    system.planCorrectiveAction.plan(finding.id(), "recalibrate", PartyId.of("executor"), LocalDate.parse("2026-04-01"));
    system.reportExecution.report(finding.id(), "done", List.of("file://photo.jpg"), PartyId.of("executor"));

    system.actors.actingAs(Actor.user(PartyId.of("intruder"), "intruder"));
    system.verifyCorrectiveAction.verify(finding.id(), true, "looks fine", inspector.id());

    Finding after = system.findings.require(finding.id());
    AuditEntry entry = system.auditTrail.withAction(AuditAction.CORRECTIVE_ACTION_VERIFIED).getFirst();
    assertThat(after.correctiveAction().status()).isEqualTo(CorrectiveActionStatus.CLOSED);
    assertThat(after.correctiveAction().verifications().getFirst().verifiedBy()).isEqualTo(inspector.id());
    assertThat(entry.actor().displayName()).isEqualTo("intruder");
}
```

*Por qué es un error:* separar ejecutor y verificador es un control: quien hizo el trabajo no puede aprobarlo, y su propio DESIGN lo promete. Una regla de rol chequeada contra un dato de entrada no es una regla, porque basta con pasar el dato correcto. Y la auditoría existe para reconstruir quién hizo qué: si contradice al registro de negocio del mismo hecho, pierde su valor.

*Causa:* el "quién" tiene dos fuentes. Los casos de uso lo reciben por parámetro (`verifiedBy`, `reportedBy`) y `AuditRecorder` lo toma de `ActorProvider`. Además `Finding` no conoce al inspector, que está en otro agregado, así que `CorrectiveAction` no tiene con qué comparar al ejecutor.

*Sugerencia:* que el actor salga siempre de `ActorProvider` y se le pase al dominio; que `Finding` copie el inspector al crearse (como ya copia el responsable) y que `CorrectiveAction` rechace que el ejecutor verifique.

### 5. El borrador del esquema se edita por fuera de la raíz

`OpenDraft` y `EditDraft` devuelven el `SchemaDraft` mutable. Se le agregó una sección directamente al borrador devuelto y se publicó sin ningún `SCHEMA_DRAFT_EDITED` para ese esquema **(test del revisor)**.

```java
public SchemaDraft open(SchemaId schemaId) {
    InspectionSchema schema = schemas.require(schemaId);
    SchemaDraft draft = schema.openDraft();
    // ...
    return draft;
}
```

```java
@Test
void theDraftIsEditedOutsideTheRootWithoutAudit() {
    InspectionSchema schema = system.createSchema.create("Factory inspection", Set.of(AssetType.FACTORY));
    SchemaDraft draft = system.openDraft.open(schema.id());

    draft.addSection(Section.of("Safety", 1, DomainWorld.temperatureCriterion()));
    var result = system.publishSchemaVersion.publish(schema.id());

    assertThat(result.publishedVersion().criteria()).hasSize(1);
    assertThat(system.auditTrail.entriesFor(AuditedElementRef.schema(schema.id().value()),
            AuditAction.SCHEMA_DRAFT_EDITED)).isEmpty();
}
```

*Por qué es un error:* la raíz deja de ser la única puerta de entrada al agregado. Y que sea un borrador no lo excusa: el PRD pide auditar "cada modificación confirmada [...] también en borradores de esquemas".

*Causa:* `SchemaDraft` tiene `addSection` y `removeSection` públicos, e `InspectionSchema` lo devuelve tal cual desde `openDraft`, `requireDraft` y `draft`. Con `CriterionRecord` y `CorrectiveAction` no pasa, porque sus mutadores son de paquete.

*Sugerencia:* que la raíz exponga `addSection` y `removeSection`, y que `SchemaDraft` tenga mutadores de paquete como las otras entidades internas.

### 6. La aplicabilidad del esquema tiene huecos

"Un único esquema por tipo de activo" es una regla del PRD, pero vive copiada en `CreateSchema` y `ChangeSchemaApplicability`. `applyTo` no la conoce. `stopApplyingTo` chequea el tamaño antes de verificar que el tipo pertenezca al set: sacar un tipo que el esquema no tiene no hace nada, y el caso de uso lo audita igual como un cambio.

```java
public void applyTo(AssetType assetType) {
    applicableAssetTypes.add(Validate.required(assetType, "asset type id"));
}

public void stopApplyingTo(AssetType assetType) {
    Validate.ensure(applicableAssetTypes.size() > 1,
            "schema " + id + " must remain applicable to at least one asset type");
    applicableAssetTypes.remove(assetType);
}
```

*Por qué es un error:* una regla del PRD sin dueño en el dominio depende de que cada camino nuevo que cambie la aplicabilidad se acuerde de chequearla. Además `stopApplyingTo` puede dejar un tipo de activo sin esquema, y por PRD eso bloquea en silencio las inspecciones nuevas de ese tipo.

*Causa:* "un esquema por tipo" es una invariante entre varios esquemas, o sea entre agregados distintos, y ningún esquema individual puede verificarla solo. Hace falta un servicio de dominio que consulte a los demás esquemas, y como el puerto del repositorio vive en application (ver punto 7), ese servicio no se puede escribir en el dominio.

*Sugerencia:* un servicio de dominio de aplicabilidad que tenga la regla en un solo lugar.

## Hay que definir

### 7. Dónde viven las reglas de negocio

Varias reglas del PRD terminaron en la capa de aplicación:

- **Qué bloquea la emisión.** `CertificateIssuancePolicy` parece un Specification, pero cada requisito compara un conteo con cero. Qué cuenta como "rechazo sin verificar" o "acción anulada que no bloquea" se decide en los filtros de `RepositoryFindingQuery`.
- **Un esquema por tipo de activo**, en dos casos de uso (punto 6).
- **Las consecuencias de rectificar** (anular, revelar o revisar el hallazgo, suspender el certificado) las decide `RectifyClosedInspection`, y además publica los eventos antes de guardar la inspección y de auditar. Con un suscriptor que falla, el hallazgo revelado quedó creado y la rectificación no quedó auditada **(test del revisor)**.

```java
// RepositoryFindingQuery (application)
public List<Finding> overdueOpenActionsOf(InspectionId inspectionId, LocalDate today) {
    return findings.findByInspection(inspectionId).stream()
            .filter(finding -> !finding.obligationVoided())
            .filter(finding -> finding.correctiveAction().overdueAndOpen(today))
            .toList();
}

// IssuanceRequirements (domain)
return context.overdueOpenActions() == 0 ? Optional.empty() : Optional.of(/* ... */);
```

```java
@Test
void aFailingSubscriberLeavesTheFindingCreatedAndTheRectificationUnaudited() {
    InspectionId id = inspectAndClose("5");
    system.events.register(event -> {
        if (event instanceof CriterionResultRevised) {
            throw new IllegalStateException("subscriber down");
        }
    });

    assertThatThrownBy(() -> rectifyTemperature(id, "30")).hasMessage("subscriber down");

    assertThat(system.findings.findByInspection(id)).hasSize(1);   // el hallazgo revelado existe
    assertThat(system.auditTrail.withAction(AuditAction.INSPECTION_RECTIFIED)).isEmpty();
}
```

*Por qué hay que definirlo:* las tres tienen la misma causa. Los puertos están en `application/**/port`, y como se vio en la clase de arquitectura, el dominio comprende toda la lógica de negocio incluyendo las interfaces de los puertos; las implementaciones concretas quedan afuera. No es solo una cuestión de ubicación: con los puertos en application, un servicio de dominio no puede consultar hallazgos ni esquemas, así que cada regla que cruza agregados terminó en un caso de uso. Si mañana alguien implementa `FindingQuery` en SQL, tiene que reescribir reglas de negocio en infraestructura.

*Sugerencia:* mover los puertos al dominio. Que la política reciba los hallazgos y aplique ella `blocksCertification`, `obligationVoided` y el vencimiento. Y que los agregados acumulen sus eventos para que el caso de uso los publique después de persistir.

## Preguntas abiertas

- **¿Por qué el motivo de una entrada de auditoría se chequea en ejecución y no con tipos?** `AuditEntry` ata la presencia del motivo a la acción (`AuditAction.requiresReason()`). Partir `AuditAction` en acciones con y sin motivo la haría cumplir el compilador. Tal como está es defendible, pero el motivo es un `String` y uno en blanco pasa.
- **¿Por qué `relocate` y `assignResponsible` modifican el activo y además devuelven un `FieldChange`?** Mezcla comando y consulta, ata el agregado al formato de la auditoría, y es inconsistente con `ChangeSchemaApplicability`, donde el `FieldChange` lo arma el caso de uso. ¿Consideraron un evento `AssetRelocated(from, to)`?
- **¿Cómo se cumple el supuesto S1?** Dice que las características son "predefinidas por tipo de activo", pero son un `Map<String,String>`, así que un activo acepta cualquier característica. Y con `AssetType` como enum, un tipo de activo nuevo exige recompilar.
- **¿Cómo distinguen un error de programación de una regla violada?** `Validate` se usa 254 veces como `required` y 37 como `ensure`, y los dos tiran la misma `DomainException` con un `String`. La mayoría de la "validación" es defensa contra null, y quien llama no puede distinguir un argumento nulo de una regla de negocio incumplida.
