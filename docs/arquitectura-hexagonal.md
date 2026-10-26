# Arquitectura hexagonal

El proyecto sigue el esquema de la materia: el dominio define los contratos de entrada
y salida; aplicación implementa los casos de uso; los adaptadores conectan HTTP,
persistencia, tiempo, identidad y servicios externos. El núcleo no depende de Spring,
JDBC, Jackson ni de los módulos externos.

## Dominio y puertos

Ubicación: `core/src/main/java/ar/edu/itba/dps/certification/domain`.

Las raíces `Inspection`, `Finding`, `InspectionSchema` y `Certificate` protegen sus
invariantes. Los evaluadores y políticas conservan las reglas del negocio. En los
paquetes `domain.<área>.port` están los puertos de salida, por ejemplo:

- `catalogue.port.AssetRepository`, `PartyRepository` y `AssetDirectory`.
- `inspection.port.InspectionRepository`, `InspectionQuery` y `FindingRegistry`.
- `schema.port.SchemaRepository` y `SchemaCatalog`.
- `certification.port.CertificateRepository` y `CertificationPolicyRegistry`.
- `finding.port.FindingRepository`, `FindingQuery` y `audit.port.AuditTrail`.
- `shared.port.Clock`, `ActorProvider`, `IdGenerator`, `Transactions` y los contratos de eventos.
- `notification.port.NotificationSender`; su mensaje y excepción pertenecen al dominio.

Los contratos de entrada están en `domain.<área>.port.inbound`. Cada caso de uso de
negocio tiene una interfaz: `IssueCertificate`, `StartInspection`, `CloseInspection`,
`WithdrawScheduledSchemaVersion`, las consultas `Browse*` y los demás. Sus firmas usan
tipos del dominio y del JDK, sin referencias a implementaciones de aplicación.

El dominio define las interfaces, pero los agregados no consultan repositorios ni
obtienen por sí mismos usuario o reloj. Reciben los hechos y valores necesarios para
evaluar sus reglas. `InspectionSummary` y `NonConformity` son valores de los contratos,
no DTO de Spring o de JDBC.

## Aplicación

Ubicación: `core/src/main/java/ar/edu/itba/dps/certification/application`.

Las clases de `application.<área>.usecase` implementan las interfaces de entrada y
reciben por constructor sus dependencias, incluida `Transactions`. Sus operaciones
públicas abren la unidad de trabajo antes de cargar, modificar, guardar y auditar.
No requieren que el llamador sea un controller ni use anotaciones de Spring.

Los servicios internos coordinan hechos, sin reemplazar las reglas de las raíces:

- `CertificateFactory`: reúne inspecciones, hallazgos y política activa para emitir o renovar.
- `CertificationReactions`: carga hechos y delega cumplimiento a `CertificateLifecycle`.
- `RectificationConsequences`: coordina las consecuencias auditables sobre hallazgos.
- `FindingService`: implementa `FindingRegistry` y crea o revisa hallazgos.
- `MaintenanceService`: implementa `RunMaintenance` y coordina barridos y entregas
  independientes; la unidad atómica es cada barrido, no todo el lote administrativo.

La emisión exige una `CertificationAssessment` creada por evaluación y compatible con
los hechos actuales. El constructor de `Certificate` exige esa prueba y calcula su
vigencia desde la política evaluada. `DeriveGlobalCertificate` consulta por activo los
últimos parciales y delega a `GlobalCertificatePolicy`; admite inspecciones y esquemas
diferentes e informa la procedencia individual.

`InspectionSchema` controla fechas estrictamente crecientes entre publicaciones no
retiradas y el retiro con motivo de una versión todavía futura. El caso de uso guarda
el retiro y su auditoría dentro de la misma unidad de trabajo.

## Adaptadores y composición

- `core/adapter` contiene adaptadores de catálogo, queries y políticas que implementan
  interfaces del dominio.
- `infrastructure` implementa repositorios, auditoría, transacciones JDBC, outbox y
  notificaciones. No importa `application`, `app` ni frameworks web.
- `app/web` consume interfaces de dominio y convierte solicitudes y respuestas mediante
  DTO. No conoce implementaciones de aplicación, repositorios ni transacciones.
- `app/jobs` es el adaptador programado: llama a `RunMaintenance` y a operaciones de
  entrega. No abre unidades de trabajo de negocio.
- `app/config` compone las implementaciones de aplicación con los adaptadores concretos.
  Este es el lugar que conoce a ambas capas.

Las dependencias de compilación apuntan a los contratos:

```text
web / jobs        -> domain (interfaces)
application       -> domain
adapter / JDBC    -> domain
config            -> application + domain + infrastructure
```

## Transacciones

`domain.shared.port.Transactions` define la unidad de trabajo; `JdbcTransactions` la
implementa. Los 43 casos de uso de negocio tienen una dependencia explícita de ese
puerto y ejecutan sus operaciones dentro de él. Una llamada directa por la interfaz
tiene la misma atomicidad que la llamada HTTP. Las llamadas anidadas se unen a la
unidad en curso; no confirman por separado.

El cambio de agregados, auditoría y encolado de eventos se confirman juntos. Si falla
alguno, la unidad revierte. La entrega del outbox mantiene su propia transacción de
infraestructura para reclamar y confirmar cada evento; eso no sustituye la transacción
del caso de uso. Los avisos externos conservan su semántica de mejor esfuerzo.

Los tests unitarios en memoria usan `DirectTransactions` de forma explícita; no simula
rollback JDBC. Las pruebas de integración usan `JdbcTransactions` y una base H2 real.
`UseCaseAtomicityIT` comprueba que, sin una transacción abierta por el llamador, una
auditoría fallida revierta el guardado y una llamada posterior pueda confirmar normalmente.

## Fecha de negocio

`Clock.today(Instant at)` convierte un instante a la fecha de la zona configurada;
`today()` delega en `today(now())`. El adaptador productivo usa Buenos Aires por defecto.
Los instantes de los hechos siguen siendo `Instant`; no se confunden con fechas límite.

`FindingService` entrega a `Finding` la fecha de negocio al crearlo y al crear acciones
de reemplazo. Planificación, verificación, evaluación y barrido usan el mismo contrato
y el instante capturado para la operación. No se deriva el comienzo del plazo desde
UTC para después compararlo con una fecha local. La aritmética de vigencia de
certificados en UTC es una regla separada de sus políticas, no el calendario del plazo
de planificación.

`BusinessCalendarIT` verifica cierres a las 10:00 y 22:30 de un mismo 5 de marzo en
Buenos Aires: ambos tienen plazo hasta el 4 de abril inclusive y vencen juntos el
5 de abril. `SystemClockTest` comprueba la conversión del adaptador real cuando UTC
ya cambió de día.

## Protección arquitectónica

- `ArchitectureBoundaryTest`: dominio independiente; puertos en dominio; cada caso de
  uso implementa su entrada y requiere `Transactions` por constructor.
- `AppBoundaryTest`: dependencias de los controllers únicamente hacia interfaces del
  dominio, ausencia de apertura de transacciones en web/jobs y conocimiento de JDBC
  limitado a configuración.
- `InfrastructureBoundaryTest`: adaptadores sin dependencias de aplicación o web.
- `UseCaseTransactionTest`: repositorio y auditoría se invocan dentro de la unidad
  incluso al entrar directamente por el puerto.

Las pruebas de negocio, repositorios, API y frontend completan estas comprobaciones.
