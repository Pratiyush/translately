-- ============================================================================
-- V3__keys_translations_icu.sql
--
-- Third migration. Adds the Phase 2 data model — translation keys,
-- namespaces, tags, translations, per-key comments and activity log.
-- Matches the JPA entities shipped in io.translately.data.entity.*
-- (Key, KeyMeta, Namespace, Tag, Translation, Comment, Activity).
--
-- Conventions match V1 / V2 (see V1__auth_and_orgs.sql header).
--
-- All child tables CASCADE on parent delete. Soft-delete is opt-in per
-- entity — the `keys` table carries `soft_deleted_at`; dependents
-- follow the hard delete cascade and are restored together if a key is
-- un-soft-deleted within the retention window.
-- ============================================================================

-- ---- namespaces ----------------------------------------------------------
CREATE TABLE namespaces (
    id           BIGSERIAL    PRIMARY KEY,
    external_id  CHAR(26)     NOT NULL,
    project_id   BIGINT       NOT NULL,
    slug         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(1024),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_namespaces_external_id   UNIQUE (external_id),
    CONSTRAINT uk_namespaces_project_slug  UNIQUE (project_id, slug),
    CONSTRAINT fk_namespaces_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_namespaces_project ON namespaces (project_id);


-- ---- tags ----------------------------------------------------------------
CREATE TABLE tags (
    id           BIGSERIAL    PRIMARY KEY,
    external_id  CHAR(26)     NOT NULL,
    project_id   BIGINT       NOT NULL,
    slug         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    color        VARCHAR(7),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tags_external_id   UNIQUE (external_id),
    CONSTRAINT uk_tags_project_slug  UNIQUE (project_id, slug),
    CONSTRAINT fk_tags_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_tags_project ON tags (project_id);


-- ---- keys ----------------------------------------------------------------
CREATE TABLE keys (
    id                BIGSERIAL    PRIMARY KEY,
    external_id       CHAR(26)     NOT NULL,
    project_id        BIGINT       NOT NULL,
    namespace_id      BIGINT       NOT NULL,
    key_name          VARCHAR(256) NOT NULL,
    description       VARCHAR(1024),
    state             VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    soft_deleted_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_keys_external_id            UNIQUE (external_id),
    CONSTRAINT uk_keys_project_namespace_name UNIQUE (project_id, namespace_id, key_name),
    CONSTRAINT fk_keys_project
        FOREIGN KEY (project_id)   REFERENCES projects   (id) ON DELETE CASCADE,
    CONSTRAINT fk_keys_namespace
        FOREIGN KEY (namespace_id) REFERENCES namespaces (id) ON DELETE CASCADE,
    CONSTRAINT ck_keys_state
        CHECK (state IN ('NEW', 'TRANSLATING', 'REVIEW', 'DONE'))
);

CREATE INDEX idx_keys_project   ON keys (project_id);
CREATE INDEX idx_keys_namespace ON keys (namespace_id);
CREATE INDEX idx_keys_state     ON keys (state);


-- ---- key_meta (side-table of arbitrary key/value metadata) ---------------
CREATE TABLE key_meta (
    id           BIGSERIAL     PRIMARY KEY,
    external_id  CHAR(26)      NOT NULL,
    key_id       BIGINT        NOT NULL,
    meta_key     VARCHAR(128)  NOT NULL,
    meta_value   VARCHAR(4096) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_key_meta_external_id   UNIQUE (external_id),
    CONSTRAINT uk_key_meta_key_meta_key  UNIQUE (key_id, meta_key),
    CONSTRAINT fk_key_meta_key
        FOREIGN KEY (key_id) REFERENCES keys (id) ON DELETE CASCADE
);

CREATE INDEX idx_key_meta_key ON key_meta (key_id);


-- ---- key_tags (many-to-many join, keys ⇄ tags) ---------------------------
CREATE TABLE key_tags (
    key_id  BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,

    CONSTRAINT uk_key_tags_key_tag UNIQUE (key_id, tag_id),
    CONSTRAINT fk_key_tags_key
        FOREIGN KEY (key_id) REFERENCES keys (id) ON DELETE CASCADE,
    CONSTRAINT fk_key_tags_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE INDEX idx_key_tags_key ON key_tags (key_id);
CREATE INDEX idx_key_tags_tag ON key_tags (tag_id);


-- ---- translations --------------------------------------------------------
CREATE TABLE translations (
    id              BIGSERIAL   PRIMARY KEY,
    external_id     CHAR(26)    NOT NULL,
    key_id          BIGINT      NOT NULL,
    language_tag    VARCHAR(32) NOT NULL,
    value           TEXT        NOT NULL DEFAULT '',
    state           VARCHAR(16) NOT NULL DEFAULT 'EMPTY',
    author_user_id  BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_translations_external_id   UNIQUE (external_id),
    CONSTRAINT uk_translations_key_language  UNIQUE (key_id, language_tag),
    CONSTRAINT fk_translations_key
        FOREIGN KEY (key_id)         REFERENCES keys  (id) ON DELETE CASCADE,
    CONSTRAINT fk_translations_author
        FOREIGN KEY (author_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_translations_state
        CHECK (state IN ('EMPTY', 'DRAFT', 'TRANSLATED', 'REVIEW', 'APPROVED'))
);

CREATE INDEX idx_translations_key   ON translations (key_id);
CREATE INDEX idx_translations_state ON translations (state);


-- ---- key_comments --------------------------------------------------------
CREATE TABLE key_comments (
    id              BIGSERIAL   PRIMARY KEY,
    external_id     CHAR(26)    NOT NULL,
    key_id          BIGINT      NOT NULL,
    author_user_id  BIGINT      NOT NULL,
    body            TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_key_comments_external_id UNIQUE (external_id),
    CONSTRAINT fk_key_comments_key
        FOREIGN KEY (key_id)         REFERENCES keys  (id) ON DELETE CASCADE,
    CONSTRAINT fk_key_comments_author
        FOREIGN KEY (author_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_key_comments_key    ON key_comments (key_id);
CREATE INDEX idx_key_comments_author ON key_comments (author_user_id);


-- ---- key_activity (append-only audit trail) -----------------------------
CREATE TABLE key_activity (
    id              BIGSERIAL   PRIMARY KEY,
    external_id     CHAR(26)    NOT NULL,
    key_id          BIGINT      NOT NULL,
    actor_user_id   BIGINT,
    action_type     VARCHAR(24) NOT NULL,
    diff_json       JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_key_activity_external_id UNIQUE (external_id),
    CONSTRAINT fk_key_activity_key
        FOREIGN KEY (key_id)        REFERENCES keys  (id) ON DELETE CASCADE,
    CONSTRAINT fk_key_activity_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_key_activity_action_type
        CHECK (action_type IN (
            'CREATED', 'UPDATED', 'DELETED', 'STATE_CHANGED',
            'TRANSLATED', 'COMMENTED', 'TAGGED'
        ))
);

CREATE INDEX idx_key_activity_key     ON key_activity (key_id);
CREATE INDEX idx_key_activity_actor   ON key_activity (actor_user_id);
CREATE INDEX idx_key_activity_created ON key_activity (created_at);
