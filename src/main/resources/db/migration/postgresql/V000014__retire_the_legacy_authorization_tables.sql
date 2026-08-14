-- =============================================================================
--  V000014  The legacy authorization model is retired (ticket 19, half two)
--
--  Universal SQL: MySQL / PostgreSQL compatible. The postgresql copy is
--  byte-identical bar the mysql copy's utf8mb4 header.
--
--  ⚠️ V000013 COPIED THESE ROWS INTO THE ENGINE'S TABLES AND LEFT THE ORIGINALS
--  STANDING, on purpose: the whole value of a parallel run is that it happens
--  before anything is thrown away. It has happened. This is the throwing away.
--
--  ⚠️ WHAT WAS STILL READING THEM. Nothing that decided anything — every route
--  has resolved through `@RequiresAccess` and the `access_*` rows since V000013.
--  What survived was a DUAL WRITE: `ProjectMembershipService` wrote a membership
--  row AND an engine assignment, and the per-project pickers were filled from
--  these tables while every other screen read the engine. One product, two
--  vocabularies — the override dialog offered `ADD_COMMENT` for a permission
--  stored as `comment:write`. That is what a second store buys you.
--
--  ⚠️ PERSONAL PERMISSION OVERRIDES ARE NOT MIGRATED — THEY ARE DELETED, AND
--  THAT IS A PRODUCT DECISION RATHER THAN A CLEANUP. A per-person allow or deny
--  inside one project was a second answer to "what may this person do", editable
--  by a project administrator and invisible to whoever maintains the roles
--  everybody else is judged by. Permissions come from roles now, and a role is
--  edited in one installation-wide place. Needing a fourth combination of
--  permissions is an argument for a fourth role, which everyone can see.
--
--  ⚠️ The engine-side grants those overrides were mirrored into are NOT removed
--  here. A `access_subject_permissions` row is a personal grant like any other
--  and the access screen shows it; deleting somebody's access from a migration,
--  silently, is not a thing this file should do. They are visible under
--  "Personal grants" and can be revoked there.
-- =============================================================================


-- ── Membership is an access_role_assignments row now ──────────────────────────
--  V000013 already carried every membership across (source 'HANDOVER'), so there
--  is nothing left in here that the engine does not already know.
DROP TABLE IF EXISTS project_permission_overrides;
DROP TABLE IF EXISTS project_memberships;


-- ── The role and permission catalogs ──────────────────────────────────────────
--  `project_role_permissions` first: it is the join, and it references both.
--
--  A permission is stored BY NAME by the engine, so `permissions` was a
--  surrogate-key table whose only job was handing out identifiers for a list the
--  code already states in `Permissions`. A role is stored by name too, and the
--  four roles live in `access_roles` under the names `policy/tessera.jmp`
--  declares.
DROP TABLE IF EXISTS project_role_permissions;
DROP TABLE IF EXISTS project_roles;
DROP TABLE IF EXISTS permissions;
