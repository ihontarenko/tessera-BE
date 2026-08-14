SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000005  Projects — projects, type→scheme presets, memberships, overrides
--
--  Universal SQL: MySQL / PostgreSQL compatible. Identical to the mysql copy
--  bar the utf8mb4 header. Project is top-level — no workspace/organization column
--  (ADR-0002). `project_key` (not `key`, which is reserved) is unique instance-wide.
--
--  project_type_default_schemes is the data-driven type→scheme mapping that lets
--  project creation seed schemes with no `if (type == TODO)` branch. Memberships
--  and permission overrides land here (not in V000004) because they reference
--  projects.id.
-- =============================================================================

-- ── projects ──────────────────────────────────────────────────────────────────
CREATE TABLE projects
(
    id                    VARCHAR(36)  NOT NULL,
    project_key           VARCHAR(32)  NOT NULL,
    name                  VARCHAR(128) NOT NULL,
    type                  VARCHAR(16)  NOT NULL
                              CHECK (type IN ('SCRUM', 'KANBAN', 'TODO')),
    lead_member_id        VARCHAR(36)  NOT NULL,
    issue_type_scheme_id  VARCHAR(36)  NOT NULL,
    workflow_scheme_id    VARCHAR(36)  NOT NULL,
    key_strategy          VARCHAR(64)  NOT NULL,
    key_pattern           VARCHAR(128),
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,

    CONSTRAINT projects_pk                  PRIMARY KEY (id),
    CONSTRAINT uq_projects_key              UNIQUE (project_key),
    CONSTRAINT fk_projects_lead             FOREIGN KEY (lead_member_id)       REFERENCES members (id),
    CONSTRAINT fk_projects_issue_type_scheme FOREIGN KEY (issue_type_scheme_id) REFERENCES issue_type_schemes (id),
    CONSTRAINT fk_projects_workflow_scheme  FOREIGN KEY (workflow_scheme_id)    REFERENCES workflow_schemes (id)
);

-- ── project_type_default_schemes ──────────────────────────────────────────────
CREATE TABLE project_type_default_schemes
(
    project_type          VARCHAR(16) NOT NULL
                              CHECK (project_type IN ('SCRUM', 'KANBAN', 'TODO')),
    issue_type_scheme_id  VARCHAR(36) NOT NULL,
    workflow_scheme_id    VARCHAR(36) NOT NULL,

    CONSTRAINT project_type_default_schemes_pk         PRIMARY KEY (project_type),
    CONSTRAINT fk_project_type_default_issue_type      FOREIGN KEY (issue_type_scheme_id) REFERENCES issue_type_schemes (id),
    CONSTRAINT fk_project_type_default_workflow        FOREIGN KEY (workflow_scheme_id)   REFERENCES workflow_schemes (id)
);

-- ── project_memberships ───────────────────────────────────────────────────────
--  (project, member, role). A member may hold several roles (several rows); their
--  permissions combine additively. Also the visibility gate (ADR-0002).
CREATE TABLE project_memberships
(
    id          VARCHAR(36) NOT NULL,
    project_id  VARCHAR(36) NOT NULL,
    member_id   VARCHAR(36) NOT NULL,
    role_id     VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,

    CONSTRAINT project_memberships_pk        PRIMARY KEY (id),
    CONSTRAINT uq_project_membership         UNIQUE (project_id, member_id, role_id),
    CONSTRAINT fk_project_memberships_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_memberships_member  FOREIGN KEY (member_id)  REFERENCES members (id),
    CONSTRAINT fk_project_memberships_role    FOREIGN KEY (role_id)    REFERENCES project_roles (id)
);

-- ── project_permission_overrides ──────────────────────────────────────────────
--  Individual ALLOW/DENY layered over role permissions; deny wins. At most one per
--  (project, member, permission).
CREATE TABLE project_permission_overrides
(
    id             VARCHAR(36) NOT NULL,
    project_id     VARCHAR(36) NOT NULL,
    member_id      VARCHAR(36) NOT NULL,
    permission_id  VARCHAR(36) NOT NULL,
    effect         VARCHAR(8)  NOT NULL
                       CHECK (effect IN ('ALLOW', 'DENY')),
    created_at     TIMESTAMP   NOT NULL,

    CONSTRAINT project_permission_overrides_pk        PRIMARY KEY (id),
    CONSTRAINT uq_project_permission_override         UNIQUE (project_id, member_id, permission_id),
    CONSTRAINT fk_project_permission_over_project     FOREIGN KEY (project_id)    REFERENCES projects (id),
    CONSTRAINT fk_project_permission_over_member      FOREIGN KEY (member_id)     REFERENCES members (id),
    CONSTRAINT fk_project_permission_over_permission  FOREIGN KEY (permission_id) REFERENCES permissions (id)
);


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

-- Type → scheme presets. SCRUM/KANBAN share the full default schemes; TODO uses the
-- minimal single-type scheme + short workflow. This mapping is what keeps project
-- provisioning free of a type branch.
INSERT INTO project_type_default_schemes (project_type, issue_type_scheme_id, workflow_scheme_id) VALUES
    ('SCRUM',  'scheme-issue-type-default', 'scheme-workflow-default'),
    ('KANBAN', 'scheme-issue-type-default', 'scheme-workflow-default'),
    ('TODO',   'scheme-issue-type-todo',    'scheme-workflow-todo');
