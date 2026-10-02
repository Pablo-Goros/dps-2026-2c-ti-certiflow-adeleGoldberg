# Entrega 2: requisitos nuevos

Este documento resume únicamente los requisitos que se agregan en la Entrega 2. Los requisitos funcionales generales ya definidos en [entrega_1.md](entrega_1.md) no se repiten.

## Funcionalidades nuevas

### F1. Certificaciones parciales por subsistema

Un activo puede dividirse en subsistemas, por ejemplo, instalación eléctrica, sistema de presión o seguridad edilicia. Una inspección debe poder aprobar algunos subsistemas y rechazar otros.

- El esquema de inspección puede asociar secciones o criterios con subsistemas.
- Una inspección puede producir certificados parciales por subsistema, cada uno con vigencia independiente.
- El certificado global se deriva de los certificados parciales mediante una política explícita.

### F2. Versiones con vigencia futura

Se puede publicar una nueva versión de un esquema con una fecha de entrada en vigencia futura.

- Hasta esa fecha, las inspecciones nuevas usan la versión vigente; desde esa fecha, usan la nueva versión.
- Una inspección conserva la versión con la que fue iniciada.
- El sistema permite consultar qué versión estaba vigente en una fecha determinada.

### F3. Políticas de certificación por jurisdicción

Cada jurisdicción puede definir su propia política de certificación, que establece qué severidades impiden emitir un certificado, qué plazos de vencimiento se aplican y si se permiten certificados condicionales.

- El mismo resultado de inspección puede producir decisiones distintas según la jurisdicción del activo.
- Incorporar una jurisdicción no requiere modificar el servicio central de certificación.
- Cada decisión registra la política aplicada.

## Requisitos no funcionales

### Arquitectura y aplicación

- Organizar el proyecto según una de estas arquitecturas: Clean Architecture, Domain-Driven Design (DDD) o Hexagonal.
- Exponer el backend mediante una API REST.
- Incluir en el frontend lo mínimo necesario para realizar las funcionalidades requeridas.
- Entregar una aplicación completa y ejecutable que implemente los requisitos funcionales generales y las funcionalidades F1, F2 y F3.
- Incluir el módulo de dominio, los casos de uso, la infraestructura (persistencia, integraciones y procesos) y la API REST, organizados según la arquitectura elegida.

### Pruebas e integración continua

- Incluir pruebas unitarias, parametrizadas, de repositorio y de API.
- Usar mocks para sustituir colaboradores en pruebas unitarias; las pruebas de repositorio y de API deben comprobar la persistencia y la API reales.
- Configurar la integración continua para compilar y ejecutar las pruebas, y bloquear la integración de pull requests que fallen.

## Documentación de diseño

Además de las decisiones de diseño ya requeridas en la Entrega 1, documentar para cada funcionalidad nueva:

- las clases agregadas y modificadas;
- las refactorizaciones realizadas;
- la deuda técnica que se decidió no resolver.

Las funcionalidades F1, F2 y F3 deben estar implementadas, probadas y documentadas.
