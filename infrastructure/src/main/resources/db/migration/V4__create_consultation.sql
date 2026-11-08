-- Consultations of the inspection assistant: what it was asked, what it suggested and what the
-- inspector decided. Same shape as the other aggregate tables; the document is the source of truth.
CREATE TABLE consultation (
    seq           BIGINT GENERATED ALWAYS AS IDENTITY,
    id            VARCHAR(200) NOT NULL PRIMARY KEY,
    row_version   BIGINT       NOT NULL,
    inspection_id VARCHAR(200) NOT NULL,
    doc           CLOB         NOT NULL,
    CONSTRAINT fk_consultation_inspection FOREIGN KEY (inspection_id) REFERENCES inspection (id)
);
CREATE INDEX ix_consultation_inspection ON consultation (inspection_id);
