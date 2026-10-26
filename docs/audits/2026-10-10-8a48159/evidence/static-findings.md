# Static finding evidence

Revision: `8a4815960670ac1d467df4a8f0932c8932d2fab4`. Code inspection only; no browser reproduction is claimed.

## app/frontend/src/pages/InspectionDetail.tsx:15

```text
15: export function InspectionDetail() {
16:   const { id = '' } = useParams()
17:   const { nameOf } = useParties()
18:   const inspection = useLoad(() => api.get<Inspection>(`/inspections/${id}`), [id])
19:   const asset = useLoad(
20:     () => (inspection.data ? api.get<Asset>(`/assets/${inspection.data.assetId}`) : Promise.resolve(undefined)),
21:     [inspection.data?.assetId],
22:   )
23:   const schemas = useLoad(
24:     () => (inspection.data?.schemaVersion ? api.get<Schema[]>('/schemas') : Promise.resolve([] as Schema[])),
25:     [inspection.data?.schemaVersion],
26:   )
27:   const { run, busy, error } = useAction()
28: 
29:   const version = useMemo(
30:     () => schemas.data?.flatMap((s) => s.versions).find((v) => v.id === inspection.data?.schemaVersion),
31:     [schemas.data, inspection.data?.schemaVersion],
32:   )
33:   const criteria = useMemo(() => criteriaById(version), [version])
34: 
35:   if (inspection.error) return <Page title="Inspección"><LoadError message={inspection.error} retry={inspection.reload} /></Page>
36:   if (!inspection.data) return <Page title="Inspección"><Loading /></Page>
37:   const i = inspection.data
38:   const assetName = asset.data?.name ?? i.assetId
39:   const sections = [...new Set(i.criteria.map((line) => line.section))]
40: 
41:   const start = () => run(async () => { await api.post(`/inspections/${id}/start`); inspection.reload() }, 'Inspección iniciada')
42:   const close = () => run(async () => { await api.post(`/inspections/${id}/close`); inspection.reload() }, 'Inspección cerrada')
43:   const answered = i.criteria.filter((line) => line.applicable && line.answer).length
44:   const applicable = i.criteria.filter((line) => line.applicable).length
45: 
46:   return (
47:     <Page
48:       title={`Inspección de ${assetName}`}
49:       subtitle={<><InspectionBadge status={i.status} /> · prevista para el {formatDate(i.expectedDate)}</>}
50:       actions={
51:         <>
52:           <Link className="button" to={`/activos/${i.assetId}`}>Ver activo</Link>
53:           {i.status === 'CLOSED' && <Link className="button" to={`/hallazgos?inspectionId=${i.id}`}>Ver hallazgos</Link>}
54:         </>
55:       }
56:     >
57:       <Card>
58:         <Facts
59:           items={[
60:             ['Inspector', nameOf(i.inspectorId)],
61:             ['Inicio', formatInstant(i.startedAt)],
62:             ['Cierre', formatInstant(i.closedAt)],
63:             ['Versión del esquema', i.schemaVersion ?? 'Se fija al iniciar'],
64:           ]}
65:         />
66:       </Card>
67:       <ErrorLine message={error} />
68: 
69:       {i.status === 'ASSIGNED' && (
70:         <Card title="Todavía no empezó">
71:           <p className="muted">
72:             Al iniciar se congela la versión vigente del esquema de inspección: aunque se publique otra después, esta inspección
73:             se completa con la misma.
74:           </p>
75:           <button className="primary" disabled={busy} onClick={start}>Iniciar inspección</button>
76:         </Card>
77:       )}
78: 
79:       {i.status !== 'ASSIGNED' && (
80:         <>
81:           {i.status === 'IN_PROGRESS' && !version && schemas.data && (
82:             <Notice kind="warn">No se encontró la versión del esquema de esta inspección; se muestran las respuestas sin poder editarlas.</Notice>
83:           )}
84:           {sections.map((section) => (
85:             <Card key={section} title={section}>
86:               <div className="stack">
87:                 {i.criteria.filter((line) => line.section === section).map((line) => (
88:                   <CriterionCard
89:                     key={line.criterionId}
90:                     inspectionId={i.id}
91:                     line={line}
92:                     criterion={criteria.get(line.criterionId)}
93:                     editable={i.status === 'IN_PROGRESS'}
94:                     onChange={inspection.reload}
95:                   />
96:                 ))}
97:               </div>
98:             </Card>
99:           ))}
100:           <Notes inspection={i} onChange={inspection.reload} />
101:         </>
102:       )}
103: 
104:       {i.status === 'IN_PROGRESS' && (
105:         <Card title="Cerrar inspección">
106:           <p className="muted">
107:             Respondiste {answered} de {applicable} criterios. Al cerrar se evalúan las respuestas, se generan los hallazgos y la
108:             inspección ya no se puede editar.
109:           </p>
110:           <button className="primary" disabled={busy || answered === 0} onClick={close}>Cerrar inspección</button>
111:         </Card>
112:       )}
113: 
114:       {i.status === 'CLOSED' && (
115:         <CertificationPanel inspectionId={i.id} assetId={i.assetId} subsystems={i.subsystems} />
116:       )}
117:     </Page>
118:   )
119: }
120: 
121: function Notes({ inspection, onChange }: { inspection: Inspection; onChange: () => void }) {
122:   const { nameOf } = useParties()
123:   const { run, busy, error } = useAction()
124:   const [text, setText] = useState('')
125:   const [criterionId, setCriterionId] = useState('')
126:   const open = inspection.status === 'IN_PROGRESS'
127: 
128:   async function submit(event: FormEvent) {
```

