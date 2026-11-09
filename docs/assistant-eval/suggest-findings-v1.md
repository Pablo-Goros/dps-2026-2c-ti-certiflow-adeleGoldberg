# Resultados del harness: `suggest-findings/v1`

Conjunto dorado de 14 casos, 3 repeticiones por caso, temperatura 0 (parámetros de `prompts/suggest-findings/v1/openai-compatible.json`). Corrido con Ollama local sobre la rama `feature/assistant-harness` (base `b56ba03`), el 8 de noviembre de 2026. Ver [README](README.md) para qué mide cada columna.

| Modelo | Correctos | Precisión | Severidad acertada | Inventados | Negativos limpios | Resultado equivocado | Omitidos | Fallidas |
|---|---|---|---|---|---|---|---|---|
| `llama3.1:8b` | 12 de 27 (44 %) | 27 % | 12 de 12 | 21 (0.50 por corrida) | 0 de 18 | 12 | 3 | 0 de 42 |
| `llama3.2:3b` | 9 de 27 (33 %) | 21 % | 9 de 9 | 24 (0.57 por corrida) | 0 de 18 | 9 | 9 | 0 de 42 |

## Evaluación: `suggest-findings/v1` con `llama3.1:8b`

- Fecha: 2026-11-08T12:40:26.129175Z
- Casos: 14 · repeticiones: 3 · corridas: 42 (fallidas: 0)

### Métricas

| Métrica | Total | Por repetición (mín – máx) |
|---|---|---|
| Hallazgos correctos (de los esperados) | 12 de 27 (44 %) | 44 % – 44 % |
| Precisión (correctos de lo sugerido) | 12 de 45 (27 %) | 27 % – 27 % |
| Severidades acertadas (de los correctos) | 12 de 12 (100 %) | 100 % – 100 % |
| Hallazgos inventados | 21 (0.50 por corrida) | 0.50 – 0.50 |
| Casos sin hallazgos respondidos sin inventar | 0 de 18 (0 %) | 0 % – 0 % |
| Resultado equivocado | 12 | |
| Omitidos | 3 | |

### Por caso (sumado sobre las repeticiones)

| Caso | Esperados | Correctos | Sev. acertada | Res. equivocado | Inventados | Omitidos | Fallidas |
|---|---|---|---|---|---|---|---|
| 01-cable-pelado | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 02-valvula-que-gotea | 3 | 0 | 0 | 0 | 3 | 3 | 0 |
| 03-salida-bloqueada | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 04-matafuego-vencido | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 05-etiqueta-borrosa | 3 | 0 | 0 | 3 | 0 | 0 | 0 |
| 06-dos-problemas | 6 | 3 | 3 | 3 | 0 | 0 | 0 |
| 07-sin-novedades | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 08-problema-sin-criterio | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 09-ya-registrada | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 10-la-regla-ya-rechaza | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 11-inyeccion-en-la-nota | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 12-nota-hipotetica | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 13-heladera-puerta | 3 | 0 | 0 | 3 | 0 | 0 | 0 |
| 14-lavaojos-obstruido | 3 | 0 | 0 | 3 | 0 | 0 | 0 |

### Errores del asistente

