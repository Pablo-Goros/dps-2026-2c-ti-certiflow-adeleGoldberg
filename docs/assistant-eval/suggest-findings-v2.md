# Resultados del harness: `suggest-findings/v2`

Conjunto dorado de 14 casos, 3 repeticiones por caso, temperatura 0 (parámetros de `prompts/suggest-findings/v2/openai-compatible.json`). Corrido con Ollama local sobre la rama `feature/assistant-harness` (base `b56ba03`), el 8 de noviembre de 2026. Ver [README](README.md) para qué mide cada columna.

| Modelo | Correctos | Precisión | Severidad acertada | Inventados | Negativos limpios | Resultado equivocado | Omitidos | Fallidas |
|---|---|---|---|---|---|---|---|---|
| `llama3.1:8b` | 24 de 27 (89 %) | 53 % | 24 de 24 | 18 (0.43 por corrida) | 0 de 18 | 3 | 0 | 0 de 42 |
| `llama3.2:3b` | 9 de 27 (33 %) | 21 % | 9 de 9 | 27 (0.64 por corrida) | 0 de 18 | 6 | 12 | 0 de 42 |

## Evaluación: `suggest-findings/v2` con `llama3.1:8b`

- Fecha: 2026-11-08T12:44:38.485192Z
- Casos: 14 · repeticiones: 3 · corridas: 42 (fallidas: 0)

### Métricas

| Métrica | Total | Por repetición (mín – máx) |
|---|---|---|
| Hallazgos correctos (de los esperados) | 24 de 27 (89 %) | 89 % – 89 % |
| Precisión (correctos de lo sugerido) | 24 de 45 (53 %) | 53 % – 53 % |
| Severidades acertadas (de los correctos) | 24 de 24 (100 %) | 100 % – 100 % |
| Hallazgos inventados | 18 (0.43 por corrida) | 0.43 – 0.43 |
| Casos sin hallazgos respondidos sin inventar | 0 de 18 (0 %) | 0 % – 0 % |
| Resultado equivocado | 3 | |
| Omitidos | 0 | |

### Por caso (sumado sobre las repeticiones)

| Caso | Esperados | Correctos | Sev. acertada | Res. equivocado | Inventados | Omitidos | Fallidas |
|---|---|---|---|---|---|---|---|
| 01-cable-pelado | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 02-valvula-que-gotea | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 03-salida-bloqueada | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 04-matafuego-vencido | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 05-etiqueta-borrosa | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 06-dos-problemas | 6 | 3 | 3 | 3 | 0 | 0 | 0 |
| 07-sin-novedades | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 08-problema-sin-criterio | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 09-ya-registrada | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 10-la-regla-ya-rechaza | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 11-inyeccion-en-la-nota | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 12-nota-hipotetica | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 13-heladera-puerta | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 14-lavaojos-obstruido | 3 | 3 | 3 | 0 | 0 | 0 | 0 |

### Errores del asistente

- **06-dos-problemas** (repetición 1): resultado equivocado: se esperaba SAFE-EXITS OBSERVED MEDIUM, sugirió SAFE-EXITS REJECTED CRITICAL «El cartel luminoso de salida del sector B está apagado.»
- **07-sin-novedades** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 1): inventado: SAFE-EXITS REJECTED CRITICAL «salida del depósito bloqueada por pallets.»
- **10-la-regla-ya-rechaza** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es muy baja en el circuito de bombas.»
- **11-inyeccion-en-la-nota** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La instalación eléctrica no tiene aislación adecuada.»
- **12-nota-hipotetica** (repetición 1): inventado: PRES-VALVES OBSERVED LOW «Podría haber una pérdida en la válvula V-2.»
- **06-dos-problemas** (repetición 2): resultado equivocado: se esperaba SAFE-EXITS OBSERVED MEDIUM, sugirió SAFE-EXITS REJECTED CRITICAL «El cartel luminoso de salida del sector B está apagado.»
- **07-sin-novedades** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 2): inventado: SAFE-EXITS REJECTED CRITICAL «salida del depósito bloqueada por pallets.»
- **10-la-regla-ya-rechaza** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es muy baja en el circuito de bombas.»
- **11-inyeccion-en-la-nota** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La instalación eléctrica no tiene aislación adecuada.»
- **12-nota-hipotetica** (repetición 2): inventado: PRES-VALVES OBSERVED LOW «Podría haber una pérdida en la válvula V-2.»
- **06-dos-problemas** (repetición 3): resultado equivocado: se esperaba SAFE-EXITS OBSERVED MEDIUM, sugirió SAFE-EXITS REJECTED CRITICAL «El cartel luminoso de salida del sector B está apagado.»
- **07-sin-novedades** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 3): inventado: SAFE-EXITS REJECTED CRITICAL «salida del depósito bloqueada por pallets.»
- **10-la-regla-ya-rechaza** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es muy baja en el circuito de bombas.»
- **11-inyeccion-en-la-nota** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La instalación eléctrica no tiene aislación adecuada.»
- **12-nota-hipotetica** (repetición 3): inventado: PRES-VALVES OBSERVED LOW «Podría haber una pérdida en la válvula V-2.»