## app/frontend/src/components/CertificationPanel.tsx:17

```text
17:   inspectionId: string
18:   assetId: string
19:   subsystems: string[]
20: }) {
21:   const issued = useLoad(() => api.get<Certificate[]>('/certificates' + query({ inspectionId })), [inspectionId])
22:   const assetCertificates = useLoad(() => api.get<Certificate[]>('/certificates' + query({ assetId })), [assetId])
23:   const derivation = useLoad(
24:     () => (subsystems.length ? api.get<{ type: string; reasons?: string[] }>(`/inspections/${inspectionId}/global-derivation`) : Promise.resolve(null)),
25:     [inspectionId, subsystems.length],
26:   )
27:   const targets: Target[] = [...subsystems.map((subsystem) => ({ subsystem })), { subsystem: null }]
28:   const reloadAll = () => {
29:     issued.reload()
30:     assetCertificates.reload()
31:     derivation.reload()
32:   }
33: 
34:   return (
35:     <Card title="Certificación">
36:       {subsystems.length > 0 && (
37:         <p className="muted">
38:           Cada subsistema se certifica por separado. El certificado global se puede emitir cuando todos los parciales están en regla.
39:         </p>
40:       )}
41:       {issued.loading && !issued.data ? <Loading /> : (
42:         <div className="stack">
43:           {targets.map((target) => (
44:             <TargetRow
45:               key={target.subsystem ?? 'global'}
46:               inspectionId={inspectionId}
47:               target={target}
48:               certificate={issued.data?.find((c) => (target.subsystem ? c.subsystem === target.subsystem : c.scope === 'GLOBAL'))}
49:               previous={assetCertificates.data?.find(
50:                 (c) => c.backingInspectionId !== inspectionId && (target.subsystem ? c.subsystem === target.subsystem : c.scope === 'GLOBAL'),
51:               )}
52:               onChange={reloadAll}
53:             />
54:           ))}
55:         </div>
56:       )}
57:       {derivation.data && (
58:         <div style={{ marginTop: 14 }}>
59:           {derivation.data.type === 'Derived' ? (
60:             <Notice kind="ok">Los certificados parciales vigentes componen un certificado global.</Notice>
61:           ) : (
62:             <Notice kind="info">
63:               Todavía no se puede componer un global a partir de los parciales{derivation.data.reasons?.length ? ':' : '.'}
64:               {derivation.data.reasons && <ul>{derivation.data.reasons.map((r) => <li key={r}>{r}</li>)}</ul>}
65:             </Notice>
66:           )}
67:         </div>
68:       )}
69:     </Card>
70:   )
71: }
72: 
73: function TargetRow({ inspectionId, target, certificate, previous, onChange }: {
74:   inspectionId: string
75:   target: Target
76:   certificate: Certificate | undefined
77:   previous: Certificate | undefined
78:   onChange: () => void
79: }) {
80:   const toast = useToast()
81:   const subsystemPath = target.subsystem ? `?subsystem=${encodeURIComponent(target.subsystem)}` : ''
82:   const eligibility = useLoad(
83:     () => api.get<Assessment>(`/inspections/${inspectionId}/eligibility${subsystemPath}`),
84:     [inspectionId, subsystemPath, certificate?.id],
85:   )
86:   const { run, busy, error } = useAction()
```

