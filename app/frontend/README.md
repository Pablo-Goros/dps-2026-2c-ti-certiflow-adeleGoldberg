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
src/lib/        api.ts (único lugar que hace HTTP), types.ts (contrato), labels.ts, format.ts, rules.ts, ruleForm.ts, hooks.ts
src/components/ ui.tsx (piezas comunes), Party.tsx (quién actúa), CriterionCard, CertificationPanel, SectionEditor, Blockers, Tree
src/pages/      una por pantalla (Dashboard, Parties, Assets, Schemas, Inspections, Findings, Certificates, Audit, Processes)
```

Las rutas usan `#` (`/#/activos`): así el servidor no necesita reenviar rutas del front a `index.html`.
La persona con la que se actúa se elige arriba a la izquierda y viaja en el header `X-Actor-Id`.
