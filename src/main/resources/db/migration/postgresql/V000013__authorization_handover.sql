-- =============================================================================
--  V000013  Authorization moves to the engine (ticket 19, half one)
--
--  Universal SQL: MySQL / PostgreSQL compatible. The mysql copy is
--  byte-identical bar its utf8mb4 header.
--
--  ⚠️ THIS MIGRATION DROPS NOTHING. It copies what Tessera's own tables say into
--  the ones `jmouse-access-jpa` owns, and leaves the originals standing so the
--  two models can be run side by side and compared on real data
--  (`ParallelAuthorizationCheck`). Retiring them is V000014, and it is deliberately
--  a separate file: the whole value of a parallel run is that it happens before
--  anything is thrown away.
--
--  ⚠️ THE access_* TABLES ARE NOT CREATED HERE and must not be. The library
--  migrates itself against a history table of its own and runs FIRST — the
--  auto-configuration adds a `depends-on` from this product's Flyway initializer
--  to the library's migrator. If these INSERTs fail with "table access_roles
--  doesn't exist", the dependency is what to look at, not this file.
--
--  ⚠️ THE ROLE IDs ARE DETERMINISTIC, and that is what lets SQL do this at all.
--  `PolicySeedStep` writes roles from `policy/tessera.jmp` at the first start —
--  after Flyway, and with generated identifiers this file could not know. Writing
--  the rows here under fixed ids means the seed finds the names already defined,
--  skips creating them, and goes on to write their bundles; and the assignments
--  below have something to point at.
--
--  Identifiers are full words, never 2-4 letter truncations.
-- =============================================================================


-- ── The four roles, under ids this file can name ──────────────────────────────
--  `assignable_at` is the widest scope each may be handed out at, and it agrees
--  with `policy/tessera.jmp` because the seed would otherwise correct it.
INSERT INTO access_roles (id, role_name, assignable_at) VALUES
    ('role-project-viewer',              'PROJECT_VIEWER',              'PROJECT'),
    ('role-project-developer',           'PROJECT_DEVELOPER',           'PROJECT'),
    ('role-project-administrator',       'PROJECT_ADMINISTRATOR',       'PROJECT'),
    ('role-global-access-administrator', 'GLOBAL_ACCESS_ADMINISTRATOR', 'GLOBAL');


-- ── Memberships become role assignments at the project ────────────────────────
--  A membership carried a role in a project; an assignment carries a role at a
--  place. The translation is one join, and the old display names are what it joins
--  on — 'Administrator', 'Developer', 'Viewer', seeded in V000004.
--
--  ⚠️ `source` is 'HANDOVER' rather than 'DIRECT'. The column is deliberately free
--  text so a product may name its own provenance, and "this came from the model
--  that used to be here" is worth being able to see a year from now.
--
--  A membership whose role is none of the three is not carried over; there are no
--  such rows, because V000004 seeded exactly three and nothing writes others.
INSERT INTO access_role_assignments (id, subject_id, role_id, scope_type, scope_id, source, granted_by, created_at)
SELECT
    membership.id,
    membership.member_id,
    CASE role.name
        WHEN 'Administrator' THEN 'role-project-administrator'
        WHEN 'Developer'     THEN 'role-project-developer'
        WHEN 'Viewer'        THEN 'role-project-viewer'
    END,
    'PROJECT',
    membership.project_id,
    'HANDOVER',
    NULL,
    membership.created_at
FROM project_memberships membership
JOIN project_roles role ON role.id = membership.role_id
WHERE role.name IN ('Administrator', 'Developer', 'Viewer');


-- ── Overrides become personal grants ──────────────────────────────────────────
--  ⚠️ A PERMISSION IS STORED BY NAME. `permissions` was a surrogate-key table whose
--  only job was handing out ids for a list the code already states, so the join
--  below is the last time anything reads it.
--
--  ⚠️ NO SEMANTIC CHANGE HERE, despite what a covering note might suggest. The
--  engine's rule is deny-wins with the subtraction last, and the old resolver
--  applied overrides in insertion order — which sounds like a difference and is
--  not, because `uq_project_permission_override` allows at most ONE row per
--  (project, member, permission). There has never been an ALLOW and a DENY of the
--  same permission to argue about.
--
--  ⚠️ THE NAMES CHANGE HERE, and the policy grammar is why. A permission in a
--  `.jmp` document must be `namespace:action` — the colon is what the parser
--  identifies the shape BY — so `BROWSE_PROJECT` cannot be written down at all.
--  The mapping below is the last thing that knows the old spelling; a name none of
--  the ten covers is left out rather than carried over under a guess, and there
--  are none, because V000004 seeded exactly these nine.
INSERT INTO access_subject_permissions (id, subject_id, permission, effect, scope_type, scope_id, reason, granted_by, created_at)
SELECT
    override.id,
    override.member_id,
    CASE permission.name
        WHEN 'BROWSE_PROJECT'     THEN 'project:browse'
        WHEN 'CREATE_ISSUE'       THEN 'issue:create'
        WHEN 'EDIT_ISSUE'         THEN 'issue:edit'
        WHEN 'ASSIGN_ISSUE'       THEN 'issue:assign'
        WHEN 'TRANSITION_ISSUE'   THEN 'issue:transition'
        WHEN 'DELETE_ISSUE'       THEN 'issue:delete'
        WHEN 'ADD_COMMENT'        THEN 'comment:write'
        WHEN 'MANAGE_SPRINT'      THEN 'sprint:manage'
        WHEN 'ADMINISTER_PROJECT' THEN 'project:administer'
    END,
    override.effect,
    'PROJECT',
    override.project_id,
    'Carried over from project_permission_overrides',
    NULL,
    override.created_at
FROM project_permission_overrides override
JOIN permissions permission ON permission.id = override.permission_id
WHERE permission.name IN ('BROWSE_PROJECT', 'CREATE_ISSUE', 'EDIT_ISSUE', 'ASSIGN_ISSUE',
                          'TRANSITION_ISSUE', 'DELETE_ISSUE', 'ADD_COMMENT', 'MANAGE_SPRINT',
                          'ADMINISTER_PROJECT');


-- ── bootstrap_records ─────────────────────────────────────────────────────────
--  Flyway's history table, for rows rather than for schema: which one-off seed has
--  run, and what its source looked like when it did.
--
--  ⚠️ It lands in this migration because the policy seed is the only thing that
--  needs it, and a table created ahead of its first reader is a table nobody can
--  explain. `checksum` is a SHA-256 in hex, which is 64 characters exactly.
--
--  ⚠️ Deleting a row here makes its step run again. That is the documented way to
--  re-apply a seed, and `PolicySeedStep` is idempotent so re-running one that did
--  not need it costs nothing.
CREATE TABLE bootstrap_records
(
    id          VARCHAR(36)  NOT NULL,
    step_key    VARCHAR(128) NOT NULL,
    checksum    VARCHAR(64)  NOT NULL,
    applied_at  TIMESTAMP    NOT NULL,
    applied_by  VARCHAR(64)  NOT NULL,
    note        VARCHAR(512),

    CONSTRAINT bootstrap_records_pk            PRIMARY KEY (id),
    CONSTRAINT uq_bootstrap_records_step_key   UNIQUE (step_key)
);
