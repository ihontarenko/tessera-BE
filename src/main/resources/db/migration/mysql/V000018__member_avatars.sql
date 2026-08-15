SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000018  Member avatars — a face instead of two letters
--
--  ⚠️ THE FIRST MIGRATION WHOSE TWO COPIES GENUINELY DIFFER, and the reason is
--  worth reading before writing the next one that references a library table.
--
--  Every other file here is byte-identical bar the utf8mb4 header, because that
--  header is all MySQL needs — or so it looked. `SET NAMES ... COLLATE` sets the
--  CONNECTION's collation, not the table's, so every Tessera table is actually
--  created at the database default, which on MySQL 8.4 is utf8mb4_0900_ai_ci.
--  `stored_files` is not Tessera's and declares utf8mb4_unicode_ci outright, and
--  MySQL refuses a foreign key between two character columns whose collations
--  differ — errno 3780, and the failure is at migrate time with the columns
--  already added.
--
--  So the mysql copy pins `avatar_file_id`'s collation to the referenced table's
--  and this one does not, because PostgreSQL has neither the clause nor the
--  problem. Moneta never hit this: its CREATE TABLEs carry an explicit
--  `COLLATE=utf8mb4_unicode_ci`, which Tessera's universal SQL deliberately does
--  not write.
--
--  Otherwise universal SQL: no database-specific types; the enum is VARCHAR +
--  CHECK, the house form since V000002.
--
--  ⚠️ THREE COLUMNS, NOT A KIND-AND-VALUE PAIR. A single `avatar_value` holding
--  "either a seed or a file identifier depending on the column beside it" is a
--  column the database cannot check and cannot reference. Split, `avatar_file_id`
--  is a real foreign key into `stored_files`, and the CHECK below states the
--  whole invariant in one place: exactly one shape is populated, and it is the
--  one `avatar_kind` names.
--
--  ⚠️ `stored_files` IS NOT TESSERA'S TABLE — jmouse-storage-jpa owns it, mapping
--  and migrations included, against a Flyway history table of its own. The
--  library orders its migration run BEFORE the product's (see
--  StorageFlywayAutoConfiguration.MigrationOrdering), which is the only reason
--  this file may declare a foreign key into a table nothing here created. Adding
--  the storage dependency and forgetting the @EntityScan entry fails here, at
--  boot, rather than on somebody's first upload.
--
--  ⚠️ NO CASCADE ON THE AVATAR FOREIGN KEY, deliberately. Bytes are shared:
--  content-addressed keys mean two people who pick the same picture point at one
--  row, so deleting that row out from under them is not a cleanup, it is a bug.
--  Reclaiming what nothing points at is the sweeper's job and it asks
--  StorageReferencesConfiguration who is still pointing.
--
--  ⚠️ A PRESET IS A SEED, NOT A CATALOG ENTRY. The generator (UI/src/lib/pixelFace.ts)
--  is total: every string produces a face, so there is no set of valid values for
--  this column to be checked against and no server-side catalog to keep in step
--  with the interface. What is curated is which seeds the picker OFFERS; what is
--  stored is whatever was picked. Length is the only constraint that means
--  anything here.
-- =============================================================================

ALTER TABLE members
    ADD COLUMN avatar_kind    VARCHAR(16) NOT NULL DEFAULT 'INITIALS',
    ADD COLUMN avatar_preset  VARCHAR(64),
    -- ⚠️ The collation is pinned to `stored_files.id`'s rather than left to the
    -- database default, or the foreign key below is refused outright. See the
    -- header; this clause is the ONE thing this copy says that the postgresql
    -- copy does not.
    ADD COLUMN avatar_file_id VARCHAR(36) COLLATE utf8mb4_unicode_ci;

ALTER TABLE members
    ADD CONSTRAINT members_check_avatar_kind
        CHECK (avatar_kind IN ('INITIALS', 'PRESET', 'UPLOAD'));

-- One shape populated, and it is the one the kind names. INITIALS is the absence
-- of a choice rather than a choice, which is why it carries neither column.
ALTER TABLE members
    ADD CONSTRAINT members_check_avatar_shape
        CHECK ((avatar_kind = 'INITIALS' AND avatar_preset IS NULL AND avatar_file_id IS NULL)
            OR (avatar_kind = 'PRESET' AND avatar_preset IS NOT NULL AND avatar_file_id IS NULL)
            OR (avatar_kind = 'UPLOAD' AND avatar_preset IS NULL AND avatar_file_id IS NOT NULL));

ALTER TABLE members
    ADD CONSTRAINT members_fk_avatar_file
        FOREIGN KEY (avatar_file_id) REFERENCES stored_files (id);

-- The sweeper reads every avatar_file_id to decide what is still spoken for, and
-- clearing a picture has to find the rows pointing at an object. Both are lookups
-- by this column, and neither may be a scan of everybody.
CREATE INDEX index_members_avatar_file ON members (avatar_file_id);
