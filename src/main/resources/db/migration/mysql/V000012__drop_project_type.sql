SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000012  Drop the project type (ADR-0015)
--
--  Universal SQL: MySQL / PostgreSQL compatible. The postgresql copy
--  is byte-identical bar the mysql copy's utf8mb4 header, there being no
--  backslashes here to double.
--
--  `projects.type` and `boards.scope_strategy` encoded one fact between them —
--  whether a project plans in sprints — and nothing kept them in agreement. A
--  project created as KANBAN whose board was later switched to ACTIVE_SPRINT kept
--  its "Kanban" badge while showing a sprint header, a backlog and sprint reports.
--  With TODO gone, the two remaining types differed by exactly the bit
--  `scope_strategy` already stores, so the type column goes and the strategy is
--  the only switch. Scrum and Kanban survive as a label derived from the strategy
--  and never stored, which is why it can no longer disagree with behaviour.
--
--  Dropping the column takes its inline CHECK with it on all three engines —
--  verified against MySQL 8.4 and PostgreSQL 17 rather than assumed, since a
--  named constraint would have had to be dropped first and would have cost the
--  mysql == postgresql rule.
--
--  project_type_default_schemes was the type -> scheme preset project creation
--  read. Creation now names its defaults directly, so the indirection has nothing
--  left to resolve. The schemes it pointed at are NOT dropped:
--  `scheme-issue-type-todo` and `workflow-todo` remain ordinary schemes any
--  project may select, which is what the scheme model was for (ADR-0015).
-- =============================================================================

ALTER TABLE projects DROP COLUMN type;

DROP TABLE project_type_default_schemes;
