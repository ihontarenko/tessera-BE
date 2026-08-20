SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000036  The wiki product is called WiQi, and the column follows
--
--  Universal SQL: MySQL / PostgreSQL compatible. Identical to the postgresql copy
--  bar this utf8mb4 header.
--
--  `projects.wiq_root_category_id` was named after the product when the product
--  was called WiQ. It was renamed to WiQi on 2026-08-18 and every other spelling
--  moved with it: the Java package, the configuration prefix, the token audience,
--  the database. A column left behind is the one place a reader would still find
--  the old name and reasonably conclude the rename was half done.
--
--  ⚠️ A RENAME, NOT AN ADD-AND-COPY. The column holds one identifier per project
--  and nothing reads it while this runs, so there is no window where two columns
--  disagree. `RENAME COLUMN` is supported by MySQL 8.0+ and PostgreSQL alike.
--
--  ⚠️ THE VALUE IS STILL A WiQi CATEGORY IDENTIFIER, and that is the thing this
--  rename does NOT fix. `WIQ-0065` argues that a project should carry a provider
--  and an opaque handle rather than a column named after whichever product
--  currently answers — at which point this column is replaced rather than renamed
--  again. This migration buys consistency today; it does not decide that.
-- =============================================================================

ALTER TABLE projects RENAME COLUMN wiq_root_category_id TO wiqi_root_category_id;
