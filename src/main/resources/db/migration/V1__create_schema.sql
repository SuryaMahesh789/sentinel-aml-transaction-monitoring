-- =============================================================================
-- V1__create_schema.sql
-- Sentinel AML — Core Schema
-- =============================================================================

-- -----------------------------------------------------------------------------
-- customers
-- -----------------------------------------------------------------------------
CREATE TABLE customers (
    id                   BIGSERIAL PRIMARY KEY,
    customer_id          VARCHAR(50)    NOT NULL UNIQUE,
    first_name           VARCHAR(100)   NOT NULL,
    last_name            VARCHAR(100)   NOT NULL,
    gender               VARCHAR(10),
    date_of_birth        DATE,
    email                VARCHAR(150),
    phone_number         VARCHAR(30),
    city                 VARCHAR(100),
    state                VARCHAR(100),
    country              VARCHAR(10),
    postal_code          VARCHAR(20),
    occupation           VARCHAR(150),
    annual_income        NUMERIC(18,2),
    customer_segment     VARCHAR(50),
    kyc_status           VARCHAR(20)    CHECK (kyc_status IN ('VERIFIED','PENDING','FAILED','EXPIRED')),
    risk_rating          VARCHAR(20)    CHECK (risk_rating IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    is_politically_exposed BOOLEAN      NOT NULL DEFAULT FALSE,
    customer_since       DATE,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_customer_id ON customers(customer_id);
CREATE INDEX idx_customers_risk_rating ON customers(risk_rating);

-- -----------------------------------------------------------------------------
-- accounts
-- -----------------------------------------------------------------------------
CREATE TABLE accounts (
    id                      BIGSERIAL PRIMARY KEY,
    account_id              VARCHAR(50)    NOT NULL UNIQUE,
    customer_id             BIGINT         NOT NULL REFERENCES customers(id),
    account_type            VARCHAR(30)    NOT NULL,
    account_status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                                CHECK (account_status IN ('ACTIVE','INACTIVE','FROZEN','CLOSED')),
    currency                VARCHAR(10)    NOT NULL DEFAULT 'INR',
    open_date               DATE,
    close_date              DATE,
    branch_code             VARCHAR(20),
    branch_city             VARCHAR(100),
    current_balance         NUMERIC(18,2)  DEFAULT 0.00,
    avg_monthly_balance_6m  NUMERIC(18,2)  DEFAULT 0.00,
    avg_monthly_txn_count   INTEGER        DEFAULT 0,
    risk_rating             VARCHAR(20)    CHECK (risk_rating IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_account_id    ON accounts(account_id);
CREATE INDEX idx_accounts_customer_id   ON accounts(customer_id);

-- -----------------------------------------------------------------------------
-- transactions
-- -----------------------------------------------------------------------------
CREATE TABLE transactions (
    id                       BIGSERIAL PRIMARY KEY,
    transaction_ref          VARCHAR(80)    NOT NULL UNIQUE,
    source_account_id        BIGINT         NOT NULL REFERENCES accounts(id),
    destination_account_ref  VARCHAR(80),
    customer_id              BIGINT         NOT NULL REFERENCES customers(id),
    amount                   NUMERIC(18,2)  NOT NULL,
    currency                 VARCHAR(10)    NOT NULL,
    amount_inr               NUMERIC(18,2),
    transaction_type         VARCHAR(20)    NOT NULL
                                CHECK (transaction_type IN (
                                    'DEPOSIT','WITHDRAWAL','TRANSFER','PAYMENT',
                                    'REMITTANCE','CASH_DEPOSIT','CASH_WITHDRAWAL')),
    channel                  VARCHAR(30)
                                CHECK (channel IN ('ONLINE','BRANCH','ATM','MOBILE','POS','WIRE','INTERNAL')),
    counterparty_name        VARCHAR(200),
    counterparty_account     VARCHAR(80),
    counterparty_bank        VARCHAR(100),
    jurisdiction             VARCHAR(5),
    transaction_timestamp    TIMESTAMP      NOT NULL,
    description              VARCHAR(500),
    status                   VARCHAR(20)    NOT NULL DEFAULT 'COMPLETED'
                                CHECK (status IN ('PENDING','COMPLETED','FAILED','REVERSED')),
    created_at               TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_source_account   ON transactions(source_account_id);
CREATE INDEX idx_txn_timestamp        ON transactions(transaction_timestamp);
CREATE INDEX idx_txn_customer_id      ON transactions(customer_id);
CREATE INDEX idx_txn_jurisdiction     ON transactions(jurisdiction);
CREATE INDEX idx_txn_type_timestamp   ON transactions(transaction_type, transaction_timestamp);

-- -----------------------------------------------------------------------------
-- alert_rules  (configurable — no code redeployment needed)
-- -----------------------------------------------------------------------------
CREATE TABLE alert_rules (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(50)    NOT NULL UNIQUE,
    rule_name       VARCHAR(100)   NOT NULL,
    description     VARCHAR(500),
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    base_risk_score INTEGER        NOT NULL DEFAULT 50
                        CHECK (base_risk_score BETWEEN 0 AND 100),
    parameters      TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- alerts
-- -----------------------------------------------------------------------------
CREATE TABLE alerts (
    id                       BIGSERIAL PRIMARY KEY,
    alert_ref                VARCHAR(80)    NOT NULL UNIQUE,
    customer_id              BIGINT         NOT NULL REFERENCES customers(id),
    account_id               BIGINT         REFERENCES accounts(id),
    transaction_id           BIGINT         REFERENCES transactions(id),
    rule_id                  BIGINT         NOT NULL REFERENCES alert_rules(id),
    risk_score               INTEGER        NOT NULL DEFAULT 0
                                CHECK (risk_score BETWEEN 0 AND 100),
    explanation              TEXT           NOT NULL,
    evidence_transaction_ids TEXT,
    status                   VARCHAR(20)    NOT NULL DEFAULT 'OPEN'
                                CHECK (status IN (
                                    'OPEN','UNDER_REVIEW','ESCALATED',
                                    'CLEARED','SAR_FILED','CLOSED')),
    reviewed_by              VARCHAR(100),
    reviewed_at              TIMESTAMP,
    clearance_reason         VARCHAR(500),
    created_at               TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_customer    ON alerts(customer_id);
CREATE INDEX idx_alert_status      ON alerts(status);
CREATE INDEX idx_alert_risk_score  ON alerts(risk_score DESC);

-- -----------------------------------------------------------------------------
-- cases
-- -----------------------------------------------------------------------------
CREATE TABLE cases (
    id              BIGSERIAL PRIMARY KEY,
    case_ref        VARCHAR(80)    NOT NULL UNIQUE,
    customer_id     BIGINT         NOT NULL REFERENCES customers(id),
    title           VARCHAR(200)   NOT NULL,
    description     TEXT,
    status          VARCHAR(30)    NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN (
                            'OPEN','IN_PROGRESS','PENDING_REVIEW',
                            'ESCALATED','CLOSED_SAR','CLOSED_NO_ACTION')),
    priority        VARCHAR(20)    CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    assigned_to     VARCHAR(100),
    assigned_at     TIMESTAMP,
    closed_at       TIMESTAMP,
    closure_notes   TEXT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cases_customer_id ON cases(customer_id);
CREATE INDEX idx_cases_status      ON cases(status);
CREATE INDEX idx_cases_assigned_to ON cases(assigned_to);

-- -----------------------------------------------------------------------------
-- case_alerts  (many-to-many join)
-- -----------------------------------------------------------------------------
CREATE TABLE case_alerts (
    id         BIGSERIAL PRIMARY KEY,
    case_id    BIGINT NOT NULL REFERENCES cases(id),
    alert_id   BIGINT NOT NULL REFERENCES alerts(id),
    linked_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (case_id, alert_id)
);

-- -----------------------------------------------------------------------------
-- audit_logs  (immutable — INSERT only)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(30)    NOT NULL,
    entity_id       BIGINT         NOT NULL,
    action          VARCHAR(50)    NOT NULL,
    previous_value  VARCHAR(100),
    new_value       VARCHAR(100),
    actor           VARCHAR(100)   NOT NULL,
    notes           TEXT,
    timestamp       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity  ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_actor   ON audit_logs(actor);
CREATE INDEX idx_audit_ts      ON audit_logs(timestamp);

-- -----------------------------------------------------------------------------
-- exchange_rates
-- -----------------------------------------------------------------------------
CREATE TABLE exchange_rates (
    id              BIGSERIAL PRIMARY KEY,
    from_currency   VARCHAR(10)    NOT NULL,
    to_currency     VARCHAR(10)    NOT NULL DEFAULT 'INR',
    rate            NUMERIC(18,6)  NOT NULL,
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    effective_from  TIMESTAMP      NOT NULL,
    effective_to    TIMESTAMP,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_exchange_currency_active ON exchange_rates(from_currency, is_active);

-- -----------------------------------------------------------------------------
-- high_risk_jurisdictions
-- -----------------------------------------------------------------------------
CREATE TABLE high_risk_jurisdictions (
    id           BIGSERIAL PRIMARY KEY,
    country_code VARCHAR(5)     NOT NULL UNIQUE,
    country_name VARCHAR(100)   NOT NULL,
    risk_level   VARCHAR(20)    NOT NULL CHECK (risk_level IN ('HIGH','SANCTIONED')),
    reason       VARCHAR(300),
    is_active    BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);
