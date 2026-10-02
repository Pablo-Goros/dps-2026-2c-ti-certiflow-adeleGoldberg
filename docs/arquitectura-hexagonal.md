# Arquitectura hexagonal

Este proyecto queda organizado como una arquitectura hexagonal pura: el dominio no conoce detalles externos, la capa de aplicacion define los casos de uso y los puertos, y los adaptadores quedan fuera del nucleo.

## Capas

### Dominio

Ubicacion: `src/main/java/ar/edu/itba/dps/certification/domain`

Contiene el modelo de negocio y las reglas propias de certificacion: activos, inspecciones, hallazgos, esquemas, certificados, reglas de evaluacion, eventos de dominio y objetos de valor. No declara puertos ni importa clases de `application` o `adapter`.

Ejemplos:

- `domain.inspection.Inspection` concentra el ciclo de vida de una inspeccion.
- `domain.schema.SchemaApplicability` valida reglas de aplicabilidad usando datos recibidos desde la aplicacion, sin consultar repositorios.
- `domain.certification.Certificate` expone operaciones de negocio explicitas como emitir, suspender, resolver suspensiones y expirar.

### Aplicacion

Ubicacion: `src/main/java/ar/edu/itba/dps/certification/application`

Contiene los casos de uso y los puertos que necesita el sistema para hablar con el exterior. Esta capa orquesta entidades de dominio, aplica transacciones logicas y delega persistencia, reloj, identidad, auditoria y publicacion de eventos a interfaces.

Ejemplos de casos de uso:

- `application.inspection.usecase.StartInspection`
- `application.certification.usecase.IssueCertificate`
- `application.schema.usecase.CreateSchema`
- `application.finding.usecase.PlanCorrectiveAction`

Ejemplos de puertos secundarios:

- `application.catalogue.port.AssetRepository`
- `application.inspection.port.InspectionRepository`
- `application.schema.port.SchemaRepository`
- `application.audit.port.AuditTrail`
- `application.shared.port.Clock`
- `application.shared.port.IdGenerator`

Tambien viven en aplicacion los servicios que coordinan varios puertos:

- `application.certification.CertificateFactory`
- `application.certification.CertificationReactions`
- `application.inspection.RectificationConsequences`

Esas clases no pertenecen al dominio porque consultan repositorios, queries u otros puertos. Su responsabilidad es coordinar informacion externa y luego invocar reglas del dominio.

Los servicios de dominio puros siguen en `domain` cuando no necesitan puertos. Por ejemplo,
`domain.certification.CertificateLifecycle` decide suspensiones y reactivaciones sobre un
`Certificate` ya cargado, mientras que `CertificationReactions` se ocupa de buscarlo,
guardarlo y auditar el cambio.

### Adaptadores

Ubicacion: `src/main/java/ar/edu/itba/dps/certification/adapter`

Contiene implementaciones concretas de puertos. Estos componentes pueden depender de aplicacion y dominio, porque estan fuera del hexagono y conectan detalles con el nucleo.

Adaptadores actuales:

- `adapter.catalogue.CatalogueAssetDirectory`: implementa `AssetDirectory` usando `AssetRepository`.
- `adapter.finding.RepositoryFindingQuery`: implementa `FindingQuery` usando `FindingRepository`.
- `adapter.schema.PublishedSchemaCatalog`: implementa `SchemaCatalog` usando `SchemaRepository`.

Los adaptadores en memoria usados por tests viven en `src/test/java/.../support` y cumplen el mismo rol externo para escenarios de integracion.

## Regla de dependencias

La direccion de dependencias es siempre hacia adentro:

```text
adapter -> application -> domain
```

Reglas aplicadas:

- `domain` no importa `application`.
- `domain` no importa `adapter`.
- `domain` no declara paquetes `.port`.
- `application` puede importar `domain`.
- `application` declara puertos y casos de uso.
- `application` no importa `adapter`.
- `adapter` puede importar `application` y `domain` para implementar puertos.

Con esto, el dominio se puede probar y evolucionar sin persistencia, frameworks, reloj real, usuario autenticado ni mecanismo concreto de eventos.

## Puertos de entrada y salida

Los puertos de entrada son los casos de uso publicos de aplicacion. Cada caso representa una intencion del usuario o del sistema, por ejemplo registrar un activo, iniciar una inspeccion, cerrar una inspeccion, emitir un certificado o planificar una accion correctiva.

Los puertos de salida son interfaces en `application.*.port`. Permiten que la aplicacion pida capacidades externas sin conocer la implementacion:

- Persistencia: repositorios de activos, partes, inspecciones, hallazgos, esquemas y certificados.
- Consultas: queries como `InspectionQuery`, `FindingQuery` y catalogos publicados.
- Tiempo e identidad: `Clock`, `ActorProvider`, `IdGenerator`.
- Auditoria y eventos: `AuditTrail`, `DomainEventPublisher`, `DomainEventHandler`.

## Independencia del dominio

Antes, algunos servicios del dominio dependian de repositorios o puertos. Eso hacia que el dominio conociera detalles de obtencion de datos. La arquitectura final evita ese acoplamiento:

- `SchemaApplicability` ya no recibe `SchemaRepository`; la aplicacion consulta el repositorio y le pasa al dominio el estado necesario.
- `CertificateFactory` se movio a `application.certification` porque coordina inspecciones, hallazgos, certificados, reloj e IDs.
- `CertificateLifecycle` quedo en `domain.certification` como servicio puro: no conoce repositorios y solo decide el cambio sobre un certificado recibido.
- `CertificationReactions` quedo en `application.certification` porque busca certificados por puerto, delega en el dominio, guarda el resultado y audita.
- `RectificationConsequences` se movio a `application.inspection` porque coordina inspecciones, hallazgos y catalogo de activos.

El dominio conserva las reglas: solo dejo de conocer como se recupera o persiste la informacion.

## Proteccion arquitectonica

Se agrego `ArchitectureBoundaryTest`, una prueba de frontera que verifica:

- que `domain` no importe `application` ni `adapter`;
- que `application` no importe `adapter`;
- que no existan paquetes de puertos dentro de `domain`.

Esto evita que futuros cambios vuelvan a degradar la arquitectura a una variante pseudo hexagonal.
