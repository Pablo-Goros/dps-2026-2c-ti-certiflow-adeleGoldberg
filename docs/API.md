# API REST de CertiFlow

Base: `http://localhost:8080/api`. Cuerpos y respuestas en JSON. Las operaciones que exigen un usuario
identificado leen el header **`X-Actor-Id`** con el id de una persona/organización ya registrada
(`POST /parties`). Sin el header la petición corre como "sistema" y los casos de uso que requieren un
usuario responden 401.

## Errores

Todos tienen la forma `{ "status", "code", "message", "details": [], "timestamp" }`.

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `INVALID_INPUT` / `MALFORMED_REQUEST` | dato inválido, JSON roto, enum desconocido, jurisdicción desconocida al registrar un activo, instante mal formado |
| 401 | `ACTOR_REQUIRED` | falta el actor o el id no existe |
| 404 | `NOT_FOUND` | el recurso o la ruta no existe (en `effective-version`: ninguna versión regía en esa fecha) |
| 405, 415… | `REQUEST_REFUSED` | método no permitido, tipo de contenido no soportado (los errores 4xx del framework conservan su estado) |
| 409 | `CONFLICT` | cambio concurrente o duplicado |
| 422 | `BUSINESS_RULE` | el dominio rechazó la operación |
| 422 | `SCHEMA_NOT_PUBLISHABLE` | borrador no publicable; `details` lista los motivos |
| 503 | `ASSISTANT_UNAVAILABLE` | el asistente no pudo responder y no se registró nada; `details` trae la categoría: `UNREACHABLE`, `REFUSED`, `INCOMPLETE`, `MALFORMED` o `MISCONFIGURED` |
| 500 | `INTERNAL_ERROR` | cualquier otra cosa |

## Reglas de evaluación de un criterio

Al crear una sección, cada criterio lleva una regla en `rule.type`:

- `YES_NO`: `yes` y `no`, cada uno un resultado `{code, result, severity, description}`
  (`result` es `APPROVED`, `OBSERVED` o `REJECTED`; `severity` va en `null` sólo si aprueba).
- `OPTIONS`: `options`, un mapa de nombre de opción a resultado.
- `NUMERIC_RANGE`: `unit`, `minimum`, `maximum`, `bands` (tramos contiguos que cubren el rango de
  punta a punta, cada uno con su resultado) y **`outOfRange`**, obligatorio: el resultado de una
  medición que cae fuera de `[minimum, maximum]`. El inspector puede registrar cualquier medición en
  esa unidad —el rango clasifica, no filtra— y publicar falla si `outOfRange` aprueba el criterio.

## Endpoints

