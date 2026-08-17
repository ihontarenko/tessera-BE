SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000019  An issue description is prose, not a summary (TSSR-1)
--
--  ⚠️ NOT byte-identical to the postgresql copy: widening a column is the one
--  thing the two engines spell differently (`MODIFY` vs `ALTER COLUMN ... TYPE`).
--  Everything else about the two files matches.
--
--  `issues.description` was VARCHAR(4000). A tracker whose description caps at
--  four thousand characters cannot hold a spec, which is the first thing anybody
--  tried to put in one — publishing epic JMF-1 failed on exactly this.
--
--  TEXT holds 65535 *bytes*; under utf8mb4 a character can take four of them, so
--  the request validation caps at 16000 characters (16000 x 4 = 64000 < 65535).
--  That bound is provable rather than hopeful, which is why it is 16000 and not a
--  rounder number.
--
--  ⚠️ A NEW migration rather than an edit of V000006, against this workspace's
--  usual "edit the existing one" rule: the development database holds live issues
--  (JMF-1..JMF-8, TSSR-1..TSSR-2) that a drop-and-remigrate would destroy. The
--  rule exists because nothing is in production to protect; here something is.
--
--  The other half of TSSR-1 — activity_log_items.old_value / new_value at
--  VARCHAR(1024) — is deliberately NOT widened. See ActivityLogService: a history
--  snapshot is a display string, so the fix is to clamp it at the recording seam
--  rather than to store two more copies of every description forever. Widening it
--  would have made this one field work and left the next long field to fail the
--  same way.
-- =============================================================================

ALTER TABLE issues MODIFY description TEXT;
