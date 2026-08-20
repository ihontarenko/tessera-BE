SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000038  The wiki product is called Kiwi, and the column follows
--
--  Universal SQL: MySQL / PostgreSQL compatible. Identical to the mysql copy bar
--  its utf8mb4 header.
--
--  ⚠️ THE THIRD NAME FOR ONE COLUMN, and that is the point rather than an
--  embarrassment: `wiq_root_category_id` (V000026) -> `wiqi_root_category_id`
--  (V000036) -> `kiwi_root_category_id` here. The product was renamed twice on
--  2026-08-18 — `wiq` -> `wiqi` -> `kiwi` — and everything else moved with it:
--  the Java package, the configuration prefix, the token audience, the database.
--  A column left behind is the one place a reader still finds an old name and
--  reasonably concludes the rename was half done. TSSR-0095 is what that costs
--  when the stale name is load-bearing: Tessera's `accepted-audiences` still said
--  `wiqi` and 401'd every call from a Kiwi page, silently, for two days.
--
--  ⚠️ A RENAME, NOT AN ADD-AND-COPY. The column holds one identifier per project
--  and nothing reads it while this runs, so there is no window where two columns
--  disagree. `RENAME COLUMN` is supported by MySQL 8.0+ and PostgreSQL alike.
--
--  ⚠️ THE VALUE IS STILL A KIWI CATEGORY IDENTIFIER, and that is the thing this
--  rename does NOT fix. `KW-0065` argues that a project should carry a provider
--  and an opaque handle rather than a column named after whichever product
--  currently answers — at which point this column is replaced rather than renamed
--  a fourth time. This migration buys consistency today; it does not decide that.
-- =============================================================================

ALTER TABLE projects RENAME COLUMN wiqi_root_category_id TO kiwi_root_category_id;