| Método y ruta | Qué hace |
|---|---|
| `POST /parties`, `GET /parties`, `GET /parties/{id}` | personas y organizaciones |
| `POST /assets`, `GET /assets?type=&responsible=&name=`, `GET /assets/{id}` | activos |
| `PUT /assets/{id}/location`, `PUT /assets/{id}/responsible` | reubicar / cambiar responsable |
| `GET /meta/asset-types`, `GET /meta/jurisdictions` | datos para armar formularios |
| `POST /schemas`, `GET /schemas`, `GET /schemas/{id}`, `GET /schemas/{id}/versions/{n}` | esquemas de inspección |
| `GET /schemas/{id}/effective-version?at=<instante ISO-8601>` | qué versión regía (o regirá) en esa fecha (F2); sin `at` usa el instante actual; 404 si ninguna regía; 400 si `at` no es un instante |
| `POST` / `DELETE /schemas/{id}/draft` | abrir / descartar borrador |
| `POST /schemas/{id}/draft/sections`, `DELETE .../sections/{name}` | editar el borrador |
| `POST /schemas/{id}/publish` | publicar; `{"effectiveFrom": "<instante futuro>"}` opcional (F2) |
| `POST /schemas/{id}/versions/{n}/withdraw` | retirar una versión todavía futura, con `{"reason": "motivo obligatorio"}`; conserva publicación y contenido, agrega auditoría y libera su fecha (F2) |
| `POST /schemas/{id}/applicability`, `DELETE .../applicability/{assetType}`, `POST .../applicability/transfer` | a qué tipos de activo aplica |
| `POST /inspections`, `GET /inspections?assetId=&inspectorId=&status=`, `GET /inspections/{id}` | inspecciones |
| `PUT /inspections/{id}/assignment` | reasignar inspector y fecha |
| `POST /inspections/{id}/start`, `POST .../close` | iniciar (congela la versión del esquema) y cerrar |
| `PUT` / `DELETE /inspections/{id}/answers/{criterionId}` | responder / quitar respuesta |
| `POST /inspections/{id}/evidence`, `DELETE .../criteria/{c}/evidence/{e}` | evidencias |
| `POST /inspections/{id}/notes`, `PUT` / `DELETE .../notes/{noteId}` | notas |
| `POST /inspections/{id}/criteria/{criterionId}/observed-nonconformities`, `DELETE .../observed-nonconformities/{observationId}` | no conformidades observadas por el inspector (ver abajo) |
| `POST /inspections/{id}/consultations`, `GET /inspections/{id}/consultations` | asistente: pedir sugerencias e historial, la más nueva primero (ver abajo) |
| `POST /inspections/{id}/consultations/{consultationId}/suggestions/{suggestionId}/decision` | aceptar, editar o descartar una sugerencia |
| `POST /inspections/{id}/rectifications` | rectificar una inspección cerrada |
| `GET /inspections/{id}/act` | acta con valores originales y rectificados |
| `GET /findings?inspectionId=&assetId=&openActions=`, `GET /findings/{id}` | hallazgos |
| `POST /findings/{id}/plan` / `execution` / `verification` | acción correctiva: planificar (responsable), ejecutar (ejecutor), verificar (inspector) |
| `GET /inspections/{id}/findings-summary` | resumen de hallazgos |
| `POST /inspections/{id}/certificates` | emitir; `{"subsystem": "..."}` = parcial (F1), sin cuerpo = global |
| `POST /inspections/{id}/certificates/renewal` | renovar |
| `GET /inspections/{id}/eligibility?subsystem=` | si se puede emitir y qué lo bloquea |
| `GET /assets/{id}/global-derivation` | global actual a partir del último parcial de cada subsistema; pueden pertenecer a distintas inspecciones y esquemas (F1) |
| `GET /inspections/{id}/global-derivation` | acceso compatible al mismo global actual del activo de esa inspección |
| `GET /certificates?assetId=&inspectionId=&status=`, `GET /certificates/{id}`, `GET /certificates/{id}/report` | certificados |
| `GET /audit?type=&id=` | auditoría, completa o de un elemento |
| `POST /admin/jobs/run` | ejecuta ahora los barridos y devuelve `{expiredCertificates, expiredActions, deliveredEvents}` |
| `GET /admin/outbox?status=PENDING\|DONE\|DEAD` | eventos del outbox (otro `status` responde 400) |
| `POST /admin/outbox/retry-dead` | da nuevos intentos a los eventos `DEAD` y los entrega: `{revived, delivered}` |

Una emisión bloqueada responde **422** con `{"outcome": "BLOCKED", "assessment": {... "blockers": [...]}}`;
una emisión repetida responde 200 con `"outcome": "ALREADY_ISSUED"`.

La derivación responde `Derived` o `NotDerivable`. El global derivado no atribuye una
inspección ni una versión de esquema únicas al activo: `certificate.provenance` contiene,
por cada parcial, `certificateId`, `subsystem`, `backingInspectionId`, `schemaVersionId`,
`policy` y `mode`. La vigencia global es la intersección de las vigencias de esos parciales.
Se consulta el último certificado de cada parte, sin recurrir a uno anterior si ese último
está suspendido o vencido. El acceso por inspección devuelve el estado actual del activo,
no una reconstrucción histórica de esa inspección.

