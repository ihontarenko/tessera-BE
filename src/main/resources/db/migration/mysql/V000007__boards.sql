SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000007  Boards — the Kanban board view (Phase 2, ticket 01)
--
--  Universal SQL: MySQL / PostgreSQL compatible. Identical to the mysql copy
--  bar the utf8mb4 header (there are no backslashes to double here, and no
--  dialect-specific functions — see the provisioning note below). Enums as
--  VARCHAR + CHECK; timestamps maintained by the application (@PrePersist /
--  @PreUpdate), not triggers. Identifiers are full words or single-letter
--  abbreviations, never 2–4 letter truncations.
--
--  A Board is a first-class, auto-provisioned view onto a project's issues
--  (ADR-0009): exactly one per project. Its ordered `board_columns` map statuses
--  to columns; an unmapped status falls into its category's fallback column
--  (ADR-0010), so a default board carries ZERO `board_column_statuses` rows and
--  everything resolves by category. `issues.resolved_at` (ADR-0011) records when
--  an issue entered a DONE status, powering the board's done-threshold hiding
--  accurately (unlike `updated_at`, which any edit resets).
--
--  Provisioning note: this migration creates the schema and backfills
--  `resolved_at` (a pure, dialect-identical UPDATE). It does NOT provision boards
--  for existing projects in SQL — that needs a fresh id per board/column, and
--  there is no portable cross-dialect UUID generator (PostgreSQL
--  gen_random_uuid() and MySQL UUID() differ), which would break the
--  `mysql==postgresql` rule and the `VARCHAR(36)` id width. Board provisioning is
--  therefore application code: new projects seed a board on creation
--  (ProjectService), and pre-existing projects are backfilled idempotently at
--  startup (BoardBackfill) — both through the one BoardProvisioner path.
-- =============================================================================

-- ── boards ────────────────────────────────────────────────────────────────────
--  One per project (`project_id` unique, ADR-0009). `swimlane_strategy` and
--  `hide_done_older_than_days` are board-wide view settings (Phase-2 tickets 04/06);
--  Phase-2-ticket-01 seeds NONE / NULL.
CREATE TABLE boards
(
    id                         VARCHAR(36)  NOT NULL,
    project_id                 VARCHAR(36)  NOT NULL,
    name                       VARCHAR(128) NOT NULL,
    swimlane_strategy          VARCHAR(16)  NOT NULL
                                   CHECK (swimlane_strategy IN ('NONE', 'ASSIGNEE', 'EPIC', 'PRIORITY')),
    hide_done_older_than_days  INTEGER,
    created_at                 TIMESTAMP    NOT NULL,
    updated_at                 TIMESTAMP    NOT NULL,

    CONSTRAINT boards_pk         PRIMARY KEY (id),
    CONSTRAINT uq_boards_project UNIQUE (project_id),
    CONSTRAINT fk_boards_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

-- ── board_columns ─────────────────────────────────────────────────────────────
--  Ordered columns (`position`). `min_issues`/`max_issues` are soft WIP limits
--  (visual only, Phase-2 ticket 03). `fallback_for_category` designates this column
--  as the home for any status of that category not explicitly mapped (ADR-0010);
--  the invariant "exactly one fallback column per category" keeps auto-mapping total.
CREATE TABLE board_columns
(
    id                     VARCHAR(36)  NOT NULL,
    board_id               VARCHAR(36)  NOT NULL,
    name                   VARCHAR(128) NOT NULL,
    position               INTEGER      NOT NULL,
    min_issues             INTEGER,
    max_issues             INTEGER,
    fallback_for_category  VARCHAR(16)
                               CHECK (fallback_for_category IN ('TODO', 'IN_PROGRESS', 'DONE')),

    CONSTRAINT board_columns_pk    PRIMARY KEY (id),
    CONSTRAINT fk_board_columns_board FOREIGN KEY (board_id) REFERENCES boards (id)
);

CREATE INDEX idx_board_columns_board ON board_columns (board_id);

-- ── board_column_statuses ─────────────────────────────────────────────────────
--  An explicit status→column mapping, present only for administrator overrides of
--  the category default (ADR-0010). `board_id` is denormalised onto the row so a
--  status maps to at most one column PER BOARD (the unique constraint) and the
--  board-render path prefetches every mapping with one `board_id` lookup — no
--  per-card join.
CREATE TABLE board_column_statuses
(
    id               VARCHAR(36) NOT NULL,
    board_id         VARCHAR(36) NOT NULL,
    board_column_id  VARCHAR(36) NOT NULL,
    status_id        VARCHAR(36) NOT NULL,

    CONSTRAINT board_column_statuses_pk            PRIMARY KEY (id),
    CONSTRAINT uq_board_column_statuses_board_status UNIQUE (board_id, status_id),
    CONSTRAINT fk_board_column_statuses_board      FOREIGN KEY (board_id)        REFERENCES boards (id),
    CONSTRAINT fk_board_column_statuses_column     FOREIGN KEY (board_column_id) REFERENCES board_columns (id),
    CONSTRAINT fk_board_column_statuses_status     FOREIGN KEY (status_id)       REFERENCES statuses (id)
);

CREATE INDEX idx_board_column_statuses_column ON board_column_statuses (board_column_id);

-- ── issues.resolved_at ────────────────────────────────────────────────────────
--  Nullable timestamp set when an issue enters a DONE-category status and cleared
--  when it leaves DONE (ADR-0011), written in the same TransitionService path that
--  sets/clears `resolution_id`. Best-effort backfill for already-closed issues:
--  `updated_at` is the closest available proxy for when they were resolved.
ALTER TABLE issues ADD COLUMN resolved_at TIMESTAMP;

UPDATE issues SET resolved_at = updated_at WHERE resolution_id IS NOT NULL;
