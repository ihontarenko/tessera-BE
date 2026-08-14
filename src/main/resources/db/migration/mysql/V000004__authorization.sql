SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000004  Local authorization — permission catalog, project roles, mappings
--
--  Universal SQL: MySQL / PostgreSQL compatible. Identical to the mysql copy
--  bar the utf8mb4 header. Identifiers are full words or single-letter
--  abbreviations (`prp-` = the single-letter form of "Project Role Permission",
--  the id-ct-01 precedent) — never 2–4 letter truncations.
--
--  The project tier of authorization (CONTEXT.md, "Permission"/"ProjectRole"). The
--  role → permission map is GLOBAL (no per-project PermissionScheme in Phase 1).
--  Membership + individual overrides live in the projects migration (V000005),
--  since they reference projects.id. Effective permissions =
--  role permissions ∪ ALLOW overrides − DENY overrides, deny wins.
-- =============================================================================

-- ── permissions ───────────────────────────────────────────────────────────────
CREATE TABLE permissions
(
    id           VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(255),

    CONSTRAINT permissions_pk       PRIMARY KEY (id),
    CONSTRAINT uq_permissions_name  UNIQUE (name)
);

-- ── project_roles ─────────────────────────────────────────────────────────────
CREATE TABLE project_roles
(
    id           VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(255),

    CONSTRAINT project_roles_pk       PRIMARY KEY (id),
    CONSTRAINT uq_project_roles_name  UNIQUE (name)
);

-- ── project_role_permissions ──────────────────────────────────────────────────
CREATE TABLE project_role_permissions
(
    id             VARCHAR(36) NOT NULL,
    role_id        VARCHAR(36) NOT NULL,
    permission_id  VARCHAR(36) NOT NULL,

    CONSTRAINT project_role_permissions_pk        PRIMARY KEY (id),
    CONSTRAINT uq_project_role_permission         UNIQUE (role_id, permission_id),
    CONSTRAINT fk_project_role_permissions_role   FOREIGN KEY (role_id)       REFERENCES project_roles (id),
    CONSTRAINT fk_project_role_permissions_perm   FOREIGN KEY (permission_id) REFERENCES permissions (id)
);


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

INSERT INTO permissions (id, name, description) VALUES
    ('permission-browse-project',     'BROWSE_PROJECT',     'View the project and its issues.'),
    ('permission-create-issue',       'CREATE_ISSUE',       'Create issues in the project.'),
    ('permission-edit-issue',         'EDIT_ISSUE',         'Edit issue fields.'),
    ('permission-assign-issue',       'ASSIGN_ISSUE',       'Assign issues to members.'),
    ('permission-transition-issue',   'TRANSITION_ISSUE',   'Move issues through the workflow.'),
    ('permission-delete-issue',       'DELETE_ISSUE',       'Delete issues.'),
    ('permission-add-comment',        'ADD_COMMENT',        'Comment on issues.'),
    ('permission-manage-sprint',      'MANAGE_SPRINT',      'Create and manage sprints.'),
    ('permission-administer-project', 'ADMINISTER_PROJECT', 'Administer the project — settings, membership, roles, overrides.');

INSERT INTO project_roles (id, name, description) VALUES
    ('project-role-administrator', 'Administrator', 'Full control of the project, its settings and its membership.'),
    ('project-role-developer',     'Developer',     'Works on issues: create, edit, assign, transition, comment, manage sprints.'),
    ('project-role-viewer',        'Viewer',        'Read-only access, may comment.');

-- Administrator — every permission.
INSERT INTO project_role_permissions (id, role_id, permission_id) VALUES
    ('prp-001', 'project-role-administrator', 'permission-browse-project'),
    ('prp-002', 'project-role-administrator', 'permission-create-issue'),
    ('prp-003', 'project-role-administrator', 'permission-edit-issue'),
    ('prp-004', 'project-role-administrator', 'permission-assign-issue'),
    ('prp-005', 'project-role-administrator', 'permission-transition-issue'),
    ('prp-006', 'project-role-administrator', 'permission-delete-issue'),
    ('prp-007', 'project-role-administrator', 'permission-add-comment'),
    ('prp-008', 'project-role-administrator', 'permission-manage-sprint'),
    ('prp-009', 'project-role-administrator', 'permission-administer-project');

-- Developer — everything except deleting issues and administering the project.
INSERT INTO project_role_permissions (id, role_id, permission_id) VALUES
    ('prp-010', 'project-role-developer', 'permission-browse-project'),
    ('prp-011', 'project-role-developer', 'permission-create-issue'),
    ('prp-012', 'project-role-developer', 'permission-edit-issue'),
    ('prp-013', 'project-role-developer', 'permission-assign-issue'),
    ('prp-014', 'project-role-developer', 'permission-transition-issue'),
    ('prp-015', 'project-role-developer', 'permission-add-comment'),
    ('prp-016', 'project-role-developer', 'permission-manage-sprint');

-- Viewer — read-only, may comment.
INSERT INTO project_role_permissions (id, role_id, permission_id) VALUES
    ('prp-017', 'project-role-viewer', 'permission-browse-project'),
    ('prp-018', 'project-role-viewer', 'permission-add-comment');
