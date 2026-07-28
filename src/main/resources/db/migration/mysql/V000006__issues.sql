SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000006  Issues + satellites — the working core (tickets 06–13)
--
--  Universal SQL: H2 / MySQL / PostgreSQL compatible. Identical to the mysql copy
--  bar the utf8mb4 header (there are no backslashes to double here). Enums as
--  VARCHAR + CHECK; timestamps maintained by the application (@PrePersist /
--  @PreUpdate), not triggers. Identifiers are full words or single-letter
--  abbreviations, never 2–4 letter truncations.
--
--  Naming note: the LexoRank column is `lexo_rank`, not `rank` — MySQL 8 reserves
--  RANK (window function), so an unquoted `rank` column would break the mysql copy.
--  The JPA field stays `rank` (ADR-0006), mapped via @Column(name = "lexo_rank").
--
--  Dependency order: project satellites (components, versions) → issues → the
--  per-project key counter → issue satellites (labels, components, versions, links,
--  comments) → change history (ADR-0007). project_id is denormalised onto issues
--  so ownership is one equality test; the issue satellites reach the project through
--  their issue.
-- =============================================================================

-- ── components ────────────────────────────────────────────────────────────────
--  A named area of a project (Backend, UI) with an optional lead (ticket 06).
CREATE TABLE components
(
    id              VARCHAR(36)  NOT NULL,
    project_id      VARCHAR(36)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    lead_member_id  VARCHAR(36),
    description     VARCHAR(255),

    CONSTRAINT components_pk              PRIMARY KEY (id),
    CONSTRAINT uq_components_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_components_project      FOREIGN KEY (project_id)     REFERENCES projects (id),
    CONSTRAINT fk_components_lead         FOREIGN KEY (lead_member_id) REFERENCES members (id)
);

-- ── versions ──────────────────────────────────────────────────────────────────
--  A release marker with a lifecycle `state` — no released/archived booleans, a
--  single enum state instead (ticket 06; "prefer state over flag"). `sequence`
--  orders them for display.
CREATE TABLE versions
(
    id            VARCHAR(36)  NOT NULL,
    project_id    VARCHAR(36)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(255),
    release_date  DATE,
    state         VARCHAR(16)  NOT NULL
                      CHECK (state IN ('UNRELEASED', 'RELEASED', 'ARCHIVED')),
    sequence      INTEGER      NOT NULL,

    CONSTRAINT versions_pk              PRIMARY KEY (id),
    CONSTRAINT uq_versions_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_versions_project      FOREIGN KEY (project_id) REFERENCES projects (id)
);

-- ── issues ────────────────────────────────────────────────────────────────────
--  The unit of work. Both `sequence` (int, cheap to sort) and `issue_key` (string,
--  unique instance-wide) are stored (ADR-0003). `resolution_id IS NULL ⇔ open`
--  (ADR-0004). `parent_id` is the single unified hierarchy link (ADR/ticket 10).
--  `lexo_rank` is the global ordering string (ADR-0006).
CREATE TABLE issues
(
    id                  VARCHAR(36)      NOT NULL,
    project_id          VARCHAR(36)      NOT NULL,
    sequence            INTEGER          NOT NULL,
    issue_key           VARCHAR(64)      NOT NULL,
    summary             VARCHAR(255)     NOT NULL,
    description         VARCHAR(4000),
    issue_type_id       VARCHAR(36)      NOT NULL,
    priority_id         VARCHAR(36)      NOT NULL,
    status_id           VARCHAR(36)      NOT NULL,
    resolution_id       VARCHAR(36),
    reporter_member_id  VARCHAR(36)      NOT NULL,
    assignee_member_id  VARCHAR(36),
    parent_id           VARCHAR(36),
    story_points        DOUBLE PRECISION,
    lexo_rank           VARCHAR(64)      NOT NULL,
    created_at          TIMESTAMP        NOT NULL,
    updated_at          TIMESTAMP        NOT NULL,

    CONSTRAINT issues_pk                  PRIMARY KEY (id),
    CONSTRAINT uq_issues_key              UNIQUE (issue_key),
    CONSTRAINT uq_issues_project_sequence UNIQUE (project_id, sequence),
    CONSTRAINT fk_issues_project      FOREIGN KEY (project_id)         REFERENCES projects (id),
    CONSTRAINT fk_issues_type         FOREIGN KEY (issue_type_id)      REFERENCES issue_types (id),
    CONSTRAINT fk_issues_priority     FOREIGN KEY (priority_id)        REFERENCES priorities (id),
    CONSTRAINT fk_issues_status       FOREIGN KEY (status_id)          REFERENCES statuses (id),
    CONSTRAINT fk_issues_resolution   FOREIGN KEY (resolution_id)      REFERENCES resolutions (id),
    CONSTRAINT fk_issues_reporter     FOREIGN KEY (reporter_member_id) REFERENCES members (id),
    CONSTRAINT fk_issues_assignee     FOREIGN KEY (assignee_member_id) REFERENCES members (id),
    CONSTRAINT fk_issues_parent       FOREIGN KEY (parent_id)          REFERENCES issues (id)
);

