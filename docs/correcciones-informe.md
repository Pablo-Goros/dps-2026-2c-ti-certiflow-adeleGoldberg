# Correcciones del informe CertiFlow

El informe original se conserva como antecedente. Las correcciones se implementan en el código y sus decisiones se documentan en DESIGN.md y PRD.md.

| Punto | Corrección |
|---|---|
| 1. Evaluación de inspecciones | La raíz conserva la versión congelada, valida respuestas y evidencias, calcula el cierre y reevalúa las rectificaciones sin aceptar resultados externos. |
| 2. Emisión y renovación | Constructor de certificado de paquete; fábrica del dominio con política obligatoria, inspección nueva posterior a la emisión anterior y renovación repetida idempotente. |
| 3. Plazos de acciones | Plazo inclusivo de 30 días para planificar desde la creación; rechazo de planes anteriores a hoy; la planificación tardía conserva el incumplimiento y puede suspender el certificado. |
| 4. Autoría y roles | Inspector copiado al hallazgo y su acción; prohibición de ejecutar y verificar la misma corrección; usuario obtenido de ActorProvider y auditado con la misma identidad. |
| 5. Borradores | Mutadores del borrador de paquete y operaciones públicas de edición en la raíz, usadas por los casos de uso auditados. |
| 6. Aplicabilidad | Regla única en SchemaApplicability; operaciones inexistentes o duplicadas rechazadas; transferencia a otro esquema publicado para evitar dejar tipos sin cobertura. |
| 7. Ubicación de reglas y eventos | Puertos en domain; bloqueos, consecuencias de rectificación y ciclo de certificados decididos en el dominio; persistencia y auditoría antes de publicación; eventos pendientes reintentables. |

Las preguntas abiertas se resolvieron conservando la auditoría genérica con motivos obligatorios no vacíos, comandos de activo sin devolver FieldChange, atributos predefinidos por tipo y InvalidArgumentException para argumentos malformados. El enum de tipos es un catálogo inicial cerrado y requiere recompilación para ampliarlo.

ReviewCorrectionsIT cubre las llamadas directas al agregado, suplantación, renovación antigua, acciones sin plan, planes vencidos, transferencias de aplicabilidad y publicación fallida con reintento. La suite existente conserva los flujos de cierre, rectificación, certificación, auditoría e informes.

Los adaptadores de esta entrega siguen en memoria. La transferencia entre esquemas y la persistencia de agregados, auditoría y eventos requieren transacciones y almacenamiento durable al incorporar infraestructura real.

Validación final: mvn verify completado con 153 pruebas, sin fallos, errores ni pruebas omitidas. Se generó el reporte JaCoCo y git diff --check no señaló problemas de espacios.