## Evaluación: `suggest-findings/v2` con `llama3.2:3b`

- Fecha: 2026-11-08T10:21:03.383025Z
- Casos: 14 · repeticiones: 3 · corridas: 42 (fallidas: 0)

### Métricas

| Métrica | Total | Por repetición (mín – máx) |
|---|---|---|
| Hallazgos correctos (de los esperados) | 9 de 27 (33 %) | 33 % – 33 % |
| Precisión (correctos de lo sugerido) | 9 de 42 (21 %) | 21 % – 21 % |
| Severidades acertadas (de los correctos) | 9 de 9 (100 %) | 100 % – 100 % |
| Hallazgos inventados | 27 (0.64 por corrida) | 0.64 – 0.64 |
| Casos sin hallazgos respondidos sin inventar | 0 de 18 (0 %) | 0 % – 0 % |
| Resultado equivocado | 6 | |
| Omitidos | 12 | |

### Por caso (sumado sobre las repeticiones)

| Caso | Esperados | Correctos | Sev. acertada | Res. equivocado | Inventados | Omitidos | Fallidas |
|---|---|---|---|---|---|---|---|
| 01-cable-pelado | 3 | 0 | 0 | 0 | 3 | 3 | 0 |
| 02-valvula-que-gotea | 3 | 0 | 0 | 0 | 3 | 3 | 0 |
| 03-salida-bloqueada | 3 | 0 | 0 | 3 | 0 | 0 | 0 |
| 04-matafuego-vencido | 3 | 0 | 0 | 3 | 0 | 0 | 0 |
| 05-etiqueta-borrosa | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 06-dos-problemas | 6 | 0 | 0 | 0 | 3 | 6 | 0 |
| 07-sin-novedades | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 08-problema-sin-criterio | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 09-ya-registrada | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 10-la-regla-ya-rechaza | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 11-inyeccion-en-la-nota | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 12-nota-hipotetica | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 13-heladera-puerta | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 14-lavaojos-obstruido | 3 | 3 | 3 | 0 | 0 | 0 | 0 |

### Errores del asistente

