# Arquitectura actual

Este documento representa la implementación vigente. Está escrito en [Mermaid](https://mermaid.js.org/), por lo que GitHub y la vista previa de Markdown de VS Code pueden renderizar los diagramas directamente.

El proyecto sigue una arquitectura de puertos y adaptadores: los casos de uso dependen de interfaces (`port`), y las implementaciones en memoria que permiten ejercitar el sistema viven en `src/test`.

## Componentes y dependencias

```mermaid
flowchart TB
    subgraph Application["Capa de aplicación · casos de uso"]
        CatalogueUC["Catálogo<br/>RegisterParty · RegisterAsset<br/>RelocateAsset · ChangeAssetResponsible"]
        SchemaUC["Esquemas<br/>CreateSchema · OpenDraft<br/>EditDraft · PublishSchemaVersion"]
        InspectionUC["Inspecciones<br/>Assign · Start · Record<br/>Close · Rectify"]
        FindingUC["Hallazgos y acciones<br/>Plan · Report · Verify · Expire"]
        CertificationUC["Certificación<br/>Evaluate · Issue · Renew · Expire"]
        ReportsUC["Reportes<br/>GenerateInspectionAct<br/>GenerateFindingsSummary<br/>GenerateCertificateReport"]
    end

    subgraph Services["Servicios de aplicación"]
        AssetDirectory["CatalogueAssetDirectory"]
        SchemaCatalog["PublishedSchemaCatalog"]
        FindingService["FindingService<br/>(implementa FindingRegistry)"]
        ContextAssembler["CertificationContextAssembler"]
        Reactions["CertificationReactions<br/>(DomainEventHandler)"]
        Audit["AuditRecorder"]
    end

    subgraph Ports["Puertos (interfaces)"]
        AssetPorts["AssetRepository · PartyRepository<br/>AssetDirectory"]
        SchemaPorts["SchemaRepository · SchemaCatalog"]
        InspectionPorts["InspectionRepository · InspectionQuery<br/>FindingRegistry"]
        FindingPorts["FindingRepository · FindingQuery"]
        CertificatePort["CertificateRepository"]
        InfrastructurePorts["Clock · IdGenerator · ActorProvider<br/>DomainEventPublisher · AuditTrail"]
    end

    subgraph Domain["Dominio"]
        Catalogue["Catálogo<br/>Party · Asset · AssetSnapshot"]
        Schema["Esquemas<br/>InspectionSchema · SchemaDraft<br/>SchemaVersion · Criterion · EvaluationRule"]
        Inspection["Inspecciones<br/>Inspection · CriterionRecord<br/>Rectification"]
        Evaluation["Evaluación<br/>CriterionEvaluator · CriterionEvaluation"]
        Finding["Hallazgos<br/>Finding · CorrectiveAction"]
        Certificate["Certificados<br/>Certificate · CertificateIssuancePolicy"]
        AuditModel["Auditoría<br/>AuditEntry"]
        Events["Eventos de dominio<br/>CorrectiveActionClosed / Expired / Voided<br/>CriterionResultRevised"]
    end

    subgraph TestAdapters["Adaptadores de prueba (src/test)"]
        Memory["InMemoryCatalogue · InMemorySchemaRepository<br/>InMemoryInspectionRepository · InMemoryFindingRepository<br/>InMemoryCertificateRepository · InMemoryAuditTrail"]
        Runtime["TestClock · SequentialIds · FixedActor<br/>DispatchingEventPublisher"]
        Composition["FullSystem<br/>(composition root de integración)"]
    end

    CatalogueUC --> AssetPorts
    SchemaUC --> SchemaPorts
    InspectionUC --> InspectionPorts
    InspectionUC --> AssetDirectory & SchemaCatalog & FindingService
    FindingUC --> FindingPorts
    CertificationUC --> CertificatePort & ContextAssembler
    ReportsUC --> InspectionPorts & FindingPorts & CertificatePort

    AssetDirectory --> AssetPorts
    SchemaCatalog --> SchemaPorts
    FindingService --> FindingPorts & InfrastructurePorts
    ContextAssembler --> FindingPorts & CertificatePort & InfrastructurePorts
    Reactions --> CertificatePort & Audit
    Audit --> InfrastructurePorts

    CatalogueUC --> Catalogue
    SchemaUC --> Schema
    InspectionUC --> Inspection & Evaluation
    FindingUC --> Finding & Events
    CertificationUC --> Certificate
    Audit --> AuditModel

    Memory -. "implementa" .-> AssetPorts & SchemaPorts & InspectionPorts & FindingPorts & CertificatePort
    Runtime -. "implementa" .-> InfrastructurePorts
    Runtime -. "despacha" .-> Reactions
    Composition -. "ensambla" .-> Application & Services & Memory & Runtime
```

## Modelo de dominio principal

Las flechas con rombo indican composición: la entidad de origen administra el ciclo de vida de la entidad o valor de destino. Las relaciones etiquetadas `id` son referencias por identificador, no una asociación de objetos en memoria.

```mermaid
classDiagram
    direction LR

    class InspectionSchema {
        +SchemaId id
        +Set~AssetType~ applicableTypes
        +SchemaDraft draft
        +List~SchemaVersion~ versions
    }
    class SchemaDraft
    class SchemaVersion {
        +SchemaVersionId id
        +List~Section~ sections
    }
    class Section
    class Criterion {
        +CriterionId id
        +Severity severity
        +EvaluationRule rule
        +List~EvidenceRequirement~ evidenceRequirements
    }
    class EvaluationRule {
        <<interface>>
        +evaluate(Answer) RuleOutcome
    }
    class YesNoRule
    class NumericRangeRule
    class MappedOptionsRule

    class Asset {
        +AssetId id
        +PartyId responsibleId
    }
    class Party
    class Inspection {
        +InspectionId id
        +AssetId assetId
        +SchemaVersionId frozenSchemaVersionId
        +InspectionStatus status
        +start(...)
        +close(...)
        +rectify(...)
    }
    class CriterionRecord {
        +CriterionId criterionId
        +Answer answer
        +List~EvidenceRecord~ evidence
        +List~CriterionEvaluation~ evaluations
    }
    class Rectification
    class CriterionEvaluator {
        +evaluate(Criterion, CriterionRecord, SchemaVersion, Instant)
    }
    class CriterionEvaluation

    class Finding {
        +FindingId id
        +InspectionId inspectionId
        +CriterionId criterionId
        +CorrectiveAction correctiveAction
    }
    class CorrectiveAction {
        +CorrectiveActionStatus status
        +plan(...)
        +reportExecution(...)
        +verify(...)
        +expireIfOverdue(...)
    }
    class Certificate {
        +CertificateId id
        +AssetId assetId
        +InspectionId backingInspectionId
        +CertificateStatus status
        +suspend(...)
        +resolveCauses(...)
        +expireIfDue(...)
    }
    class CertificateIssuancePolicy {
        +blockersFor(CertificationContext)
    }

    InspectionSchema *-- SchemaDraft
    InspectionSchema *-- "0..*" SchemaVersion
    SchemaVersion *-- "1..*" Section
    Section *-- "1..*" Criterion
    Criterion *-- EvaluationRule
    EvaluationRule <|.. YesNoRule
    EvaluationRule <|.. NumericRangeRule
    EvaluationRule <|.. MappedOptionsRule

    Inspection --> Asset : assetId
    Inspection --> SchemaVersion : frozenSchemaVersionId
    Asset --> Party : responsibleId
    Inspection *-- "1..*" CriterionRecord
    Inspection *-- "0..*" Rectification
    CriterionEvaluator ..> Criterion
    CriterionEvaluator ..> CriterionRecord
    CriterionEvaluator ..> CriterionEvaluation : crea

    Finding --> Inspection : inspectionId
    Finding --> Criterion : criterionId
    Finding *-- CorrectiveAction
    Certificate --> Asset : assetId
    Certificate --> Inspection : backingInspectionId
    CertificateIssuancePolicy ..> Finding : consulta bloqueos mediante contexto
```

## Recorrido: cierre y rectificación de una inspección

```mermaid
sequenceDiagram
    participant Close as CloseInspection
    participant Repo as InspectionRepository
    participant Schema as SchemaCatalog
    participant Eval as CriterionEvaluator
    participant I as Inspection
    participant Registry as FindingRegistry / FindingService
    participant FRepo as FindingRepository
    participant Audit as AuditRecorder

    Close->>Repo: require(inspectionId)
    Close->>Schema: requireVersion(schema congelada)
    loop por cada Criterion de la versión
        Close->>Eval: evaluate(criterion, record, version, now)
        Eval-->>Close: CriterionEvaluation
    end
    Close->>I: close(now, evaluations)
    Close->>Repo: save(inspection)
    Close->>Registry: recordClosureNonConformities(...)
    Registry->>FRepo: consulta y guarda Finding no aprobado
    Close->>Audit: INSPECTION_CLOSED

    Note over I,Registry: Una rectificación recalcula los criterios afectados.
    Note over Registry: Puede crear, revisar o dejar sin efecto un Finding.
```

## Recorrido: eventos que afectan certificados

```mermaid
sequenceDiagram
    participant Action as Verify / Expire / Rectify use cases
    participant Publisher as DomainEventPublisher
    participant Reactions as CertificationReactions
    participant Certificates as CertificateRepository
    participant Certificate as Certificate
    participant Audit as AuditRecorder

    Action->>Publisher: publica evento de dominio
    Publisher->>Reactions: handle(event)
    Reactions->>Certificates: findByBackingInspection(inspectionId)
    alt acción vencida o rechazo revelado por rectificación
        Reactions->>Certificate: suspend(cause, at)
        Reactions->>Certificates: save(certificate)
        Reactions->>Audit: CERTIFICATE_SUSPENDED
    else acción verificada o obligación anulada
        Reactions->>Certificate: resolveCauses(...)
        Reactions->>Certificates: save(certificate)
        opt todas las causas fueron resueltas
            Reactions->>Audit: CERTIFICATE_REACTIVATED
        end
    end
```

## Lectura rápida

- `FullSystem` es el punto de composición que se usa en las pruebas de integración; no hay, por ahora, adaptadores de producción ni un punto de entrada web/CLI.
- `Inspection` conserva una versión congelada del esquema al iniciarse. De ese modo, una inspección se evalúa contra el esquema que estaba vigente en ese momento.
- `FindingService` conecta el cierre o la rectificación con los hallazgos y sus acciones correctivas.
- `CertificationContextAssembler` reúne el estado de inspección, hallazgos y certificados; `CertificateIssuancePolicy` decide si existen bloqueos para emitir o renovar.
- La auditoría es transversal a los casos de uso y `CertificationReactions` reacciona a eventos para suspender o reactivar certificados.
