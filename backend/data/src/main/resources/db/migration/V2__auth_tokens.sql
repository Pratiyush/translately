-- ============================================================================
-- V2__auth_tokens.sql
--
-- Second migration. Adds the three single-use token tables that back the
-- email+password auth flow (T103):
--
--   * refresh_tokens          — rotated on every call to POST /auth/refresh;
--                               the `jti` is the stable handle embedded in
--                               the JWT, and we refuse to re-consume it.
--   * email_verification_tokens — one row per pending "verify your email"
--                                 link; single-use.
--   * password_reset_tokens   — one row per pending "reset password" link;
--                               single-use.
--
-- Token SECRETS are never stored in plaintext. For refresh tokens we store
-- only the JWT `jti` (not the JWT itself), which is safe: the JWT is signed,
-- so the signature check + jti lookup form a single logical gate. For the
-- email-verification and password-reset flows we store an Argon2id hash
-- (via PasswordHasher) of the raw token body so a DB dump can't be
-- replayed against the live server.
--
-- Conventions match V1 (see V1__auth_and_orgs.sql header).
-- ============================================================================

-- ---- refresh_tokens ------------------------------------------------------
CREATE TABLE refresh_tokens (
    id           BIGSERIAL    PRIMARY KEY,
    external_id  CHAR(26)     NOT NULL,
    user_id      BIGINT       NOT NULL,
    jti          VARCHAR(64)  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_refresh_tokens_external_id UNIQUE (external_id),
    CONSTRAINT uk_refresh_tokens_jti         UNIQUE (jti),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);


-- ---- email_verification_tokens ------------------------------------------
CREATE TABLE email_verification_tokens (
    id           BIGSERIAL    PRIMARY KEY,
    external_id  CHAR(26)     NOT NULL,
    user_id      BIGINT       NOT NULL,
    token_hash   VARCHAR(256) NOT NULL,
    consumed_at  TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_email_verif_tokens_external_id UNIQUE (external_id),
    CONSTRAINT uk_email_verif_tokens_token_hash  UNIQUE (token_hash),
    CONSTRAINT fk_email_verif_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verif_tokens_user ON email_verification_tokens (user_id);


-- ---- password_reset_tokens ----------------------------------------------
CREATE TABLE password_reset_tokens (
    id           BIGSERIAL    PRIMARY KEY,
    external_id  CHAR(26)     NOT NULL,
    user_id      BIGINT       NOT NULL,
    token_hash   VARCHAR(256) NOT NULL,
    consumed_at  TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_password_reset_tokens_external_id UNIQUE (external_id),
    CONSTRAINT uk_password_reset_tokens_token_hash  UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