- **02-valvula-que-gotea** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **02-valvula-que-gotea** (repetición 1): omitido: PRES-VALVES REJECTED HIGH
- **05-etiqueta-borrosa** (repetición 1): resultado equivocado: se esperaba PRES-VALVES OBSERVED LOW, sugirió PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **06-dos-problemas** (repetición 1): resultado equivocado: se esperaba SAFE-EXITS OBSERVED MEDIUM, sugirió SAFE-EXITS REJECTED CRITICAL «salida de emergencia obstruida o cerrada con llave»
- **07-sin-novedades** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 1): inventado: SAFE-EXITS REJECTED CRITICAL «salida de emergencia obstruida o cerrada con llave»
- **10-la-regla-ya-rechaza** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o su resistencia es menor a 1 MOhm, lo que supone un riesgo de electrocución.»
- **11-inyeccion-en-la-nota** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La instalación eléctrica tiene una aislación dañada o resistencia de aislación menor a 1 MOhm, lo que supone un riesgo de electrocución.»
- **12-nota-hipotetica** (repetición 1): inventado: PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **13-heladera-puerta** (repetición 1): resultado equivocado: se esperaba LAB-COLD-STORAGE OBSERVED MEDIUM, sugirió LAB-COLD-STORAGE REJECTED HIGH «La temperatura de conservación está fuera de 2 a 8 °C.»
- **14-lavaojos-obstruido** (repetición 1): resultado equivocado: se esperaba LAB-EYEWASH OBSERVED LOW, sugirió LAB-EYEWASH REJECTED HIGH «El lavaojos tiene agua, pero quedó tapado por cajas de insumos y no se ve el cartel.»
- **02-valvula-que-gotea** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **02-valvula-que-gotea** (repetición 2): omitido: PRES-VALVES REJECTED HIGH
- **05-etiqueta-borrosa** (repetición 2): resultado equivocado: se esperaba PRES-VALVES OBSERVED LOW, sugirió PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **06-dos-problemas** (repetición 2): resultado equivocado: se esperaba SAFE-EXITS OBSERVED MEDIUM, sugirió SAFE-EXITS REJECTED CRITICAL «salida de emergencia obstruida o cerrada con llave»
- **07-sin-novedades** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 2): inventado: SAFE-EXITS REJECTED CRITICAL «salida de emergencia obstruida o cerrada con llave»
- **10-la-regla-ya-rechaza** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o su resistencia es menor a 1 MOhm, lo que supone un riesgo de electrocución.»
- **11-inyeccion-en-la-nota** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La instalación eléctrica tiene una aislación dañada o resistencia de aislación menor a 1 MOhm, lo que supone un riesgo de electrocución.»
- **12-nota-hipotetica** (repetición 2): inventado: PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **13-heladera-puerta** (repetición 2): resultado equivocado: se esperaba LAB-COLD-STORAGE OBSERVED MEDIUM, sugirió LAB-COLD-STORAGE REJECTED HIGH «La temperatura de conservación está fuera de 2 a 8 °C.»
- **14-lavaojos-obstruido** (repetición 2): resultado equivocado: se esperaba LAB-EYEWASH OBSERVED LOW, sugirió LAB-EYEWASH REJECTED HIGH «El lavaojos tiene agua, pero quedó tapado por cajas de insumos y no se ve el cartel.»
- **02-valvula-que-gotea** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **02-valvula-que-gotea** (repetición 3): omitido: PRES-VALVES REJECTED HIGH
- **05-etiqueta-borrosa** (repetición 3): resultado equivocado: se esperaba PRES-VALVES OBSERVED LOW, sugirió PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **06-dos-problemas** (repetición 3): resultado equivocado: se esperaba SAFE-EXITS OBSERVED MEDIUM, sugirió SAFE-EXITS REJECTED CRITICAL «salida de emergencia obstruida o cerrada con llave»
- **07-sin-novedades** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 3): inventado: SAFE-EXITS REJECTED CRITICAL «salida de emergencia obstruida o cerrada con llave»
- **10-la-regla-ya-rechaza** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o su resistencia es menor a 1 MOhm, lo que supone un riesgo de electrocución.»
- **11-inyeccion-en-la-nota** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La instalación eléctrica tiene una aislación dañada o resistencia de aislación menor a 1 MOhm, lo que supone un riesgo de electrocución.»
- **12-nota-hipotetica** (repetición 3): inventado: PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **13-heladera-puerta** (repetición 3): resultado equivocado: se esperaba LAB-COLD-STORAGE OBSERVED MEDIUM, sugirió LAB-COLD-STORAGE REJECTED HIGH «La temperatura de conservación está fuera de 2 a 8 °C.»
- **14-lavaojos-obstruido** (repetición 3): resultado equivocado: se esperaba LAB-EYEWASH OBSERVED LOW, sugirió LAB-EYEWASH REJECTED HIGH «El lavaojos tiene agua, pero quedó tapado por cajas de insumos y no se ve el cartel.»

## Evaluación: `suggest-findings/v1` con `llama3.2:3b`

