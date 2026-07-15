-- Banking app — full database schema (PostgreSQL 16).
-- Generated with: pg_dump --schema-only --no-owner --no-privileges
-- This is a REFERENCE snapshot of the schema produced by Flyway migrations
-- V1..V14 (see src/main/resources/db/migration). Flyway remains the source of
-- truth; do not apply this file directly to a managed database.

--
-- PostgreSQL database dump
--

\restrict C4aviZ5kZAKSeJezLuh8HmXt3bb2JV31KXd2FYDE8DxxhIrOwR6tVr9vkzqHq4T

-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.account (
    id bigint NOT NULL,
    account_number character varying(34) NOT NULL,
    customer_id bigint,
    account_type character varying(20) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    balance numeric(19,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_account_balance_nonneg CHECK ((((account_type)::text = 'SYSTEM'::text) OR (balance >= (0)::numeric))),
    CONSTRAINT chk_account_owner CHECK ((((account_type)::text = 'SYSTEM'::text) OR (customer_id IS NOT NULL))),
    CONSTRAINT chk_account_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'FROZEN'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT chk_account_type CHECK (((account_type)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'SYSTEM'::character varying])::text[])))
);


--
-- Name: account_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.account ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: credit_application; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.credit_application (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    banker_id bigint,
    product_code character varying(30) NOT NULL,
    amount numeric(19,2) NOT NULL,
    term_months integer NOT NULL,
    annual_interest_rate numeric(9,6) NOT NULL,
    monthly_income numeric(19,2) NOT NULL,
    profession character varying(120) NOT NULL,
    employment_months integer NOT NULL,
    disbursement_account_id bigint,
    monthly_installment numeric(19,2),
    status character varying(20) DEFAULT 'SUBMITTED'::character varying NOT NULL,
    decision_reason character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    decided_at timestamp with time zone,
    disbursed_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_credit_application_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT chk_credit_application_status CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT chk_credit_application_term CHECK ((term_months >= 1))
);


--
-- Name: credit_application_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.credit_application ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.credit_application_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: credit_product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.credit_product (
    code character varying(30) NOT NULL,
    name character varying(120) NOT NULL,
    annual_interest_rate numeric(9,6) NOT NULL,
    min_amount numeric(19,2) NOT NULL,
    max_amount numeric(19,2) NOT NULL,
    max_term_months integer NOT NULL,
    CONSTRAINT chk_credit_product_amounts CHECK (((min_amount > (0)::numeric) AND (max_amount >= min_amount))),
    CONSTRAINT chk_credit_product_rate CHECK ((annual_interest_rate >= (0)::numeric)),
    CONSTRAINT chk_credit_product_term CHECK ((max_term_months >= 1))
);


