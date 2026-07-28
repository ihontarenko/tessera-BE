-- =============================================================================
--  V000011  Drop components and versions (ADR-0017)
--
--  Universal SQL: H2 / MySQL / PostgreSQL compatible. The h2 and postgresql copies
--  are byte-identical; the mysql copy adds only the utf8mb4 header, there being no
--  backslashes here to double.
--
--  Components answered "which subsystem owns this?" and versions answered "which
--  release was this broken in, and which one fixes it?". Neither question is asked
--  of this tracker, so both cost a section of the issue modal, two panels of
--  project settings and two terms of the filter grammar to say nothing. Labels
--  remain and are the one grouping mechanism actually in use.
--
--  Append-only, per the Phase-2 convention: V000006 keeps creating these tables
--  and this migration takes them away again, so a database migrated before today
--  and one created from scratch end up in the same place. Existing rows are
--  discarded — this is seed and test data, and the drop is the decision.
--
--  Order matters: the issue join tables carry the foreign keys into `components`
--  and `versions`, so they go first. Dropping a table drops its own indexes,
--  constraints and unique keys with it; none of the four is referenced from
--  anywhere else.
-- =============================================================================

DROP TABLE issue_component;
DROP TABLE issue_version;
DROP TABLE components;
DROP TABLE versions;