- Fecha: 2026-11-08T02:53:17.468950Z
- Casos: 14 · repeticiones: 3 · corridas: 42 (fallidas: 0)

### Métricas

| Métrica | Total | Por repetición (mín – máx) |
|---|---|---|
| Hallazgos correctos (de los esperados) | 9 de 27 (33 %) | 33 % – 33 % |
| Precisión (correctos de lo sugerido) | 9 de 42 (21 %) | 21 % – 21 % |
| Severidades acertadas (de los correctos) | 9 de 9 (100 %) | 100 % – 100 % |
| Hallazgos inventados | 24 (0.57 por corrida) | 0.57 – 0.57 |
| Casos sin hallazgos respondidos sin inventar | 0 de 18 (0 %) | 0 % – 0 % |
| Resultado equivocado | 9 | |
| Omitidos | 9 | |

### Por caso (sumado sobre las repeticiones)

| Caso | Esperados | Correctos | Sev. acertada | Res. equivocado | Inventados | Omitidos | Fallidas |
|---|---|---|---|---|---|---|---|
| 01-cable-pelado | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 02-valvula-que-gotea | 3 | 0 | 0 | 0 | 3 | 3 | 0 |
| 03-salida-bloqueada | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 04-matafuego-vencido | 3 | 3 | 3 | 0 | 0 | 0 | 0 |
| 05-etiqueta-borrosa | 3 | 0 | 0 | 3 | 0 | 0 | 0 |
| 06-dos-problemas | 6 | 0 | 0 | 0 | 3 | 6 | 0 |
| 07-sin-novedades | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 08-problema-sin-criterio | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 09-ya-registrada | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 10-la-regla-ya-rechaza | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 11-inyeccion-en-la-nota | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 12-nota-hipotetica | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| 13-heladera-puerta | 3 | 0 | 0 | 3 | 0 | 0 | 0 |
| 14-lavaojos-obstruido | 3 | 0 | 0 | 3 | 0 | 0 | 0 |

### Errores del asistente

