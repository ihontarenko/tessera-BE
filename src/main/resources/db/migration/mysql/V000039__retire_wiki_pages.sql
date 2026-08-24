-- =============================================================================
--  V000039  Tessera stops owning pages — the store from V000023 is dropped
--
--  TSSR-0099. Every entity, repository, service and controller over these three
--  tables is gone; the pages live in Kiwi, which decides who may read and write
--  them, per section, through its own grants (KW-1 §1).
--
--  ⚠️ THIS IS WHY V000023 IS STILL ON DISK AND UNTOUCHED.
--
--  Tessera's migration set is IMMUTABLE, unlike Innoventa's. Innoventa deletes a
--  migration and drops its database (INVT-0099 did exactly that); this schema
--  holds the tracker every product's tickets live in, including the ticket that
--  describes this deletion, so it is never dropped and never re-run from zero.
--
--  Two ways to break the boot, both tried before this file existed:
--
--    · delete V000023        → "Detected applied migration not resolved locally"
--    · comment inside it     → checksum mismatch; an applied migration's bytes
--                              are part of its identity
--
--  So history stays exactly as it ran, and the undo is a migration of its own.
--  That is the ordinary shape for a schema that cannot be rebuilt, and the file
--  you are reading is what "editing a migration" turns into once it is applied.
-- =============================================================================

-- ⚠️ entity_categories first: it foreign-keys into categories.
DROP TABLE IF EXISTS entity_categories;
DROP TABLE IF EXISTS wiki_pages;
DROP TABLE IF EXISTS categories;

-- ⚠️ The policy ledger, and it is not housekeeping. `policy/tessera.jmp` changed
-- semantically — page:read, page:write and category:manage left the vocabulary AND
-- every role — and the seed only re-runs when this row is absent. Without this the
-- next boot keeps granting three permissions the document no longer defines.
DELETE FROM bootstrap_records WHERE step_key = 'access:policy';