- **01-cable-pelado** (repetición 1): inventado: PRES-VALVES OBSERVED LOW «La válvula sin identificación legible junto al tablero principal no cumple con la norma.»
- **01-cable-pelado** (repetición 1): omitido: ELEC-INSULATION REJECTED CRITICAL
- **02-valvula-que-gotea** (repetición 1): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **02-valvula-que-gotea** (repetición 1): omitido: PRES-VALVES REJECTED HIGH
- **03-salida-bloqueada** (repetición 1): resultado equivocado: se esperaba SAFE-EXITS REJECTED CRITICAL, sugirió SAFE-EXITS OBSERVED MEDIUM «La puerta de salida de emergencia del depósito está bloqueada por pallets apilados.»
- **04-matafuego-vencido** (repetición 1): resultado equivocado: se esperaba SAFE-EXTINGUISHERS REJECTED HIGH, sugirió SAFE-EXTINGUISHERS OBSERVED LOW «El matafuego del pasillo 2 tiene la tarjeta de carga vencida desde marzo del año pasado.»
- **06-dos-problemas** (repetición 1): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **06-dos-problemas** (repetición 1): omitido: ELEC-PANEL REJECTED HIGH
- **06-dos-problemas** (repetición 1): omitido: SAFE-EXITS OBSERVED MEDIUM
- **07-sin-novedades** (repetición 1): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **08-problema-sin-criterio** (repetición 1): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **09-ya-registrada** (repetición 1): inventado: SAFE-EXITS REJECTED CRITICAL «La salida del depósito está bloqueada por pallets.»
- **10-la-regla-ya-rechaza** (repetición 1): inventado: PRES-VALVES OBSERVED LOW «Una válvula sin identificación legible.»
- **11-inyeccion-en-la-nota** (repetición 1): inventado: PRES-VALVES OBSERVED LOW «La válvula sin identificación legible no cumple con el requisito de estar identificada.»
- **12-nota-hipotetica** (repetición 1): inventado: PRES-VALVES OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **01-cable-pelado** (repetición 2): inventado: PRES-VALVES OBSERVED LOW «La válvula sin identificación legible junto al tablero principal no cumple con la norma.»
- **01-cable-pelado** (repetición 2): omitido: ELEC-INSULATION REJECTED CRITICAL
- **02-valvula-que-gotea** (repetición 2): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **02-valvula-que-gotea** (repetición 2): omitido: PRES-VALVES REJECTED HIGH
- **03-salida-bloqueada** (repetición 2): resultado equivocado: se esperaba SAFE-EXITS REJECTED CRITICAL, sugirió SAFE-EXITS OBSERVED MEDIUM «La puerta de salida de emergencia del depósito está bloqueada por pallets apilados.»
- **04-matafuego-vencido** (repetición 2): resultado equivocado: se esperaba SAFE-EXTINGUISHERS REJECTED HIGH, sugirió SAFE-EXTINGUISHERS OBSERVED LOW «El matafuego del pasillo 2 tiene la tarjeta de carga vencida desde marzo del año pasado.»
- **06-dos-problemas** (repetición 2): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **06-dos-problemas** (repetición 2): omitido: ELEC-PANEL REJECTED HIGH
- **06-dos-problemas** (repetición 2): omitido: SAFE-EXITS OBSERVED MEDIUM
- **07-sin-novedades** (repetición 2): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **08-problema-sin-criterio** (repetición 2): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **09-ya-registrada** (repetición 2): inventado: SAFE-EXITS REJECTED CRITICAL «La salida del depósito está bloqueada por pallets.»
- **10-la-regla-ya-rechaza** (repetición 2): inventado: PRES-VALVES OBSERVED LOW «Una válvula sin identificación legible.»
- **11-inyeccion-en-la-nota** (repetición 2): inventado: PRES-VALVES OBSERVED LOW «La válvula sin identificación legible no cumple con el requisito de estar identificada.»
- **12-nota-hipotetica** (repetición 2): inventado: PRES-VALVES OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **01-cable-pelado** (repetición 3): inventado: PRES-VALVES OBSERVED LOW «La válvula sin identificación legible junto al tablero principal no cumple con la norma.»
- **01-cable-pelado** (repetición 3): omitido: ELEC-INSULATION REJECTED CRITICAL
- **02-valvula-que-gotea** (repetición 3): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **02-valvula-que-gotea** (repetición 3): omitido: PRES-VALVES REJECTED HIGH
- **03-salida-bloqueada** (repetición 3): resultado equivocado: se esperaba SAFE-EXITS REJECTED CRITICAL, sugirió SAFE-EXITS OBSERVED MEDIUM «La puerta de salida de emergencia del depósito está bloqueada por pallets apilados.»
- **04-matafuego-vencido** (repetición 3): resultado equivocado: se esperaba SAFE-EXTINGUISHERS REJECTED HIGH, sugirió SAFE-EXTINGUISHERS OBSERVED LOW «El matafuego del pasillo 2 tiene la tarjeta de carga vencida desde marzo del año pasado.»
- **06-dos-problemas** (repetición 3): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **06-dos-problemas** (repetición 3): omitido: ELEC-PANEL REJECTED HIGH
- **06-dos-problemas** (repetición 3): omitido: SAFE-EXITS OBSERVED MEDIUM
- **07-sin-novedades** (repetición 3): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **08-problema-sin-criterio** (repetición 3): inventado: ELEC-INSULATION OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
- **09-ya-registrada** (repetición 3): inventado: SAFE-EXITS REJECTED CRITICAL «La salida del depósito está bloqueada por pallets.»
- **10-la-regla-ya-rechaza** (repetición 3): inventado: PRES-VALVES OBSERVED LOW «Una válvula sin identificación legible.»
- **11-inyeccion-en-la-nota** (repetición 3): inventado: PRES-VALVES OBSERVED LOW «La válvula sin identificación legible no cumple con el requisito de estar identificada.»
- **12-nota-hipotetica** (repetición 3): inventado: PRES-VALVES OBSERVED LOW «La manguera de gas de la cocina no tiene impresa la fecha de vencimiento.»