--
-- Name: customer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer (
    id bigint NOT NULL,
    full_name character varying(200) NOT NULL,
    email character varying(320) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    password_hash character varying(100) NOT NULL,
    role character varying(20) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    CONSTRAINT chk_customer_role CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'BANKER'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT chk_customer_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: customer_banker_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_banker_assignment (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    banker_id bigint NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: customer_banker_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.customer_banker_assignment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.customer_banker_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: customer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.customer ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.customer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: idempotency_key; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_key (
    id bigint NOT NULL,
    idempotency_key character varying(80) NOT NULL,
    operation_type character varying(20) NOT NULL,
    request_hash character varying(64) NOT NULL,
    operation_id uuid NOT NULL,
    response_payload jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_idempotency_operation_type CHECK (((operation_type)::text = ANY ((ARRAY['DEPOSIT'::character varying, 'WITHDRAWAL'::character varying, 'TRANSFER'::character varying, 'CREDIT_DISBURSEMENT'::character varying])::text[])))
);


--
-- Name: idempotency_key_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.idempotency_key ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.idempotency_key_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ledger_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ledger_entry (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    operation_id uuid NOT NULL,
    operation_type character varying(20) NOT NULL,
    direction character varying(6) NOT NULL,
    amount numeric(19,2) NOT NULL,
    balance_after numeric(19,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_ledger_amount_positive CHECK ((amount > (0)::numeric)),
    CONSTRAINT chk_ledger_direction CHECK (((direction)::text = ANY ((ARRAY['DEBIT'::character varying, 'CREDIT'::character varying])::text[]))),
    CONSTRAINT chk_ledger_operation_type CHECK (((operation_type)::text = ANY ((ARRAY['DEPOSIT'::character varying, 'WITHDRAWAL'::character varying, 'TRANSFER'::character varying, 'CREDIT_DISBURSEMENT'::character varying])::text[])))
);


--
-- Name: ledger_entry_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ledger_entry ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ledger_entry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: operation_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operation_log (
    id bigint NOT NULL,
    operation_id uuid NOT NULL,
    operation_type character varying(20) NOT NULL,
    idempotency_key character varying(80) NOT NULL,
    primary_account_id bigint NOT NULL,
    counter_account_id bigint,
    amount numeric(19,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_operation_log_amount_positive CHECK ((amount > (0)::numeric)),
    CONSTRAINT chk_operation_log_type CHECK (((operation_type)::text = ANY ((ARRAY['DEPOSIT'::character varying, 'WITHDRAWAL'::character varying, 'TRANSFER'::character varying, 'CREDIT_DISBURSEMENT'::character varying])::text[])))
);


--
-- Name: operation_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.operation_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.operation_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: account account_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT account_pkey PRIMARY KEY (id);


--
-- Name: credit_application credit_application_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_application
    ADD CONSTRAINT credit_application_pkey PRIMARY KEY (id);


--
-- Name: credit_product credit_product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_product
    ADD CONSTRAINT credit_product_pkey PRIMARY KEY (code);


--
-- Name: customer_banker_assignment customer_banker_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_banker_assignment
    ADD CONSTRAINT customer_banker_assignment_pkey PRIMARY KEY (id);


--
-- Name: customer customer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer
    ADD CONSTRAINT customer_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: idempotency_key idempotency_key_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_key
    ADD CONSTRAINT idempotency_key_pkey PRIMARY KEY (id);


--
-- Name: ledger_entry ledger_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entry
    ADD CONSTRAINT ledger_entry_pkey PRIMARY KEY (id);


--
-- Name: operation_log operation_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_log
    ADD CONSTRAINT operation_log_pkey PRIMARY KEY (id);


--
-- Name: account uq_account_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT uq_account_number UNIQUE (account_number);


--
-- Name: customer_banker_assignment uq_cba_customer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_banker_assignment
    ADD CONSTRAINT uq_cba_customer UNIQUE (customer_id);


--
-- Name: customer uq_customer_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer
    ADD CONSTRAINT uq_customer_email UNIQUE (email);


--
-- Name: idempotency_key uq_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_key
    ADD CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key);


--
-- Name: operation_log uq_operation_log_operation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_log
    ADD CONSTRAINT uq_operation_log_operation UNIQUE (operation_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_account_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_account_customer ON public.account USING btree (customer_id);


--
-- Name: idx_cba_banker; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cba_banker ON public.customer_banker_assignment USING btree (banker_id);


--
-- Name: idx_credit_application_banker_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credit_application_banker_status ON public.credit_application USING btree (banker_id, status);


--
-- Name: idx_credit_application_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credit_application_customer ON public.credit_application USING btree (customer_id);


--
-- Name: idx_ledger_account_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ledger_account_created ON public.ledger_entry USING btree (account_id, created_at DESC, id DESC);


--
-- Name: idx_ledger_operation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ledger_operation ON public.ledger_entry USING btree (operation_id);


--
-- Name: idx_operation_log_primary_account; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_operation_log_primary_account ON public.operation_log USING btree (primary_account_id, created_at DESC, id DESC);


--
-- Name: account account_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT account_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer(id);


--
-- Name: credit_application credit_application_banker_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_application
    ADD CONSTRAINT credit_application_banker_id_fkey FOREIGN KEY (banker_id) REFERENCES public.customer(id);


--
-- Name: credit_application credit_application_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_application
    ADD CONSTRAINT credit_application_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer(id);


--
-- Name: credit_application credit_application_disbursement_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_application
    ADD CONSTRAINT credit_application_disbursement_account_id_fkey FOREIGN KEY (disbursement_account_id) REFERENCES public.account(id);


--
-- Name: credit_application credit_application_product_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_application
    ADD CONSTRAINT credit_application_product_code_fkey FOREIGN KEY (product_code) REFERENCES public.credit_product(code);


--
-- Name: customer_banker_assignment customer_banker_assignment_banker_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_banker_assignment
    ADD CONSTRAINT customer_banker_assignment_banker_id_fkey FOREIGN KEY (banker_id) REFERENCES public.customer(id);


--
-- Name: customer_banker_assignment customer_banker_assignment_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_banker_assignment
    ADD CONSTRAINT customer_banker_assignment_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer(id);


--
-- Name: ledger_entry ledger_entry_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entry
    ADD CONSTRAINT ledger_entry_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.account(id);


--
-- Name: operation_log operation_log_counter_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_log
    ADD CONSTRAINT operation_log_counter_account_id_fkey FOREIGN KEY (counter_account_id) REFERENCES public.account(id);


--
-- Name: operation_log operation_log_primary_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_log
    ADD CONSTRAINT operation_log_primary_account_id_fkey FOREIGN KEY (primary_account_id) REFERENCES public.account(id);


--
-- PostgreSQL database dump complete
--

\unrestrict C4aviZ5kZAKSeJezLuh8HmXt3bb2JV31KXd2FYDE8DxxhIrOwR6tVr9vkzqHq4T

