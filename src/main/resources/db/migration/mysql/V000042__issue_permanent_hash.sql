-- ============================================================================
--  V000042  An issue gets an identifier its key cannot be
-- ----------------------------------------------------------------------------
--  `issue_key` is a project's key plus a counter, formatted by the project's
--  key STRATEGY — which is configuration, and configuration changes. Every
--  reference stored outside this database carries that key today: a wiki page
--  quoting a ticket, another product's description, a link somebody sent. Move
--  a key and all of them resolve to nothing, one page at a time, discovered
--  months later.
--
--  So an issue now also carries `hash`: six characters, drawn once at creation,
--  updatable by nothing. A stored reference resolves through it and survives
--  anything that happens to the key.
--
--  ⚠️ THE BACKFILL IS DERIVED FROM `id`, NOT DRAWN.
--
--  `id` is already a UUID, so its first six hex characters carry exactly the
--  entropy a fresh draw would — and a derived backfill re-runs to the same
--  answer, which `UUID()` per row does not. That matters if this migration is
--  ever replayed against a half-migrated database: a re-draw would hand every
--  existing issue a NEW permanent identifier, which is a contradiction in
--  terms and would break every reference written in between.
--
--  ⚠️ The unique index is what proves the backfill, and it goes on LAST for
--  that reason. Six characters over a few hundred rows collides with a
--  probability around one in two thousand; if it ever fires, the migration
--  stops here with a duplicate-key error naming the value — and the fix is a
--  longer hash, not a different scheme. The column is VARCHAR(16) so that fix
--  needs no second migration.
-- ============================================================================

ALTER TABLE issues
    ADD COLUMN hash VARCHAR(16) NULL;

UPDATE issues
   SET hash = SUBSTRING(REPLACE(id, '-', ''), 1, 6)
 WHERE hash IS NULL;

ALTER TABLE issues
    MODIFY COLUMN hash VARCHAR(16) NOT NULL;

CREATE UNIQUE INDEX uq_issues_hash ON issues (hash);
