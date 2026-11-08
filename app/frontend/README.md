# CertiFlow — front end

React 19 + TypeScript + Vite. La interfaz está en castellano y habla con la API REST (`/api`).

## Cómo se usa

- **Todo junto (lo normal):** `mvn verify` en la raíz instala Node, corre los tests del front, lo compila y lo deja en
  `app/target/classes/static`. Al iniciar la aplicación Spring Boot (`java -jar app/target/certiflow-app-*-exec.jar`)
  la interfaz queda en <http://localhost:8080/>. Con `-Dskip.frontend=true` se omite todo esto.
- **Desarrollo del front:** con la API corriendo en el puerto 8080, `npm install` y `npm run dev` (puerto 5173, con
  recarga en vivo; `/api` se reenvía a 8080).
- **Tests:** `npm test` (Vitest: cliente HTTP, conversión de formularios de reglas, formatos y etiquetas).

## Estructura

```
src/lib/        api.ts (único lugar que hace HTTP), types.ts (contrato), labels.ts, format.ts, rules.ts, ruleForm.ts, hooks.ts,
                assistant.ts (origen de una no conformidad, cuerpo de una edición, errores del asistente)
src/components/ ui.tsx (piezas comunes), Party.tsx (quién actúa), CriterionCard, CertificationPanel, SectionEditor, Blockers, Tree,
                AssistantPanel (asistente de inspección)
src/pages/      una por pantalla (Dashboard, Parties, Assets, Schemas, Inspections, Findings, Certificates, Audit, Processes)
```

## Asistente de inspección

En una inspección en curso, el panel **Asistente de inspección** (`AssistantPanel`) pide sugerencias de hallazgos a
partir de las notas y la evidencia. Cada sugerencia se **acepta**, se **edita** (con una nota obligatoria que explica
por qué) o se **descarta** (con un motivo opcional); nada se aplica sin esa decisión, y el panel nunca cierra la
inspección ni emite certificados. Debajo queda el historial de consultas con el modelo y la versión de prompt que
respondió, y lo que el sistema descartó de la respuesta.

Cada criterio lista sus no conformidades observadas con su origen: «Sugerida por IA», «Sugerida por IA, editada» o
«Del inspector». Mientras la inspección está en curso se pueden registrar a mano y quitar. El acta muestra el mismo
origen después del cierre.

El panel consulta `GET /api/meta/assistant`: si el asistente está apagado en el servidor, muestra cómo encenderlo en
lugar del botón.

Las rutas usan `#` (`/#/activos`): así el servidor no necesita reenviar rutas del front a `index.html`.
La persona con la que se actúa se elige arriba a la izquierda y viaja en el header `X-Actor-Id`.
