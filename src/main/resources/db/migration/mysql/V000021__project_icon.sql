SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000021  A project carries one emoji (TSSR-7)
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar this utf8mb4 header.
--
--  Every project drew with the same folder glyph, so a switcher of three was
--  three identical rows. One emoji per project is the cheapest thing that fixes
--  it: a column, no upload, no storage backend, nothing to serve.
--
--  ⚠️ VARCHAR(16) for what is drawn as ONE character. An emoji is a grapheme
--  cluster, not a code point: a flag is two regional indicators, a family is up
--  to seven code points joined by zero-width joiners, and a skin tone adds a
--  modifier to each. Sixteen is comfortably above the longest sequence anybody
--  types and still small enough that the column cannot become a text field.
--
--  ⚠️ The utf8mb4 header is load-bearing HERE, not decorative. Emoji live outside
--  the Basic Multilingual Plane, so a `utf8` (three-byte) column would refuse
--  every value this field exists to hold.
--
--  "Exactly one emoji" is enforced in ProjectIcon, not by a CHECK: counting
--  grapheme clusters is not something SQL can say, and a constraint that could
--  only approximate the rule would be a second, wrong answer beside the real one.
-- =============================================================================

ALTER TABLE projects ADD COLUMN icon VARCHAR(16) NULL;
