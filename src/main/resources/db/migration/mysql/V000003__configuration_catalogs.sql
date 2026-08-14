SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000003  Configuration catalogs + schemes (ADR-0001, ADR-0004, ADR-0005)
--
--  Universal SQL: MySQL / PostgreSQL compatible. Enums as VARCHAR + CHECK.
--  Identifiers are full words or single-letter abbreviations, never 2–4 letter
--  truncations (the `itsi-` prefix on issue_type_scheme_items is the single-letter
--  form of "Issue Type Scheme Item", the id-ct-01 precedent). The mysql copy is
--  identical bar the utf8mb4 header.
--
--  Global, reusable, seeded here; Phase 1 exposes these read-only. Priority /
--  Status / Resolution have no scheme; exactly two schemes exist (IssueTypeScheme,
--  WorkflowScheme). StatusCategory is a closed enum, not a table (ADR-0004).
-- =============================================================================

-- ── issue_types ───────────────────────────────────────────────────────────────
CREATE TABLE issue_types
(
    id               VARCHAR(36) NOT NULL,
    name             VARCHAR(64) NOT NULL,
    hierarchy_level  INTEGER     NOT NULL,
    icon_key         VARCHAR(64),
    description      VARCHAR(255),

    CONSTRAINT issue_types_pk        PRIMARY KEY (id),
    CONSTRAINT uq_issue_types_name   UNIQUE (name)
);

-- ── priorities ────────────────────────────────────────────────────────────────
CREATE TABLE priorities
(
    id         VARCHAR(36) NOT NULL,
    name       VARCHAR(64) NOT NULL,
    sequence   INTEGER     NOT NULL,
    color      VARCHAR(16),

    CONSTRAINT priorities_pk        PRIMARY KEY (id),
    CONSTRAINT uq_priorities_name   UNIQUE (name)
);

-- ── statuses ──────────────────────────────────────────────────────────────────
CREATE TABLE statuses
(
    id         VARCHAR(36) NOT NULL,
    name       VARCHAR(64) NOT NULL,
    category   VARCHAR(16) NOT NULL
                   CHECK (category IN ('TODO', 'IN_PROGRESS', 'DONE')),

    CONSTRAINT statuses_pk       PRIMARY KEY (id),
    CONSTRAINT uq_statuses_name  UNIQUE (name)
);

-- ── resolutions ───────────────────────────────────────────────────────────────
CREATE TABLE resolutions
(
    id           VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(255),

    CONSTRAINT resolutions_pk       PRIMARY KEY (id),
    CONSTRAINT uq_resolutions_name  UNIQUE (name)
);

-- ── workflows ─────────────────────────────────────────────────────────────────
CREATE TABLE workflows
(
    id           VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(255),

    CONSTRAINT workflows_pk       PRIMARY KEY (id),
    CONSTRAINT uq_workflows_name  UNIQUE (name)
);

-- ── transitions ───────────────────────────────────────────────────────────────
--  A null from_status_id is the initial (create) transition. The (from → to) edge
--  is what the data-driven engine checks (ADR-0005).
CREATE TABLE transitions
(
    id              VARCHAR(36) NOT NULL,
    workflow_id     VARCHAR(36) NOT NULL,
    from_status_id  VARCHAR(36),
    to_status_id    VARCHAR(36) NOT NULL,
    name            VARCHAR(64) NOT NULL,

    CONSTRAINT transitions_pk               PRIMARY KEY (id),
    CONSTRAINT fk_transitions_workflow      FOREIGN KEY (workflow_id)    REFERENCES workflows (id),
    CONSTRAINT fk_transitions_from_status   FOREIGN KEY (from_status_id) REFERENCES statuses (id),
    CONSTRAINT fk_transitions_to_status     FOREIGN KEY (to_status_id)   REFERENCES statuses (id)
);