F2 admite varias versiones programadas con fechas estrictamente crecientes entre las
publicaciones no retiradas. Publicar con la misma fecha o antes de la última no retirada
responde 422 y conserva el borrador. Para una corrección urgente se retiran explícitamente
las futuras que bloquean la fecha. El retiro responde 200 con el esquema actualizado y
`withdrawals: [{number, withdrawnAt, reason}]`; no borra versiones ni reutiliza sus números.
Un motivo vacío responde 400; retirar una versión vigente o ya retirada responde 422.
La consulta por fecha omite las retiradas. El retiro exige usuario (`X-Actor-Id`) y se
guarda junto con su auditoría en la misma transacción.

## No conformidades observadas

El inspector registra en un criterio un incumplimiento que la regla no mide, por ejemplo un cable
pelado junto a una medición de aislación correcta. La respuesta del criterio no cambia.

- `POST .../criteria/{criterionId}/observed-nonconformities` con
  `{"result": "REJECTED", "severity": "CRITICAL", "description": "...", "noteId": "opcional"}` responde
  201 con `{id, criterionId, result, severity, description, noteId, recordedBy, recordedAt}`.
  `result` es `OBSERVED` o `REJECTED`; `noteId`, si viene, es una nota de la inspección, y vacío
  equivale a no enviarlo.
- Solo el inspector asignado, con la inspección en curso y en un criterio aplicable. Si no, 422; si
  falta un campo, 400.
- Al cerrar, la evaluación del criterio toma el peor resultado y la mayor severidad entre la regla y
  las observaciones, y suma el motivo `observed by the inspector: <descripción>`. Así llega al
  hallazgo, al acta y a la certificación como cualquier otro motivo.
- Cada línea de criterio de `GET /inspections/{id}` trae `observedNonConformities`.
- Una nota que respalda una no conformidad observada no se puede borrar mientras la observación siga
  registrada: responde 422, y primero se retira la observación.
- `DELETE .../observed-nonconformities/{observationId}` la retira mientras la inspección está en curso,
  y responde 200 con la inspección. Después del cierre solo se retira por rectificación, con la
  corrección `{"type": "OBSERVATION_WITHDRAWAL", "criterionId": "...", "observationId": "..."}`.
- Registrar y retirar se auditan como `INSPECTION_DATA_RECORDED` e `INSPECTION_DATA_CORRECTED`, con el
  campo `observation.<criterio>.<id>`.

## Asistente de inspección (Entrega 3)

Sugiere hallazgos y severidades a partir de las notas y la evidencia de una inspección en curso. Solo
sugiere: lo que entra en la inspección es lo que el inspector acepta o edita, como no conformidad
observada. El asistente nunca cierra, emite ni registra hallazgos.

Está apagado por defecto. `certiflow.assistant.enabled=true` publica sus rutas, y mientras tanto
responden 404. `certiflow.assistant.provider` elige el adaptador: `none`, que responde 503
`MISCONFIGURED` en cada consulta, u `openai-compatible`, que funciona con cualquier servicio de ese
protocolo (OpenAI, el endpoint compatible de Gemini, Ollama, llama.cpp, vLLM…). Este último usa
`certiflow.assistant.base-url` y `certiflow.assistant.model`, y la clave, si el proveedor la pide,
en la variable de entorno `CERTIFLOW_ASSISTANT_API_KEY`. También se configuran
`certiflow.assistant.prompt` (`suggest-findings/v1`) y `certiflow.assistant.timeout-seconds` (60), que
limita la respuesta completa. Los parámetros que manda el adaptador están en el archivo de la versión
de prompt; si el proveedor rechaza alguno (los modelos de razonamiento de OpenAI, por ejemplo, no
aceptan `temperature` ni `max_tokens`), cada consulta responde 503 `MISCONFIGURED`, y ese modelo
necesita una versión de prompt con sus propios parámetros.

