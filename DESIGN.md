### Entidades

Persona
- String Nombre
- String Apellido

Enum TipoDeActivo {
    LABORATORIO,
    FABRICA,
    INSTALACIONES,
    EQUIPOS
};
 
Enum TipoEvidencia {
    MEDICION,
    RESPUESTA,
    FOTOGRAFIA,
    DOCUMENTO,
    OBSERVACION
}

Enum TipoResultado {
    APROBADO,
    REVISION,
    DESAPROBADO
}

-------------------------

LABORATORIO [D]
- Favaloro  (ACTIVO)
- Merk      (ACTIVO)
FABRICA [B]

Esquema
- id version
- Date fecha
- List<Seccion> secciones           // ?
- List<Criterio> criterios          // ?
- TipoDeActivo tipoDeActivo

Activo
- TipoDeActivo tipo
- Persona responsable
- Location location
- Caracteresticas caracteristicas

Inspeccion
- Activo activo
- Esquema esquemaUtilizado
- Persona inspector
- Date fechaPrevista
- Map<Criterio, List<Evidencia>> evidenciaPorCriterio
- Map<Criterio, TipoResultado> resultados;

Evidencia
- TipoEvidencia tipo
- DateTime fecha
- Contenido contenido              // ??

Criterio
- Severidad severidad
- ReglaDeEvualuacion (I) regla

Seccion()

// Supuestos:
- Un esquema puede aplicar a distintos tipos de activo
- El output de un criterio se determina a partir de la evidencia (que es el input)
- Hallazgos son solo aquellos rechazados

// Siguientes:
Accions correctivas
Certificación
Auditoría
Informes