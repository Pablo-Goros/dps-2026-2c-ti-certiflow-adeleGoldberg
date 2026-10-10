# Validación de integración continua

El workflow `.github/workflows/ci.yml` ejecuta `mvn --batch-mode --no-transfer-progress verify`
con Java 25 en los pull requests y en los pushes a `main`.

## Pruebas ejecutadas

| Parte | Unitarias | Integración | Alcance |
| --- | ---: | ---: | --- |
| core | 237 | 125 | Reglas de negocio, inspecciones, certificación, jurisdicciones, auditoría y arquitectura. |
| infrastructure | 16 | 35 | Serialización, persistencia H2, transacciones, restricciones, outbox y notificaciones. |
| app | 36 | 72 | API HTTP, errores, actores, concurrencia, procesos y recursos del frontend. |
| frontend | 20 | — | Cliente HTTP, formularios de reglas, números, fechas y etiquetas. |

Los conteos corresponden a la ejecución local del 10 de octubre de 2026 con JDK 26:
521 pruebas Java y 20 de Vitest, sin fallos ni omisiones. La validación remota usa JDK 25.

Surefire ejecuta las clases `*Test.java`; Failsafe ejecuta `*IT.java` y comprueba sus
resultados en `verify`. El plugin de frontend instala Node, ejecuta `npm ci`, Vitest,
TypeScript y Vite. El build también genera el JAR ejecutable.

Los reportes Java están en `*/target/surefire-reports` y `*/target/failsafe-reports`.
Los informes de cobertura están en `*/target/site/jacoco/index.html`. En GitHub Actions
se pueden descargar en el artefacto `reports`; los resultados de Vitest aparecen en el log.

## Comprobación de la protección de main

1. Crear una rama temporal basada en `main` y agregar una prueba JUnit deliberadamente fallida.
2. Abrir un PR hacia `main` y esperar a que termine `Build and test`.
3. Comprobar que el fallo corresponde a la prueba temporal y que el PR está bloqueado.
4. Eliminar la prueba temporal y subir otro commit.
5. Comprobar que el workflow completo termina correctamente y cerrar el PR sin integrarlo.

La protección debe estar activa, apuntar a `main`, exigir PR y el chequeo `Build and test`,
y no conceder bypass. No alcanza con que el workflow falle: su chequeo debe ser obligatorio.

### Evidencia remota del 10 de octubre de 2026

- [PR temporal #3](https://github.com/Pablo-Goros/dps-2026-2c-ti-certiflow-adeleGoldberg/pull/3).
- [Ejecución con fallo intencional](https://github.com/Pablo-Goros/dps-2026-2c-ti-certiflow-adeleGoldberg/actions/runs/38073814666).
- Surefire ejecutó 238 pruebas del núcleo: 237 correctas y un fallo en
  `CiFailureTest.intentionalFailureBlocksThePullRequest`. Maven terminó con `BUILD FAILURE`.
- El job `Build and test` terminó con `conclusion: failure` y el PR informó
  `mergeable_state: blocked`, aunque no tenía conflictos (`mergeable: true`).
- El ruleset `Main Protection` está activo, apunta a `refs/heads/main`, exige PR y el
  chequeo de GitHub Actions `Build and test`. No tiene actores con permiso de bypass.
- La opción de exigir actualización con `main` está desactivada. Esto no impide el
  bloqueo por fallos, pero conviene activarla para validar también los cambios recientes de la rama base.
- La prueba intencional se eliminó antes de la ejecución de recuperación; no debe incorporarse a `main`.

## Revisión de cobertura funcional

Las pruebas actuales incluyen rollback completo, rechazo de escrituras obsoletas,
restricciones de unicidad, reintentos de eventos, emisión concurrente sin duplicados,
cierre idempotente de inspecciones y contratos de error de la API.

Las principales ampliaciones pendientes son pruebas de interacción de componentes React
y recorridos completos en navegador. `FrontendIT` comprueba que Spring sirva el frontend
y sus recursos; no ejecuta interacciones de usuario. Las pruebas de arquitectura inspeccionan
el texto de los imports, por lo que su alcance es limitado a esas dependencias explícitas.

JaCoCo genera informes de cobertura, pero los POM actuales no imponen un umbral mínimo.
