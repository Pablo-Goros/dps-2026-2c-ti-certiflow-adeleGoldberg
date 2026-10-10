# CertiFlow

Sistema de certificación de activos (Entrega 2): inspecciones con esquemas versionados, hallazgos y acciones
correctivas, y certificados globales o parciales por subsistema, con políticas por jurisdicción.
Spring Boot 4 + Java 25 en el servidor, React + TypeScript en el front, H2 embebida como base.

## Requisitos

- **JDK 25** y **Maven 3.9+**.
- Red la primera vez: Maven descarga Node (para el front) y las dependencias. Después queda en caché de `~/.m2`.

## Compilar y probar

```
mvn verify
```

Compila los tres módulos, corre las pruebas unitarias (Surefire) y de integración (Failsafe: repositorios y API con
una base H2 real), los tests del front y arma el jar ejecutable con el front incluido. Los reportes quedan en
`*/target/surefire-reports`, `*/target/failsafe-reports` y `*/target/site/jacoco`.
`-Dskip.frontend=true` omite todo lo del front (más rápido si solo tocás el back).

## Ejecutar

```
java -jar app/target/certiflow-app-1.0.0-SNAPSHOT-exec.jar
```

- Interfaz: <http://localhost:8080/>
- API: <http://localhost:8080/api> (contrato en [`docs/API.md`](docs/API.md))
- La base es un archivo en `./data/certiflow` (se crea sola y está en `.gitignore`). Para empezar de cero, borrá `data/`.
- Otra base o puerto: variables de entorno estándar de Spring, por ejemplo `SPRING_DATASOURCE_URL` o `SERVER_PORT`.
- Aviso de acciones correctivas vencidas: por defecto va al log; con `certiflow.notifications.webhook-url=<url>`
  se envía como POST JSON a esa dirección.

### Primer recorrido

1. Elegir quién actúa arriba a la izquierda (si no hay nadie, crear una persona en **Personas**; la acción se
   registra en la auditoría con ese actor).
2. Registrar un activo y elegir su jurisdicción (`REFERENCE`, `AR-BA` o `AR-CBA`).
3. Crear un esquema de inspección, armar un borrador con secciones y criterios, y publicarlo (inmediato o con
   fecha de vigencia futura: F2).
4. Crear la inspección, responder los criterios y cerrarla.
5. En la inspección, ver la elegibilidad y emitir el certificado global o por subsistema (F1). Según la jurisdicción
   la misma inspección puede emitirse, quedar condicional o bloquearse (F3).
6. En **Procesos** se pueden ejecutar a demanda los vencimientos y el despacho de eventos.

## Estructura

| Módulo | Qué contiene |
|---|---|
| `core` (`certification-domain`) | Dominio y casos de uso. No conoce Spring ni la base. |
| `infrastructure` | Persistencia JDBC + Flyway sobre H2, outbox de eventos, notificaciones. |
| `app` | Spring Boot: composición, controladores REST, tareas programadas y el front (`app/frontend`). |

La regla de dependencias es `app → infrastructure → core`, y dentro del núcleo `adapter → application → domain`;
lo verifican pruebas de arquitectura en cada módulo.

## Documentación

- [`DESIGN.md`](DESIGN.md): decisiones de diseño, clases agregadas/modificadas por funcionalidad (F1, F2, F3),
  refactorizaciones y deuda técnica deliberada.
- [`docs/API.md`](docs/API.md): endpoints y contrato de errores.
- [`docs/arquitectura-hexagonal.md`](docs/arquitectura-hexagonal.md): arquitectura.
- [`app/frontend/README.md`](app/frontend/README.md): desarrollo del front.

## Integración continua

`.github/workflows/ci.yml` corre `mvn verify` con JDK 25 en cada PR y en cada push a `main`. Para que un PR con
tests rotos no pueda integrarse, en GitHub hay que exigir el chequeo **Build and test** en la protección de la rama
principal (Settings → Branches).
