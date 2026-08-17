-- =============================================================================
--  V000032  A link type for gathering an effort (TSSR-43)
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar the utf8mb4 header the MySQL twin needs.
--
--  A refactor or a migration is one thing that lands as twenty tickets in four
--  projects. A TRACKING ISSUE gathers them: one hub whose links name everything
--  the effort touches. This is the type those links carry.
--
--  ⚠️ NOT `Relates`, AND THAT IS THE WHOLE POINT. "Relates to" and "is part of
--  this effort" are different claims. A hub built on the first cannot be told
--  apart from an issue that merely mentions things, so the register it was
--  supposed to be is unreadable the moment anybody links anything else.
--
--  ⚠️ EFFECT `NONE`, DELIBERATELY (TSSR-40). A tracking link must not block
--  anything. A hub is a register, not a dependency — an effort whose members
--  all waited on their own hub would deadlock the day it was created.
--
--  ⚠️ This ships the type, not the ability to use it across projects. Links
--  have never had a project constraint in the service; what stopped a
--  cross-project one was the interface offering only same-project candidates.
--  That is a UI change, and the disclosure rule that goes with it lives in
--  IssueAssembler — see IssueReference.redacted.
-- =============================================================================

INSERT INTO link_types (id, name, outward_label, inward_label, effect) VALUES
    ('link-type-tracks', 'Tracks', 'tracks', 'tracked by', 'NONE');