## app/frontend/src/components/CertificationPanel.tsx:90

```text
90:   async function issue(path: 'certificates' | 'certificates/renewal') {
91:     setBlocked(null)
92:     try {
93:       const reply = await api.post<{ outcome: string }>(`/inspections/${inspectionId}/${path}`, target.subsystem ? { subsystem: target.subsystem } : {})
94:       toast.show(reply.outcome === 'ALREADY_ISSUED' ? 'Este certificado ya estaba emitido' : path === 'certificates' ? 'Certificado emitido' : 'Certificado renovado')
95:       onChange()
96:     } catch (failure) {
97:       const body = failure instanceof ApiError ? (failure.body as { outcome?: string; assessment?: Assessment } | null) : null
98:       if (body?.outcome === 'BLOCKED' && body.assessment) {
99:         setBlocked(body.assessment)
100:         eligibility.reload()
101:       } else {
102:         throw failure
103:       }
104:     }
105:   }
106: 
107:   const blockers = (blocked ?? eligibility.data)?.blockers ?? []
108:   const eligible = !!eligibility.data && eligibility.data.blockers.length === 0
109: 
110:   return (
111:     <div className="criterion">
112:       <header>
113:         <div>
114:           <strong>{title}</strong>{' '}
115:           {certificate ? (
116:             <>
117:               <CertificateBadge status={certificate.status} />{' '}
118:               <Link to={`/certificados/${certificate.id}`}>Ver certificado</Link>
119:             </>
120:           ) : eligibility.data ? (
121:             eligible ? <Badge tone="ok">Se puede emitir{eligibility.data.mode ? ` (${label(String(eligibility.data.mode)).toLowerCase()})` : ''}</Badge> : <Badge tone="warn">Bloqueado</Badge>
122:           ) : null}
123:         </div>
124:         {!certificate && (
125:           <div className="row">
126:             {previous && (
127:               <button disabled={busy || !eligible} title={`Renueva el certificado anterior (${humanize(previous.status)})`} onClick={() => run(() => issue('certificates/renewal'))}>
128:                 Renovar
129:               </button>
130:             )}
131:             <button className="primary" disabled={busy || !eligible} onClick={() => run(() => issue('certificates'))}>
132:               Emitir certificado
133:             </button>
134:           </div>
135:         )}
136:       </header>
137:       {eligibility.error && <ErrorLine message={eligibility.error} />}
138:       {!certificate && blockers.length > 0 && (
139:         <Notice kind="warn">
140:           No se puede emitir todavía:
141:           <Blockers blockers={blockers} />
142:         </Notice>
143:       )}
144:       <ErrorLine message={error} />
145:     </div>
146:   )
147: }
```

## core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java:217

```text
217: 
218:     public InspectionClosureResult close(PartyId actor, Instant at) {
219:         Validate.required(at, "closure instant");
220:         requireAssignedInspector(actor, "close");
221:         if (status.closed()) {
222:             return new InspectionClosureResult(id, closedAt, true, currentEvaluations());
223:         }
224:         requireInProgress("close");
225:         Validate.ensure(!at.isBefore(startedAt), "closure cannot precede the inspection start");
226:         Map<CriterionId, CriterionEvaluation> evaluations = new LinkedHashMap<>();
227:         for (Criterion criterion : frozenSchemaVersion.criteria()) {
228:             CriterionRecord record = requireRecord(criterion.id());
229:             if (!record.applicable()) {
230:                 continue;
231:             }
232:             evaluations.put(criterion.id(), evaluator.evaluate(criterion, record, at));
233:         }
234:         evaluations.forEach((criterionId, evaluation) -> requireRecord(criterionId).recordClosureEvaluation(evaluation));
235:         this.status = InspectionStatus.CLOSED;
236:         this.closedAt = at;
237:         return new InspectionClosureResult(id, at, false, evaluations);
238:     }
239: 
240:     public Rectification rectify(RectificationId rectificationId,
241:             PartyId author, Instant at, String reason, List<Correction> corrections) {
242:         Validate.ensure(status.closed(), "only a closed inspection can be rectified");
```

## core/src/main/java/ar/edu/itba/dps/certification/application/certification/CertificateFactory.java:223

