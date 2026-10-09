-- One row per aggregate: `doc` holds the whole aggregate (the unit of consistency); the other
-- columns are a projection rewritten on every save so the repositories can filter and order
-- without reading every document. `seq` is the insertion order, `row_version` the optimistic lock.

CREATE TABLE party (
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    id          VARCHAR(200) NOT NULL PRIMARY KEY,
    row_version BIGINT       NOT NULL,
    doc         CLOB         NOT NULL
);

CREATE TABLE asset (
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    id             VARCHAR(200)  NOT NULL PRIMARY KEY,
    row_version    BIGINT        NOT NULL,
    asset_type     VARCHAR(50)   NOT NULL,
    responsible_id VARCHAR(200)  NOT NULL,
    name           VARCHAR(1000) NOT NULL,
    jurisdiction   VARCHAR(200)  NOT NULL,
    doc            CLOB          NOT NULL
);
CREATE INDEX ix_asset_type ON asset (asset_type);
CREATE INDEX ix_asset_responsible ON asset (responsible_id);

CREATE TABLE inspection_schema (
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    id          VARCHAR(200) NOT NULL PRIMARY KEY,
    row_version BIGINT       NOT NULL,
    doc         CLOB         NOT NULL
);

-- The primary key is the asset type: a type is covered by at most one schema.
CREATE TABLE schema_applicability (
    asset_type VARCHAR(50)  NOT NULL PRIMARY KEY,
    schema_id  VARCHAR(200) NOT NULL,
    CONSTRAINT fk_applicability_schema FOREIGN KEY (schema_id) REFERENCES inspection_schema (id)
);

CREATE TABLE inspection (
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    id          VARCHAR(200) NOT NULL PRIMARY KEY,
    row_version BIGINT       NOT NULL,
    asset_id    VARCHAR(200) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    doc         CLOB         NOT NULL
);
CREATE INDEX ix_inspection_asset ON inspection (asset_id, status);

CREATE TABLE finding (
    seq           BIGINT GENERATED ALWAYS AS IDENTITY,
    id            VARCHAR(200) NOT NULL PRIMARY KEY,
    row_version   BIGINT       NOT NULL,
    inspection_id VARCHAR(200) NOT NULL,
    criterion_id  VARCHAR(200) NOT NULL,
    action_open   BOOLEAN      NOT NULL,
    doc           CLOB         NOT NULL
);
CREATE INDEX ix_finding_inspection ON finding (inspection_id, criterion_id);
CREATE INDEX ix_finding_open_action ON finding (action_open);

CREATE TABLE certificate (
    seq                   BIGINT GENERATED ALWAYS AS IDENTITY,
    id                    VARCHAR(200) NOT NULL PRIMARY KEY,
    row_version           BIGINT       NOT NULL,
    asset_id              VARCHAR(200) NOT NULL,
    backing_inspection_id VARCHAR(200) NOT NULL,
    scope_key             VARCHAR(300) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    expires_at_ms         BIGINT       NOT NULL,
    doc                   CLOB         NOT NULL
);
-- At most one certificate of each scope per inspection, even under concurrent issuance.
CREATE UNIQUE INDEX ux_certificate_inspection_scope ON certificate (backing_inspection_id, scope_key);
CREATE INDEX ix_certificate_asset_scope ON certificate (asset_id, scope_key);
CREATE INDEX ix_certificate_expiry ON certificate (status, expires_at_ms);

-- Append-only record of every audited operation.
CREATE TABLE audit_entry (
    seq          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    element_type VARCHAR(30)  NOT NULL,
    element_id   VARCHAR(200) NOT NULL,
    action       VARCHAR(60)  NOT NULL,
    doc          CLOB         NOT NULL
);
CREATE INDEX ix_audit_element ON audit_entry (element_type, element_id);
