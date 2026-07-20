-- A customer's credit application and its decision. Created SUBMITTED and routed to the
-- customer's assigned banker, who moves it to a terminal APPROVED (triggering
-- disbursement to `disbursement_account_id`) or REJECTED. The interest rate and monthly
-- installment are snapshotted at submission so the offer the customer saw does not drift.
CREATE TABLE credit_application (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id              BIGINT         NOT NULL REFERENCES customer (id),
    banker_id                BIGINT         REFERENCES customer (id),
    product_code             VARCHAR(30)    NOT NULL REFERENCES credit_product (code),
    amount                   NUMERIC(19, 2) NOT NULL,
    term_months              INTEGER        NOT NULL,
    annual_interest_rate     NUMERIC(9, 6)  NOT NULL,
    monthly_income           NUMERIC(19, 2) NOT NULL,
    profession               VARCHAR(120)   NOT NULL,
    employment_months        INTEGER        NOT NULL,
    disbursement_account_id  BIGINT         REFERENCES account (id),
    monthly_installment      NUMERIC(19, 2),
    status                   VARCHAR(20)    NOT NULL DEFAULT 'SUBMITTED',
    decision_reason          VARCHAR(500),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    decided_at               TIMESTAMPTZ,
    disbursed_at             TIMESTAMPTZ,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_credit_application_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_credit_application_amount CHECK (amount > 0),
    CONSTRAINT chk_credit_application_term CHECK (term_months >= 1)
);

CREATE INDEX idx_credit_application_customer ON credit_application (customer_id);
CREATE INDEX idx_credit_application_banker_status ON credit_application (banker_id, status);