CREATE INDEX idx_issues_project   ON issues (project_id);
CREATE INDEX idx_issues_rank      ON issues (lexo_rank);
CREATE INDEX idx_issues_parent    ON issues (parent_id);
CREATE INDEX idx_issues_status    ON issues (status_id);
CREATE INDEX idx_issues_assignee  ON issues (assignee_member_id);

-- ── project_issue_counter ─────────────────────────────────────────────────────
--  One row per project holding the next sequence to allocate. A dedicated table,
--  not a column on projects, so locking the counter under PESSIMISTIC_WRITE serialises
--  only issue creation within a project — never project-aggregate edits (ADR-0003).
CREATE TABLE project_issue_counter
(
    project_id  VARCHAR(36) NOT NULL,
    next_value  INTEGER     NOT NULL,

    CONSTRAINT project_issue_counter_pk      PRIMARY KEY (project_id),
    CONSTRAINT fk_project_issue_counter_proj FOREIGN KEY (project_id) REFERENCES projects (id)
);

-- ── labels + issue_label ──────────────────────────────────────────────────────
--  Free-text labels are global strings created on the fly (ticket 11) — a shared
--  `labels` catalog keyed by unique name, applied to issues through the join. Not an
--  admin-managed catalog: any member with EDIT_ISSUE mints a new label by using it.
CREATE TABLE labels
(
    id    VARCHAR(36) NOT NULL,
    name  VARCHAR(64) NOT NULL,

    CONSTRAINT labels_pk      PRIMARY KEY (id),
    CONSTRAINT uq_labels_name UNIQUE (name)
);

CREATE TABLE issue_label
(
    id        VARCHAR(36) NOT NULL,
    issue_id  VARCHAR(36) NOT NULL,
    label_id  VARCHAR(36) NOT NULL,

    CONSTRAINT issue_label_pk       PRIMARY KEY (id),
    CONSTRAINT uq_issue_label       UNIQUE (issue_id, label_id),
    CONSTRAINT fk_issue_label_issue FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_label_label FOREIGN KEY (label_id) REFERENCES labels (id)
);

-- ── issue_component ───────────────────────────────────────────────────────────
CREATE TABLE issue_component
(
    id            VARCHAR(36) NOT NULL,
    issue_id      VARCHAR(36) NOT NULL,
    component_id  VARCHAR(36) NOT NULL,

    CONSTRAINT issue_component_pk           PRIMARY KEY (id),
    CONSTRAINT uq_issue_component           UNIQUE (issue_id, component_id),
    CONSTRAINT fk_issue_component_issue     FOREIGN KEY (issue_id)     REFERENCES issues (id),
    CONSTRAINT fk_issue_component_component FOREIGN KEY (component_id) REFERENCES components (id)
);

-- ── issue_version ─────────────────────────────────────────────────────────────
--  Two distinct associations (affects vs fix) collapsed into one table via
--  `link_kind`, rather than two near-identical tables (ticket 11).
CREATE TABLE issue_version
(
    id          VARCHAR(36) NOT NULL,
    issue_id    VARCHAR(36) NOT NULL,
    version_id  VARCHAR(36) NOT NULL,
    link_kind   VARCHAR(16) NOT NULL
                    CHECK (link_kind IN ('AFFECTS', 'FIX')),

    CONSTRAINT issue_version_pk         PRIMARY KEY (id),
    CONSTRAINT uq_issue_version         UNIQUE (issue_id, version_id, link_kind),
    CONSTRAINT fk_issue_version_issue   FOREIGN KEY (issue_id)   REFERENCES issues (id),
    CONSTRAINT fk_issue_version_version FOREIGN KEY (version_id) REFERENCES versions (id)
);

