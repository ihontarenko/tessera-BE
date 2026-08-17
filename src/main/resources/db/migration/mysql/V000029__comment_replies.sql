SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000029  A comment can answer another comment (TSSR-26)
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar the utf8mb4 header the MySQL twin needs.
--
--  Every remark used to land at the same level in one time-ordered stream, so
--  "no, that was the staging box" sat five entries below the thing it
--  contradicted, separated by three status changes.
--
--  ⚠️ THIS PARTLY REVERSES A WRITTEN DECISION. Comment.java said "a flat list,
--  no threaded replies (ticket 13)" and meant it. What that was protecting
--  against still holds — a tracker is not a forum, and unbounded nesting makes
--  a shape nobody can scan or quote from — so the reversal is bounded: ONE
--  level. A reply answers a top-level comment and cannot itself be answered.
--
--  ⚠️ The depth cap is NOT in this file, and cannot be. "The parent must have no
--  parent" is not something a column constraint can say; it lives in
--  CommentService.add, next to the check that a parent is on the same issue.
--
--  ⚠️ NO ON DELETE CASCADE, deliberately. Deleting a parent does take its
--  replies — an answer to a comment that no longer exists is an answer to
--  nothing — but the service does it, so the delete control can say HOW MANY
--  first. A cascade that happens silently is the version people are right to
--  hate.
--
--  The index goes in BEFORE the foreign key: MySQL creates one of its own for
--  an FK that has none, so declaring the key first leaves the table carrying
--  two indexes over one column. PostgreSQL creates neither and needs this
--  outright — and this one is read on every thread render.
-- =============================================================================

ALTER TABLE comments ADD COLUMN parent_comment_id VARCHAR(36) NULL;

CREATE INDEX idx_comments_parent ON comments (parent_comment_id);

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES comments (id);