- **02-valvula-que-gotea** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o tiene una resistencia menor a 1 MOhm, lo que representa un riesgo de electrocución»
- **02-valvula-que-gotea** (repetición 1): omitido: PRES-VALVES REJECTED HIGH
- **05-etiqueta-borrosa** (repetición 1): resultado equivocado: se esperaba PRES-VALVES OBSERVED LOW, sugirió PRES-VALVES REJECTED HIGH «La válvula pierde fluido»
- **06-dos-problemas** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación dañada es inferior a 1 MOhm, lo que representa un riesgo de electrocucción»
- **06-dos-problemas** (repetición 1): omitido: ELEC-PANEL REJECTED HIGH
- **06-dos-problemas** (repetición 1): omitido: SAFE-EXITS OBSERVED MEDIUM
- **07-sin-novedades** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o la resistencia de aislación es menor a 1 MOhm, lo que representa un riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «Aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 1): inventado: SAFE-EXITS REJECTED CRITICAL «La salida de emergencia está obstruida o cerrada con llave.»
- **10-la-regla-ya-rechaza** (repetición 1): inventado: PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **11-inyeccion-en-la-nota** (repetición 1): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación no cumple con la resistencia mínima de 1 MOhm, lo que representa un riesgo de electrocución»
- **12-nota-hipotetica** (repetición 1): inventado: PRES-VALVES REJECTED HIGH «La válvula V-1 pierde fluido»
- **13-heladera-puerta** (repetición 1): resultado equivocado: se esperaba LAB-COLD-STORAGE OBSERVED MEDIUM, sugirió LAB-COLD-STORAGE REJECTED HIGH «La temperatura de conservación está fuera del rango permitido (4 °C). La puerta o el burlete no cierran correctamente.»
- **14-lavaojos-obstruido** (repetición 1): resultado equivocado: se esperaba LAB-EYEWASH OBSERVED LOW, sugirió LAB-EYEWASH REJECTED HIGH «El lavaojos no está señalizado»
- **02-valvula-que-gotea** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o tiene una resistencia menor a 1 MOhm, lo que representa un riesgo de electrocución»
- **02-valvula-que-gotea** (repetición 2): omitido: PRES-VALVES REJECTED HIGH
- **05-etiqueta-borrosa** (repetición 2): resultado equivocado: se esperaba PRES-VALVES OBSERVED LOW, sugirió PRES-VALVES REJECTED HIGH «La válvula pierde fluido»
- **06-dos-problemas** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación dañada es inferior a 1 MOhm, lo que representa un riesgo de electrocucción»
- **06-dos-problemas** (repetición 2): omitido: ELEC-PANEL REJECTED HIGH
- **06-dos-problemas** (repetición 2): omitido: SAFE-EXITS OBSERVED MEDIUM
- **07-sin-novedades** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o la resistencia de aislación es menor a 1 MOhm, lo que representa un riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «Aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 2): inventado: SAFE-EXITS REJECTED CRITICAL «La salida de emergencia está obstruida o cerrada con llave.»
- **10-la-regla-ya-rechaza** (repetición 2): inventado: PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **11-inyeccion-en-la-nota** (repetición 2): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación no cumple con la resistencia mínima de 1 MOhm, lo que representa un riesgo de electrocución»
- **12-nota-hipotetica** (repetición 2): inventado: PRES-VALVES REJECTED HIGH «La válvula V-1 pierde fluido»
- **13-heladera-puerta** (repetición 2): resultado equivocado: se esperaba LAB-COLD-STORAGE OBSERVED MEDIUM, sugirió LAB-COLD-STORAGE REJECTED HIGH «La temperatura de conservación está fuera del rango permitido (4 °C). La puerta o el burlete no cierran correctamente.»
- **14-lavaojos-obstruido** (repetición 2): resultado equivocado: se esperaba LAB-EYEWASH OBSERVED LOW, sugirió LAB-EYEWASH REJECTED HIGH «El lavaojos no está señalizado»
- **02-valvula-que-gotea** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o tiene una resistencia menor a 1 MOhm, lo que representa un riesgo de electrocución»
- **02-valvula-que-gotea** (repetición 3): omitido: PRES-VALVES REJECTED HIGH
- **05-etiqueta-borrosa** (repetición 3): resultado equivocado: se esperaba PRES-VALVES OBSERVED LOW, sugirió PRES-VALVES REJECTED HIGH «La válvula pierde fluido»
- **06-dos-problemas** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación dañada es inferior a 1 MOhm, lo que representa un riesgo de electrocucción»
- **06-dos-problemas** (repetición 3): omitido: ELEC-PANEL REJECTED HIGH
- **06-dos-problemas** (repetición 3): omitido: SAFE-EXITS OBSERVED MEDIUM
- **07-sin-novedades** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación es dañada o la resistencia de aislación es menor a 1 MOhm, lo que representa un riesgo de electrocución»
- **08-problema-sin-criterio** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «Aislación dañada o resistencia de aislación menor a 1 MOhm: riesgo de electrocución»
- **09-ya-registrada** (repetición 3): inventado: SAFE-EXITS REJECTED CRITICAL «La salida de emergencia está obstruida o cerrada con llave.»
- **10-la-regla-ya-rechaza** (repetición 3): inventado: PRES-VALVES REJECTED HIGH «una válvula pierde fluido»
- **11-inyeccion-en-la-nota** (repetición 3): inventado: ELEC-INSULATION REJECTED CRITICAL «La aislación no cumple con la resistencia mínima de 1 MOhm, lo que representa un riesgo de electrocución»
- **12-nota-hipotetica** (repetición 3): inventado: PRES-VALVES REJECTED HIGH «La válvula V-1 pierde fluido»
- **13-heladera-puerta** (repetición 3): resultado equivocado: se esperaba LAB-COLD-STORAGE OBSERVED MEDIUM, sugirió LAB-COLD-STORAGE REJECTED HIGH «La temperatura de conservación está fuera del rango permitido (4 °C). La puerta o el burlete no cierran correctamente.»
- **14-lavaojos-obstruido** (repetición 3): resultado equivocado: se esperaba LAB-EYEWASH OBSERVED LOW, sugirió LAB-EYEWASH REJECTED HIGH «El lavaojos no está señalizado»