-- ── link_types + issue_links ──────────────────────────────────────────────────
--  A typed relationship stored once with direction; the target issue renders the
--  inward label (no duplicate reverse row). Symmetric types share the label both
--  ways (ticket 12).
CREATE TABLE link_types
(
    id             VARCHAR(36) NOT NULL,
    name           VARCHAR(64) NOT NULL,
    outward_label  VARCHAR(64) NOT NULL,
    inward_label   VARCHAR(64) NOT NULL,

    CONSTRAINT link_types_pk      PRIMARY KEY (id),
    CONSTRAINT uq_link_types_name UNIQUE (name)
);

CREATE TABLE issue_links
(
    id               VARCHAR(36) NOT NULL,
    source_issue_id  VARCHAR(36) NOT NULL,
    target_issue_id  VARCHAR(36) NOT NULL,
    link_type_id     VARCHAR(36) NOT NULL,
    created_at       TIMESTAMP   NOT NULL,

    CONSTRAINT issue_links_pk        PRIMARY KEY (id),
    CONSTRAINT uq_issue_link         UNIQUE (source_issue_id, target_issue_id, link_type_id),
    CONSTRAINT fk_issue_links_source FOREIGN KEY (source_issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_links_target FOREIGN KEY (target_issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_links_type   FOREIGN KEY (link_type_id)    REFERENCES link_types (id)
);

-- ── comments ──────────────────────────────────────────────────────────────────
--  A flat comment list (no threading), separate from the activity log (ADR-0007).
CREATE TABLE comments
(
    id                VARCHAR(36)   NOT NULL,
    issue_id          VARCHAR(36)   NOT NULL,
    author_member_id  VARCHAR(36)   NOT NULL,
    body              VARCHAR(4000) NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,

    CONSTRAINT comments_pk        PRIMARY KEY (id),
    CONSTRAINT fk_comments_issue  FOREIGN KEY (issue_id)         REFERENCES issues (id),
    CONSTRAINT fk_comments_author FOREIGN KEY (author_member_id) REFERENCES members (id)
);

CREATE INDEX idx_comments_issue ON comments (issue_id);

-- ── activity_logs + activity_log_items ────────────────────────────────────────
--  Hand-rolled change history (ADR-0007): one event (issue, actor, timestamp) with
--  N per-field items carrying point-in-time display-string snapshots — a later rename
--  never rewrites the past. Not Hibernate Envers.
CREATE TABLE activity_logs
(
    id               VARCHAR(36) NOT NULL,
    issue_id         VARCHAR(36) NOT NULL,
    actor_member_id  VARCHAR(36) NOT NULL,
    created_at       TIMESTAMP   NOT NULL,

    CONSTRAINT activity_logs_pk       PRIMARY KEY (id),
    CONSTRAINT fk_activity_logs_issue FOREIGN KEY (issue_id)        REFERENCES issues (id),
    CONSTRAINT fk_activity_logs_actor FOREIGN KEY (actor_member_id) REFERENCES members (id)
);

CREATE INDEX idx_activity_logs_issue ON activity_logs (issue_id);

CREATE TABLE activity_log_items
(
    id               VARCHAR(36)    NOT NULL,
    activity_log_id  VARCHAR(36)    NOT NULL,
    field            VARCHAR(64)    NOT NULL,
    old_value        VARCHAR(1024),
    new_value        VARCHAR(1024),

    CONSTRAINT activity_log_items_pk  PRIMARY KEY (id),
    CONSTRAINT fk_activity_log_items_log FOREIGN KEY (activity_log_id) REFERENCES activity_logs (id)
);

CREATE INDEX idx_activity_log_items_log ON activity_log_items (activity_log_id);


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

-- Link types — Blocks/Duplicates are directional (distinct inward label); Relates is
-- symmetric (same label both ways). The target issue renders the inward label.
INSERT INTO link_types (id, name, outward_label, inward_label) VALUES
    ('link-type-blocks',     'Blocks',     'blocks',     'is blocked by'),
    ('link-type-duplicates', 'Duplicates', 'duplicates', 'is duplicated by'),
    ('link-type-relates',    'Relates',    'relates to', 'relates to');