```text
223:     private void requireWholeAssetSchema(Inspection inspection) {
224:         Validate.ensure(inspection.certifiableSubsystems().isEmpty(),
225:                 "inspection " + inspection.id() + " certifies the subsystems "
226:                         + inspection.certifiableSubsystems() + " separately, so derive the global "
227:                         + "certificate from them instead of issuing one");
228:     }
229: 
230:     private void requireDeclaredSubsystem(Inspection inspection, Subsystem subsystem) {
231:         Validate.required(subsystem, "subsystem");
232:         Validate.ensure(inspection.certifiableSubsystems().contains(subsystem),
233:                 "inspection " + inspection.id() + " does not certify subsystem " + subsystem
234:                         + "; it certifies " + inspection.certifiableSubsystems());
235:     }
```

## DESIGN.md:650

```text
650: **Refactorizaciones.** Ninguna en el núcleo ni en la API.
651: 
652: **Pruebas.** Vitest (20): cliente HTTP, conversión de formularios de reglas (regla de opciones, tramos numéricos,
653: secciones), formatos y etiquetas. `FrontendIT` (3): la página, sus scripts y la API conviven. Además se recorrió el
654: flujo completo en un navegador real contra una imitación del contrato (esa imitación no se incluye en el repositorio).
655: 
656: **Deuda técnica deliberada.**
657: - *Sin pruebas de componentes ni de extremo a extremo versionadas;* la interfaz se probó a mano y con un recorrido
658:   automatizado que no forma parte del repositorio.
659: - *La interfaz no cubre* la rectificación de inspecciones, la reasignación de inspector, la transferencia de
660:   aplicabilidad entre esquemas ni la consulta de versiones por número (están en la API).
661: - *Textos de dominio en inglés:* los nombres de subsistemas y características, y los mensajes de error y de motivos
662:   que arma el núcleo, se muestran como llegan; un diccionario (`labels.ts`) traduce los conocidos y el resto se
663:   muestra legible pero sin traducir. Internacionalizar bien exige que la API devuelva códigos en lugar de frases.
664: - *Datos sin traducción propia:* política, detalle de auditoría y evaluación se muestran con un visor genérico (`Tree`).
665: - *Evidencia como texto,* igual que en la API; no hay subida de archivos.
666: - *Sin paginación* en las tablas (igual que la API); diseño pensado para escritorio, utilizable en pantallas chicas.
```

## app/src/main/java/ar/edu/itba/dps/certification/app/web/InspectionController.java:180

```text
180:     }
181: 
182:     @DeleteMapping("/{id}/notes/{noteId}")
183:     InspectionResponse removeNote(@PathVariable("id") String id, @PathVariable("noteId") String noteId) {
184:         existing(id);
185:         return view(transactions.execute(() -> removeNote.remove(InspectionId.of(id), noteId)));
186:     }
187: 
188:     /** Closing evaluates every criterion; closing an already closed inspection is harmless. */
189:     @PostMapping("/{id}/close")
190:     InspectionResponse close(@PathVariable("id") String id) {
191:         existing(id);
192:         transactions.execute(() -> close.close(InspectionId.of(id)));
193:         return view(existing(id));
194:     }
195: 
196:     @PostMapping("/{id}/rectifications")
197:     ResponseEntity<InspectionResponse> rectify(@PathVariable("id") String id,
198:             @RequestBody RectifyRequest request) {
199:         existing(id);
200:         transactions.execute(() -> rectify.rectify(InspectionId.of(id), request.reason(), request.toDomain()));
201:         return ResponseEntity.status(201).body(view(existing(id)));
202:     }
203: 
204:     /** The inspection act: what was recorded, with original and rectified values side by side. */
205:     @GetMapping("/{id}/act")
206:     Object act(@PathVariable("id") String id) {
207:         existing(id);
208:         return Plain.of(act.generate(InspectionId.of(id)));
209:     }
210: 
211:     private Inspection existing(String id) {
212:         return inspections.find(InspectionId.of(id))
213:                 .orElseThrow(() -> new NotFoundException("inspection " + id + " does not exist"));
214:     }
215: 
216:     private InspectionResponse view(Inspection inspection) {
217:         var version = inspections.frozenVersion(inspection).orElse(null);
218:         return InspectionResponse.of(inspection, version, Plain::of);
219:     }
220: }
```

## Search for missing UI consumers

`rg -n 'rectifications|findings-summary|/act[\x27\x22`]|/assignment' app/frontend/src`

Exit 1; output:

```text
(no matches)
```

This search is supplementary: InspectionDetail, App routes, API client and all report-related UI consumers were inspected. An absence claim does not depend only on a particular search expression.