-- ── issue_type_schemes ────────────────────────────────────────────────────────
CREATE TABLE issue_type_schemes
(
    id                     VARCHAR(36) NOT NULL,
    name                   VARCHAR(64) NOT NULL,
    default_issue_type_id  VARCHAR(36) NOT NULL,
    description            VARCHAR(255),

    CONSTRAINT issue_type_schemes_pk        PRIMARY KEY (id),
    CONSTRAINT uq_issue_type_schemes_name   UNIQUE (name),
    CONSTRAINT fk_issue_type_schemes_default FOREIGN KEY (default_issue_type_id) REFERENCES issue_types (id)
);

-- ── issue_type_scheme_items ───────────────────────────────────────────────────
CREATE TABLE issue_type_scheme_items
(
    id             VARCHAR(36) NOT NULL,
    scheme_id      VARCHAR(36) NOT NULL,
    issue_type_id  VARCHAR(36) NOT NULL,
    sequence       INTEGER     NOT NULL,

    CONSTRAINT issue_type_scheme_items_pk       PRIMARY KEY (id),
    CONSTRAINT uq_issue_type_scheme_item        UNIQUE (scheme_id, issue_type_id),
    CONSTRAINT fk_issue_type_scheme_items_scheme FOREIGN KEY (scheme_id)     REFERENCES issue_type_schemes (id),
    CONSTRAINT fk_issue_type_scheme_items_type   FOREIGN KEY (issue_type_id) REFERENCES issue_types (id)
);

-- ── workflow_schemes ──────────────────────────────────────────────────────────
CREATE TABLE workflow_schemes
(
    id                   VARCHAR(36) NOT NULL,
    name                 VARCHAR(64) NOT NULL,
    default_workflow_id  VARCHAR(36) NOT NULL,
    description          VARCHAR(255),

    CONSTRAINT workflow_schemes_pk        PRIMARY KEY (id),
    CONSTRAINT uq_workflow_schemes_name   UNIQUE (name),
    CONSTRAINT fk_workflow_schemes_default FOREIGN KEY (default_workflow_id) REFERENCES workflows (id)
);

-- ── workflow_scheme_items ─────────────────────────────────────────────────────
--  Per-type overrides only; types with no row here use the scheme's default
--  workflow. Seeded empty — both default schemes rely on their default workflow.
CREATE TABLE workflow_scheme_items
(
    id             VARCHAR(36) NOT NULL,
    scheme_id      VARCHAR(36) NOT NULL,
    issue_type_id  VARCHAR(36) NOT NULL,
    workflow_id    VARCHAR(36) NOT NULL,

    CONSTRAINT workflow_scheme_items_pk       PRIMARY KEY (id),
    CONSTRAINT uq_workflow_scheme_item        UNIQUE (scheme_id, issue_type_id),
    CONSTRAINT fk_workflow_scheme_items_scheme   FOREIGN KEY (scheme_id)     REFERENCES workflow_schemes (id),
    CONSTRAINT fk_workflow_scheme_items_type     FOREIGN KEY (issue_type_id) REFERENCES issue_types (id),
    CONSTRAINT fk_workflow_scheme_items_workflow FOREIGN KEY (workflow_id)   REFERENCES workflows (id)
);


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

-- Issue types — hierarchyLevel encodes the hierarchy: Epic=1, Story/Task/Bug=0,
-- Sub-task=-1. Sub-task-ness is level < 0; higher levels are addable, no schema change.
INSERT INTO issue_types (id, name, hierarchy_level, icon_key, description) VALUES
    ('issue-type-epic',     'Epic',     1,  'epic',     'A large body of work broken into stories.'),
    ('issue-type-story',    'Story',    0,  'story',    'A unit of user-facing work.'),
    ('issue-type-task',     'Task',     0,  'task',     'A general unit of work.'),
    ('issue-type-bug',      'Bug',      0,  'bug',      'A defect to be fixed.'),
    ('issue-type-sub-task', 'Sub-task', -1, 'sub-task', 'A unit of work under a parent issue.');

-- Priorities — sequence orders them (1 = Highest).
INSERT INTO priorities (id, name, sequence, color) VALUES
    ('priority-highest', 'Highest', 1, '#d0454c'),
    ('priority-high',    'High',    2, '#e57a3a'),
    ('priority-medium',  'Medium',  3, '#e2b93b'),
    ('priority-low',     'Low',     4, '#4a9d6a'),
    ('priority-lowest',  'Lowest',  5, '#6b7a90');

