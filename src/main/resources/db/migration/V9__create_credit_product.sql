-- Selectable credit products (loan types). Reference data: the interest rate and the
-- amount/term limits that constrain an application. `code` is the natural key clients
-- refer to; `annual_interest_rate` is a fraction (0.360000 = 36%).
CREATE TABLE credit_product (
    code                 VARCHAR(30)    PRIMARY KEY,
    name                 VARCHAR(120)   NOT NULL,
    annual_interest_rate NUMERIC(9, 6)  NOT NULL,
    min_amount           NUMERIC(19, 2) NOT NULL,
    max_amount           NUMERIC(19, 2) NOT NULL,
    max_term_months      INTEGER        NOT NULL,
    CONSTRAINT chk_credit_product_rate CHECK (annual_interest_rate >= 0),
    CONSTRAINT chk_credit_product_amounts CHECK (min_amount > 0 AND max_amount >= min_amount),
    CONSTRAINT chk_credit_product_term CHECK (max_term_months >= 1)
);

INSERT INTO credit_product (code, name, annual_interest_rate, min_amount, max_amount, max_term_months)
VALUES
    ('IHTIYAC', 'İhtiyaç Kredisi', 0.360000, 1000.00, 500000.00, 36),
    ('TASIT',   'Taşıt Kredisi',   0.300000, 50000.00, 2000000.00, 48);
