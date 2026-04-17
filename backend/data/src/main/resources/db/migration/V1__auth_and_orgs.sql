-- ============================================================================
-- V1__auth_and_orgs.sql
--
-- First migration. Creates the identity + organization + project + token
-- tables that back Phase 1 of Translately. Matches the JPA entities shipped
-- in io.translately.data.entity.*.
--
-- Conventions
--   * Table names: plural snake_case.
--   * `id`            -> bigserial primary key, internal only.
--   * `external_id`   -> char(26) ULID, unique, public identifier.
--   * `created_at`    -> timestamptz NOT NULL, maintained by JPA @PrePersist.
--   * `updated_at`    -> timestamptz NOT NULL, maintained by JPA @PreUpdate.
--   * `deleted_at`    -> timestamptz NULL on the soft-deleted tables.
--   * FK names:    fk_<child>_<parent>
--   * Unique names: uk_<table>_<cols>
--   * Index names:  idx_<table>_<cols>
--
-- Tenant scoping (multi-tenancy filter T111) is applied at the application
-- layer via a Hibernate filter on every table that carries `organization_id`
-- directly or transitively.
-- ============================================================================

-- ---- users ---------------------------------------------------------------
CREATE TABLE users (
    id                 BIGSERIAL    PRIMARY KEY,
    external_id        CHAR(26)     NOT NULL,
    email              VARCHAR(254) NOT NULL,
    email_verified_at  TIMESTAMPTZ,
    password_hash      VARCHAR(256),
    full_name          VARCHAR(128) NOT NULL,
    locale             VARCHAR(32)  NOT NULL DEFAULT 'en',
    timezone           VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT uk_users_external_id UNIQUE (external_id),
    CONSTRAINT uk_users_email       UNIQUE (email)
);

CREATE INDEX idx_users_email_verified ON users (email_verified_at) WHERE email_verified_at IS NOT NULL;


-- ---- organizations -------------------------------------------------------
CREATE TABLE organizations (
    id           BIGSERIAL    PRIMARY KEY,
    external_id  CHAR(26)     NOT NULL,
    slug         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,

    CONSTRAINT uk_organizations_external_id UNIQUE (external_id),
    CONSTRAINT uk_organizations_slug        UNIQUE (slug)
);


-- ---- organization_members ------------------------------------------------
CREATE TABLE organization_members (
    id               BIGSERIAL   PRIMARY KEY,
    external_id      CHAR(26)    NOT NULL,
    organization_id  BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    role             VARCHAR(16) NOT NULL,
    invited_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    joined_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_org_members_external_id UNIQUE (external_id),
    CONSTRAINT uk_org_members_org_user    UNIQUE (organization_id, user_id),
    CONSTRAINT fk_org_members_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_members_user
        FOREIGN KEY (user_id)         REFERENCES users         (id) ON DELETE CASCADE,
    CONSTRAINT ck_org_members_role
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_org_members_user ON organization_members (user_id);


-- ---- projects ------------------------------------------------------------
CREATE TABLE projects (
    id                          BIGSERIAL     PRIMARY KEY,
    external_id                 CHAR(26)      NOT NULL,
    organization_id             BIGINT        NOT NULL,
    slug                        VARCHAR(64)   NOT NULL,
    name                        VARCHAR(128)  NOT NULL,
    description                 VARCHAR(1024),
    base_language_tag           VARCHAR(32)   NOT NULL DEFAULT 'en',

    -- BYOK AI (all nullable; platform works without any provider configured)
    ai_provider                 VARCHAR(32),
    ai_model                    VARCHAR(128),
    ai_base_url                 VARCHAR(512),
    ai_api_key_encrypted        BYTEA,
    ai_budget_cap_usd_monthly   NUMERIC(12, 2),

    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ,

    CONSTRAINT uk_projects_external_id UNIQUE (external_id),
    CONSTRAINT uk_projects_org_slug    UNIQUE (organization_id, slug),
    CONSTRAINT fk_projects_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT ck_projects_ai_provider
        CHECK (ai_provider IS NULL OR ai_provider IN ('ANTHROPIC', 'OPENAI', 'OPENAI_COMPATIBLE')),
    CONSTRAINT ck_projects_ai_budget_non_negative
        CHECK (ai_budget_cap_usd_monthly IS NULL OR ai_budget_cap_usd_monthly >= 0)
);

CREATE INDEX idx_projects_organization ON projects (organization_id);


-- ---- project_languages ---------------------------------------------------
CREATE TABLE project_languages (
    id            BIGSERIAL    PRIMARY KEY,
    external_id   CHAR(26)     NOT NULL,
    project_id    BIGINT       NOT NULL,
    language_tag  VARCHAR(32)  NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    direction     VARCHAR(8)   NOT NULL DEFAULT 'LTR',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_project_languages_external_id     UNIQUE (external_id),
    CONSTRAINT uk_project_languages_project_tag     UNIQUE (project_id, language_tag),
    CONSTRAINT fk_project_languages_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_languages_direction
        CHECK (direction IN ('LTR', 'RTL'))
);

CREATE INDEX idx_project_languages_project ON project_languages (project_id);


-- ---- api_keys ------------------------------------------------------------
CREATE TABLE api_keys (
    id             BIGSERIAL    PRIMARY KEY,
    external_id    CHAR(26)     NOT NULL,
    project_id     BIGINT       NOT NULL,
    prefix         VARCHAR(16)  NOT NULL,
    secret_hash    VARCHAR(256) NOT NULL,
    name           VARCHAR(128) NOT NULL,
    scopes         VARCHAR(512) NOT NULL DEFAULT '',
    expires_at     TIMESTAMPTZ,
    last_used_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_api_keys_external_id UNIQUE (external_id),
    CONSTRAINT uk_api_keys_prefix      UNIQUE (prefix),
    CONSTRAINT fk_api_keys_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_api_keys_project ON api_keys (project_id);


-- ---- personal_access_tokens ---------------------------------------------
CREATE TABLE personal_access_tokens (
    id             BIGSERIAL    PRIMARY KEY,
    external_id    CHAR(26)     NOT NULL,
    user_id        BIGINT       NOT NULL,
    prefix         VARCHAR(16)  NOT NULL,
    secret_hash    VARCHAR(256) NOT NULL,
    name           VARCHAR(128) NOT NULL,
    scopes         VARCHAR(512) NOT NULL DEFAULT '',
    expires_at     TIMESTAMPTZ,
    last_used_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_pats_external_id UNIQUE (external_id),
    CONSTRAINT uk_pats_prefix      UNIQUE (prefix),
    CONSTRAINT fk_pats_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_pats_user ON personal_access_tokens (user_id);