-- Statuses — each in exactly one of the three fixed categories.
INSERT INTO statuses (id, name, category) VALUES
    ('status-to-do',       'To Do',       'TODO'),
    ('status-in-progress', 'In Progress', 'IN_PROGRESS'),
    ('status-in-review',   'In Review',   'IN_PROGRESS'),
    ('status-done',        'Done',        'DONE');

-- Resolutions — the reason an issue closed (independent of status name, ADR-0004).
INSERT INTO resolutions (id, name, description) VALUES
    ('resolution-done',              'Done',             'Work completed.'),
    ('resolution-wont-do',           'Won''t Do',        'Decided not to action.'),
    ('resolution-duplicate',         'Duplicate',        'Already tracked elsewhere.'),
    ('resolution-cannot-reproduce',  'Cannot Reproduce', 'Could not be reproduced.');

-- Workflows.
INSERT INTO workflows (id, name, description) VALUES
    ('workflow-default', 'Default Workflow',      'To Do → In Progress → In Review → Done, with backward transitions.'),
    ('workflow-todo',    'Simple Todo Workflow',  'A flat To Do ↔ Done workflow for lightweight lists.');

-- Default workflow transitions (null from = the create transition).
INSERT INTO transitions (id, workflow_id, from_status_id, to_status_id, name) VALUES
    ('transition-default-create',   'workflow-default', NULL,                'status-to-do',       'Create'),
    ('transition-default-start',    'workflow-default', 'status-to-do',      'status-in-progress', 'Start Progress'),
    ('transition-default-review',   'workflow-default', 'status-in-progress','status-in-review',   'Ready for Review'),
    ('transition-default-complete', 'workflow-default', 'status-in-review',  'status-done',        'Done'),
    ('transition-default-stop',     'workflow-default', 'status-in-progress','status-to-do',       'Stop Progress'),
    ('transition-default-reject',   'workflow-default', 'status-in-review',  'status-in-progress', 'Back to In Progress'),
    ('transition-default-reopen',   'workflow-default', 'status-done',       'status-in-progress', 'Reopen');

-- Simple todo workflow transitions.
INSERT INTO transitions (id, workflow_id, from_status_id, to_status_id, name) VALUES
    ('transition-todo-create',   'workflow-todo', NULL,           'status-to-do', 'Create'),
    ('transition-todo-complete', 'workflow-todo', 'status-to-do', 'status-done',  'Complete'),
    ('transition-todo-reopen',   'workflow-todo', 'status-done',  'status-to-do', 'Reopen');

-- Issue type schemes.
INSERT INTO issue_type_schemes (id, name, default_issue_type_id, description) VALUES
    ('scheme-issue-type-default', 'Default Issue Type Scheme', 'issue-type-story', 'Epic, Story, Task, Bug and Sub-task.'),
    ('scheme-issue-type-todo',    'Todo Issue Type Scheme',    'issue-type-task',  'A single Task type for lightweight lists.');

INSERT INTO issue_type_scheme_items (id, scheme_id, issue_type_id, sequence) VALUES
    ('itsi-default-epic',     'scheme-issue-type-default', 'issue-type-epic',     0),
    ('itsi-default-story',    'scheme-issue-type-default', 'issue-type-story',    1),
    ('itsi-default-task',     'scheme-issue-type-default', 'issue-type-task',     2),
    ('itsi-default-bug',      'scheme-issue-type-default', 'issue-type-bug',      3),
    ('itsi-default-sub-task', 'scheme-issue-type-default', 'issue-type-sub-task', 4),
    ('itsi-todo-task',        'scheme-issue-type-todo',    'issue-type-task',     0);

-- Workflow schemes (no per-type overrides — each relies on its default workflow).
INSERT INTO workflow_schemes (id, name, default_workflow_id, description) VALUES
    ('scheme-workflow-default', 'Default Workflow Scheme', 'workflow-default', 'Maps every issue type to the default workflow.'),
    ('scheme-workflow-todo',    'Todo Workflow Scheme',    'workflow-todo',    'Maps the single Task type to the simple todo workflow.');
