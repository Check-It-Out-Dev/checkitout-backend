-- liquibase formatted sql
-- changeset system:create-company-data-table

-- ============================================================================
-- COMPANY DATA TABLE: Verified company registry data from GUS/VAT/CEIDG
-- One row per COMPANY user. Source of truth for NIP and all business identity.
-- ============================================================================

-- Sequence for Hibernate pooled-lo optimizer
CREATE SEQUENCE IF NOT EXISTS company_data_seq START WITH 1 INCREMENT BY 50 CACHE 50;

CREATE TABLE IF NOT EXISTS public.company_data (
    id                      BIGINT PRIMARY KEY DEFAULT nextval('company_data_seq'),
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES "user"(id) ON DELETE CASCADE,
    nip                     VARCHAR(10) NOT NULL UNIQUE,
    regon                   VARCHAR(14),
    krs                     VARCHAR(10),
    company_name            VARCHAR(500) NOT NULL,
    company_type            VARCHAR(30) NOT NULL CHECK (company_type IN (
        'JDG', 'SP_ZOO', 'SA', 'SP_K', 'SP_J', 'OTHER_KRS'
    )),
    legal_form_code         VARCHAR(10),
    legal_form_name         VARCHAR(255),
    registered_address      JSONB,
    correspondence_address  JSONB,
    pkd_codes               JSONB,
    vat_status              VARCHAR(30) CHECK (vat_status IN (
        'ACTIVE', 'EXEMPT', 'DEREGISTERED', 'UNREGISTERED'
    )),
    bank_accounts           JSONB,
    owner_name              VARCHAR(255),
    registry_data_fetched_at TIMESTAMP,
    data_verified           BOOLEAN NOT NULL DEFAULT FALSE,
    source_gus              BOOLEAN NOT NULL DEFAULT FALSE,
    source_vat              BOOLEAN NOT NULL DEFAULT FALSE,
    source_ceidg            BOOLEAN NOT NULL DEFAULT FALSE,
    raw_gus_response        JSONB,
    created_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT NOT NULL DEFAULT 0
);

-- Index for user lookups (user_id already has UNIQUE constraint which creates an index)
-- Index for NIP lookups (nip already has UNIQUE constraint which creates an index)

-- Index for querying by company type
CREATE INDEX IF NOT EXISTS idx_company_data_company_type
    ON public.company_data (company_type);

-- Index for querying verified companies
CREATE INDEX IF NOT EXISTS idx_company_data_verified
    ON public.company_data (data_verified)
    WHERE data_verified = TRUE;

COMMENT ON TABLE company_data IS 'Verified company registry data fetched from Polish public registries (GUS BIR1, Biala Lista, CEIDG). One row per COMPANY user. Source of truth for NIP.';
COMMENT ON COLUMN company_data.nip IS 'Polish Tax Identification Number (NIP) - 10 digits, unique per company';
COMMENT ON COLUMN company_data.registered_address IS 'JSONB: {street, building, apartment, city, postalCode, voivodeship}';
COMMENT ON COLUMN company_data.correspondence_address IS 'JSONB: user-provided correspondence address, nullable';
COMMENT ON COLUMN company_data.pkd_codes IS 'JSONB: array of {code, description, isPrimary}';
COMMENT ON COLUMN company_data.bank_accounts IS 'JSONB: array of registered bank account numbers from Biala Lista';
COMMENT ON COLUMN company_data.raw_gus_response IS 'JSONB: full GUS BIR1 response for audit trail';

-- rollback DROP TABLE IF EXISTS company_data CASCADE;
-- rollback DROP SEQUENCE IF EXISTS company_data_seq;
