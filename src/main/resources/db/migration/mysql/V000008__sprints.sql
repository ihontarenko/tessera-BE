SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000008  Sprints — the Scrum planning model (Phase 3, tickets 02/03/04)
--
--  Universal SQL: MySQL / PostgreSQL compatible. Identical to the mysql copy
--  bar the utf8mb4 header (there are no backslashes to double here, and no
--  dialect-specific functions). Enums as VARCHAR + CHECK; timestamps maintained
--  by the application (@PrePersist / @PreUpdate), not triggers. Identifiers are
--  full words or single-letter abbreviations, never 2–4 letter truncations —
--  `scope_strategy`, `story_points_at_add`.
--
--  A Sprint is owned by a PROJECT, not by a board (ADR-0012), which keeps
--  ADR-0009's one-board-per-project rule intact. The board instead gains
--  `scope_strategy`: the single field that makes a project "do Scrum". It selects
--  the board's issue source (the whole project, or just the active sprint) and the
--  availability of the Backlog view — so no code, backend or frontend, ever
--  branches on `projects.type`.
--
--  Three invariants are NOT expressed here and are enforced in the service layer,
--  because none is portable: "an issue holds at most one membership with a null
--  `removed_at` in a non-CLOSED sprint" spans two tables and would need a partial
--  unique index (PostgreSQL only, breaking the mysql == postgresql rule); "a project
--  has at most one ACTIVE sprint" needs the same; and "only a hierarchy-level-0
--  issue may hold membership" reaches through `issues` into `issue_types`. This
--  mirrors the Phase-2 precedent where "exactly one fallback column per category"
--  is likewise service-enforced.
-- =============================================================================

-- ── sprints ───────────────────────────────────────────────────────────────────
--  A named, ordered bucket of committed work with a one-way lifecycle
--  FUTURE → ACTIVE → CLOSED (deliberately a plain enum, not the ADR-0005 workflow
--  engine: a sprint is not an issue and its states are fixed by the product rather
--  than configured per project). All three dates are nullable because a FUTURE
--  sprint has none: `end_date` arrives when the sprint is started, `started_at` is
--  stamped server-side at that moment, and `completed_at` at close.
CREATE TABLE sprints
(
    id            VARCHAR(36)   NOT NULL,
    project_id    VARCHAR(36)   NOT NULL,
    name          VARCHAR(128)  NOT NULL,
    goal          VARCHAR(1000),
    state         VARCHAR(16)   NOT NULL
                      CHECK (state IN ('FUTURE', 'ACTIVE', 'CLOSED')),
    started_at    TIMESTAMP,
    end_date      DATE,
    completed_at  TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,

    CONSTRAINT sprints_pk         PRIMARY KEY (id),
    CONSTRAINT fk_sprints_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

-- The backlog screen and every invariant check filter on (project, state).
CREATE INDEX idx_sprints_project_state ON sprints (project_id, state);

-- ── sprint_issues ─────────────────────────────────────────────────────────────
--  The membership row. Identity is (sprint, issue) — re-adding an issue to a
--  sprint it was removed from REVIVES this row rather than writing a second one,
--  so churn within one sprint collapses to its latest stint. Carry-over at sprint
--  close instead produces a row in the NEXT sprint, which is what makes this table
--  the membership history: an issue that took two sprints has two rows and both
--  sprint reports read correctly.
--
--  `story_points_at_add` freezes the estimate at the moment membership begins, so
--  a later re-estimate never rewrites what was committed to. DOUBLE PRECISION
--  matches `issues.story_points`; null means unestimated and counts as zero.
CREATE TABLE sprint_issues
(
    id                   VARCHAR(36) NOT NULL,
    sprint_id            VARCHAR(36) NOT NULL,
    issue_id             VARCHAR(36) NOT NULL,
    added_at             TIMESTAMP   NOT NULL,
    removed_at           TIMESTAMP,
    story_points_at_add  DOUBLE PRECISION,
    added_by_member_id   VARCHAR(36) NOT NULL,

    CONSTRAINT sprint_issues_pk              PRIMARY KEY (id),
    CONSTRAINT uq_sprint_issues_sprint_issue UNIQUE (sprint_id, issue_id),
    CONSTRAINT fk_sprint_issues_sprint       FOREIGN KEY (sprint_id)          REFERENCES sprints (id),
    CONSTRAINT fk_sprint_issues_issue        FOREIGN KEY (issue_id)           REFERENCES issues (id),
    CONSTRAINT fk_sprint_issues_member       FOREIGN KEY (added_by_member_id) REFERENCES members (id)
);

-- "Is this issue committed anywhere?" is the backlog's hot predicate and is asked
-- issue-first; the unique constraint already covers the sprint-first direction.
CREATE INDEX idx_sprint_issues_issue ON sprint_issues (issue_id);

-- ── boards.scope_strategy ─────────────────────────────────────────────────────
--  ALL_ISSUES renders the whole project (today's behaviour); ACTIVE_SPRINT renders
--  exactly the active sprint's current members, and zero cards when none is
--  running. The DEFAULT is what makes this portable in one statement across both
--  dialects (PostgreSQL accepts `ALTER COLUMN … SET NOT NULL`, MySQL
--  does not), and it stays on the column as the safe value for any row inserted by
--  something other than the application.
ALTER TABLE boards ADD COLUMN scope_strategy VARCHAR(16) NOT NULL DEFAULT 'ALL_ISSUES';

ALTER TABLE boards ADD CONSTRAINT ck_boards_scope_strategy
    CHECK (scope_strategy IN ('ALL_ISSUES', 'ACTIVE_SPRINT'));

--  Backfill from the project's type. Unlike the Phase-2 board provisioning this
--  needs no application code, because no new row ids are generated — it is a plain
--  portable UPDATE and the type is read once, here, never again at runtime.
UPDATE boards SET scope_strategy = 'ACTIVE_SPRINT'
 WHERE project_id IN (SELECT id FROM projects WHERE type = 'SCRUM');

-- ── project_type_default_schemes.board_scope_strategy ─────────────────────────
--  The preset a NEW project's board is provisioned with, sourced from data exactly
--  as the type→scheme mapping already is. Adding a project type stays a new row
--  here, not an `if (type == SCRUM)` branch in BoardProvisioner.
ALTER TABLE project_type_default_schemes
    ADD COLUMN board_scope_strategy VARCHAR(16) NOT NULL DEFAULT 'ALL_ISSUES';

ALTER TABLE project_type_default_schemes ADD CONSTRAINT ck_project_type_default_scope_strategy
    CHECK (board_scope_strategy IN ('ALL_ISSUES', 'ACTIVE_SPRINT'));

UPDATE project_type_default_schemes SET board_scope_strategy = 'ACTIVE_SPRINT'
 WHERE project_type = 'SCRUM';