`POST /inspections/{id}/consultations`, sin cuerpo, responde 201 con la consulta:

```json
{
  "id": "c-1", "inspectionId": "i-1", "requestedBy": "p-7", "requestedAt": "2026-11-04T14:03:00Z",
  "assistant": { "adapter": "openai-compatible", "model": "llama3.1:8b", "prompt": "suggest-findings/v1" },
  "suggestions": [{
    "id": "s1", "criterionId": "ELEC", "section": "Electrical",
    "result": "REJECTED", "severity": "CRITICAL",
    "description": "Conductor con la aislación expuesta junto al tablero",
    "rationale": "La nota general describe un cable pelado; la medición de 12 MOhm no detecta un daño visible.",
    "decision": null
  }],
  "rejected": [{ "criterionId": "DOC", "result": "APPROVED", "severity": "LOW",
                 "description": "...", "rationale": "...", "reason": "an approving result is not a finding" }]
}
```

- `assistant.model` es el modelo que respondió según el proveedor, no necesariamente el configurado.
- `rejected` guarda lo que el dominio no aceptó y por qué: un criterio que no estaba en la consulta,
  un resultado aprobatorio, una descripción o justificación vacía, o un segundo resultado para el
  mismo criterio.
- Solo consulta el inspector asignado, con la inspección en curso y con al menos una nota. Si no,
  responde 422 sin llamar al modelo. Cada pedido es una consulta nueva.
- Si la consulta falla, la respuesta es 503 `ASSISTANT_UNAVAILABLE`, y no se guarda la consulta ni
  cambia la inspección. `details` dice por qué: `UNREACHABLE` (no respondió a tiempo o está
  saturado), `REFUSED` (se negó), `INCOMPLETE` (la respuesta o la inspección superan un límite de
  longitud del modelo), `MALFORMED` (la respuesta no sigue el esquema) o `MISCONFIGURED` (el proveedor
  rechaza la dirección, el modelo, la clave o los parámetros).

`POST .../consultations/{consultationId}/suggestions/{suggestionId}/decision` responde 200 con la
consulta actualizada. El cuerpo es uno de estos:

- `{"verdict": "ACCEPTED"}` registra la sugerencia tal cual, como no conformidad observada.
- `{"verdict": "EDITED", "criterionId": "...", "result": "OBSERVED", "severity": "MEDIUM", "description": "...", "note": "por qué se editó"}`
  registra la versión del inspector. `result` no puede ser `APPROVED`: para eso se descarta.
- `{"verdict": "DISCARDED", "reason": "opcional"}` no registra nada en la inspección. Un `reason`
  vacío equivale a no enviarlo.

Aceptar y editar siguen las reglas de la no conformidad observada, y se guardan en la misma
transacción que la decisión y su auditoría. `decision` trae `verdict`, `by`, `at`, y además
`observationId` (al aceptar o editar), los valores editados con `note`, o `reason`. Cada sugerencia
se decide una sola vez.

Errores: 400 sin `verdict`, con uno desconocido o con un campo de la edición vacío; 401; 404 si la
inspección o la consulta de esa inspección no existen; 409 ante una decisión concurrente; 422 si la
sugerencia ya se decidió o no existe, si la edición aprueba, o si la inspección no está en curso o no
la decide su inspector.

La inspección audita `ASSISTANCE_REQUESTED` y `ASSISTANCE_SUGGESTION_DECIDED`, junto a la no conformidad
que quedó registrada.

## Notificaciones

Al vencer una acción correctiva el sistema avisa a su responsable por un canal configurable. Por defecto el aviso va
al log; con `certiflow.notifications.webhook-url=<url>` se envía como `POST` JSON
(`recipientId`, `recipientName`, `subject`, `message`, `occurredAt`) con tiempo de espera
`certiflow.notifications.timeout-seconds` (3 por defecto). Es al mejor esfuerzo: si el canal falla se registra y no
afecta a la suspensión del certificado.
