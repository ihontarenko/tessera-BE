SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000031  A link type says what it does, not just what it is called (TSSR-40)
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar the utf8mb4 header the MySQL twin needs.
--
--  Every link in this product was decoration: a name, two labels, and nothing
--  that read them. This is the field that lets the catalog say which
--  relationships the product acts on.
--
--  ⚠️ AN ENUM, NOT A BOOLEAN, BECAUSE THE EFFECT IS ASYMMETRIC. In "A blocks B"
--  it is B that cannot proceed, never A. A flag says WHETHER something happens
--  and never TO WHICH END. Every value is written from the point of view of the
--  INWARD side — the issue that reads "is blocked by".
--
--  ⚠️ NOT NULL, DEFAULT 'NONE'. A nullable effect would make "does nothing" and
--  "nobody said" two values that render the same and behave the same, which is
--  one value too many. Every existing row therefore reads NONE and every
--  existing link keeps behaving exactly as it does today.
--
--  ⚠️ WARNS_START IS IN THE LIST FROM THE BEGINNING, AND IT IS NOT PADDING. The
--  escape from a hard gate is retyping the link; a team doing that weekly is
--  telling you they needed a warning rather than a wall. Without the softer
--  level they corrupt the catalog instead of turning the strength down, and
--  adding it afterwards means migrating a value everybody already worked
--  around.
--
--  BLOCKS_DONE ships unused. Blocking the start and blocking the finish are
--  genuinely different rules, and finding that out after choosing a boolean
--  would have been a migration.
--
--  ⚠️ THIS REPLACES A DEFINITION THAT WAS A STRING MATCH. The filter grammar
--  decided "blocked" by comparing the link type's NAME to the literal 'Blocks'
--  (IssueLinkTestExtension), which broke the moment anybody renamed the row or
--  wanted a second blocking type. After this, anything asking whether a link
--  blocks reads this column, and there is one definition rather than two.
-- =============================================================================

ALTER TABLE link_types
    ADD COLUMN effect VARCHAR(16) DEFAULT 'NONE' NOT NULL;

ALTER TABLE link_types
    ADD CONSTRAINT ck_link_types_effect
        CHECK (effect IN ('NONE', 'WARNS_START', 'BLOCKS_START', 'BLOCKS_DONE'));


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

-- The one seeded type that was always meant to mean something. Duplicates and
-- Relates stay NONE: they tell a reader where to look, and nothing more.
UPDATE link_types SET effect = 'BLOCKS_START' WHERE id = 'link-type-blocks';
