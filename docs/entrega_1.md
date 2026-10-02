# Extracted source: OFF-001

## Page 1

Trabajo Integrador - Entrega 1
Plataforma de inspección, habilitación y cer-
tificación de activos
Introducción
El trabajo integrador se desarrolla en grupos de cinco integrantes sobre esta consigna. La comple-
jidad de negocio es suficiente para que la calidad del modelado, la separación de responsabilidades
y la extensibilidad resulten determinantes: no alcanza con que el sistema funcione.
Enunciado
Una entidad de certificación inspecciona instalaciones, laboratorios, equipos y otros activos técni-
cos.Cadatipodeactivoestásujetoaunesquemadeinspeccióncompuestoporcriterios,evidencias
obligatorias, niveles de severidad y reglas de aprobación.
Los esquemas cambian con el tiempo. Una inspección debe conservar las reglas que estaban
vigentes cuando fue iniciada, aun cuando posteriormente se publique una versión nueva. Los
inspectores registran mediciones, respuestas, fotografías, documentos y observaciones. A partir
de esos datos, el sistema determina hallazgos, acciones correctivas y la posibilidad de emitir un
certificado.
El equipo deberá desarrollar una aplicación para administrar activos, definir esquemas versiona-
dos, ejecutar inspecciones, registrar evidencias, evaluar resultados y gestionar el ciclo de vida de
certificados y acciones correctivas.
Requisitos funcionales obligatorios
Capacidad Comportamiento esperado
Catálogo de activos Dar de alta y consulta de activos con tipo, ubicación,
responsable y características
Esquemas de inspección Creación de plantillas formadas por secciones y criterios
Versionado Publicación de nuevas versiones sin modificar inspecciones
anteriores
Asignación de inspecciones Designación de inspector, fecha prevista y alcance
Ejecución Registro progresivo de respuestas, mediciones, evidencias y
observaciones
Evaluación Determinación automática de criterios aprobados, observados
o rechazados
Hallazgos Creación de no conformidades con severidad, evidencia y
responsable
Acciones correctivas Planificación, vencimiento, verificación y cierre
1

## Page 2

Trabajo Integrador - Entrega 1
Capacidad Comportamiento esperado
Certificación Emisión, suspensión, renovación y vencimiento de certificados
Auditoría Historial de modificaciones, decisiones y transiciones
Informes Acta de inspección, resumen de hallazgos y certificado
Una inspección no puede alterarse libremente luego de cerrarse. Las correcciones posteriores deben
realizarse mediante una rectificación auditable.
Entrega 1 - Módulo de dominio
Objetivo:demostrar mediante código que el equipo comprendió el dominio y estableció un diseño
extensible antes de construir la aplicación completa.
El entregable central es elmódulo de dominio: un proyecto Java compilable y probado que
contiene los modelos del negocio, los contratos y los casos de uso más relevantes para conocer el
dominio (implementaciones concretas).
Deben existir tests con los casos más relevantes del negocio únicamente.
Entregables obligatorios
Entregable Contenido mínimo
Repositorio URL del repositorio de GitHub y hash del último commit
que corresponde a la entrega
Código del dominio modelos, servicios, casos de uso, interfaces
Tests Unitarios y de Integración, con cobertura significativa de los
casos de uso
Decisiones de diseño ArchivoDESIGN.mden la raíz del repositorio que enumera las
decisiones de diseño principales: qué patrón o principio se
aplicó, dónde y por qué; qué alternativas se descartaron; y
qué patrones se decidió no aplicar, con sus consecuencias
Qué no se exige en esta entrega
No se exige API REST, persistencia real, frontend, seguridad ni despliegue. Únicamente módulo
del dominio
2
