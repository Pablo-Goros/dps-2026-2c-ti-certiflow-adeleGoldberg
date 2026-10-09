-- Transactional outbox. A domain event is written here in the same transaction as the change that
-- raised it, and delivered to its handlers afterwards, so a failing handler can neither lose the
-- event nor roll back the change.
--   PENDING: waiting to be delivered (or retried)   DONE: delivered   DEAD: gave up after too many attempts
CREATE TABLE domain_event_outbox (
    seq        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(300)  NOT NULL,
    status     VARCHAR(10)   NOT NULL,
    attempts   INT           NOT NULL,
    last_error VARCHAR(1000),
    created_at BIGINT        NOT NULL,
    doc        CLOB          NOT NULL
);
CREATE INDEX ix_outbox_status ON domain_event_outbox (status, seq);
