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
| 500 | `INTERNAL_ERROR` | cualquier otra cosa |

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
| `POST /schemas/{id}/applicability`, `DELETE .../applicability/{assetType}`, `POST .../applicability/transfer` | a qué tipos de activo aplica |
| `POST /inspections`, `GET /inspections?assetId=&inspectorId=&status=`, `GET /inspections/{id}` | inspecciones |
| `PUT /inspections/{id}/assignment` | reasignar inspector y fecha |
| `POST /inspections/{id}/start`, `POST .../close` | iniciar (congela la versión del esquema) y cerrar |
| `PUT` / `DELETE /inspections/{id}/answers/{criterionId}` | responder / quitar respuesta |
| `POST /inspections/{id}/evidence`, `DELETE .../criteria/{c}/evidence/{e}` | evidencias |
| `POST /inspections/{id}/notes`, `PUT` / `DELETE .../notes/{noteId}` | notas |
| `POST /inspections/{id}/rectifications` | rectificar una inspección cerrada |
| `GET /inspections/{id}/act` | acta con valores originales y rectificados |
| `GET /findings?inspectionId=&assetId=&openActions=`, `GET /findings/{id}` | hallazgos |
| `POST /findings/{id}/plan` / `execution` / `verification` | acción correctiva: planificar (responsable), ejecutar (ejecutor), verificar (inspector) |
| `GET /inspections/{id}/findings-summary` | resumen de hallazgos |
| `POST /inspections/{id}/certificates` | emitir; `{"subsystem": "..."}` = parcial (F1), sin cuerpo = global |
| `POST /inspections/{id}/certificates/renewal` | renovar |
| `GET /inspections/{id}/eligibility?subsystem=` | si se puede emitir y qué lo bloquea |
| `GET /inspections/{id}/global-derivation` | si los parciales vigentes componen un global |
| `GET /certificates?assetId=&inspectionId=&status=`, `GET /certificates/{id}`, `GET /certificates/{id}/report` | certificados |
| `GET /audit?type=&id=` | auditoría, completa o de un elemento |
| `POST /admin/jobs/run` | ejecuta ahora los barridos y devuelve `{expiredCertificates, expiredActions, deliveredEvents}` |
| `GET /admin/outbox?status=PENDING\|DONE\|DEAD` | eventos del outbox (otro `status` responde 400) |
| `POST /admin/outbox/retry-dead` | da nuevos intentos a los eventos `DEAD` y los entrega: `{revived, delivered}` |

Una emisión bloqueada responde **422** con `{"outcome": "BLOCKED", "assessment": {... "blockers": [...]}}`;
una emisión repetida responde 200 con `"outcome": "ALREADY_ISSUED"`.

## Notificaciones

Al vencer una acción correctiva el sistema avisa a su responsable por un canal configurable. Por defecto el aviso va
al log; con `certiflow.notifications.webhook-url=<url>` se envía como `POST` JSON
(`recipientId`, `recipientName`, `subject`, `message`, `occurredAt`) con tiempo de espera
`certiflow.notifications.timeout-seconds` (3 por defecto). Es al mejor esfuerzo: si el canal falla se registra y no
afecta a la suspensión del certificado.
