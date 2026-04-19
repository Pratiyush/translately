-- ============================================================================
-- V4__keys_fts_trigram.sql
--
-- Fourth migration. Wires the Postgres-native search surface that backs
-- T206 (key search + tag filter) and primes T207's filter UX:
--
--   * Enables the `pg_trgm` extension (ships with Postgres 16 core).
--   * Adds a generated `tsvector` column on `keys` so full-text indexing
--     stays in sync with `key_name` + `description` without triggers.
--   * Indexes that column with GIN for exact-ish FTS hits.
--   * Adds a GIN trigram index on `translations.value` so a fuzzy match
--     can fall back to translation bodies when FTS finds nothing on the
--     key side.
--
-- Text-search configuration is deliberately `simple` (no stemming, no
-- language-specific stopwords). Rationale: Translately is multilingual by
-- design, and callers search for keys like "settings.save.button" where
-- English stemming would merge unrelated tokens. A future migration can
-- switch specific columns to a language-tag-aware configuration once the
-- UX calls for it — the generated-column approach keeps that change
-- contained to a single ALTER.
--
-- Conventions match V1 / V2 / V3 (see V1__auth_and_orgs.sql header).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- ---- keys.search_vector -------------------------------------------------
-- Generated-column keeps the tsvector in lock-step with key_name /
-- description — no trigger, no app-side bookkeeping, no drift.
--
-- `key_name` is stored in its original dotted form ("settings.save.button")
-- and also as a space-split copy so FTS matches each segment. Postgres's
-- default parser tokenises "a.b" as a single `host` token, which would hide
-- typical identifier keys from searches like "save" or "button". Splitting
-- on `[._-]` at vector-generation time keeps the raw name searchable as a
-- phrase and exposes each segment as its own lexeme.
ALTER TABLE keys
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (
            to_tsvector(
                'simple',
                COALESCE(key_name, '') || ' ' ||
                regexp_replace(COALESCE(key_name, ''), '[._-]+', ' ', 'g') || ' ' ||
                COALESCE(description, '')
            )
        ) STORED;

CREATE INDEX idx_keys_search_vector ON keys USING gin (search_vector);


-- ---- translations.value trigram -----------------------------------------
-- Fuzzy substring / similarity search for translated text. GIN + trgm_ops
-- covers ILIKE and the `%` similarity operator cheaply.
CREATE INDEX idx_translations_value_trgm
    ON translations USING gin (value gin_trgm_ops);
